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
import org.schabi.newpipe.download.downloadDB.MissionEntity
import java.io.IOException

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


    @Test
    @Throws(Exception::class)
    fun addMissionTest() {
        val missionEntity = MissionEntity("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        for (i in 0 .. 49) {
            missionEntity.timestamp = System.currentTimeMillis() - i

            downloadDataSource.addMission(missionEntity)
        }

        val obtainFromDb = downloadDataSource.loadMissions()
        obtainFromDb.forEach {
            assertMissionEntityEquals(missionEntity, it)
        }
        Log.d(TAG, "size of List<MissionEntity> : ${obtainFromDb.size}")
    }

    private fun assertMissionEntityEquals(first: MissionEntity, second: MissionEntity){
        assertEquals(first.name, second.name)
        assertEquals(first.url, second.url)
        assertEquals(first.location, second.location)
    }

    @Test
    @Throws(Exception::class)
    fun updateMissionsTest() {
        val missionEntity = MissionEntity("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntity)
        missionEntity.url = "noUrl"
        missionEntity.done = 100L
        missionEntity.timestamp = 19398749L
        DownloadDAO.updateMission(missionEntity, downloadDataSource)

        val obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntity> : ${obtainFromDb.size}")
        obtainFromDb.forEach {
            Log.d(TAG, "obtainFromDb: $it")
            assertMissionEntityEquals(missionEntity, it)
        }
    }

    @Test
    @Throws(Exception::class)
    fun loadMissionsTest() {
        val missionEntity = MissionEntity("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntity)
        val obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntity : ${obtainFromDb.size}")

        obtainFromDb.forEach {
            assertMissionEntityEquals(missionEntity, it)
            Log.d(TAG, "obtainFromDb: $it")
        }
    }


    @Test
    @Throws(Exception::class)
    fun deleteMissionsTest() {
        val missionEntity = MissionEntity("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")
        downloadDataSource.addMission(missionEntity)
        var obtainFromDb = downloadDataSource.loadMissions()
        Log.d(TAG, "size of List<MissionEntity : ${obtainFromDb.size}")

        downloadDataSource.deleteMission(missionEntity)
        obtainFromDb = downloadDataSource.loadMissions()

        obtainFromDb.forEach {
            assertMissionEntityEquals(missionEntity, it)
            Log.d(TAG, "obtainFromDb: $it")
        }
    }

    companion object {
        const val TAG = "DownloadDatabaseTest"
    }
}