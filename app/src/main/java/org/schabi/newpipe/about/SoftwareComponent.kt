package org.schabi.newpipe.about

import android.os.Parcel
import android.os.Parcelable

class SoftwareComponent : Parcelable {

    val license: License?
    val name: String?
    val years: String?
    val copyrightOwner: String?
    val link: String?
    val version: String?

    constructor(name: String, years: String, copyrightOwner: String, link: String, license: License) {
        this.name = name
        this.years = years
        this.copyrightOwner = copyrightOwner
        this.link = link
        this.license = license
        this.version = null
    }

    protected constructor(`in`: Parcel) {
        this.name = `in`.readString()
        this.license = `in`.readParcelable(License::class.java.classLoader)
        this.copyrightOwner = `in`.readString()
        this.link = `in`.readString()
        this.years = `in`.readString()
        this.version = `in`.readString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeParcelable(license, flags)
        dest.writeString(copyrightOwner)
        dest.writeString(link)
        dest.writeString(years)
        dest.writeString(version)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SoftwareComponent> = object : Parcelable.Creator<SoftwareComponent> {
            override fun createFromParcel(source: Parcel): SoftwareComponent {
                return SoftwareComponent(source)
            }

            override fun newArray(size: Int): Array<SoftwareComponent?> {
                return arrayOfNulls(size)
            }
        }
    }
}
