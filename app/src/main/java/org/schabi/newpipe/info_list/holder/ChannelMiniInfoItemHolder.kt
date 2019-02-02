package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

import de.hdodenhof.circleimageview.CircleImageView

open class ChannelMiniInfoItemHolder(infoItemBuilder: InfoItemBuilder,
                                     layoutId: Int,
                                     parent: ViewGroup) : InfoItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: CircleImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemTitleView: TextView = itemView.findViewById(R.id.itemTitleView)
    private val itemAdditionalDetailView: TextView = itemView.findViewById(R.id.itemAdditionalDetails)

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_channel_mini_item, parent) {}

    // bindToView
    override fun updateFromItem(infoItem: InfoItem) {
        if (infoItem !is ChannelInfoItem) return

        itemTitleView.text = infoItem.name
        itemAdditionalDetailView.text = getDetailLine(infoItem)

        itemBuilder.imageLoader
                .displayImage(infoItem.thumbnailUrl,
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        itemView.setOnClickListener { view ->
            if (itemBuilder.onChannelSelectedListener != null) {
                itemBuilder.onChannelSelectedListener!!.selected(infoItem)
            }
        }

        itemView.setOnLongClickListener { view ->
            if (itemBuilder.onChannelSelectedListener != null) {
                itemBuilder.onChannelSelectedListener!!.held(infoItem)
            }
            true
        }
    }

    protected open fun getDetailLine(item: ChannelInfoItem): String {
        var details = ""
        if (item.subscriberCount >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.context,
                    item.subscriberCount)
        }
        return details
    }
}
