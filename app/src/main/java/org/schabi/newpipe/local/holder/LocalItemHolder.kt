package org.schabi.newpipe.local.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.local.LocalItemBuilder
import java.text.DateFormat


abstract class LocalItemHolder(
        protected val itemBuilder: LocalItemBuilder,
        layoutId: Int,
        parent: ViewGroup)
    : androidx.recyclerview.widget.RecyclerView.ViewHolder(LayoutInflater.from(itemBuilder.context).inflate(layoutId, parent, false)
) {
    // bind-To-View
    abstract fun updateFromItem(item: LocalItem, dateFormat: DateFormat)
}
