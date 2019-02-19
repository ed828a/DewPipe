package org.schabi.newpipe.download.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import icepick.Icepick
import icepick.State
import io.reactivex.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.download.service.DownloadManagerService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.*
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper
import java.util.*

class DownloadDialog : androidx.fragment.app.DialogFragment(), RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

    @State @JvmField
    var currentInfo: StreamInfo? = null
    @State @JvmField
    var wrappedAudioStreams = StreamSizeWrapper.empty<AudioStream>()
    @State @JvmField
    var wrappedVideoStreams = StreamSizeWrapper.empty<VideoStream>()
    @State @JvmField
    var selectedVideoIndex = 0
    @State @JvmField
    var selectedAudioIndex = 0

    private var audioStreamsAdapter: StreamItemAdapter<AudioStream>? = null
    private var videoStreamsAdapter: StreamItemAdapter<VideoStream>? = null

    private val disposables = CompositeDisposable()

    private var nameEditText: EditText? = null
    private var streamsSpinner: Spinner? = null
    private var radioVideoAudioGroup: RadioGroup? = null
    private var threadsCountTextView: TextView? = null
    private var threadsSeekBar: SeekBar? = null

    private fun setInfo(info: StreamInfo) {
        this.currentInfo = info
    }

    fun setAudioStreams(audioStreams: List<AudioStream>) {
        setAudioStreams(StreamSizeWrapper(audioStreams))
    }

    fun setAudioStreams(wrappedAudioStreams: StreamSizeWrapper<AudioStream>) {
        this.wrappedAudioStreams = wrappedAudioStreams
    }

    fun setVideoStreams(videoStreams: List<VideoStream>) {
        setVideoStreams(StreamSizeWrapper(videoStreams))
    }

    fun setVideoStreams(wrappedVideoStreams: StreamSizeWrapper<VideoStream>) {
        this.wrappedVideoStreams = wrappedVideoStreams
    }

    fun setSelectedVideoStream(selectedVideoIndex: Int) {
        this.selectedVideoIndex = selectedVideoIndex
    }

    fun setSelectedAudioStream(selectedAudioIndex: Int) {
        this.selectedAudioIndex = selectedAudioIndex
    }

    ///////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")
        if (!PermissionHelper.checkStoragePermissions(activity!!, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            dialog?.dismiss()
            return
        }

        setStyle(androidx.fragment.app.DialogFragment.STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context!!))
        Icepick.restoreInstanceState(this, savedInstanceState)

        this.videoStreamsAdapter = StreamItemAdapter(context!!, wrappedVideoStreams, true)
        this.audioStreamsAdapter = StreamItemAdapter(context!!, wrappedAudioStreams)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView() called with: inflater = [$inflater], container = [$container], savedInstanceState = [$savedInstanceState]")
        return inflater.inflate(R.layout.download_dialog, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nameEditText = view.findViewById(R.id.file_name)
        nameEditText!!.setText(FilenameUtils.createFilename(context!!, currentInfo!!.name))
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(context!!, currentInfo!!.audioStreams)

        streamsSpinner = view.findViewById(R.id.quality_spinner)
        streamsSpinner!!.onItemSelectedListener = this

        threadsCountTextView = view.findViewById(R.id.threads_count)
        threadsSeekBar = view.findViewById(R.id.threads)

        radioVideoAudioGroup = view.findViewById(R.id.video_audio_group)
        radioVideoAudioGroup!!.setOnCheckedChangeListener(this)

        initToolbar(view.findViewById(R.id.toolbar))
        setupDownloadOptions()

        val def = 3
        threadsCountTextView!!.text = def.toString()
        threadsSeekBar!!.progress = def - 1
        threadsSeekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekbar: SeekBar, progress: Int, fromUser: Boolean) {
                threadsCountTextView!!.text = (progress + 1).toString()
            }

            override fun onStartTrackingTouch(p1: SeekBar) {}

            override fun onStopTrackingTouch(p1: SeekBar) {}
        })

        fetchStreamsSize()
    }

    private fun fetchStreamsSize() {
        disposables.clear()

        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedVideoStreams).subscribe { result ->
            if (radioVideoAudioGroup!!.checkedRadioButtonId == R.id.video_button) {
                setupVideoSpinner()
            }
        })
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams).subscribe { result ->
            if (radioVideoAudioGroup!!.checkedRadioButtonId == R.id.audio_button) {
                setupAudioSpinner()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inits
    ///////////////////////////////////////////////////////////////////////////

    private fun initToolbar(toolbar: Toolbar) {
        Log.d(TAG, "initToolbar() called with: toolbar = [$toolbar]")
        toolbar.setTitle(R.string.download_dialog_title)
        toolbar.setNavigationIcon(if (ThemeHelper.isLightThemeSelected(activity!!)) R.drawable.ic_arrow_back_black_24dp else R.drawable.ic_arrow_back_white_24dp)
        toolbar.inflateMenu(R.menu.dialog_url)
        toolbar.setNavigationOnClickListener { v -> dialog?.dismiss() }

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.okay) {
                downloadSelected()
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    private fun setupAudioSpinner() {
        if (context == null) return

        streamsSpinner!!.adapter = audioStreamsAdapter
        streamsSpinner!!.setSelection(selectedAudioIndex)
        setRadioButtonsState(true)
    }

    private fun setupVideoSpinner() {
        if (context == null) return

        streamsSpinner!!.adapter = videoStreamsAdapter
        streamsSpinner!!.setSelection(selectedVideoIndex)
        setRadioButtonsState(true)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Radio group Video&Audio options - Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun onCheckedChanged(group: RadioGroup, @IdRes checkedId: Int) {
        Log.d(TAG, "onCheckedChanged() called with: group = [$group], checkedId = [$checkedId]")
        when (checkedId) {
            R.id.audio_button -> setupAudioSpinner()
            R.id.video_button -> setupVideoSpinner()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Streams Spinner Listener
    ///////////////////////////////////////////////////////////////////////////

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        Log.d(TAG, "onItemSelected() called with: parent = [$parent], view = [$view], position = [$position], id = [$id]")
        when (radioVideoAudioGroup!!.checkedRadioButtonId) {
            R.id.audio_button -> selectedAudioIndex = position
            R.id.video_button -> selectedVideoIndex = position
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {}

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    protected fun setupDownloadOptions() {
        setRadioButtonsState(false)

        val audioButton = radioVideoAudioGroup!!.findViewById<RadioButton>(R.id.audio_button)
        val videoButton = radioVideoAudioGroup!!.findViewById<RadioButton>(R.id.video_button)
        val isVideoStreamsAvailable = videoStreamsAdapter!!.count > 0
        val isAudioStreamsAvailable = audioStreamsAdapter!!.count > 0

        audioButton.visibility = if (isAudioStreamsAvailable) View.VISIBLE else View.GONE
        videoButton.visibility = if (isVideoStreamsAvailable) View.VISIBLE else View.GONE

        if (isVideoStreamsAvailable) {
            videoButton.isChecked = true
            setupVideoSpinner()
        } else if (isAudioStreamsAvailable) {
            audioButton.isChecked = true
            setupAudioSpinner()
        } else {
            Toast.makeText(context, R.string.no_streams_available_download, Toast.LENGTH_SHORT).show()
            dialog?.dismiss()
        }
    }

    private fun setRadioButtonsState(enabled: Boolean) {
        radioVideoAudioGroup!!.findViewById<View>(R.id.audio_button).isEnabled = enabled
        radioVideoAudioGroup!!.findViewById<View>(R.id.video_button).isEnabled = enabled
    }

    private fun downloadSelected() {
        val stream: Stream
        val location: String

        var fileName = nameEditText!!.text.toString().trim { it <= ' ' }
        if (fileName.isEmpty()) fileName = FilenameUtils.createFilename(context!!, currentInfo!!.name)

        Log.d(TAG, "fileName= $fileName, currentInfo.name= ${currentInfo!!.name}")

        val isAudio = radioVideoAudioGroup!!.checkedRadioButtonId == R.id.audio_button
        if (isAudio) {
            stream = audioStreamsAdapter!!.getItem(selectedAudioIndex)
            location = NewPipeSettings.getAudioDownloadPath(context!!)
        } else {
            stream = videoStreamsAdapter!!.getItem(selectedVideoIndex)
            location = NewPipeSettings.getVideoDownloadPath(context!!)
        }

        val url = stream.getUrl()
        fileName += "." + stream.getFormat().getSuffix()

        // start Downloading Service
        DownloadManagerService.startMission(context, url, location, fileName, isAudio, threadsSeekBar!!.progress + 1)
        dialog?.dismiss()
    }

    companion object {
        private const val TAG = "DialogFragment"

        fun newInstance(info: StreamInfo): DownloadDialog {
            val dialog = DownloadDialog()
            dialog.setInfo(info)
            return dialog
        }

        fun newInstance(context: Context, info: StreamInfo): DownloadDialog {
            val streamsList = ArrayList(ListHelper.getSortedStreamVideosList(context,
                    info.videoStreams, info.videoOnlyStreams, false))
            val selectedStreamIndex = ListHelper.getDefaultResolutionIndex(context, streamsList)

            val instance = newInstance(info)
            instance.setVideoStreams(streamsList)
            instance.setSelectedVideoStream(selectedStreamIndex)
            instance.setAudioStreams(info.audioStreams)
            return instance
        }
    }
}
