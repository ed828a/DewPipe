package org.schabi.newpipe.background

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.download.downloadDB.DownloadDAO
import org.schabi.newpipe.download.downloadDB.MissionEntry
import org.schabi.newpipe.download.giga.get.DownloadMission
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Created by Edward on 12/30/2018.
 */

@RunWith(AndroidJUnit4::class)
class DownloadDatabaseTest {
    private lateinit var downloadDataSource: DownloadDAO
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        
        val context = InstrumentationRegistry.getContext()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        downloadDataSource = db.downloadDAO()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Throws(IOException::class)
    private fun generateFinishedDownloadMission(): DownloadMission {
        val file = File.createTempFile("newpipetest", ".mp4")
        file.deleteOnExit()
        val randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile.setLength(1000)
        randomAccessFile.close()
        val downloadMission = DownloadMission(file.name,
                "http://google.com/?q=how+to+google", file.parent)
        downloadMission.blocks = 1000
        downloadMission.done = 1000
        downloadMission.finished = true
        return downloadMission
    }

    @Test
    @Throws(Exception::class)
    fun addMissionTest() {
        val missionEntry = MissionEntry("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        for (i in 0 .. 49) {
            downloadDataSource.addMission(missionEntry)
        }

        val obtainFromDb = downloadDataSource.loadMissions()
        obtainFromDb.forEach {
            assertMissionEntryEquals(missionEntry, it)
        }
        Log.d(TAG, "size of List<MissionEntry> : ${obtainFromDb.size}")
    }

    private fun assertMissionEntryEquals(first: MissionEntry, second: MissionEntry){
        assertEquals(first.name, second.name)
        assertEquals(first.url, second.url)
        assertEquals(first.location, second.location)
    }

    @Test
    @Throws(Exception::class)
    fun updateMissionsTest() {
        val missionEntry = MissionEntry("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntry)
        missionEntry.url = "noUrl"
        missionEntry.done = 100L
        missionEntry.timestamp = 19398749L
        DownloadDAO.updateMission(missionEntry, downloadDataSource)

        val obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntry> : ${obtainFromDb.size}")
        obtainFromDb.forEach {
            Log.d(TAG, "obtainFromDb: $it")
            assertMissionEntryEquals(missionEntry, it)
        }
    }

    @Test
    @Throws(Exception::class)
    fun loadMissionsTest() {
        val missionEntry = MissionEntry("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntry)
        val obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntry : ${obtainFromDb.size}")

        obtainFromDb.forEach {
            assertMissionEntryEquals(missionEntry, it)
            Log.d(TAG, "obtainFromDb: $it")
        }
    }


    @Test
    @Throws(Exception::class)
    fun deleteMissionsTest() {
        val missionEntry = MissionEntry("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntry)
        var obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntry : ${obtainFromDb.size}")

        downloadDataSource.deleteMission(missionEntry)
        obtainFromDb = downloadDataSource.loadMissions()

        obtainFromDb.forEach {
            assertMissionEntryEquals(missionEntry, it)
            Log.d(TAG, "obtainFromDb: $it")
        }
    }

    companion object {
        const val TAG = "DownloadDatabseTest"
    }
}