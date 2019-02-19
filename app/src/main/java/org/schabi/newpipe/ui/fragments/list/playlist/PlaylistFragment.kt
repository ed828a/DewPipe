package org.schabi.newpipe.ui.fragments.list.playlist

import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.InfoItemDialog
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.ui.fragments.BaseFragment
import org.schabi.newpipe.ui.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class PlaylistFragment : BaseListInfoFragment<PlaylistInfo>() {

    //    private var disposables: CompositeDisposable? = null
    private var bookmarkReactor: Subscription? = null
    private var isBookmarkButtonReady: AtomicBoolean = AtomicBoolean(false)

    private lateinit var remotePlaylistManager: RemotePlaylistManager
    private var playlistEntity: PlaylistRemoteEntity? = null
    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private lateinit var headerRootLayout: View
    private lateinit var headerTitleView: TextView
    private lateinit var headerUploaderLayout: View
    private lateinit var headerUploaderName: TextView
    private lateinit var headerUploaderAvatar: ImageView
    private lateinit var headerStreamCount: TextView
    private lateinit var playlistCtrl: View

    private lateinit var headerPlayAllButton: View
    private lateinit var headerPopupButton: View
    private lateinit var headerBackgroundButton: View

    private var playlistBookmarkButton: MenuItem? = null

    private val playQueue: PlayQueue
        get() = getPlayQueue(0)

    private val playlistBookmarkSubscriber: Subscriber<List<PlaylistRemoteEntity>>
        get() = object : Subscriber<List<PlaylistRemoteEntity>> {
            override fun onSubscribe(subscription: Subscription) {
                if (bookmarkReactor != null) bookmarkReactor!!.cancel()
                bookmarkReactor = subscription
                bookmarkReactor!!.request(1)
            }

            override fun onNext(playlist: List<PlaylistRemoteEntity>) {
                playlistEntity = if (playlist.isEmpty()) null else playlist[0]

                updateBookmarkButtons()
                isBookmarkButtonReady?.set(true)
                bookmarkReactor?.request(1)
            }

            override fun onError(t: Throwable) {
                this@PlaylistFragment.onError(t)
            }

            override fun onComplete() {

            }
        }

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        disposables = CompositeDisposable()
//        isBookmarkButtonReady = AtomicBoolean(false)
//        remotePlaylistManager = RemotePlaylistManager(NewPipeDatabase.getInstance(requireContext()))
        remotePlaylistManager = RemotePlaylistManager(AppDatabase.getDatabase(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    override fun getListHeader(): View? {
        headerRootLayout = activity!!.layoutInflater.inflate(R.layout.playlist_header, itemsList, false)

        headerTitleView = headerRootLayout.findViewById(R.id.playlist_title_view)
        headerUploaderLayout = headerRootLayout.findViewById(R.id.uploader_layout)
        headerUploaderName = headerRootLayout.findViewById(R.id.uploader_name)
        headerUploaderAvatar = headerRootLayout.findViewById(R.id.uploader_avatar_view)
        headerStreamCount = headerRootLayout.findViewById(R.id.playlist_stream_count)
        playlistCtrl = headerRootLayout.findViewById(R.id.playlist_control)

        headerPlayAllButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_all_button)
        headerPopupButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_popup_button)
        headerBackgroundButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_bg_button)

        return headerRootLayout
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        infoListAdapter!!.useMiniItemVariants(true)
    }

    override fun showStreamDialog(item: StreamInfoItem) {
        val context = context
        val activity = getActivity()
        if (context == null || context.resources == null || getActivity() == null) return

        val commands = arrayOf(context.resources.getString(R.string.enqueue_on_background), context.resources.getString(R.string.enqueue_on_popup), context.resources.getString(R.string.start_here_on_main), context.resources.getString(R.string.start_here_on_background), context.resources.getString(R.string.start_here_on_popup), context.resources.getString(R.string.share))

        val actions = DialogInterface.OnClickListener { dialogInterface, which ->
            val index = Math.max(infoListAdapter!!.itemsList.indexOf(item), 0)
            when (which) {
                0 -> NavigationHelper.enqueueOnBackgroundPlayer(context, SinglePlayQueue(item))
                1 -> NavigationHelper.enqueueOnPopupPlayer(activity, SinglePlayQueue(item))
                2 -> NavigationHelper.playOnMainPlayer(context, getPlayQueue(index))
                3 -> NavigationHelper.playOnBackgroundPlayer(context, getPlayQueue(index))
                4 -> NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(index))
                5 -> shareUrl(item.name, item.url)
                else -> {
                }
            }
        }

        InfoItemDialog(getActivity()!!, item, commands, actions).show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu], inflater = [$inflater]")
//        if (menu == null || inflater == null) return

        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_playlist, menu)

        playlistBookmarkButton = menu.findItem(R.id.menu_item_bookmark)
        updateBookmarkButtons()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        isBookmarkButtonReady.set(false)

        compositeDisposable.clear()
        if (bookmarkReactor != null) bookmarkReactor!!.cancel()
        bookmarkReactor = null
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!compositeDisposable.isDisposed) compositeDisposable.dispose()

//        disposables = null
//        remotePlaylistManager = null
        playlistEntity = null
//        isBookmarkButtonReady = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load and handle
    ///////////////////////////////////////////////////////////////////////////

    override fun loadMoreItemsLogic(): Single<ListExtractor.InfoItemsPage<*>> {
        return ExtractorHelper.getMorePlaylistItems(serviceId, url, currentNextPageUrl)
    }

    override fun loadResult(forceLoad: Boolean): Single<PlaylistInfo> {
        return ExtractorHelper.getPlaylistInfo(serviceId, url, forceLoad)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_openInBrowser -> {
                openUrlInBrowser(url)
                true
            }
            R.id.menu_item_share -> {
                shareUrl(name, url)
                true
            }
            R.id.menu_item_bookmark -> {
                onBookmarkClicked()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        animateView(headerRootLayout, false, 200)
        animateView(itemsList!!, false, 100)

        BaseFragment.imageLoader.cancelDisplayTask(headerUploaderAvatar)
        animateView(headerUploaderLayout, false, 200)
    }

    override fun handleResult(result: PlaylistInfo) {
        super.handleResult(result)

        animateView(headerRootLayout, true, 100)
        animateView(headerUploaderLayout, true, 300)
        headerUploaderLayout.setOnClickListener(null)
        if (!TextUtils.isEmpty(result.uploaderName)) {
            headerUploaderName.text = result.uploaderName
            if (!TextUtils.isEmpty(result.uploaderUrl)) {
                headerUploaderLayout.setOnClickListener { v ->
                    try {
                        NavigationHelper.openChannelFragment(fragmentManager,
                                result.serviceId,
                                result.uploaderUrl,
                                result.uploaderName)
                    } catch (e: Exception) {
                        val context = getActivity()
                        context?.let {
                            ErrorActivity.reportUiError(it as AppCompatActivity, e)
                        }
                    }
                }
            }
        }

        playlistCtrl.visibility = View.VISIBLE

        BaseFragment.imageLoader.displayImage(result.uploaderAvatarUrl, headerUploaderAvatar,
                ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS)
        headerStreamCount.text = resources.getQuantityString(R.plurals.videos,
                result.streamCount.toInt(), result.streamCount.toInt())

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_PLAYLIST, NewPipe.getNameOfService(result.serviceId), result.url, 0)
        }

        remotePlaylistManager.getPlaylist(result)
                .flatMap({ lists -> getUpdateProcessor(lists, result) },
                        { lists, id -> lists })
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistBookmarkSubscriber)

        headerPlayAllButton.setOnClickListener { view -> NavigationHelper.playOnMainPlayer(activity, playQueue) }
        headerPopupButton.setOnClickListener { view -> NavigationHelper.playOnPopupPlayer(activity, playQueue) }
        headerBackgroundButton.setOnClickListener { view -> NavigationHelper.playOnBackgroundPlayer(activity, playQueue) }
    }

    private fun getPlayQueue(index: Int): PlayQueue {
        val infoItems = ArrayList<StreamInfoItem>()
        for (infoItem in infoListAdapter!!.itemsList) {
            if (infoItem is StreamInfoItem) {
                infoItems.add(infoItem)
            }
        }
        return PlaylistPlayQueue(
                currentInfo!!.serviceId,
                currentInfo!!.url,
                currentInfo!!.nextPageUrl,
                infoItems,
                index
        )
    }

    override fun handleNextItems(result: ListExtractor.InfoItemsPage<*>) {
        super.handleNextItems(result)

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_PLAYLIST, NewPipe.getNameOfService(serviceId), "Get next page of: $url", 0)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // OnError
    ///////////////////////////////////////////////////////////////////////////

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        val errorId = if (exception is ExtractionException) R.string.parsing_error else R.string.general_error
        onUnrecoverableError(exception,
                UserAction.REQUESTED_PLAYLIST,
                NewPipe.getNameOfService(serviceId),
                url,
                errorId)
        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun getUpdateProcessor(playlists: List<PlaylistRemoteEntity>,
                                   result: PlaylistInfo): Flowable<Int> {
        val noItemToUpdate = Flowable.just(/*noItemToUpdate=*/-1)
        if (playlists.isEmpty()) return noItemToUpdate

        val playlistEntity = playlists[0]
        return if (playlistEntity.isIdenticalTo(result)) noItemToUpdate
        else remotePlaylistManager.onUpdate(playlists[0].uid, result).toFlowable()
    }

    override fun setTitle(title: String) {
        Log.d(TAG, "setTitle: title = $title")
        super.setTitle(title)
        headerTitleView.text = title
    }

    private fun onBookmarkClicked() {
        if (!isBookmarkButtonReady.get()) return

        val action =
                when {
                    currentInfo != null && playlistEntity == null -> {
                        remotePlaylistManager.onBookmark(currentInfo!!)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({/* Do nothing */ ignored -> }, { this.onError(it) })
                    }

                    playlistEntity != null -> {
                        remotePlaylistManager.deletePlaylist(playlistEntity!!.uid)
                                .observeOn(AndroidSchedulers.mainThread())
                                .doFinally { playlistEntity = null }
                                .subscribe({/* Do nothing */ ignored -> }, { this.onError(it) })
                    }

                    else -> Disposables.empty()
                }

        compositeDisposable.add(action)
    }

    private fun updateBookmarkButtons() {
        if (playlistBookmarkButton == null || activity == null) return

        val iconAttr = if (playlistEntity == null) R.attr.ic_playlist_add else R.attr.ic_playlist_check

        val titleRes = if (playlistEntity == null) R.string.bookmark_playlist else R.string.unbookmark_playlist

        playlistBookmarkButton!!.setIcon(ThemeHelper.resolveResourceIdFromAttr(activity!!, iconAttr))
        playlistBookmarkButton!!.setTitle(titleRes)
    }

    companion object {

        fun getInstance(serviceId: Int, url: String, name: String): PlaylistFragment {
            val instance = PlaylistFragment()
            instance.setInitialData(serviceId, url, name)
            return instance
        }
    }
}