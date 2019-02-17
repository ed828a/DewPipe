package org.schabi.newpipe.info_list.holder

import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.util.Localization


class StreamInfoItemHolder(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : StreamMiniInfoItemHolder(infoItemBuilder, R.layout.list_stream_item, parent) {

    val itemAdditionalDetails: TextView = itemView.findViewById(R.id.itemAdditionalDetails)

    override fun updateFromItem(infoItem: InfoItem) {
        super.updateFromItem(infoItem)

        if (infoItem !is StreamInfoItem) return

        itemAdditionalDetails.text = getStreamInfoDetailLine(infoItem)
    }

    private fun getStreamInfoDetailLine(infoItem: StreamInfoItem): String {
        var viewsAndDate = ""
        if (infoItem.viewCount >= 0) {
            viewsAndDate = Localization.shortViewCount(itemBuilder.context, infoItem.viewCount)
        }
        if (!TextUtils.isEmpty(infoItem.uploadDate)) {
            if (viewsAndDate.isEmpty()) {
                viewsAndDate = infoItem.uploadDate
            } else {
                viewsAndDate += " • " + infoItem.uploadDate
            }
        }
        return viewsAndDate
    }


}
