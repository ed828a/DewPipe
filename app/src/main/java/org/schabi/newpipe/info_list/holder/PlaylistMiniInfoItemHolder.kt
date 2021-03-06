package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants

open class PlaylistMiniInfoItemHolder(infoItemBuilder: InfoItemBuilder, layoutId: Int, parent: ViewGroup) : InfoItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemTitleView: TextView = itemView.findViewById(R.id.itemTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemStreamCountView: TextView = itemView.findViewById(R.id.itemStreamCountView)

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent)

    override fun updateFromItem(infoItem: InfoItem) {
        if (infoItem !is PlaylistInfoItem) return

        itemTitleView.text = infoItem.name
        itemStreamCountView.text = infoItem.streamCount.toString()
        itemUploaderView.text = infoItem.uploaderName

        itemBuilder.imageLoader
                .displayImage(infoItem.thumbnailUrl, itemThumbnailView, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        itemView.setOnClickListener { view ->
//            if (itemBuilder.onPlaylistSelectedListener != null) {
//                itemBuilder.onPlaylistSelectedListener!!.selected(infoItem)
//            }
            itemBuilder.onPlaylistSelectedListener?.selected(infoItem)
        }

        itemView.isLongClickable = true
        itemView.setOnLongClickListener { view ->
//            if (itemBuilder.onPlaylistSelectedListener != null) {
//                itemBuilder.onPlaylistSelectedListener!!.held(infoItem)
//            }
            itemBuilder.onPlaylistSelectedListener?.held(infoItem)
            true
        }
    }
}
