package org.schabi.newpipe.player.playqueue

import android.os.Parcel
import android.os.Parcelable
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

import java.io.Serializable

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.util.ExtractorHelper

//class PlayQueueItem private constructor(name: String?,
//                                        url: String?,
//                                        val serviceId: Int,
//                                        val duration: Long,
//                                        thumbnailUrl: String?,
//                                        uploader: String?,
//                                        val streamType: StreamType) : Parcelable {
//
//    val title: String
//    val url: String
//    val thumbnailUrl: String
//    val uploader: String
//    var recoveryPosition: Long = 0
//    var error: Throwable? = null
//
//
//    init {
//        this.title = name ?: EMPTY_STRING
//        this.url = url ?: EMPTY_STRING
//        this.thumbnailUrl = thumbnailUrl ?: EMPTY_STRING
//        this.uploader = uploader ?: EMPTY_STRING
//
//        this.recoveryPosition = RECOVERY_UNSET
//    }
//
//    constructor(parcel: Parcel) : this(
//            parcel.readString(),
//            parcel.readString(),
//            parcel.readInt(),
//            parcel.readLong(),
//            parcel.readString(),
//            parcel.readString(),
//            StreamType.valueOf(parcel.readString())) {
//        recoveryPosition = parcel.readLong()
//    }
//
//    constructor(info: StreamInfo) : this(info.name, info.url, info.serviceId, info.duration,
//            info.thumbnailUrl, info.uploaderName, info.streamType) {
//
//        if (info.startPosition > 0)
//            recoveryPosition = info.startPosition * 1000
//    }
//
//    constructor(item: StreamInfoItem) : this(item.name, item.url, item.serviceId, item.duration,
//            item.thumbnailUrl, item.uploaderName, item.streamType) {
//    }
//
//    override fun writeToParcel(parcel: Parcel, flags: Int) {
//        parcel.writeString(title)
//        parcel.writeString(url)
//        parcel.writeInt(serviceId)
//        parcel.writeLong(duration)
//        parcel.writeString(thumbnailUrl)
//        parcel.writeString(uploader)
//        parcel.writeString(streamType.name)
//        parcel.writeLong(recoveryPosition)
//    }
//
//    val stream: Single<StreamInfo>
//        get() = ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
//                .subscribeOn(Schedulers.io())
//                .doOnError { throwable -> error = throwable }
//
//    override fun describeContents(): Int {
//        return 0
//    }
//
//    companion object {
//        const val RECOVERY_UNSET = java.lang.Long.MIN_VALUE
//        private const val EMPTY_STRING = ""
//
//        @JvmField
//        val CREATOR: Parcelable.Creator<PlayQueueItem> = object : Parcelable.Creator<PlayQueueItem> {
//            override fun createFromParcel(parcel: Parcel): PlayQueueItem {
//                return PlayQueueItem(parcel)
//            }
//
//            override fun newArray(size: Int): Array<PlayQueueItem?> {
//                return arrayOfNulls(size)
//            }
//        }
//    }
//}


// Todo: temporarily comment off, testing parcelabled PlayQueueItem
class PlayQueueItem private constructor(name: String?, url: String?,
                                        val serviceId: Int, val duration: Long,
                                        thumbnailUrl: String?, uploader: String?,
                                        val streamType: StreamType) : Serializable {

    val title: String
    val url: String
    val thumbnailUrl: String
    val uploader: String

    ////////////////////////////////////////////////////////////////////////////
    // Item States, keep external access out
    ////////////////////////////////////////////////////////////////////////////

    /*package-private*/
    var recoveryPosition: Long = 0

    var error: Throwable? = null

    val stream: Single<StreamInfo>
        get() = ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .doOnError { throwable -> error = throwable }

    constructor(info: StreamInfo) : this(info.name, info.url, info.serviceId, info.duration,
            info.thumbnailUrl, info.uploaderName, info.streamType) {

        if (info.startPosition > 0)
            recoveryPosition = info.startPosition * 1000
    }

    constructor(item: StreamInfoItem) : this(item.name, item.url, item.serviceId, item.duration,
            item.thumbnailUrl, item.uploaderName, item.streamType) {}

    init {
        this.title = name ?: EMPTY_STRING
        this.url = url ?: EMPTY_STRING
        this.thumbnailUrl = thumbnailUrl ?: EMPTY_STRING
        this.uploader = uploader ?: EMPTY_STRING

        this.recoveryPosition = RECOVERY_UNSET
    }

    companion object {
        const val RECOVERY_UNSET = java.lang.Long.MIN_VALUE
        private const val EMPTY_STRING = ""
    }
}
