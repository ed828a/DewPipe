package org.schabi.newpipe.player.mediasource

import android.util.Log

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.BaseMediaSource
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.TransferListener

import org.schabi.newpipe.player.playqueue.PlayQueueItem

import java.io.IOException

class FailedMediaSource : BaseMediaSource, ManagedMediaSource {
    override fun prepareSourceInternal(player: ExoPlayer?, isTopLevelSource: Boolean, mediaTransferListener: TransferListener?) {
        Log.e(TAG, "Loading failed source: ", error)
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId?, allocator: Allocator?, startPositionUs: Long): MediaPeriod? {
        return null
    }


    private val TAG = "FailedMediaSource@" + Integer.toHexString(hashCode())

    val stream: PlayQueueItem
    val error: FailedMediaSourceException

    private val retryTimestamp: Long

    open class FailedMediaSourceException : Exception {
        internal constructor(message: String) : super(message) {}

        internal constructor(cause: Throwable) : super(cause) {}
    }

    class MediaSourceResolutionException(message: String) : FailedMediaSourceException(message)

    class StreamInfoLoadException(cause: Throwable) : FailedMediaSourceException(cause)

    constructor(playQueueItem: PlayQueueItem,
                error: FailedMediaSourceException,
                retryTimestamp: Long) {
        this.stream = playQueueItem
        this.error = error
        this.retryTimestamp = retryTimestamp
    }

    /**
     * Permanently fail the play queue item associated with this source, with no hope of retrying.
     * The error will always be propagated to ExoPlayer.
     */
    constructor(playQueueItem: PlayQueueItem,
                error: FailedMediaSourceException) {
        this.stream = playQueueItem
        this.error = error
        this.retryTimestamp = java.lang.Long.MAX_VALUE
    }

    private fun canRetry(): Boolean {
        return System.currentTimeMillis() >= retryTimestamp
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        throw IOException(error)
    }



    override fun releasePeriod(mediaPeriod: MediaPeriod) {}



    override fun releaseSourceInternal() {}

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                      isInterruptable: Boolean): Boolean {
        return newIdentity != stream || canRetry()
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return this.stream == stream
    }
}
