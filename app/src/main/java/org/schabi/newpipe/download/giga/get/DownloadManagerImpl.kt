package org.schabi.newpipe.download.giga.get

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.download.ui.ExtSDDownloadFailedActivity
import org.schabi.newpipe.download.util.Utility
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class DownloadManagerImpl : DownloadManager {
    private val mDownloadDataSource: DownloadDataSource

    private val mMissions = ArrayList<DownloadMission>()
    private val context: Context?

    /**
     * Create a new instance
     *
     * @param searchLocations    the directories to search for unfinished downloads
     * @param downloadDataSource the data source for finished downloads
     */
    constructor(searchLocations: Collection<String>, downloadDataSource: DownloadDataSource) {
        mDownloadDataSource = downloadDataSource
        this.context = null
        loadMissions(searchLocations)
    }

    constructor(searchLocations: Collection<String>, downloadDataSource: DownloadDataSource, context: Context) {
        mDownloadDataSource = downloadDataSource
        this.context = context
        loadMissions(searchLocations)
    }

    override fun startMission(url: String, location: String, name: String, isAudio: Boolean, threads: Int): Int {
        var name = name
        val existingMission = getMissionByLocation(location, name)
        if (existingMission != null) {
            // Already downloaded or downloading
            if (existingMission.finished) {
                // Overwrite mission
                deleteMission(mMissions.indexOf(existingMission))
            } else {
                // Rename file (?)
                try {
                    name = generateUniqueName(location, name)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to generate unique name", e)
                    name = System.currentTimeMillis().toString() + name
                    Log.i(TAG, "Using $name")
                }

            }
        }

        val mission = DownloadMission(name, url, location)
        mission.timestamp = System.currentTimeMillis()
        mission.threadCount = threads
        mission.addListener(MissionListener(mission))
        Initializer(mission).start()
        return insertMission(mission)
    }

    override fun resumeMission(i: Int) {
        val d = getMission(i)
        if (!d.running && d.errCode == -1) {
            d.start()
        }
    }

    override fun pauseMission(i: Int) {
        val d = getMission(i)
        if (d.running) {
            d.pause()
        }
    }

    override fun deleteMission(i: Int) {
        val mission = getMission(i)
        if (mission.finished) {
            mDownloadDataSource.deleteMission(mission)
        }
        mission.delete()
        mMissions.removeAt(i)
    }

    private fun loadMissions(searchLocations: Iterable<String>) {
        mMissions.clear()
        loadFinishedMissions()
        for (location in searchLocations) {
            loadMissions(location)
        }

    }

    /**
     * Loads finished missions from the data source
     */
    private fun loadFinishedMissions() {
        var finishedMissions: List<DownloadMission>? = mDownloadDataSource.loadMissions()
        if (finishedMissions == null) {
            finishedMissions = ArrayList()
        }
        // Ensure its sorted
        sortByTimestamp(finishedMissions)

        mMissions.ensureCapacity(mMissions.size + finishedMissions.size)
        for (mission in finishedMissions) {
            val downloadedFile = mission.downloadedFile
            if (!downloadedFile.isFile) {
                if (DEBUG) {
                    Log.d(TAG, "downloaded file removed: " + downloadedFile.absolutePath)
                }
                mDownloadDataSource.deleteMission(mission)
            } else {
                mission.length = downloadedFile.length()
                mission.finished = true
                mission.running = false
                mMissions.add(mission)
            }
        }
    }

    private fun loadMissions(location: String) {

        val f = File(location)

        if (f.exists() && f.isDirectory) {
            val subs = f.listFiles()

            if (subs == null) {
                Log.e(TAG, "listFiles() returned null")
                return
            }

            for (sub in subs) {
                if (sub.isFile && sub.name.endsWith(".giga")) {
                    val mis = Utility.readFromFile<DownloadMission>(sub.absolutePath)
                    if (mis != null) {
                        if (mis.finished) {
                            if (!sub.delete()) {
                                Log.w(TAG, "Unable to delete .giga file: " + sub.path)
                            }
                            continue
                        }

                        mis.running = false
                        mis.recovered = true
                        insertMission(mis)
                    }
                }
            }
        }
    }

    override fun getMission(i: Int): DownloadMission {
        return mMissions[i]
    }

    override val count: Int
        get() = mMissions.size

    private fun insertMission(mission: DownloadMission): Int {
        var i = -1

        var m: DownloadMission? = null

        if (mMissions.size > 0) {
            do {
                m = mMissions[++i]
            } while (m!!.timestamp > mission.timestamp && i < mMissions.size - 1)

            //if (i > 0) i--;
        } else {
            i = 0
        }

        mMissions.add(i, mission)

        return i
    }

    /**
     * Get a mission by its location and name
     *
     * @param location the location
     * @param name     the name
     * @return the mission or null if no such mission exists
     */
    private fun getMissionByLocation(location: String, name: String): DownloadMission? {
        for (mission in mMissions) {
            if (location == mission.location && name == mission.name) {
                return mission
            }
        }
        return null
    }

    private inner class Initializer(private val mission: DownloadMission) : Thread() {
        private val handler: Handler

        init {
            this.handler = Handler()
        }

        override fun run() {
            try {
                val url = URL(mission.url)
                var conn = url.openConnection() as HttpURLConnection
                mission.length = conn.contentLength.toLong()

                if (mission.length <= 0) {
                    mission.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED
                    //mission.notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED);
                    return
                }

                // Open again
                conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Range", "bytes=" + (mission.length - 10) + "-" + mission.length)

                if (conn.responseCode != 206) {
                    // Fallback to single thread if no partial content support
                    mission.fallback = true

                    if (DEBUG) {
                        Log.d(TAG, "falling back")
                    }
                }

                if (DEBUG) {
                    Log.d(TAG, "response = " + conn.responseCode)
                }

                mission.blocks = mission.length / DownloadManager.BLOCK_SIZE

                if (mission.threadCount > mission.blocks) {
                    mission.threadCount = mission.blocks.toInt()
                }

                if (mission.threadCount <= 0) {
                    mission.threadCount = 1
                }

                if (mission.blocks * DownloadManager.BLOCK_SIZE < mission.length) {
                    mission.blocks = mission.blocks + 1
                }


                File(mission.location).mkdirs()
                File(mission.location + "/" + mission.name).createNewFile()
                val af = RandomAccessFile(mission.location + "/" + mission.name, "rw")
                af.setLength(mission.length)
                af.close()

                mission.start()
            } catch (ie: IOException) {
                if (context == null) throw RuntimeException(ie)

                if (ie.message != null && ie.message!!.contains("Permission denied")) {
                    handler.post { context.startActivity(Intent(context, ExtSDDownloadFailedActivity::class.java)) }
                } else
                    throw RuntimeException(ie)
            } catch (e: Exception) {
                // TODO Notify
                throw RuntimeException(e)
            }

        }
    }

    /**
     * Waits for mission to finish to add it to the [.mDownloadDataSource]
     */
    private inner class MissionListener(private val mMission: DownloadMission?) : DownloadMission.MissionListener {

        init {
            if (mMission == null) throw NullPointerException("mission is null")
        }// Could the mission be passed in onFinish()?

        override fun onProgressUpdate(downloadMission: DownloadMission, done: Long, total: Long) {}

        override fun onFinish(downloadMission: DownloadMission) {
            mDownloadDataSource.addMission(mMission)
        }

        override fun onError(downloadMission: DownloadMission, errCode: Int) {}
    }

    companion object {
        private val TAG = DownloadManagerImpl::class.java.simpleName

        /**
         * Sort a list of mission by its timestamp. Oldest first
         * @param missions the missions to sort
         */
        internal fun sortByTimestamp(missions: List<DownloadMission>) {
            Collections.sort(missions) { o1, o2 -> java.lang.Long.compare(o1.timestamp, o2.timestamp) }
        }

        /**
         * Splits the filename into name and extension
         *
         *
         * Dots are ignored if they appear: not at all, at the beginning of the file,
         * at the end of the file
         *
         * @param name the name to split
         * @return a string array with a length of 2 containing the name and the extension
         */
        private fun splitName(name: String): Array<String> {
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex <= 0 || dotIndex == name.length - 1) {
                arrayOf(name, "")
            } else {
                arrayOf(name.substring(0, dotIndex), name.substring(dotIndex + 1))
            }
        }

        /**
         * Generates a unique file name.
         *
         *
         * e.g. "myname (1).txt" if the name "myname.txt" exists.
         *
         * @param location the location (to check for existing files)
         * @param name     the name of the file
         * @return the unique file name
         * @throws IllegalArgumentException if the location is not a directory
         * @throws SecurityException        if the location is not readable
         */
        private fun generateUniqueName(location: String?, name: String?): String {
            if (location == null) throw NullPointerException("location is null")
            if (name == null) throw NullPointerException("name is null")
            val destination = File(location)
            if (!destination.isDirectory) {
                throw IllegalArgumentException("location is not a directory: $location")
            }
            val nameParts = splitName(name)
            val existingName = destination.list { dir, name -> name.startsWith(nameParts[0]) }
            Arrays.sort(existingName)
            var newName: String
            var downloadIndex = 0
            do {
                newName = nameParts[0] + " (" + downloadIndex + ")." + nameParts[1]
                ++downloadIndex
                if (downloadIndex == 1000) {  // Probably an error on our side
                    throw RuntimeException("Too many existing files")
                }
            } while (Arrays.binarySearch(existingName, newName) >= 0)
            return newName
        }
    }
}
