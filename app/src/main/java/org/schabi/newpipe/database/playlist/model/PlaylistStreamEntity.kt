package org.schabi.newpipe.database.playlist.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.Index
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_INDEX
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(tableName = PLAYLIST_STREAM_JOIN_TABLE, primaryKeys = arrayOf(JOIN_PLAYLIST_ID, JOIN_INDEX), indices = arrayOf(Index(value = *arrayOf(JOIN_PLAYLIST_ID, JOIN_INDEX), unique = true), Index(value = *arrayOf(JOIN_STREAM_ID))), foreignKeys = arrayOf(ForeignKey(entity = PlaylistEntity::class, parentColumns = arrayOf(PlaylistEntity.PLAYLIST_ID), childColumns = arrayOf(JOIN_PLAYLIST_ID), onDelete = CASCADE, onUpdate = CASCADE, deferred = true), ForeignKey(entity = StreamEntity::class, parentColumns = arrayOf(StreamEntity.STREAM_ID), childColumns = arrayOf(JOIN_STREAM_ID), onDelete = CASCADE, onUpdate = CASCADE, deferred = true)))
class PlaylistStreamEntity(@field:ColumnInfo(name = JOIN_PLAYLIST_ID)
                           var playlistUid: Long, @field:ColumnInfo(name = JOIN_STREAM_ID)
                           var streamUid: Long, @field:ColumnInfo(name = JOIN_INDEX)
                           var index: Int) {
    companion object {

        const val PLAYLIST_STREAM_JOIN_TABLE = "playlist_stream_join"
        const val JOIN_PLAYLIST_ID = "playlist_id"
        const val JOIN_STREAM_ID = "stream_id"
        const val JOIN_INDEX = "join_index"
    }
}
