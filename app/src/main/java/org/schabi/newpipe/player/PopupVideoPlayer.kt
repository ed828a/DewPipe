
package org.schabi.newpipe.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.NotificationCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AnticipateInterpolator
import android.widget.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.text.CaptionStyleCompat
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.nostra13.universalimageloader.core.assist.FailReason
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.BasePlayer.Companion.STATE_PLAYING
import org.schabi.newpipe.player.VideoPlayer.Companion.DEFAULT_CONTROLS_DURATION
import org.schabi.newpipe.player.VideoPlayer.Companion.DEFAULT_CONTROLS_HIDE_TIME
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.helper.LockManager
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.model.PlayerServiceBinder
import org.schabi.newpipe.player.resolver.MediaSourceTag
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * Service Popup Player implementing VideoPlayer
 *
 */
class PopupVideoPlayer : Service() {

    private var windowManager: WindowManager? = null
    private var popupLayoutParams: WindowManager.LayoutParams? = null
    private var popupGestureDetector: GestureDetector? = null

    private var closeOverlayView: View? = null
    private var closeOverlayButton: FloatingActionButton? = null

    private var tossFlingVelocity: Int = 0

    private var screenWidth: Float = 0.toFloat()
    private var screenHeight: Float = 0.toFloat()
    private var popupWidth: Float = 0.toFloat()
    private var popupHeight: Float = 0.toFloat()

    private var minimumWidth: Float = 0.toFloat()
    private var minimumHeight: Float = 0.toFloat()
    private var maximumWidth: Float = 0.toFloat()
    private var maximumHeight: Float = 0.toFloat()

    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationRemoteView: RemoteViews? = null

    private var playerImpl: VideoPlayerImpl? = null
    private var lockManager: LockManager? = null
    private var isPopupClosing = false

    ///////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    ///////////////////////////////////////////////////////////////////////////

    private var playerEventListener: PlayerEventListener? = null
    private var mBinder: IBinder? = null

    ///////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        lockManager = LockManager(this)
        playerImpl = VideoPlayerImpl(this)
        ThemeHelper.setTheme(this)

        mBinder = PlayerServiceBinder(playerImpl!!)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() called with: intent = [$intent], flags = [$flags], startId = [$startId]")

        if (playerImpl!!.simpleExoPlayer == null) {
            initPopup()
            initPopupCloseOverlay()
        }
        if (!playerImpl!!.isPlaying) playerImpl!!.simpleExoPlayer!!.playWhenReady = true

        playerImpl!!.handleIntent(intent)

        return Service.START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged() called with: newConfig = [$newConfig]")
        updateScreenSize()
        updatePopupSize(popupLayoutParams!!.width, -1)
        checkPopupPositionBounds()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        closePopup()
    }

    override fun onBind(intent: Intent): IBinder? = mBinder


    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    @SuppressLint("RtlHardcoded")
    private fun initPopup() {
        Log.d(TAG, "initPopup() called")
        val rootView = View.inflate(this, R.layout.player_popup, null)
        playerImpl!!.setup(rootView)

        tossFlingVelocity = PlayerHelper.getTossFlingVelocity(this)

        updateScreenSize()

        val popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(this)
        val defaultSize = resources.getDimension(R.dimen.popup_default_width)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        popupWidth = if (popupRememberSizeAndPos) sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) else defaultSize

        val layoutParamType = if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_PHONE
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        popupLayoutParams = WindowManager.LayoutParams(
                popupWidth.toInt(), getMinimumVideoHeight(popupWidth).toInt(),
                layoutParamType,
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT)
        popupLayoutParams!!.gravity = Gravity.LEFT or Gravity.TOP
        popupLayoutParams!!.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        val centerX = (screenWidth / 2f - popupWidth / 2f).toInt()
        val centerY = (screenHeight / 2f - popupHeight / 2f).toInt()
        popupLayoutParams!!.x = if (popupRememberSizeAndPos) sharedPreferences.getInt(POPUP_SAVED_X, centerX) else centerX
        popupLayoutParams!!.y = if (popupRememberSizeAndPos) sharedPreferences.getInt(POPUP_SAVED_Y, centerY) else centerY

        checkPopupPositionBounds()

        val listener = PopupWindowGestureListener()
        popupGestureDetector = GestureDetector(this, listener)
        rootView.setOnTouchListener(listener)

        playerImpl!!.loadingPanel!!.minimumWidth = popupLayoutParams!!.width
        playerImpl!!.loadingPanel!!.minimumHeight = popupLayoutParams!!.height
        windowManager!!.addView(rootView, popupLayoutParams)
    }

    @SuppressLint("RtlHardcoded", "RestrictedApi")
    private fun initPopupCloseOverlay() {
        Log.d(TAG, "initPopupCloseOverlay() called")
        closeOverlayView = View.inflate(this, R.layout.player_popup_close_overlay, null)
        closeOverlayButton = closeOverlayView!!.findViewById(R.id.closeButton)

        val layoutParamType = if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_PHONE
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        val closeOverlayLayoutParams = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParamType,
                flags,
                PixelFormat.TRANSLUCENT)
        closeOverlayLayoutParams.gravity = Gravity.LEFT or Gravity.TOP
        closeOverlayLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        closeOverlayButton?.visibility = View.GONE
        windowManager!!.addView(closeOverlayView, closeOverlayLayoutParams)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Notification
    ///////////////////////////////////////////////////////////////////////////

    private fun resetNotification() {
        notificationBuilder = createNotification()
    }

    private fun createNotification(): NotificationCompat.Builder {
        notificationRemoteView = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_popup_notification)

        notificationRemoteView!!.setTextViewText(R.id.notificationSongName, playerImpl!!.videoTitle)
        notificationRemoteView!!.setTextViewText(R.id.notificationArtist, playerImpl!!.uploaderName)
        notificationRemoteView!!.setImageViewBitmap(R.id.notificationCover, playerImpl!!.thumbnail)

        notificationRemoteView!!.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT))
        notificationRemoteView!!.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT))
        notificationRemoteView!!.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT))

        // Starts popup simpleExoPlayer activity -- attempts to unlock lockscreen
        val intent = NavigationHelper.getPopupPlayerActivityIntent(this)
        notificationRemoteView!!.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT))

        setRepeatModeRemote(notificationRemoteView, playerImpl!!.repeatMode)

        val builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContent(notificationRemoteView)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            builder.priority = NotificationCompat.PRIORITY_MAX
        }
        return builder
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private fun updateNotification(drawableId: Int) {
        Log.d(TAG, "updateNotification() called with: drawableId = [$drawableId]")
        if (notificationBuilder == null || notificationRemoteView == null) return
        if (drawableId != -1) notificationRemoteView!!.setImageViewResource(R.id.notificationPlayPause, drawableId)
        notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////

    fun closePopup() {
        Log.d(TAG, "closePopup() called, isPopupClosing = $isPopupClosing")
        if (isPopupClosing) return
        isPopupClosing = true

        if (playerImpl != null) {
            if (playerImpl!!.rootView != null) {
                windowManager!!.removeView(playerImpl!!.rootView)
            }
            playerImpl!!.rootView = null
            playerImpl!!.stopActivityBinding()
            playerImpl!!.destroy()
            playerImpl = null
        }

        mBinder = null
        if (lockManager != null) lockManager!!.releaseWifiAndCpu()
        if (notificationManager != null) notificationManager!!.cancel(NOTIFICATION_ID)

        animateOverlayAndFinishService()
    }

    private fun animateOverlayAndFinishService() {
        val targetTranslationY = (closeOverlayButton!!.rootView.height - closeOverlayButton!!.y).toInt()

        closeOverlayButton!!.animate().setListener(null).cancel()
        closeOverlayButton!!.animate()
                .setInterpolator(AnticipateInterpolator())
                .translationY(targetTranslationY.toFloat())
                .setDuration(400)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        end()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        end()
                    }

                    private fun end() {
                        windowManager!!.removeView(closeOverlayView)

                        stopForeground(true)
                        stopSelf()
                    }
                }).start()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    /**
     * @see .checkPopupPositionBounds
     */
    private fun checkPopupPositionBounds(): Boolean {
        return checkPopupPositionBounds(screenWidth, screenHeight)
    }

    /**
     * Check if [.popupLayoutParams]' position is within a arbitrary boundary that goes getTabFrom (0,0) to (boundaryWidth,boundaryHeight).
     *
     *
     * If it's out of these boundaries, [.popupLayoutParams]' position is changed and `true` is returned
     * to represent this change.
     *
     * @return if the popup was out of bounds and have been moved back to it
     */
    private fun checkPopupPositionBounds(boundaryWidth: Float, boundaryHeight: Float): Boolean {
        Log.d(TAG, "checkPopupPositionBounds() called with: boundaryWidth = [$boundaryWidth], boundaryHeight = [$boundaryHeight]")


        when {
            popupLayoutParams!!.x < 0 -> {
                popupLayoutParams!!.x = 0
                return true
            }
            popupLayoutParams!!.x > boundaryWidth - popupLayoutParams!!.width -> {
                popupLayoutParams!!.x = (boundaryWidth - popupLayoutParams!!.width).toInt()
                return true
            }
            popupLayoutParams!!.y < 0 -> {
                popupLayoutParams!!.y = 0
                return true
            }
            popupLayoutParams!!.y > boundaryHeight - popupLayoutParams!!.height -> {
                popupLayoutParams!!.y = (boundaryHeight - popupLayoutParams!!.height).toInt()
                return true
            }
            else -> return false
        }

    }

    private fun savePositionAndSize() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@PopupVideoPlayer)
        sharedPreferences.edit().putInt(POPUP_SAVED_X, popupLayoutParams!!.x).apply()
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, popupLayoutParams!!.y).apply()
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, popupLayoutParams!!.width.toFloat()).apply()
    }

    private fun getMinimumVideoHeight(width: Float): Float {
        Log.d(TAG, "getMinimumVideoHeight() called with: width = [$width]")
        return width / (16.0f / 9.0f) // Respect the 16:9 ratio that most videos have
    }

    private fun updateScreenSize() {
        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getMetrics(metrics)

        screenWidth = metrics.widthPixels.toFloat()
        screenHeight = metrics.heightPixels.toFloat()

        Log.d(TAG, "updateScreenSize() called > screenWidth = $screenWidth, screenHeight = $screenHeight")

        popupWidth = resources.getDimension(R.dimen.popup_default_width)
        popupHeight = getMinimumVideoHeight(popupWidth)

        minimumWidth = resources.getDimension(R.dimen.popup_minimum_width)
        minimumHeight = getMinimumVideoHeight(minimumWidth)

        maximumWidth = screenWidth
        maximumHeight = screenHeight
    }

    private fun updatePopupSize(width: Int, height: Int) {
        var width = width
        var height = height
        if (playerImpl == null) return

        Log.d(TAG, "updatePopupSize() called with: width = [$width], height = [$height]")

        width = (when {
            width > maximumWidth -> maximumWidth.toInt()
            width < minimumWidth -> minimumWidth.toInt()
            else -> width
        })

        height = if (height == -1) getMinimumVideoHeight(width.toFloat()).toInt()
        else when {
            height > maximumHeight -> maximumHeight.toInt()
            height < minimumHeight -> minimumHeight.toInt()
            else -> height
        }

        popupLayoutParams!!.width = width
        popupLayoutParams!!.height = height
        popupWidth = width.toFloat()
        popupHeight = height.toFloat()

        Log.d(TAG, "updatePopupSize() updated values:  width = [$width], height = [$height]")
        windowManager!!.updateViewLayout(playerImpl!!.rootView, popupLayoutParams)
    }

    private fun setRepeatModeRemote(remoteViews: RemoteViews?, repeatMode: Int) {
        val methodName = "setImageResource"

        if (remoteViews == null) return

        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_off)
            Player.REPEAT_MODE_ONE -> remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_one)
            Player.REPEAT_MODE_ALL -> remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_all)
        }
    }

    private fun updateWindowFlags(flags: Int) {
        if (popupLayoutParams == null || windowManager == null || playerImpl == null) return

        popupLayoutParams!!.flags = flags
        windowManager!!.updateViewLayout(playerImpl!!.rootView, popupLayoutParams)
    }
    ///////////////////////////////////////////////////////////////////////////

    inner class VideoPlayerImpl internal constructor(context: Context) : VideoPlayer("${PopupVideoPlayer.TAG}.VideoPlayerImpl", context), View.OnLayoutChangeListener {


        var resizingIndicator: TextView? = null
            private set
        private var fullScreenButton: ImageButton? = null
        private var videoPlayPause: ImageView? = null

        private var extraOptionsView: View? = null
        var closingOverlayView: View? = null
            private set

        override fun handleIntent(intent: Intent?) {
            super.handleIntent(intent)

            resetNotification()
            startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
        }

        override fun initViews(rootView: View) {
            super.initViews(rootView)
            resizingIndicator = rootView.findViewById(R.id.resizing_indicator)
            fullScreenButton = rootView.findViewById(R.id.fullScreenButton)
            fullScreenButton!!.setOnClickListener { v -> onFullScreenButtonClicked() }
            videoPlayPause = rootView.findViewById(R.id.videoPlayPause)

            extraOptionsView = rootView.findViewById(R.id.extraOptionsView)
            closingOverlayView = rootView.findViewById(R.id.closingOverlay)
            rootView.addOnLayoutChangeListener(this)
        }

        override fun initListeners() {
            super.initListeners()
            videoPlayPause!!.setOnClickListener { v -> onPlayPause() }
        }

        override fun setupSubtitleView(view: SubtitleView,
                                       captionScale: Float,
                                       captionStyle: CaptionStyleCompat) {
            val captionRatio = (captionScale - 1f) / 5f + 1f
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio)
            view.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT)
            view.setStyle(captionStyle)
        }

        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            val widthDp = Math.abs(right - left) / resources.displayMetrics.density
            val visibility = if (widthDp > MINIMUM_SHOW_EXTRA_WIDTH_DP) View.VISIBLE else View.GONE
            extraOptionsView!!.visibility = visibility
        }

        override fun destroy() {
            if (notificationRemoteView != null) notificationRemoteView!!.setImageViewBitmap(R.id.notificationCover, null)
            super.destroy()
        }

        public override fun onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked()

            Log.d(TAG, "onFullScreenButtonClicked() called")

            setRecovery()
            // using only ExoPlayer
            val intent = NavigationHelper.getPlayerIntent(
                    context,
                    MainVideoPlayer::class.java,
                    this.playQueue!!,
                    this.repeatMode,
                    this.playbackSpeed,
                    this.playbackPitch,
                    this.playbackSkipSilence,
                    this.playbackQuality
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
            closePopup()
        }

        override fun onDismiss(menu: PopupMenu) {
            super.onDismiss(menu)
            if (isPlaying) hideControls(500, 0)
        }

        override fun nextResizeMode(currentResizeMode: Int): Int {
            return if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            super.onStopTrackingTouch(seekBar)
            if (wasPlaying()) {
                hideControls(100, 0)
            }
        }

        override fun onShuffleClicked() {
            super.onShuffleClicked()
            updatePlayback()
        }

        override fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
            updateProgress(currentProgress, duration, bufferPercent)
            super.onUpdateProgress(currentProgress, duration, bufferPercent)
        }

        ///////////////////////////////////////////////////////////////////////////
        // Getters
        ///////////////////////////////////////////////////////////////////////////
        override val qualityResolver: VideoPlaybackResolver.QualityResolver
            get () = object : VideoPlaybackResolver.QualityResolver {
                override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>): Int =
                        ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos)

                override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>, playbackQuality: String?): Int =
                        ListHelper.getPopupResolutionIndex(context, sortedVideos, playbackQuality!!)
            }


        ///////////////////////////////////////////////////////////////////////////
        // Thumbnail Loading
        ///////////////////////////////////////////////////////////////////////////

        override fun onLoadingComplete(imageUri: String, view: View?, loadedImage: Bitmap?) {
            super.onLoadingComplete(imageUri, view, loadedImage)
            // rebuild notification here since remote view does not release bitmaps,
            // causing memory leaks
            resetNotification()
            updateNotification(-1)
        }

        override fun onLoadingFailed(imageUri: String, view: View?, failReason: FailReason) {
            super.onLoadingFailed(imageUri, view, failReason)
            resetNotification()
            updateNotification(-1)
        }

        override fun onLoadingCancelled(imageUri: String, view: View) {
            super.onLoadingCancelled(imageUri, view)
            resetNotification()
            updateNotification(-1)
        }

        ///////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        ///////////////////////////////////////////////////////////////////////////

        internal fun setActivityListener(listener: PlayerEventListener) {
            playerEventListener = listener
            updateMetadata()
            updatePlayback()
            triggerProgressUpdate()
        }

        internal fun removeActivityListener(listener: PlayerEventListener) {
            if (playerEventListener == listener) {
                playerEventListener = null
            }
        }

        private fun updateMetadata() {
            if (playerEventListener != null && currentMetadata != null) {
                playerEventListener!!.onMetadataUpdate(currentMetadata!!.metadata)
            }
        }

        private fun updatePlayback() {
            if (playerEventListener != null && simpleExoPlayer != null && playQueue != null) {
                playerEventListener!!.onPlaybackUpdate(currentState, repeatMode,
                        playQueue!!.isShuffled, simpleExoPlayer!!.playbackParameters)
            }
        }

        private fun updateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
            if (playerEventListener != null) {
                playerEventListener!!.onProgressUpdate(currentProgress, duration, bufferPercent)
            }
        }

        fun stopActivityBinding() {
            if (playerEventListener != null) {
                playerEventListener!!.onServiceStopped()
                playerEventListener = null
            }
        }

        ///////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onRepeatModeChanged(reason: Int) {
            super.onRepeatModeChanged(reason)
            setRepeatModeRemote(notificationRemoteView, reason)
            updatePlayback()
            resetNotification()
            updateNotification(-1)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
            updatePlayback()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Playback Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onMetadataChanged(tag: MediaSourceTag) {
            super.onMetadataChanged(tag)
            resetNotification()
            updateNotification(-1)
            updateMetadata()
        }

        override fun onPlaybackShutdown() {
            super.onPlaybackShutdown()
            closePopup()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Broadcast Receiver
        ///////////////////////////////////////////////////////////////////////////

        override fun setupBroadcastReceiver(intentFilter: IntentFilter) {
            super.setupBroadcastReceiver(intentFilter)
            Log.d(TAG, "setupBroadcastReceiver() called with: intentFilter = [$intentFilter]")
            intentFilter.addAction(ACTION_CLOSE)
            intentFilter.addAction(ACTION_PLAY_PAUSE)
            intentFilter.addAction(ACTION_REPEAT)

            intentFilter.addAction(Intent.ACTION_SCREEN_ON)
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        }

        override fun onBroadcastReceived(intent: Intent?) {
            super.onBroadcastReceived(intent)
            if (intent == null || intent.action == null) return
            Log.d(TAG, "onBroadcastReceived() called with: intent = [$intent]")
            when (intent.action) {
                ACTION_CLOSE -> closePopup()
                ACTION_PLAY_PAUSE -> onPlayPause()
                ACTION_REPEAT -> onRepeatClicked()
                Intent.ACTION_SCREEN_ON -> enableVideoRenderer(true)
                Intent.ACTION_SCREEN_OFF -> enableVideoRenderer(false)
            }
        }

        ///////////////////////////////////////////////////////////////////////////
        // States
        ///////////////////////////////////////////////////////////////////////////

        override fun changeState(state: Int) {
            super.changeState(state)
            updatePlayback()
        }

        override fun onBlocked() {
            super.onBlocked()
            resetNotification()
            updateNotification(R.drawable.ic_play_arrow_white)
        }

        override fun onPlaying() {
            super.onPlaying()

            updateWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS)

            resetNotification()
            updateNotification(R.drawable.ic_pause_white)

            videoPlayPause!!.setBackgroundResource(R.drawable.ic_pause_white)
            hideControls(DEFAULT_CONTROLS_DURATION.toLong(), DEFAULT_CONTROLS_HIDE_TIME.toLong())

            startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
            lockManager!!.acquireWifiAndCpu()
        }

        override fun onBuffering() {
            super.onBuffering()
            resetNotification()
            updateNotification(R.drawable.ic_play_arrow_white)
        }

        override fun onPaused() {
            super.onPaused()

            updateWindowFlags(IDLE_WINDOW_FLAGS)

            resetNotification()
            updateNotification(R.drawable.ic_play_arrow_white)

            videoPlayPause!!.setBackgroundResource(R.drawable.ic_play_arrow_white)
            lockManager!!.releaseWifiAndCpu()

            stopForeground(false)
        }

        override fun onPausedSeek() {
            super.onPausedSeek()
            resetNotification()
            updateNotification(R.drawable.ic_play_arrow_white)

            videoPlayPause!!.setBackgroundResource(R.drawable.ic_pause_white)
        }

        override fun onCompleted() {
            super.onCompleted()

            updateWindowFlags(IDLE_WINDOW_FLAGS)

            resetNotification()
            updateNotification(R.drawable.ic_replay_white)

            videoPlayPause!!.setBackgroundResource(R.drawable.ic_replay_white)
            lockManager!!.releaseWifiAndCpu()

            stopForeground(false)
        }

        override fun showControlsThenHide() {
            videoPlayPause!!.visibility = View.VISIBLE
            super.showControlsThenHide()
        }

        override fun showControls(duration: Long) {
            videoPlayPause!!.visibility = View.VISIBLE
            super.showControls(duration)
        }

        override fun hideControls(duration: Long, delay: Long) {
            super.hideControlsAndButton(duration, delay, videoPlayPause)
        }

        ///////////////////////////////////////////////////////////////////////////
        // Utils
        ///////////////////////////////////////////////////////////////////////////

        private fun enableVideoRenderer(enable: Boolean) {
            val videoRendererIndex = getRendererIndex(C.TRACK_TYPE_VIDEO)
            if (videoRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(videoRendererIndex, !enable))
            }
        }
    }

    private inner class PopupWindowGestureListener : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
        private var initialPopupX: Int = 0
        private var initialPopupY: Int = 0
        private var isMoving: Boolean = false
        private var isResizing: Boolean = false

        private val closingRadius: Float
            get() {
                val buttonRadius = closeOverlayButton!!.width / 2
                return buttonRadius * 1.2f    // 20% wider than the button itself
            }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d(TAG, "onDoubleTap() called with: event = [$e] rawXy = ${e.rawX}, ${e.rawY}, xy = ${e.x}, ${e.y}")
            if (playerImpl == null || !playerImpl!!.isPlaying) return false

            playerImpl!!.hideControls(0, 0)

            if (e.x > popupWidth / 2) {
                playerImpl!!.onFastForward()
            } else {
                playerImpl!!.onFastRewind()
            }

            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
            if (playerImpl == null || playerImpl!!.simpleExoPlayer == null) return false

            if (playerImpl!!.isControlsVisible) {
                playerImpl!!.hideControls(100, 100)
            } else {
                playerImpl!!.showControlsThenHide()

            }
            return true
        }

        override fun onDown(event: MotionEvent): Boolean {
            Log.d(TAG, "onDown() called with: event = [$event]")

            // Fix popup position when the user touch it, it may have the wrong one
            // because the soft input is visible (the draggable area is currently resized).
            if (event.pointerCount != 2)
                checkPopupPositionBounds(closeOverlayView!!.width.toFloat(), closeOverlayView!!.height.toFloat())

            initialPopupX = popupLayoutParams!!.x
            initialPopupY = popupLayoutParams!!.y
            popupWidth = popupLayoutParams!!.width.toFloat()
            popupHeight = popupLayoutParams!!.height.toFloat()
            return super.onDown(event)
        }

        override fun onLongPress(e: MotionEvent) {
            Log.d(TAG, "onLongPress() called with: e = [$e]")
            updateScreenSize()
            checkPopupPositionBounds()
            updatePopupSize(screenWidth.toInt(), -1)
        }

        override fun onScroll(initialEvent: MotionEvent, movingEvent: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isResizing || playerImpl == null) return super.onScroll(initialEvent, movingEvent, distanceX, distanceY)

            if (!isMoving) {
                animateView(closeOverlayButton!!, true, 200)
            }

            isMoving = true

            val diffX = (movingEvent.rawX - initialEvent.rawX).toInt().toFloat()
            var posX = (initialPopupX + diffX).toInt().toFloat()
            val diffY = (movingEvent.rawY - initialEvent.rawY).toInt().toFloat()
            var posY = (initialPopupY + diffY).toInt().toFloat()

            if (posX > screenWidth - popupWidth)
                posX = (screenWidth - popupWidth).toInt().toFloat()
            else if (posX < 0) posX = 0f

            if (posY > screenHeight - popupHeight)
                posY = (screenHeight - popupHeight).toInt().toFloat()
            else if (posY < 0) posY = 0f

            popupLayoutParams!!.x = posX.toInt()
            popupLayoutParams!!.y = posY.toInt()

            val closingOverlayView = playerImpl!!.closingOverlayView
            if (isInsideClosingRadius(movingEvent)) {
                if (closingOverlayView!!.visibility == View.GONE) {
                    animateView(closingOverlayView, true, 250)
                }
            } else {
                if (closingOverlayView!!.visibility == View.VISIBLE) {
                    animateView(closingOverlayView, false, 0)
                }
            }


            Log.d(TAG, "PopupVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + initialEvent.rawX + ", " + initialEvent.rawY + "]" + ", e1.getX,Y = [" + initialEvent.x + ", " + initialEvent.y + "]" +
                    ", e2.getRaw = [" + movingEvent.rawX + ", " + movingEvent.rawY + "]" + ", e2.getX,Y = [" + movingEvent.x + ", " + movingEvent.y + "]" +
                    ", distanceX,Y = [" + distanceX + ", " + distanceY + "]" +
                    ", posX,Y = [" + posX + ", " + posY + "]" +
                    ", popupW,H = [" + popupWidth + " x " + popupHeight + "]")

            windowManager!!.updateViewLayout(playerImpl!!.rootView, popupLayoutParams)
            return true
        }

        private fun onScrollEnd(event: MotionEvent) {
            Log.d(TAG, "onScrollEnd() called")
            if (playerImpl == null) return
            if (playerImpl!!.isControlsVisible && playerImpl!!.currentState == STATE_PLAYING) {
                playerImpl!!.hideControls(DEFAULT_CONTROLS_DURATION.toLong(), DEFAULT_CONTROLS_HIDE_TIME.toLong())
            }

            if (isInsideClosingRadius(event)) {
                closePopup()
            } else {
                animateView(playerImpl!!.closingOverlayView!!, false, 0)

                if (!isPopupClosing) {
                    animateView(closeOverlayButton!!, false, 200)
                }
            }
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            Log.d(TAG, "Fling velocity: dX=[$velocityX], dY=[$velocityY], e1:[${e1.x}, ${e1.y}], e2:[${e2.x}, ${e2.y}]")
            if (playerImpl == null) return false


            val absVelocityX = Math.abs(velocityX)
            val absVelocityY = Math.abs(velocityY)

            // only happens when popup window can't move
            if (Math.max(absVelocityX, absVelocityY) > 5000 && Math.abs(e2.y - e1.y) < 100) {
                if ((e2.x - e1.x) > 0 && Math.abs(e1.x - e2.x) > Math.abs(e1.y - e2.y) ){
                    Log.d(TAG, "Fling velocity: left to right: e2.y - e1.y = ${e2.y - e1.y}, e2.x - e1.x = ${e2.x - e1.x}")
                    playerImpl!!.onFastForward() // fling from left to right
                }

                if ((e2.x - e1.x) < 0 && Math.abs(e1.x - e2.x) > Math.abs(e1.y - e2.y)) {
                    Log.d(TAG, "Fling velocity: right to left: e2.y - e1.y = ${e2.y - e1.y}, e2.x - e1.x = ${e2.x - e1.x}")
                    playerImpl!!.onFastRewind()  // fling from right to left
                }
            }

            if (  Math.max(absVelocityX, absVelocityY) in tossFlingVelocity.toFloat() .. 5000.0f) {
                if (absVelocityX > tossFlingVelocity) popupLayoutParams!!.x = velocityX.toInt()
                if (absVelocityY > tossFlingVelocity) popupLayoutParams!!.y = velocityY.toInt()
                checkPopupPositionBounds()
                windowManager!!.updateViewLayout(playerImpl!!.rootView, popupLayoutParams)
                return true
            }


            return false
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            popupGestureDetector!!.onTouchEvent(event)
            if (playerImpl == null) return false
            if (event.pointerCount == 2 && !isResizing) {
                Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.")
                playerImpl!!.showAndAnimateControl(-1, true)
                playerImpl!!.loadingPanel!!.visibility = View.GONE

                playerImpl!!.hideControls(0, 0)
                animateView(playerImpl!!.currentDisplaySeek!!, false, 0, 0)
                animateView(playerImpl!!.resizingIndicator!!, true, 200, 0)
                isResizing = true
            }

            if (event.action == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
                Log.d(TAG, "onTouch() ACTION_MOVE > view = [$view],  e1.getRaw = [${event.rawX}, ${event.rawY}]")
                return handleMultiDrag(event)
            }

            if (event.action == MotionEvent.ACTION_UP) {
                Log.d(TAG, "onTouch() ACTION_UP > view = [$view],  e1.getRaw = [${event.rawX}, ${event.rawY}]")
                if (isMoving) {
                    isMoving = false
                    onScrollEnd(event)
                }

                if (isResizing) {
                    isResizing = false
                    animateView(playerImpl!!.resizingIndicator!!, false, 100, 0)
                    playerImpl!!.changeState(playerImpl!!.currentState)
                }

                if (!isPopupClosing) {
                    savePositionAndSize()
                }
            }

            view.performClick()
            return true
        }

        private fun handleMultiDrag(event: MotionEvent): Boolean {
            if (event.pointerCount != 2) return false

            val firstPointerX = event.getX(0)
            val secondPointerX = event.getX(1)

            val diff = Math.abs(firstPointerX - secondPointerX)
            if (firstPointerX > secondPointerX) {
                // second pointer is the anchor (the leftmost pointer)
                popupLayoutParams!!.x = (event.rawX - diff).toInt()
            } else {
                // first pointer is the anchor
                popupLayoutParams!!.x = event.rawX.toInt()
            }

            checkPopupPositionBounds()
            updateScreenSize()

            val width = Math.min(screenWidth, diff).toInt()
            updatePopupSize(width, -1)

            return true
        }

        ///////////////////////////////////////////////////////////////////////////
        // Utils
        ///////////////////////////////////////////////////////////////////////////

        private fun distanceFromCloseButton(popupMotionEvent: MotionEvent): Int {
            val closeOverlayButtonX = closeOverlayButton!!.left + closeOverlayButton!!.width / 2
            val closeOverlayButtonY = closeOverlayButton!!.top + closeOverlayButton!!.height / 2

            val fingerX = popupLayoutParams!!.x + popupMotionEvent.x
            val fingerY = popupLayoutParams!!.y + popupMotionEvent.y

            return Math.sqrt(Math.pow((closeOverlayButtonX - fingerX).toDouble(), 2.0) + Math.pow((closeOverlayButtonY - fingerY).toDouble(), 2.0)).toInt()
        }

        private fun isInsideClosingRadius(popupMotionEvent: MotionEvent): Boolean {
            return distanceFromCloseButton(popupMotionEvent) <= closingRadius
        }
    }

    companion object {
        private const val TAG = "PopupVideoPlayer"

        private const val NOTIFICATION_ID = 40028922
        const val ACTION_CLOSE = "org.schabi.newpipe.simpleExoPlayer.PopupVideoPlayer.CLOSE"
        const val ACTION_PLAY_PAUSE = "org.schabi.newpipe.simpleExoPlayer.PopupVideoPlayer.PLAY_PAUSE"
        const val ACTION_REPEAT = "org.schabi.newpipe.simpleExoPlayer.PopupVideoPlayer.REPEAT"

        private const val POPUP_SAVED_WIDTH = "popup_saved_width"
        private const val POPUP_SAVED_X = "popup_saved_x"
        private const val POPUP_SAVED_Y = "popup_saved_y"

        private const val MINIMUM_SHOW_EXTRA_WIDTH_DP = 300

        private const val IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        private const val ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    }
}
