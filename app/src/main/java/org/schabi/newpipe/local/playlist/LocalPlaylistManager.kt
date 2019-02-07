package org.schabi.newpipe.local.playlist

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.util.*

class LocalPlaylistManager(private val database: AppDatabase) {
    private val streamTable: StreamDAO = database.streamDAO()
    private val playlistTable: PlaylistDAO = database.playlistDAO()
    private val playlistStreamTable: PlaylistStreamDAO = database.playlistStreamDAO()

    val playlists: Flowable<List<PlaylistMetadataEntry>>
        get() = playlistStreamTable.playlistMetadata.subscribeOn(Schedulers.io())

    fun createPlaylist(name: String, streams: List<StreamEntity>): Maybe<List<Long>> {
        // Disallow creation of empty playlists
        if (streams.isEmpty()) return Maybe.empty()
        val defaultStream = streams[0]
        val newPlaylist = PlaylistEntity(name, defaultStream.thumbnailUrl)

        return Maybe.fromCallable {
            database.runInTransaction<List<Long>> {
                upsertStreams(playlistTable.insert(newPlaylist), streams, 0)
            } }
                .subscribeOn(Schedulers.io())
    }

    fun appendToPlaylist(playlistId: Long,
                         streams: List<StreamEntity>): Maybe<List<Long>> {
        return playlistStreamTable.getMaximumIndexOf(playlistId)
                .firstElement()
                .map { maxJoinIndex ->
                    database.runInTransaction<List<Long>> {
                        upsertStreams(playlistId, streams, maxJoinIndex + 1)
                    } }
                .subscribeOn(Schedulers.io())
    }

    private fun upsertStreams(playlistId: Long,
                              streams: List<StreamEntity>,
                              indexOffset: Int): List<Long> {

        val joinEntities = ArrayList<PlaylistStreamEntity>(streams.size)
        val streamIds = streamTable.upsertAll(streams)
        for (index in streamIds.indices) {
            joinEntities.add(PlaylistStreamEntity(playlistId, streamIds[index],
                    index + indexOffset))
        }
        return playlistStreamTable.insertAll(joinEntities)
    }

    fun updateJoin(playlistId: Long, streamIds: List<Long>): Completable {
        val joinEntities = ArrayList<PlaylistStreamEntity>(streamIds.size)
        for (i in streamIds.indices) {
            joinEntities.add(PlaylistStreamEntity(playlistId, streamIds[i], i))
        }

        return Completable.fromRunnable {
            database.runInTransaction {
                playlistStreamTable.deleteBatch(playlistId)
                playlistStreamTable.insertAll(joinEntities)
            }
        }.subscribeOn(Schedulers.io())
    }

    fun getPlaylistStreams(playlistId: Long): Flowable<List<PlaylistStreamEntry>> {
        return playlistStreamTable.getOrderedStreamsOf(playlistId).subscribeOn(Schedulers.io())
    }

    fun deletePlaylist(playlistId: Long): Single<Int> {
        return Single.fromCallable { playlistTable.deletePlaylist(playlistId) }
                .subscribeOn(Schedulers.io())
    }

    fun renamePlaylist(playlistId: Long, name: String): Maybe<Int> {
        return modifyPlaylist(playlistId, name, null)
    }

    fun changePlaylistThumbnail(playlistId: Long,
                                thumbnailUrl: String): Maybe<Int> {
        return modifyPlaylist(playlistId, null, thumbnailUrl)
    }

    private fun modifyPlaylist(playlistId: Long,
                               name: String?,
                               thumbnailUrl: String?): Maybe<Int> {
        return playlistTable.getPlaylist(playlistId)
                .firstElement()
                .filter { playlistEntities -> !playlistEntities.isEmpty() }
                .map { playlistEntities ->
                    val playlist = playlistEntities[0]
                    if (name != null) playlist.name = name
                    if (thumbnailUrl != null) playlist.thumbnailUrl = thumbnailUrl
                    playlistTable.update(playlist)
                }
                .subscribeOn(Schedulers.io())
    }

}
