package org.schabi.newpipe.player.playqueue

import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ExtractorHelper

import java.io.Serializable

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class PlayQueueItem private constructor(name: String?,
                                        url: String?,
                                        val serviceId: Int,
                                        val duration: Long,
                                        thumbnailUrl: String?,
                                        uploader: String?,
                                        val streamType: StreamType) : Serializable {

    val title: String
    val url: String
    val thumbnailUrl: String
    val uploader: String
    var recoveryPosition: Long = 0
        internal set

    init {
        this.title = name ?: EMPTY_STRING
        this.url = url ?: EMPTY_STRING
        this.thumbnailUrl = thumbnailUrl ?: EMPTY_STRING
        this.uploader = uploader ?: EMPTY_STRING

        this.recoveryPosition = RECOVERY_UNSET
    }


    var isAutoQueued: Boolean = false

    var error: Throwable? = null
        private set

    val stream: Single<StreamInfo>
        get() = ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .doOnError { throwable -> error = throwable }

    internal constructor(info: StreamInfo) : this(info.name, info.url, info.serviceId, info.duration,
            info.thumbnailUrl, info.uploaderName, info.streamType) {

        if (info.startPosition > 0)
            recoveryPosition = info.startPosition * 1000
    }

    internal constructor(item: StreamInfoItem) : this(item.name, item.url, item.serviceId, item.duration,
            item.thumbnailUrl, item.uploaderName, item.streamType) {}

    companion object {
        const val RECOVERY_UNSET = java.lang.Long.MIN_VALUE
        private const val EMPTY_STRING = ""
    }
}
