package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.Localization

class ChannelInfoItemHolder(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : ChannelMiniInfoItemHolder(infoItemBuilder, R.layout.list_channel_item, parent) {
    private val itemChannelDescriptionView: TextView = itemView.findViewById(R.id.itemChannelDescriptionView)

    override fun updateFromItem(infoItem: InfoItem) {
        super.updateFromItem(infoItem)

        if (infoItem !is ChannelInfoItem) return

        itemChannelDescriptionView.text = infoItem.description
    }

    override fun getDetailLine(item: ChannelInfoItem): String {
        var details = super.getDetailLine(item)

        if (item.streamCount >= 0) {
            val formattedVideoAmount = Localization.localizeStreamCount(itemBuilder.context, item.streamCount)

            if (!details.isEmpty()) {
                details += " • $formattedVideoAmount"
            } else {
                details = formattedVideoAmount
            }
        }
        return details
    }
}
