package org.schabi.newpipe.database.stream.model

import androidx.room.ColumnInfo
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.*

class StreamStatisticsEntry(
        @field:ColumnInfo(name = StreamEntity.STREAM_ID) val uid: Long,
        @field:ColumnInfo(name = StreamEntity.STREAM_SERVICE_ID) val serviceId: Int,
        @field:ColumnInfo(name = StreamEntity.STREAM_URL) val url: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_TITLE) val title: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_TYPE) val streamType: StreamType,
        @field:ColumnInfo(name = StreamEntity.STREAM_DURATION) val duration: Long,
        @field:ColumnInfo(name = StreamEntity.STREAM_UPLOADER) val uploader: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_THUMBNAIL_URL) val thumbnailUrl: String,
        @field:ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID) val streamId: Long,
        @field:ColumnInfo(name = STREAM_LATEST_DATE) val latestAccessDate: Date,
        @field:ColumnInfo(name = STREAM_WATCH_COUNT) val watchCount: Long
) : LocalItem {

    override val localItemType: LocalItem.LocalItemType
        get() = LocalItem.LocalItemType.STATISTIC_STREAM_ITEM

    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, title, streamType)
        item.duration = duration
        item.uploaderName = uploader
        item.thumbnailUrl = thumbnailUrl
        return item
    }

    companion object {
        const val STREAM_LATEST_DATE = "latestAccess"
        const val STREAM_WATCH_COUNT = "watchCount"
    }
}
