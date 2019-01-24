package org.schabi.newpipe.fragments.list

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener
import org.schabi.newpipe.info_list.InfoItemDialog
import org.schabi.newpipe.info_list.InfoListAdapter
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.StateSaver
import java.util.*

abstract class BaseListFragment<I, N> : BaseStateFragment<I>(), ListViewContract<I, N>, StateSaver.WriteRead, SharedPreferences.OnSharedPreferenceChangeListener {

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    protected var infoListAdapter: InfoListAdapter? = null
    protected var itemsList: RecyclerView? = null
    private var updateFlags = 0

    ///////////////////////////////////////////////////////////////////////////
    // State Saving
    ///////////////////////////////////////////////////////////////////////////

    protected var savedState: StateSaver.SavedState? = null

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    protected open fun getListHeader(): View? = null

    protected open fun getListFooter(): View = activity!!.layoutInflater.inflate(R.layout.pignate_footer, itemsList, false)

    protected fun getListLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(activity)

    protected fun getGridLayoutManager(): RecyclerView.LayoutManager {
        val resources = activity!!.resources
        var width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
        width += (24 * resources.displayMetrics.density).toInt()
        val spanCount = Math.floor(resources.displayMetrics.widthPixels / width.toDouble()).toInt()
        val layoutManager = GridLayoutManager(activity, spanCount)
        layoutManager.spanSizeLookup = infoListAdapter!!.getSpanSizeLookup(spanCount)
        return layoutManager
    }


    protected val isGridLayout: Boolean
        get() {
            val listMode = PreferenceManager.getDefaultSharedPreferences(activity).getString(getString(R.string.list_view_mode_key), getString(R.string.list_view_mode_value))
            if ("auto" == listMode) {
                val configuration = resources.configuration
                return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
            } else {
                return "grid" == listMode
            }
        }

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        infoListAdapter = InfoListAdapter(activity!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        StateSaver.onDestroy(savedState)
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        if (updateFlags != FLAG_NO_UPDATE) {
            if (updateFlags and LIST_MODE_UPDATE_FLAG != FLAG_NO_UPDATE) {
                val useGrid = isGridLayout
                itemsList?.layoutManager = if (useGrid) getGridLayoutManager() else getListLayoutManager()
                infoListAdapter?.setGridItemVariants(useGrid)
                infoListAdapter?.notifyDataSetChanged()
            }
            updateFlags = FLAG_NO_UPDATE
        }
    }

    override fun generateSuffix(): String {
        // Naive solution, but it's good for now (the items don't change)
        return ".${infoListAdapter!!.itemsList.size}.list"
    }

    override fun writeTo(objectsToSave: Queue<Any>) {
        objectsToSave.add(infoListAdapter!!.itemsList)
    }

    @Throws(Exception::class)
    override fun readFrom(savedObjects: Queue<Any>) {
        infoListAdapter!!.itemsList.clear()
        infoListAdapter!!.itemsList.addAll(savedObjects.poll() as List<InfoItem>)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        savedState = StateSaver.tryToSave(activity!!.isChangingConfigurations, savedState, bundle, this)
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        savedState = StateSaver.tryToRestore(bundle, this)
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        val useGrid = isGridLayout
        itemsList = rootView.findViewById(R.id.items_list)
        itemsList!!.layoutManager = if (useGrid) getGridLayoutManager() else getListLayoutManager()

        infoListAdapter!!.setGridItemVariants(useGrid)
        infoListAdapter!!.setFooter(getListFooter())
        infoListAdapter!!.setHeader(getListHeader())

        itemsList!!.adapter = infoListAdapter
    }

    protected open fun onItemSelected(selectedItem: InfoItem) {
        if (DEBUG) Log.d(TAG, "onItemSelected() called with: selectedItem = [$selectedItem]")
    }

    override fun initListeners() {
        super.initListeners()
        infoListAdapter!!.setOnStreamSelectedListener(object : OnClickGesture<StreamInfoItem>() {
            override fun selected(selectedItem: StreamInfoItem) {
                onStreamSelected(selectedItem)
            }

            override fun held(selectedItem: StreamInfoItem) {
                showStreamDialog(selectedItem)
            }
        })

        infoListAdapter!!.setOnChannelSelectedListener(object : OnClickGesture<ChannelInfoItem>() {
            override fun selected(selectedItem: ChannelInfoItem) {
                try {
                    onItemSelected(selectedItem)
                    NavigationHelper.openChannelFragment(getFM(),
                            selectedItem.serviceId,
                            selectedItem.url,
                            selectedItem.name)
                } catch (e: Exception) {
                    val context = getActivity()
                    context?.let {
                        ErrorActivity.reportUiError(it as AppCompatActivity, e)
                    }
                }

            }
        })

        infoListAdapter!!.setOnPlaylistSelectedListener(object : OnClickGesture<PlaylistInfoItem>() {
            override fun selected(selectedItem: PlaylistInfoItem) {
                try {
                    onItemSelected(selectedItem)
                    NavigationHelper.openPlaylistFragment(getFM(),
                            selectedItem.serviceId,
                            selectedItem.url,
                            selectedItem.name)
                } catch (e: Exception) {
                    val context = getActivity()
                    context?.let{
                        ErrorActivity.reportUiError(it as AppCompatActivity, e)
                    }

                }

            }
        })

        itemsList!!.clearOnScrollListeners()
        itemsList!!.addOnScrollListener(object : OnScrollBelowItemsListener() {
            override fun onScrolledDown(recyclerView: RecyclerView) {
                onScrollToBottom()
            }
        })
    }

    private fun onStreamSelected(selectedItem: StreamInfoItem) {
        onItemSelected(selectedItem)

        NavigationHelper.openVideoDetailFragment(getFM(), selectedItem.serviceId, selectedItem.url, selectedItem.name)
    }

    protected fun onScrollToBottom() {
        if (hasMoreItems() && !isLoading.get()) {
            loadMoreItems()
        }
    }

    protected open fun showStreamDialog(item: StreamInfoItem) {
        val context = context
        val activity = getActivity()
        if (context == null || context.resources == null || getActivity() == null) return

        val commands = arrayOf(context.resources.getString(R.string.enqueue_on_background), context.resources.getString(R.string.enqueue_on_popup), context.resources.getString(R.string.append_playlist), context.resources.getString(R.string.share))

        val actions = DialogInterface.OnClickListener { dialog, which ->
            when (which) {
                0 -> NavigationHelper.enqueueOnBackgroundPlayer(context, SinglePlayQueue(item))
                1 -> NavigationHelper.enqueueOnPopupPlayer(activity, SinglePlayQueue(item))
                2 -> if (fragmentManager != null) {
                    PlaylistAppendDialog.fromStreamInfoItems(listOf(item))
                            .show(fragmentManager!!, TAG)
                }
                3 -> shareUrl(item.name, item.url)
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
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu], inflater = [$inflater]")
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar = activity!!.supportActionBar
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true)
            if (useAsFrontPage) {
                supportActionBar.setDisplayHomeAsUpEnabled(false)
            } else {
                supportActionBar.setDisplayHomeAsUpEnabled(true)
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Load and handle
    ///////////////////////////////////////////////////////////////////////////

    protected abstract fun loadMoreItems()

    protected abstract fun hasMoreItems(): Boolean

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        // animateView(itemsList, false, 400);
    }

    override fun hideLoading() {
        super.hideLoading()
        animateView(itemsList!!, true, 300)
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        super.showError(message, showRetryButton)
        showListFooter(false)
        animateView(itemsList!!, false, 200)
    }

    override fun showEmptyState() {
        super.showEmptyState()
        showListFooter(false)
    }

    override fun showListFooter(show: Boolean) {
        itemsList!!.post {
            if (infoListAdapter != null && itemsList != null) {
                infoListAdapter!!.showFooter(show)
            }
        }
    }

    override fun handleNextItems(result: N) {
        isLoading.set(false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == getString(R.string.list_view_mode_key)) {
            updateFlags = updateFlags or LIST_MODE_UPDATE_FLAG
        }
    }

    companion object {
        private const val FLAG_NO_UPDATE = 0
        private const val LIST_MODE_UPDATE_FLAG = 0x32
    }
}
