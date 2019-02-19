package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.schabi.newpipe.database.downloadDB.DownloadDAO
import org.schabi.newpipe.database.downloadDB.MissionEntity
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
    MissionEntity::class
],
//        version = DB_VER_12_0,
        version = 1,
        exportSchema = false)
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

        // make this class a singleton
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // factory method
        fun getDatabase(context: Context): AppDatabase =
                INSTANCE ?: synchronized(AppDatabase::class.java) {
                    INSTANCE ?: Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            DATABASE_NAME)
                            .build()
                            .also { INSTANCE = it }
                }
    }
}
