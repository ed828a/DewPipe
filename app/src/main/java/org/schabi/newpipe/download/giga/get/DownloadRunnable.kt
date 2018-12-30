package org.schabi.newpipe.download.giga.get

import android.util.Log

import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

import org.schabi.newpipe.BuildConfig.DEBUG

/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
class DownloadRunnable(private val mMission: DownloadMission?, private val mId: Int) : Runnable {

    init {
        if (mMission == null) throw NullPointerException("mission is null")
    }

    override fun run() {
        var retry = mMission!!.recovered
        var position = mMission!!.getPosition(mId)

        if (DEBUG) {
            Log.d(TAG, mId.toString() + ":default pos " + position)
            Log.d(TAG, mId.toString() + ":recovered: " + mMission.recovered)
        }

        while (mMission.errCode == -1 && mMission.running && position < mMission.blocks) {

            if (Thread.currentThread().isInterrupted) {
                mMission.pause()
                return
            }

            if (DEBUG && retry) {
                Log.d(TAG, mId.toString() + ":retry is true. Resuming at " + position)
            }

            // Wait for an unblocked position
            while (!retry && position < mMission.blocks && mMission.isBlockPreserved(position)) {

                if (DEBUG) {
                    Log.d(TAG, mId.toString() + ":position " + position + " preserved, passing")
                }

                position++
            }

            retry = false

            if (position >= mMission.blocks) {
                break
            }

            if (DEBUG) {
                Log.d(TAG, mId.toString() + ":preserving position " + position)
            }

            mMission.preserveBlock(position)
            mMission.setPosition(mId, position)

            var start = position * DownloadManager.BLOCK_SIZE
            var end = start + DownloadManager.BLOCK_SIZE - 1

            if (end >= mMission.length) {
                end = mMission.length - 1
            }

            var conn: HttpURLConnection? = null

            var total = 0

            try {
                val url = URL(mMission.url)
                conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Range", "bytes=$start-$end")

                if (DEBUG) {
                    Log.d(TAG, mId.toString() + ":" + conn.getRequestProperty("Range"))
                    Log.d(TAG, mId.toString() + ":Content-Length=" + conn.contentLength + " Code:" + conn.responseCode)
                }

                // A server may be ignoring the range request
                if (conn.responseCode != 206) {
                    mMission.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED
                    notifyError()

                    if (DEBUG) {
                        Log.e(TAG, mId.toString() + ":Unsupported " + conn.responseCode)
                    }

                    break
                }

                val f = RandomAccessFile(mMission.location + "/" + mMission.name, "rw")
                f.seek(start)
                val ipt = conn.inputStream
                val buf = ByteArray(64 * 1024)

                while (start < end && mMission.running) {
                    val len = ipt.read(buf, 0, buf.size)

                    if (len == -1) {
                        break
                    } else {
                        start += len.toLong()
                        total += len
                        f.write(buf, 0, len)
                        notifyProgress(len.toLong())
                    }
                }

                if (DEBUG && mMission.running) {
                    Log.d(TAG, mId.toString() + ":position " + position + " finished, total length " + total)
                }

                f.close()
                ipt.close()

                // TODO We should save progress for each thread
            } catch (e: Exception) {
                // TODO Retry count limit & notify error
                retry = true

                notifyProgress((-total).toLong())

                if (DEBUG) {
                    Log.d(TAG, mId.toString() + ":position " + position + " retrying", e)
                }
            }

        }

        if (DEBUG) {
            Log.d(TAG, "thread $mId exited main loop")
        }

        if (mMission.errCode == -1 && mMission.running) {
            if (DEBUG) {
                Log.d(TAG, "no error has happened, notifying")
            }
            notifyFinished()
        }

        if (DEBUG && !mMission.running) {
            Log.d(TAG, "The mission has been paused. Passing.")
        }
    }

    private fun notifyProgress(len: Long) {
        synchronized(mMission!!) {
            mMission.notifyProgress(len)
        }
    }

    private fun notifyError() {
        synchronized(mMission!!) {
            mMission.notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED)
            mMission.pause()
        }
    }

    private fun notifyFinished() {
        synchronized(mMission!!) {
            mMission.notifyFinished()
        }
    }

    companion object {
        private val TAG = DownloadRunnable::class.java.simpleName
    }
}
