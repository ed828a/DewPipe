package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem

import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_ID
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_NAME
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_URL

class PlaylistMetadataEntry(
        @field:ColumnInfo(name = PLAYLIST_ID) val uid: Long,
        @field:ColumnInfo(name = PLAYLIST_NAME) val name: String,
        @field:ColumnInfo(name = PLAYLIST_THUMBNAIL_URL) val thumbnailUrl: String,
        @field:ColumnInfo(name = PLAYLIST_STREAM_COUNT) val streamCount: Long) : PlaylistLocalItem {

    override val localItemType: LocalItem.LocalItemType
        get() = LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM

    override fun getOrderingName(): String = name

    companion object {
        const val PLAYLIST_STREAM_COUNT = "streamCount"
    }
}
