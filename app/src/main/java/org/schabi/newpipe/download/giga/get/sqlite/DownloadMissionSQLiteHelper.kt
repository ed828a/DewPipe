package org.schabi.newpipe.download.giga.get.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import org.schabi.newpipe.download.giga.get.DownloadMission

/**
 * SqliteHelper to store [us.shandian.giga.get.DownloadMission]
 */
class DownloadMissionSQLiteHelper internal constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {


    private val TAG = "DownloadMissionHelper"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MISSIONS_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Currently nothing to do
    }

    companion object {

        // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
        private const val DATABASE_NAME = "downloads.db"

        private const val DATABASE_VERSION = 2
        /**
         * The table name of download missions
         */
        internal const val MISSIONS_TABLE_NAME = "download_missions"

        /**
         * The key to the directory location of the mission
         */
        internal const val KEY_LOCATION = "location"
        /**
         * The key to the url of a mission
         */
        internal const val KEY_URL = "url"
        /**
         * The key to the name of a mission
         */
        internal const val KEY_NAME = "name"

        /**
         * The key to the done.
         */
        internal const val KEY_DONE = "bytes_downloaded"

        internal const val KEY_TIMESTAMP = "timestamp"

        /**
         * The statement to create the table
         */
        private const val MISSIONS_CREATE_TABLE = "CREATE TABLE " + MISSIONS_TABLE_NAME + " (" +
                KEY_LOCATION + " TEXT NOT NULL, " +
                KEY_NAME + " TEXT NOT NULL, " +
                KEY_URL + " TEXT NOT NULL, " +
                KEY_DONE + " INTEGER NOT NULL, " +
                KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                " UNIQUE(" + KEY_LOCATION + ", " + KEY_NAME + "));"

        /**
         * Returns all values of the download mission as ContentValues.
         *
         * @param downloadMission the download mission
         * @return the content values
         */
        fun getValuesOfMission(downloadMission: DownloadMission): ContentValues {
            val values = ContentValues()
            values.put(KEY_URL, downloadMission.url)
            values.put(KEY_LOCATION, downloadMission.location)
            values.put(KEY_NAME, downloadMission.name)
            values.put(KEY_DONE, downloadMission.done)
            values.put(KEY_TIMESTAMP, downloadMission.timestamp)
            return values
        }

        fun getMissionFromCursor(cursor: Cursor?): DownloadMission {
            if (cursor == null) throw NullPointerException("cursor is null")

            val name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME))
            val location = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION))
            val url = cursor.getString(cursor.getColumnIndexOrThrow(KEY_URL))
            val mission = DownloadMission(name, url, location)
            mission.done = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE))
            mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP))
            mission.finished = true
            return mission
        }
    }
}
