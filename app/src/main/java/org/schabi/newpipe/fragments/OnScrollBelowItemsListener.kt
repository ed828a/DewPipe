package org.schabi.newpipe.fragments

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager

/**
 * Recycler view scroll listener which calls the method [.onScrolledDown]
 * if the view is scrolled below the last item.
 */
abstract class OnScrollBelowItemsListener : RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy > 0) {
            var pastVisibleItems = 0
            val layoutManager = recyclerView.layoutManager
            val visibleItemCount: Int = layoutManager!!.childCount
            val totalItemCount: Int = layoutManager.itemCount

            // Already covers the GridLayoutManager case
            if (layoutManager is LinearLayoutManager) {
                pastVisibleItems = layoutManager.findFirstVisibleItemPosition()
            } else if (layoutManager is StaggeredGridLayoutManager) {
                val positions = layoutManager.findFirstVisibleItemPositions(null)
                if (positions != null && positions.isNotEmpty()) pastVisibleItems = positions[0]
            }

            if (visibleItemCount + pastVisibleItems >= totalItemCount) {
                onScrolledDown(recyclerView) // load more items in this function
            }
        }
    }

    /**
     * Called when the recycler view is scrolled below the last item.
     *
     * @param recyclerView the recycler view
     */
    abstract fun onScrolledDown(recyclerView: RecyclerView)
}
