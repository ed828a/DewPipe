package org.schabi.newpipe.download.background

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.download.downloadDB.DownloadDAO
import org.schabi.newpipe.download.downloadDB.DownloadDatabase
import org.schabi.newpipe.download.downloadDB.MissionEntry
import org.schabi.newpipe.download.giga.get.DownloadMission


import org.schabi.newpipe.download.util.Utility
import org.schabi.newpipe.download.ui.ExtSDDownloadFailedActivity
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Created by Edward on 12/22/2018.
 *
 * @param downloadDataSource: should be like:
 *                  val db: DownloadDatabase = DownloadDatabase.getDatabase(context)
 *                  private val mDownloadDataSource = db.downloadDao()
 */

class DownloadMissionManagerImpl(searchLocations: Collection<String>,
//                                 private val downloadDataSource: DownloadDAO,
                                 val context: Context
) : DownloadMissionManager {
    //    val db: DownloadDatabase = DownloadDatabase.getDatabase(context)
    val db: AppDatabase = NewPipeDatabase.getInstance(context)
    private val downloadDataSource: DownloadDAO = db.downloadDAO()
    private val mMissionControls = ArrayList<MissionControl>()

    init {
        // to initialize mMissionControls with searchLocations
        loadMissionControls(searchLocations)
    }

    override fun startMission(url: String, location: String, name: String, isAudio: Boolean, threads: Int): Int {
        var fileName = name
        val existingMission = getMissionByLocation(location, fileName)
        if (existingMission != null) {
            // Already downloaded or downloading
            if (existingMission.finished) {
                // Overwrite mission
                deleteMission(mMissionControls.indexOf(existingMission))
            } else {
                // Rename file (?)
                try {
                    fileName = generateUniqueName(location, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to generate unique name", e)
                    fileName = System.currentTimeMillis().toString() + fileName
                    Log.i(TAG, "Using $fileName")
                }
            }
        }

        val missionEntry = MissionEntry(fileName, url, location)
        val missionControl = MissionControl(missionEntry)
        missionControl.mission.timestamp = System.currentTimeMillis()
        missionControl.threadCount = threads
        missionControl.addListener(MissionControlListener(missionControl))
        Initializer(missionControl).start()
        return insertMission(missionControl)
    }

    override fun resumeMission(missionId: Int) {
        val missionControl = getMission(missionId)
        if (!missionControl.running && missionControl.errCode == -1) {
            missionControl.start()
        }
    }

    override fun pauseMission(missionId: Int) {
        val missionControl = getMission(missionId)
        if (missionControl.running) {
            missionControl.pause()
        }
    }

    override fun deleteMission(missionId: Int) {
        val missionControl = getMission(missionId)
        if (missionControl.finished) {
            downloadDataSource.deleteMission(missionControl.mission)
        }
        missionControl.delete()
        mMissionControls.removeAt(missionId)
    }

    private fun loadMissionControls(searchLocations: Iterable<String>) {
        mMissionControls.clear()
        loadFinishedMissions()
        for (location in searchLocations) {
            loadMissionControls(location)
        }

    }

    /**
     * Loads finished missions from the data source
     */
    private fun buildMissionControls(missions: List<MissionEntry>): MutableList<MissionControl> {
        val missionControlList = ArrayList<MissionControl>(missions.size)
        missions.forEach {
            missionControlList.add(MissionControl(it))
        }
        return missionControlList
    }

    private fun loadFinishedMissions() {
        val finishedMissions: MutableList<MissionControl> =
                buildMissionControls(downloadDataSource.loadMissions())

        // Ensure its sorted
        sortByTimestamp(finishedMissions)

        mMissionControls.ensureCapacity(mMissionControls.size + finishedMissions.size)
        for (missionControl in finishedMissions) {
            val downloadedFile = missionControl.downloadedFile
            if (!downloadedFile.isFile) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "downloaded file removed: " + downloadedFile.absolutePath)
                }
                downloadDataSource.deleteMission(missionControl.mission)
            } else {
                missionControl.length = downloadedFile.length()
                missionControl.finished = true
                missionControl.running = false
                mMissionControls.add(missionControl)
            }
        }
    }

    private fun loadMissionControls(location: String) {

        val file = File(location)

        if (file.exists() && file.isDirectory) {
            val subs = file.listFiles()

            if (subs == null) {
                Log.e(TAG, "listFiles() returned null")
                return
            }

            for (sub in subs) {
                if (sub.isFile && sub.name.endsWith(".giga")) {
                    val missionControl = Utility.readFromFile<MissionControl>(sub.absolutePath)
                    if (missionControl != null) {
                        if (missionControl.finished) {
                            if (!sub.delete()) {
                                Log.w(TAG, "Unable to delete .giga file: " + sub.path)
                            }
                            continue
                        }

                        missionControl.running = false
                        missionControl.recovered = true
                        insertMission(missionControl)
                    }
                }
            }
        }
    }

    override fun getMission(missionId: Int): MissionControl {
        return mMissionControls[missionId]
    }

    override val count: Int
        get() = mMissionControls.size

    private fun insertMission(missionControl: MissionControl): Int {
        var index = -1

        var mc: MissionControl

        if (mMissionControls.size > 0) {
            do {
                mc = mMissionControls[++index]
            } while (mc.mission.timestamp > missionControl.mission.timestamp && index < mMissionControls.size - 1)
        } else {
            index = 0
        }

        mMissionControls.add(index, missionControl)

        return index
    }

    /**
     * Get a mission by its location and name
     *
     * @param location the location
     * @param name     the name
     * @return the mission or null if no such mission exists
     */
    private fun getMissionByLocation(location: String, name: String): MissionControl? {
        for (missionControl in mMissionControls) {
            if (location == missionControl.mission.location && name == missionControl.mission.name) {
                return missionControl
            }
        }
        return null
    }

    private inner class Initializer(private val missionControl: MissionControl) : Thread() {
        private val handler: Handler = Handler()

        override fun run() {
            try {
                val url = URL(missionControl.mission.url)
                var conn = url.openConnection() as HttpURLConnection
                missionControl.length = conn.contentLength.toLong()

                if (missionControl.length <= 0) {
                    missionControl.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED
                    missionControl.notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED)
                    return
                }

                // Open again
                conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Range", "bytes=${missionControl.length - 10}-${missionControl.length}")

                if (conn.responseCode != 206) {
                    // Fallback to single thread if no partial content support
                    missionControl.fallback = true

                    Log.d(TAG, "falling back")
                }

                Log.d(TAG, "response = " + conn.responseCode)

                missionControl.blocks = missionControl.length / DownloadMissionManager.BLOCK_SIZE

                if (missionControl.threadCount > missionControl.blocks) {
                    missionControl.threadCount = missionControl.blocks.toInt()
                }

                if (missionControl.threadCount <= 0) {
                    missionControl.threadCount = 1
                }

                if (missionControl.blocks * DownloadMissionManager.BLOCK_SIZE < missionControl.length) {
                    missionControl.blocks = missionControl.blocks + 1
                }


                File(missionControl.mission.location).mkdirs()
                File("${missionControl.mission.location}/${missionControl.mission.name}").createNewFile()
                val af = RandomAccessFile("${missionControl.mission.location}/${missionControl.mission.name}", "rw")
                af.setLength(missionControl.length)
                af.close()

                missionControl.start()
            } catch (ie: IOException) {
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
    private inner class MissionControlListener(
            private val mMissionControl: MissionControl
    ) : org.schabi.newpipe.download.background.MissionControlListener {

        override fun onFinish(missionControl: MissionControl) {
            downloadDataSource.addMission(mMissionControl.mission)
        }

        override fun onProgressUpdate(missionControl: MissionControl, done: Long, total: Long) {
            // todo: need to show the progress on notification bar
        }


        override fun onError(missionControl: MissionControl, errCode: Int) {
            // todo: show a Toast/snackbar and remove the notification icon on state bar.
        }
    }

    companion object {
        private val TAG = DownloadMissionManagerImpl::class.java.simpleName

        /**
         * Sort a list of mission by its timestamp. Oldest first
         * @param missions the missions to sort
         */
        internal fun sortByTimestamp(missions: MutableList<MissionControl>) {
            missions.sortWith(Comparator { o1, o2 ->
                java.lang.Long.compare(o1.mission.timestamp, o2.mission.timestamp)
            })
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
