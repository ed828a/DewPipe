package org.schabi.newpipe.background

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.filters.LargeTest
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.download.background.DownloadMissionManagerImpl
import org.schabi.newpipe.download.giga.get.DownloadDataSource
import org.schabi.newpipe.download.giga.get.DownloadManagerImpl
import org.schabi.newpipe.download.giga.get.DownloadMission
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.ArrayList

/**
 * Created by Edward on 12/30/2018.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DownloadMissionManagerImplTest {
    private var downloadMissionManager: DownloadMissionManagerImpl? = null
    private var missions: ArrayList<DownloadMission>? = null
    private lateinit var context: Context


    @org.junit.Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getContext()
        missions = ArrayList()
        for (i in 0..49) {
            missions!!.add(generateFinishedDownloadMission())
        }
        downloadMissionManager = DownloadMissionManagerImpl(ArrayList(), context)
    }

    @Test(expected = NullPointerException::class)
    fun testConstructor() {
        DownloadMissionManagerImpl(ArrayList(), context)
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

    private fun assertMissionEquals(message: String, expected: DownloadMission, actual: DownloadMission) {
        if (expected == actual) return
        Assert.assertEquals("$message: Name", expected.name, actual.name)
        Assert.assertEquals("$message: Location", expected.location, actual.location)
        Assert.assertEquals("$message: Url", expected.url, actual.url)
    }

//    @Test
//    @Throws(IOException::class)
//    fun testThatMissionsAreLoaded() {
//        val missions = ArrayList<DownloadMission>()
//        val millis = System.currentTimeMillis()
//        for (i in 0..49) {
//            val mission = generateFinishedDownloadMission()
//            mission.timestamp = millis - i // reverse order by timestamp
//            missions.add(mission)
//        }
//
//        downloadDataSource = Mockito.mock(DownloadDataSource::class.java)
//        Mockito.`when`(downloadDataSource!!.loadMissions()).thenReturn(ArrayList(missions))
//        downloadManager = DownloadManagerImpl(ArrayList(), downloadDataSource!!)
//        Mockito.verify<DownloadDataSource>(downloadDataSource, Mockito.times(1)).loadMissions()
//
//        Assert.assertEquals(50, downloadManager!!.count.toLong())
//
//        for (i in 0..49) {
//            assertMissionEquals("mission $i", missions[50 - 1 - i], downloadManager!!.getMission(i))
//        }
//    }

//    @Ignore
//    @Test
//    @Throws(Exception::class)
//    fun startMission() {
//        var mission = missions!![0]
//        mission = Mockito.spy(mission)
//        missions!![0] = mission
//        val url = "https://github.com/favicon.ico"
//        // create a temp file and delete it so we have a temp directory
//        val tempFile = File.createTempFile("favicon", ".ico")
//        val name = tempFile.name
//        val location = tempFile.parent
//        Assert.assertTrue(tempFile.delete())
//        val id = downloadManager!!.startMission(url, location, name, true, 10)
//    }
//
//    @Test
//    fun resumeMission() {
//        val mission = missions!![0]
//        mission.running = true
//        Mockito.verify(mission, Mockito.never()).start()
//        downloadManager!!.resumeMission(0)
//        Mockito.verify(mission, Mockito.never()).start()
//        mission.running = false
//        downloadManager!!.resumeMission(0)
//        Mockito.verify(mission, Mockito.times(1)).start()
//    }
//
//    @Test
//    fun pauseMission() {
//        val mission = missions!![0]
//        mission.running = false
//        downloadManager!!.pauseMission(0)
//        Mockito.verify(mission, Mockito.never()).pause()
//        mission.running = true
//        downloadManager!!.pauseMission(0)
//        Mockito.verify(mission, Mockito.times(1)).pause()
//    }
//
//    @Test
//    fun deleteMission() {
//        val mission = missions!![0]
//        Assert.assertEquals(mission, downloadManager!!.getMission(0))
//        downloadManager!!.deleteMission(0)
//        Mockito.verify(mission, Mockito.times(1)).delete()
//        Assert.assertNotEquals(mission, downloadManager!!.getMission(0))
//        Assert.assertEquals(49, downloadManager!!.count.toLong())
//    }
//
//    @Test(expected = RuntimeException::class)
//    fun getMissionWithNegativeIndex() {
//        downloadManager!!.getMission(-1)
//    }
//
//    @Test
//    fun getMission() {
//        Assert.assertSame(missions!![0], downloadManager!!.getMission(0))
//        Assert.assertSame(missions!![1], downloadManager!!.getMission(1))
//    }

    @Test
    fun sortByTimestamp() {
        val downloadMissions = ArrayList<DownloadMission>()
        val mission = DownloadMission()
        mission.timestamp = 0

        val mission1 = DownloadMission()
        mission1.timestamp = Integer.MAX_VALUE + 1L

        val mission2 = DownloadMission()
        mission2.timestamp = 2L * Integer.MAX_VALUE

        val mission3 = DownloadMission()
        mission3.timestamp = 2L * Integer.MAX_VALUE + 5L


        downloadMissions.add(mission3)
        downloadMissions.add(mission1)
        downloadMissions.add(mission2)
        downloadMissions.add(mission)


        DownloadManagerImpl.sortByTimestamp(downloadMissions)

        Assert.assertEquals(mission, downloadMissions[0])
        Assert.assertEquals(mission1, downloadMissions[1])
        Assert.assertEquals(mission2, downloadMissions[2])
        Assert.assertEquals(mission3, downloadMissions[3])
    }

}