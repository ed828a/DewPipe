package org.schabi.newpipe.database.playlist.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_NAME
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_TABLE

@Entity(tableName = PLAYLIST_TABLE, indices = arrayOf(Index(value = *arrayOf(PLAYLIST_NAME))))
class PlaylistEntity(@field:ColumnInfo(name = PLAYLIST_NAME)
                     var name: String?, @field:ColumnInfo(name = PLAYLIST_THUMBNAIL_URL)
                     var thumbnailUrl: String?) {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PLAYLIST_ID)
    var uid: Long = 0

    companion object {
        const val PLAYLIST_TABLE = "playlists"
        const val PLAYLIST_ID = "uid"
        const val PLAYLIST_NAME = "name"
        const val PLAYLIST_THUMBNAIL_URL = "thumbnail_url"
    }
}
