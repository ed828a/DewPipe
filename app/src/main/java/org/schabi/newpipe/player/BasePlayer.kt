/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BasePlayer.java is part of NewPipe
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.print.PrintJobInfo.STATE_BLOCKED
import android.util.Log
import android.view.View
import android.widget.Toast

import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.FailReason
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener

import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.Downloader
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.MediaSessionManager
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.playback.BasePlayerMediaSession
import org.schabi.newpipe.player.playback.CustomTrackSelector
import org.schabi.newpipe.player.playback.MediaSourceManager
import org.schabi.newpipe.player.playback.PlaybackListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.MediaSourceTag
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.SerializedCache

import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.SerialDisposable

import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT

/**
 * Base for the players, joining the common properties
 *
 */
abstract class BasePlayer(protected val context: Context) : Player.EventListener, PlaybackListener, ImageLoadingListener {

    private val broadcastReceiver: BroadcastReceiver

    private val intentFilter: IntentFilter

    private val recordManager: HistoryRecordManager

    private val progressUpdateReactor: SerialDisposable
    private val databaseUpdateReactor: CompositeDisposable

    protected val dataSource: PlayerDataSource
    protected val trackSelector: CustomTrackSelector

    private val loadControl: LoadControl
    private val renderFactory: RenderersFactory

    var playQueue: PlayQueue? = null
        protected set

    var playQueueAdapter: PlayQueueAdapter? = null
        protected set

    private var playbackManager: MediaSourceManager? = null

    private var currentItem: PlayQueueItem? = null
    var currentMetadata: MediaSourceTag? = null
        private set
    private var currentThumbnail: Bitmap? = null

    private var errorToast: Toast? = null

    ///////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    ///////////////////////////////////////////////////////////////////////////

    var player: SimpleExoPlayer? = null
        protected set
    var audioReactor: AudioReactor? = null
        protected set
    var mediaSessionManager: MediaSessionManager? = null

    var isPrepared = false
        private set

    var currentState = STATE_PREFLIGHT
        protected set

    private val progressReactor: Disposable
        get() = Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS.toLong(), TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ ignored -> triggerProgressUpdate() },
                        { error -> Log.e(TAG, "Progress update failure: ", error) })

    private val isCurrentWindowValid: Boolean
        get() = (player != null && player!!.duration >= 0 && player!!.currentPosition >= 0)

    val videoUrl: String
        get() = if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata!!.metadata.url

    val videoTitle: String
        get() = if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata!!.metadata.name

    val uploaderName: String
        get() = if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata!!.metadata.uploaderName

    val thumbnail: Bitmap?
        get() = if (currentThumbnail == null)
            BitmapFactory.decodeResource(context.resources, R.drawable.dummy_thumbnail)
        else
            currentThumbnail

    /** Checks if the current playback is a livestream AND is playing at or beyond the live edge  */
    val isLiveEdge: Boolean
        get() {
            if (player == null || !isLive) return false

            val currentTimeline = player!!.currentTimeline
            val currentWindowIndex = player!!.currentWindowIndex
            if (currentTimeline.isEmpty ||
                    currentWindowIndex < 0 ||
                    currentWindowIndex >= currentTimeline.windowCount) {
                return false
            }

            val timelineWindow = Timeline.Window()
            currentTimeline.getWindow(currentWindowIndex, timelineWindow)
            return timelineWindow.defaultPositionMs <= player!!.currentPosition
        }


    private val isLive: Boolean
        get() {
            if (player == null) return false
            try {
                return player!!.isCurrentWindowDynamic
            } catch (ignored: IndexOutOfBoundsException) {
                // Why would this even happen =(
                // But lets log it anyway. Save is save
                if (DEBUG) Log.d(TAG, "Could not update metadata: " + ignored.message)
                if (DEBUG) ignored.printStackTrace()
                return false
            }

        }

    val isPlaying: Boolean
        get() {
            if (player == null) return false
            val state = player!!.playbackState
            return (state == Player.STATE_READY || state == Player.STATE_BUFFERING) && player!!.playWhenReady
        }

    var repeatMode: Int
        @Player.RepeatMode
        get() = if (player == null) Player.REPEAT_MODE_OFF else player!!.repeatMode
        set(@Player.RepeatMode repeatMode) {
            if (player != null) player!!.repeatMode = repeatMode
        }

    var playbackSpeed: Float
        get() = playbackParameters.speed
        set(speed) = setPlaybackParameters(speed, playbackPitch, playbackSkipSilence)

    val playbackPitch: Float
        get() = playbackParameters.pitch

    val playbackSkipSilence: Boolean
        get() = playbackParameters.skipSilence

    val playbackParameters: PlaybackParameters
        get() {
            if (player == null) return PlaybackParameters.DEFAULT
            val parameters = player!!.playbackParameters
            return parameters ?: PlaybackParameters.DEFAULT
        }

    val isProgressLoopRunning: Boolean
        get() = progressUpdateReactor.get() != null

    init {

        this.broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onBroadcastReceived(intent)
            }
        }
        this.intentFilter = IntentFilter()
        setupBroadcastReceiver(intentFilter)

        this.recordManager = HistoryRecordManager(context)

        this.progressUpdateReactor = SerialDisposable()
        this.databaseUpdateReactor = CompositeDisposable()

        val userAgent = Downloader.USER_AGENT
        val bandwidthMeter = DefaultBandwidthMeter()
        this.dataSource = PlayerDataSource(context, userAgent, bandwidthMeter)

        val trackSelectionFactory = PlayerHelper.getQualitySelector(context, bandwidthMeter)
        this.trackSelector = CustomTrackSelector(trackSelectionFactory)

        this.loadControl = LoadController(context)
        this.renderFactory = DefaultRenderersFactory(context)
    }

    fun setup() {
        if (player == null) {
            initPlayer(/*playOnInit=*/true)
        }
        initListeners()
    }

    open fun initPlayer(playOnReady: Boolean) {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [$context]")

        player = ExoPlayerFactory.newSimpleInstance(renderFactory, trackSelector, loadControl)
        player!!.addListener(this)
        player!!.playWhenReady = playOnReady
        player!!.setSeekParameters(PlayerHelper.getSeekParameters(context))

        audioReactor = AudioReactor(context, player!!)
        mediaSessionManager = MediaSessionManager(context, player!!,
                BasePlayerMediaSession(this))

        registerBroadcastReceiver()
    }

    open fun initListeners() {}

    open fun handleIntent(intent: Intent?) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [$intent]")
        if (intent == null) return

        // Resolve play queue
        if (!intent.hasExtra(PLAY_QUEUE_KEY)) return
        val intentCacheKey = intent.getStringExtra(PLAY_QUEUE_KEY)
        val queue = SerializedCache.instance.take(intentCacheKey, PlayQueue::class.java)
                ?: return

        // Resolve append intents
        if (intent.getBooleanExtra(APPEND_ONLY, false) && playQueue != null) {
            val sizeBeforeAppend = playQueue!!.size()
            playQueue!!.append(queue.streams!!)

            if ((intent.getBooleanExtra(SELECT_ON_APPEND, false) || currentState == STATE_COMPLETED) && queue.streams!!.size > 0) {
                playQueue!!.index = sizeBeforeAppend
            }

            return
        }

        val repeatMode = intent.getIntExtra(REPEAT_MODE, repeatMode)
        val playbackSpeed = intent.getFloatExtra(PLAYBACK_SPEED, playbackSpeed)
        val playbackPitch = intent.getFloatExtra(PLAYBACK_PITCH, playbackPitch)
        val playbackSkipSilence = intent.getBooleanExtra(PLAYBACK_SKIP_SILENCE,
                playbackSkipSilence)

        // Good to go...
        initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, playbackSkipSilence,
                /*playOnInit=*/true)
    }

    fun initPlayback(queue: PlayQueue,
                               @Player.RepeatMode repeatMode: Int,
                               playbackSpeed: Float,
                               playbackPitch: Float,
                               playbackSkipSilence: Boolean,
                               playOnReady: Boolean) {
        destroyPlayer()
        initPlayer(playOnReady)
        this.repeatMode = repeatMode
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence)

        playQueue = queue
        playQueue!!.init()
        if (playbackManager != null) playbackManager!!.dispose()
        playbackManager = MediaSourceManager(this, playQueue!!)

        if (playQueueAdapter != null) playQueueAdapter!!.dispose()
        playQueueAdapter = PlayQueueAdapter(context, playQueue!!)
    }

    fun destroyPlayer() {
        if (DEBUG) Log.d(TAG, "destroyPlayer() called")
        if (player != null) {
            player!!.removeListener(this)
            player!!.stop()
            player!!.release()
        }
        if (isProgressLoopRunning) stopProgressLoop()
        if (playQueue != null) playQueue!!.dispose()
        if (audioReactor != null) audioReactor!!.dispose()
        if (playbackManager != null) playbackManager!!.dispose()
        if (mediaSessionManager != null) mediaSessionManager!!.dispose()

        if (playQueueAdapter != null) {
            playQueueAdapter!!.unsetSelectedListener()
            playQueueAdapter!!.dispose()
        }
    }

    open fun destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called")
        destroyPlayer()
        unregisterBroadcastReceiver()

        databaseUpdateReactor.clear()
        progressUpdateReactor.set(null)

        player = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    ///////////////////////////////////////////////////////////////////////////

    private fun initThumbnail(url: String?) {
        if (DEBUG) Log.d(TAG, "Thumbnail - initThumbnail() called")
        if (url == null || url.isEmpty()) return
        ImageLoader.getInstance().resume()
        ImageLoader.getInstance().loadImage(url, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS,
                this)
    }

    override fun onLoadingStarted(imageUri: String, view: View?) {
        if (DEBUG)
            Log.d(TAG, "Thumbnail - onLoadingStarted() called on: " +
                    "imageUri = [" + imageUri + "], view = [" + view + "]")
    }

    override fun onLoadingFailed(imageUri: String, view: View, failReason: FailReason) {
        Log.e(TAG, "Thumbnail - onLoadingFailed() called on imageUri = [$imageUri]",
                failReason.cause)
        currentThumbnail = null
    }

    override fun onLoadingComplete(imageUri: String, view: View?, loadedImage: Bitmap?) {
        if (DEBUG)
            Log.d(TAG, "Thumbnail - onLoadingComplete() called with: " +
                    "imageUri = [" + imageUri + "], view = [" + view + "], " +
                    "loadedImage = [" + loadedImage + "]")
        currentThumbnail = loadedImage
    }

    override fun onLoadingCancelled(imageUri: String, view: View) {
        if (DEBUG)
            Log.d(TAG, "Thumbnail - onLoadingCancelled() called with: " +
                    "imageUri = [" + imageUri + "], view = [" + view + "]")
        currentThumbnail = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Add your action in the intentFilter
     *
     * @param intentFilter intent filter that will be used for register the receiver
     */
    protected open fun setupBroadcastReceiver(intentFilter: IntentFilter) {
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    }

    open fun onBroadcastReceived(intent: Intent?) {
        if (intent == null || intent.action == null) return
        when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> onPause()
        }
    }

    protected fun registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver()
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    protected fun unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (unregisteredException: IllegalArgumentException) {
            Log.w(TAG, "Broadcast receiver already unregistered (" + unregisteredException.message + ")")
        }

    }

    open fun changeState(state: Int) {
        if (DEBUG) Log.d(TAG, "changeState() called with: state = [$state]")
        currentState = state
        when (state) {
            STATE_BLOCKED -> onBlocked()
            STATE_PLAYING -> onPlaying()
            STATE_BUFFERING -> onBuffering()
            STATE_PAUSED -> onPaused()
            STATE_PAUSED_SEEK -> onPausedSeek()
            STATE_COMPLETED -> onCompleted()
        }
    }

    open fun onBlocked() {
        if (DEBUG) Log.d(TAG, "onBlocked() called")
        if (!isProgressLoopRunning) startProgressLoop()
    }

    open fun onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called")
        if (!isProgressLoopRunning) startProgressLoop()
    }

    open fun onBuffering() {}

    open fun onPaused() {
        if (isProgressLoopRunning) stopProgressLoop()
    }

    open fun onPausedSeek() {}

    open fun onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called")
        if (playQueue!!.index < playQueue!!.size() - 1) playQueue!!.offsetIndex(+1)
        if (isProgressLoopRunning) stopProgressLoop()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    ///////////////////////////////////////////////////////////////////////////

    fun onRepeatClicked() {
        if (DEBUG) Log.d(TAG, "onRepeatClicked() called")

        val mode: Int

        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> mode = Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> mode = Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> mode = Player.REPEAT_MODE_OFF
            else -> mode = Player.REPEAT_MODE_OFF
        }

        repeatMode = mode
        if (DEBUG) Log.d(TAG, "onRepeatClicked() currentRepeatMode = $repeatMode")
    }

    open fun onShuffleClicked() {
        if (DEBUG) Log.d(TAG, "onShuffleClicked() called")

        if (player == null) return
        player!!.shuffleModeEnabled = !player!!.shuffleModeEnabled
    }

    ///////////////////////////////////////////////////////////////////////////
    // Progress Updates
    ///////////////////////////////////////////////////////////////////////////

    abstract fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int)

    fun startProgressLoop() {
        progressUpdateReactor.set(progressReactor)
    }

    fun stopProgressLoop() {
        progressUpdateReactor.set(null)
    }

    fun triggerProgressUpdate() {
        if (player == null) return
        onUpdateProgress(
                Math.max(player!!.currentPosition.toInt(), 0),
                player!!.duration.toInt(),
                player!!.bufferedPercentage
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun onTimelineChanged(timeline: Timeline, manifest: Any?,
                                   @Player.TimelineChangeReason reason: Int) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onTimelineChanged() called with " +
                    (if (manifest == null) "no manifest" else "available manifest") + ", " +
                    "timeline size = [" + timeline.windowCount + "], " +
                    "reason = [" + reason + "]")

        maybeUpdateCurrentMetadata()
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onTracksChanged(), " +
                    "track group size = " + trackGroups.length)

        maybeUpdateCurrentMetadata()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - playbackParameters(), " +
                    "speed: " + playbackParameters.speed + ", " +
                    "pitch: " + playbackParameters.pitch)
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onLoadingChanged() called with: " +
                    "isLoading = [" + isLoading + "]")

        if (!isLoading && currentState == STATE_PAUSED && isProgressLoopRunning) {
            stopProgressLoop()
        } else if (isLoading && !isProgressLoopRunning) {
            startProgressLoop()
        }

        maybeUpdateCurrentMetadata()
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: " +
                    "playWhenReady = [" + playWhenReady + "], " +
                    "playbackState = [" + playbackState + "]")

        if (currentState == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerStateChanged() is currently blocked")
            return
        }

        when (playbackState) {
            Player.STATE_IDLE // 1
            -> isPrepared = false
            Player.STATE_BUFFERING // 2
            -> if (isPrepared) {
                changeState(STATE_BUFFERING)
            }
            Player.STATE_READY //3
            -> {
                maybeUpdateCurrentMetadata()
                maybeCorrectSeekPosition()
                if (!isPrepared) {
                    isPrepared = true
                    onPrepared(playWhenReady)
                } else {
                    changeState(if (playWhenReady) STATE_PLAYING else STATE_PAUSED)
                }
            }
            Player.STATE_ENDED // 4
            -> {
                changeState(STATE_COMPLETED)
                isPrepared = false
            }
        }
    }

    private fun maybeCorrectSeekPosition() {
        if (playQueue == null || player == null || currentMetadata == null) return

        val currentSourceItem = playQueue!!.item ?: return

        val currentInfo = currentMetadata!!.metadata
        val presetStartPositionMillis = currentInfo.startPosition * 1000
        if (presetStartPositionMillis > 0L) {
            // Has another start position?
            if (DEBUG)
                Log.d(TAG, "Playback - Seeking to preset start " +
                        "position=[" + presetStartPositionMillis + "]")
            seekTo(presetStartPositionMillis)
        }
    }

    /**
     * Processes the exceptions produced by [ExoPlayer][com.google.android.exoplayer2.ExoPlayer].
     * There are multiple types of errors: <br></br><br></br>
     *
     * [TYPE_SOURCE][ExoPlaybackException.TYPE_SOURCE]: <br></br><br></br>
     *
     * [TYPE_UNEXPECTED][ExoPlaybackException.TYPE_UNEXPECTED]: <br></br><br></br>
     * If a runtime error occurred, then we can try to recover it by restarting the playback
     * after setting the timestamp recovery. <br></br><br></br>
     *
     * [TYPE_RENDERER][ExoPlaybackException.TYPE_RENDERER]: <br></br><br></br>
     * If the renderer failed, treat the error as unrecoverable.
     *
     * @see .processSourceError
     * @see Player.EventListener.onPlayerError
     */
    override fun onPlayerError(error: ExoPlaybackException) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onPlayerError() called with: " +
                    "error = [" + error + "]")
        if (errorToast != null) {
            errorToast!!.cancel()
            errorToast = null
        }

        savePlaybackState()

        when (error.type) {
            ExoPlaybackException.TYPE_SOURCE -> {
                processSourceError(error.sourceException)
                showStreamError(error)
            }
            ExoPlaybackException.TYPE_UNEXPECTED -> {
                showRecoverableError(error)
                setRecovery()
                reload()
            }
            else -> {
                showUnrecoverableError(error)
                onPlaybackShutdown()
            }
        }
    }

    private fun processSourceError(error: IOException) {
        if (player == null || playQueue == null) return
        setRecovery()

        val cause = error.cause
        if (error is BehindLiveWindowException) {
            reload()
        } else if (cause is UnknownHostException) {
            playQueue!!.error(/*isNetworkProblem=*/true)
        } else if (isCurrentWindowValid) {
            playQueue!!.error(/*isTransitioningToBadStream=*/true)
        } else if (cause is FailedMediaSource.MediaSourceResolutionException) {
            playQueue!!.error(/*recoverableWithNoAvailableStream=*/false)
        } else if (cause is FailedMediaSource.StreamInfoLoadException) {
            playQueue!!.error(/*recoverableIfLoadFailsWhenNetworkIsFine=*/false)
        } else {
            playQueue!!.error(/*noIdeaWhatHappenedAndLetUserChooseWhatToDo=*/true)
        }
    }

    override fun onPositionDiscontinuity(@Player.DiscontinuityReason reason: Int) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with " +
                    "reason = [" + reason + "]")
        if (playQueue == null) return

        // Refresh the playback if there is a transition to the next video
        val newWindowIndex = player!!.currentWindowIndex
        when (reason) {
            DISCONTINUITY_REASON_PERIOD_TRANSITION -> {
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (repeatMode == Player.REPEAT_MODE_ONE && newWindowIndex == playQueue!!.index) {
                    registerView()
                } else if (playQueue!!.index != newWindowIndex) {
                    playQueue!!.index = newWindowIndex
                }
            }

            DISCONTINUITY_REASON_SEEK, DISCONTINUITY_REASON_SEEK_ADJUSTMENT, DISCONTINUITY_REASON_INTERNAL -> if (playQueue!!.index != newWindowIndex) {
                playQueue!!.index = newWindowIndex
            }
        }

        maybeUpdateCurrentMetadata()
    }

    override fun onRepeatModeChanged(@Player.RepeatMode reason: Int) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: " +
                    "mode = [" + reason + "]")
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (DEBUG)
            Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: " +
                    "mode = [" + shuffleModeEnabled + "]")
        if (playQueue == null) return
        if (shuffleModeEnabled) {
            playQueue!!.shuffle()
        } else {
            playQueue!!.unshuffle()
        }
    }

    override fun onSeekProcessed() {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onSeekProcessed() called")
    }
    ///////////////////////////////////////////////////////////////////////////
    // Playback Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (player == null || isLive || !isPlaying) return false

        val currentPositionMillis = player!!.currentPosition
        val currentDurationMillis = player!!.duration
        return currentDurationMillis - currentPositionMillis < timeToEndMillis
    }

    override fun onPlaybackBlock() {
        if (player == null) return
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackBlock() called")

        currentItem = null
        currentMetadata = null
        player!!.stop()
        isPrepared = false

        changeState(STATE_BLOCKED)
    }

    override fun onPlaybackUnblock(mediaSource: MediaSource) {
        if (player == null) return
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackUnblock() called")

        if (currentState == STATE_BLOCKED) changeState(STATE_BUFFERING)

        player!!.prepare(mediaSource)
    }

    override fun onPlaybackSynchronize(item: PlayQueueItem) {
        if (DEBUG)
            Log.d(TAG, "Playback - onPlaybackSynchronize() called with " +
                    "item=[" + item.title + "], url=[" + item.url + "]")
        if (player == null || playQueue == null) return

        val onPlaybackInitial = currentItem == null
        val hasPlayQueueItemChanged = currentItem !== item

        val currentPlayQueueIndex = playQueue!!.indexOf(item)
        val currentPlaylistIndex = player!!.currentWindowIndex
        val currentPlaylistSize = player!!.currentTimeline.windowCount

        // If nothing to synchronize
        if (!hasPlayQueueItemChanged) return
        currentItem = item

        // Check if on wrong window
        if (currentPlayQueueIndex != playQueue!!.index) {
            Log.e(TAG, "Playback - Play Queue may be desynchronized: item " +
                    "index=[" + currentPlayQueueIndex + "], " +
                    "queue index=[" + playQueue!!.index + "]")

            // Check if bad seek position
        } else if (currentPlaylistSize > 0 && currentPlayQueueIndex >= currentPlaylistSize || currentPlayQueueIndex < 0) {
            Log.e(TAG, "Playback - Trying to seek to invalid " +
                    "index=[" + currentPlayQueueIndex + "] with " +
                    "playlist length=[" + currentPlaylistSize + "]")

        } else if (currentPlaylistIndex != currentPlayQueueIndex || onPlaybackInitial ||
                !isPlaying) {
            if (DEBUG)
                Log.d(TAG, "Playback - Rewinding to correct" +
                        " index=[" + currentPlayQueueIndex + "]," +
                        " getTabFrom=[" + currentPlaylistIndex + "], size=[" + currentPlaylistSize + "].")

            if (item.recoveryPosition != PlayQueueItem.RECOVERY_UNSET) {
                player!!.seekTo(currentPlayQueueIndex, item.recoveryPosition)
                playQueue!!.unsetRecovery(currentPlayQueueIndex)
            } else {
                player!!.seekToDefaultPosition(currentPlayQueueIndex)
            }
        }
    }

    protected open fun onMetadataChanged(tag: MediaSourceTag) {
        val info = tag.metadata
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.name)
        }

        initThumbnail(info.thumbnailUrl)
        registerView()
    }

    override fun onPlaybackShutdown() {
        if (DEBUG) Log.d(TAG, "Shutting down...")
        destroy()
    }

    ///////////////////////////////////////////////////////////////////////////
    // General Player
    ///////////////////////////////////////////////////////////////////////////

    fun showStreamError(exception: Exception) {
        exception.printStackTrace()

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_stream_failure, Toast.LENGTH_SHORT)
            errorToast!!.show()
        }
    }

    fun showRecoverableError(exception: Exception) {
        exception.printStackTrace()

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_recoverable_failure, Toast.LENGTH_SHORT)
            errorToast!!.show()
        }
    }

    fun showUnrecoverableError(exception: Exception) {
        exception.printStackTrace()

        if (errorToast != null) {
            errorToast!!.cancel()
        }
        errorToast = Toast.makeText(context, R.string.player_unrecoverable_failure, Toast.LENGTH_SHORT)
        errorToast!!.show()
    }

    open fun onPrepared(playWhenReady: Boolean) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [$playWhenReady]")
        if (playWhenReady) audioReactor!!.requestAudioFocus()
        changeState(if (playWhenReady) STATE_PLAYING else STATE_PAUSED)
    }

    fun onPlay() {
        if (DEBUG) Log.d(TAG, "onPlay() called")
        if (audioReactor == null || playQueue == null || player == null) return

        audioReactor!!.requestAudioFocus()

        if (currentState == STATE_COMPLETED) {
            if (playQueue!!.index == 0) {
                seekToDefault()
            } else {
                playQueue!!.index = 0
            }
        }

        player!!.playWhenReady = true
    }

    fun onPause() {
        if (DEBUG) Log.d(TAG, "onPause() called")
        if (audioReactor == null || player == null) return

        audioReactor!!.abandonAudioFocus()
        player!!.playWhenReady = false
    }

    fun onPlayPause() {
        if (DEBUG) Log.d(TAG, "onPlayPause() called")

        if (!isPlaying) {
            onPlay()
        } else {
            onPause()
        }
    }

    open fun onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called")
        seekBy((-FAST_FORWARD_REWIND_AMOUNT_MILLIS).toLong())
    }

    open fun onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called")
        seekBy(FAST_FORWARD_REWIND_AMOUNT_MILLIS.toLong())
    }

    open fun onPlayPrevious() {
        if (player == null || playQueue == null) return
        if (DEBUG) Log.d(TAG, "onPlayPrevious() called")

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
        * restart current track. Also restart the track if the current track
        * is the first in a queue.*/
        if (player!!.currentPosition > PLAY_PREV_ACTIVATION_LIMIT_MILLIS || playQueue!!.index == 0) {
            seekToDefault()
            playQueue!!.offsetIndex(0)
        } else {
            savePlaybackState()
            playQueue!!.offsetIndex(-1)
        }
    }

    open fun onPlayNext() {
        if (playQueue == null) return
        if (DEBUG) Log.d(TAG, "onPlayNext() called")

        savePlaybackState()
        playQueue!!.offsetIndex(+1)
    }

    fun onSelected(item: PlayQueueItem) {
        if (playQueue == null || player == null) return

        val index = playQueue!!.indexOf(item)
        if (index == -1) return

        if (playQueue!!.index == index && player!!.currentWindowIndex == index) {
            seekToDefault()
        } else {
            savePlaybackState()
        }
        playQueue!!.index = index
    }

    fun seekTo(positionMillis: Long) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: position = [$positionMillis]")
        if (player != null) player!!.seekTo(positionMillis)
    }

    fun seekBy(offsetMillis: Long) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: offsetMillis = [$offsetMillis]")
        seekTo(player!!.currentPosition + offsetMillis)
    }

    fun seekToDefault() {
        if (player != null) {
            player!!.seekToDefaultPosition()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun registerView() {
        if (currentMetadata == null) return
        val currentInfo = currentMetadata!!.metadata
        val viewRegister = recordManager.onViewed(currentInfo).onErrorComplete()
                .subscribe(
                        {/* successful */ ignored -> },
                        { error -> Log.e(TAG, "Player onViewed() failure: ", error) }
                )
        databaseUpdateReactor.add(viewRegister)
    }

    protected fun reload() {
        if (playbackManager != null) {
            playbackManager!!.dispose()
        }

        if (playQueue != null) {
            playbackManager = MediaSourceManager(this, playQueue!!)
        }
    }

    protected fun savePlaybackState(info: StreamInfo?, progress: Long) {
        if (info == null) return
        val stateSaver = recordManager.saveStreamState(info, progress)
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorComplete()
                .subscribe(
                        {/* successful */ ignored -> },
                        { error -> Log.e(TAG, "savePlaybackState() failure: ", error) }
                )
        databaseUpdateReactor.add(stateSaver)
    }

    private fun savePlaybackState() {
        if (player == null || currentMetadata == null) return
        val currentInfo = currentMetadata!!.metadata

        if (player!!.currentPosition > RECOVERY_SKIP_THRESHOLD_MILLIS && player!!.currentPosition < player!!.duration - RECOVERY_SKIP_THRESHOLD_MILLIS) {
            savePlaybackState(currentInfo, player!!.currentPosition)
        }
    }

    private fun maybeUpdateCurrentMetadata() {
        if (player == null) return

        val metadata: MediaSourceTag?
        try {
            metadata = player!!.currentTag as MediaSourceTag?
        } catch (error: IndexOutOfBoundsException) {
            if (DEBUG) Log.d(TAG, "Could not update metadata: " + error.message)
            if (DEBUG) error.printStackTrace()
            return
        } catch (error: ClassCastException) {
            if (DEBUG) Log.d(TAG, "Could not update metadata: " + error.message)
            if (DEBUG) error.printStackTrace()
            return
        }

        if (metadata == null) return
        maybeAutoQueueNextStream(metadata)

        if (currentMetadata === metadata) return
        currentMetadata = metadata
        onMetadataChanged(metadata)
    }

    private fun maybeAutoQueueNextStream(currentMetadata: MediaSourceTag) {
        if (playQueue == null || playQueue!!.index != playQueue!!.size() - 1 ||
                repeatMode != Player.REPEAT_MODE_OFF ||
                !PlayerHelper.isAutoQueueEnabled(context))
            return
        // auto queue when starting playback on the last item when not repeating
        val autoQueue = PlayerHelper.autoQueueOf(currentMetadata.metadata,
                playQueue!!.streams!!)
        if (autoQueue != null) playQueue!!.append(autoQueue.streams!!)
    }

    fun setPlaybackParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        player!!.playbackParameters = PlaybackParameters(speed, pitch, skipSilence)
    }

    fun setRecovery() {
        if (playQueue == null || player == null) return

        val queuePos = playQueue!!.index
        val windowPos = player!!.currentPosition

        if (windowPos > 0 && windowPos <= player!!.duration) {
            setRecovery(queuePos, windowPos)
        }
    }

    fun setRecovery(queuePos: Int, windowPos: Long) {
        if (playQueue!!.size() <= queuePos) return

        if (DEBUG) Log.d(TAG, "Setting recovery, queue: $queuePos, pos: $windowPos")
        playQueue!!.setRecovery(queuePos, windowPos)
    }

    companion object {

        val DEBUG = BuildConfig.BUILD_TYPE != "release"
        val TAG = "BasePlayer"
        ///////////////////////////////////////////////////////////////////////////
        // Intent
        ///////////////////////////////////////////////////////////////////////////

        val REPEAT_MODE = "repeat_mode"
        val PLAYBACK_PITCH = "playback_pitch"
        val PLAYBACK_SPEED = "playback_speed"
        val PLAYBACK_SKIP_SILENCE = "playback_skip_silence"
        val PLAYBACK_QUALITY = "playback_quality"
        val PLAY_QUEUE_KEY = "play_queue_key"
        val APPEND_ONLY = "append_only"
        val SELECT_ON_APPEND = "select_on_append"

        ///////////////////////////////////////////////////////////////////////////
        // Playback
        ///////////////////////////////////////////////////////////////////////////

        val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        ///////////////////////////////////////////////////////////////////////////
        // Player
        ///////////////////////////////////////////////////////////////////////////

        protected val FAST_FORWARD_REWIND_AMOUNT_MILLIS = 10000 // 10 Seconds
        protected val PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000 // 5 seconds
        protected const val PROGRESS_LOOP_INTERVAL_MILLIS = 500
        protected val RECOVERY_SKIP_THRESHOLD_MILLIS = 3000 // 3 seconds

        ///////////////////////////////////////////////////////////////////////////
        // States Implementation
        ///////////////////////////////////////////////////////////////////////////

        val STATE_PREFLIGHT = -1
        val STATE_BLOCKED = 123
        val STATE_PLAYING = 124
        val STATE_BUFFERING = 125
        val STATE_PAUSED = 126
        val STATE_PAUSED_SEEK = 127
        val STATE_COMPLETED = 128
    }
}
