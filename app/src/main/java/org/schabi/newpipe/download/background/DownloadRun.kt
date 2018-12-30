package org.schabi.newpipe.download.background

import android.util.Log
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by Edward on 12/21/2018.
 *
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
class DownloadRun(private val missionControl: MissionControl, private val mId: Int) : Runnable {

    override fun run() {
        var retry = missionControl.recovered
        var position = missionControl.getPosition(mId)

        Log.d(TAG, "${mId.toString()}:default pos $position, -- recovered: ${missionControl.recovered}")

        while (missionControl.errCode == -1 && missionControl.running && position < missionControl.blocks) {

            if (Thread.currentThread().isInterrupted) {
                missionControl.pause()
                return
            }

            if (retry) {
                Log.d(TAG, mId.toString() + ":retry is true. Resuming at " + position)
            }

            // Wait for an unblocked position
            while (!retry && position < missionControl.blocks && missionControl.isBlockPreserved(position)) {
                Log.d(TAG, mId.toString() + ":position " + position + " preserved, passing")

                position++
            }

            retry = false

            if (position >= missionControl.blocks) {
                break
            }

            Log.d(TAG, "${mId.toString()}:preserving position $position")

            missionControl.preserveBlock(position)
            missionControl.setPosition(mId, position)

            var start = position * DownloadMissionManager.BLOCK_SIZE
            var end = start + DownloadMissionManager.BLOCK_SIZE - 1

            if (end >= missionControl.length) {
                end = missionControl.length - 1
            }

            var total = 0
            var urlConnection: HttpURLConnection
            try {
                val url = URL(missionControl.mission.url)
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.setRequestProperty("Range", "bytes=$start-$end")

                Log.d(TAG, "${mId.toString()}:${urlConnection.getRequestProperty("Range")}, -- Content-Length = ${urlConnection.contentLength} Code:${urlConnection.responseCode}")

                /**
                 * 2×× SUCCESS
                 * 206 PARTIAL CONTENT: The server is successfully fulfilling a range request for the target resource
                 * by transferring one or more parts of the selected representation that correspond
                 * to the satisfiable ranges found in the request's Range header field
                 */
                if (urlConnection.responseCode != 206) {
                    missionControl.errCode = MissionControl.ERROR_SERVER_UNSUPPORTED
                    notifyError()

                    Log.e(TAG, mId.toString() + ":Unsupported " + urlConnection.responseCode)

                    break
                }

                val file = RandomAccessFile("${missionControl.mission.location}/${missionControl.mission.name}", "rw")
                file.seek(start)
                val inputStream = urlConnection.inputStream
                val buf = ByteArray(64 * 1024)

                while (start < end && missionControl.running) {
                    val len = inputStream.read(buf, 0, buf.size)

                    if (len == -1) {
                        break
                    } else {
                        start += len.toLong()
                        total += len
                        file.write(buf, 0, len)
                        notifyProgress(len.toLong())
                    }
                }

                Log.d(TAG, "${mId.toString()}:position $position finished, total length $total")

                file.close()
                inputStream.close()

                // TODO We should save progress for each thread
            } catch (e: Exception) {
                // TODO Retry count limit & notify error
                retry = true

                notifyProgress((-total).toLong())

                Log.d(TAG, mId.toString() + ":position " + position + " retrying", e)
            }
        }

        Log.d(TAG, "thread $mId exited main loop")

        if (missionControl.errCode == -1 && missionControl.running) {
            Log.d(TAG, "no error has happened, notifying")

            notifyFinished()
        }

        if (!missionControl.running) {
            Log.d(TAG, "The mission has been paused. Passing.")
        }
    }

    private fun notifyProgress(len: Long) {
        synchronized(missionControl) {
            missionControl.notifyProgress(len)
        }
    }

    private fun notifyError() {
        synchronized(missionControl) {
            missionControl.notifyError(MissionControl.ERROR_SERVER_UNSUPPORTED)
            missionControl.pause()
        }
    }

    private fun notifyFinished() {
        synchronized(missionControl) {
            missionControl.notifyFinished()
        }
    }

    companion object {
        private const val TAG = "DownloadRun"

    }
}
