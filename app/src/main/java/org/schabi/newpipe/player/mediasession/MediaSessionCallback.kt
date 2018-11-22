package org.schabi.newpipe.player.mediasession

import android.support.v4.media.MediaDescriptionCompat

interface MediaSessionCallback {

    fun getCurrentPlayingIndex(): Int
    fun getQueueSize(): Int
    fun onSkipToPrevious()
    fun onSkipToNext()
    fun onSkipToIndex(index: Int)
    fun getQueueMetadata(index: Int): MediaDescriptionCompat?

    fun onPlay()
    fun onPause()
}
