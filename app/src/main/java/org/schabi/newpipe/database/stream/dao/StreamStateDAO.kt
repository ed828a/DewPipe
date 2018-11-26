package org.schabi.newpipe.database.stream.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.arch.persistence.room.Transaction

import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.stream.model.StreamStateEntity

import io.reactivex.Flowable

import org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE

@Dao
abstract class StreamStateDAO : BasicDAO<StreamStateEntity> {
    @get:Query("SELECT * FROM $STREAM_STATE_TABLE")
    abstract override val all: Flowable<List<StreamStateEntity>>

    @Query("DELETE FROM $STREAM_STATE_TABLE")
    abstract override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<StreamStateEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM $STREAM_STATE_TABLE WHERE $JOIN_STREAM_ID = :streamId")
    abstract fun getState(streamId: Long): Flowable<List<StreamStateEntity>>

    @Query("DELETE FROM $STREAM_STATE_TABLE WHERE $JOIN_STREAM_ID = :streamId")
    abstract fun deleteState(streamId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertInternal(streamState: StreamStateEntity)

    @Transaction
    open fun upsert(stream: StreamStateEntity): Long {
        silentInsertInternal(stream)
        return update(stream).toLong()
    }
}
