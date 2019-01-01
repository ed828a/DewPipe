package org.schabi.newpipe.download.giga.get

/**
 * Provides access to the storage of [DownloadMission]s
 */
interface DownloadDataSource {

    /**
     * Load all missions
     *
     * @return a list of download missions
     */
    fun loadMissions(): List<DownloadMission>

    /**
     * Add a download missionControl to the storage
     *
     * @param downloadMission the download missionControl to add
     * @return the identifier of the missionControl
     */
    fun addMission(downloadMission: DownloadMission?)

    /**
     * Update a download missionControl which exists in the storage
     *
     * @param downloadMission the download missionControl to update
     * @throws IllegalArgumentException if the missionControl was not added to storage
     */
    fun updateMission(downloadMission: DownloadMission?)


    /**
     * Delete a download missionControl
     *
     * @param downloadMission the missionControl to delete
     */
    fun deleteMission(downloadMission: DownloadMission?)
}