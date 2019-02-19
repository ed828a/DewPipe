package org.schabi.newpipe.local.holder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.stream.model.StreamStatisticsEntry
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization
import java.text.DateFormat


open class LocalStatisticStreamItemHolder(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemVideoTitleView: TextView = itemView.findViewById(R.id.itemVideoTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemDurationView: TextView = itemView.findViewById(R.id.itemDurationView)
    val itemAdditionalDetails: TextView? = itemView.findViewById(R.id.itemAdditionalDetails)

    constructor(itemBuilder: LocalItemBuilder, parent: ViewGroup) : this(itemBuilder, R.layout.list_stream_item, parent) {}

    private fun getStreamInfoDetailLine(entry: StreamStatisticsEntry, dateFormat: DateFormat): String {
        val watchCount = Localization.shortViewCount(itemBuilder.context!!, entry.watchCount)
        val uploadDate = dateFormat.format(entry.latestAccessDate)
        val serviceName = NewPipe.getNameOfService(entry.serviceId)

        return Localization.concatenateStrings(watchCount, uploadDate, serviceName)
    }

    override fun updateFromItem(item: LocalItem, dateFormat: DateFormat) {
        if (item !is StreamStatisticsEntry) return

        itemVideoTitleView.text = item.title
        itemUploaderView.text = item.uploader

        if (item.duration > 0) {
            itemDurationView.text = Localization.getDurationString(item.duration)
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context!!, R.color.duration_background_color))
            itemDurationView.visibility = View.VISIBLE
        } else {
            itemDurationView.visibility = View.GONE
        }

        itemAdditionalDetails?.text = getStreamInfoDetailLine(item, dateFormat)

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.displayImage(item.thumbnailUrl, itemThumbnailView, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

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
