package org.schabi.newpipe.background

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.room.Room
import androidx.test.InstrumentationRegistry
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.downloadDB.DownloadDAO
import org.schabi.newpipe.database.downloadDB.MissionEntity
import org.schabi.newpipe.download.background.DownloadMissionManagerImpl
import org.schabi.newpipe.download.background.MissionControl
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*

/**
 * Created by Edward on 12/30/2018.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DownloadMissionManagerImplTest {
    private lateinit var downloadMissionManager: DownloadMissionManagerImpl
    private val missions: ArrayList<MissionControl> = ArrayList()
    private lateinit var context: Context
    private lateinit var downloadDataSource: DownloadDAO
    private lateinit var db: AppDatabase

    @org.junit.Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getContext()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        downloadDataSource = db.downloadDAO()

        for (i in 0..49) {
            missions.add(generateFinishedDownloadMissionControl())
        }
        Log.d(TAG, "context = $context")
        downloadMissionManager = DownloadMissionManagerImpl(ArrayList(), context)

        Looper.prepare()
    }

//    @Test(expected = NullPointerException::class)
    @Test
    fun testConstructor() {
        Log.d(TAG, "context = $context")
        DownloadMissionManagerImpl(ArrayList(), context)
    }


    @Throws(IOException::class)
    private fun generateFinishedDownloadMissionControl(): MissionControl {
        val file = File.createTempFile("newpipetest", ".mp4")
        file.deleteOnExit()
        val randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile.setLength(1000)
        randomAccessFile.close()
        val downloadMission = MissionControl(
                MissionEntity(file.name,
                        "http://google.com/?q=how+to+google",
                        file.parent))
        downloadMission.blocks = 1000
        downloadMission.mission.done = 1000
        downloadMission.finished = true
        return downloadMission
    }

    private fun assertMissionEquals(message: String, expected: MissionControl, actual: MissionControl) {
        if (expected == actual) return
        Assert.assertEquals("$message: Name", expected.mission.name, actual.mission.name)
        Assert.assertEquals("$message: Location", expected.mission.location, actual.mission.location)
        Assert.assertEquals("$message: Url", expected.mission.url, actual.mission.url)
    }

    @Test
    @Throws(IOException::class)
    fun testThatMissionsAreLoaded() {
        val missionEntity = MissionEntity("testFile.ept",
                "http://google.com/?q=how+to+google",
                "West Aust")

        val millis = System.currentTimeMillis()
        for (i in 0..49) {
            missionEntity.timestamp = millis - i // reverse order by timestamp
            missionEntity.done = 1000
            missionEntity.name += i.toString()
            downloadDataSource.addMission(missionEntity)
        }

        downloadMissionManager = DownloadMissionManagerImpl(ArrayList(), context)

        Assert.assertEquals(0, downloadMissionManager.count.toLong())
    }


    @Test
    @Throws(Exception::class)
    fun startMissionTest() {
        var mission = missions[0]

        val url = "https://github.com/favicon.ico"
        // create a temp file and delete it so we have a temp directory
        val tempFile = File.createTempFile("favicon", ".ico")
        val name = tempFile.name
        val location = tempFile.parent
        Assert.assertTrue(tempFile.delete())
        val id = downloadMissionManager.startMission(url, location, name, true, 10)
        Log.d(TAG, "New Mission id: $id ")
    }

    @Test
    fun resumeMissionTest() {
        var mission = missions[0]
//        missionControl = Mockito.spy(missionControl) // we have a problem here.
        mission.running = true
//        Mockito.verify(missionControl, Mockito.never()).start()
        downloadMissionManager.resumeMission(0)
//        Mockito.verify(missionControl, Mockito.never()).start()
        mission.running = false
        downloadMissionManager.resumeMission(0)
//        Mockito.verify(missionControl, Mockito.times(1)).start()
    }

    @Test
    fun pauseMissionTest() {
        val mission = missions[0]
        mission.running = false
        downloadMissionManager.pauseMission(0)
        Mockito.verify(mission, Mockito.never()).pause()
        mission.running = true
        downloadMissionManager.pauseMission(0)
        Mockito.verify(mission, Mockito.times(1)).pause()
    }

    @Test
    fun deleteMissionTest() {
        val mission = missions[0]
        Assert.assertEquals(mission, downloadMissionManager.getMission(0))
        downloadMissionManager.deleteMission(0)
        Mockito.verify(mission, Mockito.times(1)).delete()
        Assert.assertNotEquals(mission, downloadMissionManager.getMission(0))
        Assert.assertEquals(49, downloadMissionManager.count.toLong())
    }

    @Test(expected = RuntimeException::class)
    fun getMissionWithNegativeIndex() {
        downloadMissionManager.getMission(-1)
    }

    @Test
    fun getMission() {
        val missionFromDownloadMissionManager = downloadMissionManager.getMission(0)
        Log.d(TAG, "missionFromDMM: $missionFromDownloadMissionManager")
//        Assert.assertSame(missions[0], downloadMissionManager.getMissionControl(0))
//        Assert.assertSame(missions[1], downloadMissionManager.getMissionControl(1))
    }

    @Test
    fun sortByTimestamp() {
        val downloadMissions = ArrayList<MissionControl>()
        val missionControl = MissionControl(MissionEntity())
        missionControl.mission.timestamp = 0

        val missionControl1 = MissionControl(MissionEntity())
        missionControl1.mission.timestamp = Integer.MAX_VALUE + 1L

        val missionControl2 = MissionControl(MissionEntity())
        missionControl2.mission.timestamp = 2L * Integer.MAX_VALUE

        val missionControl3 = MissionControl(MissionEntity())
        missionControl3.mission.timestamp = 2L * Integer.MAX_VALUE + 5L


        downloadMissions.add(missionControl3)
        downloadMissions.add(missionControl1)
        downloadMissions.add(missionControl2)
        downloadMissions.add(missionControl)


        DownloadMissionManagerImpl.sortByTimestamp(downloadMissions)

        Assert.assertEquals(missionControl, downloadMissions[0])
        Assert.assertEquals(missionControl1, downloadMissions[1])
        Assert.assertEquals(missionControl2, downloadMissions[2])
        Assert.assertEquals(missionControl3, downloadMissions[3])
    }

    companion object {
    const val TAG = "DownloadMissionManagerImplTest"
    }
}