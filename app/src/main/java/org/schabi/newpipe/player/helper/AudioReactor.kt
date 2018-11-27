package org.schabi.newpipe.player.helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.decoder.DecoderCounters

class AudioReactor(private val context: Context,
                   private val player: SimpleExoPlayer) : AudioManager.OnAudioFocusChangeListener, AudioRendererEventListener {
    private val audioManager: AudioManager

    private val request: AudioFocusRequest?

    var volume: Int
        get() = audioManager.getStreamVolume(STREAM_TYPE)
        set(volume) = audioManager.setStreamVolume(STREAM_TYPE, volume, 0)

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(STREAM_TYPE)

    init {
        this.audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        player.addAudioDebugListener(this)

        request = if (SHOULD_BUILD_FOCUS_REQUEST) {
            AudioFocusRequest.Builder(FOCUS_GAIN_TYPE)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()
        } else {
            null
        }
    }

    fun dispose() {
        abandonAudioFocus()
        player.removeAudioDebugListener(this)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Audio Manager
    ///////////////////////////////////////////////////////////////////////////

    fun requestAudioFocus() {
        if (SHOULD_BUILD_FOCUS_REQUEST) {
            audioManager.requestAudioFocus(request!!)
        } else {
            audioManager.requestAudioFocus(this, STREAM_TYPE, FOCUS_GAIN_TYPE)
        }
    }

    fun abandonAudioFocus() {
        if (SHOULD_BUILD_FOCUS_REQUEST) {
            audioManager.abandonAudioFocusRequest(request!!)
        } else {
            audioManager.abandonAudioFocus(this)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // AudioFocus
    ///////////////////////////////////////////////////////////////////////////

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange() called with: focusChange = [$focusChange]")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> onAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onAudioFocusLossCanDuck()
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onAudioFocusLoss()
        }
    }

    private fun onAudioFocusGain() {
        Log.d(TAG, "onAudioFocusGain() called")
        player.volume = DUCK_AUDIO_TO
        animateAudio(DUCK_AUDIO_TO, 1f)

        if (PlayerHelper.isResumeAfterAudioFocusGain(context)) {
            player.playWhenReady = true
        }
    }

    private fun onAudioFocusLoss() {
        Log.d(TAG, "onAudioFocusLoss() called")
        player.playWhenReady = false
    }

    private fun onAudioFocusLossCanDuck() {
        Log.d(TAG, "onAudioFocusLossCanDuck() called")
        // Set the volume to 1/10 on ducking
        animateAudio(player.volume, DUCK_AUDIO_TO)
    }

    private fun animateAudio(from: Float, to: Float) {
        val valueAnimator = ValueAnimator()
        valueAnimator.setFloatValues(from, to)
        valueAnimator.duration = AudioReactor.DUCK_DURATION.toLong()
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                player.volume = from
            }

            override fun onAnimationCancel(animation: Animator) {
                player.volume = to
            }

            override fun onAnimationEnd(animation: Animator) {
                player.volume = to
            }
        })
        valueAnimator.addUpdateListener { animation -> player.volume = animation.animatedValue as Float }
        valueAnimator.start()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Audio Processing
    ///////////////////////////////////////////////////////////////////////////

    override fun onAudioSessionId(i: Int) {
        if (!PlayerHelper.isUsingDSP(context)) return

        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, i)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        context.sendBroadcast(intent)
    }

    override fun onAudioEnabled(decoderCounters: DecoderCounters) {}

    override fun onAudioDecoderInitialized(s: String, l: Long, l1: Long) {}

    override fun onAudioInputFormatChanged(format: Format) {}

    override fun onAudioSinkUnderrun(bufferSize: Int,
                                     bufferSizeMs: Long,
                                     elapsedSinceLastFeedMs: Long) {
    }

    override fun onAudioDisabled(decoderCounters: DecoderCounters) {}

    companion object {

        private const val TAG = "AudioFocusReactor"

        private val SHOULD_BUILD_FOCUS_REQUEST = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        private const val DUCK_DURATION = 1500
        private const val DUCK_AUDIO_TO = .2f

        private const val FOCUS_GAIN_TYPE = AudioManager.AUDIOFOCUS_GAIN
        private const val STREAM_TYPE = AudioManager.STREAM_MUSIC
    }
}
