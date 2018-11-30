package org.schabi.newpipe.fragments.list.channel

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.InfoItemDialog
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.subscription.SubscriptionService
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.*
import org.schabi.newpipe.util.AnimationUtils.animateBackgroundColor
import org.schabi.newpipe.util.AnimationUtils.animateTextColor
import org.schabi.newpipe.util.AnimationUtils.animateView
import java.util.*
import java.util.concurrent.TimeUnit

class ChannelFragment : BaseListInfoFragment<ChannelInfo>() {

    private val disposables = CompositeDisposable()
    private var subscribeButtonMonitor: Disposable? = null
    private var subscriptionService: SubscriptionService? = null

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private var headerRootLayout: View? = null
    private var headerChannelBanner: ImageView? = null
    private var headerAvatarView: ImageView? = null
    private var headerTitleView: TextView? = null
    private var headerSubscribersTextView: TextView? = null
    private var headerSubscribeButton: Button? = null
    private var playlistCtrl: View? = null

    private var headerPlayAllButton: LinearLayout? = null
    private var headerPopupButton: LinearLayout? = null
    private var headerBackgroundButton: LinearLayout? = null

    private var menuRssButton: MenuItem? = null

    private val playQueue: PlayQueue
        get() = getPlayQueue(0)

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (activity != null
                && useAsFrontPage
                && isVisibleToUser) {
            setTitle(if (currentInfo != null) currentInfo!!.name else name)
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        subscriptionService = SubscriptionService.getInstance(activity!!)
        Log.d(TAG, "ChannelFragment::onAttach called")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_channel, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables?.clear()
        if (subscribeButtonMonitor != null) subscribeButtonMonitor!!.dispose()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    override fun getListHeader(): View? {
        headerRootLayout = activity!!.layoutInflater.inflate(R.layout.channel_header, itemsList, false)
        headerChannelBanner = headerRootLayout!!.findViewById(R.id.channel_banner_image)
        headerAvatarView = headerRootLayout!!.findViewById(R.id.channel_avatar_view)
        headerTitleView = headerRootLayout!!.findViewById(R.id.channel_title_view)
        headerSubscribersTextView = headerRootLayout!!.findViewById(R.id.channel_subscriber_view)
        headerSubscribeButton = headerRootLayout!!.findViewById(R.id.channel_subscribe_button)
        playlistCtrl = headerRootLayout!!.findViewById(R.id.playlist_control)


        headerPlayAllButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_all_button)
        headerPopupButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_popup_button)
        headerBackgroundButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_bg_button)

        return headerRootLayout
    }

    override fun showStreamDialog(item: StreamInfoItem) {
        val activity = getActivity()
        val context = context
        if (context == null || context.resources == null || getActivity() == null) return

        val commands = arrayOf(context.resources.getString(R.string.enqueue_on_background), context.resources.getString(R.string.enqueue_on_popup), context.resources.getString(R.string.start_here_on_main), context.resources.getString(R.string.start_here_on_background), context.resources.getString(R.string.start_here_on_popup), context.resources.getString(R.string.append_playlist), context.resources.getString(R.string.share))

        val actions = DialogInterface.OnClickListener{ dialogInterface: DialogInterface, i: Int ->
            val index = Math.max(infoListAdapter!!.itemsList.indexOf(item), 0)
            when (i) {
                0 -> NavigationHelper.enqueueOnBackgroundPlayer(context, SinglePlayQueue(item))
                1 -> NavigationHelper.enqueueOnPopupPlayer(activity, SinglePlayQueue(item))
                2 -> NavigationHelper.playOnMainPlayer(context, getPlayQueue(index))
                3 -> NavigationHelper.playOnBackgroundPlayer(context, getPlayQueue(index))
                4 -> NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(index))
                5 -> if (fragmentManager != null) {
                    PlaylistAppendDialog.fromStreamInfoItems(listOf(item))
                            .show(fragmentManager!!, TAG)
                }
                6 -> shareUrl(item.name, item.url)
                else -> {
                }
            }
        }

        InfoItemDialog(getActivity()!!, item, commands, actions).show()
    }
    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar = activity!!.supportActionBar
        if (useAsFrontPage && supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false)
        } else {
            inflater!!.inflate(R.menu.menu_channel, menu)

            if (DEBUG)
                Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu +
                        "], inflater = [" + inflater + "]")
            menuRssButton = menu!!.findItem(R.id.menu_item_rss)
        }
    }

    private fun openRssFeed() {
        val info = currentInfo
        if (info != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.feedUrl))
            startActivity(intent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_item_rss -> openRssFeed()
            R.id.menu_item_openInBrowser -> openUrlInBrowser(url)
            R.id.menu_item_share -> shareUrl(name, url)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun monitorSubscription(info: ChannelInfo) {
        val onError = Consumer<Throwable>{ throwable: Throwable ->
            animateView(headerSubscribeButton!!, false, 100)
            showSnackBarError(throwable, UserAction.SUBSCRIPTION,
                    NewPipe.getNameOfService(currentInfo!!.serviceId),
                    "Get subscription status",
                    0)
        }

        val observable = subscriptionService!!.subscriptionTable()
                .getSubscription(info.serviceId, info.url)
                .toObservable()

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError))

        disposables.add(observable
                // Some updates are very rapid (when calling the updateSubscription(info), for example)
                // so only update the UI for the latest emission ("sync" the subscribe button's state)
                .debounce(100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer{ subscriptionEntities: List<SubscriptionEntity> -> updateSubscribeButton(!subscriptionEntities.isEmpty()) }, onError))

    }

    private fun mapOnSubscribe(subscription: SubscriptionEntity): Function<Any, Any> {
        return Function{ o: Any ->
            subscriptionService!!.subscriptionTable().insert(subscription)
            o
        }
    }

    private fun mapOnUnsubscribe(subscription: SubscriptionEntity): Function<Any, Any> {
        return Function{ o: Any ->
            subscriptionService!!.subscriptionTable().delete(subscription)
            o
        }
    }

    private fun updateSubscription(info: ChannelInfo) {
        if (DEBUG) Log.d(TAG, "updateSubscription() called with: info = [$info]")
        val onComplete = { if (DEBUG) Log.d(TAG, "Updated subscription: " + info.url) }

        val onError = { throwable: Throwable ->
            onUnrecoverableError(throwable,
                    UserAction.SUBSCRIPTION,
                    NewPipe.getNameOfService(info.serviceId),
                    "Updating Subscription for " + info.url,
                    R.string.subscription_update_failed)
        }

        disposables.add(subscriptionService!!.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError))
    }

    private fun monitorSubscribeButton(subscribeButton: Button?, action: Function<Any, Any>): Disposable {
        val onNext = { o: Any -> if (DEBUG) Log.d(TAG, "Changed subscription status to this channel!") }

        val onError = { throwable: Throwable ->
            onUnrecoverableError(throwable,
                    UserAction.SUBSCRIPTION,
                    NewPipe.getNameOfService(currentInfo!!.serviceId),
                    "Subscription Change",
                    R.string.subscription_change_failed)
        }

        /* Emit clicks from main thread unto io thread */
        return RxView.clicks(subscribeButton!!)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .debounce(BUTTON_DEBOUNCE_INTERVAL.toLong(), TimeUnit.MILLISECONDS) // Ignore rapid clicks
                .map(action)
                .subscribe(onNext, onError)
    }

    private fun getSubscribeUpdateMonitor(info: ChannelInfo): Consumer<List<SubscriptionEntity>> {
        return Consumer{ subscriptionEntities: List<SubscriptionEntity> ->
            if (DEBUG)
                Log.d(TAG, "subscriptionService.subscriptionTable.doOnNext() called with: subscriptionEntities = [$subscriptionEntities]")
            if (subscribeButtonMonitor != null) subscribeButtonMonitor!!.dispose()

            if (subscriptionEntities.isEmpty()) {
                if (DEBUG) Log.d(TAG, "No subscription to this channel!")
                val channel = SubscriptionEntity()
                channel.serviceId = info.serviceId
                channel.url = info.url
                channel.setData(info.name,
                        info.avatarUrl,
                        info.description,
                        info.subscriberCount)
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnSubscribe(channel))
            } else {
                if (DEBUG) Log.d(TAG, "Found subscription to this channel!")
                val subscription = subscriptionEntities[0]
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton, mapOnUnsubscribe(subscription))
            }
        }
    }

    private fun updateSubscribeButton(isSubscribed: Boolean) {
        if (DEBUG) Log.d(TAG, "updateSubscribeButton() called with: isSubscribed = [$isSubscribed]")

        val isButtonVisible = headerSubscribeButton!!.visibility == View.VISIBLE
        val backgroundDuration = if (isButtonVisible) 300 else 0
        val textDuration = if (isButtonVisible) 200 else 0

        val subscribeBackground = ContextCompat.getColor(activity!!, R.color.subscribe_background_color)
        val subscribeText = ContextCompat.getColor(activity!!, R.color.subscribe_text_color)
        val subscribedBackground = ContextCompat.getColor(activity!!, R.color.subscribed_background_color)
        val subscribedText = ContextCompat.getColor(activity!!, R.color.subscribed_text_color)

        if (!isSubscribed) {
            headerSubscribeButton!!.setText(R.string.subscribe_button_title)
            animateBackgroundColor(headerSubscribeButton!!, backgroundDuration.toLong(), subscribedBackground, subscribeBackground)
            animateTextColor(headerSubscribeButton!!, textDuration.toLong(), subscribedText, subscribeText)
        } else {
            headerSubscribeButton!!.setText(R.string.subscribed_button_title)
            animateBackgroundColor(headerSubscribeButton!!, backgroundDuration.toLong(), subscribeBackground, subscribedBackground)
            animateTextColor(headerSubscribeButton!!, textDuration.toLong(), subscribeText, subscribedText)
        }

        animateView(headerSubscribeButton!!, AnimationUtils.Type.LIGHT_SCALE_AND_ALPHA, true, 100)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load and handle
    ///////////////////////////////////////////////////////////////////////////

    override fun loadMoreItemsLogic(): Single<ListExtractor.InfoItemsPage<*>> {
        return ExtractorHelper.getMoreChannelItems(serviceId, url, currentNextPageUrl)
    }

    override fun loadResult(forceLoad: Boolean): Single<ChannelInfo> {
        return ExtractorHelper.getChannelInfo(serviceId, url, forceLoad)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()

        BaseFragment.imageLoader.cancelDisplayTask(headerChannelBanner!!)
        BaseFragment.imageLoader.cancelDisplayTask(headerAvatarView!!)
        animateView(headerSubscribeButton!!, false, 100)
    }

    override fun handleResult(result: ChannelInfo) {
        super.handleResult(result)

        headerRootLayout!!.visibility = View.VISIBLE
        BaseFragment.imageLoader.displayImage(result.bannerUrl, headerChannelBanner!!,
                ImageDisplayConstants.DISPLAY_BANNER_OPTIONS)
        BaseFragment.imageLoader.displayImage(result.avatarUrl, headerAvatarView!!,
                ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS)

        headerSubscribersTextView!!.visibility = View.VISIBLE
        if (result.subscriberCount >= 0) {
            headerSubscribersTextView!!.text = Localization.localizeSubscribersCount(activity!!, result.subscriberCount)
        } else {
            headerSubscribersTextView!!.setText(R.string.subscribers_count_not_available)
        }

        if (menuRssButton != null) menuRssButton!!.isVisible = !TextUtils.isEmpty(result.feedUrl)

        playlistCtrl!!.visibility = View.VISIBLE

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_CHANNEL, NewPipe.getNameOfService(result.serviceId), result.url, 0)
        }

        disposables?.clear()
        if (subscribeButtonMonitor != null) subscribeButtonMonitor!!.dispose()
        updateSubscription(result)
        monitorSubscription(result)

        headerPlayAllButton!!.setOnClickListener { view -> NavigationHelper.playOnMainPlayer(activity, playQueue) }
        headerPopupButton!!.setOnClickListener { view -> NavigationHelper.playOnPopupPlayer(activity, playQueue) }
        headerBackgroundButton!!.setOnClickListener { view -> NavigationHelper.playOnBackgroundPlayer(activity, playQueue) }
    }

    private fun getPlayQueue(index: Int): PlayQueue {
        val streamItems = ArrayList<StreamInfoItem>()
        for (i in infoListAdapter!!.itemsList) {
            if (i is StreamInfoItem) {
                streamItems.add(i)
            }
        }
        return ChannelPlayQueue(
                currentInfo!!.serviceId,
                currentInfo!!.url,
                currentInfo!!.nextPageUrl,
                streamItems,
                index
        )
    }

    override fun handleNextItems(result: ListExtractor.InfoItemsPage<*>) {
        super.handleNextItems(result)

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors,
                    UserAction.REQUESTED_CHANNEL,
                    NewPipe.getNameOfService(serviceId),
                    "Get next page of: $url",
                    R.string.general_error)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // OnError
    ///////////////////////////////////////////////////////////////////////////

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        val errorId = if (exception is ExtractionException) R.string.parsing_error else R.string.general_error
        onUnrecoverableError(exception,
                UserAction.REQUESTED_CHANNEL,
                NewPipe.getNameOfService(serviceId),
                url,
                errorId)
        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    override fun setTitle(title: String) {
        super.setTitle(title)
        if (!useAsFrontPage) headerTitleView!!.text = title
    }

    companion object {

        fun getInstance(serviceId: Int, url: String, name: String): ChannelFragment {
            val instance = ChannelFragment()
            instance.setInitialData(serviceId, url, name)
            return instance
        }

        ///////////////////////////////////////////////////////////////////////////
        // Channel Subscription
        ///////////////////////////////////////////////////////////////////////////

        private const val BUTTON_DEBOUNCE_INTERVAL = 100
    }
}
