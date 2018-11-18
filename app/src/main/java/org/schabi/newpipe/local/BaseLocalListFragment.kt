package org.schabi.newpipe.local

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.View

import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.list.ListViewContract

import org.schabi.newpipe.util.AnimationUtils.animateView

/**
 * This fragment is design to be used with persistent data such as
 * [org.schabi.newpipe.database.LocalItem], and does not cache the data contained
 * in the list adapter to avoid extra writes when the it exits or re-enters its lifecycle.
 *
 * This fragment destroys its adapter and views when [Fragment.onDestroyView] is
 * called and is memory efficient when in backstack.
 */
abstract class BaseLocalListFragment<I, N> : BaseStateFragment<I>(), ListViewContract<I, N>, SharedPreferences.OnSharedPreferenceChangeListener {

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    protected var headerRootView: View? = null
    protected var footerRootView: View? = null

    protected var itemListAdapter: LocalItemListAdapter? = null
    protected var itemsList: RecyclerView? = null
    private var updateFlags = 0

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle - View
    ///////////////////////////////////////////////////////////////////////////

    protected open fun getListHeader(): View? = null

    protected fun getListFooter(): View = activity.layoutInflater.inflate(R.layout.pignate_footer, itemsList, false)

    protected fun getGridLayoutManager(): RecyclerView.LayoutManager {
        val resources = activity.resources
        var width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
        width += (24 * resources.displayMetrics.density).toInt()
        val spanCount = Math.floor(resources.displayMetrics.widthPixels / width.toDouble()).toInt()
        val layoutManager = GridLayoutManager(activity, spanCount)
        layoutManager.spanSizeLookup = itemListAdapter!!.getSpanSizeLookup(spanCount)
        return layoutManager
    }

    protected fun getListLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(activity)

    protected fun isGridLayout(): Boolean {
            val list_mode = PreferenceManager.getDefaultSharedPreferences(activity).getString(getString(R.string.list_view_mode_key), getString(R.string.list_view_mode_value))
            return if ("auto" == list_mode) {
                val configuration = resources.configuration
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
            } else {
                "grid" == list_mode
            }
        }

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (updateFlags != 0) {
            if (updateFlags and LIST_MODE_UPDATE_FLAG != 0) {
                val useGrid = isGridLayout()
                itemsList!!.layoutManager = if (useGrid) getGridLayoutManager() else getListLayoutManager()
                itemListAdapter!!.setGridItemVariants(useGrid)
                itemListAdapter!!.notifyDataSetChanged()
            }
            updateFlags = 0
        }
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        itemListAdapter = LocalItemListAdapter(activity)

        val useGrid = isGridLayout()
        itemsList = rootView.findViewById(R.id.items_list)
        itemsList!!.layoutManager = if (useGrid) getGridLayoutManager() else getListLayoutManager()

        itemListAdapter!!.setGridItemVariants(useGrid)
        headerRootView = getListHeader()
        itemListAdapter!!.setHeader(headerRootView)
        footerRootView = getListFooter()
        itemListAdapter!!.setFooter(footerRootView)

        itemsList!!.adapter = itemListAdapter
    }

    override fun initListeners() {
        super.initListeners()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle - Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        if (DEBUG)
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu +
                    "], inflater = [" + inflater + "]")

        val supportActionBar = activity.supportActionBar ?: return

        supportActionBar.setDisplayShowTitleEnabled(true)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    override fun onDestroyView() {
        super.onDestroyView()
        itemsList = null
        itemListAdapter = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        resetFragment()
    }

    override fun showLoading() {
        super.showLoading()
        if (itemsList != null) animateView(itemsList, false, 200)
        if (headerRootView != null) animateView(headerRootView, false, 200)
    }

    override fun hideLoading() {
        super.hideLoading()
        if (itemsList != null) animateView(itemsList, true, 200)
        if (headerRootView != null) animateView(headerRootView, true, 200)
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        super.showError(message, showRetryButton)
        showListFooter(false)

        if (itemsList != null) animateView(itemsList, false, 200)
        if (headerRootView != null) animateView(headerRootView, false, 200)
    }

    override fun showEmptyState() {
        super.showEmptyState()
        showListFooter(false)
    }

    override fun showListFooter(show: Boolean) {
        if (itemsList == null) return
        itemsList!!.post { if (itemListAdapter != null) itemListAdapter!!.showFooter(show) }
    }

    override fun handleNextItems(result: N) {
        isLoading.set(false)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error handling
    ///////////////////////////////////////////////////////////////////////////

    protected open fun resetFragment() {
        if (itemListAdapter != null) itemListAdapter!!.clearStreamItemList()
    }

    override fun onError(exception: Throwable): Boolean {
        resetFragment()
        return super.onError(exception)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == getString(R.string.list_view_mode_key)) {
            updateFlags = updateFlags or LIST_MODE_UPDATE_FLAG
        }
    }

    companion object {

        private val LIST_MODE_UPDATE_FLAG = 0x32
    }
}
