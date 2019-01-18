package org.schabi.newpipe.database.downloadDB

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import org.schabi.newpipe.download.background.MissionControl

/**
 * Created by Edward on 1/1/2019.
 */
interface DownloadMissionDataSource {
    /**
     * Load all missions
     *
     * @return a list of download missions
     */
    fun loadMissions(): Flowable<List<MissionControl>>

    /**
     * Add a download missionControl to the storage
     *
     * @param missionControl the download missionControl to add
     * @return the identifier of the missionControl
     */
    fun addMission(missionControl: MissionControl): Completable

    /**
     * Update a download missionControl which exists in the storage
     *
     * @param missionControl the download missionControl to update
     * @throws IllegalArgumentException if the missionControl was not added to storage
     */
    fun updateMission(missionControl: MissionControl): Completable


    /**
     * Delete a download missionControl
     *
     * @param missionControl the missionControl to delete
     */
    fun deleteMission(missionControl: MissionControl): Completable

    /**
     * Check whether there is a list of DownloadEntry stored in the cache.
     *
     * @return true if the list is cached, otherwise false
     */
    fun isCached(): Single<Boolean>

}