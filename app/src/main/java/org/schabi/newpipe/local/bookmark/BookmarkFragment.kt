package org.schabi.newpipe.local.bookmark

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import icepick.State
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.BaseLocalListFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import java.util.*

class BookmarkFragment : BaseLocalListFragment<List<PlaylistLocalItem>, Void>() {

    @State
    var itemsListState: Parcelable? = null

    private var databaseSubscription: Subscription? = null
    private var disposables: CompositeDisposable? = CompositeDisposable()
    private var localPlaylistManager: LocalPlaylistManager? = null
    private var remotePlaylistManager: RemotePlaylistManager? = null

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private val playlistsSubscriber: Subscriber<List<PlaylistLocalItem>>
        get() = object : Subscriber<List<PlaylistLocalItem>> {
            override fun onSubscribe(s: Subscription) {
                showLoading()
                if (databaseSubscription != null) databaseSubscription!!.cancel()
                databaseSubscription = s
                databaseSubscription!!.request(1)
            }

            override fun onNext(subscriptions: List<PlaylistLocalItem>) {
                handleResult(subscriptions)
                if (databaseSubscription != null) databaseSubscription!!.request(1)
            }

            override fun onError(exception: Throwable) {
                this@BookmarkFragment.onError(exception)
            }

            override fun onComplete() {}
        }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (activity == null) return
        val database = NewPipeDatabase.getInstance(activity)
        localPlaylistManager = LocalPlaylistManager(database)
        remotePlaylistManager = RemotePlaylistManager(database)
        disposables = CompositeDisposable()
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        if (!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks))
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false)
    }


    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (activity != null && isVisibleToUser) {
            setTitle(activity.getString(R.string.tab_bookmarks))
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        Log.d("BookmarkFragment", "BookmarkFragment::initViews() called.")
    }

    override fun initListeners() {
        super.initListeners()

        itemListAdapter.setSelectedListener(object : OnClickGesture<LocalItem>() {
            override fun selected(selectedItem: LocalItem) {
                val fragmentManager = fm

                if (selectedItem is PlaylistMetadataEntry) {
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, selectedItem.uid,
                            selectedItem.name)

                } else if (selectedItem is PlaylistRemoteEntity) {
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            selectedItem.serviceId,
                            selectedItem.url,
                            selectedItem.name)
                }
            }

            override fun held(selectedItem: LocalItem) {
                if (selectedItem is PlaylistMetadataEntry) {
                    showLocalDeleteDialog(selectedItem)

                } else if (selectedItem is PlaylistRemoteEntity) {
                    showRemoteDeleteDialog(selectedItem)
                }
            }
        })
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)

        Flowable.combineLatest<List<PlaylistMetadataEntry>, List<PlaylistRemoteEntity>, List<PlaylistLocalItem>>(
                localPlaylistManager!!.playlists,
                remotePlaylistManager!!.playlists,
                BiFunction<List<PlaylistMetadataEntry>, List<PlaylistRemoteEntity>, List<PlaylistLocalItem>> { localPlaylists, remotePlaylists -> merge(localPlaylists, remotePlaylists) }
        ).onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistsSubscriber)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    override fun onPause() {
        super.onPause()
        itemsListState = itemsList.layoutManager!!.onSaveInstanceState()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (disposables != null) disposables!!.clear()
        if (databaseSubscription != null) databaseSubscription!!.cancel()

        databaseSubscription = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposables != null) disposables!!.dispose()

        disposables = null
        localPlaylistManager = null
        remotePlaylistManager = null
        itemsListState = null
    }

    override fun handleResult(result: List<PlaylistLocalItem>) {
        super.handleResult(result)

        itemListAdapter.clearStreamItemList()

        if (result.isEmpty()) {
            showEmptyState()
            return
        }

        itemListAdapter.addItems(result)
        if (itemsListState != null) {
            itemsList.layoutManager!!.onRestoreInstanceState(itemsListState)
            itemsListState = null
        }
        hideLoading()
    }
    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Bookmark", R.string.general_error)
        return true
    }

    override fun resetFragment() {
        super.resetFragment()
        if (disposables != null) disposables!!.clear()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun showLocalDeleteDialog(item: PlaylistMetadataEntry) {
        showDeleteDialog(item.name, localPlaylistManager!!.deletePlaylist(item.uid))
    }

    private fun showRemoteDeleteDialog(item: PlaylistRemoteEntity) {
        showDeleteDialog(item.name, remotePlaylistManager!!.deletePlaylist(item.uid))
    }

    private fun showDeleteDialog(name: String, deleteReactor: Single<Int>) {
        if (activity == null || disposables == null) return

        AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete
                ) { dialog, i ->
                    disposables!!.add(deleteReactor
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({/*Do nothing on success*/ ignored -> }, { this.onError(it) }))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun merge(localPlaylists: List<PlaylistMetadataEntry>,
                      remotePlaylists: List<PlaylistRemoteEntity>): List<PlaylistLocalItem> {
        val items = ArrayList<PlaylistLocalItem>(
                localPlaylists.size + remotePlaylists.size)
        items.addAll(localPlaylists)
        items.addAll(remotePlaylists)

        items.sortWith(Comparator { left, right -> left.orderingName.compareTo(right.orderingName, ignoreCase = true) })

        return items
    }
}

