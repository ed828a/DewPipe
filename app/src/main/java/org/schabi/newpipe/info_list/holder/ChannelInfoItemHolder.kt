package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.Localization

/*
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemHolder .java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

class ChannelInfoItemHolder(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : ChannelMiniInfoItemHolder(infoItemBuilder, R.layout.list_channel_item, parent) {
    val itemChannelDescriptionView: TextView

    init {
        itemChannelDescriptionView = itemView.findViewById(R.id.itemChannelDescriptionView)
    }

    override fun updateFromItem(infoItem: InfoItem) {
        super.updateFromItem(infoItem)

        if (infoItem !is ChannelInfoItem) return

        itemChannelDescriptionView.text = infoItem.description
    }

    override fun getDetailLine(item: ChannelInfoItem): String {
        var details = super.getDetailLine(item)

        if (item.streamCount >= 0) {
            val formattedVideoAmount = Localization.localizeStreamCount(itemBuilder.context,
                    item.streamCount)

            if (!details.isEmpty()) {
                details += " • $formattedVideoAmount"
            } else {
                details = formattedVideoAmount
            }
        }
        return details
    }
}
