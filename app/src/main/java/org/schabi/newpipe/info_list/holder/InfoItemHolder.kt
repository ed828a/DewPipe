package org.schabi.newpipe.info_list.holder

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder

abstract class InfoItemHolder(
        protected val itemBuilder: InfoItemBuilder,
        layoutId: Int,
        parent: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(itemBuilder.context).inflate(layoutId, parent, false)) {

    abstract fun updateFromItem(infoItem: InfoItem)
}
