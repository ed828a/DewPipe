package org.schabi.newpipe.player.helper

import android.content.Context

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.DefaultAllocator

import com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS
import com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES

class LoadController private constructor(initialPlaybackBufferMs: Int,
                                         minimumPlaybackbufferMs: Int,
                                         optimalPlaybackBufferMs: Int) : LoadControl {

    private val initialPlaybackBufferUs: Long = (initialPlaybackBufferMs * 1000).toLong()
    private val internalLoadControl: LoadControl

    ///////////////////////////////////////////////////////////////////////////
    // Default Load Control
    ///////////////////////////////////////////////////////////////////////////

    constructor(context: Context) : this(PlayerHelper.getPlaybackStartBufferMs(context),
            PlayerHelper.getPlaybackMinimumBufferMs(context),
            PlayerHelper.getPlaybackOptimalBufferMs(context))

    init {
        // this is a default one in DefaultLoadControl
//        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

//        internalLoadControl = DefaultLoadControl(allocator,
//                /*minBufferMs=*/minimumPlaybackbufferMs,
//                /*maxBufferMs=*/optimalPlaybackBufferMs,
//                /*bufferForPlaybackMs=*/initialPlaybackBufferMs,
//                /*bufferForPlaybackAfterRebufferMs=*/initialPlaybackBufferMs,
//                DEFAULT_TARGET_BUFFER_BYTES,
//                DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS)

        internalLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        /*minBufferMs=*/minimumPlaybackbufferMs,
                        /*maxBufferMs=*/optimalPlaybackBufferMs,
                        /*bufferForPlaybackMs=*/initialPlaybackBufferMs,
                        /*bufferForPlaybackAfterRebufferMs=*/initialPlaybackBufferMs
                )
                .createDefaultLoadControl()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Custom behaviours
    ///////////////////////////////////////////////////////////////////////////

    override fun onPrepared() {
        internalLoadControl.onPrepared()
    }

    override fun onTracksSelected(renderers: Array<Renderer>,
                                  trackGroupArray: TrackGroupArray,
                                  trackSelectionArray: TrackSelectionArray) {
        internalLoadControl.onTracksSelected(renderers, trackGroupArray, trackSelectionArray)
    }

    override fun onStopped() {
        internalLoadControl.onStopped()
    }

    override fun onReleased() {
        internalLoadControl.onReleased()
    }

    override fun getAllocator(): Allocator = internalLoadControl.allocator


    override fun getBackBufferDurationUs(): Long = internalLoadControl.backBufferDurationUs

    override fun retainBackBufferFromKeyframe(): Boolean = internalLoadControl.retainBackBufferFromKeyframe()

    override fun shouldContinueLoading(bufferedDurationUs: Long, playbackSpeed: Float): Boolean =
            internalLoadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed)

    override fun shouldStartPlayback(bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean): Boolean {
        val isInitialPlaybackBufferFilled = bufferedDurationUs >= this.initialPlaybackBufferUs * playbackSpeed
        val isInternalStartingPlayback = internalLoadControl.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering)

        return isInitialPlaybackBufferFilled || isInternalStartingPlayback
    }

    companion object {

        const val TAG = "LoadController"
    }
}
