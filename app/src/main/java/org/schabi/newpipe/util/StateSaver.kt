package org.schabi.newpipe.util


import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.MainActivity
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A way to save state to disk or in a in-memory map if it's just changing configurations (i.e. rotating the phone).
 *
 *  icepick may replace this StateSaver
 */
object StateSaver {
    private val stateObjectsHolder = ConcurrentHashMap<String, Queue<Any>>()
    private const val TAG = "StateSaver"
    private const val CACHE_DIR_NAME = "state_cache"

    const val KEY_SAVED_STATE = "key_saved_state"
    private var cacheDirPath: String? = null

    /**
     * Initialize the StateSaver, usually you want to call this in the Application class
     *
     * @param context used to get the available cache dir
     */
    fun init(context: Context) {
        val externalCacheDir = context.externalCacheDir
        cacheDirPath = externalCacheDir?.absolutePath
        if (TextUtils.isEmpty(cacheDirPath)) cacheDirPath = context.cacheDir.absolutePath
    }

    /**
     * Used for describe how to save/read the objects.
     *
     *
     * Queue was chosen by its FIFO property.
     */
    interface WriteRead {
        /**
         * Generate a changing suffix that will name the cache file,
         * and be used to identify if it changed (thus reducing useless reading/saving).
         *
         * @return a unique value
         */
        fun generateSuffix(): String

        /**
         * Add to this queue objects that you want to save.
         */
        fun writeTo(objectsToSave: Queue<Any>)

        /**
         * Poll saved objects getTabFrom the queue in the order they were written.
         *
         * @param savedObjects queue of objects returned by [.writeTo]
         */
        @Throws(Exception::class)
        fun readFrom(savedObjects: Queue<Any>)
    }

    /**
     * @see .tryToRestore
     */
    fun tryToRestore(outState: Bundle?, writeRead: WriteRead?): SavedState? {
        if (outState == null || writeRead == null) return null

        val savedState = outState.getParcelable<SavedState>(KEY_SAVED_STATE) ?: return null

        return tryToRestore(savedState, writeRead)
    }

    /**
     * Try to restore the state getTabFrom memory and disk, using the [StateSaver.WriteRead.readFrom] getTabFrom the writeRead.
     */
    private fun tryToRestore(savedState: SavedState, writeRead: WriteRead): SavedState? {
        Log.d(TAG, "tryToRestore() called with: savedState = [$savedState], writeRead = [$writeRead]")

        var fileInputStream: FileInputStream? = null
        try {
            var savedObjects = stateObjectsHolder.remove(savedState.prefixFileSaved)
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects)

                Log.d(TAG, "tryToSave: reading objects getTabFrom holder > $savedObjects, stateObjectsHolder > $stateObjectsHolder")

                return savedState
            }

            val file = File(savedState.pathFileSaved)
            if (!file.exists()) {
                Log.d(TAG, "Cache file doesn't exist: " + file.absolutePath)
                return null
            }

            fileInputStream = FileInputStream(file)
            val inputStream = ObjectInputStream(fileInputStream)

            savedObjects = inputStream.readObject() as Queue<Any>?
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects)
            }

            return savedState
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state", e)
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close()
                } catch (ignored: IOException) {
                }

            }
        }
        return null
    }

    /**
     * @see .tryToSave
     */
    fun tryToSave(isChangingConfig: Boolean, savedState: SavedState?, outState: Bundle, writeRead: WriteRead): SavedState? {
        var locSavedState = savedState
        val currentSavedPrefix: String = if (locSavedState == null || TextUtils.isEmpty(locSavedState.prefixFileSaved)) {
            // Generate unique prefix
            "${(System.nanoTime() - writeRead.hashCode())}"
        } else {
            // Reuse prefix
            locSavedState.prefixFileSaved!!
        }

        locSavedState = tryToSave(isChangingConfig, currentSavedPrefix, writeRead.generateSuffix(), writeRead)
        return if (locSavedState != null) {
            outState.putParcelable(StateSaver.KEY_SAVED_STATE, locSavedState)
            locSavedState
        } else null
    }

    /**
     * If it's not changing configuration (i.e. rotating screen), try to write the state getTabFrom [StateSaver.WriteRead.writeTo]
     * to the file with the name of prefixFileName + suffixFileName, in a cache folder got getTabFrom the [.init].
     *
     *
     * It checks if the file already exists and if it does, just return the path, so a good way to save is:
     *
     *  *  A fixed prefix for the file
     *  *  A changing suffix
     *
     *
     * @param isChangingConfig
     * @param prefixFileName
     * @param suffixFileName
     * @param writeRead
     */
    private fun tryToSave(isChangingConfig: Boolean, prefixFileName: String, suffixFileName: String, writeRead: WriteRead): SavedState? {
        Log.d(TAG, "tryToSave() called with: isChangingConfig = [$isChangingConfig], prefixFileName = [$prefixFileName], suffixFileName = [$suffixFileName], writeRead = [$writeRead]")

        var locSuffixFileName = suffixFileName
        val savedObjects = LinkedList<Any>()
        writeRead.writeTo(savedObjects)

        if (isChangingConfig) {
            return if (savedObjects.size > 0) {
                stateObjectsHolder[prefixFileName] = savedObjects
                SavedState(prefixFileName, "")
            } else {
                Log.d(TAG, "Nothing to save")
                null
            }
        }

        var fileOutputStream: FileOutputStream? = null
        try {
            var cacheDir = File(cacheDirPath)
            if (!cacheDir.exists()) throw RuntimeException("Cache dir does not exist > ${cacheDirPath!!}")
            cacheDir = File(cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists() && !cacheDir.mkdir()) {
                Log.e(TAG, "Failed to create cache directory ${cacheDir.absolutePath}")
                return null
            }

            if (TextUtils.isEmpty(locSuffixFileName)) locSuffixFileName = ".cache"
            val file = File(cacheDir, prefixFileName + locSuffixFileName)
            if (file.exists() && file.length() > 0) {
                // If the file already exists, just return it
                return SavedState(prefixFileName, file.absolutePath)
            } else {
                // Delete any file that contains the prefix
                val files = cacheDir.listFiles { dir, name -> name.contains(prefixFileName) }
                for (fileToDelete in files) {
                    fileToDelete.delete()
                }
            }

            fileOutputStream = FileOutputStream(file)
            val outputStream = ObjectOutputStream(fileOutputStream)
            outputStream.writeObject(savedObjects)

            return SavedState(prefixFileName, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state", e)
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close()
                } catch (ignored: IOException) {
                }

            }
        }
        return null
    }

    /**
     * Delete the cache file contained in the savedState and remove any possible-existing value in the memory-cache.
     */
    fun onDestroy(savedState: SavedState?) {
        Log.d(TAG, "StateSaver.onDestroy() called with: savedState = [$savedState]")

        if (savedState != null && !TextUtils.isEmpty(savedState.pathFileSaved)) {
            stateObjectsHolder.remove(savedState.prefixFileSaved)

            try {
                File(savedState.pathFileSaved).delete()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Clear all the files in cache (in memory and disk).
     */
    fun clearStateFiles() {
        Log.d(TAG, "clearStateFiles() called")

        stateObjectsHolder.clear()
        var cacheDir = File(cacheDirPath)
        if (!cacheDir.exists()) return

        cacheDir = File(cacheDir, CACHE_DIR_NAME)
        if (cacheDir.exists()) {
            for (file in cacheDir.listFiles()) file.delete()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inner
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Information about the saved state on the disk
     */
    class SavedState(val prefixFileSaved: String?, // Get the prefix of the saved file -- the file prefix
                     val pathFileSaved: String? // Get the path to the saved file
    ) : Parcelable {

        constructor(`in`: Parcel) : this(
                `in`.readString(),
                `in`.readString()
        )

        override fun toString(): String {
            return "prefix: $prefixFileSaved > path: $pathFileSaved"
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(prefixFileSaved)
            dest.writeString(pathFileSaved)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }


    }


}
