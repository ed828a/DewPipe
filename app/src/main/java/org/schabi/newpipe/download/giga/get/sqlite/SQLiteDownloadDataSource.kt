package org.schabi.newpipe.download.giga.get.sqlite

import android.content.Context
import android.util.Log

import java.util.ArrayList

import org.schabi.newpipe.download.giga.get.DownloadDataSource
import org.schabi.newpipe.download.giga.get.DownloadMission

import org.schabi.newpipe.download.giga.get.sqlite.DownloadMissionSQLiteHelper.Companion.KEY_LOCATION
import org.schabi.newpipe.download.giga.get.sqlite.DownloadMissionSQLiteHelper.Companion.KEY_NAME
import org.schabi.newpipe.download.giga.get.sqlite.DownloadMissionSQLiteHelper.Companion.MISSIONS_TABLE_NAME


/**
 * Non-thread-safe implementation of [DownloadDataSource]
 */
class SQLiteDownloadDataSource(context: Context) : DownloadDataSource {
    private val downloadMissionSQLiteHelper: DownloadMissionSQLiteHelper =
            DownloadMissionSQLiteHelper(context)

    override fun loadMissions(): List<DownloadMission> {
        val result: ArrayList<DownloadMission>
        val database = downloadMissionSQLiteHelper.readableDatabase
        val cursor = database.query(MISSIONS_TABLE_NAME, null, null, null, null, null, DownloadMissionSQLiteHelper.KEY_TIMESTAMP)

        val count = cursor.count
        if (count == 0) return ArrayList()
        result = ArrayList(count)
        while (cursor.moveToNext()) {
            result.add(DownloadMissionSQLiteHelper.getMissionFromCursor(cursor))
        }
        return result
    }

    override fun addMission(downloadMission: DownloadMission?) {
        if (downloadMission == null) throw NullPointerException("downloadMission is null")
        val database = downloadMissionSQLiteHelper.writableDatabase
        val values = DownloadMissionSQLiteHelper.getValuesOfMission(downloadMission)
        database.insert(MISSIONS_TABLE_NAME, null, values)
    }

    override fun updateMission(downloadMission: DownloadMission?) {
        if (downloadMission == null) throw NullPointerException("downloadMission is null")
        val database = downloadMissionSQLiteHelper.writableDatabase
        val values = DownloadMissionSQLiteHelper.getValuesOfMission(downloadMission)
        val whereClause = KEY_LOCATION + " = ? AND " +
                KEY_NAME + " = ?"
        val rowsAffected = database.update(MISSIONS_TABLE_NAME, values,
                whereClause, arrayOf(downloadMission.location, downloadMission.name))
        if (rowsAffected != 1) {
            Log.e(TAG, "Expected 1 row to be affected by update but got $rowsAffected")
        }
    }

    override fun deleteMission(downloadMission: DownloadMission?) {
        if (downloadMission == null) throw NullPointerException("downloadMission is null")
        val database = downloadMissionSQLiteHelper.writableDatabase
        database.delete(MISSIONS_TABLE_NAME,
                KEY_LOCATION + " = ? AND " +
                        KEY_NAME + " = ?",
                arrayOf(downloadMission.location, downloadMission.name))
    }

    companion object {

        private const val TAG = "DownloadDataSourceImpl"
    }
}
