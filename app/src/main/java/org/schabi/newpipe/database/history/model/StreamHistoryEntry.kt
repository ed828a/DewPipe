package org.schabi.newpipe.database.history.model

import android.arch.persistence.room.ColumnInfo
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.*

class StreamHistoryEntry(
        @field:ColumnInfo(name = StreamEntity.STREAM_ID) val uid: Long,
        @field:ColumnInfo(name = StreamEntity.STREAM_SERVICE_ID) val serviceId: Int,
        @field:ColumnInfo(name = StreamEntity.STREAM_URL) val url: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_TITLE) val title: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_TYPE) val streamType: StreamType,
        @field:ColumnInfo(name = StreamEntity.STREAM_DURATION) val duration: Long,
        @field:ColumnInfo(name = StreamEntity.STREAM_UPLOADER) val uploader: String,
        @field:ColumnInfo(name = StreamEntity.STREAM_THUMBNAIL_URL) val thumbnailUrl: String,
        @field:ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID) val streamId: Long,
        @field:ColumnInfo(name = StreamHistoryEntity.STREAM_ACCESS_DATE) val accessDate: Date,
        @field:ColumnInfo(name = StreamHistoryEntity.STREAM_REPEAT_COUNT) val repeatCount: Long) {

    fun toStreamHistoryEntity(): StreamHistoryEntity {
        return StreamHistoryEntity(streamId, accessDate, repeatCount)
    }

    fun hasEqualValues(other: StreamHistoryEntry): Boolean =
            this.uid == other.uid && streamId == other.streamId && accessDate.compareTo(other.accessDate) == 0

}
