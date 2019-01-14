package org.schabi.newpipe.database.stream.model


import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.STREAM_STATE_TABLE

@Entity(tableName = STREAM_STATE_TABLE,
        primaryKeys = [JOIN_STREAM_ID],
        foreignKeys = [ForeignKey(entity = StreamEntity::class,
                parentColumns = arrayOf(StreamEntity.STREAM_ID),
                childColumns = arrayOf(JOIN_STREAM_ID),
                onDelete = CASCADE,
                onUpdate = CASCADE)])
class StreamStateEntity(@field:ColumnInfo(name = JOIN_STREAM_ID) var streamUid: Long,
                        @field:ColumnInfo(name = STREAM_PROGRESS_TIME) var progressTime: Long) {
    companion object {
        const val STREAM_STATE_TABLE = "stream_state"
        const val JOIN_STREAM_ID = "stream_id"
        const val STREAM_PROGRESS_TIME = "progress_time"
    }
}
