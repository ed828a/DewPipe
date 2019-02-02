package org.schabi.newpipe

import android.arch.persistence.room.Room
import android.content.Context
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.AppDatabase.Companion.DATABASE_NAME
import org.schabi.newpipe.database.Migrations.MIGRATION_11_12

object NewPipeDatabase {

    @Volatile
    private var databaseInstance: AppDatabase? = null

    private fun getDatabase(context: Context): AppDatabase {
        return Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
    }

    fun getInstance(context: Context): AppDatabase {
        var result = databaseInstance
        if (result == null) {
            synchronized(NewPipeDatabase::class.java) {
                result = databaseInstance
                if (result == null) {
                    result = getDatabase(context)
                    databaseInstance = result
                }
            }
        }

        return result!!
    }
}
