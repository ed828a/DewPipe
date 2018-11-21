package org.schabi.newpipe.player.playqueue

import android.content.Context
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View

import com.nostra13.universalimageloader.core.ImageLoader

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

class PlayQueueItemBuilder(context: Context) {

    private var onItemClickListener: OnSelectedListener? = null

    interface OnSelectedListener {
        fun selected(item: PlayQueueItem, view: View)
        fun held(item: PlayQueueItem, view: View)
        fun onStartDrag(viewHolder: PlayQueueItemHolder)
    }

    fun setOnSelectedListener(listener: OnSelectedListener?) {
        this.onItemClickListener = listener
    }

    fun buildStreamInfoItem(holder: PlayQueueItemHolder, item: PlayQueueItem) {
        if (!TextUtils.isEmpty(item.title)) holder.itemVideoTitleView.text = item.title
        holder.itemAdditionalDetailsView.text = Localization.concatenateStrings(item.uploader,
                NewPipe.getNameOfService(item.serviceId))

        if (item.duration > 0) {
            holder.itemDurationView.text = Localization.getDurationString(item.duration)
        } else {
            holder.itemDurationView.visibility = View.GONE
        }

        ImageLoader.getInstance().displayImage(item.thumbnailUrl, holder.itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        holder.itemRoot.setOnClickListener { view ->
            if (onItemClickListener != null) {
                onItemClickListener!!.selected(item, view)
            }
        }

        holder.itemRoot.setOnLongClickListener { view ->
            if (onItemClickListener != null) {
                onItemClickListener!!.held(item, view)
                return@setOnLongClickListener true
            }
            false
        }

        holder.itemThumbnailView.setOnTouchListener(getOnTouchListener(holder))
        holder.itemHandle.setOnTouchListener(getOnTouchListener(holder))
    }

    private fun getOnTouchListener(holder: PlayQueueItemHolder): View.OnTouchListener = View.OnTouchListener()
        { view, motionEvent ->
            view.performClick()

            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN && onItemClickListener != null) {
                onItemClickListener!!.onStartDrag(holder)
            }
            false
        }


    companion object {

        private val TAG = PlayQueueItemBuilder::class.java.toString()
    }
}
