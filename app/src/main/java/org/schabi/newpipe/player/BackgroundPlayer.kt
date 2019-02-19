/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BackgroundPlayer.java is part of NewPipe
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

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.nostra13.universalimageloader.core.assist.FailReason
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.helper.LockManager
import org.schabi.newpipe.player.helper.PlayerHelper.getTimeString
import org.schabi.newpipe.player.model.PlayerServiceBinder
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.MediaSourceTag
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper


/**
 * Base players joining the common properties
 *
 */
class BackgroundPlayer : Service() {

    private var basePlayerImpl: BasePlayerImpl? = null
    private var lockManager: LockManager? = null

    ///////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    ///////////////////////////////////////////////////////////////////////////

    private var activityListener: PlayerEventListener? = null
    private var mBinder: IBinder? = null
    private var notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationRemoteView: RemoteViews? = null
    private var bigNotificationRemoteView: RemoteViews? = null

    private var shouldUpdateOnProgress: Boolean = false

    ///////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate() {
        Log.d(TAG, "onCreate() called")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        lockManager = LockManager(this)

        ThemeHelper.setTheme(this)
        basePlayerImpl = BasePlayerImpl(this)
        basePlayerImpl!!.setup()

        mBinder = PlayerServiceBinder(basePlayerImpl!!)
        shouldUpdateOnProgress = true
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() called with: intent = [$intent], flags = [$flags], startId = [$startId]")

        basePlayerImpl!!.handleIntent(intent)

        if (basePlayerImpl!!.mediaSessionManager != null) {
            basePlayerImpl!!.mediaSessionManager!!.handleMediaButtonIntent(intent)
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "destroy() called")
        onClose()
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    ///////////////////////////////////////////////////////////////////////////
    // Actions
    ///////////////////////////////////////////////////////////////////////////
    private fun onClose() {
        Log.d(TAG, "onClose() called")

        lockManager?.releaseWifiAndCpu()
        basePlayerImpl?.stopActivityBinding()
        basePlayerImpl?.destroy()
        notificationManager?.cancel(NOTIFICATION_ID)

        mBinder = null
        basePlayerImpl = null
        lockManager = null

        stopForeground(true)
        stopSelf()
    }

    private fun onScreenOnOff(on: Boolean) {
        Log.d(TAG, "onScreenOnOff() called with: on = [$on]")
        shouldUpdateOnProgress = on
        basePlayerImpl!!.triggerProgressUpdate()
        if (on) {
            basePlayerImpl!!.startProgressLoop()
        } else {
            basePlayerImpl!!.stopProgressLoop()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Notification
    ///////////////////////////////////////////////////////////////////////////

    private fun resetNotification() {
        notificationBuilder = createNotification()
    }

    private fun createNotification(): NotificationCompat.Builder {
        notificationRemoteView = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification)
        bigNotificationRemoteView = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded)

        setupNotification(notificationRemoteView!!)
        setupNotification(bigNotificationRemoteView!!)

        val builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notificationRemoteView)
                .setCustomBigContentView(bigNotificationRemoteView)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            builder.priority = NotificationCompat.PRIORITY_MAX
        }
        return builder
    }

    private fun setupNotification(remoteViews: RemoteViews) {
        if (basePlayerImpl == null) return

        remoteViews.setTextViewText(R.id.notificationSongName, basePlayerImpl!!.videoTitle)
        remoteViews.setTextViewText(R.id.notificationArtist, basePlayerImpl!!.uploaderName)

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT))
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT))
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT))

        // Starts background simpleExoPlayer activity -- attempts to unlock lockscreen
        val intent = NavigationHelper.getBackgroundPlayerActivityIntent(this)
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT))

        if (basePlayerImpl!!.playQueue != null && basePlayerImpl!!.playQueue!!.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous)
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next)
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_PLAY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT))
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_PLAY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind)
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_fastforward)
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT))
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT))
        }

        setRepeatModeIcon(remoteViews, basePlayerImpl!!.repeatMode)
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    @Synchronized
    private fun updateNotification(drawableId: Int) {
        //Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notificationBuilder == null) return
        if (drawableId != -1) {
            notificationRemoteView?.setImageViewResource(R.id.notificationPlayPause, drawableId)
            bigNotificationRemoteView?.setImageViewResource(R.id.notificationPlayPause, drawableId)
        }
        notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun setRepeatModeIcon(remoteViews: RemoteViews, repeatMode: Int) {
        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_off)
            Player.REPEAT_MODE_ONE -> remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_one)
            Player.REPEAT_MODE_ALL -> remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_all)
        }
    }
    //////////////////////////////////////////////////////////////////////////

    inner class BasePlayerImpl internal constructor(context: Context) : BasePlayer(context) {

        private val resolver: AudioPlaybackResolver = AudioPlaybackResolver(context, dataSource)

        override fun initPlayer(playOnReady: Boolean) {
            super.initPlayer(playOnReady)
        }

        override fun handleIntent(intent: Intent?) {
            super.handleIntent(intent)

            resetNotification()
            bigNotificationRemoteView?.setProgressBar(R.id.notificationProgressBar, 100, 0, false)
            notificationRemoteView?.setProgressBar(R.id.notificationProgressBar, 100, 0, false)
            startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
        }

        ///////////////////////////////////////////////////////////////////////////
        // Thumbnail Loading
        ///////////////////////////////////////////////////////////////////////////

        private fun updateNotificationThumbnail() {
            if (basePlayerImpl == null) return

            notificationRemoteView?.setImageViewBitmap(R.id.notificationCover, basePlayerImpl!!.thumbnail)
            bigNotificationRemoteView?.setImageViewBitmap(R.id.notificationCover, basePlayerImpl!!.thumbnail)
        }

        override fun onLoadingComplete(imageUri: String, view: View?, loadedImage: Bitmap?) {
            super.onLoadingComplete(imageUri, view, loadedImage)
            resetNotification()
            updateNotificationThumbnail()
            updateNotification(-1)
        }

        override fun onLoadingFailed(imageUri: String, view: View?, failReason: FailReason) {
            super.onLoadingFailed(imageUri, view, failReason)
            resetNotification()
            updateNotificationThumbnail()
            updateNotification(-1)
        }
        ///////////////////////////////////////////////////////////////////////////
        // States Implementation
        ///////////////////////////////////////////////////////////////////////////

        override fun onPrepared(playWhenReady: Boolean) {
            super.onPrepared(playWhenReady)
            simpleExoPlayer!!.volume = 1f
        }

        override fun onShuffleClicked() {
            super.onShuffleClicked()
            updatePlayback()
        }

        override fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
            updateProgress(currentProgress, duration, bufferPercent)

            if (!shouldUpdateOnProgress) return
            resetNotification()
            if (Build.VERSION.SDK_INT >= 26 /*Oreo*/) updateNotificationThumbnail()

            bigNotificationRemoteView?.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false)
            bigNotificationRemoteView?.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration))

            notificationRemoteView?.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false)

            updateNotification(-1)
        }

        override fun onPlayPrevious() {
            super.onPlayPrevious()
            triggerProgressUpdate()
        }

        override fun onPlayNext() {
            super.onPlayNext()
            triggerProgressUpdate()
        }

        override fun destroy() {
            super.destroy()
            notificationRemoteView?.setImageViewBitmap(R.id.notificationCover, null)
            bigNotificationRemoteView?.setImageViewBitmap(R.id.notificationCover, null)
        }

        ///////////////////////////////////////////////////////////////////////////
        // ExoPlayer Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
            updatePlayback()
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            // Disable default behavior
        }

        override fun onRepeatModeChanged(i: Int) {
            resetNotification()
            updateNotification(-1)
            updatePlayback()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Playback Listener
        ///////////////////////////////////////////////////////////////////////////

        override fun onMetadataChanged(tag: MediaSourceTag) {
            super.onMetadataChanged(tag)
            resetNotification()
            updateNotificationThumbnail()
            updateNotification(-1)
            updateMetadata()
        }

        override fun sourceOf(item: PlayQueueItem, info: StreamInfo): MediaSource? {
            return resolver.resolve(info)
        }

        override fun onPlaybackShutdown() {
            super.onPlaybackShutdown()
            onClose()
        }

        ///////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        ///////////////////////////////////////////////////////////////////////////

        internal fun setActivityListener(listener: PlayerEventListener) {
            activityListener = listener
            updateMetadata()
            updatePlayback()
            triggerProgressUpdate()
        }

        internal fun removeActivityListener(listener: PlayerEventListener) {
            if (activityListener === listener) {
                activityListener = null
            }
        }

        private fun updateMetadata() {
            if (activityListener != null && currentMetadata != null) {
                activityListener!!.onMetadataUpdate(currentMetadata!!.metadata)
            }
        }

        private fun updatePlayback() {
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener!!.onPlaybackUpdate(currentState, repeatMode,
                        playQueue!!.isShuffled, playbackParameters)
            }
        }

        private fun updateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {
            activityListener?.onProgressUpdate(currentProgress, duration, bufferPercent)
        }

        fun stopActivityBinding() {
            activityListener?.onServiceStopped()
            activityListener = null
        }

        ///////////////////////////////////////////////////////////////////////////
        // Broadcast Receiver
        ///////////////////////////////////////////////////////////////////////////

        override fun setupBroadcastReceiver(intentFilter: IntentFilter) {
            super.setupBroadcastReceiver(intentFilter)
            intentFilter.addAction(ACTION_CLOSE)
            intentFilter.addAction(ACTION_PLAY_PAUSE)
            intentFilter.addAction(ACTION_REPEAT)
            intentFilter.addAction(ACTION_PLAY_PREVIOUS)
            intentFilter.addAction(ACTION_PLAY_NEXT)
            intentFilter.addAction(ACTION_FAST_REWIND)
            intentFilter.addAction(ACTION_FAST_FORWARD)

            intentFilter.addAction(Intent.ACTION_SCREEN_ON)
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF)

            intentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        }

        override fun onBroadcastReceived(intent: Intent?) {
            super.onBroadcastReceived(intent)
            if (intent == null || intent.action == null) return
            Log.d(BasePlayer.TAG, "onBroadcastReceived() called with: intent = [$intent]")
            when (intent.action) {
                ACTION_CLOSE -> onClose()
                ACTION_PLAY_PAUSE -> onPlayPause()
                ACTION_REPEAT -> onRepeatClicked()
                ACTION_PLAY_NEXT -> onPlayNext()
                ACTION_PLAY_PREVIOUS -> onPlayPrevious()
                ACTION_FAST_FORWARD -> onFastForward()
                ACTION_FAST_REWIND -> onFastRewind()
                Intent.ACTION_SCREEN_ON -> onScreenOnOff(true)
                Intent.ACTION_SCREEN_OFF -> onScreenOnOff(false)
            }
        }

        ///////////////////////////////////////////////////////////////////////////
        // States
        ///////////////////////////////////////////////////////////////////////////

        override fun changeState(state: Int) {
            super.changeState(state)
            updatePlayback()
        }

        override fun onPlaying() {
            super.onPlaying()
            resetNotification()
            updateNotificationThumbnail()
            updateNotification(R.drawable.ic_pause_white)
            lockManager!!.acquireWifiAndCpu()
        }

        override fun onPaused() {
            super.onPaused()
            resetNotification()
            updateNotificationThumbnail()
            updateNotification(R.drawable.ic_play_arrow_white)
            lockManager!!.releaseWifiAndCpu()
        }

        override fun onCompleted() {
            super.onCompleted()
            resetNotification()

            bigNotificationRemoteView?.setProgressBar(R.id.notificationProgressBar, 100, 100, false)
            notificationRemoteView?.setProgressBar(R.id.notificationProgressBar, 100, 100, false)

            updateNotificationThumbnail()
            updateNotification(R.drawable.ic_replay_white)
            lockManager!!.releaseWifiAndCpu()
        }
    }

    companion object {
        private const val TAG = "BackgroundPlayer"

        const val ACTION_CLOSE = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.CLOSE"
        const val ACTION_PLAY_PAUSE = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.PLAY_PAUSE"
        const val ACTION_REPEAT = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.REPEAT"
        const val ACTION_PLAY_NEXT = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.ACTION_PLAY_NEXT"
        const val ACTION_PLAY_PREVIOUS = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.ACTION_PLAY_PREVIOUS"
        const val ACTION_FAST_REWIND = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.ACTION_FAST_REWIND"
        const val ACTION_FAST_FORWARD = "org.schabi.newpipe.simpleExoPlayer.BackgroundPlayer.ACTION_FAST_FORWARD"

        const val SET_IMAGE_RESOURCE_METHOD = "setImageResource"

        ///////////////////////////////////////////////////////////////////////////
        // Notification
        ///////////////////////////////////////////////////////////////////////////

        private const val NOTIFICATION_ID = 123789
    }
}
