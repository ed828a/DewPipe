package org.schabi.newpipe.report

import android.os.Parcel
import androidx.test.InstrumentationRegistry
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

/**
 * Instrumented tests for [ErrorInfo]
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ErrorInfoTest {

    private val context = InstrumentationRegistry.getContext()

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