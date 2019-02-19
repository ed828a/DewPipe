package org.schabi.newpipe.info_list.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder

abstract class InfoItemHolder(
        protected val itemBuilder: InfoItemBuilder,
        layoutId: Int,
        parent: ViewGroup) : androidx.recyclerview.widget.RecyclerView.ViewHolder(LayoutInflater.from(itemBuilder.context).inflate(layoutId, parent, false)) {

    abstract fun updateFromItem(infoItem: InfoItem)


}
