package org.schabi.newpipe.util

import android.content.Context
import android.graphics.PointF
import androidx.recyclerview.widget.LinearLayoutManager

class LayoutManagerSmoothScroller (context: Context,
                                   orientation: Int = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
                                   reverseLayout: Boolean = false
): androidx.recyclerview.widget.LinearLayoutManager(context, orientation, reverseLayout){

    override fun smoothScrollToPosition(recyclerView: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State?, position: Int) {
        val smoothScroller = TopSnappedSmoothScroller(recyclerView.context)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    private inner class TopSnappedSmoothScroller(context: Context) : androidx.recyclerview.widget.LinearSmoothScroller(context) {

        override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
            return this@LayoutManagerSmoothScroller
                    .computeScrollVectorForPosition(targetPosition)
        }

        override fun getVerticalSnapPreference(): Int = androidx.recyclerview.widget.LinearSmoothScroller.SNAP_TO_START

    }
}