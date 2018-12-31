package org.schabi.newpipe.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters

import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity

import org.schabi.newpipe.database.Migrations.DB_VER_12_0
import org.schabi.newpipe.download.downloadDB.DownloadDAO
import org.schabi.newpipe.download.downloadDB.MissionEntry

@TypeConverters(Converters::class)
@Database(entities = [
    SubscriptionEntity::class,
    SearchHistoryEntry::class,
    StreamEntity::class,
    StreamHistoryEntity::class,
    StreamStateEntity::class,
    PlaylistEntity::class,
    PlaylistStreamEntity::class,
    PlaylistRemoteEntity::class,
    MissionEntry::class
],
        version = DB_VER_12_0, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriptionDAO(): SubscriptionDAO

    abstract fun searchHistoryDAO(): SearchHistoryDAO

    abstract fun streamDAO(): StreamDAO

    abstract fun streamHistoryDAO(): StreamHistoryDAO

    abstract fun streamStateDAO(): StreamStateDAO

    abstract fun playlistDAO(): PlaylistDAO

    abstract fun playlistStreamDAO(): PlaylistStreamDAO

    abstract fun playlistRemoteDAO(): PlaylistRemoteDAO

    abstract fun downloadDAO(): DownloadDAO

    companion object {

        const val DATABASE_NAME = "newpipe.db"
    }
}
