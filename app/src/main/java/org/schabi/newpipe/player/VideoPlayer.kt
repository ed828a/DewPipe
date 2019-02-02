package org.schabi.newpipe.player

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceView
import android.view.View
import android.widget.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.text.CaptionStyleCompat
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.video.VideoListener
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed
import org.schabi.newpipe.player.helper.PlayerHelper.getTimeString
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.MediaSourceTag
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.AnimationUtils
import org.schabi.newpipe.util.AnimationUtils.animateView
import java.util.*

/**
 * Base for **video** players
 *
 */
abstract class VideoPlayer(val TAG: String,
                           context: Context
) : BasePlayer(context), VideoListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener, Player.EventListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {

    private var availableStreams: List<VideoStream>? = null
    private var selectedStreamIndex: Int = 0

    protected var wasPlaying = false

    private val resolver: VideoPlaybackResolver
    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    var rootView: View? = null

    var aspectRatioFrameLayout: AspectRatioFrameLayout? = null
        private set
    var surfaceView: SurfaceView? = null
        private set
    var surfaceForeground: View? = null
        private set

    var loadingPanel: View? = null
        private set
    var endScreen: ImageView? = null
        private set
    var controlAnimationView: ImageView? = null
        private set

    var controlsRoot: View? = null
        private set
    var currentDisplaySeek: TextView? = null
        private set

    var bottomControlsRoot: View? = null
        private set
    var playbackSeekBar: SeekBar? = null
        private set
    var playbackCurrentTime: TextView? = null
        private set
    var playbackEndTime: TextView? = null
        private set
    private var playbackLiveSync: TextView? = null
    private var playbackSpeedTextView: TextView? = null

    var topControlsRoot: View? = null
        private set
    var qualityTextView: TextView? = null
        private set

    var subtitleView: SubtitleView? = null
        private set

    var resizeView: TextView? = null
        private set
    var captionTextView: TextView? = null
        private set

    private var controlViewAnimator: ValueAnimator? = null
    val controlsVisibilityHandler = Handler()

    var isSomePopupMenuVisible = false
        internal set
    private val qualityPopupMenuGroupId = 69
    var qualityPopupMenu: PopupMenu? = null
        private set

    private val playbackSpeedPopupMenuGroupId = 79
    var playbackSpeedPopupMenu: PopupMenu? = null
        private set

    private val captionPopupMenuGroupId = 89
    private var captionPopupMenu: PopupMenu? = null
    ///////////////////////////////////////////////////////////////////////////
    // Playback Listener
    ///////////////////////////////////////////////////////////////////////////

    protected abstract val qualityResolver: VideoPlaybackResolver.QualityResolver

    val isControlsVisible: Boolean
        get() = controlsRoot != null && controlsRoot!!.visibility == View.VISIBLE

    ///////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    ///////////////////////////////////////////////////////////////////////////

    var playbackQuality: String?
        get() = resolver.playbackQuality
        set(quality) {
            this.resolver.playbackQuality = quality
        }

    val selectedVideoStream: VideoStream?
        get() = if (selectedStreamIndex >= 0 && availableStreams != null &&
                availableStreams!!.size > selectedStreamIndex)
            availableStreams!![selectedStreamIndex]
        else
            null

    init {
        this.resolver = VideoPlaybackResolver(context, dataSource, qualityResolver)
    }

    fun setup(rootView: View) {
        initViews(rootView)
        setup()
    }

    open fun initViews(rootView: View) {
        this.rootView = rootView
        this.aspectRatioFrameLayout = rootView.findViewById(R.id.aspectRatioLayout)
        this.surfaceView = rootView.findViewById(R.id.surfaceView)
        this.surfaceForeground = rootView.findViewById(R.id.surfaceForeground)
        this.loadingPanel = rootView.findViewById(R.id.loading_panel)
        this.endScreen = rootView.findViewById(R.id.endScreen)
        this.controlAnimationView = rootView.findViewById(R.id.controlAnimationView)
        this.controlsRoot = rootView.findViewById(R.id.playbackControlRoot)
        this.currentDisplaySeek = rootView.findViewById(R.id.currentDisplaySeek)
        this.playbackSeekBar = rootView.findViewById(R.id.playbackSeekBar)
        this.playbackCurrentTime = rootView.findViewById(R.id.playbackCurrentTime)
        this.playbackEndTime = rootView.findViewById(R.id.playbackEndTime)
        this.playbackLiveSync = rootView.findViewById(R.id.playbackLiveSync)
        this.playbackSpeedTextView = rootView.findViewById(R.id.playbackSpeed)
        this.bottomControlsRoot = rootView.findViewById(R.id.bottomControls)
        this.topControlsRoot = rootView.findViewById(R.id.topControls)
        this.qualityTextView = rootView.findViewById(R.id.qualityTextView)

        this.subtitleView = rootView.findViewById(R.id.subtitleView)

        val captionScale = PlayerHelper.getCaptionScale(context)
        val captionStyle = PlayerHelper.getCaptionStyle(context)
        setupSubtitleView(subtitleView!!, captionScale, captionStyle)

        this.resizeView = rootView.findViewById(R.id.resizeTextView)
        resizeView!!.text = PlayerHelper.resizeTypeOf(context, aspectRatioFrameLayout!!.resizeMode)

        this.captionTextView = rootView.findViewById(R.id.captionTextView)

        //this.aspectRatioFrameLayout.setAspectRatio(16.0f / 9.0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            playbackSeekBar!!.thumb.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        this.playbackSeekBar!!.progressDrawable.setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY)

        this.qualityPopupMenu = PopupMenu(context, qualityTextView)
        this.playbackSpeedPopupMenu = PopupMenu(context, playbackSpeedTextView)
        this.captionPopupMenu = PopupMenu(context, captionTextView)

        (this.loadingPanel!!.findViewById<View>(R.id.progressBarLoadingPanel) as ProgressBar)
                .indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)
    }

    protected abstract fun setupSubtitleView(view: SubtitleView,
                                             captionScale: Float,
                                             captionStyle: CaptionStyleCompat)

    override fun initListeners() {
        super.initListeners()
        playbackSeekBar!!.setOnSeekBarChangeListener(this)
        playbackSpeedTextView!!.setOnClickListener(this)
        qualityTextView!!.setOnClickListener(this)
        captionTextView!!.setOnClickListener(this)
        resizeView!!.setOnClickListener(this)
        playbackLiveSync!!.setOnClickListener(this)
    }

    override fun initPlayer(playOnReady: Boolean) {
        super.initPlayer(playOnReady)

        // Setup video view
        player?.setVideoSurfaceView(surfaceView)
        player?.addVideoListener(this)

        // Setup subtitle view
        player?.addTextOutput { cues -> subtitleView!!.onCues(cues) }

        // Setup audio session with onboard equalizer
        if (Build.VERSION.SDK_INT >= 21) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context)))
        }
    }

    override fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra(BasePlayer.PLAYBACK_QUALITY)) {
            playbackQuality = intent.getStringExtra(BasePlayer.PLAYBACK_QUALITY)
        }

        super.handleIntent(intent)
    }

    ///////////////////////////////////////////////////////////////////////////
    // UI Builders
    ///////////////////////////////////////////////////////////////////////////

    fun buildQualityMenu() {
        if (qualityPopupMenu == null) return

        qualityPopupMenu!!.menu.removeGroup(qualityPopupMenuGroupId)
        for (i in availableStreams!!.indices) {
            val videoStream = availableStreams!![i]
            qualityPopupMenu!!.menu.add(qualityPopupMenuGroupId, i, Menu.NONE,
                    MediaFormat.getNameById(videoStream.formatId) + " " + videoStream.resolution)
        }
        if (selectedVideoStream != null) {
            qualityTextView!!.text = selectedVideoStream!!.resolution
        }
        qualityPopupMenu!!.setOnMenuItemClickListener(this)
        qualityPopupMenu!!.setOnDismissListener(this)
    }

    private fun buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) return

        playbackSpeedPopupMenu!!.menu.removeGroup(playbackSpeedPopupMenuGroupId)

        for (i in BasePlayer.PLAYBACK_SPEEDS.indices) {
            playbackSpeedPopupMenu!!.menu.add(playbackSpeedPopupMenuGroupId, i, Menu.NONE, formatSpeed(BasePlayer.PLAYBACK_SPEEDS[i].toDouble()))
        }
        playbackSpeedTextView!!.text = formatSpeed(playbackSpeed.toDouble())
        playbackSpeedPopupMenu!!.setOnMenuItemClickListener(this)
        playbackSpeedPopupMenu!!.setOnDismissListener(this)
    }

    private fun buildCaptionMenu(availableLanguages: List<String>) {
        if (captionPopupMenu == null) return
        captionPopupMenu!!.menu.removeGroup(captionPopupMenuGroupId)

        // Add option for turning off caption
        val captionOffItem = captionPopupMenu!!.menu.add(captionPopupMenuGroupId,
                0, Menu.NONE, R.string.caption_none)
        captionOffItem.setOnMenuItemClickListener { menuItem ->
            val textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT)
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true))
            }
            true
        }

        // Add all available captions
        for (i in availableLanguages.indices) {
            val captionLanguage = availableLanguages[i]
            val captionItem = captionPopupMenu!!.menu.add(captionPopupMenuGroupId,
                    i + 1, Menu.NONE, captionLanguage)
            captionItem.setOnMenuItemClickListener { menuItem ->
                val textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT)
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.preferredTextLanguage = captionLanguage
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false))
                }
                true
            }
        }
        captionPopupMenu!!.setOnDismissListener(this)
    }


    private fun updateStreamRelatedViews() {
        if (currentMetadata == null) return

        val tag = currentMetadata
        val metadata = tag!!.metadata

        qualityTextView!!.visibility = View.GONE
        playbackSpeedTextView!!.visibility = View.GONE

        playbackEndTime!!.visibility = View.GONE
        playbackLiveSync!!.visibility = View.GONE

        when (metadata.streamType) {
            StreamType.AUDIO_STREAM -> {
                surfaceView!!.visibility = View.GONE
                endScreen!!.visibility = View.VISIBLE
                playbackEndTime!!.visibility = View.VISIBLE
            }

            StreamType.AUDIO_LIVE_STREAM -> {
                surfaceView!!.visibility = View.GONE
                endScreen!!.visibility = View.VISIBLE
                playbackLiveSync!!.visibility = View.VISIBLE
            }

            StreamType.LIVE_STREAM -> {
                surfaceView!!.visibility = View.VISIBLE
                endScreen!!.visibility = View.GONE
                playbackLiveSync!!.visibility = View.VISIBLE
            }

            StreamType.VIDEO_STREAM -> {
                if (metadata.videoStreams.size > 0 || metadata.videoOnlyStreams.size > 0) {
                    availableStreams = tag.sortedAvailableVideoStreams
                    selectedStreamIndex = tag.selectedVideoStreamIndex
                    buildQualityMenu()

                    qualityTextView!!.visibility = View.VISIBLE
                    surfaceView!!.visibility = View.VISIBLE
                    endScreen!!.visibility = View.GONE
                    playbackEndTime!!.visibility = View.VISIBLE
                }
            }
            else -> {
                endScreen!!.visibility = View.GONE
                playbackEndTime!!.visibility = View.VISIBLE
            }
        }

        buildPlaybackSpeedMenu()
        playbackSpeedTextView!!.visibility = View.VISIBLE
    }

    override fun onMetadataChanged(tag: MediaSourceTag) {
        super.onMetadataChanged(tag)
        updateStreamRelatedViews()
    }

    override fun sourceOf(item: PlayQueueItem, info: StreamInfo): MediaSource? {
        return resolver.resolve(info)
    }

    ///////////////////////////////////////////////////////////////////////////
    // States Implementation
    ///////////////////////////////////////////////////////////////////////////

    override fun onBlocked() {
        super.onBlocked()

        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        animateView(controlsRoot!!, false, DEFAULT_CONTROLS_DURATION.toLong())

        playbackSeekBar!!.isEnabled = false
        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-, so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            playbackSeekBar!!.thumb.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)

        loadingPanel!!.setBackgroundColor(Color.BLACK)
        animateView(loadingPanel!!, true, 0)
        animateView(surfaceForeground!!, true, 100)
    }

    override fun onPlaying() {
        super.onPlaying()

        updateStreamRelatedViews()

        showAndAnimateControl(-1, true)

        playbackSeekBar!!.isEnabled = true
        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-, so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            playbackSeekBar!!.thumb.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)

        loadingPanel!!.visibility = View.GONE

        animateView(currentDisplaySeek!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 200)
    }

    override fun onBuffering() {
        if (DEBUG) Log.d(TAG, "onBuffering() called")
        loadingPanel!!.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onPaused() {
        if (DEBUG) Log.d(TAG, "onPaused() called")
        showControls(400)
        loadingPanel!!.visibility = View.GONE
    }

    override fun onPausedSeek() {
        if (DEBUG) Log.d(TAG, "onPausedSeek() called")
        showAndAnimateControl(-1, true)
    }

    override fun onCompleted() {
        super.onCompleted()

        showControls(500)
        animateView(endScreen!!, true, 800)
        animateView(currentDisplaySeek!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 200)
        loadingPanel!!.visibility = View.GONE

        animateView(surfaceForeground!!, true, 100)
    }

    ///////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        super.onTracksChanged(trackGroups, trackSelections)
        onTextTrackUpdate()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        playbackSpeedTextView!!.text = formatSpeed(playbackParameters.speed.toDouble())
    }

    override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: width / height = [" + width + " / " + height + " = " + width.toFloat() / height + "], unappliedRotationDegrees = [" + unappliedRotationDegrees + "], pixelWidthHeightRatio = [" + pixelWidthHeightRatio + "]")
        }
        aspectRatioFrameLayout!!.setAspectRatio(width.toFloat() / height)
    }

    override fun onRenderedFirstFrame() {
        animateView(surfaceForeground!!, false, 100)
    }

    ///////////////////////////////////////////////////////////////////////////
    // ExoPlayer Track Updates
    ///////////////////////////////////////////////////////////////////////////

    private fun onTextTrackUpdate() {
        val textRenderer = getRendererIndex(C.TRACK_TYPE_TEXT)

        if (captionTextView == null) return
        if (trackSelector.currentMappedTrackInfo == null || textRenderer == RENDERER_UNAVAILABLE) {
            captionTextView!!.visibility = View.GONE
            return
        }

        val textTracks = trackSelector.currentMappedTrackInfo!!
                .getTrackGroups(textRenderer)

        // Extract all loaded languages
        val availableLanguages = ArrayList<String>(textTracks.length)
        for (i in 0 until textTracks.length) {
            val textTrack = textTracks.get(i)
            if (textTrack.length > 0 && textTrack.getFormat(0) != null) {
                availableLanguages.add(textTrack.getFormat(0).language!!)
            }
        }

        // Normalize mismatching language strings
        val preferredLanguage = trackSelector.preferredTextLanguage

        // Build UI
        buildCaptionMenu(availableLanguages)
        if (trackSelector.parameters.getRendererDisabled(textRenderer) ||
                preferredLanguage == null || !availableLanguages.contains(preferredLanguage)) {
            captionTextView!!.setText(R.string.caption_none)
        } else {
            captionTextView!!.text = preferredLanguage
        }
        captionTextView!!.visibility = if (availableLanguages.isEmpty()) View.GONE else View.VISIBLE
    }

    ///////////////////////////////////////////////////////////////////////////
    // General Player
    ///////////////////////////////////////////////////////////////////////////

    override fun onPrepared(playWhenReady: Boolean) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [$playWhenReady]")

        playbackSeekBar!!.max = player!!.duration.toInt()
        playbackEndTime!!.text = getTimeString(player!!.duration.toInt())
        playbackSpeedTextView!!.text = formatSpeed(playbackSpeed.toDouble())

        super.onPrepared(playWhenReady)
    }

    override fun destroy() {
        super.destroy()
        if (endScreen != null) endScreen!!.setImageBitmap(null)
    }

    override fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
        if (!isPrepared) return

        if (duration != playbackSeekBar!!.max) {
            playbackEndTime!!.text = getTimeString(duration)
            playbackSeekBar!!.max = duration
        }
        if (currentState != BasePlayer.STATE_PAUSED) {
            if (currentState != BasePlayer.STATE_PAUSED_SEEK) playbackSeekBar!!.progress = currentProgress
            playbackCurrentTime!!.text = getTimeString(currentProgress)
        }
        if (player!!.isLoading || bufferPercent > 90) {
            playbackSeekBar!!.secondaryProgress = (playbackSeekBar!!.max * (bufferPercent.toFloat() / 100)).toInt()
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "updateProgress() called with: isVisible = $isControlsVisible, currentProgress = [$currentProgress], duration = [$duration], bufferPercent = [$bufferPercent]")
        }
        playbackLiveSync!!.isClickable = !isLiveEdge
    }

    override fun onLoadingComplete(imageUri: String, view: View?, loadedImage: Bitmap?) {
        super.onLoadingComplete(imageUri, view, loadedImage)
        if (loadedImage != null) endScreen!!.setImageBitmap(loadedImage)
    }

    protected open fun onFullScreenButtonClicked() {
        changeState(BasePlayer.STATE_BLOCKED)
    }

    override fun onFastRewind() {
        super.onFastRewind()
        showAndAnimateControl(R.drawable.ic_action_av_fast_rewind, true)
    }

    override fun onFastForward() {
        super.onFastForward()
        showAndAnimateControl(R.drawable.ic_action_av_fast_forward, true)
    }

    ///////////////////////////////////////////////////////////////////////////
    // OnClick related
    ///////////////////////////////////////////////////////////////////////////

    override fun onClick(v: View) {
        if (DEBUG) Log.d(TAG, "onClick() called with: v = [$v]")
        if (v.id == qualityTextView!!.id) {
            onQualitySelectorClicked()
        } else if (v.id == playbackSpeedTextView!!.id) {
            onPlaybackSpeedClicked()
        } else if (v.id == resizeView!!.id) {
            onResizeClicked()
        } else if (v.id == captionTextView!!.id) {
            onCaptionClicked()
        } else if (v.id == playbackLiveSync!!.id) {
            seekToDefault()
        }
    }

    /**
     * Called when an item of the quality selector or the playback speed selector is selected
     */
    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (DEBUG)
            Log.d(TAG, "onMenuItemClick() called with: menuItem = [" + menuItem + "], menuItem.getItemId = [" + menuItem.itemId + "]")

        if (qualityPopupMenuGroupId == menuItem.groupId) {
            val menuItemIndex = menuItem.itemId
            if (selectedStreamIndex == menuItemIndex ||
                    availableStreams == null || availableStreams!!.size <= menuItemIndex)
                return true

            val newResolution = availableStreams!![menuItemIndex].resolution
            setRecovery()
            playbackQuality = newResolution
            reload()

            qualityTextView!!.text = menuItem.title
            return true
        } else if (playbackSpeedPopupMenuGroupId == menuItem.groupId) {
            val speedIndex = menuItem.itemId
            val speed = BasePlayer.PLAYBACK_SPEEDS[speedIndex]

            playbackSpeed = speed
            playbackSpeedTextView!!.text = formatSpeed(speed.toDouble())
        }

        return false
    }

    /**
     * Called when some popup menu is dismissed
     */
    override fun onDismiss(menu: PopupMenu) {
        if (DEBUG) Log.d(TAG, "onDismiss() called with: menu = [$menu]")
        isSomePopupMenuVisible = false
        if (selectedVideoStream != null) {
            qualityTextView!!.text = selectedVideoStream!!.resolution
        }
    }

    fun onQualitySelectorClicked() {
        if (DEBUG) Log.d(TAG, "onQualitySelectorClicked() called")
        qualityPopupMenu!!.show()
        isSomePopupMenuVisible = true
        showControls(DEFAULT_CONTROLS_DURATION.toLong())

        val videoStream = selectedVideoStream
        if (videoStream != null) {
            val qualityText = (MediaFormat.getNameById(videoStream.formatId) + " "
                    + videoStream.resolution)
            qualityTextView!!.text = qualityText
        }

        wasPlaying = player!!.playWhenReady
    }

    open fun onPlaybackSpeedClicked() {
        if (DEBUG) Log.d(TAG, "onPlaybackSpeedClicked() called")
        playbackSpeedPopupMenu!!.show()
        isSomePopupMenuVisible = true
        showControls(DEFAULT_CONTROLS_DURATION.toLong())
    }

    private fun onCaptionClicked() {
        if (DEBUG) Log.d(TAG, "onCaptionClicked() called")
        captionPopupMenu!!.show()
        isSomePopupMenuVisible = true
        showControls(DEFAULT_CONTROLS_DURATION.toLong())
    }

    private fun onResizeClicked() {
        if (aspectRatioFrameLayout != null) {
            val currentResizeMode = aspectRatioFrameLayout!!.resizeMode
            val newResizeMode = nextResizeMode(currentResizeMode)
            setResizeMode(newResizeMode)
        }
    }

    fun setResizeMode(@AspectRatioFrameLayout.ResizeMode resizeMode: Int) {
        aspectRatioFrameLayout!!.resizeMode = resizeMode
        resizeView!!.text = PlayerHelper.resizeTypeOf(context, resizeMode)
    }

    protected abstract fun nextResizeMode(@AspectRatioFrameLayout.ResizeMode resizeMode: Int): Int

    ///////////////////////////////////////////////////////////////////////////
    // SeekBar Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (DEBUG && fromUser) Log.d(TAG, "onProgressChanged() called with: seekBar = [$seekBar], progress = [$progress]")
        //if (fromUser) playbackCurrentTime.setText(getTimeString(progress));
        if (fromUser) currentDisplaySeek!!.text = getTimeString(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (DEBUG) Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [$seekBar]")
        if (currentState != BasePlayer.STATE_PAUSED_SEEK) changeState(BasePlayer.STATE_PAUSED_SEEK)

        wasPlaying = player!!.playWhenReady
        if (isPlaying) player!!.playWhenReady = false

        showControls(0)
        animateView(currentDisplaySeek!!, AnimationUtils.Type.SCALE_AND_ALPHA, true,
                DEFAULT_CONTROLS_DURATION.toLong())
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (DEBUG) Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [$seekBar]")

        seekTo(seekBar.progress.toLong())
        if (wasPlaying || player!!.duration == seekBar.progress.toLong()) player!!.playWhenReady = true

        playbackCurrentTime!!.text = getTimeString(seekBar.progress)
        animateView(currentDisplaySeek!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 200)

        if (currentState == BasePlayer.STATE_PAUSED_SEEK) changeState(BasePlayer.STATE_BUFFERING)
        if (!isProgressLoopRunning) startProgressLoop()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    fun getRendererIndex(trackIndex: Int): Int {
        if (player == null) return RENDERER_UNAVAILABLE

        for (t in 0 until player!!.rendererCount) {
            if (player!!.getRendererType(t) == trackIndex) {
                return t
            }
        }

        return RENDERER_UNAVAILABLE
    }

    /**
     * Show a animation, and depending on goneOnEnd, will stay on the screen or be gone
     *
     * @param drawableId the drawable that will be used to animate, pass -1 to clear any animation that is visible
     * @param goneOnEnd  will set the animation view to GONE on the end of the animation
     */
    fun showAndAnimateControl(drawableId: Int, goneOnEnd: Boolean) {
        if (DEBUG) Log.d(TAG, "showAndAnimateControl() called with: drawableId = [$drawableId], goneOnEnd = [$goneOnEnd]")
        if (controlViewAnimator != null && controlViewAnimator!!.isRunning) {
            if (DEBUG) Log.d(TAG, "showAndAnimateControl: controlViewAnimator.isRunning")
            controlViewAnimator!!.end()
        }

        if (drawableId == -1) {
            if (controlAnimationView!!.visibility == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1f)
                ).setDuration(DEFAULT_CONTROLS_DURATION.toLong())
                controlViewAnimator!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        controlAnimationView!!.visibility = View.GONE
                    }
                })
                controlViewAnimator!!.start()
            }
            return
        }

        val scaleFrom = if (goneOnEnd) 1f else 1f
        val scaleTo = if (goneOnEnd) 1.8f else 1.4f
        val alphaFrom = if (goneOnEnd) 1f else 0f
        val alphaTo = if (goneOnEnd) 0f else 1f


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        )
        controlViewAnimator!!.duration = (if (goneOnEnd) 1000 else 500).toLong()
        controlViewAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (goneOnEnd)
                    controlAnimationView!!.visibility = View.GONE
                else
                    controlAnimationView!!.visibility = View.VISIBLE
            }
        })


        controlAnimationView!!.visibility = View.VISIBLE
        controlAnimationView!!.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
        controlViewAnimator!!.start()
    }

    open fun showControlsThenHide() {
        if (DEBUG) Log.d(TAG, "showControlsThenHide() called")
        animateView(controlsRoot!!, true, DEFAULT_CONTROLS_DURATION.toLong(), 0,
                Runnable {
                    hideControls(DEFAULT_CONTROLS_DURATION.toLong(), DEFAULT_CONTROLS_HIDE_TIME.toLong())
                }
        )
    }

    open fun showControls(duration: Long) {
        if (DEBUG) Log.d(TAG, "showControls() called")
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        animateView(controlsRoot!!, true, duration)
    }

    open fun hideControls(duration: Long, delay: Long) {
        if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [$delay]")
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        controlsVisibilityHandler.postDelayed(
                { animateView(controlsRoot!!, false, duration) }, delay)
    }

    fun hideControlsAndButton(duration: Long, delay: Long, button: View?) {
        if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [$delay]")
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        controlsVisibilityHandler.postDelayed(hideControlsAndButtonHandler(duration, button), delay)
    }

    private fun hideControlsAndButtonHandler(duration: Long, videoPlayPause: View?): Runnable = Runnable {
        videoPlayPause?.visibility = View.INVISIBLE
        animateView(controlsRoot!!, false, duration)
    }

    fun wasPlaying(): Boolean {
        return wasPlaying
    }

    companion object {

        ///////////////////////////////////////////////////////////////////////////
        // Player
        ///////////////////////////////////////////////////////////////////////////

        const val RENDERER_UNAVAILABLE = -1
        const val DEFAULT_CONTROLS_DURATION = 300 // 300 millis
        const val DEFAULT_CONTROLS_HIDE_TIME = 2000  // 2 Seconds
    }
}
