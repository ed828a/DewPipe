package org.schabi.newpipe.player.playqueue

import android.util.Log

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.player.playqueue.events.AppendEvent
import org.schabi.newpipe.player.playqueue.events.ErrorEvent
import org.schabi.newpipe.player.playqueue.events.InitEvent
import org.schabi.newpipe.player.playqueue.events.MoveEvent
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent
import org.schabi.newpipe.player.playqueue.events.RecoveryEvent
import org.schabi.newpipe.player.playqueue.events.RemoveEvent
import org.schabi.newpipe.player.playqueue.events.ReorderEvent
import org.schabi.newpipe.player.playqueue.events.SelectEvent

import java.io.Serializable
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject

/**
 * PlayQueue is responsible for keeping track of a list of streams and the index of
 * the stream that should be currently playing.
 *
 * This class contains basic manipulation of a playlist while also functions as a
 * message bus, providing all listeners with new updates to the play queue.
 *
 * This class can be serialized for passing intents, but in order to start the
 * message bus, it must be initialized.
 */
abstract class PlayQueue internal constructor(index: Int, startWith: List<PlayQueueItem>) : Serializable {
    private val TAG = "PlayQueue@" + Integer.toHexString(hashCode())

    private var backup: ArrayList<PlayQueueItem>? = null
    var streams: ArrayList<PlayQueueItem>? = null
    private val queueIndex: AtomicInteger

    @Transient
    private var eventBroadcast: BehaviorSubject<PlayQueueEvent>? = null
    /**
     * Returns the play queue's update broadcast.
     * May be null if the play queue message bus is not initialized.
     */
    @Transient
    var broadcastReceiver: Flowable<PlayQueueEvent>? = null
        private set
    @Transient
    private var reportingReactor: Subscription? = null

    /**
     * Checks if the queue is complete.
     *
     * A queue is complete if it has loaded all items in an external playlist
     * single stream or local queues are always complete.
     */
    abstract val isComplete: Boolean

    ///////////////////////////////////////////////////////////////////////////
    // Readonly ops
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the current index that should be played.
     */
    ///////////////////////////////////////////////////////////////////////////
    // Write ops
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Changes the current playing index to a new index.
     *
     * This method is guarded using in a circular manner for index exceeding the play queue size.
     *
     * Will emit a [SelectEvent] if the index is not the current playing index.
     */
    var index: Int
        get() = queueIndex.get()
        @Synchronized set(index) {
            val oldIndex = index

            var newIndex = index
            if (index < 0) newIndex = 0
            if (index >= streams!!.size) newIndex = if (isComplete) index % streams!!.size else streams!!.size - 1

            queueIndex.set(newIndex)
            broadcast(SelectEvent(oldIndex, newIndex))
        }

    /**
     * Returns the current item that should be played.
     */
    val item: PlayQueueItem?
        get() = getItem(index)

    /**
     * Checks if the play queue is empty.
     */
    val isEmpty: Boolean
        get() = streams!!.isEmpty()

    /**
     * Determines if the current play queue is shuffled.
     */
    val isShuffled: Boolean
        get() = backup != null

    private val selfReporter: Subscriber<PlayQueueEvent>
        get() = object : Subscriber<PlayQueueEvent> {
            override fun onSubscribe(s: Subscription) {
                if (reportingReactor != null) reportingReactor!!.cancel()
                reportingReactor = s
                reportingReactor!!.request(1)
            }

            override fun onNext(event: PlayQueueEvent) {
                Log.d(TAG, "Received broadcast: " + event.type().name + ". Current index: " + index + ", play queue length: " + size() + ".")
                reportingReactor!!.request(1)
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "Received broadcast error", t)
            }

            override fun onComplete() {
                Log.d(TAG, "Broadcast is shutting down.")
            }
        }

    init {
        streams = ArrayList()
        streams!!.addAll(startWith)

        queueIndex = AtomicInteger(index)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist actions
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Initializes the play queue message buses.
     *
     * Also starts a self reporter for logging if debug mode is enabled.
     */
    fun init() {
        eventBroadcast = BehaviorSubject.create()

        broadcastReceiver = eventBroadcast!!.toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(AndroidSchedulers.mainThread())
                .startWith(InitEvent())

        if (DEBUG) broadcastReceiver!!.subscribe(selfReporter)
    }

    /**
     * Dispose the play queue by stopping all message buses.
     */
    open fun dispose() {
        if (eventBroadcast != null) eventBroadcast!!.onComplete()
        if (reportingReactor != null) reportingReactor!!.cancel()

        eventBroadcast = null
        broadcastReceiver = null
        reportingReactor = null
    }

    /**
     * Load partial queue in the background, does nothing if the queue is complete.
     */
    abstract fun fetch()

    /**
     * Returns the item at the given index.
     * May throw [IndexOutOfBoundsException].
     */
    fun getItem(index: Int): PlayQueueItem? {
        return if (index < 0 || index >= streams!!.size || streams!![index] == null) null else streams!![index]
    }

    /**
     * Returns the index of the given item using referential equality.
     * May be null despite play queue contains identical item.
     */
    fun indexOf(item: PlayQueueItem): Int {
        // referential equality, can't think of a better way to do this
        // todo: better than this
        return streams!!.indexOf(item)
    }

    /**
     * Returns the current size of play queue.
     */
    fun size(): Int {
        return streams!!.size
    }

    /**
     * Returns an immutable view of the play queue.
     */
    fun getStreams(): List<PlayQueueItem> {
        return Collections.unmodifiableList(streams)
    }

    /**
     * Changes the current playing index by an offset amount.
     *
     * Will emit a [SelectEvent] if offset is non-zero.
     */
    @Synchronized
    fun offsetIndex(offset: Int) {
        index = index + offset
    }

    /**
     * Appends the given [PlayQueueItem]s to the current play queue.
     *
     * @see .append
     */
    @Synchronized
    fun append(vararg items: PlayQueueItem) {
        append(Arrays.asList(*items))
    }

    /**
     * Appends the given [PlayQueueItem]s to the current play queue.
     *
     * If the play queue is shuffled, then append the items to the backup queue as is and
     * append the shuffle items to the play queue.
     *
     * Will emit a [AppendEvent] on any given context.
     */
    @Synchronized
    fun append(items: List<PlayQueueItem>) {
        val itemList = ArrayList(items)

        if (isShuffled) {
            backup!!.addAll(itemList)
            Collections.shuffle(itemList)
        }
        streams!!.addAll(itemList)

        broadcast(AppendEvent(itemList.size))
    }

    /**
     * Removes the item at the given index getTabFrom the play queue.
     *
     * The current playing index will decrement if it is greater than the index being removed.
     * On cases where the current playing index exceeds the playlist range, it is set to 0.
     *
     * Will emit a [RemoveEvent] if the index is within the play queue index range.
     */
    @Synchronized
    fun remove(index: Int) {
        if (index >= streams!!.size || index < 0) return
        removeInternal(index)
        broadcast(RemoveEvent(index, index))
    }

    /**
     * Report an exception for the item at the current index in order and the course of action:
     * if the error can be skipped or the current item should be removed.
     *
     * This is done as a separate event as the underlying manager may have
     * different implementation regarding exceptions.
     */
    @Synchronized
    fun error(skippable: Boolean) {
        val index = index

        if (skippable) {
            queueIndex.incrementAndGet()
        } else {
            removeInternal(index)
        }

        broadcast(ErrorEvent(index, index, skippable))
    }

    @Synchronized
    private fun removeInternal(removeIndex: Int) {
        val currentIndex = queueIndex.get()
        val size = size()

        if (currentIndex > removeIndex) {
            queueIndex.decrementAndGet()

        } else if (currentIndex >= size) {
            queueIndex.set(currentIndex % (size - 1))

        } else if (currentIndex == removeIndex && currentIndex == size - 1) {
            queueIndex.set(0)
        }

        if (backup != null) {
            val backupIndex = backup!!.indexOf(getItem(removeIndex))
            backup!!.removeAt(backupIndex)
        }
        streams!!.removeAt(removeIndex)
    }

    /**
     * Moves a queue item at the source index to the target index.
     *
     * If the item being moved is the currently playing, then the current playing index is set
     * to that of the target.
     * If the moved item is not the currently playing and moves to an index **AFTER** the
     * current playing index, then the current playing index is decremented.
     * Vice versa if the an item after the currently playing is moved **BEFORE**.
     */
    @Synchronized
    fun move(source: Int, target: Int) {
        if (source < 0 || target < 0) return
        if (source >= streams!!.size || target >= streams!!.size) return

        val current = index
        if (source == current) {
            queueIndex.set(target)
        } else if (source < current && target >= current) {
            queueIndex.decrementAndGet()
        } else if (source > current && target <= current) {
            queueIndex.incrementAndGet()
        }

        streams!!.add(target, streams!!.removeAt(source))
        broadcast(MoveEvent(source, target))
    }

    /**
     * Sets the recovery record of the item at the index.
     *
     * Broadcasts a recovery event.
     */
    @Synchronized
    fun setRecovery(index: Int, position: Long) {
        if (index < 0 || index >= streams!!.size) return
        streams!![index].recoveryPosition = position
        broadcast(RecoveryEvent(index, position))
    }

    /**
     * Revoke the recovery record of the item at the index.
     *
     * Broadcasts a recovery event.
     */
    @Synchronized
    fun unsetRecovery(index: Int) {
        setRecovery(index, PlayQueueItem.RECOVERY_UNSET)
    }

    /**
     * Shuffles the current play queue.
     *
     * This method first backs up the existing play queue and item being played.
     * Then a newly shuffled play queue will be generated along with currently
     * playing item placed at the beginning of the queue.
     *
     * Will emit a [ReorderEvent] in any context.
     */
    @Synchronized
    fun shuffle() {
        if (backup == null) {
            backup = ArrayList(streams!!)
        }
        val originIndex = index
        val current = item
        Collections.shuffle(streams)

        val newIndex = streams!!.indexOf(current)
        if (newIndex != -1) {
            streams!!.add(0, streams!!.removeAt(newIndex))
        }
        queueIndex.set(0)

        broadcast(ReorderEvent(originIndex, queueIndex.get()))
    }

    /**
     * Unshuffles the current play queue if a backup play queue exists.
     *
     * This method undoes shuffling and index will be set to the previously playing item if found,
     * otherwise, the index will reset to 0.
     *
     * Will emit a [ReorderEvent] if a backup exists.
     */
    @Synchronized
    fun unshuffle() {
        if (backup == null) return
        val originIndex = index
        val current = item

        streams!!.clear()
        streams = backup
        backup = null

        val newIndex = streams!!.indexOf(current)
        if (newIndex != -1) {
            queueIndex.set(newIndex)
        } else {
            queueIndex.set(0)
        }

        broadcast(ReorderEvent(originIndex, queueIndex.get()))
    }

    ///////////////////////////////////////////////////////////////////////////
    // Rx Broadcast
    ///////////////////////////////////////////////////////////////////////////

    private fun broadcast(event: PlayQueueEvent) {
        if (eventBroadcast != null) {
            eventBroadcast!!.onNext(event)
        }
    }

    companion object {

        val DEBUG = BuildConfig.BUILD_TYPE != "release"
    }
}

