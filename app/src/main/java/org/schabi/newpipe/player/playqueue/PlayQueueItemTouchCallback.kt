package org.schabi.newpipe.player.playqueue

import androidx.recyclerview.widget.ItemTouchHelper

abstract class PlayQueueItemTouchCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    abstract fun onMove(sourceIndex: Int, targetIndex: Int)

    override fun interpolateOutOfBoundsScroll(recyclerView: androidx.recyclerview.widget.RecyclerView,
                                              viewSize: Int,
                                              viewSizeOutOfBounds: Int,
                                              totalSize: Int,
                                              msSinceStartScroll: Long): Int {
        val standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll)

        val clampedAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY, Math.min(Math.abs(standardSpeed), MAXIMUM_INITIAL_DRAG_VELOCITY))

        return clampedAbsVelocity * Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
    }

    override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView,
                        source: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                        target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
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

    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, swipeDir: Int) {}

    companion object {
        private const val MINIMUM_INITIAL_DRAG_VELOCITY = 10
        private const val MAXIMUM_INITIAL_DRAG_VELOCITY = 25
    }
}
