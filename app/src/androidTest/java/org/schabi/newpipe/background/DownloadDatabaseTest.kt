package org.schabi.newpipe.background

import android.arch.persistence.room.Room
import android.content.Context
import android.support.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.ScriptRuntime.setName
import org.schabi.newpipe.download.downloadDB.DownloadDAO
import org.schabi.newpipe.download.downloadDB.DownloadDatabase
import java.io.IOException

/**
 * Created by Edward on 12/30/2018.
 */

@RunWith(AndroidJUnit4::class)
class SimpleEntityReadWriteTest {
    private lateinit var downloadDataSource: DownloadDAO
    private lateinit var db: DownloadDatabase

    @Before
    fun createDb() {
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, TestDatabase::class.java).build()
        userDao = db.getUserDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeUserAndReadInList() {
        val user: User = TestUtil.createUser(3).apply {
            setName("george")
        }
        userDao.insert(user)
        val byName = userDao.findUsersByName("george")
        assertThat(byName.get(0), equalTo(user))
    }
}