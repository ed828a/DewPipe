package org.schabi.newpipe.player.mediasource

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.BaseMediaSource
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.TransferListener
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class PlaceholderMediaSource : BaseMediaSource(), ManagedMediaSource {

    // @param startPositionUs The expected start position, in microseconds.
    override fun createPeriod(id: MediaSource.MediaPeriodId?, allocator: Allocator?, startPositionUs: Long): MediaPeriod? {
        return null
    }

    // Do nothing, so this will stall the playback
    override fun maybeThrowSourceInfoRefreshError() {}


    override fun releasePeriod(mediaPeriod: MediaPeriod) {}

    // @param mediaTransferListener:  The transfer listener which should be informed of any media data
    //  transfers. May be null if no listener is available.
    override fun prepareSourceInternal(player: ExoPlayer?, isTopLevelSource: Boolean, mediaTransferListener: TransferListener?) {}

    override fun releaseSourceInternal() {}

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                      isInterruptable: Boolean): Boolean {
        return true
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return false
    }
}
