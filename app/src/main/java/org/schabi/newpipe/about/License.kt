package org.schabi.newpipe.about

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * A software license
 */
class License : Parcelable {
    val abbreviation: String?
    val name: String?
    var filename: String? = null
        private set

    val contentUri: Uri
        get() = Uri.Builder()
                .scheme("file")
                .path("/android_asset")
                .appendPath(filename)
                .build()

    constructor(name: String?, abbreviation: String?, filename: String?) {
        if (name == null) throw NullPointerException("name is null")
        if (abbreviation == null) throw NullPointerException("abbreviation is null")
        if (filename == null) throw NullPointerException("filename is null")
        this.name = name
        this.filename = filename
        this.abbreviation = abbreviation
    }

    protected constructor(`in`: Parcel) {
        this.filename = `in`.readString()
        this.abbreviation = `in`.readString()
        this.name = `in`.readString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(this.filename)
        dest.writeString(this.abbreviation)
        dest.writeString(this.name)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<License> = object : Parcelable.Creator<License> {
            override fun createFromParcel(source: Parcel): License {
                return License(source)
            }

            override fun newArray(size: Int): Array<License?> {
                return arrayOfNulls(size)
            }
        }
    }
}
