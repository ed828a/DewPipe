package org.schabi.newpipe.local.holder

import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

import java.text.DateFormat

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

open class LocalStatisticStreamItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView
    val itemVideoTitleView: TextView
    val itemUploaderView: TextView
    val itemDurationView: TextView
    val itemAdditionalDetails: TextView?

    constructor(itemBuilder: LocalItemBuilder, parent: ViewGroup) : this(itemBuilder, R.layout.list_stream_item, parent) {}

    init {

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
        itemDurationView = itemView.findViewById(R.id.itemDurationView)
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails)
    }

    private fun getStreamInfoDetailLine(entry: StreamStatisticsEntry,
                                        dateFormat: DateFormat): String {
        val watchCount = Localization.shortViewCount(itemBuilder.context,
                entry.watchCount)
        val uploadDate = dateFormat.format(entry.latestAccessDate)
        val serviceName = NewPipe.getNameOfService(entry.serviceId)
        return Localization.concatenateStrings(watchCount, uploadDate, serviceName)
    }

    override fun updateFromItem(localItem: LocalItem, dateFormat: DateFormat) {
        if (localItem !is StreamStatisticsEntry) return

        itemVideoTitleView.text = localItem.title
        itemUploaderView.text = localItem.uploader

        if (localItem.duration > 0) {
            itemDurationView.text = Localization.getDurationString(localItem.duration)
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context!!,
                    R.color.duration_background_color))
            itemDurationView.visibility = View.VISIBLE
        } else {
            itemDurationView.visibility = View.GONE
        }

        if (itemAdditionalDetails != null) {
            itemAdditionalDetails.text = getStreamInfoDetailLine(localItem, dateFormat)
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.displayImage(localItem.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

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
