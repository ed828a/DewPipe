package org.schabi.newpipe.player.playback


import com.google.android.exoplayer2.PlaybackParameters

import org.schabi.newpipe.extractor.stream.StreamInfo

interface PlayerEventListener {
    fun onPlaybackUpdate(state: Int, repeatMode: Int, shuffled: Boolean, parameters: PlaybackParameters)

    fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int)

    fun onMetadataUpdate(info: StreamInfo?)

    fun onServiceStopped()
}
