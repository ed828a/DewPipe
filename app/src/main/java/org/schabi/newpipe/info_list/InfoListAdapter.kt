package org.schabi.newpipe.info_list

import android.app.Activity
import android.content.Context
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.cache.TransportCache
import org.schabi.newpipe.info_list.holder.*
import org.schabi.newpipe.util.OnClickGesture
import java.util.*


class InfoListAdapter(activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val infoItemBuilder: InfoItemBuilder = InfoItemBuilder(activity)
    val itemsList: ArrayList<InfoItem> = ArrayList()
    private var useMiniVariant = false
    private var useGridVariant = false
    private var showFooter = false
    private var header: View? = null
    private var footer: View? = null

    // for surface view for playing video
    private var videoSurfaceView: PlayerView? = null
    private var player: SimpleExoPlayer? = null
    private val appContext: Context = activity.applicationContext


    fun setOnStreamSelectedListener(listener: OnClickGesture<StreamInfoItem>) {
        infoItemBuilder.onStreamSelectedListener = listener
    }

    fun setOnChannelSelectedListener(listener: OnClickGesture<ChannelInfoItem>) {
        infoItemBuilder.onChannelSelectedListener = listener
    }

    fun setOnPlaylistSelectedListener(listener: OnClickGesture<PlaylistInfoItem>) {
        infoItemBuilder.onPlaylistSelectedListener = listener
    }

    fun useMiniItemVariants(useMiniVariant: Boolean) {
        this.useMiniVariant = useMiniVariant
    }

    fun setGridItemVariants(useGridVariant: Boolean) {
        this.useGridVariant = useGridVariant
    }

    fun addInfoItemList(data: List<InfoItem>?) {
        data?.let { dataList ->
            Log.d(TAG, "addInfoItemList() before > infoItemList.size() = ${itemsList.size}, data.size() = ${dataList.size}")

            val offsetStart = sizeConsideringHeaderOffset()
            itemsList.addAll(dataList)

            Log.d(TAG, "addInfoItemList() after > offsetStart = $offsetStart, infoItemList.size() = ${itemsList.size}, header = $header, footer = $footer, showFooter = $showFooter")

            notifyItemRangeInserted(offsetStart, dataList.size)

            if (footer != null && showFooter) {
                val footerNow = sizeConsideringHeaderOffset()
                notifyItemMoved(offsetStart, footerNow)

                Log.d(TAG, "addInfoItemList() footer getTabFrom $offsetStart to $footerNow")
            }
        }
    }

    fun addInfoItem(data: InfoItem?) {
        if (data != null) {
            Log.d(TAG, "addInfoItem() before > infoItemList.size() = ${itemsList.size}, thread = ${Thread.currentThread()}")

            val positionInserted = sizeConsideringHeaderOffset()
            itemsList.add(data)

            Log.d(TAG, "addInfoItem() after > position = $positionInserted, infoItemList.size() = ${itemsList.size}, header = $header, footer = $footer, showFooter = $showFooter")
            notifyItemInserted(positionInserted)

            if (footer != null && showFooter) {
                val footerNow = sizeConsideringHeaderOffset()
                notifyItemMoved(positionInserted, footerNow)

                Log.d(TAG, "addInfoItem() footer getTabFrom $positionInserted to $footerNow")
            }
        }
    }

    fun clearStreamItemList() {
        if (itemsList.isEmpty()) {
            return
        }
        itemsList.clear()
        notifyDataSetChanged()
    }

    fun setHeader(header: View?) {
        val changed = header !== this.header
        this.header = header
        if (changed) notifyDataSetChanged()
    }

    fun setFooter(view: View?) {
        this.footer = view
    }

    fun showFooter(show: Boolean) {
        Log.d(TAG, "showFooter() called with: show = [$show]")
        if (show == showFooter) return

        showFooter = show
        if (show)
            notifyItemInserted(sizeConsideringHeaderOffset())
        else
            notifyItemRemoved(sizeConsideringHeaderOffset())
    }


    private fun sizeConsideringHeaderOffset(): Int {
        val size = itemsList.size + if (header != null) 1 else 0
        Log.d(TAG, "sizeConsideringHeaderOffset() called → $size")
        return size
    }

    override fun getItemCount(): Int {
        var count = itemsList.size
        if (header != null) count++
        if (footer != null && showFooter) count++

        Log.d(TAG, "getItemCount() called, count = $count, infoItemList.size() = ${itemsList.size}, header = $header, footer = $footer, showFooter = $showFooter")
        return count
    }

    override fun getItemViewType(pos: Int): Int {
        var position = pos
        Log.d(TAG, "getItemViewType() called with: pos = [$position]")

        if (header != null && position == 0) {
            return HEADER_TYPE
        } else if (header != null) {
            position--
        }
        if (footer != null && position == itemsList.size && showFooter) {
            return FOOTER_TYPE
        }
        val item = itemsList[position]
        return when (item.infoType) {
            InfoItem.InfoType.STREAM -> when {
                useGridVariant -> GRID_STREAM_HOLDER_TYPE
                useMiniVariant -> MINI_STREAM_HOLDER_TYPE
                else -> STREAM_HOLDER_TYPE
            }
            InfoItem.InfoType.CHANNEL -> when {
                useGridVariant -> GRID_CHANNEL_HOLDER_TYPE
                useMiniVariant -> MINI_CHANNEL_HOLDER_TYPE
                else -> CHANNEL_HOLDER_TYPE
            }
            InfoItem.InfoType.PLAYLIST -> when {
                useGridVariant -> GRID_PLAYLIST_HOLDER_TYPE
                useMiniVariant -> MINI_PLAYLIST_HOLDER_TYPE
                else -> PLAYLIST_HOLDER_TYPE
            }
            else -> {
                Log.e(TAG, "item type is invalid: item.infoType = ${item.infoType}")
                -1
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d(TAG, "onCreateViewHolder() called with: parent = [$parent], viewType = [$viewType]")
        return when (viewType) {
            HEADER_TYPE -> HeaderFooterHolder(header!!)
            FOOTER_TYPE -> HeaderFooterHolder(footer!!)
            MINI_STREAM_HOLDER_TYPE -> StreamMiniInfoItemHolder(infoItemBuilder, parent)
            STREAM_HOLDER_TYPE -> StreamInfoItemHolder(infoItemBuilder, parent)
            GRID_STREAM_HOLDER_TYPE -> StreamGridInfoItemHolder(infoItemBuilder, parent)
            MINI_CHANNEL_HOLDER_TYPE -> ChannelMiniInfoItemHolder(infoItemBuilder, parent)
            CHANNEL_HOLDER_TYPE -> ChannelInfoItemHolder(infoItemBuilder, parent)
            GRID_CHANNEL_HOLDER_TYPE -> ChannelGridInfoItemHolder(infoItemBuilder, parent)
            MINI_PLAYLIST_HOLDER_TYPE -> PlaylistMiniInfoItemHolder(infoItemBuilder, parent)
            PLAYLIST_HOLDER_TYPE -> PlaylistInfoItemHolder(infoItemBuilder, parent)
            GRID_PLAYLIST_HOLDER_TYPE -> PlaylistGridInfoItemHolder(infoItemBuilder, parent)
            else -> {
                Log.e(TAG, "onCreateViewHolder(), viewType is invalid: viewType = $viewType")
                FallbackViewHolder(View(parent.context))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var position = position
        Log.d(TAG, "onBindViewHolder() called with: holder = [${holder.javaClass.simpleName}], position = [$position]")
        when {
            holder is InfoItemHolder -> {
                // If header isn't null, offset the items by -1
                if (header != null) position--

                holder.updateFromItem(itemsList[position])
            }
            holder is HeaderFooterHolder && position == 0 && header != null -> holder.view = header!!
            holder is HeaderFooterHolder && position == sizeConsideringHeaderOffset() && footer != null && showFooter -> holder.view = footer!!
        }
    }

    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val type = getItemViewType(position)
                return if (type == HEADER_TYPE || type == FOOTER_TYPE) spanCount else 1
            }
        }
    }


    // make sure this one is called in onStart() to prevent the leakage.
    internal fun initializePlayer() {
        // 1. create SurfaceView
        videoSurfaceView = PlayerView(appContext)
        videoSurfaceView!!.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        videoSurfaceView!!.useController = false
        videoSurfaceView!!.setShowBuffering(true)

        // 2. create SimpleExoPlayer
        val bandwidthMeter = DefaultBandwidthMeter()
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(trackSelectionFactory)
        val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 16))
                .setBufferDurationsMs(
                        MIN_BUFFER_DURATION,
                        MAX_BUFFER_DURATION,
                        MIN_PLAYBACK_START_BUFFER,
                        MIN_PLAYBACK_RESUME_BUFFER
                )
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .createDefaultLoadControl()

        player = ExoPlayerFactory.newSimpleInstance(
                appContext,
                DefaultRenderersFactory(appContext),
                trackSelector,
                loadControl
        )

        // 3. bind SurfaceView to ExoPlayer
        videoSurfaceView?.player = player

        player!!.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
//                super.onPlayerStateChanged(playWhenReady, playbackState)
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        TransportCache.transport.lastPlayingCover?.visibility = View.VISIBLE
                        videoSurfaceView!!.alpha = 0.5f
                        Log.d(TAG, "onPlayerStateChanged(): Buffering")

                    }

                    Player.STATE_READY -> {
                        Log.d(TAG, "onPlayerStateChanged(): Ready")
                        TransportCache.transport.lastPlayingCover?.visibility = View.GONE
                        videoSurfaceView?.visibility = View.VISIBLE
                        videoSurfaceView?.alpha = 1.0f

                    }

                    Player.STATE_ENDED -> {
                        Log.d(TAG, "onPlayerStateChanged(): Ended")
                        // make circularly playing
//                        player?.seekTo(0)
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            }

            override fun onSeekProcessed() {
            }

            override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
            }

            override fun onPlayerError(error: ExoPlaybackException?) {
            }

            override fun onLoadingChanged(isLoading: Boolean) {
            }

            override fun onPositionDiscontinuity(reason: Int) {
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            }

            override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            }


        })

        TransportCache.transport.player = player
        TransportCache.transport.videoSurfaceView = videoSurfaceView
    }

    fun onRelease() {
        TransportCache.reset()
    }


    companion object {
        private val TAG = InfoListAdapter::class.java.simpleName

        private const val HEADER_TYPE = 0
        private const val FOOTER_TYPE = 1

        private const val MINI_STREAM_HOLDER_TYPE = 0x100
        private const val STREAM_HOLDER_TYPE = 0x101
        private const val GRID_STREAM_HOLDER_TYPE = 0x102
        private const val MINI_CHANNEL_HOLDER_TYPE = 0x200
        private const val CHANNEL_HOLDER_TYPE = 0x201
        private const val GRID_CHANNEL_HOLDER_TYPE = 0x202
        private const val MINI_PLAYLIST_HOLDER_TYPE = 0x300
        private const val PLAYLIST_HOLDER_TYPE = 0x301
        private const val GRID_PLAYLIST_HOLDER_TYPE = 0x302

        const val MIN_BUFFER_DURATION = 5000
        const val MAX_BUFFER_DURATION = 15000
        const val MIN_PLAYBACK_START_BUFFER = 1500
        const val MIN_PLAYBACK_RESUME_BUFFER = 5000
    }
}
