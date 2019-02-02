package org.schabi.newpipe.info_list.holder

import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

open class StreamMiniInfoItemHolder (infoItemBuilder: InfoItemBuilder, layoutId: Int, parent: ViewGroup) : InfoItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemVideoTitleView: TextView = itemView.findViewById(R.id.itemVideoTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemDurationView: TextView = itemView.findViewById(R.id.itemDurationView)

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_stream_mini_item, parent) {}

    override fun updateFromItem(infoItem: InfoItem) {
        if (infoItem !is StreamInfoItem) return

        itemVideoTitleView.text = infoItem.name
        itemUploaderView.text = infoItem.uploaderName

        when {
            infoItem.duration > 0 -> {
                itemDurationView.text = Localization.getDurationString(infoItem.duration)
                itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context,
                        R.color.duration_background_color))
                itemDurationView.visibility = View.VISIBLE
            }
            infoItem.streamType == StreamType.LIVE_STREAM -> {
                itemDurationView.setText(R.string.duration_live)
                itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context,
                        R.color.live_duration_background_color))
                itemDurationView.visibility = View.VISIBLE
            }
            else -> itemDurationView.visibility = View.GONE
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.imageLoader
                .displayImage(infoItem.thumbnailUrl,
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        itemView.setOnClickListener { view ->
            if (itemBuilder.onStreamSelectedListener != null) {
                itemBuilder.onStreamSelectedListener?.selected(infoItem)
            }
        }

        when (infoItem.streamType) {
            StreamType.AUDIO_STREAM, StreamType.VIDEO_STREAM, StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM -> enableLongClick(infoItem)
            StreamType.FILE, StreamType.NONE -> disableLongClick()
            else -> disableLongClick()
        }
    }

    private fun enableLongClick(item: StreamInfoItem) {
        itemView.isLongClickable = true
        itemView.setOnLongClickListener { view ->
            if (itemBuilder.onStreamSelectedListener != null) {
                itemBuilder.onStreamSelectedListener?.held(item)
            }
            true
        }
    }

    private fun disableLongClick() {
        itemView.isLongClickable = false
        itemView.setOnLongClickListener(null)
    }
}
