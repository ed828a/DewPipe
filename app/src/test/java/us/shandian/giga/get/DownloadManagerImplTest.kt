package us.shandian.giga.get

import org.junit.Ignore
import org.junit.Test

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.ArrayList

import us.shandian.giga.get.DownloadDataSource
import us.shandian.giga.get.DownloadManagerImpl
import us.shandian.giga.get.DownloadMission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test for [DownloadManagerImpl]
 *
 * TODO: test loading from .giga files, startMission and improve tests
 */
class DownloadManagerImplTest {

    private var downloadManager: DownloadManagerImpl? = null
    private var downloadDataSource: DownloadDataSource? = null
    private var missions: ArrayList<DownloadMission>? = null

    @org.junit.Before
    @Throws(Exception::class)
    fun setUp() {
        downloadDataSource = mock(DownloadDataSource::class.java)
        missions = ArrayList()
        for (i in 0..49) {
            missions!!.add(generateFinishedDownloadMission())
        }
        `when`(downloadDataSource!!.loadMissions()).thenReturn(ArrayList(missions!!))
        downloadManager = DownloadManagerImpl(ArrayList(), downloadDataSource!!)
    }

    @Test(expected = NullPointerException::class)
    fun testConstructorWithNullAsDownloadDataSource() {
        DownloadManagerImpl(ArrayList(), null!!)
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
        return spy(downloadMission)
    }

    private fun assertMissionEquals(message: String, expected: DownloadMission, actual: DownloadMission) {
        if (expected == actual) return
        assertEquals("$message: Name", expected.name, actual.name)
        assertEquals("$message: Location", expected.location, actual.location)
        assertEquals("$message: Url", expected.url, actual.url)
    }

    @Test
    @Throws(IOException::class)
    fun testThatMissionsAreLoaded() {
        val missions = ArrayList<DownloadMission>()
        val millis = System.currentTimeMillis()
        for (i in 0..49) {
            val mission = generateFinishedDownloadMission()
            mission.timestamp = millis - i // reverse order by timestamp
            missions.add(mission)
        }

        downloadDataSource = mock(DownloadDataSource::class.java)
        `when`(downloadDataSource!!.loadMissions()).thenReturn(ArrayList(missions))
        downloadManager = DownloadManagerImpl(ArrayList(), downloadDataSource!!)
        verify<DownloadDataSource>(downloadDataSource, times(1)).loadMissions()

        assertEquals(50, downloadManager!!.count.toLong())

        for (i in 0..49) {
            assertMissionEquals("mission $i", missions[50 - 1 - i], downloadManager!!.getMission(i))
        }
    }

    @Ignore
    @Test
    @Throws(Exception::class)
    fun startMission() {
        var mission = missions!![0]
        mission = spy(mission)
        missions!![0] = mission
        val url = "https://github.com/favicon.ico"
        // create a temp file and delete it so we have a temp directory
        val tempFile = File.createTempFile("favicon", ".ico")
        val name = tempFile.name
        val location = tempFile.parent
        assertTrue(tempFile.delete())
        val id = downloadManager!!.startMission(url, location, name, true, 10)
    }

    @Test
    fun resumeMission() {
        val mission = missions!![0]
        mission.running = true
        verify(mission, never()).start()
        downloadManager!!.resumeMission(0)
        verify(mission, never()).start()
        mission.running = false
        downloadManager!!.resumeMission(0)
        verify(mission, times(1)).start()
    }

    @Test
    fun pauseMission() {
        val mission = missions!![0]
        mission.running = false
        downloadManager!!.pauseMission(0)
        verify(mission, never()).pause()
        mission.running = true
        downloadManager!!.pauseMission(0)
        verify(mission, times(1)).pause()
    }

    @Test
    fun deleteMission() {
        val mission = missions!![0]
        assertEquals(mission, downloadManager!!.getMission(0))
        downloadManager!!.deleteMission(0)
        verify(mission, times(1)).delete()
        assertNotEquals(mission, downloadManager!!.getMission(0))
        assertEquals(49, downloadManager!!.count.toLong())
    }

    @Test(expected = RuntimeException::class)
    fun getMissionWithNegativeIndex() {
        downloadManager!!.getMission(-1)
    }

    @Test
    fun getMission() {
        assertSame(missions!![0], downloadManager!!.getMission(0))
        assertSame(missions!![1], downloadManager!!.getMission(1))
    }

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

        assertEquals(mission, downloadMissions[0])
        assertEquals(mission1, downloadMissions[1])
        assertEquals(mission2, downloadMissions[2])
        assertEquals(mission3, downloadMissions[3])
    }

}