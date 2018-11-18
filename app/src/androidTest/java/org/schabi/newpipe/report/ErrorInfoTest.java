package org.schabi.newpipe.report;

import android.os.Parcel;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.report.ErrorActivity.ErrorInfo;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented tests for {@link ErrorInfo}
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ErrorInfoTest {

    @Test
    public void errorInfo_testParcelable() {
        ErrorInfo info = ErrorInfo.Companion.make(UserAction.USER_REPORT, "youtube", "request", R.string.general_error);
        // Obtain a Parcel object and write the parcelable object to it:
        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ErrorInfo infoFromParcel = ErrorInfo.Companion.getCREATOR().createFromParcel(parcel);

        assertEquals(UserAction.USER_REPORT, infoFromParcel.getUserAction());
        assertEquals("youtube", infoFromParcel.getServiceName());
        assertEquals("request", infoFromParcel.getRequest());
        assertEquals(R.string.general_error, infoFromParcel.getMessage());

        parcel.recycle();
    }
}