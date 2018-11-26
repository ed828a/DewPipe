package org.schabi.newpipe.database.history.model

import android.arch.persistence.room.*
import android.arch.persistence.room.ForeignKey.CASCADE
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.STREAM_ACCESS_DATE
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.Companion.STREAM_HISTORY_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.util.*

@Entity(tableName = STREAM_HISTORY_TABLE,
        primaryKeys = [JOIN_STREAM_ID, STREAM_ACCESS_DATE],
        indices = [Index(value = arrayOf(JOIN_STREAM_ID))],
        foreignKeys = [ForeignKey(entity = StreamEntity::class, parentColumns = arrayOf(StreamEntity.STREAM_ID),
                childColumns = arrayOf(JOIN_STREAM_ID), onDelete = CASCADE, onUpdate = CASCADE)])
class StreamHistoryEntity(@field:ColumnInfo(name = JOIN_STREAM_ID)
                          var streamUid: Long, @field:ColumnInfo(name = STREAM_ACCESS_DATE)
                          var accessDate: Date, @field:ColumnInfo(name = STREAM_REPEAT_COUNT)
                          var repeatCount: Long) {

    @Ignore
    constructor(streamUid: Long, accessDate: Date) : this(streamUid, accessDate, 1) {}

    companion object {
        const val STREAM_HISTORY_TABLE = "stream_history"
        const val JOIN_STREAM_ID = "stream_id"
        const val STREAM_ACCESS_DATE = "access_date"
        const val STREAM_REPEAT_COUNT = "repeat_count"
    }
}
