/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * MainVideoPlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.annotation.ColorInt
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.content.res.AppCompatResources
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.text.CaptionStyleCompat
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener
import org.schabi.newpipe.player.BasePlayer.Companion.STATE_PLAYING
import org.schabi.newpipe.player.VideoPlayer.Companion.DEFAULT_CONTROLS_DURATION
import org.schabi.newpipe.player.VideoPlayer.Companion.DEFAULT_CONTROLS_HIDE_TIME
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback
import org.schabi.newpipe.player.resolver.MediaSourceTag
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.*
import org.schabi.newpipe.util.AnimationUtils.Type.SCALE_AND_ALPHA
import org.schabi.newpipe.util.AnimationUtils.Type.SLIDE_AND_ALPHA
import org.schabi.newpipe.util.AnimationUtils.animateRotation
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.StateSaver.KEY_SAVED_STATE
import java.util.*

/**
 * Activity Player implementing VideoPlayer
 *
 */
class MainVideoPlayer : AppCompatActivity(), StateSaver.WriteRead, PlaybackParameterDialog.Callback {

    private var gestureDetector: GestureDetector? = null

    private var playerImpl: VideoPlayerImpl? = null

    private var defaultPreferences: SharedPreferences? = null

    private var playerState: PlayerState? = null
    private var isInMultiWindow: Boolean = false
    private var isBackPressed: Boolean = false

    private var isLandscape: Boolean
        get() = resources.displayMetrics.heightPixels < resources.displayMetrics.widthPixels
        set(value) {
            requestedOrientation = if (value)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

    ///////////////////////////////////////////////////////////////////////////
    // Activity LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")

        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.setTheme(this)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK
        volumeControlStream = AudioManager.STREAM_MUSIC

        val lp = window.attributes
        lp.screenBrightness = PlayerHelper.getScreenBrightness(applicationContext)
        window.attributes = lp

        hideSystemUi()
        setContentView(R.layout.activity_main_player)
        playerImpl = VideoPlayerImpl(this)
        playerImpl!!.setup(findViewById(android.R.id.content))

        if (savedInstanceState != null && savedInstanceState.get(KEY_SAVED_STATE) != null) {
            // this means this activity is restored from the previous instance. no intent data passing from other components.
            return  // We have saved states, stop here to restore it
        }

        val intent = intent
        if (intent != null) {
            playerImpl!!.handleIntent(intent)
        } else {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        Log.d(TAG, "onRestoreInstanceState() called")
        super.onRestoreInstanceState(bundle)
        StateSaver.tryToRestore(bundle, this)
    }

    override fun onNewIntent(intent: Intent?) {
        Log.d(TAG, "onNewIntent() called with: intent = [$intent]")
        super.onNewIntent(intent)
        if (intent != null) {
            playerState = null
            playerImpl!!.handleIntent(intent)
        }
    }

    override fun onResume() {
        Log.d(TAG, "onResume() called")
        super.onResume()

        if (globalScreenOrientationLocked()) {
            val lastOrientationWasLandscape = defaultPreferences!!.getBoolean(getString(R.string.last_orientation_landscape_key), false)
            isLandscape = lastOrientationWasLandscape
        }

        val lastResizeMode = defaultPreferences!!.getInt(getString(R.string.last_resize_mode), AspectRatioFrameLayout.RESIZE_MODE_FIT)
        playerImpl!!.setResizeMode(lastResizeMode)

        // Upon going in or out of multiwindow mode, isInMultiWindow will always be false,
        // since the first onResume needs to restore the simpleExoPlayer.
        // Subsequent onResume calls while multiwindow mode remains the same and the simpleExoPlayer is
        // prepared should be ignored.
        if (isInMultiWindow) return
        isInMultiWindow = isInMultiWindow()

        if (playerState != null) {
            playerImpl!!.playbackQuality = playerState!!.playbackQuality
            playerImpl!!.initPlayback(
                    playerState!!.playQueue,
                    playerState!!.repeatMode,
                    playerState!!.playbackSpeed,
                    playerState!!.playbackPitch,
                    playerState!!.isPlaybackSkipSilence,
                    playerState!!.wasPlaying)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (playerImpl!!.isSomePopupMenuVisible) {
            playerImpl!!.qualityPopupMenu!!.dismiss()
            playerImpl!!.playbackSpeedPopupMenu!!.dismiss()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isBackPressed = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(TAG, "onSaveInstanceState() called")
        super.onSaveInstanceState(outState)
        if (playerImpl == null) return

        playerImpl!!.setRecovery()
        playerState = PlayerState(
                playerImpl!!.playQueue!!,
                playerImpl!!.repeatMode,
                playerImpl!!.playbackSpeed,
                playerImpl!!.playbackPitch,
                playerImpl!!.playbackQuality,
                playerImpl!!.playbackSkipSilence,
                playerImpl!!.isPlaying)

        StateSaver.tryToSave(isChangingConfigurations, null, outState, this)
    }

    override fun onStop() {
        Log.d(TAG, "onStop() called")
        super.onStop()
        PlayerHelper.setScreenBrightness(applicationContext, window.attributes.screenBrightness)

        if (playerImpl == null) return
        if (!isBackPressed) {
            playerImpl!!.minimize()
        }
        playerImpl!!.destroy()

        isInMultiWindow = false
        isBackPressed = false
    }

    ///////////////////////////////////////////////////////////////////////////
    // State Saving
    ///////////////////////////////////////////////////////////////////////////

    override fun generateSuffix(): String {
        return "." + UUID.randomUUID().toString() + ".simpleExoPlayer"
    }

    override fun writeTo(objectsToSave: Queue<Any>) {
//        if (objectsToSave == null) return
        objectsToSave.add(playerState)
    }

    override fun readFrom(savedObjects: Queue<Any>) {
        playerState = savedObjects.poll() as PlayerState
    }

    ///////////////////////////////////////////////////////////////////////////
    // View
    ///////////////////////////////////////////////////////////////////////////

    private fun showSystemUi() {
        Log.d(TAG, "showSystemUi() called")
        if (playerImpl != null && playerImpl!!.queueVisible) return

        val visibility: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        } else {
            View.STATUS_BAR_VISIBLE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @ColorInt val systemUiColor = ActivityCompat.getColor(applicationContext, R.color.video_overlay_color)
            window.statusBarColor = systemUiColor
            window.navigationBarColor = systemUiColor
        }

        window.decorView.systemUiVisibility = visibility
        if (Build.VERSION.SDK_INT < 16) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun hideSystemUi() {
        Log.d(TAG, "hideSystemUi() called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            var visibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility = visibility or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            window.decorView.systemUiVisibility = visibility
        }

        if (Build.VERSION.SDK_INT < 16) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun toggleOrientation() {
        isLandscape = !isLandscape
        defaultPreferences!!.edit()
                .putBoolean(getString(R.string.last_orientation_landscape_key), !isLandscape)
                .apply()
    }

    private fun globalScreenOrientationLocked(): Boolean {
        // 1: Screen orientation changes using acelerometer
        // 0: Screen orientation is locked
        return android.provider.Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 1
    }

    private fun setRepeatModeButton(imageButton: ImageButton, repeatMode: Int) {
        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> imageButton.setImageResource(R.drawable.exo_controls_repeat_off)
            Player.REPEAT_MODE_ONE -> imageButton.setImageResource(R.drawable.exo_controls_repeat_one)
            Player.REPEAT_MODE_ALL -> imageButton.setImageResource(R.drawable.exo_controls_repeat_all)
        }
    }

    private fun setShuffleButton(shuffleButton: ImageButton, shuffled: Boolean) {
        val shuffleAlpha = if (shuffled) 255 else 77
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            shuffleButton.imageAlpha = shuffleAlpha
        } else {
            shuffleButton.imageAlpha = shuffleAlpha
        }
    }

    private fun isInMultiWindow(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode
    }

    ////////////////////////////////////////////////////////////////////////////
    // Playback Parameters Listener
    ////////////////////////////////////////////////////////////////////////////

    override fun onPlaybackParameterChanged(playbackTempo: Float,
                                            playbackPitch: Float,
                                            playbackSkipSilence: Boolean) {
        playerImpl?.setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence)
    }

    ///////////////////////////////////////////////////////////////////////////

    private inner class VideoPlayerImpl internal constructor(context: Context) : VideoPlayer("VideoPlayerImpl" + MainVideoPlayer.TAG, context) {

        ///////////////////////////////////////////////////////////////////////////
        // Getters
        ///////////////////////////////////////////////////////////////////////////

        var titleTextView: TextView? = null
            private set
        var channelTextView: TextView? = null
            private set
        var volumeRelativeLayout: RelativeLayout? = null
            private set
        var volumeProgressBar: ProgressBar? = null
            private set
        var volumeImageView: ImageView? = null
            private set
        var brightnessRelativeLayout: RelativeLayout? = null
            private set
        var brightnessProgressBar: ProgressBar? = null
            private set
        var brightnessImageView: ImageView? = null
            private set
        private var queueButton: ImageButton? = null

        var repeatButton: ImageButton? = null
            private set
        private var shuffleButton: ImageButton? = null

        var playPauseButton: ImageButton? = null
            private set
        private var playPreviousButton: ImageButton? = null
        private var playNextButton: ImageButton? = null

        private var queueLayout: RelativeLayout? = null
        private var itemsListCloseButton: ImageButton? = null
        private var itemsList: RecyclerView? = null
        private var itemTouchHelper: ItemTouchHelper? = null

        var queueVisible: Boolean = false

        private var moreOptionsButton: ImageButton? = null
        private var toggleOrientationButton: ImageButton? = null
        private var switchPopupButton: ImageButton? = null
        private var switchBackgroundButton: ImageButton? = null

        private val windowRootLayout: RelativeLayout? = null
        private var secondaryControls: View? = null

        var maxGestureLength: Int = 0
            private set

        private val queueScrollListener: OnScrollBelowItemsListener
            get() = object : OnScrollBelowItemsListener() {
                override fun onScrolledDown(recyclerView: RecyclerView) {
                    if (playQueue != null && !playQueue!!.isComplete) {
                        playQueue!!.fetch()
                    } else if (itemsList != null) {
                        itemsList!!.clearOnScrollListeners()
                    }
                }
            }

        private val itemTouchCallback: ItemTouchHelper.SimpleCallback
            get() = object : PlayQueueItemTouchCallback() {
                override fun onMove(sourceIndex: Int, targetIndex: Int) {
                    if (playQueue != null) playQueue!!.move(sourceIndex, targetIndex)
                }
            }

        private val onSelectedListener: PlayQueueItemBuilder.OnSelectedListener
            get() = object : PlayQueueItemBuilder.OnSelectedListener {
                override fun selected(item: PlayQueueItem, view: View) {
                    onSelected(item)
                }

                override fun held(item: PlayQueueItem, view: View) {
                    val index = playQueue!!.indexOf(item)
                    if (index != -1) playQueue!!.remove(index)
                }

                override fun onStartDrag(viewHolder: PlayQueueItemHolder) {
                    if (itemTouchHelper != null) itemTouchHelper!!.startDrag(viewHolder)
                }
            }

        override fun initViews(rootView: View) {
            super.initViews(rootView)
            this.titleTextView = rootView.findViewById(R.id.titleTextView)
            this.channelTextView = rootView.findViewById(R.id.channelTextView)
            this.volumeRelativeLayout = rootView.findViewById(R.id.volumeRelativeLayout)
            this.volumeProgressBar = rootView.findViewById(R.id.volumeProgressBar)
            this.volumeImageView = rootView.findViewById(R.id.volumeImageView)
            this.brightnessRelativeLayout = rootView.findViewById(R.id.brightnessRelativeLayout)
            this.brightnessProgressBar = rootView.findViewById(R.id.brightnessProgressBar)
            this.brightnessImageView = rootView.findViewById(R.id.brightnessImageView)
            this.queueButton = rootView.findViewById(R.id.queueButton)
            this.repeatButton = rootView.findViewById(R.id.repeatButton)
            this.shuffleButton = rootView.findViewById(R.id.shuffleButton)

            this.playPauseButton = rootView.findViewById(R.id.playPauseButton)
            this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton)
            this.playNextButton = rootView.findViewById(R.id.playNextButton)

            this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton)
            this.secondaryControls = rootView.findViewById(R.id.secondaryControls)
            this.toggleOrientationButton = rootView.findViewById(R.id.toggleOrientation)
            this.switchBackgroundButton = rootView.findViewById(R.id.switchBackground)
            this.switchPopupButton = rootView.findViewById(R.id.switchPopup)

            this.queueLayout = findViewById(R.id.playQueuePanel)
            this.itemsListCloseButton = findViewById(R.id.playQueueClose)
            this.itemsList = findViewById(R.id.playQueue)

            titleTextView?.isSelected = true
            channelTextView?.isSelected = true

            rootView.keepScreenOn = true
        }

        override fun setupSubtitleView(view: SubtitleView,
                                       captionScale: Float,
                                       captionStyle: CaptionStyleCompat) {
            val metrics = context.resources.displayMetrics
            val minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels)
            val captionRatioInverse = 20f + 4f * (1f - captionScale)
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,
                    minimumLength.toFloat() / captionRatioInverse)
            view.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT)
            view.setStyle(captionStyle)
        }

        override fun initListeners() {
            super.initListeners()

            val listener = PlayerGestureListener()
            gestureDetector = GestureDetector(context, listener)
            gestureDetector!!.setIsLongpressEnabled(false)
            rootView!!.setOnTouchListener(listener)

            queueButton!!.setOnClickListener(this)
            repeatButton!!.setOnClickListener(this)
            shuffleButton!!.setOnClickListener(this)

            playPauseButton!!.setOnClickListener(this)
            playPreviousButton!!.setOnClickListener(this)
            playNextButton!!.setOnClickListener(this)

            moreOptionsButton!!.setOnClickListener(this)
            toggleOrientationButton!!.setOnClickListener(this)
            switchBackgroundButton!!.setOnClickListener(this)
            switchPopupButton!!.setOnClickListener(this)

            rootView!!.addOnLayoutChangeListener { view, l, t, r, b, ol, ot, or, ob ->
                if (l != ol || t != ot || r != or || b != ob) {
                    // Use smaller value to be consistent between screen orientations (and to make usage easier)
                    val width = r - l
                    val height = b - t
                    maxGestureLength = (Math.min(width, height) * MAX_GESTURE_LENGTH).toInt()

                    Log.d(TAG, "maxGestureLength = $maxGestureLength")

                    volumeProgressBar!!.max = maxGestureLength
                    brightnessProgressBar!!.max = maxGestureLength

                    setInitialGestureValues()
                }
            }
        }

        fun minimize() {
            when (PlayerHelper.getMinimizeOnExitAction(context)) {
                PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND -> onPlayBackgroundButtonClicked()
                PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP -> onFullScreenButtonClicked()
                PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE -> { /* no-op */
                }
                else -> { /* no-op */
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onRepeatModeChanged(i: Int) {
            super.onRepeatModeChanged(i)
            updatePlaybackButtons()
        }

        override fun onShuffleClicked() {
            super.onShuffleClicked()
            updatePlaybackButtons()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Playback Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onMetadataChanged(tag: MediaSourceTag) {
            super.onMetadataChanged(tag)

            titleTextView!!.text = tag.metadata.name
            channelTextView!!.text = tag.metadata.uploaderName
        }

        override fun onPlaybackShutdown() {
            super.onPlaybackShutdown()
            finish()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Player Overrides
        ///////////////////////////////////////////////////////////////////////////

        public override fun onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked()

            Log.d(TAG, "onFullScreenButtonClicked() called")
            if (simpleExoPlayer == null) return

            if (!PermissionHelper.isPopupEnabled(context)) {
                PermissionHelper.showPopupEnablementToast(context)
                return
            }

            setRecovery()
            val intent = NavigationHelper.getPlayerIntent(
                    context,
                    PopupVideoPlayer::class.java,
                    this.playQueue!!,
                    this.repeatMode,
                    this.playbackSpeed,
                    this.playbackPitch,
                    this.playbackSkipSilence,
                    this.playbackQuality
            )
            context.startService(intent)

            (controlAnimationView!!.parent as View).visibility = View.GONE
            destroy()
            finish()
        }

        fun onPlayBackgroundButtonClicked() {
            Log.d(TAG, "onPlayBackgroundButtonClicked() called")
            if (playerImpl!!.simpleExoPlayer == null) return

            setRecovery()
            val intent = NavigationHelper.getPlayerIntent(
                    context,
                    BackgroundPlayer::class.java,
                    this.playQueue!!,
                    this.repeatMode,
                    this.playbackSpeed,
                    this.playbackPitch,
                    this.playbackSkipSilence,
                    this.playbackQuality
            )
            context.startService(intent)

            (controlAnimationView!!.parent as View).visibility = View.GONE
            destroy()
            finish()
        }


        override fun onClick(view: View) {
            super.onClick(view)
            when (view.id) {
                playPauseButton!!.id -> onPlayPause()
                playPreviousButton!!.id -> onPlayPrevious()
                playNextButton!!.id -> onPlayNext()
                queueButton!!.id -> {
                    onQueueClicked()
                    return
                }
                repeatButton!!.id -> {
                    onRepeatClicked()
                    return
                }
                shuffleButton!!.id -> {
                    onShuffleClicked()
                    return
                }
                moreOptionsButton!!.id -> onMoreOptionsClicked()
                toggleOrientationButton!!.id -> onScreenRotationClicked()
                switchPopupButton!!.id -> onFullScreenButtonClicked()
                switchBackgroundButton!!.id -> onPlayBackgroundButtonClicked()
            }

            if (currentState != BasePlayer.STATE_COMPLETED) {
                controlsVisibilityHandler.removeCallbacksAndMessages(null)
                animateView(controlsRoot!!,
                        true,
                        DEFAULT_CONTROLS_DURATION.toLong(),
                        0,
                        Runnable {
                            if (currentState == STATE_PLAYING && !isSomePopupMenuVisible) {
                                hideControls(DEFAULT_CONTROLS_DURATION.toLong(), DEFAULT_CONTROLS_HIDE_TIME.toLong())
                            }
                        })
            }
        }

        private fun onQueueClicked() {
            queueVisible = true
            hideSystemUi()

            buildQueue()
            updatePlaybackButtons()

            controlsRoot!!.visibility = View.INVISIBLE
            animateView(queueLayout!!, SLIDE_AND_ALPHA, /*visible=*/true,
                    DEFAULT_CONTROLS_DURATION.toLong())

            itemsList!!.scrollToPosition(playQueue!!.index)
        }

        private fun onQueueClosed() {
            animateView(queueLayout!!, SLIDE_AND_ALPHA, /*visible=*/false,
                    DEFAULT_CONTROLS_DURATION.toLong())
            queueVisible = false
        }

        private fun onMoreOptionsClicked() {
            Log.d(TAG, "onMoreOptionsClicked() called")

            val isMoreControlsVisible = secondaryControls!!.visibility == View.VISIBLE

            animateRotation(moreOptionsButton!!, DEFAULT_CONTROLS_DURATION.toLong(),
                    if (isMoreControlsVisible) 0 else 180)
            animateView(secondaryControls!!, SLIDE_AND_ALPHA, !isMoreControlsVisible,
                    DEFAULT_CONTROLS_DURATION.toLong())
            showControls(DEFAULT_CONTROLS_DURATION.toLong())
        }

        private fun onScreenRotationClicked() {
            Log.d(TAG, "onScreenRotationClicked() called")
            toggleOrientation()
            showControlsThenHide()
        }

        override fun onPlaybackSpeedClicked() {
            PlaybackParameterDialog
                    .newInstance(playbackSpeed.toDouble(), playbackPitch.toDouble(), playbackSkipSilence)
                    .show(supportFragmentManager, TAG)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            super.onStopTrackingTouch(seekBar)
            if (wasPlaying()) showControlsThenHide()
        }

        override fun onDismiss(menu: PopupMenu) {
            super.onDismiss(menu)
            if (isPlaying) hideControls(DEFAULT_CONTROLS_DURATION.toLong(), 0)
            hideSystemUi()
        }

        override fun nextResizeMode(currentResizeMode: Int): Int {
            val newResizeMode: Int = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }

            storeResizeMode(newResizeMode)
            return newResizeMode
        }

        private fun storeResizeMode(@AspectRatioFrameLayout.ResizeMode resizeMode: Int) {
            defaultPreferences!!.edit()
                    .putInt(getString(R.string.last_resize_mode), resizeMode)
                    .apply()
        }

        override val qualityResolver: VideoPlaybackResolver.QualityResolver
            get() = object : VideoPlaybackResolver.QualityResolver {
                override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>): Int =
                        ListHelper.getDefaultResolutionIndex(context, sortedVideos)

                override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>,
                                                        playbackQuality: String?): Int =
                        ListHelper.getResolutionIndex(context, sortedVideos, playbackQuality!!)
            }

        ///////////////////////////////////////////////////////////////////////////
        // States
        ///////////////////////////////////////////////////////////////////////////

        private fun animatePlayButtons(show: Boolean, duration: Int) {
            animateView(playPauseButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration.toLong())
            animateView(playPreviousButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration.toLong())
            animateView(playNextButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration.toLong())
        }

        override fun onBlocked() {
            super.onBlocked()
            playPauseButton!!.setImageResource(R.drawable.ic_pause_white)
            animatePlayButtons(false, 100)
            rootView!!.keepScreenOn = true
        }

        override fun onBuffering() {
            super.onBuffering()
            rootView!!.keepScreenOn = true
        }

        override fun onPlaying() {
            super.onPlaying()
            animateView(playPauseButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0,
                    Runnable {
                        playPauseButton!!.setImageResource(R.drawable.ic_pause_white)
                        animatePlayButtons(true, 200)
                    }
            )

            rootView!!.keepScreenOn = true
        }

        override fun onPaused() {
            super.onPaused()
            animateView(playPauseButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0,
                    Runnable {
                        playPauseButton!!.setImageResource(R.drawable.ic_play_arrow_white)
                        animatePlayButtons(true, 200)
                    }
            )

            showSystemUi()
            rootView!!.keepScreenOn = false
        }

        override fun onPausedSeek() {
            super.onPausedSeek()
            animatePlayButtons(false, 100)
            rootView!!.keepScreenOn = true
        }

        override fun onCompleted() {
            animateView(playPauseButton!!, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0,
                    Runnable {
                        playPauseButton!!.setImageResource(R.drawable.ic_replay_white)
                        animatePlayButtons(true, DEFAULT_CONTROLS_DURATION)
                    })

            rootView!!.keepScreenOn = false
            super.onCompleted()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Utils
        ///////////////////////////////////////////////////////////////////////////

        private fun setInitialGestureValues() {
            if (audioReactor != null) {
                val currentVolumeNormalized = audioReactor!!.volume.toFloat() / audioReactor!!.maxVolume
                volumeProgressBar!!.progress = (volumeProgressBar!!.max * currentVolumeNormalized).toInt()
            }
        }

        override fun showControlsThenHide() {
            if (queueVisible) return

            super.showControlsThenHide()
        }

        override fun showControls(duration: Long) {
            if (queueVisible) return

            super.showControls(duration)
        }

        override fun hideControls(duration: Long, delay: Long) {
            Log.d(TAG, "hideControls() called with: delay = [$delay]")
            controlsVisibilityHandler.removeCallbacksAndMessages(null)
            controlsVisibilityHandler.postDelayed({
                animateView(controlsRoot!!, false, duration, 0,
                        Runnable { this@MainVideoPlayer.hideSystemUi() }
                )
            },
                    /*delayMillis=*/delay
            )
        }

        private fun updatePlaybackButtons() {
            if (repeatButton == null || shuffleButton == null ||
                    simpleExoPlayer == null || playQueue == null)
                return

            setRepeatModeButton(repeatButton!!, repeatMode)
            setShuffleButton(shuffleButton!!, playQueue!!.isShuffled)
        }

        private fun buildQueue() {
            itemsList!!.adapter = playQueueAdapter
            itemsList!!.isClickable = true
            itemsList!!.isLongClickable = true

            itemsList!!.clearOnScrollListeners()
            itemsList!!.addOnScrollListener(queueScrollListener)

            itemTouchHelper = ItemTouchHelper(itemTouchCallback)
            itemTouchHelper!!.attachToRecyclerView(itemsList)

            playQueueAdapter!!.setSelectedListener(onSelectedListener)

            itemsListCloseButton!!.setOnClickListener { view -> onQueueClosed() }
        }
    }

    private inner class PlayerGestureListener : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
        private var isMoving: Boolean = false

        private val isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(applicationContext)
        private val isBrightnessGestureEnabled = PlayerHelper.isBrightnessGestureEnabled(applicationContext)

        private val maxVolume = playerImpl!!.audioReactor!!.maxVolume

        override fun onDoubleTap(event: MotionEvent): Boolean {
            Log.d(TAG, "onDoubleTap() called with: event = [$event]rawXy = ${event.rawX}, ${event.rawY}, xy = ${event.x}, ${event.y}")

            when {
                event.x > playerImpl!!.rootView!!.width * 2 / 3 -> playerImpl!!.onFastForward()
                event.x < playerImpl!!.rootView!!.width / 3 -> playerImpl!!.onFastRewind()
                else -> playerImpl!!.playPauseButton!!.performClick()
            }

            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {

            if (e1 != null && e2 != null)
                when {
                    ((e2.x - e1.x) > 0 && Math.abs(e1.x - e2.x) > Math.abs(e1.y - e2.y)) -> playerImpl!!.onFastForward()
                    ((e2.x - e1.x) < 0 && Math.abs(e1.x - e2.x) > Math.abs(e1.y - e2.y)) -> playerImpl!!.onFastRewind()
                    ((e2.y - e1.y) > 0 && Math.abs(e1.y - e2.y) > Math.abs(e1.x - e2.x)) -> "up to down fling"
                    ((e2.y - e1.y) < 0 && Math.abs(e1.y - e2.y) > Math.abs(e1.x - e2.x)) -> "down to up fling"
                }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
            if (playerImpl!!.currentState == BasePlayer.STATE_BLOCKED) return true

            if (playerImpl!!.isControlsVisible) {
                playerImpl!!.hideControls(150, 0)
            } else {
                playerImpl!!.showControlsThenHide()
                showSystemUi()
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            Log.d(TAG, "onDown() called with: e = [$e]")

            return super.onDown(e)
        }

        override fun onScroll(initialEvent: MotionEvent, movingEvent: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!isVolumeGestureEnabled && !isBrightnessGestureEnabled) return false

            Log.d(TAG, "MainVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + initialEvent.rawX + ", " + initialEvent.rawY + "]" +
                    ", e2.getRaw = [" + movingEvent.rawX + ", " + movingEvent.rawY + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]")

            val insideThreshold = Math.abs(movingEvent.y - initialEvent.y) <= MOVEMENT_THRESHOLD
            if (!isMoving && (insideThreshold || Math.abs(distanceX) > Math.abs(distanceY)) || playerImpl!!.currentState == BasePlayer.STATE_COMPLETED) {
                return false
            }

            isMoving = true

            val acceptAnyArea = isVolumeGestureEnabled != isBrightnessGestureEnabled
            val acceptVolumeArea = acceptAnyArea || initialEvent.x > playerImpl!!.rootView!!.width / 2
            val acceptBrightnessArea = acceptAnyArea || !acceptVolumeArea

            if (isVolumeGestureEnabled && acceptVolumeArea) {
                playerImpl!!.volumeProgressBar!!.incrementProgressBy(distanceY.toInt())
                val currentProgressPercent = playerImpl!!.volumeProgressBar!!.progress.toFloat() / playerImpl!!.maxGestureLength
                val currentVolume = (maxVolume * currentProgressPercent).toInt()
                playerImpl!!.audioReactor!!.volume = currentVolume

                Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")

                val resId = when {
                    currentProgressPercent <= 0 -> R.drawable.ic_volume_off_white_72dp
                    currentProgressPercent < 0.25 -> R.drawable.ic_volume_mute_white_72dp
                    currentProgressPercent < 0.75 -> R.drawable.ic_volume_down_white_72dp
                    else -> R.drawable.ic_volume_up_white_72dp
                }

                playerImpl!!.volumeImageView!!.setImageDrawable(
                        AppCompatResources.getDrawable(applicationContext, resId)
                )

                if (playerImpl!!.volumeRelativeLayout!!.visibility != View.VISIBLE) {
                    animateView(playerImpl!!.volumeRelativeLayout!!, SCALE_AND_ALPHA, true, 200)
                }
                if (playerImpl!!.brightnessRelativeLayout!!.visibility == View.VISIBLE) {
                    playerImpl!!.brightnessRelativeLayout!!.visibility = View.GONE
                }
            } else if (isBrightnessGestureEnabled && acceptBrightnessArea) {
                playerImpl!!.brightnessProgressBar!!.incrementProgressBy(distanceY.toInt())
                val currentProgressPercent = playerImpl!!.brightnessProgressBar!!.progress.toFloat() / playerImpl!!.maxGestureLength
                val layoutParams = window.attributes
                layoutParams.screenBrightness = currentProgressPercent
                window.attributes = layoutParams

                Log.d(TAG, "onScroll().brightnessControl, currentBrightness = $currentProgressPercent")

                val resId = when {
                    currentProgressPercent < 0.25 -> R.drawable.ic_brightness_low_white_72dp
                    currentProgressPercent < 0.75 -> R.drawable.ic_brightness_medium_white_72dp
                    else -> R.drawable.ic_brightness_high_white_72dp
                }

                playerImpl!!.brightnessImageView!!.setImageDrawable(
                        AppCompatResources.getDrawable(applicationContext, resId)
                )

                if (playerImpl!!.brightnessRelativeLayout!!.visibility != View.VISIBLE) {
                    animateView(playerImpl!!.brightnessRelativeLayout!!, SCALE_AND_ALPHA, true, 200)
                }
                if (playerImpl!!.volumeRelativeLayout!!.visibility == View.VISIBLE) {
                    playerImpl!!.volumeRelativeLayout!!.visibility = View.GONE
                }
            }
            return true
        }

        private fun onScrollEnd() {
            Log.d(TAG, "onScrollEnd() called")

            if (playerImpl!!.volumeRelativeLayout!!.visibility == View.VISIBLE) {
                animateView(playerImpl!!.volumeRelativeLayout!!, SCALE_AND_ALPHA, false, 200, 200)
            }
            if (playerImpl!!.brightnessRelativeLayout!!.visibility == View.VISIBLE) {
                animateView(playerImpl!!.brightnessRelativeLayout!!, SCALE_AND_ALPHA, false, 200, 200)
            }

            if (playerImpl!!.isControlsVisible && playerImpl!!.currentState == STATE_PLAYING) {
                playerImpl!!.hideControls(DEFAULT_CONTROLS_DURATION.toLong(), DEFAULT_CONTROLS_HIDE_TIME.toLong())
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {

            Log.d(TAG, "onTouch() called with: v = [$v], event = [$event]")
            gestureDetector!!.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isMoving) {
                isMoving = false
                onScrollEnd()
            }
            return true
        }

    }

    companion object {
        const val MOVEMENT_THRESHOLD = 40
        private const val TAG = ".MainVideoPlayer"
    }
}
