package org.schabi.newpipe.player.helper

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.util.SliderStrategy

class PlaybackParameterDialog : androidx.fragment.app.DialogFragment() {

    private var callback: Callback? = null

    private val strategy =
            SliderStrategy.Quadratic(
                    MINIMUM_PLAYBACK_VALUE,
                    MAXIMUM_PLAYBACK_VALUE,
                    /*centerAt=*/1.00,
                    /*sliderGranularity=*/10000)

    private var initialTempo = DEFAULT_TEMPO
    private var initialPitch = DEFAULT_PITCH
    private var initialSkipSilence = DEFAULT_SKIP_SILENCE

    private var tempo = DEFAULT_TEMPO
    private var pitch = DEFAULT_PITCH
    private var currentStepSize = DEFAULT_STEP

    private var tempoSlider: SeekBar? = null
    private var tempoCurrentText: TextView? = null
    private var tempoStepDownText: TextView? = null
    private var tempoStepUpText: TextView? = null

    private var pitchSlider: SeekBar? = null
    private var pitchCurrentText: TextView? = null
    private var pitchStepDownText: TextView? = null
    private var pitchStepUpText: TextView? = null

    private var unhookingCheckbox: CheckBox? = null
    private var skipSilenceCheckbox: CheckBox? = null

    ///////////////////////////////////////////////////////////////////////////
    // Sliders
    ///////////////////////////////////////////////////////////////////////////

    private val onTempoChangedListener: SeekBar.OnSeekBarChangeListener
        get() = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val currentTempo = strategy.valueOf(progress)
                if (fromUser) {
                    onTempoSliderUpdated(currentTempo)
                    setCurrentPlaybackParameters()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}  // Do Nothing.

            override fun onStopTrackingTouch(seekBar: SeekBar) {}  // Do Nothing.
        }

    private val onPitchChangedListener: SeekBar.OnSeekBarChangeListener
        get() = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val currentPitch = strategy.valueOf(progress)
                if (fromUser) { // this change is first in chain
                    onPitchSliderUpdated(currentPitch)
                    setCurrentPlaybackParameters()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {} // Do Nothing.

            override fun onStopTrackingTouch(seekBar: SeekBar) {}  // Do Nothing.
        }

    ///////////////////////////////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////////////////////////////
    private val currentTempo: Double
        get() = if (tempoSlider == null) tempo else strategy.valueOf(tempoSlider!!.progress)

    private val currentPitch: Double
        get() = if (pitchSlider == null) pitch else strategy.valueOf(pitchSlider!!.progress)

    private val currentSkipSilence: Boolean
        get() = skipSilenceCheckbox != null && skipSilenceCheckbox!!.isChecked

    interface Callback {
        fun onPlaybackParameterChanged(playbackTempo: Float,
                                       playbackPitch: Float,
                                       playbackSkipSilence: Boolean)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context != null && context is Callback) {
            callback = context
        } else {
            dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            initialTempo = savedInstanceState.getDouble(INITIAL_TEMPO_KEY, DEFAULT_TEMPO)
            initialPitch = savedInstanceState.getDouble(INITIAL_PITCH_KEY, DEFAULT_PITCH)

            tempo = savedInstanceState.getDouble(TEMPO_KEY, DEFAULT_TEMPO)
            pitch = savedInstanceState.getDouble(PITCH_KEY, DEFAULT_PITCH)
            currentStepSize = savedInstanceState.getDouble(STEP_SIZE_KEY, DEFAULT_STEP)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble(INITIAL_TEMPO_KEY, initialTempo)
        outState.putDouble(INITIAL_PITCH_KEY, initialPitch)

        outState.putDouble(TEMPO_KEY, currentTempo)
        outState.putDouble(PITCH_KEY, currentPitch)
        outState.putDouble(STEP_SIZE_KEY, currentStepSize)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Dialog
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(context, R.layout.dialog_playback_parameter, null)
        setupControlViews(view)

        val dialogBuilder = AlertDialog.Builder(requireActivity())
                .setTitle(R.string.playback_speed_control)
                .setView(view)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel) { dialogInterface, i -> setPlaybackParameters(initialTempo, initialPitch, initialSkipSilence) }
                .setNeutralButton(R.string.playback_reset) { dialogInterface, i -> setPlaybackParameters(DEFAULT_TEMPO, DEFAULT_PITCH, DEFAULT_SKIP_SILENCE) }
                .setPositiveButton(R.string.finish) { dialogInterface, i -> setCurrentPlaybackParameters() }

        return dialogBuilder.create()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Control Views
    ///////////////////////////////////////////////////////////////////////////

    private fun setupControlViews(rootView: View) {
        setupHookingControl(rootView)
        setupSkipSilenceControl(rootView)

        setupTempoControl(rootView)
        setupPitchControl(rootView)

        changeStepSize(currentStepSize)
        setupStepSizeSelector(rootView)
    }

    private fun setupHookingControl(rootView: View) {
        unhookingCheckbox = rootView.findViewById(R.id.unhookCheckbox)
        unhookingCheckbox?.let {
            it.isChecked = pitch != tempo
            it.setOnCheckedChangeListener { compoundButton, isChecked ->
                if (isChecked) return@setOnCheckedChangeListener
                // When unchecked, slide back to the minimum of current tempo or pitch
                val minimum = Math.min(currentPitch, currentTempo)
                setSliders(minimum)
                setCurrentPlaybackParameters()
            }
        }
    }

    private fun setupSkipSilenceControl(rootView: View) {
        skipSilenceCheckbox = rootView.findViewById(R.id.skipSilenceCheckbox)
        skipSilenceCheckbox?.let {
            it.isChecked = initialSkipSilence
            it.setOnCheckedChangeListener { compoundButton, isChecked -> setCurrentPlaybackParameters() }
        }
    }

    private fun setupTempoControl(rootView: View) {
        tempoSlider = rootView.findViewById(R.id.tempoSeekbar)
        val tempoMinimumText = rootView.findViewById<TextView>(R.id.tempoMinimumText)
        val tempoMaximumText = rootView.findViewById<TextView>(R.id.tempoMaximumText)
        tempoCurrentText = rootView.findViewById(R.id.tempoCurrentText)
        tempoStepUpText = rootView.findViewById(R.id.tempoStepUp)
        tempoStepDownText = rootView.findViewById(R.id.tempoStepDown)

        tempoCurrentText?.text = PlayerHelper.formatSpeed(tempo)
        tempoMaximumText?.text = PlayerHelper.formatSpeed(MAXIMUM_PLAYBACK_VALUE)
        tempoMinimumText?.text = PlayerHelper.formatSpeed(MINIMUM_PLAYBACK_VALUE)

        tempoSlider?.let {
            it.max = strategy.progressOf(MAXIMUM_PLAYBACK_VALUE)
            it.progress = strategy.progressOf(tempo)
            it.setOnSeekBarChangeListener(onTempoChangedListener)
        }
    }

    private fun setupPitchControl(rootView: View) {
        pitchSlider = rootView.findViewById(R.id.pitchSeekbar)
        val pitchMinimumText = rootView.findViewById<TextView>(R.id.pitchMinimumText)
        val pitchMaximumText = rootView.findViewById<TextView>(R.id.pitchMaximumText)
        pitchCurrentText = rootView.findViewById(R.id.pitchCurrentText)
        pitchStepDownText = rootView.findViewById(R.id.pitchStepDown)
        pitchStepUpText = rootView.findViewById(R.id.pitchStepUp)

        pitchCurrentText?.text = PlayerHelper.formatPitch(pitch)
        pitchMaximumText?.text = PlayerHelper.formatPitch(MAXIMUM_PLAYBACK_VALUE)
        pitchMinimumText?.text = PlayerHelper.formatPitch(MINIMUM_PLAYBACK_VALUE)

        pitchSlider?.let {
            it.max = strategy.progressOf(MAXIMUM_PLAYBACK_VALUE)
            it.progress = strategy.progressOf(pitch)
            it.setOnSeekBarChangeListener(onPitchChangedListener)

        }
    }

    private fun setupStepSizeSelector(rootView: View) {
        val stepSizeOnePercentText = rootView.findViewById<TextView>(R.id.stepSizeOnePercent)
        val stepSizeFivePercentText = rootView.findViewById<TextView>(R.id.stepSizeFivePercent)
        val stepSizeTenPercentText = rootView.findViewById<TextView>(R.id.stepSizeTenPercent)
        val stepSizeTwentyFivePercentText = rootView.findViewById<TextView>(R.id.stepSizeTwentyFivePercent)
        val stepSizeOneHundredPercentText = rootView.findViewById<TextView>(R.id.stepSizeOneHundredPercent)

        if (stepSizeOnePercentText != null) {
            stepSizeOnePercentText.text = getPercentString(STEP_ONE_PERCENT_VALUE)
            stepSizeOnePercentText.setOnClickListener { view -> changeStepSize(STEP_ONE_PERCENT_VALUE) }
        }

        if (stepSizeFivePercentText != null) {
            stepSizeFivePercentText.text = getPercentString(STEP_FIVE_PERCENT_VALUE)
            stepSizeFivePercentText.setOnClickListener { view -> changeStepSize(STEP_FIVE_PERCENT_VALUE) }
        }

        if (stepSizeTenPercentText != null) {
            stepSizeTenPercentText.text = getPercentString(STEP_TEN_PERCENT_VALUE)
            stepSizeTenPercentText.setOnClickListener { view -> changeStepSize(STEP_TEN_PERCENT_VALUE) }
        }

        if (stepSizeTwentyFivePercentText != null) {
            stepSizeTwentyFivePercentText.text = getPercentString(STEP_TWENTY_FIVE_PERCENT_VALUE)
            stepSizeTwentyFivePercentText.setOnClickListener { view -> changeStepSize(STEP_TWENTY_FIVE_PERCENT_VALUE) }
        }

        if (stepSizeOneHundredPercentText != null) {
            stepSizeOneHundredPercentText.text = getPercentString(STEP_ONE_HUNDRED_PERCENT_VALUE)
            stepSizeOneHundredPercentText.setOnClickListener { view -> changeStepSize(STEP_ONE_HUNDRED_PERCENT_VALUE) }
        }
    }

    private fun changeStepSize(stepSize: Double) {
        this.currentStepSize = stepSize

        if (tempoStepUpText != null) {
            tempoStepUpText!!.text = getStepUpPercentString(stepSize)
            tempoStepUpText!!.setOnClickListener { view ->
                onTempoSliderUpdated(currentTempo + stepSize)
                setCurrentPlaybackParameters()
            }
        }

        if (tempoStepDownText != null) {
            tempoStepDownText!!.text = getStepDownPercentString(stepSize)
            tempoStepDownText!!.setOnClickListener { view ->
                onTempoSliderUpdated(currentTempo - stepSize)
                setCurrentPlaybackParameters()
            }
        }

        if (pitchStepUpText != null) {
            pitchStepUpText!!.text = getStepUpPercentString(stepSize)
            pitchStepUpText!!.setOnClickListener { view ->
                onPitchSliderUpdated(currentPitch + stepSize)
                setCurrentPlaybackParameters()
            }
        }

        if (pitchStepDownText != null) {
            pitchStepDownText!!.text = getStepDownPercentString(stepSize)
            pitchStepDownText!!.setOnClickListener { view ->
                onPitchSliderUpdated(currentPitch - stepSize)
                setCurrentPlaybackParameters()
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Sliders
    ///////////////////////////////////////////////////////////////////////////
    private fun onTempoSliderUpdated(newTempo: Double) {
        if (unhookingCheckbox == null) return
        if (!unhookingCheckbox!!.isChecked) {
            setSliders(newTempo)
        } else {
            setTempoSlider(newTempo)
        }
    }

    private fun onPitchSliderUpdated(newPitch: Double) {
        if (unhookingCheckbox == null) return
        if (!unhookingCheckbox!!.isChecked) {
            setSliders(newPitch)
        } else {
            setPitchSlider(newPitch)
        }
    }

    private fun setSliders(newValue: Double) {
        setTempoSlider(newValue)
        setPitchSlider(newValue)
    }

    private fun setTempoSlider(newTempo: Double) {
        if (tempoSlider == null) return
        tempoSlider!!.progress = strategy.progressOf(newTempo)
    }

    private fun setPitchSlider(newPitch: Double) {
        if (pitchSlider == null) return
        pitchSlider!!.progress = strategy.progressOf(newPitch)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////////////////////////////

    private fun setCurrentPlaybackParameters() {
        setPlaybackParameters(currentTempo, currentPitch, currentSkipSilence)
    }

    private fun setPlaybackParameters(tempo: Double, pitch: Double,
                                      skipSilence: Boolean) {
        if (callback != null && tempoCurrentText != null && pitchCurrentText != null) {
            Log.d(TAG, "Setting playback parameters to tempo=[$tempo], pitch=[$pitch]")

            tempoCurrentText!!.text = PlayerHelper.formatSpeed(tempo)
            pitchCurrentText!!.text = PlayerHelper.formatPitch(pitch)
            callback!!.onPlaybackParameterChanged(tempo.toFloat(), pitch.toFloat(), skipSilence)
        }
    }

    companion object {
        private const val TAG = "PlaybackParameterDialog"

        // Minimum allowable range in ExoPlayer
        const val MINIMUM_PLAYBACK_VALUE = 0.10
        const val MAXIMUM_PLAYBACK_VALUE = 3.00

        private const val STEP_UP_SIGN = '+'
        private const val STEP_DOWN_SIGN = '-'

        const val STEP_ONE_PERCENT_VALUE = 0.01
        const val STEP_FIVE_PERCENT_VALUE = 0.05
        const val STEP_TEN_PERCENT_VALUE = 0.10
        const val STEP_TWENTY_FIVE_PERCENT_VALUE = 0.25
        const val STEP_ONE_HUNDRED_PERCENT_VALUE = 1.00

        const val DEFAULT_TEMPO = 1.00
        const val DEFAULT_PITCH = 1.00
        const val DEFAULT_STEP = STEP_TWENTY_FIVE_PERCENT_VALUE
        const val DEFAULT_SKIP_SILENCE = false

        private const val INITIAL_TEMPO_KEY = "initial_tempo_key"
        private const val INITIAL_PITCH_KEY = "initial_pitch_key"

        private const val TEMPO_KEY = "tempo_key"
        private const val PITCH_KEY = "pitch_key"
        private const val STEP_SIZE_KEY = "step_size_key"

        fun newInstance(playbackTempo: Double,
                        playbackPitch: Double,
                        playbackSkipSilence: Boolean): PlaybackParameterDialog {
            val dialog = PlaybackParameterDialog()
            dialog.initialTempo = playbackTempo
            dialog.initialPitch = playbackPitch

            dialog.tempo = playbackTempo
            dialog.pitch = playbackPitch

            dialog.initialSkipSilence = playbackSkipSilence
            return dialog
        }

        private fun getStepUpPercentString(percent: Double): String {
            return STEP_UP_SIGN + getPercentString(percent)
        }

        private fun getStepDownPercentString(percent: Double): String {
            return STEP_DOWN_SIGN + getPercentString(percent)
        }

        private fun getPercentString(percent: Double): String {
            return PlayerHelper.formatPitch(percent)
        }
    }
}
