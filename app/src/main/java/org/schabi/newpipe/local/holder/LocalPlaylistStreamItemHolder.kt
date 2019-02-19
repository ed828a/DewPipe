package org.schabi.newpipe.local.holder

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntry
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization
import java.text.DateFormat

open class LocalPlaylistStreamItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemVideoTitleView: TextView = itemView.findViewById(R.id.itemVideoTitleView)
    val itemAdditionalDetailsView: TextView = itemView.findViewById(R.id.itemAdditionalDetails)
    val itemDurationView: TextView = itemView.findViewById(R.id.itemDurationView)
    val itemHandleView: View = itemView.findViewById(R.id.itemHandle)

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_stream_playlist_item, parent) {}

    @SuppressLint("ClickableViewAccessibility")
    override fun updateFromItem(item: LocalItem, dateFormat: DateFormat) {
        if (item !is PlaylistStreamEntry) return

        itemVideoTitleView.text = item.title
        itemAdditionalDetailsView.text = Localization.concatenateStrings(item.uploader, NewPipe.getNameOfService(item.serviceId))

        if (item.duration > 0) {
            itemDurationView.text = Localization.getDurationString(item.duration)
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context!!, R.color.duration_background_color))
            itemDurationView.visibility = View.VISIBLE
        } else {
            itemDurationView.visibility = View.GONE
        }

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

        itemThumbnailView.setOnTouchListener(getOnTouchListener(item))
        itemHandleView.setOnTouchListener(getOnTouchListener(item))
    }

    private fun getOnTouchListener(item: PlaylistStreamEntry): View.OnTouchListener =
        View.OnTouchListener { view, motionEvent ->
            view.performClick()
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                itemBuilder.onItemSelectedListener?.drag(item, this@LocalPlaylistStreamItemHolder)
            }
            false
        }
    }

