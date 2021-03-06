package org.schabi.newpipe.report

import android.app.Activity

import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.fragments.detail.VideoDetailFragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Unit tests for [ErrorActivity]
 */
class ErrorActivityTest {
    @Test
    fun getReturnActivity() {
        var returnActivity: Class<out Activity>? = ErrorActivity.getReturnActivity(MainActivity::class.java)
        assertEquals(MainActivity::class.java, returnActivity)

        returnActivity = ErrorActivity.getReturnActivity(RouterActivity::class.java)
        assertEquals(RouterActivity::class.java, returnActivity)

        returnActivity = ErrorActivity.getReturnActivity(null)
        assertNull(returnActivity)

        returnActivity = ErrorActivity.getReturnActivity(Int::class.java)
        assertEquals(MainActivity::class.java, returnActivity)

        returnActivity = ErrorActivity.getReturnActivity(VideoDetailFragment::class.java)
        assertEquals(MainActivity::class.java, returnActivity)
    }


}