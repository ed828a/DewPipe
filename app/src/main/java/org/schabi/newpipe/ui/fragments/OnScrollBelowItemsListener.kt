package org.schabi.newpipe.ui.fragments

/**
 * Recycler view scroll listener which calls the method [.onScrolledDown]
 * if the view is scrolled below the last item.
 */
abstract class OnScrollBelowItemsListener : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy > 0) {
            var pastVisibleItems = 0
            val layoutManager = recyclerView.layoutManager
            val visibleItemCount: Int = layoutManager!!.childCount
            val totalItemCount: Int = layoutManager.itemCount

            // Already covers the GridLayoutManager case
            if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
                pastVisibleItems = layoutManager.findFirstVisibleItemPosition()
            } else if (layoutManager is androidx.recyclerview.widget.StaggeredGridLayoutManager) {
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
    abstract fun onScrolledDown(recyclerView: androidx.recyclerview.widget.RecyclerView)
}
