package org.schabi.newpipe.database.stream.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.arch.persistence.room.Transaction

import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntity

import java.util.ArrayList

import io.reactivex.Flowable

import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_SERVICE_ID
import org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_URL
import org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_HISTORY_TABLE

@Dao
abstract class StreamDAO : BasicDAO<StreamEntity> {
    @get:Query("SELECT * FROM $STREAM_TABLE")
    abstract override val all: Flowable<List<StreamEntity>>

    @Query("DELETE FROM $STREAM_TABLE")
    abstract override fun deleteAll(): Int

    @Query("SELECT * FROM $STREAM_TABLE WHERE $STREAM_SERVICE_ID = :serviceId")
    abstract override fun listByService(serviceId: Int): Flowable<List<StreamEntity>>

    @Query("SELECT * FROM " + STREAM_TABLE + " WHERE " +
            STREAM_URL + " = :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    abstract fun getStream(serviceId: Long, url: String): Flowable<List<StreamEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertAllInternal(streams: List<StreamEntity>)

    @Query("SELECT " + STREAM_ID + " FROM " + STREAM_TABLE + " WHERE " +
            STREAM_URL + " = :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    internal abstract fun getStreamIdInternal(serviceId: Long, url: String): Long?

    @Transaction
    open fun upsert(stream: StreamEntity): Long {
        val streamIdCandidate = getStreamIdInternal(stream.serviceId.toLong(), stream.url)

        if (streamIdCandidate == null) {
            return insert(stream)
        } else {
            stream.uid = streamIdCandidate
            update(stream)
            return streamIdCandidate
        }
    }

    @Transaction
    open fun upsertAll(streams: List<StreamEntity>): List<Long> {
        silentInsertAllInternal(streams)

        val streamIds = ArrayList<Long>(streams.size)
        for (stream in streams) {
            val streamId = getStreamIdInternal(stream.serviceId.toLong(), stream.url)
                    ?: throw IllegalStateException("StreamID cannot be null just after insertion.")

            streamIds.add(streamId)
            stream.uid = streamId
        }

        update(streams)
        return streamIds
    }

    @Query("DELETE FROM " + STREAM_TABLE + " WHERE " + STREAM_ID +
            " NOT IN " +
            "(SELECT DISTINCT " + STREAM_ID + " FROM " + STREAM_TABLE +

            " LEFT JOIN " + STREAM_HISTORY_TABLE +
            " ON " + STREAM_ID + " = " +
            StreamHistoryEntity.STREAM_HISTORY_TABLE + "." + StreamHistoryEntity.JOIN_STREAM_ID +

            " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE +
            " ON " + STREAM_ID + " = " +
            PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE + "." + PlaylistStreamEntity.JOIN_STREAM_ID +
            ")")
    abstract fun deleteOrphans(): Int
}
