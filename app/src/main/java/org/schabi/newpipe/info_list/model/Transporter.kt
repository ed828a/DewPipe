package org.schabi.newpipe.info_list.model

import android.widget.ImageView
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView

data class Transporter(
        var videoSurfaceView: PlayerView? = null,
        var player: SimpleExoPlayer? = null,
        var lastPlayingCover: ImageView? = null
)