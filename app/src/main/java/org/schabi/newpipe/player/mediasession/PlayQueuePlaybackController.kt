package org.schabi.newpipe.player.mediasession

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.DefaultPlaybackController

class PlayQueuePlaybackController(private val callback: MediaSessionCallback) : DefaultPlaybackController() {

    override fun onPlay(player: Player) {
        callback.onPlay()
    }

    override fun onPause(player: Player) {
        callback.onPause()
    }
}
