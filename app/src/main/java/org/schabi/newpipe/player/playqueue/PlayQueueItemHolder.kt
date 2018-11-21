package org.schabi.newpipe.player.playqueue

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R

/**
 * Created by Christian Schabesberger on 01.08.16.
 *
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger></chris.schabesberger>@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 *
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

class PlayQueueItemHolder(v: View) : RecyclerView.ViewHolder(v) {

    val itemVideoTitleView: TextView
    val itemDurationView: TextView
    val itemAdditionalDetailsView: TextView
    val itemSelected: ImageView
    val itemThumbnailView: ImageView
    val itemHandle: ImageView

    val itemRoot: View

    init {
        itemRoot = v.findViewById(R.id.itemRoot)
        itemVideoTitleView = v.findViewById(R.id.itemVideoTitleView)
        itemDurationView = v.findViewById(R.id.itemDurationView)
        itemAdditionalDetailsView = v.findViewById(R.id.itemAdditionalDetails)
        itemSelected = v.findViewById(R.id.itemSelected)
        itemThumbnailView = v.findViewById(R.id.itemThumbnailView)
        itemHandle = v.findViewById(R.id.itemHandle)
    }
}
