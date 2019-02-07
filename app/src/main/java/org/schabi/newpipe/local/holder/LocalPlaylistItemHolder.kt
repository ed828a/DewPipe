package org.schabi.newpipe.local.holder

import android.view.View
import android.view.ViewGroup

import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistMetadataEntry
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants

import java.text.DateFormat

open class LocalPlaylistItemHolder : PlaylistItemHolder {

    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : super(infoItemBuilder, parent) {}

    internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : super(infoItemBuilder, layoutId, parent) {}

    override fun updateFromItem(localItem: LocalItem, dateFormat: DateFormat) {
        if (localItem !is PlaylistMetadataEntry) return

        itemTitleView.text = localItem.name
        itemStreamCountView.text = localItem.streamCount.toString()
        itemUploaderView.visibility = View.INVISIBLE

        itemBuilder.displayImage(localItem.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS)

        super.updateFromItem(localItem, dateFormat)
    }
}
