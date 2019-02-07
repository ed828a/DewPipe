package org.schabi.newpipe.player.playqueue

import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper

abstract class PlayQueueItemTouchCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    abstract fun onMove(sourceIndex: Int, targetIndex: Int)

    override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView,
                                              viewSize: Int,
                                              viewSizeOutOfBounds: Int,
                                              totalSize: Int,
                                              msSinceStartScroll: Long): Int {
        val standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll)

        val clampedAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY, Math.min(Math.abs(standardSpeed), MAXIMUM_INITIAL_DRAG_VELOCITY))

        return clampedAbsVelocity * Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
    }

    override fun onMove(recyclerView: RecyclerView,
                        source: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        if (source.itemViewType != target.itemViewType) {
            return false
        }

        val sourceIndex = source.layoutPosition
        val targetIndex = target.layoutPosition
        onMove(sourceIndex, targetIndex)
        return true
    }

    override fun isLongPressDragEnabled(): Boolean {
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {}

    companion object {
        private const val MINIMUM_INITIAL_DRAG_VELOCITY = 10
        private const val MAXIMUM_INITIAL_DRAG_VELOCITY = 25
    }
}
