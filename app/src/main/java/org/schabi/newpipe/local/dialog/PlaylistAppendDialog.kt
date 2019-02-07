package org.schabi.newpipe.local.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistMetadataEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.OnClickGesture
import java.util.*

class PlaylistAppendDialog : PlaylistDialog() {

    private var playlistRecyclerView: RecyclerView? = null
    private var playlistAdapter: LocalItemListAdapter? = null

    private var playlistReactor: Disposable? = null

    private val compositeDisposable = CompositeDisposable()
    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_playlists, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(context!!))
        val playlistManager = LocalPlaylistManager(AppDatabase.getDatabase(context!!))
        playlistAdapter = LocalItemListAdapter(activity!!)
        playlistAdapter?.setSelectedListener(object : OnClickGesture<LocalItem>() {
            override fun selected(selectedItem: LocalItem) {
                if (selectedItem !is PlaylistMetadataEntry || streams == null)
                    return
                onPlaylistSelected(playlistManager, selectedItem, streams!!)
            }
        })

        playlistRecyclerView = view.findViewById(R.id.playlist_list)
        playlistRecyclerView?.layoutManager = LinearLayoutManager(context) as RecyclerView.LayoutManager?
        playlistRecyclerView?.adapter = playlistAdapter

        val newPlaylistButton = view.findViewById<View>(R.id.newPlaylist)
        newPlaylistButton.setOnClickListener { ignored -> openCreatePlaylistDialog() }

        playlistReactor = playlistManager.playlists
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {list -> this.onPlaylistsReceived(list) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    override fun onDestroyView() {
        super.onDestroyView()
        playlistReactor?.dispose()
        playlistAdapter?.unsetSelectedListener()

        playlistReactor = null
        playlistRecyclerView = null
        playlistAdapter = null
        if (!compositeDisposable.isDisposed) compositeDisposable.dispose()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////////////////////////////

    private fun openCreatePlaylistDialog() {
        if (streams == null || fragmentManager == null) return

        PlaylistCreationDialog.newInstance(streams!!).show(fragmentManager!!, TAG)
        dialog.dismiss()
    }

    private fun onPlaylistsReceived(playlists: List<PlaylistMetadataEntry>) {
        if (playlists.isEmpty()) {
            openCreatePlaylistDialog()
            return
        }

        if (playlistAdapter != null && playlistRecyclerView != null) {
            playlistAdapter!!.clearStreamItemList()
            playlistAdapter!!.addItems(playlists)
            playlistRecyclerView!!.visibility = View.VISIBLE
        }
    }

    private fun onPlaylistSelected(manager: LocalPlaylistManager,
                                   playlist: PlaylistMetadataEntry,
                                   streams: List<StreamEntity>) {

        @SuppressLint("ShowToast")
        val successToast = Toast.makeText(context,
                R.string.playlist_add_stream_success, Toast.LENGTH_SHORT)

        val d = manager.appendToPlaylist(playlist.uid, streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { ignored -> successToast.show() }

        compositeDisposable.add(d)
        dialog.dismiss()
    }

    companion object {
        private val TAG = PlaylistAppendDialog::class.java.canonicalName

        fun fromStreamInfo(info: StreamInfo): PlaylistAppendDialog {
            val dialog = PlaylistAppendDialog()
            dialog.setInfo(listOf(StreamEntity(info)))
            return dialog
        }

        fun fromStreamInfoItems(items: List<StreamInfoItem>): PlaylistAppendDialog {
            val dialog = PlaylistAppendDialog()
            val entities = ArrayList<StreamEntity>(items.size)
            for (item in items) {
                entities.add(StreamEntity(item))
            }
            dialog.setInfo(entities)
            return dialog
        }

        fun fromPlayQueueItems(items: List<PlayQueueItem>): PlaylistAppendDialog {
            val dialog = PlaylistAppendDialog()
            val entities = ArrayList<StreamEntity>(items.size)
            for (item in items) {
                entities.add(StreamEntity(item))
            }
            dialog.setInfo(entities)
            return dialog
        }
    }
}
