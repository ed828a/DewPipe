package org.schabi.newpipe.local.holder

import android.view.ViewGroup

import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

import java.text.DateFormat

open class RemotePlaylistItemHolder : PlaylistItemHolder {
    constructor(infoItemBuilder: LocalItemBuilder, parent: ViewGroup) : super(infoItemBuilder, parent) {}

    internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int, parent: ViewGroup) : super(infoItemBuilder, layoutId, parent) {}

    override fun updateFromItem(localItem: LocalItem, dateFormat: DateFormat) {
        if (localItem !is PlaylistRemoteEntity) return

        itemTitleView.text = localItem.name
        itemStreamCountView.text = localItem.streamCount.toString()
        itemUploaderView.text = Localization.concatenateStrings(localItem.uploader,
                NewPipe.getNameOfService(localItem.serviceId))

        itemBuilder.displayImage(localItem.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS)

        super.updateFromItem(localItem, dateFormat)
    }
}
