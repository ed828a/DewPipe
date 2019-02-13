package org.schabi.newpipe.player.model

import org.schabi.newpipe.player.playqueue.PlayQueue

import java.io.Serializable

class PlayerState(
        val playQueue: PlayQueue,
        val repeatMode: Int,
        val playbackSpeed: Float,
        val playbackPitch: Float,
        val playbackQuality: String?,
        val isPlaybackSkipSilence: Boolean,
        val wasPlaying: Boolean) : Serializable {

    internal constructor(playQueue: PlayQueue,
                         repeatMode: Int,
                         playbackSpeed: Float,
                         playbackPitch: Float,
                         playbackSkipSilence: Boolean,
                         wasPlaying: Boolean
    ) : this(playQueue, repeatMode, playbackSpeed, playbackPitch, null, playbackSkipSilence, wasPlaying)
}
