package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.IntentService
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.DrawableRes
import android.support.v4.app.NotificationCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ThemeHelper

import java.io.Serializable
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet

import icepick.Icepick
import icepick.State
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.AUDIO
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO
import org.schabi.newpipe.util.ThemeHelper.resolveResourceIdFromAttr

/**
 * Get the url from the intent and open it in the chosen preferred player
 */
class RouterActivity : AppCompatActivity() {

    @State
    var currentServiceId = -1
    private var currentService: StreamingService? = null
    @State
    lateinit var currentLinkType: LinkType
    @State
    var selectedRadioPosition = -1
    protected var selectedPreviously = -1

    protected var currentUrl: String? = null
    protected val disposables = CompositeDisposable()

    private var selectionIsDownload = false

    private val themeWrapperContext: Context
        get() = ContextThemeWrapper(this,
                if (ThemeHelper.isLightThemeSelected(this)) R.style.LightTheme else R.style.DarkTheme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        if (TextUtils.isEmpty(currentUrl)) {
            currentUrl = getUrl(intent)

            if (TextUtils.isEmpty(currentUrl)) {
                Toast.makeText(this, R.string.invalid_url_toast, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        setTheme(if (ThemeHelper.isLightThemeSelected(this))
            R.style.RouterActivityThemeLight
        else
            R.style.RouterActivityThemeDark)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    override fun onStart() {
        super.onStart()

        handleUrl(currentUrl!!)
    }

    override fun onDestroy() {
        super.onDestroy()

        disposables.clear()
    }

    private fun handleUrl(url: String) {
        disposables.add(Observable
                .fromCallable {
                    if (currentServiceId == -1) {
                        currentService = NewPipe.getServiceByUrl(url)
                        currentServiceId = currentService!!.serviceId
                        currentLinkType = currentService!!.getLinkTypeByUrl(url)
                        currentUrl = url
                    } else {
                        currentService = NewPipe.getService(currentServiceId)
                    }

                    currentLinkType != LinkType.NONE
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result!!) {
                        onSuccess()
                    } else {
                        onError()
                    }
                }, { this.handleError(it) }))
    }

    private fun handleError(error: Throwable) {
        error.printStackTrace()

        if (error is ExtractionException) {
            Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG).show()
        } else {
            ExtractorHelper.handleGeneralException(this, -1, null, error, UserAction.SOMETHING_ELSE, null)
        }

        finish()
    }

    private fun onError() {
        Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG).show()
        finish()
    }

    protected fun onSuccess() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedChoiceKey = preferences.getString(getString(R.string.preferred_open_action_key), getString(R.string.preferred_open_action_default))

        val showInfoKey = getString(R.string.show_info_key)
        val videoPlayerKey = getString(R.string.video_player_key)
        val backgroundPlayerKey = getString(R.string.background_player_key)
        val popupPlayerKey = getString(R.string.popup_player_key)
        val downloadKey = getString(R.string.download_key)
        val alwaysAskKey = getString(R.string.always_ask_open_action_key)

        if (selectedChoiceKey == alwaysAskKey) {
            val choices = getChoicesForService(currentService!!, currentLinkType)

            when (choices.size) {
                1 -> handleChoice(choices[0].key)
                0 -> handleChoice(showInfoKey)
                else -> showDialog(choices)
            }
        } else if (selectedChoiceKey == showInfoKey) {
            handleChoice(showInfoKey)
        } else if (selectedChoiceKey == downloadKey) {
            handleChoice(downloadKey)
        } else {
            val isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false)
            val isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false)
            val isVideoPlayerSelected = selectedChoiceKey == videoPlayerKey || selectedChoiceKey == popupPlayerKey
            val isAudioPlayerSelected = selectedChoiceKey == backgroundPlayerKey

            if (currentLinkType != LinkType.STREAM) {
                if (isExtAudioEnabled && isAudioPlayerSelected || isExtVideoEnabled && isVideoPlayerSelected) {
                    Toast.makeText(this, R.string.external_player_unsupported_link_type, Toast.LENGTH_LONG).show()
                    handleChoice(showInfoKey)
                    return
                }
            }

            val capabilities = currentService!!.serviceInfo.mediaCapabilities

            var serviceSupportsChoice = false
            if (isVideoPlayerSelected) {
                serviceSupportsChoice = capabilities.contains(VIDEO)
            } else if (selectedChoiceKey == backgroundPlayerKey) {
                serviceSupportsChoice = capabilities.contains(AUDIO)
            }

            if (serviceSupportsChoice) {
                handleChoice(selectedChoiceKey)
            } else {
                handleChoice(showInfoKey)
            }
        }
    }

    private fun showDialog(choices: List<AdapterChoiceItem>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val themeWrapperContext = themeWrapperContext

        val inflater = LayoutInflater.from(themeWrapperContext)
        val rootLayout = inflater.inflate(R.layout.preferred_player_dialog_view, null, false) as LinearLayout
        val radioGroup = rootLayout.findViewById<RadioGroup>(android.R.id.list)

        val dialogButtonsClickListener = DialogInterface.OnClickListener {  dialog, which ->
            val indexOfChild = radioGroup.indexOfChild(
                    radioGroup.findViewById(radioGroup.checkedRadioButtonId))
            val choice = choices[indexOfChild]

            handleChoice(choice.key)

            if (which == DialogInterface.BUTTON_POSITIVE) {
                preferences.edit().putString(getString(R.string.preferred_open_action_key), choice.key).apply()
            }
        }

        val alertDialog = AlertDialog.Builder(themeWrapperContext)
                .setTitle(R.string.preferred_open_action_share_menu_title)
                .setView(radioGroup)
                .setCancelable(true)
                .setNegativeButton(R.string.just_once, dialogButtonsClickListener)
                .setPositiveButton(R.string.always, dialogButtonsClickListener)
                .setOnDismissListener { dialog -> if (!selectionIsDownload) finish() }
                .create()


        alertDialog.setOnShowListener { dialog -> setDialogButtonsState(alertDialog, radioGroup.checkedRadioButtonId != -1) }

        radioGroup.setOnCheckedChangeListener { group, checkedId -> setDialogButtonsState(alertDialog, true) }
        val radioButtonsClickListener =  View.OnClickListener{ v ->
            val indexOfChild = radioGroup.indexOfChild(v)
            if (indexOfChild == -1) return@OnClickListener

            selectedPreviously = selectedRadioPosition
            selectedRadioPosition = indexOfChild

            if (selectedPreviously == selectedRadioPosition) {
                handleChoice(choices[selectedRadioPosition].key)
            }
        }

        var id = SAMPLE_ID
        for (item in choices) {
            val radioButton = inflater.inflate(R.layout.list_radio_icon_item, null) as RadioButton
            radioButton.text = item.description
            radioButton.setCompoundDrawablesWithIntrinsicBounds(item.icon, 0, 0, 0)
            radioButton.isChecked = false
            radioButton.id = id++
            radioButton.layoutParams = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            radioButton.setOnClickListener(radioButtonsClickListener)
            radioGroup.addView(radioButton)
        }

        if (selectedRadioPosition == -1) {
            val lastSelectedPlayer = preferences.getString(getString(R.string.preferred_open_action_last_selected_key), null)
            if (!TextUtils.isEmpty(lastSelectedPlayer)) {
                for (i in choices.indices) {
                    val c = choices[i]
                    if (lastSelectedPlayer == c.key) {
                        selectedRadioPosition = i
                        break
                    }
                }
            }
        }

        selectedRadioPosition = Math.min(Math.max(-1, selectedRadioPosition), choices.size - 1)
        if (selectedRadioPosition != -1) {
            (radioGroup.getChildAt(selectedRadioPosition) as RadioButton).isChecked = true
        }
        selectedPreviously = selectedRadioPosition

        alertDialog.show()
    }

    private fun getChoicesForService(service: StreamingService, linkType: LinkType): List<AdapterChoiceItem> {
        val context = themeWrapperContext

        val returnList = ArrayList<AdapterChoiceItem>()
        val capabilities = service.serviceInfo.mediaCapabilities

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false)
        val isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false)

        returnList.add(AdapterChoiceItem(getString(R.string.show_info_key), getString(R.string.show_info),
                resolveResourceIdFromAttr(context, R.attr.info)))

        if (capabilities.contains(VIDEO) && !(isExtVideoEnabled && linkType != LinkType.STREAM)) {
            returnList.add(AdapterChoiceItem(getString(R.string.video_player_key), getString(R.string.video_player),
                    resolveResourceIdFromAttr(context, R.attr.play)))
            returnList.add(AdapterChoiceItem(getString(R.string.popup_player_key), getString(R.string.popup_player),
                    resolveResourceIdFromAttr(context, R.attr.popup)))
        }

        if (capabilities.contains(AUDIO) && !(isExtAudioEnabled && linkType != LinkType.STREAM)) {
            returnList.add(AdapterChoiceItem(getString(R.string.background_player_key), getString(R.string.background_player),
                    resolveResourceIdFromAttr(context, R.attr.audio)))
        }

        returnList.add(AdapterChoiceItem(getString(R.string.download_key), getString(R.string.download),
                resolveResourceIdFromAttr(context, R.attr.download)))

        return returnList
    }

    private fun setDialogButtonsState(dialog: AlertDialog, state: Boolean) {
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        if (negativeButton == null || positiveButton == null) return

        negativeButton.isEnabled = state
        positiveButton.isEnabled = state
    }

    private fun handleChoice(selectedChoiceKey: String?) {
        val validChoicesList = Arrays.asList(*resources.getStringArray(R.array.preferred_open_action_values_list))
        if (validChoicesList.contains(selectedChoiceKey)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(getString(R.string.preferred_open_action_last_selected_key), selectedChoiceKey)
                    .apply()
        }

        if (selectedChoiceKey == getString(R.string.popup_player_key) && !PermissionHelper.isPopupEnabled(this)) {
            PermissionHelper.showPopupEnablementToast(this)
            finish()
            return
        }

        if (selectedChoiceKey == getString(R.string.download_key)) {
            if (PermissionHelper.checkStoragePermissions(this, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                selectionIsDownload = true
                openDownloadDialog()
            }
            return
        }

        // stop and bypass FetcherService if InfoScreen was selected since
        // StreamDetailFragment can fetch data itself
        if (selectedChoiceKey == getString(R.string.show_info_key)) {
            disposables.add(Observable
                    .fromCallable { NavigationHelper.getIntentByLink(this, currentUrl) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)

                        finish()
                    }, { this.handleError(it) })
            )
            return
        }

        val intent = Intent(this, FetcherService::class.java)
        val choice = Choice(currentService!!.serviceId, currentLinkType, currentUrl!!, selectedChoiceKey!!)
        intent.putExtra(FetcherService.KEY_CHOICE, choice)
        startService(intent)

        finish()
    }

    @SuppressLint("CheckResult")
    private fun openDownloadDialog() {
        ExtractorHelper.getStreamInfo(currentServiceId, currentUrl, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result: StreamInfo ->
                    val sortedVideoStreams = ListHelper.getSortedStreamVideosList(this,
                            result.videoStreams,
                            result.videoOnlyStreams,
                            false)
                    val selectedVideoStreamIndex = ListHelper.getDefaultResolutionIndex(this,
                            sortedVideoStreams)

                    val fm = supportFragmentManager
                    val downloadDialog = DownloadDialog.newInstance(result)
                    downloadDialog.setVideoStreams(sortedVideoStreams)
                    downloadDialog.setAudioStreams(result.audioStreams)
                    downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex)
                    downloadDialog.show(fm, "downloadDialog")
                    fm.executePendingTransactions()
                    downloadDialog.dialog.setOnDismissListener { dialog -> finish() }
                }, { throwable: Throwable -> onError() })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        for (i in grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                finish()
                return
            }
        }
        if (requestCode == PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE) {
            openDownloadDialog()
        }
    }

    private class AdapterChoiceItem internal constructor(internal val key: String, internal val description: String, @field:DrawableRes internal val icon: Int)

    class Choice internal constructor(internal val serviceId: Int, internal val linkType: LinkType, internal val url: String, internal val playerChoice: String) : Serializable {

        override fun toString(): String {
            return serviceId.toString() + ":" + url + " > " + linkType + " ::: " + playerChoice
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Service Fetcher
    ///////////////////////////////////////////////////////////////////////////

    class FetcherService : IntentService(FetcherService::class.java.simpleName) {
        private var fetcher: Disposable? = null

        override fun onCreate() {
            super.onCreate()
            startForeground(ID, createNotification().build())
        }

        override fun onHandleIntent(intent: Intent?) {
            if (intent == null) return

            val serializable = intent.getSerializableExtra(KEY_CHOICE) as? Choice ?: return
            handleChoice(serializable)
        }

        fun handleChoice(choice: Choice) {
            var single: Single<out Info>? = null
            var userAction = UserAction.SOMETHING_ELSE

            when (choice.linkType) {
                StreamingService.LinkType.STREAM -> {
                    single = ExtractorHelper.getStreamInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_STREAM
                }
                StreamingService.LinkType.CHANNEL -> {
                    single = ExtractorHelper.getChannelInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_CHANNEL
                }
                StreamingService.LinkType.PLAYLIST -> {
                    single = ExtractorHelper.getPlaylistInfo(choice.serviceId, choice.url, false)
                    userAction = UserAction.REQUESTED_PLAYLIST
                }
            }


            if (single != null) {
                val finalUserAction = userAction
                val resultHandler = getResultHandler(choice)
                fetcher = single
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ info ->
                            resultHandler.accept(info)
                            if (fetcher != null) fetcher!!.dispose()
                        }, { throwable ->
                            ExtractorHelper.handleGeneralException(this,
                                    choice.serviceId, choice.url, throwable, finalUserAction, ", opened with " + choice.playerChoice)
                        })
            }
        }

        fun getResultHandler(choice: Choice): Consumer<Info> {
            return Consumer{ info ->
                val videoPlayerKey = getString(R.string.video_player_key)
                val backgroundPlayerKey = getString(R.string.background_player_key)
                val popupPlayerKey = getString(R.string.popup_player_key)

                val preferences = PreferenceManager.getDefaultSharedPreferences(this)
                val isExtVideoEnabled = preferences.getBoolean(getString(R.string.use_external_video_player_key), false)
                val isExtAudioEnabled = preferences.getBoolean(getString(R.string.use_external_audio_player_key), false)
                val useOldVideoPlayer = PlayerHelper.isUsingOldPlayer(this)

                var playQueue: PlayQueue
                val playerChoice = choice.playerChoice

                if (info is StreamInfo) {
                    if (playerChoice == backgroundPlayerKey && isExtAudioEnabled) {
                        NavigationHelper.playOnExternalAudioPlayer(this, info as StreamInfo)

                    } else if (playerChoice == videoPlayerKey && isExtVideoEnabled) {
                        NavigationHelper.playOnExternalVideoPlayer(this, info as StreamInfo)

                    } else if (playerChoice == videoPlayerKey && useOldVideoPlayer) {
                        NavigationHelper.playOnOldVideoPlayer(this, info as StreamInfo)

                    } else {
                        playQueue = SinglePlayQueue(info as StreamInfo)

                        if (playerChoice == videoPlayerKey) {
                            NavigationHelper.playOnMainPlayer(this, playQueue)
                        } else if (playerChoice == backgroundPlayerKey) {
                            NavigationHelper.enqueueOnBackgroundPlayer(this, playQueue, true)
                        } else if (playerChoice == popupPlayerKey) {
                            NavigationHelper.enqueueOnPopupPlayer(this, playQueue, true)
                        }
                    }
                }

                if (info is ChannelInfo || info is PlaylistInfo) {
                    playQueue = if (info is ChannelInfo) ChannelPlayQueue(info as ChannelInfo) else PlaylistPlayQueue(info as PlaylistInfo)

                    if (playerChoice == videoPlayerKey) {
                        NavigationHelper.playOnMainPlayer(this, playQueue)
                    } else if (playerChoice == backgroundPlayerKey) {
                        NavigationHelper.playOnBackgroundPlayer(this, playQueue)
                    } else if (playerChoice == popupPlayerKey) {
                        NavigationHelper.playOnPopupPlayer(this, playQueue)
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            stopForeground(true)
            if (fetcher != null) fetcher!!.dispose()
        }

        private fun createNotification(): NotificationCompat.Builder {
            return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(getString(R.string.preferred_player_fetcher_notification_title))
                    .setContentText(getString(R.string.preferred_player_fetcher_notification_message))
        }

        companion object {
            private const val ID = 456
            const val KEY_CHOICE = "key_choice"
        }
    }

    private fun getUrl(intent: Intent): String? {
        // first gather data and find service
        var videoUrl: String? = null
        if (intent.data != null) {
            // this means the video was called though another app
            videoUrl = intent.data!!.toString()
        } else if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            //this means that vidoe was called through share menu
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val uris = getUris(extraText)
            videoUrl = if (uris.isNotEmpty()) uris[0] else null
        }

        return videoUrl
    }

    private fun removeHeadingGibberish(input: String): String {
        var start = 0
        for (i in input.indexOf("://") - 1 downTo 0) {
            if (!input.substring(i, i + 1).matches("\\p{L}".toRegex())) {
                start = i + 1
                break
            }
        }
        return input.substring(start, input.length)
    }

    private fun trim(input: String?): String? {
        if (input == null || input.length < 1) {
            return input
        } else {
            var output: String = input
            while (output.isNotEmpty() && output.substring(0, 1).matches(REGEX_REMOVE_FROM_URL.toRegex())) {
                output = output.substring(1)
            }
            while (output.isNotEmpty() && output.substring(output.length - 1, output.length).matches(REGEX_REMOVE_FROM_URL.toRegex())) {
                output = output.substring(0, output.length - 1)
            }
            return output
        }
    }

    /**
     * Retrieves all Strings which look remotely like URLs from a text.
     * Used if NewPipe was called through share menu.
     *
     * @param sharedText text to scan for URLs.
     * @return potential URLs
     */
    protected fun getUris(sharedText: String?): Array<String> {
        val result = HashSet<String>()
        if (sharedText != null) {
            val array = sharedText.split("\\p{Space}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (s in array) {
                val string = trim(s)!!
                if (string.isNotEmpty()) {
                    if (s.matches(".+://.+".toRegex())) {
                        result.add(removeHeadingGibberish(s))
                    } else if (s.matches(".+\\..+".toRegex())) {
                        result.add("http://$s")
                    }
                }
            }
        }
        return result.toTypedArray()
    }

    companion object {

        ///////////////////////////////////////////////////////////////////////////
        // Utils
        ///////////////////////////////////////////////////////////////////////////

        /**
         * Removes invisible separators (\p{Z}) and punctuation characters including
         * brackets (\p{P}). See http://www.regular-expressions.info/unicode.html for
         * more details.
         */
        private const val REGEX_REMOVE_FROM_URL = "[\\p{Z}\\p{P}]"
        private const val SAMPLE_ID = 12345
    }
}
