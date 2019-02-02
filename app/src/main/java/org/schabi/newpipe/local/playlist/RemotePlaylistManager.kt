package org.schabi.newpipe.local.playlist

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class RemotePlaylistManager(db: AppDatabase) {

    private val playlistRemoteTable: PlaylistRemoteDAO = db.playlistRemoteDAO()

    val playlists: Flowable<List<PlaylistRemoteEntity>>
        get() = playlistRemoteTable.all.subscribeOn(Schedulers.io())

    fun getPlaylist(info: PlaylistInfo): Flowable<List<PlaylistRemoteEntity>> =
            playlistRemoteTable.getPlaylist(info.serviceId.toLong(), info.url)
                    .subscribeOn(Schedulers.io())

    fun deletePlaylist(playlistId: Long): Single<Int> =
            Single.fromCallable { playlistRemoteTable.deletePlaylist(playlistId) }
                    .subscribeOn(Schedulers.io())

    fun onBookmark(playlistInfo: PlaylistInfo): Single<Long> =
            Single.fromCallable {
                val playlist = PlaylistRemoteEntity(playlistInfo)
                playlistRemoteTable.upsert(playlist)
            }.subscribeOn(Schedulers.io())

    fun onUpdate(playlistId: Long, playlistInfo: PlaylistInfo): Single<Int> =
            Single.fromCallable {
                val playlist = PlaylistRemoteEntity(playlistInfo)
                playlist.uid = playlistId
                playlistRemoteTable.update(playlist)
            }.subscribeOn(Schedulers.io())

}
