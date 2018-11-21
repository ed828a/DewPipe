package org.schabi.newpipe.player.playqueue

import android.util.Log
import io.reactivex.SingleObserver
import io.reactivex.annotations.NonNull
import io.reactivex.disposables.Disposable
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.*

abstract class AbstractInfoPlayQueue<T : ListInfo<*>, U : InfoItem>(
        val serviceId: Int,
        val baseUrl: String,
        var nextUrl: String?,
        streams: List<StreamInfoItem>,
        index: Int) : PlayQueue(index, extractListItems(streams)) {
    var isInitial: Boolean = false
    override var isComplete: Boolean = false

    @Transient
    var fetchReactor: Disposable? = null

    protected abstract val tag: String

    // Notify change
    val headListObserver: SingleObserver<T>
        get() = object : SingleObserver<T> {
            override fun onSubscribe(@NonNull d: Disposable) {
                if (isComplete || !isInitial || fetchReactor != null && !fetchReactor!!.isDisposed) {
                    d.dispose()
                } else {
                    fetchReactor = d
                }
            }

            override fun onSuccess(@NonNull result: T) {
                isInitial = false
                if (!result.hasNextPage()) isComplete = true
                nextUrl = result.nextPageUrl

                append(extractListItems(result.relatedItems))

                fetchReactor!!.dispose()
                fetchReactor = null
            }

            override fun onError(@NonNull e: Throwable) {
                Log.e(tag, "Error fetching more playlist, marking playlist as complete.", e)
                isComplete = true
                append()
            }
        }

    // Notify change
    val nextPageObserver: SingleObserver<ListExtractor.InfoItemsPage<*>>
        get() = object : SingleObserver<ListExtractor.InfoItemsPage<*>> {
            override fun onSubscribe(@NonNull d: Disposable) {
                if (isComplete || isInitial || fetchReactor != null && !fetchReactor!!.isDisposed) {
                    d.dispose()
                } else {
                    fetchReactor = d
                }
            }

            override fun onSuccess(@NonNull result: ListExtractor.InfoItemsPage<*>) {
                if (!result.hasNextPage()) isComplete = true
                nextUrl = result.nextPageUrl

                append(extractListItems(result.getItems()))

                fetchReactor!!.dispose()
                fetchReactor = null
            }

            override fun onError(@NonNull e: Throwable) {
                Log.e(tag, "Error fetching more playlist, marking playlist as complete.", e)
                isComplete = true
                append()
            }
        }

    constructor(item: U) : this(item.serviceId, item.url, null, emptyList<StreamInfoItem>(), 0) {}

    init {

        this.isInitial = streams.isEmpty()
        this.isComplete = !isInitial && (nextUrl == null || nextUrl!!.isEmpty())
    }

    override fun dispose() {
        super.dispose()
        if (fetchReactor != null) fetchReactor!!.dispose()
        fetchReactor = null
    }

    companion object {
        private fun extractListItems(infos: List<out InfoItem>): List<PlayQueueItem> {
            val result = ArrayList<PlayQueueItem>()
            for (stream in infos) {
                if (stream is StreamInfoItem) {
                    result.add(PlayQueueItem(stream))
                }
            }
            return result
        }
    }


}
