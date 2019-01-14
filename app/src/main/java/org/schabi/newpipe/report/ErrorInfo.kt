package org.schabi.newpipe.report

import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.StringRes

/**
 * Created by Edward on 1/12/2019.
 */

data class ErrorInfo(val userAction: UserAction, val serviceName: String?, val request: String?, @StringRes val message: Int) : Parcelable {


    constructor(parcel: Parcel) : this(
            userAction = UserAction.valueOf(parcel.readString()!!),
            request = parcel.readString(),
            serviceName = parcel.readString(),
            message = parcel.readInt()
    )

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(userAction.name)
        dest.writeString(request)
        dest.writeString(serviceName)
        dest.writeInt(message)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ErrorInfo> = object : Parcelable.Creator<ErrorInfo> {
            override fun createFromParcel(source: Parcel): ErrorInfo {
                return ErrorInfo(source)
            }

            override fun newArray(size: Int): Array<ErrorInfo?> {
                return arrayOfNulls(size)
            }
        }

        fun make(userAction: UserAction, serviceName: String, request: String, @StringRes message: Int): ErrorInfo {
            return ErrorInfo(userAction, serviceName, request, message)
        }
    }
}