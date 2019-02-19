package org.schabi.newpipe.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.FailReason
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.SerialDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediasession.BasePlayerMediaSession
import org.schabi.newpipe.player.mediasession.MediaSessionManager
import org.schabi.newpipe.player.mediasource.FailedMediaSource
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

/**
 * Base for the players, joining the common properties
 *
 */
abstract class BasePlayer(protected val context: Context) : Player.EventListener, PlaybackListener, ImageLoadingListener {

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onBroadcastReceived(intent)
        }
    }
    private val intentFilter: IntentFilter = IntentFilter()  // for setup receiver of this class and its subclass

    private val recordManager: HistoryRecordManager = HistoryRecordManager(context)

    private val loadControl: LoadControl = LoadController(context)
    private val renderFactory: RenderersFactory = DefaultRenderersFactory(context)

    private val progressUpdateReactor: SerialDisposable = SerialDisposable()
    private val databaseUpdateReactor: CompositeDisposable = CompositeDisposable()

    protected val trackSelector: CustomTrackSelector
    protected val dataSource: PlayerDataSource

    init {
        val userAgent = context.packageName
        Log.d(TAG, "BasePlayer: userAgent = $userAgent")
        val bandwidthMeter = DefaultBandwidthMeter()
        this.dataSource = PlayerDataSource(context, userAgent, bandwidthMeter)

        val trackSelectionFactory = PlayerHelper.getQualitySelector(context, bandwidthMeter)
        this.trackSelector = CustomTrackSelector(trackSelectionFactory)  // DefaultTrackSelector
        this.setupBroadcastReceiver(intentFilter)     // just filtering AudioManager.ACTION_AUDIO_BECOMING_NOISY
    }


    ///////////////////////////////////////////////////////////////////////////
    // Playback
    ///////////////////////////////////////////////////////////////////////////
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
    // Player
    ///////////////////////////////////////////////////////////////////////////
    var simpleExoPlayer: SimpleExoPlayer? = null
        protected set

    var audioReactor: AudioReactor? = null
        protected set

    var mediaSessionManager: MediaSessionManager? = null

    var isPrepared = false
        private set

    ///////////////////////////////////////////////////////////////////////////
    // States Implementation
    ///////////////////////////////////////////////////////////////////////////
    var currentState = STATE_PREFLIGHT    // store the player state
        protected set


    ///////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    ///////////////////////////////////////////////////////////////////////////
    private val progressReactor: Disposable
        get() = Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS.toLong(), TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ ignored -> triggerProgressUpdate() },
                        { error -> Log.e(TAG, "Progress update failure: ", error) })

    private val isCurrentWindowValid: Boolean
        get() = (simpleExoPlayer != null && simpleExoPlayer!!.duration >= 0 && simpleExoPlayer!!.currentPosition >= 0)

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
            if (simpleExoPlayer == null || !isLive) return false

            val currentTimeline = simpleExoPlayer!!.currentTimeline
            val currentWindowIndex = simpleExoPlayer!!.currentWindowIndex
            if (currentTimeline.isEmpty ||
                    currentWindowIndex < 0 ||
                    currentWindowIndex >= currentTimeline.windowCount) {
                return false
            }

            val timelineWindow = Timeline.Window()
            currentTimeline.getWindow(currentWindowIndex, timelineWindow)
            return timelineWindow.defaultPositionMs <= simpleExoPlayer!!.currentPosition
        }


    private val isLive: Boolean
        get() {
            if (simpleExoPlayer == null) return false
            try {
                return simpleExoPlayer!!.isCurrentWindowDynamic
            } catch (ignored: IndexOutOfBoundsException) {
                // Why would this even happen =(
                // But lets log it anyway. Save is save
                Log.d(TAG, "Could not update metadata: " + ignored.message)
                ignored.printStackTrace()
                return false
            }

        }

    val isPlaying: Boolean
        get() {
            if (simpleExoPlayer == null) return false
            val state = simpleExoPlayer!!.playbackState
            return (state == Player.STATE_READY || state == Player.STATE_BUFFERING) && simpleExoPlayer!!.playWhenReady
        }

    var repeatMode: Int
        @Player.RepeatMode
        get() = if (simpleExoPlayer == null) Player.REPEAT_MODE_OFF else simpleExoPlayer!!.repeatMode
        set(@Player.RepeatMode repeatMode) {
            if (simpleExoPlayer != null) simpleExoPlayer!!.repeatMode = repeatMode
        }

    val playbackParameters: PlaybackParameters
        get() {
            if (simpleExoPlayer == null) return PlaybackParameters.DEFAULT
            val parameters = simpleExoPlayer!!.playbackParameters
            return parameters ?: PlaybackParameters.DEFAULT
        }

    var playbackSpeed: Float
        get() = playbackParameters.speed
        set(speed) = setPlaybackParameters(speed, playbackPitch, playbackSkipSilence)

    val playbackPitch: Float
        get() = playbackParameters.pitch

    val playbackSkipSilence: Boolean
        get() = playbackParameters.skipSilence

    val isProgressLoopRunning: Boolean
        get() = progressUpdateReactor.get() != null

    /////////////////////////////////////////////////////////////////

    fun setup() {
        if (simpleExoPlayer == null) {
            initPlayer(/*playOnInit=*/true)
        }
        initListeners()
    }

    open fun initPlayer(playOnReady: Boolean) {
        Log.d(TAG, "initPlayer() called with: context = [$context]")

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderFactory, trackSelector, loadControl)
        simpleExoPlayer!!.addListener(this)
        simpleExoPlayer!!.playWhenReady = playOnReady
        simpleExoPlayer!!.setSeekParameters(PlayerHelper.getSeekParameters(context))

        audioReactor = AudioReactor(context, simpleExoPlayer!!)
        mediaSessionManager = MediaSessionManager(context, simpleExoPlayer!!, BasePlayerMediaSession(this))

        registerBroadcastReceiver()
    }

    open fun initListeners() {}

    open fun handleIntent(intent: Intent?) {
        Log.d(TAG, "handleIntent() called with: intent = [$intent]")
        if (intent == null) return

        // Resolve play queue
        if (!intent.hasExtra(PLAY_QUEUE_KEY)) return
        val intentCacheKey = intent.getStringExtra(PLAY_QUEUE_KEY)
        val queue = SerializedCache.instance.take(intentCacheKey, PlayQueue::class.java) ?: return

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
        val playbackSkipSilence = intent.getBooleanExtra(PLAYBACK_SKIP_SILENCE, playbackSkipSilence)

        // Good to go...
        initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, playbackSkipSilence, /*playOnInit=*/true)
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
        playQueue!!.initialize()
        playbackManager?.dispose()
        playbackManager = MediaSourceManager(this, playQueue!!)

        playQueueAdapter?.dispose()
        playQueueAdapter = PlayQueueAdapter(context, playQueue!!)
    }

    private fun destroyPlayer() {
        Log.d(TAG, "destroyPlayer() called")
        if (simpleExoPlayer != null) {
            simpleExoPlayer!!.removeListener(this)
            simpleExoPlayer!!.stop()
            simpleExoPlayer!!.release()
        }
        if (isProgressLoopRunning) stopProgressLoop()
        playQueue?.dispose()
        audioReactor?.dispose()
        playbackManager?.dispose()
        mediaSessionManager?.dispose()

        playQueueAdapter?.let {
            it.unsetSelectedListener()
            it.dispose()
        }
    }

    open fun destroy() {
        Log.d(TAG, "destroy() called")
        destroyPlayer()
        unregisterBroadcastReceiver()

        databaseUpdateReactor.clear()
        progressUpdateReactor.set(null)

        simpleExoPlayer = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    ///////////////////////////////////////////////////////////////////////////

    private fun initThumbnail(url: String?) {
        Log.d(TAG, "Thumbnail - initThumbnail() called")
        if (url == null || url.isEmpty()) return
        ImageLoader.getInstance().resume()
        ImageLoader.getInstance().loadImage(url, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, this)
    }

    //////////////////////////////////////////////////////////////////////////
    // ImageLoadingListener
    //////////////////////////////////////////////////////////////////////////
    override fun onLoadingStarted(imageUri: String, view: View?) {
        Log.d(TAG, "Thumbnail - onLoadingStarted() called on: imageUri = [$imageUri], view = [$view]")
    }

    override fun onLoadingFailed(imageUri: String, view: View?, failReason: FailReason) {
        Log.e(TAG, "Thumbnail - onLoadingFailed() called on imageUri = [$imageUri]", failReason.cause)
        currentThumbnail = null
    }

    override fun onLoadingComplete(imageUri: String, view: View?, loadedImage: Bitmap?) {

        Log.d(TAG, "Thumbnail - onLoadingComplete() called with: imageUri = [$imageUri], view = [$view], loadedImage = [$loadedImage]")
        currentThumbnail = loadedImage
    }

    override fun onLoadingCancelled(imageUri: String, view: View) {

        Log.d(TAG, "Thumbnail - onLoadingCancelled() called with: imageUri = [$imageUri], view = [$view]")
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

    private fun registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver()
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (unregisteredException: IllegalArgumentException) {
            Log.w(TAG, "Broadcast receiver already unregistered (${unregisteredException.message})")
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // States Implementation
    ///////////////////////////////////////////////////////////////////////////

    open fun changeState(state: Int) {
        Log.d(TAG, "changeState() called with: state = [$state]")
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
        Log.d(TAG, "onBlocked() called")
        if (!isProgressLoopRunning) startProgressLoop()
    }

    open fun onPlaying() {
        Log.d(TAG, "onPlaying() called")
        if (!isProgressLoopRunning) startProgressLoop()
    }

    open fun onBuffering() {
        Log.d(TAG, "onBuffering() called")
    }

    open fun onPaused() {
        Log.d(TAG, "onPaused() called")
        if (isProgressLoopRunning) stopProgressLoop()
    }

    open fun onPausedSeek() {
        Log.d(TAG, "onPausedSeek() called")
    }

    open fun onCompleted() {
        Log.d(TAG, "onCompleted() called")
        if (playQueue!!.index < playQueue!!.size() - 1) playQueue!!.offsetIndex(+1)
        if (isProgressLoopRunning) stopProgressLoop()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    ///////////////////////////////////////////////////////////////////////////

    fun onRepeatClicked() {
        Log.d(TAG, "onRepeatClicked() called")

        val mode: Int = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }

        repeatMode = mode
        Log.d(TAG, "onRepeatClicked() currentRepeatMode = $repeatMode")
    }

    open fun onShuffleClicked() {
        Log.d(TAG, "onShuffleClicked() called")

        if (simpleExoPlayer == null) return
        simpleExoPlayer!!.shuffleModeEnabled = !simpleExoPlayer!!.shuffleModeEnabled
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
        if (simpleExoPlayer == null) return
        onUpdateProgress(
                Math.max(simpleExoPlayer!!.currentPosition.toInt(), 0),
                simpleExoPlayer!!.duration.toInt(),
                simpleExoPlayer!!.bufferedPercentage
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interface Player.EventListener
    ///////////////////////////////////////////////////////////////////////////

    override fun onTimelineChanged(timeline: Timeline, manifest: Any?,
                                   @Player.TimelineChangeReason reason: Int) {

        Log.d(TAG, "ExoPlayer - onTimelineChanged() called with " +
                "${if (manifest == null) "no manifest" else "available manifest"}, " +
                "timeline size = [${timeline.windowCount}], reason = [$reason]")

        maybeUpdateCurrentMetadata()
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {

        Log.d(TAG, "ExoPlayer - onTracksChanged(), track group size = ${trackGroups.length}")

        maybeUpdateCurrentMetadata()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {

        Log.d(TAG, "ExoPlayer - playbackParameters(), " +
                "speed: " + playbackParameters.speed + ", " +
                "pitch: " + playbackParameters.pitch)
    }

    override fun onLoadingChanged(isLoading: Boolean) {

        Log.d(TAG, "ExoPlayer - onLoadingChanged() called with: isLoading = [$isLoading]")

        if (!isLoading && currentState == STATE_PAUSED && isProgressLoopRunning) {
            stopProgressLoop()
        } else if (isLoading && !isProgressLoopRunning) {
            startProgressLoop()
        }

        maybeUpdateCurrentMetadata()
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {

        Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: playWhenReady = [$playWhenReady], playbackState = [$playbackState]")

        if (currentState == STATE_PAUSED_SEEK) {
            Log.d(TAG, "ExoPlayer - onPlayerStateChanged() is currently blocked")
            return
        }

        when (playbackState) {
            // 1
            Player.STATE_IDLE -> isPrepared = false
            // 2
            Player.STATE_BUFFERING -> if (isPrepared) {
                changeState(STATE_BUFFERING)
            }
            //3
            Player.STATE_READY -> {
                maybeUpdateCurrentMetadata()
                maybeCorrectSeekPosition()
                if (!isPrepared) {
                    isPrepared = true
                    onPrepared(playWhenReady)
                } else {
                    changeState(if (playWhenReady) STATE_PLAYING else STATE_PAUSED)
                }
            }
            // 4
            Player.STATE_ENDED -> {
                changeState(STATE_COMPLETED)
                isPrepared = false
            }
        }
    }

    private fun maybeCorrectSeekPosition() {
        if (playQueue == null || simpleExoPlayer == null || currentMetadata == null || playQueue!!.item == null) return

        val currentInfo = currentMetadata!!.metadata
        val presetStartPositionMillis = currentInfo.startPosition * 1000
        if (presetStartPositionMillis > 0L) {
            Log.d(TAG, "Playback - Seeking to preset start position=[$presetStartPositionMillis]")
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
        Log.d(TAG, "ExoPlayer - onPlayerError() called with: error = [$error]: error.type = ${error.type}")

        errorToast?.cancel()
        errorToast = null

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

            ExoPlaybackException.TYPE_RENDERER -> {
                showUnrecoverableError(error)
                onPlaybackShutdown()
            }
        }
    }

    private fun processSourceError(error: IOException) {
        if (simpleExoPlayer == null || playQueue == null) return

        setRecovery()

        val cause = error.cause
        when {
            error is BehindLiveWindowException -> reload()
            cause is UnknownHostException -> playQueue!!.error(/*isNetworkProblem=*/true)
            isCurrentWindowValid -> playQueue!!.error(/*isTransitioningToBadStream=*/true)
            cause is FailedMediaSource.MediaSourceResolutionException -> playQueue!!.error(/*recoverableWithNoAvailableStream=*/false)
            cause is FailedMediaSource.StreamInfoLoadException -> playQueue!!.error(/*recoverableIfLoadFailsWhenNetworkIsFine=*/false)
            else -> playQueue!!.error(/*noIdeaWhatHappenedAndLetUserChooseWhatToDo=*/true)
        }
    }

    override fun onPositionDiscontinuity(@Player.DiscontinuityReason reason: Int) {

        Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with reason = [$reason]")
        if (playQueue == null) return

        // Refresh the playback if there is a transition to the next video
        val newWindowIndex = simpleExoPlayer!!.currentWindowIndex
        when (reason) {
            DISCONTINUITY_REASON_PERIOD_TRANSITION -> {
                // When simpleExoPlayer is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (repeatMode == Player.REPEAT_MODE_ONE && newWindowIndex == playQueue!!.index) {
                    registerView()
                } else if (playQueue!!.index != newWindowIndex) {
                    playQueue!!.index = newWindowIndex
                }
            }

            DISCONTINUITY_REASON_SEEK,
            DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
            DISCONTINUITY_REASON_INTERNAL -> if (playQueue!!.index != newWindowIndex) {
                playQueue!!.index = newWindowIndex
            }

            DISCONTINUITY_REASON_AD_INSERTION -> { /* no-op */
            }
        }

        maybeUpdateCurrentMetadata()
    }

    override fun onRepeatModeChanged(@Player.RepeatMode reason: Int) {

        Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: mode = [$reason]")
        // no-op for now
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: mode = [$shuffleModeEnabled]")

        if (playQueue == null) return

        if (shuffleModeEnabled) {
            playQueue!!.shuffle()
        } else {
            playQueue!!.unshuffle()
        }
    }

    override fun onSeekProcessed() {
        Log.d(TAG, "ExoPlayer - onSeekProcessed() called")
        // no-op for now
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playback Listener
    ///////////////////////////////////////////////////////////////////////////
    override fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (simpleExoPlayer == null || isLive || !isPlaying) return false

        val currentPositionMillis = simpleExoPlayer!!.currentPosition
        val currentDurationMillis = simpleExoPlayer!!.duration
        return currentDurationMillis - currentPositionMillis < timeToEndMillis
    }

    override fun onPlaybackBlock() {
        if (simpleExoPlayer == null) return
        Log.d(TAG, "Playback - onPlaybackBlock() called")

        currentItem = null
        currentMetadata = null
        simpleExoPlayer!!.stop()
        isPrepared = false

        changeState(STATE_BLOCKED)
    }

    override fun onPlaybackUnblock(mediaSource: MediaSource) {
        if (simpleExoPlayer == null) return
        Log.d(TAG, "Playback - onPlaybackUnblock() called")

        if (currentState == STATE_BLOCKED) changeState(STATE_BUFFERING)

        simpleExoPlayer!!.prepare(mediaSource)
    }

    override fun onPlaybackSynchronize(item: PlayQueueItem) {

        Log.d(TAG, "Playback - onPlaybackSynchronize() called with item=[${item.title}], url=[${item.url}]")
        if (simpleExoPlayer == null || playQueue == null) return

        val onPlaybackInitial = currentItem == null
        val hasPlayQueueItemChanged = currentItem != item

        val currentPlayQueueIndex = playQueue!!.indexOf(item)
        val currentPlaylistIndex = simpleExoPlayer!!.currentWindowIndex
        val currentPlaylistSize = simpleExoPlayer!!.currentTimeline.windowCount

        // If nothing to synchronize
        if (!hasPlayQueueItemChanged) return

        currentItem = item

        when {

            currentPlayQueueIndex != playQueue!!.index -> // Check if on wrong window
                Log.e(TAG, "Playback - Play Queue may be desynchronized: item index=[$currentPlayQueueIndex], queue index=[${playQueue!!.index}]")
            // no-op for now

            currentPlaylistSize in 1..currentPlayQueueIndex || currentPlayQueueIndex < 0 ->  // Check if bad seek position
                Log.e(TAG, "Playback - Trying to seek to invalid index=[$currentPlayQueueIndex] with playlist length=[$currentPlaylistSize]")
            // no-op for now

            currentPlaylistIndex != currentPlayQueueIndex || onPlaybackInitial || !isPlaying -> {
                Log.d(TAG, "Playback - Rewinding to correct index=[$currentPlayQueueIndex], getTabFrom=[$currentPlaylistIndex], size=[$currentPlaylistSize].")

                if (item.recoveryPosition != PlayQueueItem.RECOVERY_UNSET) {
                    simpleExoPlayer!!.seekTo(currentPlayQueueIndex, item.recoveryPosition)
                    playQueue!!.unsetRecovery(currentPlayQueueIndex)
                } else {
                    simpleExoPlayer!!.seekToDefaultPosition(currentPlayQueueIndex)
                }
            }
        }
    }

    protected open fun onMetadataChanged(tag: MediaSourceTag) {
        val info = tag.metadata

        Log.d(TAG, "Playback - onMetadataChanged() called, playing: ${info.name}")

        initThumbnail(info.thumbnailUrl)
        registerView()
    }

    override fun onPlaybackShutdown() {
        Log.d(TAG, "Shutting down...")
        destroy()
    }

    /**
     * sourceOf() function will be overridden in component
     */

    ///////////////////////////////////////////////////////////////////////////
    // General Player
    ///////////////////////////////////////////////////////////////////////////
    private fun showStreamError(exception: Exception) {
        Log.d(TAG, "showStreamError(): ${exception.message}")
        exception.printStackTrace()

        errorToast?.cancel()
        errorToast = Toast.makeText(context, R.string.player_stream_failure, Toast.LENGTH_SHORT)
        errorToast?.show()
    }

    private fun showRecoverableError(exception: Exception) {
        Log.d(TAG, "showRecoverableError(): ${exception.message}")
        exception.printStackTrace()

        errorToast?.cancel()
        errorToast = Toast.makeText(context, R.string.player_recoverable_failure, Toast.LENGTH_SHORT)
        errorToast?.show()
    }

    private fun showUnrecoverableError(exception: Exception) {
        Log.d(TAG, "showUnrecoverableError(): ${exception.message}")
        exception.printStackTrace()

        errorToast?.cancel()
        errorToast = Toast.makeText(context, R.string.player_unrecoverable_failure, Toast.LENGTH_SHORT)
        errorToast?.show()
    }

    open fun onPrepared(playWhenReady: Boolean) {
        Log.d(TAG, "onPrepared() called with: playWhenReady = [$playWhenReady]")
        if (playWhenReady) audioReactor!!.requestAudioFocus()
        changeState(if (playWhenReady) STATE_PLAYING else STATE_PAUSED)
    }

    fun onPlay() {
        Log.d(TAG, "onPlay() called")
        if (audioReactor == null || playQueue == null || simpleExoPlayer == null) return

        audioReactor!!.requestAudioFocus()

        if (currentState == STATE_COMPLETED) {
            if (playQueue!!.index == 0) {
                seekToDefault()
            } else {
                playQueue!!.index = 0
            }
        }

        simpleExoPlayer!!.playWhenReady = true
    }

    fun onPause() {
        Log.d(TAG, "onPause() called")
        if (audioReactor == null || simpleExoPlayer == null) return

        audioReactor!!.abandonAudioFocus()
        simpleExoPlayer!!.playWhenReady = false
    }

    fun onPlayPause() {
        Log.d(TAG, "onPlayPause() called")

        if (!isPlaying) {
            onPlay()
        } else {
            onPause()
        }
    }

    open fun onFastRewind() {
        Log.d(TAG, "onFastRewind() called")
        seekBy((-FAST_REWIND_AMOUNT_MILLIS).toLong())
    }

    open fun onFastForward() {
        Log.d(TAG, "onFastForward() called")
        seekBy(FAST_FORWARD_AMOUNT_MILLIS.toLong())
    }

    open fun onPlayPrevious() {
        Log.d(TAG, "onPlayPrevious() called")
        if (simpleExoPlayer == null || playQueue == null) return

        /**If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track.
         * Also restart the track if the current track is the first in a queue.
         * else play the previous one.
         * */
        if (simpleExoPlayer!!.currentPosition > PLAY_PREV_ACTIVATION_LIMIT_MILLIS || playQueue!!.index == 0) {
            seekToDefault()
            playQueue!!.offsetIndex(0)
        } else {
            savePlaybackState()
            playQueue!!.offsetIndex(-1)
        }
    }

    open fun onPlayNext() {
        if (playQueue == null) return
        Log.d(TAG, "onPlayNext() called")

        savePlaybackState()
        playQueue!!.offsetIndex(+1)
    }

    fun onSelected(item: PlayQueueItem) {
        if (playQueue == null || simpleExoPlayer == null) return

        val index = playQueue!!.indexOf(item)
        if (index == -1) return

        if (playQueue!!.index == index && simpleExoPlayer!!.currentWindowIndex == index) {
            seekToDefault()
        } else {
            savePlaybackState()
        }
        playQueue!!.index = index
    }

    fun seekTo(positionMillis: Long) {
        Log.d(TAG, "seekBy() called with: position = [$positionMillis]")
        simpleExoPlayer?.seekTo(positionMillis)
    }

    private fun seekBy(offsetMillis: Long) {
        Log.d(TAG, "seekBy() called with: offsetMillis = [$offsetMillis]")
        if (simpleExoPlayer == null) return

        seekTo(simpleExoPlayer!!.currentPosition + offsetMillis)
    }

    fun seekToDefault() {
        if (simpleExoPlayer != null) {
            simpleExoPlayer!!.seekToDefaultPosition()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun registerView() {
        if (currentMetadata == null) return
        val currentInfo = currentMetadata!!.metadata
        val d = recordManager.onViewed(currentInfo).onErrorComplete()
                .subscribe(
                        {/* successful */ ignored -> },
                        { error -> Log.e(TAG, "Player onViewed() failure: ", error) }
                )
        databaseUpdateReactor.add(d)
    }

    protected fun reload() {

        playbackManager?.dispose()

        if (playQueue != null) {
            playbackManager = MediaSourceManager(this, playQueue!!)
        }
    }

    private fun savePlaybackState(info: StreamInfo?, progress: Long) {
        if (info == null) return
        val d = recordManager.saveStreamState(info, progress)
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorComplete()
                .subscribe(
                        {/* successful */ ignored -> },
                        { error -> Log.e(TAG, "savePlaybackState() failure: ", error) }
                )
        databaseUpdateReactor.add(d)
    }

    private fun savePlaybackState() {
        if (simpleExoPlayer == null || currentMetadata == null) return

        val currentInfo = currentMetadata!!.metadata

        if (simpleExoPlayer!!.currentPosition > RECOVERY_SKIP_THRESHOLD_MILLIS &&
                simpleExoPlayer!!.currentPosition < simpleExoPlayer!!.duration - RECOVERY_SKIP_THRESHOLD_MILLIS) {
            savePlaybackState(currentInfo, simpleExoPlayer!!.currentPosition)
        }
    }

    private fun maybeUpdateCurrentMetadata() {
        if (simpleExoPlayer == null) return

        val metadata: MediaSourceTag?
        try {
            metadata = simpleExoPlayer!!.currentTag as MediaSourceTag?
        } catch (error: IndexOutOfBoundsException) {
            Log.d(TAG, " updating metadata error: ${error.message}")
            error.printStackTrace()
            return
        } catch (error: ClassCastException) {
            Log.d(TAG, "updating metadata classCasting error: ${error.message}")
            error.printStackTrace()
            return
        }

        if (metadata == null) return
        maybeAutoQueueNextStream(metadata)

        if (currentMetadata == metadata) return
        currentMetadata = metadata
        onMetadataChanged(metadata)
    }

    private fun maybeAutoQueueNextStream(currentMetadata: MediaSourceTag) {
        val predication = playQueue == null ||
                playQueue!!.index != playQueue!!.size() - 1 ||
                repeatMode != Player.REPEAT_MODE_OFF ||
                !PlayerHelper.isAutoQueueEnabled(context)

        if (predication) return

        // auto queue when starting playback on the last item when not repeating
        val autoQueue = PlayerHelper.autoQueueOf(currentMetadata.metadata, playQueue!!.streams!!)
        if (autoQueue != null) playQueue!!.append(autoQueue.streams!!)
    }

    fun setPlaybackParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        simpleExoPlayer!!.playbackParameters = PlaybackParameters(speed, pitch, skipSilence)
    }

    fun setRecovery() {
        if (playQueue == null || simpleExoPlayer == null) return

        val queueIndex = playQueue!!.index
        val windowIndex = simpleExoPlayer!!.currentPosition

        if (windowIndex > 0 && windowIndex <= simpleExoPlayer!!.duration) {
            setRecovery(queueIndex, windowIndex)
        }
    }

    private fun setRecovery(queueIndex: Int, windowIndex: Long) {
        if (playQueue!!.size() <= queueIndex) return

        Log.d(TAG, "Setting recovery, queue: $queueIndex, pos: $windowIndex")
        playQueue!!.setRecovery(queueIndex, windowIndex)
    }

    companion object {
        const val TAG = "BasePlayer"

        ///////////////////////////////////////////////////////////////////////////
        // Intent
        ///////////////////////////////////////////////////////////////////////////
        const val REPEAT_MODE = "repeat_mode"
        const val PLAYBACK_PITCH = "playback_pitch"
        const val PLAYBACK_SPEED = "playback_speed"
        const val PLAYBACK_SKIP_SILENCE = "playback_skip_silence"
        const val PLAYBACK_QUALITY = "playback_quality"
        const val PLAY_QUEUE_KEY = "play_queue_key"
        const val APPEND_ONLY = "append_only"
        const val SELECT_ON_APPEND = "select_on_append"

        ///////////////////////////////////////////////////////////////////////////
        // Playback
        ///////////////////////////////////////////////////////////////////////////
        val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        ///////////////////////////////////////////////////////////////////////////
        // Player
        ///////////////////////////////////////////////////////////////////////////
        protected const val FAST_FORWARD_REWIND_AMOUNT_MILLIS = 10000 // 10 Seconds
        protected const val FAST_REWIND_AMOUNT_MILLIS = 15000         // 15 seconds
        protected const val FAST_FORWARD_AMOUNT_MILLIS = 30000         // 30 seconds
        protected const val PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000 // 5 seconds
        protected const val PROGRESS_LOOP_INTERVAL_MILLIS = 500
        protected const val RECOVERY_SKIP_THRESHOLD_MILLIS = 3000 // 3 seconds

        ///////////////////////////////////////////////////////////////////////////
        // States Implementation
        ///////////////////////////////////////////////////////////////////////////
        const val STATE_PREFLIGHT = -1
        const val STATE_BLOCKED = 123
        const val STATE_PLAYING = 124
        const val STATE_BUFFERING = 125
        const val STATE_PAUSED = 126
        const val STATE_PAUSED_SEEK = 127
        const val STATE_COMPLETED = 128
    }
}

