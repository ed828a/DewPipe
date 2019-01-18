package org.schabi.newpipe.database.downloadDB

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context

/**
 * Created by Edward on 12/22/2018.
 *
 * This db is for temporary use, MissionEntity table stays in AppDatabase Finally.
 */

@Database(entities = [MissionEntity::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase(){

    abstract fun downloadDao(): DownloadDAO

    companion object {

        // make this class a singleton
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        // factory method
        fun getDatabase(context: Context): DownloadDatabase =
                INSTANCE ?: synchronized(DownloadDatabase::class.java) {
                    INSTANCE ?: Room
                            .databaseBuilder(context.applicationContext,
                                    DownloadDatabase::class.java,
                                    "download_database.db")
                            .build()
                            .also { INSTANCE = it }
                }

    }
}