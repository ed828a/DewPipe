package org.schabi.newpipe.download.background

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.download.downloadDB.MissionEntry
import org.schabi.newpipe.download.util.Utility
import java.io.File
import java.io.ObjectInputStream
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap

/**
 * Created by Edward on 12/21/2018.
 */

class MissionControl (val mission: MissionEntry): Serializable{

    /**
     * Number of blocks the size of [DownloadManager.BLOCK_SIZE]
     */
    var blocks: Long = 0

    /**
     * Number of bytes
     */
    var length: Long = 0

    var threadCount = 3
    var finishCount: Int = 0
    private val blockState: MutableMap<Long, Boolean> = HashMap()
    var running: Boolean = false
    var finished: Boolean = false
    var fallback: Boolean = false
    var errCode = -1

    @Transient
    var recovered: Boolean = false
    @Transient
    private var mListeners = ArrayList<WeakReference<MissionControlListener>>()
    @Transient
    private var mWritingToFile: Boolean = false

    private val threadPositions = ArrayList<Long>()

    /**
     * Get the path of the meta file
     *
     * @return the path to the meta file
     */
    private val metaFilename: String
        get() = "${mission.location}/${mission.name}.giga"

    val downloadedFile: File
        get() = File(mission.location, mission.name)

    private fun checkBlock(block: Long) {
        if (block < 0 || block >= blocks) {
            throw IllegalArgumentException("illegal block identifier")
        }
    }

    /**
     * Check if a block is reserved
     *
     * @param block the block identifier
     * @return true if the block is reserved and false if otherwise
     */
    fun isBlockPreserved(block: Long): Boolean {
        checkBlock(block)
        return if (blockState.containsKey(block) && blockState[block] != null)
            blockState[block]!!
        else
            false
    }

    fun preserveBlock(block: Long) {
        checkBlock(block)
        synchronized(blockState) {
            blockState.put(block, true)
        }
    }

    /**
     * Set the download position of the file
     *
     * @param threadId the identifier of the thread
     * @param position the download position of the thread
     */
    fun setPosition(threadId: Int, position: Long) {
        threadPositions[threadId] = position
    }

    /**
     * Get the position of a thread
     *
     * @param threadId the identifier of the thread
     * @return the position for the thread
     */
    fun getPosition(threadId: Int): Long {
        return threadPositions[threadId]
    }

    @Synchronized
    fun notifyProgress(deltaLen: Long) {
        if (!running) return

        if (recovered) {
            recovered = false
        }

        mission.done += deltaLen

        if (mission.done > length) {
            mission.done = length
        }

        if (mission.done != length) {
            writeThisToFile()
        }

        for (ref in mListeners) {
            val listener = ref.get()
            if (listener != null) {
                MissionControlListener.handlerStore[listener]!!.post {
                    listener.onProgressUpdate(this@MissionControl, mission.done, length)
                }
            }
        }
    }

    /**
     * Called by a download thread when it finished.
     */
    @Synchronized
    fun notifyFinished() {
        if (errCode > 0) return

        finishCount++

        Log.d(TAG, "finishCount: $finishCount -- threadCount: $threadCount")
        if (finishCount == threadCount) {
            onFinish()
        }
    }

    /**
     * Called when all parts are downloaded
     */
    private fun onFinish() {
        if (errCode > 0) return

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onFinish")
        }

        running = false
        finished = true

        deleteThisFromFile()

        for (ref in mListeners) {
            val listener = ref.get()
            listener?.onFinish(this@MissionControl)
        }
    }

    @Synchronized
    fun notifyError(err: Int) {
        errCode = err

        writeThisToFile()

        for (ref in mListeners) {
            val listener = ref.get()
            MissionControlListener.handlerStore[listener]!!.post {
                listener!!.onError(this@MissionControl, errCode)
            }
        }
    }

    @Synchronized
    fun addListener(listener: MissionControlListener) {
        val handler = Handler(Looper.getMainLooper())
        MissionControlListener.handlerStore[listener] = handler
        mListeners.add(WeakReference(listener))
    }

    @Synchronized
    fun removeListener(listener: MissionControlListener?) {
        val iterator = mListeners.iterator()
        while (iterator.hasNext()) {
            val weakRef = iterator.next()
            if (listener != null && listener === weakRef.get()) {
                iterator.remove()
            }
        }
    }

    /**
     * Start downloading with multiple threads.
     */
    fun start() {
        if (!running && !finished) {
            running = true

            if (!fallback) {
                for (i in 0 until threadCount) {
                    if (threadPositions.size <= i && !recovered) {
                        threadPositions.add(i.toLong())
                    }
                    Thread(DownloadRun(this, i)).start()
                }
            } else {
                // In fallback mode, resuming is not supported.
                threadCount = 1
                mission.done = 0
                blocks = 0
                Thread(DownloadRunFallback(this)).start()
            }
        }
    }

    fun pause() {
        if (running) {
            running = false
            recovered = true

            // TODO: Notify & Write state to info file
            // if (err)
        }
    }

    /**
     * Removes the file and the meta file
     */
    fun delete() {
        deleteThisFromFile()
        File(mission.location, mission.name).delete()
    }

    /**
     * Write this [DownloadMission] to the meta file asynchronously
     * if no thread is already running.
     */
    fun writeThisToFile() {
        if (!mWritingToFile) {
            mWritingToFile = true
//            object : Thread() {
//                override fun run() {
//                    doWriteThisToFile()
//                    mWritingToFile = false
//                }
//            }.start()
            Thread(Runnable {
                doWriteThisToFile()
                mWritingToFile = false
            }).start()
        }
    }

    /**
     * Write this [DownloadMission] to the meta file.
     */
    private fun doWriteThisToFile() {
        synchronized(blockState) {
            Utility.writeToFile(metaFilename, this)
        }
    }

    @Throws(java.io.IOException::class, ClassNotFoundException::class)
    private fun readObject(inputStream: ObjectInputStream) {
        inputStream.defaultReadObject()
        mListeners = ArrayList()
    }

    private fun deleteThisFromFile() {
        File(metaFilename).delete()
    }

    companion object {
        private const val serialVersionUID = 0L

        private val TAG = MissionControl::class.java.simpleName

        val ERROR_SERVER_UNSUPPORTED = 206
        val ERROR_UNKNOWN = 233

        private val NO_IDENTIFIER = -1
    }

}
