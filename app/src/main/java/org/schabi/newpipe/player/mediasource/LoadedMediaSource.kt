package org.schabi.newpipe.player.mediasource

import android.os.Handler

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.TransferListener

import org.schabi.newpipe.player.playqueue.PlayQueueItem

import java.io.IOException

class LoadedMediaSource(private val source: MediaSource,
                        val stream: PlayQueueItem,
                        private val expireTimestamp: Long) : ManagedMediaSource {
    override fun prepareSource(player: ExoPlayer?, isTopLevelSource: Boolean, listener: MediaSource.SourceInfoRefreshListener?, mediaTransferListener: TransferListener?) {
        source.prepareSource(player, isTopLevelSource, listener, mediaTransferListener)
    }

    private val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireTimestamp

    override fun prepareSource(player: ExoPlayer, isTopLevelSource: Boolean,
                               listener: MediaSource.SourceInfoRefreshListener) {
        source.prepareSource(player, isTopLevelSource, listener)
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        source.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId?, allocator: Allocator?, startPositionUs: Long): MediaPeriod {
        return source.createPeriod(id, allocator, startPositionUs)
    }


    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        source.releasePeriod(mediaPeriod)
    }

    override fun releaseSource(listener: MediaSource.SourceInfoRefreshListener) {
        source.releaseSource(listener)
    }

    override fun addEventListener(handler: Handler, eventListener: MediaSourceEventListener) {
        source.addEventListener(handler, eventListener)
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener) {
        source.removeEventListener(eventListener)
    }

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                      isInterruptable: Boolean): Boolean {
        return newIdentity != stream || isInterruptable && isExpired
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return this.stream == stream
    }
}
