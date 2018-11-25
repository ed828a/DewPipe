package org.schabi.newpipe.info_list

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup

import com.nostra13.universalimageloader.core.ImageLoader

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder
import org.schabi.newpipe.info_list.holder.InfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder
import org.schabi.newpipe.util.OnClickGesture

/*
 * Created by Christian Schabesberger on 26.09.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoItemBuilder.java is part of NewPipe.
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

class InfoItemBuilder(val context: Context) {
    val imageLoader = ImageLoader.getInstance()

    var onStreamSelectedListener: OnClickGesture<StreamInfoItem>? = null
    var onChannelSelectedListener: OnClickGesture<ChannelInfoItem>? = null
    var onPlaylistSelectedListener: OnClickGesture<PlaylistInfoItem>? = null

    @JvmOverloads
    fun buildView(parent: ViewGroup, infoItem: InfoItem, useMiniVariant: Boolean = false): View {
        val holder = holderFromInfoType(parent, infoItem.infoType, useMiniVariant)
        holder.updateFromItem(infoItem)
        return holder.itemView
    }

    private fun holderFromInfoType(parent: ViewGroup, infoType: InfoItem.InfoType, useMiniVariant: Boolean): InfoItemHolder {
        return when (infoType) {
            InfoItem.InfoType.STREAM -> if (useMiniVariant) StreamMiniInfoItemHolder(this, parent) else StreamInfoItemHolder(this, parent)
            InfoItem.InfoType.CHANNEL -> if (useMiniVariant) ChannelMiniInfoItemHolder(this, parent) else ChannelInfoItemHolder(this, parent)
            InfoItem.InfoType.PLAYLIST -> if (useMiniVariant) PlaylistMiniInfoItemHolder(this, parent) else PlaylistInfoItemHolder(this, parent)
            else -> {
                Log.e(TAG, "Trollolo")
                throw RuntimeException("InfoType not expected = " + infoType.name)
            }
        }
    }

    companion object {
        private val TAG = InfoItemBuilder::class.java.toString()
    }

}
