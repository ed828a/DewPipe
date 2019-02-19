package org.schabi.newpipe.player.mediasession

import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector

class MediaSessionManager(context: Context,
                          player: Player,
                          callback: MediaSessionCallback) {

    private val mediaSession: MediaSessionCompat
    private val sessionConnector: MediaSessionConnector

    init {
        this.mediaSession = MediaSessionCompat(context, TAG)
        this.mediaSession.isActive = true

        this.sessionConnector = MediaSessionConnector(mediaSession, PlayQueuePlaybackController(callback))
        this.sessionConnector.setQueueNavigator(PlayQueueNavigator(mediaSession, callback))
        this.sessionConnector.setPlayer(player, null)
    }

    // process the instruction signals from outside of the app
    fun handleMediaButtonIntent(intent: Intent): KeyEvent? =
            androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)


    /**
     * Should be called on simpleExoPlayer destruction to prevent leakage.
     */
    fun dispose() {
        this.sessionConnector.setPlayer(null, null)
        this.sessionConnector.setQueueNavigator(null)
        this.mediaSession.isActive = false
        this.mediaSession.release()
    }

    companion object {
        private const val TAG = "MediaSessionManager"
    }
}
