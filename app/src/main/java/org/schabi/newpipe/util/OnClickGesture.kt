package org.schabi.newpipe.util

import android.support.v7.widget.RecyclerView

abstract class OnClickGesture<T> {

    abstract fun selected(selectedItem: T)

    open fun held(selectedItem: T) {
        // Optional gesture
    }

    open fun drag(selectedItem: T, viewHolder: RecyclerView.ViewHolder) {
        // Optional gesture
    }
}
