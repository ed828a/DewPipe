package org.schabi.newpipe.util

abstract class OnClickGesture<T> {

    abstract fun selected(selectedItem: T)

    open fun held(selectedItem: T) {
        // Optional gesture
    }

    open fun drag(selectedItem: T, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
        // Optional gesture
    }
}
