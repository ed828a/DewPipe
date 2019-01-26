package org.schabi.newpipe.util

import android.content.Context
import android.graphics.PointF
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.LinearSmoothScroller
import android.support.v7.widget.RecyclerView

class LayoutManagerSmoothScroller (context: Context,
                                   orientation: Int = LinearLayoutManager.VERTICAL,
                                   reverseLayout: Boolean = false
): LinearLayoutManager (context, orientation, reverseLayout){

    override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State?, position: Int) {
        val smoothScroller = TopSnappedSmoothScroller(recyclerView.context)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    private inner class TopSnappedSmoothScroller(context: Context) : LinearSmoothScroller(context) {

        override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
            return this@LayoutManagerSmoothScroller
                    .computeScrollVectorForPosition(targetPosition)
        }

        override fun getVerticalSnapPreference(): Int {
            return LinearSmoothScroller.SNAP_TO_START
        }
    }
}