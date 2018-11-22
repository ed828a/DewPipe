package org.schabi.newpipe.player.mediasource

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.BaseMediaSource
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.Allocator

import org.schabi.newpipe.player.playqueue.PlayQueueItem

class PlaceholderMediaSource : BaseMediaSource(), ManagedMediaSource {
    // Do nothing, so this will stall the playback
    override fun maybeThrowSourceInfoRefreshError() {}

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator): MediaPeriod? {
        return null
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {}
    override fun prepareSourceInternal(player: ExoPlayer, isTopLevelSource: Boolean) {}
    override fun releaseSourceInternal() {}

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                      isInterruptable: Boolean): Boolean {
        return true
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return false
    }
}
