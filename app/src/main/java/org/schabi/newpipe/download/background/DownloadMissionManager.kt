package org.schabi.newpipe.download.background

/**
 * Created by Edward on 12/22/2018.
 */

interface DownloadMissionManager {

    /**
     * Get the number of download missions.
     *
     * @return the number of download missions.
     */
    val count: Int

    /**
     * Start a new download missionControl
     *
     * @param url      the url to download
     * @param location the location
     * @param name     the name of the file to create
     * @param isAudio  true if the download is an audio file
     * @param threads  the number of threads maximal used to download chunks of the file.
     * @return the identifier of the missionControl.
     */
    fun startMission(url: String, location: String, name: String, isAudio: Boolean, threads: Int): Int

    /**
     * Resume the execution of a download missionControl.
     *
     * @param id the identifier of the missionControl to resume.
     */
    fun resumeMission(missionId: Int)

    /**
     * Pause the execution of a download missionControl.
     *
     * @param id the identifier of the missionControl to pause.
     */
    fun pauseMission(missionId: Int)

    /**
     * Deletes the missionControl from the downloaded list but keeps the downloaded file.
     *
     * @param id The missionControl identifier
     */
    fun deleteMission(missionId: Int)

    /**
     * Get the download missionControl by its identifier
     *
     * @param id the identifier of the download missionControl
     * @return the download missionControl or null if the missionControl doesn't exist
     */
    fun getMission(missionId: Int): MissionControl

    companion object {
        const val BLOCK_SIZE = 512 * 1024
    }
}
