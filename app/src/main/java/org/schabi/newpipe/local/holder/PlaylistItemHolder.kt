package org.schabi.newpipe.local.holder

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.local.LocalItemBuilder

import java.text.DateFormat

abstract class PlaylistItemHolder(infoItemBuilder: LocalItemBuilder,
                                  layoutId: Int, parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView
    val itemStreamCountView: TextView
    val itemTitleView: TextView
    val itemUploaderView: TextView

    init {

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemTitleView = itemView.findViewById(R.id.itemTitleView)
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
    }

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent) {}

    override fun updateFromItem(localItem: LocalItem, dateFormat: DateFormat) {
        itemView.setOnClickListener { view ->
            if (itemBuilder.onItemSelectedListener != null) {
                itemBuilder.onItemSelectedListener!!.selected(localItem)
            }
        }

        itemView.isLongClickable = true
        itemView.setOnLongClickListener { view ->
            if (itemBuilder.onItemSelectedListener != null) {
                itemBuilder.onItemSelectedListener!!.held(localItem)
            }
            true
        }
    }
}
