package org.schabi.newpipe.local.holder

import android.support.v4.content.ContextCompat
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

import java.text.DateFormat

open class LocalPlaylistStreamItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : LocalItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView
    val itemVideoTitleView: TextView
    val itemAdditionalDetailsView: TextView
    val itemDurationView: TextView
    val itemHandleView: View

    init {

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView)
        itemAdditionalDetailsView = itemView.findViewById(R.id.itemAdditionalDetails)
        itemDurationView = itemView.findViewById(R.id.itemDurationView)
        itemHandleView = itemView.findViewById(R.id.itemHandle)
    }

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_stream_playlist_item, parent) {}

    override fun updateFromItem(localItem: LocalItem, dateFormat: DateFormat) {
        if (localItem !is PlaylistStreamEntry) return

        itemVideoTitleView.text = localItem.title
        itemAdditionalDetailsView.text = Localization.concatenateStrings(localItem.uploader,
                NewPipe.getNameOfService(localItem.serviceId))

        if (localItem.duration > 0) {
            itemDurationView.text = Localization.getDurationString(localItem.duration)
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context!!,
                    R.color.duration_background_color))
            itemDurationView.visibility = View.VISIBLE
        } else {
            itemDurationView.visibility = View.GONE
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

        itemThumbnailView.setOnTouchListener(getOnTouchListener(localItem))
        itemHandleView.setOnTouchListener(getOnTouchListener(localItem))
    }

    private fun getOnTouchListener(item: PlaylistStreamEntry): View.OnTouchListener =
        View.OnTouchListener() { view, motionEvent ->
            view.performClick()
            if (itemBuilder != null && itemBuilder.onItemSelectedListener != null &&
                    motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemBuilder.onItemSelectedListener!!.drag(item,
                        this@LocalPlaylistStreamItemHolder)
            }
            false
        }
    }

