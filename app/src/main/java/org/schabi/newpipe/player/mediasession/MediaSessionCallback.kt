package org.schabi.newpipe.player.mediasession

import android.support.v4.media.MediaDescriptionCompat

interface MediaSessionCallback {

    fun onPlay()
    fun onPause()
    fun onSkipToPrevious()
    fun onSkipToNext()
    fun onSkipToIndex(index: Int)

    fun getCurrentPlayingIndex(): Int
    fun getQueueSize(): Int
    fun getQueueMetadata(index: Int): MediaDescriptionCompat?
}
