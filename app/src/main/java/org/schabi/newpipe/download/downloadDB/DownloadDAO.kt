package org.schabi.newpipe.download.downloadDB

import android.arch.persistence.room.*

/**
 * Created by Edward on 12/21/2018.
 */

@Dao
interface DownloadDAO {

    @Query("SELECT * FROM ${MissionEntry.TABLE_NAME} ORDER BY ${MissionEntry.TIMESTAMP}")
    fun loadMissions(): List<MissionEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addMission(missionEntry: MissionEntry)


    @Update()
    fun updateMission(missionEntry: MissionEntry)

    @Delete
    fun deleteMission(missionEntry: MissionEntry)

}