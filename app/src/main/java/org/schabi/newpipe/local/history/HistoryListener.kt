package org.schabi.newpipe.local.history

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream


// no usage of this class yet. Edward

interface HistoryListener {
    /**
     * Called when a video is played
     *
     * @param streamInfo  the stream info
     * @param videoStream the video stream that is played. Can be null if it's not sure what
     * quality was viewed (e.g. with Kodi).
     */
    fun onVideoPlayed(streamInfo: StreamInfo, videoStream: VideoStream?)

    /**
     * Called when the audio is played in the background
     *
     * @param streamInfo  the stream info
     * @param audioStream the audio stream that is played
     */
    fun onAudioPlayed(streamInfo: StreamInfo, audioStream: AudioStream)

    /**
     * Called when the user searched for something
     *
     * @param serviceId which service the search was done
     * @param query     what the user searched for
     */
    fun onSearch(serviceId: Int, query: String)
}
