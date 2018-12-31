package org.schabi.newpipe.download.downloadDB

import android.arch.persistence.room.*

/**
 * Created by Edward on 12/21/2018.
 */

@Dao
interface DownloadDAO {

    @Query("SELECT * FROM ${MissionEntry.TABLE_NAME} ORDER BY ${MissionEntry.TIMESTAMP}")
    fun loadMissions(): List<MissionEntry>

//    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Insert
    fun addMission(missionEntry: MissionEntry)

    @Query("UPDATE ${MissionEntry.TABLE_NAME} SET ${MissionEntry.URL} = :url, ${MissionEntry.DONE} = :done, ${MissionEntry.TIMESTAMP} = :timestamp WHERE ${MissionEntry.FILE_NAME} = :name AND ${MissionEntry.LOCATION} = :location")
    fun updateMission(name: String, location: String, url: String, done: kotlin.Long, timestamp: kotlin.Long)

    @Delete
    fun deleteMission(missionEntry: MissionEntry)

    companion object {
        fun updateMission(missionEntry: MissionEntry, downloadDataSource: DownloadDAO){
            with(missionEntry){
                downloadDataSource.updateMission(name, location, url, done, timestamp)
            }
        }
    }
}