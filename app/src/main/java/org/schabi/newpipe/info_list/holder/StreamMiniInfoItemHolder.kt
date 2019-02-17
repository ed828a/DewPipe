package org.schabi.newpipe.info_list.holder

import android.content.Intent
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.PlayerView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.info_list.cache.TransportCache
import org.schabi.newpipe.player.PopupVideoPlayer.Companion.ACTION_CLOSE
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization

open class StreamMiniInfoItemHolder (infoItemBuilder: InfoItemBuilder, layoutId: Int, parent: ViewGroup) : InfoItemHolder(infoItemBuilder, layoutId, parent) {

    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemVideoTitleView: TextView = itemView.findViewById(R.id.itemVideoTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemDurationView: TextView = itemView.findViewById(R.id.itemDurationView)

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup) : this(infoItemBuilder, R.layout.list_stream_mini_item, parent) {}

    override fun updateFromItem(infoItem: InfoItem) {
        if (infoItem !is StreamInfoItem) return

        itemVideoTitleView.text = infoItem.name
        itemUploaderView.text = infoItem.uploaderName

        when {
            infoItem.duration > 0 -> {
                itemDurationView.text = Localization.getDurationString(infoItem.duration)
                itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context,
                        R.color.duration_background_color))
                itemDurationView.visibility = View.VISIBLE
            }
            infoItem.streamType == StreamType.LIVE_STREAM -> {
                itemDurationView.setText(R.string.duration_live)
                itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.context,
                        R.color.live_duration_background_color))
                itemDurationView.visibility = View.VISIBLE
            }
            else -> itemDurationView.visibility = View.GONE
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.imageLoader
                .displayImage(infoItem.thumbnailUrl,
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        itemView.setOnClickListener { view ->
            view.context.applicationContext.sendBroadcast(Intent(ACTION_CLOSE))

            if (itemBuilder.onStreamSelectedListener != null) {
                itemBuilder.onStreamSelectedListener?.selected(infoItem)
            }
        }

        when (infoItem.streamType) {
            StreamType.AUDIO_STREAM, StreamType.VIDEO_STREAM, StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM -> enableLongClick(infoItem)
            StreamType.FILE, StreamType.NONE -> disableLongClick()
            else -> disableLongClick()
        }
    }

    private fun enableLongClick(item: StreamInfoItem) {
        itemView.isLongClickable = true
        itemView.setOnLongClickListener { view ->
            if (itemBuilder.onStreamSelectedListener != null) {
                itemBuilder.onStreamSelectedListener?.held(item)
            }
            true
        }
    }

    private fun disableLongClick() {
        itemView.isLongClickable = false
        itemView.setOnLongClickListener(null)
    }

    private fun playOnView(mediaSource: MediaSource) {
        val appContext = itemBuilder.context.applicationContext
        val videoLayout = itemView.findViewById<FrameLayout>(R.id.videoLayout)
        val cover = itemView.findViewById<ImageView>(R.id.itemThumbnailView)

        // add SurfaceView
        val lastPlayingCover = TransportCache.transport.lastPlayingCover
        lastPlayingCover?.visibility = View.VISIBLE
        cover.visibility = View.GONE
        TransportCache.transport.lastPlayingCover = cover
        val videoSurfaceView = TransportCache.transport.videoSurfaceView
        videoLayout?.addView(videoSurfaceView)
        videoSurfaceView?.requestFocus()

        val player = TransportCache.transport.player!!
        with(player) {
            prepare(mediaSource)
            playWhenReady = true
        }
    }

    private fun removePreviousPlayView(videoView: PlayerView) {
        val parent = videoView.parent as ViewGroup? ?: return

        val index = parent.indexOfChild(videoView)
        Log.d(TAG, "removePreviousPlayView(): index = $index, parent = $parent")
        if (index >= 0) {
            parent.removeViewAt(index)
        }
    }

    companion object {
        const val TAG = "InfoItemHolder"
    }

}
