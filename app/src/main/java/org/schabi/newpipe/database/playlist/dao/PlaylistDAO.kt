package org.schabi.newpipe.database.playlist.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import io.reactivex.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_ID
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_TABLE

@Dao
abstract class PlaylistDAO : BasicDAO<PlaylistEntity> {
    @get:Query("SELECT * FROM $PLAYLIST_TABLE")
    abstract override val all: Flowable<List<PlaylistEntity>>

    @Query("DELETE FROM $PLAYLIST_TABLE")
    abstract override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM $PLAYLIST_TABLE WHERE $PLAYLIST_ID = :playlistId")
    abstract fun getPlaylist(playlistId: Long): Flowable<List<PlaylistEntity>>

    @Query("DELETE FROM $PLAYLIST_TABLE WHERE $PLAYLIST_ID = :playlistId")
    abstract fun deletePlaylist(playlistId: Long): Int
}
