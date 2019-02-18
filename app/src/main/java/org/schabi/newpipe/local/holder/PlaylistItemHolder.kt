package org.schabi.newpipe.local.holder

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.local.LocalItemBuilder
import java.text.DateFormat

/**
 * Todo: this class should be open class, not abstract class, and should be named PlaylistMiniItemHolder
 * But the author don't want this class to be instantiated, so name as abstract class.
 * In that case, the second construct is redundant, because this constructor is for instantiation purpose
 */
//

abstract class PlaylistItemHolder(infoItemBuilder: LocalItemBuilder,
                                  layoutId: Int,
                                  parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemTitleView: TextView = itemView.findViewById(R.id.itemTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemStreamCountView: TextView = itemView.findViewById(R.id.itemStreamCountView)

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent)

    override fun updateFromItem(item: LocalItem, dateFormat: DateFormat) {
        itemView.setOnClickListener { view ->
            itemBuilder.onItemSelectedListener?.selected(item)
        }

        itemView.isLongClickable = true
        itemView.setOnLongClickListener { view ->
            itemBuilder.onItemSelectedListener?.held(item)
            true
        }
    }
}
