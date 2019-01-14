package org.schabi.newpipe.download.downloadDB

import android.arch.persistence.room.*

/**
 * Created by Edward on 12/21/2018.
 */

@Dao
interface DownloadDAO {

    @Query("SELECT * FROM ${MissionEntity.TABLE_NAME} ORDER BY ${MissionEntity.TIMESTAMP}")
    fun loadMissions(): List<MissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addMission(missionEntity: MissionEntity)

    @Query("UPDATE ${MissionEntity.TABLE_NAME} SET ${MissionEntity.URL} = :url, ${MissionEntity.DONE} = :done, ${MissionEntity.TIMESTAMP} = :timestamp WHERE ${MissionEntity.FILE_NAME} = :name AND ${MissionEntity.LOCATION} = :location")
    fun updateMission(name: String, location: String, url: String, done: kotlin.Long, timestamp: kotlin.Long)

    @Delete
    fun deleteMission(missionEntity: MissionEntity)

    companion object {
        fun updateMission(missionEntity: MissionEntity, downloadDataSource: DownloadDAO){
            with(missionEntity){
                downloadDataSource.updateMission(name, location, url, done, timestamp)
            }
        }
    }
}