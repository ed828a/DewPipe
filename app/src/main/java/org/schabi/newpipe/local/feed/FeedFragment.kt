package org.schabi.newpipe.local.feed

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import io.reactivex.Flowable
import io.reactivex.MaybeObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.local.subscription.SubscriptionService
import org.schabi.newpipe.report.UserAction
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FeedFragment : BaseListFragment<List<SubscriptionEntity>, Void>() {


    private var subscriptionPoolSize: Int = 0

    private var subscriptionService: SubscriptionService? = null

    private var allItemsLoaded = AtomicBoolean(false)  // StateSaver take care this
    private var itemsLoaded = HashSet<String>()  // StateSaver take care this
    private val requestLoadedAtomic = AtomicInteger()

//    private var compositeDisposable: CompositeDisposable? = CompositeDisposable()
    private var subscriptionObserver: Disposable? = null
    private var feedSubscriber: Subscription? = null

    private val delayHandler = Handler()

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionService = SubscriptionService.getInstance(activity!!)

        FEED_LOAD_COUNT = howManyItemsToLoad()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (!useAsFrontPage) {
            setTitle(activity!!.getString(R.string.fragment_whats_new))
        }
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onPause() {
        super.onPause()
        disposeEverything()
    }

    override fun onResume() {
        super.onResume()
        if (wasLoading.get()) doInitialLoadLogic()
    }

    override fun onDestroy() {
        super.onDestroy()

        disposeEverything()
        subscriptionService = null
        if (!compositeDisposable.isDisposed) compositeDisposable.dispose()
        subscriptionObserver = null
        feedSubscriber = null
    }

    override fun onDestroyView() {
        // Do not monitor for updates when user is not viewing the feed fragment.
        // This is a waste of bandwidth.
        disposeEverything()
        super.onDestroyView()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (activity != null && isVisibleToUser) {
            setTitle(activity!!.getString(R.string.fragment_whats_new))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = activity!!.supportActionBar

        if (useAsFrontPage) {
            supportActionBar?.setDisplayShowTitleEnabled(true)
//            supportActionBar?.setDisplayShowTitleEnabled(false)
        }
    }

    override fun reloadContent() {
        resetFragment()
        super.reloadContent()
    }

    ///////////////////////////////////////////////////////////////////////////
    // StateSaving
    ///////////////////////////////////////////////////////////////////////////

    override fun writeTo(objectsToSave: Queue<Any>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(allItemsLoaded)
        objectsToSave.add(itemsLoaded)
    }

    @Throws(Exception::class)
    override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        allItemsLoaded = savedObjects.poll() as AtomicBoolean
        itemsLoaded = savedObjects.poll() as HashSet<String>
    }

    ///////////////////////////////////////////////////////////////////////////
    // Feed Loader
    ///////////////////////////////////////////////////////////////////////////

    public override fun startLoading(forceLoad: Boolean) {
        Log.d(TAG, "startLoading() called with: forceLoad = [$forceLoad]")
        subscriptionObserver?.dispose()

        if (allItemsLoaded.get()) {
            if (infoListAdapter!!.itemsList.size == 0) {
                showEmptyState()
            } else {
                showListFooter(false)
                hideLoading()
            }

            isLoading.set(false)
            return
        }

        isLoading.set(true)
        showLoading()
        showListFooter(true)
        subscriptionObserver = subscriptionService!!.subscription
                .onErrorReturnItem(emptyList())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({list -> this.handleResult(list) },
                        {error -> this.onError(error) })
    }

    override fun handleResult(result: List<SubscriptionEntity>) {
        super.handleResult(result)

        if (result.isEmpty()) {
            infoListAdapter!!.clearStreamItemList()
            showEmptyState()
            return
        }

        subscriptionPoolSize = result.size
        Flowable.fromIterable(result)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver())
    }

    /**
     * Responsible for reacting to user pulling request and starting a request for new feed stream.
     *
     * On initialization, it automatically requests the amount of feed needed to display
     * a minimum amount required (FEED_LOAD_SIZE).
     *
     * Upon receiving a user pull, it creates a Single Observer to fetch the ChannelInfo
     * containing the feed streams.
     */
    private fun getSubscriptionObserver(): Subscriber<SubscriptionEntity> {
        return object : Subscriber<SubscriptionEntity> {
            override fun onSubscribe(subscription: Subscription) {
                feedSubscriber?.cancel()
                feedSubscriber = subscription

                var requestSize = FEED_LOAD_COUNT - infoListAdapter!!.itemsList.size
                if (wasLoading.getAndSet(false)) requestSize = FEED_LOAD_COUNT

                val hasToLoad = requestSize > 0
                if (hasToLoad) {
                    requestLoadedAtomic.set(infoListAdapter!!.itemsList.size)
                    requestFeed(requestSize)
                }
                isLoading.set(hasToLoad)
            }

            override fun onNext(subscriptionEntity: SubscriptionEntity) {
                Log.d(TAG, "subscriptionEntity.serviceId.toString() + subscriptionEntity.url = ${subscriptionEntity.serviceId.toString() + subscriptionEntity.url}")
                if (!itemsLoaded.contains(subscriptionEntity.serviceId.toString() + subscriptionEntity.url)) {
                    subscriptionService!!.getChannelInfo(subscriptionEntity)
                            .observeOn(AndroidSchedulers.mainThread())
                            .onErrorComplete { throwable: Throwable -> super@FeedFragment.onError(throwable) }
                            .subscribe(getChannelInfoObserver(subscriptionEntity.serviceId, subscriptionEntity.url!!))

                } else {
                    requestFeed(1)
                }
            }

            override fun onError(exception: Throwable) {
                this@FeedFragment.onError(exception)
            }

            override fun onComplete() {
                if (DEBUG) Log.d(TAG, "getSubscriptionObserver > onComplete() called")
            }
        }
    }

    /**
     * On each request, a subscription item getTabFrom the updated table is transformed
     * into a ChannelInfo, containing the latest streams getTabFrom the channel.
     *
     *
     * Currently, the feed uses the first into getTabFrom the list of streams.
     *
     *
     * If chosen feed already displayed, then we request another feed getTabFrom another
     * subscription, until the subscription table runs out of new items.
     *
     *
     * This Observer is self-contained and will dispose itself when complete. However, this
     * does not obey the fragment lifecycle and may continue running in the background
     * until it is complete. This is done due to RxJava2 no longer propagate errors once
     * an observer is unsubscribed while the thread process is still running.
     *
     *
     * To solve the above issue, we can either set a global RxJava Error Handler, or
     * manage exceptions case by case. This should be done if the current implementation is
     * too costly when dealing with larger subscription sets.
     *
     * @param url + serviceId to put in [.allItemsLoaded] to signal that this specific entity has been loaded.
     */
    private fun getChannelInfoObserver(serviceId: Int, url: String): MaybeObserver<ChannelInfo> {
        return object : MaybeObserver<ChannelInfo> {
            private var observer: Disposable? = null

            override fun onSubscribe(d: Disposable) {
                observer = d
                compositeDisposable.add(d)
                isLoading.set(true)
            }

            // Called only when response is non-empty
            override fun onSuccess(channelInfo: ChannelInfo) {
//                if (infoListAdapter == null || channelInfo.relatedItems.isEmpty()) {
//                    onDone()
//                    return
//                }
                if (infoListAdapter != null && channelInfo.relatedItems.isNotEmpty()) {
                    // the feed uses the first into getTabFrom the list of streams.
                    val item = channelInfo.relatedItems[0]
                    // Keep requesting new items if the current one already exists
                    val itemExists = doesItemExist(infoListAdapter!!.itemsList, item)
                    if (!itemExists) {
                        infoListAdapter!!.addInfoItem(item)
                        //updateSubscription(channelInfo);
                    } else {
                        requestFeed(1)
                    }
                }
                onDone()
            }

            override fun onError(exception: Throwable) {
                showSnackBarError(exception,
                        UserAction.SUBSCRIPTION,
                        NewPipe.getNameOfService(serviceId),
                        url, 0)
                requestFeed(1)
                onDone()
            }

            // Called only when response is empty
            override fun onComplete() {
                onDone()
            }

            private fun onDone() {
                if (observer!!.isDisposed) {
                    return
                }

                itemsLoaded.add(serviceId.toString() + url)
                compositeDisposable.remove(observer!!)

                val loaded = requestLoadedAtomic.incrementAndGet()
                if (loaded >= Math.min(FEED_LOAD_COUNT, subscriptionPoolSize)) {
                    requestLoadedAtomic.set(0)
                    isLoading.set(false)
                }

                if (itemsLoaded.size == subscriptionPoolSize) {
                    Log.d(TAG, "getChannelInfoObserver > All Items Loaded")
                    allItemsLoaded.set(true)
                    showListFooter(false)
                    isLoading.set(false)
                    hideLoading()
                    if (infoListAdapter!!.itemsList.size == 0) {
                        showEmptyState()
                    }
                }
            }
        }
    }

    override fun loadMoreItems() {
        isLoading.set(true)
        delayHandler.removeCallbacksAndMessages(null)
        // Add a little of a delay when requesting more items because the cache is so fast,
        // that the view seems stuck to the user when he scroll to the bottom
        delayHandler.postDelayed({ requestFeed(FEED_LOAD_COUNT) }, 300)
    }

    override fun hasMoreItems(): Boolean = !allItemsLoaded.get()


    private fun requestFeed(count: Int) {
        Log.d(TAG, "requestFeed() called with: count = [$count], feedSubscriber = [$feedSubscriber]")
        if (feedSubscriber == null) return

        isLoading.set(true)
        delayHandler.removeCallbacksAndMessages(null)
        feedSubscriber!!.request(count.toLong())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun resetFragment() {
        Log.d(TAG, "resetFragment() called")
        subscriptionObserver?.dispose()
        compositeDisposable.clear()
        infoListAdapter?.clearStreamItemList()

        delayHandler.removeCallbacksAndMessages(null)
        requestLoadedAtomic.set(0)
        allItemsLoaded.set(false)
        showListFooter(false)
        itemsLoaded.clear()
    }

    private fun disposeEverything() {
        subscriptionObserver?.dispose()
        compositeDisposable.clear()
        feedSubscriber?.cancel()
        delayHandler.removeCallbacksAndMessages(null)
    }

    private fun doesItemExist(items: List<InfoItem>, item: InfoItem): Boolean {
        for (existingItem in items) {
            if (existingItem.infoType == item.infoType &&
                    existingItem.serviceId == item.serviceId &&
                    existingItem.name == item.name &&
                    existingItem.url == item.url)
                return true
        }
        return false
    }

    private fun howManyItemsToLoad(): Int {
        val heightPixels = resources.displayMetrics.heightPixels
        val itemHeightPixels = activity!!.resources.getDimensionPixelSize(R.dimen.video_item_search_height)

        val items = if (itemHeightPixels > 0)
            heightPixels / itemHeightPixels + OFF_SCREEN_ITEMS_COUNT
        else
            MIN_ITEMS_INITIAL_LOAD
        return Math.max(MIN_ITEMS_INITIAL_LOAD, items)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    override fun showError(message: String, showRetryButton: Boolean) {
        resetFragment()
        super.showError(message, showRetryButton)
    }

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        val errorId = if (exception is ExtractionException)
            R.string.parsing_error
        else
            R.string.general_error
        onUnrecoverableError(exception,
                UserAction.SOMETHING_ELSE,
                "none",
                "Requesting feed",
                errorId)
        return true
    }

    companion object {
        private const val OFF_SCREEN_ITEMS_COUNT = 3
        private const val MIN_ITEMS_INITIAL_LOAD = 8
        private var FEED_LOAD_COUNT = MIN_ITEMS_INITIAL_LOAD
    }
}
