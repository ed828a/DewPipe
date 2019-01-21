package org.schabi.newpipe.player.playqueue

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.holder.FallbackViewHolder

import io.reactivex.Observer
import io.reactivex.annotations.NonNull
import io.reactivex.disposables.Disposable
import org.schabi.newpipe.player.playqueue.events.*

/**
 * Created by Christian Schabesberger on 01.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger></chris.schabesberger>@mailbox.org>
 * InfoListAdapter.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

class PlayQueueAdapter(context: Context, private val playQueue: PlayQueue) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val playQueueItemBuilder: PlayQueueItemBuilder
    private var showFooter = false
    private var footer: View? = null

    private var playQueueReactor: Disposable? = null

    private val reactor: Observer<PlayQueueEvent>
        get() = object : Observer<PlayQueueEvent> {
            override fun onSubscribe(@NonNull d: Disposable) {
                if (playQueueReactor != null) playQueueReactor!!.dispose()
                playQueueReactor = d
            }

            override fun onNext(@NonNull playQueueMessage: PlayQueueEvent) {
                if (playQueueReactor != null) onPlayQueueChanged(playQueueMessage)
            }

            override fun onError(@NonNull e: Throwable) {}

            override fun onComplete() {
                dispose()
            }
        }

    val items: List<PlayQueueItem>
        get() = playQueue.streams!!

    inner class HFHolder(var view: View) : RecyclerView.ViewHolder(view)

    init {
        if (playQueue.broadcastReceiver == null) {
            throw IllegalStateException("Play Queue has not been initialized.")
        }

        this.playQueueItemBuilder = PlayQueueItemBuilder(context)

        playQueue.broadcastReceiver!!.toObservable().subscribe(reactor)
    }

    private fun onPlayQueueChanged(message: PlayQueueEvent) {
        when (message.type()) {
            PlayQueueEventType.RECOVERY -> {
            }
            PlayQueueEventType.SELECT -> {
                val selectEvent = message as SelectEvent
                notifyItemChanged(selectEvent.oldIndex)
                notifyItemChanged(selectEvent.newIndex)
            }
            PlayQueueEventType.APPEND -> {
                val appendEvent = message as AppendEvent
                notifyItemRangeInserted(playQueue.size(), appendEvent.amount)
            }
            PlayQueueEventType.ERROR -> {
                val errorEvent = message as ErrorEvent
                if (!errorEvent.isSkippable) {
                    notifyItemRemoved(errorEvent.errorIndex)
                }
                notifyItemChanged(errorEvent.errorIndex)
                notifyItemChanged(errorEvent.queueIndex)
            }
            PlayQueueEventType.REMOVE -> {
                val removeEvent = message as RemoveEvent
                notifyItemRemoved(removeEvent.removeIndex)
                notifyItemChanged(removeEvent.queueIndex)
            }
            PlayQueueEventType.MOVE -> {
                val moveEvent = message as MoveEvent
                notifyItemMoved(moveEvent.fromIndex, moveEvent.toIndex)
            }
            PlayQueueEventType.INIT, PlayQueueEventType.REORDER -> notifyDataSetChanged()
            else -> notifyDataSetChanged()
        }// Do nothing.
    }

    fun dispose() {
        if (playQueueReactor != null) playQueueReactor!!.dispose()
        playQueueReactor = null
    }

    fun setSelectedListener(listener: PlayQueueItemBuilder.OnSelectedListener) {
        playQueueItemBuilder.setOnSelectedListener(listener)
    }

    fun unsetSelectedListener() {
        playQueueItemBuilder.setOnSelectedListener(null)
    }

    fun setFooter(footer: View) {
        this.footer = footer
        notifyItemChanged(playQueue.size())
    }

    fun showFooter(show: Boolean) {
        showFooter = show
        notifyItemChanged(playQueue.size())
    }

    override fun getItemCount(): Int {
        var count = playQueue.streams!!.size
        if (footer != null && showFooter) count++
        return count
    }

    override fun getItemViewType(position: Int): Int {
        return if (footer != null && position == playQueue.streams!!.size && showFooter) {
            FOOTER_VIEW_TYPE_ID
        } else ITEM_VIEW_TYPE_ID

    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        return when (type) {
            FOOTER_VIEW_TYPE_ID -> HFHolder(footer!!)
            ITEM_VIEW_TYPE_ID -> PlayQueueItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.play_queue_item, parent, false))
            else -> {
                Log.e(TAG, "Attempting to create view holder with undefined type: $type")
                FallbackViewHolder(View(parent.context))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PlayQueueItemHolder) {

            // Build the list item
            playQueueItemBuilder.buildStreamInfoItem(holder, playQueue.streams!![position])

            // Check if the current item should be selected/highlighted
            val isSelected = playQueue.index == position
            holder.itemSelected.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            holder.itemView.isSelected = isSelected
        } else if (holder is HFHolder && position == playQueue.streams!!.size && footer != null && showFooter) {
            holder.view = footer!!
        }
    }

    companion object {
        private val TAG = PlayQueueAdapter::class.java.toString()

        private val ITEM_VIEW_TYPE_ID = 0
        private val FOOTER_VIEW_TYPE_ID = 1
    }
}
