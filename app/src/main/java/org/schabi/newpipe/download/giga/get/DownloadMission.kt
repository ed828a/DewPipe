package org.schabi.newpipe.download.giga.get

import android.os.Handler
import android.os.Looper
import android.util.Log

import java.io.File
import java.io.ObjectInputStream
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap

import org.schabi.newpipe.download.util.Utility

import org.schabi.newpipe.BuildConfig.DEBUG

class DownloadMission : Serializable {

    /**
     * The filename
     */
    var name: String = ""

    /**
     * The url of the file to download
     */
    var url: String = ""

    /**
     * The directory to store the download
     */
    var location: String = ""

    /**
     * Number of blocks the size of [DownloadManager.BLOCK_SIZE]
     */
    var blocks: Long = 0

    /**
     * Number of bytes
     */
    var length: Long = 0

    /**
     * Number of bytes downloaded
     */
    var done: Long = 0
    var threadCount = 3
    private var finishCount: Int = 0
    private val threadPositions = ArrayList<Long>()
    private val blockState: MutableMap<Long, Boolean> = HashMap()
    var running: Boolean = false
    var finished: Boolean = false
    var fallback: Boolean = false
    var errCode = -1
    var timestamp: Long = 0

    @Transient
    var recovered: Boolean = false

    @Transient
    private var mListeners = ArrayList<WeakReference<MissionListener>>()
    @Transient
    private var mWritingToFile: Boolean = false

    /**
     * Get the path of the meta file
     *
     * @return the path to the meta file
     */
    private val metaFilename: String
        get() = "$location/$name.giga"

    val downloadedFile: File
        get() = File(location, name)

    interface MissionListener {

        fun onProgressUpdate(downloadMission: DownloadMission, done: Long, total: Long)

        fun onFinish(downloadMission: DownloadMission)

        fun onError(downloadMission: DownloadMission, errCode: Int)

        companion object {
            val handlerStore = HashMap<MissionListener, Handler>()
        }
    }

    constructor() {}

    constructor(name: String?, url: String?, location: String?) {
        if (name == null) throw NullPointerException("name is null")
        if (name.isEmpty()) throw IllegalArgumentException("name is empty")
        if (url == null) throw NullPointerException("url is null")
        if (url.isEmpty()) throw IllegalArgumentException("url is empty")
        if (location == null) throw NullPointerException("location is null")
        if (location.isEmpty()) throw IllegalArgumentException("location is empty")
        this.url = url
        this.name = name
        this.location = location
    }


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
        return if (blockState.containsKey(block) && blockState[block] != null) blockState[block]!! else false
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

        done += deltaLen

        if (done > length) {
            done = length
        }

        if (done != length) {
            writeThisToFile()
        }

        for (ref in mListeners) {
            val listener = ref.get()
            if (listener != null) {
                MissionListener.handlerStore[listener]!!.post { listener.onProgressUpdate(this@DownloadMission, done, length) }
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

        if (finishCount == threadCount) {
            onFinish()
        }
    }

    /**
     * Called when all parts are downloaded
     */
    private fun onFinish() {
        if (errCode > 0) return

        if (DEBUG) {
            Log.d(TAG, "onFinish")
        }

        running = false
        finished = true

        deleteThisFromFile()

        for (ref in mListeners) {
            val listener = ref.get()
            if (listener != null) {
                MissionListener.handlerStore[listener]!!.post { listener.onFinish(this@DownloadMission) }
            }
        }
    }

    @Synchronized
    fun notifyError(err: Int) {
        errCode = err

        writeThisToFile()

        for (ref in mListeners) {
            val listener = ref.get()
            MissionListener.handlerStore[listener]!!.post { listener!!.onError(this@DownloadMission, errCode) }
        }
    }

    @Synchronized
    fun addListener(listener: MissionListener) {
        val handler = Handler(Looper.getMainLooper())
        MissionListener.handlerStore[listener] = handler
        mListeners.add(WeakReference(listener))
    }

    @Synchronized
    fun removeListener(listener: MissionListener?) {
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
                    Thread(DownloadRunnable(this, i)).start()
                }
            } else {
                // In fallback mode, resuming is not supported.
                threadCount = 1
                done = 0
                blocks = 0
                Thread(DownloadRunnableFallback(this)).start()
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
        File(location, name).delete()
    }

    /**
     * Write this [DownloadMission] to the meta file asynchronously
     * if no thread is already running.
     */
    fun writeThisToFile() {
        if (!mWritingToFile) {
            mWritingToFile = true
            object : Thread() {
                override fun run() {
                    doWriteThisToFile()
                    mWritingToFile = false
                }
            }.start()
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

        private val TAG = DownloadMission::class.java.simpleName

        val ERROR_SERVER_UNSUPPORTED = 206
        val ERROR_UNKNOWN = 233

        private val NO_IDENTIFIER = -1
    }

}
