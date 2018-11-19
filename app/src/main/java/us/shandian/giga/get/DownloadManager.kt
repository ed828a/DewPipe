package us.shandian.giga.get

interface DownloadManager {

    /**
     * Get the number of download missions.
     *
     * @return the number of download missions.
     */
    val count: Int

    /**
     * Start a new download mission
     *
     * @param url      the url to download
     * @param location the location
     * @param name     the name of the file to create
     * @param isAudio  true if the download is an audio file
     * @param threads  the number of threads maximal used to download chunks of the file.    @return the identifier of the mission.
     */
    fun startMission(url: String, location: String, name: String, isAudio: Boolean, threads: Int): Int

    /**
     * Resume the execution of a download mission.
     *
     * @param id the identifier of the mission to resume.
     */
    fun resumeMission(id: Int)

    /**
     * Pause the execution of a download mission.
     *
     * @param id the identifier of the mission to pause.
     */
    fun pauseMission(id: Int)

    /**
     * Deletes the mission from the downloaded list but keeps the downloaded file.
     *
     * @param id The mission identifier
     */
    fun deleteMission(id: Int)

    /**
     * Get the download mission by its identifier
     *
     * @param id the identifier of the download mission
     * @return the download mission or null if the mission doesn't exist
     */
    fun getMission(id: Int): DownloadMission

    companion object {
        const val BLOCK_SIZE = 512 * 1024
    }

}
