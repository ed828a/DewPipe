package org.schabi.newpipe.report

import android.os.Parcel
import android.support.test.filters.LargeTest
import android.support.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.report.ErrorActivity.ErrorInfo

import org.junit.Assert.assertEquals

/**
 * Instrumented tests for [ErrorInfo]
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ErrorInfoTest {

    @Test
    fun errorInfo_testParcelable() {
        val info = ErrorInfo.make(UserAction.USER_REPORT, "youtube", "request", R.string.general_error)
        // Obtain a Parcel object and write the parcelable object to it:
        val parcel = Parcel.obtain()
        info.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val infoFromParcel = ErrorInfo.CREATOR.createFromParcel(parcel)

        assertEquals(UserAction.USER_REPORT, infoFromParcel.userAction)
        assertEquals("youtube", infoFromParcel.serviceName)
        assertEquals("request", infoFromParcel.request)
        assertEquals(R.string.general_error.toLong(), infoFromParcel.message.toLong())

        parcel.recycle()
    }
}