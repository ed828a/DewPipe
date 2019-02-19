package org.schabi.newpipe.player.mediasource

import android.os.Handler
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ShuffleOrder

class ManagedMediaSourcePlaylist {
    val internalMediaSource: ConcatenatingMediaSource = ConcatenatingMediaSource(false, /*isPlaylistAtomic=*/
            ShuffleOrder.UnshuffledShuffleOrder(0))

    ///////////////////////////////////////////////////////////////////////////
    // MediaSource Delegations
    ///////////////////////////////////////////////////////////////////////////

    fun size(): Int {
        return internalMediaSource.size
    }

    /**
     * Returns the [ManagedMediaSource] at the given index of the playlist.
     * If the index is invalid, then null is returned.
     */
    operator fun get(index: Int): ManagedMediaSource? {
        return if (index < 0 || index >= size())
        /*doNothing=*/ null
        else
            internalMediaSource.getMediaSource(index) as ManagedMediaSource
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist Manipulation
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Expands the [ConcatenatingMediaSource] by appending it with a
     * [PlaceholderMediaSource].
     *
     * @see .append
     */
    @Synchronized
    fun expand() {
        append(PlaceholderMediaSource())
    }

    /**
     * Appends a [ManagedMediaSource] to the end of [ConcatenatingMediaSource].
     * @see ConcatenatingMediaSource.addMediaSource
     *
     */
    @Synchronized
    fun append(source: ManagedMediaSource) {
        internalMediaSource.addMediaSource(source)
    }

    /**
     * Removes a [ManagedMediaSource] getTabFrom [ConcatenatingMediaSource]
     * at the given index. If this index is out of bound, then the removal is ignored.
     * @see ConcatenatingMediaSource.removeMediaSource
     */
    @Synchronized
    fun remove(index: Int) {
        if (index < 0 || index > internalMediaSource.size) return

        internalMediaSource.removeMediaSource(index)
    }

    /**
     * Moves a [ManagedMediaSource] in [ConcatenatingMediaSource]
     * getTabFrom the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     * @see ConcatenatingMediaSource.moveMediaSource
     */
    @Synchronized
    fun move(source: Int, target: Int) {
        if (source < 0 || target < 0 || source >= internalMediaSource.size || target >= internalMediaSource.size) return

        internalMediaSource.moveMediaSource(source, target)
    }

    /**
     * Invalidates the [ManagedMediaSource] at the given index by replacing it
     * with a [PlaceholderMediaSource].
     * @see .update
     */
    @Synchronized
    fun invalidate(index: Int,
                   finalizingAction: Runnable?) {
        if (get(index) is PlaceholderMediaSource) return
        update(index, PlaceholderMediaSource(), finalizingAction)
    }

    /**
     * Updates the [ManagedMediaSource] in [ConcatenatingMediaSource]
     * at the given index with a given [ManagedMediaSource].
     * @see .update
     */
    @Synchronized
    fun update(index: Int, source: ManagedMediaSource) {
        update(index, source, null)
    }

    /**
     * Updates the [ManagedMediaSource] in [ConcatenatingMediaSource]
     * at the given index with a given [ManagedMediaSource]. If the index is out of bound,
     * then the replacement is ignored.
     * @see ConcatenatingMediaSource.addMediaSource
     *
     * @see ConcatenatingMediaSource.removeMediaSource
     */
    @Synchronized
    fun update(index: Int, source: ManagedMediaSource,
               finalizingAction: Runnable?) {
        if (index < 0 || index >= internalMediaSource.size) return

        // Add and remove are sequential on the same thread, therefore here, the exoplayer
        // message queue must receive and process add before remove, effectively treating them
        // as atomic.

        // Since the finalizing action occurs strictly after the timeline has completed
        // all its changes on the playback thread, thus, it is possible, in the meantime,
        // other calls that modifies the playlist media source occur in between. This makes
        // it unsafe to call remove as the finalizing action of add.
        internalMediaSource.addMediaSource(index + 1, source)

        // Because of the above race condition, it is thus only safe to synchronize the simpleExoPlayer
        // in the finalizing action AFTER the removal is complete and the timeline has changed.
        internalMediaSource.removeMediaSource(index, Handler(), finalizingAction)
    }
}
