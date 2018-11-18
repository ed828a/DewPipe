package org.schabi.newpipe.fragments.list.playlist

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.Consumer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.InfoItemDialog
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class PlaylistFragment : BaseListInfoFragment<PlaylistInfo>() {

    private var disposables: CompositeDisposable? = null
    private var bookmarkReactor: Subscription? = null
    private var isBookmarkButtonReady: AtomicBoolean? = null

    private var remotePlaylistManager: RemotePlaylistManager? = null
    private var playlistEntity: PlaylistRemoteEntity? = null
    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private var headerRootLayout: View? = null
    private var headerTitleView: TextView? = null
    private var headerUploaderLayout: View? = null
    private var headerUploaderName: TextView? = null
    private var headerUploaderAvatar: ImageView? = null
    private var headerStreamCount: TextView? = null
    private var playlistCtrl: View? = null

    private var headerPlayAllButton: View? = null
    private var headerPopupButton: View? = null
    private var headerBackgroundButton: View? = null

    private var playlistBookmarkButton: MenuItem? = null

    private val playQueue: PlayQueue
        get() = getPlayQueue(0)

    private val playlistBookmarkSubscriber: Subscriber<List<PlaylistRemoteEntity>>
        get() = object : Subscriber<List<PlaylistRemoteEntity>> {
            override fun onSubscribe(s: Subscription) {
                if (bookmarkReactor != null) bookmarkReactor!!.cancel()
                bookmarkReactor = s
                bookmarkReactor!!.request(1)
            }

            override fun onNext(playlist: List<PlaylistRemoteEntity>) {
                playlistEntity = if (playlist.isEmpty()) null else playlist[0]

                updateBookmarkButtons()
                isBookmarkButtonReady!!.set(true)

                if (bookmarkReactor != null) bookmarkReactor!!.request(1)
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
        disposables = CompositeDisposable()
        isBookmarkButtonReady = AtomicBoolean(false)
        remotePlaylistManager = RemotePlaylistManager(NewPipeDatabase.getInstance(
                requireContext()))
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
        headerTitleView = headerRootLayout!!.findViewById(R.id.playlist_title_view)
        headerUploaderLayout = headerRootLayout!!.findViewById(R.id.uploader_layout)
        headerUploaderName = headerRootLayout!!.findViewById(R.id.uploader_name)
        headerUploaderAvatar = headerRootLayout!!.findViewById(R.id.uploader_avatar_view)
        headerStreamCount = headerRootLayout!!.findViewById(R.id.playlist_stream_count)
        playlistCtrl = headerRootLayout!!.findViewById(R.id.playlist_control)

        headerPlayAllButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_all_button)
        headerPopupButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_popup_button)
        headerBackgroundButton = headerRootLayout!!.findViewById(R.id.playlist_ctrl_play_bg_button)


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

        val actions = DialogInterface.OnClickListener { dialogInterface, i ->
            val index = Math.max(infoListAdapter!!.itemsList.indexOf(item), 0)
            when (i) {
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

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        if (DEBUG)
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu +
                    "], inflater = [" + inflater + "]")
        super.onCreateOptionsMenu(menu, inflater)
        inflater!!.inflate(R.menu.menu_playlist, menu)

        playlistBookmarkButton = menu!!.findItem(R.id.menu_item_bookmark)
        updateBookmarkButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBookmarkButtonReady != null) isBookmarkButtonReady!!.set(false)

        if (disposables != null) disposables!!.clear()
        if (bookmarkReactor != null) bookmarkReactor!!.cancel()

        bookmarkReactor = null
    }

    override fun onDestroy() {
        super.onDestroy()

        if (disposables != null) disposables!!.dispose()

        disposables = null
        remotePlaylistManager = null
        playlistEntity = null
        isBookmarkButtonReady = null
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

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_item_openInBrowser -> openUrlInBrowser(url)
            R.id.menu_item_share -> shareUrl(name, url)
            R.id.menu_item_bookmark -> onBookmarkClicked()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }


    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        animateView(headerRootLayout, false, 200)
        animateView(itemsList, false, 100)

        BaseFragment.imageLoader.cancelDisplayTask(headerUploaderAvatar!!)
        animateView(headerUploaderLayout, false, 200)
    }

    override fun handleResult(result: PlaylistInfo) {
        super.handleResult(result)

        animateView(headerRootLayout, true, 100)
        animateView(headerUploaderLayout, true, 300)
        headerUploaderLayout!!.setOnClickListener(null)
        if (!TextUtils.isEmpty(result.uploaderName)) {
            headerUploaderName!!.text = result.uploaderName
            if (!TextUtils.isEmpty(result.uploaderUrl)) {
                headerUploaderLayout!!.setOnClickListener { v ->
                    try {
                        NavigationHelper.openChannelFragment(fragmentManager,
                                result.serviceId,
                                result.uploaderUrl,
                                result.uploaderName)
                    } catch (e: Exception) {
                        val context = getActivity()
                        context?.let{
                            ErrorActivity.reportUiError( it as AppCompatActivity, e)
                        }
                    }
                }
            }
        }

        playlistCtrl!!.visibility = View.VISIBLE

        BaseFragment.imageLoader.displayImage(result.uploaderAvatarUrl, headerUploaderAvatar!!,
                ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS)
        headerStreamCount!!.text = resources.getQuantityString(R.plurals.videos,
                result.streamCount.toInt(), result.streamCount.toInt())

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.REQUESTED_PLAYLIST, NewPipe.getNameOfService(result.serviceId), result.url, 0)
        }

        remotePlaylistManager!!.getPlaylist(result)
                .flatMap({ lists -> getUpdateProcessor(lists, result) }, { lists, id -> lists })
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistBookmarkSubscriber)

        headerPlayAllButton!!.setOnClickListener { view -> NavigationHelper.playOnMainPlayer(activity, playQueue) }
        headerPopupButton!!.setOnClickListener { view -> NavigationHelper.playOnPopupPlayer(activity, playQueue) }
        headerBackgroundButton!!.setOnClickListener { view -> NavigationHelper.playOnBackgroundPlayer(activity, playQueue) }
    }

    private fun getPlayQueue(index: Int): PlayQueue {
        val infoItems = ArrayList<StreamInfoItem>()
        for (i in infoListAdapter!!.itemsList) {
            if (i is StreamInfoItem) {
                infoItems.add(i)
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
        return if (playlistEntity.isIdenticalTo(result)) noItemToUpdate else remotePlaylistManager!!.onUpdate(playlists[0].uid, result).toFlowable()

    }

    override fun setTitle(title: String) {
        super.setTitle(title)
        headerTitleView!!.text = title
    }

    private fun onBookmarkClicked() {
        if (isBookmarkButtonReady == null || !isBookmarkButtonReady!!.get() ||
                remotePlaylistManager == null)
            return

        val action: Disposable

        if (currentInfo != null && playlistEntity == null) {
            action = remotePlaylistManager!!.onBookmark(currentInfo)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({/* Do nothing */ ignored -> }, { this.onError(it) })
        } else if (playlistEntity != null) {
            action = remotePlaylistManager!!.deletePlaylist(playlistEntity!!.uid)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally { playlistEntity = null }
                    .subscribe({/* Do nothing */ ignored -> }, { this.onError(it) })
        } else {
            action = Disposables.empty()
        }

        disposables!!.add(action)
    }

    private fun updateBookmarkButtons() {
        if (playlistBookmarkButton == null || activity == null) return

        val iconAttr = if (playlistEntity == null)
            R.attr.ic_playlist_add
        else
            R.attr.ic_playlist_check

        val titleRes = if (playlistEntity == null)
            R.string.bookmark_playlist
        else
            R.string.unbookmark_playlist

        playlistBookmarkButton!!.setIcon(ThemeHelper.resolveResourceIdFromAttr(activity, iconAttr))
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