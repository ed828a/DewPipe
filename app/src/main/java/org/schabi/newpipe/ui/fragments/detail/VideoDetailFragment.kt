package org.schabi.newpipe.ui.fragments.detail

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.DrawableRes
import android.support.annotation.FloatRange
import android.support.v4.content.ContextCompat
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.nirhart.parallaxscroll.views.ParallaxScrollView
import com.nostra13.universalimageloader.core.assist.FailReason
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import icepick.State
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.ui.fragments.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.activity.ReCaptchaActivity
import org.schabi.newpipe.download.ui.DownloadDialog
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.*
import org.schabi.newpipe.ui.fragments.BackPressable
import org.schabi.newpipe.ui.fragments.BaseStateFragment
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.info_list.InfoItemDialog
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.MainVideoPlayer
import org.schabi.newpipe.player.PopupVideoPlayer
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.old.PlayVideoActivity
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.*
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper
import java.util.*

class VideoDetailFragment : BaseStateFragment<StreamInfo>(), BackPressable, SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener, View.OnLongClickListener {

    private lateinit var infoItemBuilder: InfoItemBuilder

    private var updateFlags = 0

    private var autoPlayEnabled: Boolean = false
    private var showRelatedStreams: Boolean = false
    private var wasRelatedStreamsExpanded = false

    @State
    @JvmField
    var serviceId = Constants.NO_SERVICE_ID
    @State
    @JvmField
    var name: String? = null
    @State
    @JvmField
    var url: String? = null

    private var currentInfo: StreamInfo? = null
    private var currentWorker: Disposable? = null
//    private var disposables: CompositeDisposable? = CompositeDisposable()

    private var sortedVideoStreams: List<VideoStream>? = null
    private var selectedVideoStreamIndex = -1

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    private var menu: Menu? = null

    private var toolbarSpinner: Spinner? = null

    private var parallaxScrollRootView: ParallaxScrollView? = null
    private var contentRootLayoutHiding: LinearLayout? = null

    private var thumbnailBackgroundButton: View? = null
    private var thumbnailImageView: ImageView? = null
    private var thumbnailPlayButton: ImageView? = null

    private var videoTitleRoot: View? = null
    private var videoTitleTextView: TextView? = null
    private var videoTitleToggleArrow: ImageView? = null
    private var videoCountView: TextView? = null

    private var detailControlsBackground: TextView? = null
    private var detailControlsPopup: TextView? = null
    private var detailControlsAddToPlaylist: TextView? = null
    private var detailControlsDownload: TextView? = null
    private var appendControlsDetail: TextView? = null
    private var detailDurationView: TextView? = null

    private var videoDescriptionRootLayout: LinearLayout? = null
    private var videoUploadDateView: TextView? = null
    private var videoDescriptionView: TextView? = null

    private var uploaderRootLayout: View? = null
    private var uploaderTextView: TextView? = null
    private var uploaderThumb: ImageView? = null

    private var thumbsUpTextView: TextView? = null
    private var thumbsUpImageView: ImageView? = null
    private var thumbsDownTextView: TextView? = null
    private var thumbsDownImageView: ImageView? = null
    private var thumbsDisabledTextView: TextView? = null

    private var nextStreamTitle: TextView? = null
    private var relatedStreamRootLayout: LinearLayout? = null
    private var relatedStreamsView: LinearLayout? = null
    private var relatedStreamExpandButton: ImageButton? = null

    private val onControlsTouchListener: View.OnTouchListener
        get() = View.OnTouchListener { view: View, motionEvent: MotionEvent ->
            val isShowHoldToAppend = PreferenceManager.getDefaultSharedPreferences(activity as Context)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)

            if (isShowHoldToAppend && motionEvent.action == MotionEvent.ACTION_DOWN) {
                animateView(appendControlsDetail!!,
                        true,
                        250,
                        0,
                        Runnable {
                            animateView(appendControlsDetail!!, false, 1500, 1000)
                        })
            }

            false
        }

    ///////////////////////////////////////////////////////////////////////////
    // OwnStack
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Stack that contains the "navigation history".<br></br>
     * The peek is the current video.
     */
    private val stack = LinkedList<StackItem>()

    private val selectedVideoStream: VideoStream?
        get() = if (sortedVideoStreams != null) sortedVideoStreams!![selectedVideoStreamIndex] else null

    private val separatorView: View
        get() {
            val separator = View(activity)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            val m8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()

            val m5 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt()
            params.setMargins(m8, m5, m8, m5)
            separator.layoutParams = params

            val typedValue = TypedValue()
            activity?.theme?.resolveAttribute(R.attr.separator_color, typedValue, true)
            separator.setBackgroundColor(typedValue.data)

            return separator
        }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_next_video_key), true)
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_video_detail, container, false)
    }

    override fun onPause() {
        super.onPause()
        if (currentWorker != null) currentWorker!!.dispose()
    }

    override fun onResume() {
        super.onResume()

        if (updateFlags != 0) {
            if (!isLoading.get() && currentInfo != null) {
                if (updateFlags and RELATED_STREAMS_UPDATE_FLAG != 0) initRelatedVideos(currentInfo!!)
                if (updateFlags and RESOLUTIONS_MENU_UPDATE_FLAG != 0) setupActionBar(currentInfo!!)
            }

            if (updateFlags and TOOLBAR_ITEMS_UPDATE_FLAG != 0 && menu != null) {
                updateMenuItemVisibility()
            }
            updateFlags = 0
        }

        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            selectAndLoadVideo(serviceId, url!!, name!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this)

        currentWorker?.dispose()
        if (!compositeDisposable.isDisposed) compositeDisposable.dispose()
        currentWorker = null
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView() called")
        toolbarSpinner?.onItemSelectedListener = null
        toolbarSpinner?.adapter = null
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ReCaptchaActivity.RECAPTCHA_REQUEST -> if (resultCode == Activity.RESULT_OK) {
                NavigationHelper.openVideoDetailFragment(fragmentManager, serviceId, url, name)
            } else
                Log.e(TAG, "ReCaptcha failed")

            else -> Log.e(TAG, "Request code getTabFrom activity not supported [$requestCode]")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            getString(R.string.show_next_video_key) -> {
                showRelatedStreams = sharedPreferences.getBoolean(key, true)
                updateFlags = updateFlags or RELATED_STREAMS_UPDATE_FLAG
            }

            getString(R.string.default_video_format_key),
            getString(R.string.default_resolution_key),
            getString(R.string.show_higher_resolutions_key),
            getString(R.string.use_external_video_player_key) -> updateFlags = updateFlags or RESOLUTIONS_MENU_UPDATE_FLAG

            getString(R.string.show_play_with_kodi_key) -> updateFlags = updateFlags or TOOLBAR_ITEMS_UPDATE_FLAG
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        val nextCount = if (currentInfo != null && currentInfo!!.nextVideo != null) 2 else 0
        if (relatedStreamsView != null && relatedStreamsView!!.childCount > INITIAL_RELATED_VIDEOS + nextCount) {
            outState.putSerializable(WAS_RELATED_EXPANDED_KEY, true)
        }

        if (!isLoading.get() && currentInfo != null && isVisible) {
            outState.putSerializable(INFO_KEY, currentInfo)
        }

        outState.putSerializable(STACK_KEY, stack)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        wasRelatedStreamsExpanded = savedInstanceState.getBoolean(WAS_RELATED_EXPANDED_KEY, false)
        var serializable = savedInstanceState.getSerializable(INFO_KEY)
        if (serializable is StreamInfo) {

            currentInfo = serializable
            url?.let { url ->
                InfoCache.instance.putInfo(serviceId, url, currentInfo!!)
            }

        }

        serializable = savedInstanceState.getSerializable(STACK_KEY)
        if (serializable is Collection<*>) {

            stack.addAll(serializable as Collection<StackItem>)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // OnClick
    ///////////////////////////////////////////////////////////////////////////

    override fun onClick(view: View) {
        if (isLoading.get() || currentInfo == null) return

        when (view.id) {
            R.id.detail_controls_background -> openBackgroundPlayer(false)
            R.id.detail_controls_popup -> openPopupPlayer(false)
            R.id.detail_controls_playlist_append -> if (fragmentManager != null && currentInfo != null) {
                PlaylistAppendDialog.fromStreamInfo(currentInfo!!)
                        .show(fragmentManager!!, TAG)
            }
            R.id.detail_controls_download -> if (PermissionHelper.checkStoragePermissions(activity!!, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                this.openDownloadDialog()
            }
            R.id.detail_uploader_root_layout -> if (TextUtils.isEmpty(currentInfo!!.uploaderUrl)) {
                Log.w(TAG, "Can't open channel because we got no channel URL")
            } else {
                // Todo: this also can fix opening Channel in Channel issue
                try {
                    NavigationHelper.openChannelFragment(
                            fragmentManager,
                            currentInfo!!.serviceId,
                            currentInfo!!.uploaderUrl,
                            currentInfo!!.uploaderName)
                } catch (e: Exception) {
                    val context = getActivity()
                    context?.let {
                        ErrorActivity.reportUiError(it as AppCompatActivity, e)
                    }

                }

            }
            R.id.detail_thumbnail_root_layout -> if (currentInfo!!.videoStreams.isEmpty() && currentInfo!!.videoOnlyStreams.isEmpty()) {
                openBackgroundPlayer(false)
            } else {
                openVideoPlayer()
            }
            R.id.detail_title_root_layout -> toggleTitleAndDescription()
            R.id.detail_related_streams_expand -> toggleExpandRelatedVideos(currentInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (isLoading.get() || currentInfo == null) return false

        when (view.id) {
            R.id.detail_controls_background -> openBackgroundPlayer(true)
            R.id.detail_controls_popup -> openPopupPlayer(true)
            R.id.detail_controls_download -> {
                val activity = getActivity()
                activity?.let {
                    NavigationHelper.openDownloads(it)
                }
            }
        }

        return true
    }

    private fun toggleTitleAndDescription() {
        if (videoTitleToggleArrow != null) {    //it is null for tablets
            if (videoDescriptionRootLayout!!.visibility == View.VISIBLE) {
                videoTitleTextView!!.maxLines = 1
                videoDescriptionRootLayout!!.visibility = View.GONE
                videoTitleToggleArrow!!.setImageResource(R.drawable.arrow_down)
            } else {
                videoTitleTextView!!.maxLines = 10
                videoDescriptionRootLayout!!.visibility = View.VISIBLE
                videoTitleToggleArrow!!.setImageResource(R.drawable.arrow_up)
            }
        }
    }

    private fun toggleExpandRelatedVideos(info: StreamInfo?) {
        Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [$info]")
        if (!showRelatedStreams) return

        val nextCount = if (info!!.nextVideo != null) 2 else 0
        val initialCount = INITIAL_RELATED_VIDEOS + nextCount

        if (relatedStreamsView!!.childCount > initialCount) {
            relatedStreamsView!!.removeViews(initialCount,
                    relatedStreamsView!!.childCount - initialCount)

            activity?.let {
                relatedStreamExpandButton!!.setImageDrawable(ContextCompat.getDrawable(
                        it as Context, ThemeHelper.resolveResourceIdFromAttr(activity!!, R.attr.expand)))
            }

        } else {

            Log.d(TAG, "toggleExpandRelatedVideos() called with: info = [$info], getTabFrom = [$INITIAL_RELATED_VIDEOS]")
            for (i in INITIAL_RELATED_VIDEOS until info.relatedStreams.size) {
                val item = info.relatedStreams[i]
                //Log.d(TAG, "i = " + i);
                relatedStreamsView!!.addView(infoItemBuilder.buildView(relatedStreamsView!!, item))
            }
            activity?.let {
                relatedStreamExpandButton!!.setImageDrawable(
                        ContextCompat.getDrawable(it as Context,
                                ThemeHelper.resolveResourceIdFromAttr(activity!!, R.attr.collapse)))
            }
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        toolbarSpinner = activity!!.findViewById<View>(R.id.toolbar).findViewById(R.id.toolbarSpinner)

        parallaxScrollRootView = rootView.findViewById(R.id.detail_main_content)

        thumbnailBackgroundButton = rootView.findViewById(R.id.detail_thumbnail_root_layout)
        thumbnailImageView = rootView.findViewById(R.id.detail_thumbnail_image_view)
        thumbnailPlayButton = rootView.findViewById(R.id.detail_thumbnail_play_button)

        contentRootLayoutHiding = rootView.findViewById(R.id.detail_content_root_hiding)

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout)
        videoTitleTextView = rootView.findViewById(R.id.detail_video_title_view)
        videoTitleToggleArrow = rootView.findViewById(R.id.detail_toggle_description_view)
        videoCountView = rootView.findViewById(R.id.detail_view_count_view)

        detailControlsBackground = rootView.findViewById(R.id.detail_controls_background)
        detailControlsPopup = rootView.findViewById(R.id.detail_controls_popup)
        detailControlsAddToPlaylist = rootView.findViewById(R.id.detail_controls_playlist_append)
        detailControlsDownload = rootView.findViewById(R.id.detail_controls_download)
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail)
        detailDurationView = rootView.findViewById(R.id.detail_duration_view)

        videoDescriptionRootLayout = rootView.findViewById(R.id.detail_description_root_layout)
        videoUploadDateView = rootView.findViewById(R.id.detail_upload_date_view)
        videoDescriptionView = rootView.findViewById(R.id.detail_description_view)
        videoDescriptionView!!.movementMethod = LinkMovementMethod.getInstance()
        videoDescriptionView!!.autoLinkMask = Linkify.WEB_URLS

        //thumbsRootLayout = rootView.findViewById(R.id.detail_thumbs_root_layout);
        thumbsUpTextView = rootView.findViewById(R.id.detail_thumbs_up_count_view)
        thumbsUpImageView = rootView.findViewById(R.id.detail_thumbs_up_img_view)
        thumbsDownTextView = rootView.findViewById(R.id.detail_thumbs_down_count_view)
        thumbsDownImageView = rootView.findViewById(R.id.detail_thumbs_down_img_view)
        thumbsDisabledTextView = rootView.findViewById(R.id.detail_thumbs_disabled_view)

        uploaderRootLayout = rootView.findViewById(R.id.detail_uploader_root_layout)
        uploaderTextView = rootView.findViewById(R.id.detail_uploader_text_view)
        uploaderThumb = rootView.findViewById(R.id.detail_uploader_thumbnail_view)

        relatedStreamRootLayout = rootView.findViewById(R.id.detail_related_streams_root_layout)
        nextStreamTitle = rootView.findViewById(R.id.detail_next_stream_title)
        relatedStreamsView = rootView.findViewById(R.id.detail_related_streams_view)

        relatedStreamExpandButton = rootView.findViewById(R.id.detail_related_streams_expand)

        infoItemBuilder = InfoItemBuilder(activity!!)
        setHeightThumbnail()
    }

    @SuppressLint("ClickableViewAccessibility")  // for blind people
    override fun initListeners() {
        super.initListeners()
        infoItemBuilder.onStreamSelectedListener = object : OnClickGesture<StreamInfoItem>() {
            override fun selected(selectedItem: StreamInfoItem) {
                selectAndLoadVideo(selectedItem.serviceId, selectedItem.url, selectedItem.name)
            }

            override fun held(selectedItem: StreamInfoItem) {
                showStreamDialog(selectedItem)
            }
        }

        videoTitleRoot!!.setOnClickListener(this)
        uploaderRootLayout!!.setOnClickListener(this)
        thumbnailBackgroundButton!!.setOnClickListener(this)
        detailControlsBackground!!.setOnClickListener(this)
        detailControlsPopup!!.setOnClickListener(this)
        detailControlsAddToPlaylist!!.setOnClickListener(this)
        detailControlsDownload!!.setOnClickListener(this)
        detailControlsDownload!!.setOnLongClickListener(this)
        relatedStreamExpandButton!!.setOnClickListener(this)

        detailControlsBackground!!.isLongClickable = true
        detailControlsPopup!!.isLongClickable = true
        detailControlsBackground!!.setOnLongClickListener(this)
        detailControlsPopup!!.setOnLongClickListener(this)
        detailControlsBackground!!.setOnTouchListener(onControlsTouchListener)
        detailControlsPopup!!.setOnTouchListener(onControlsTouchListener)
    }

    private fun showStreamDialog(item: StreamInfoItem) {
        val context = context
        if (context == null || context.resources == null || getActivity() == null) return

        val commands = arrayOf(context.resources.getString(R.string.enqueue_on_background), context.resources.getString(R.string.enqueue_on_popup), context.resources.getString(R.string.append_playlist), context.resources.getString(R.string.share))

        val actions = DialogInterface.OnClickListener { dialogInterface: DialogInterface, which: Int ->
            when (which) {
                0 -> NavigationHelper.enqueueOnBackgroundPlayer(context, SinglePlayQueue(item))
                1 -> NavigationHelper.enqueueOnPopupPlayer(getActivity(), SinglePlayQueue(item))
                2 -> if (fragmentManager != null) {
                    PlaylistAppendDialog.fromStreamInfoItems(listOf(item))
                            .show(fragmentManager!!, TAG)
                }
                3 -> shareUrl(item.name, item.url)
                else -> {
                }
            }
        }

        InfoItemDialog(getActivity()!!, item, commands, actions).show()
    }

    private fun initThumbnailViews(info: StreamInfo) {
        thumbnailImageView!!.setImageResource(R.drawable.dummy_thumbnail_dark)
        if (!TextUtils.isEmpty(info.thumbnailUrl)) {
            val infoServiceName: String = NewPipe.getNameOfService(info.serviceId)
            val onFailListener = object : SimpleImageLoadingListener() {
                override fun onLoadingFailed(imageUri: String?, view: View?, failReason: FailReason) {
                    imageUri?.let {
                        showSnackBarError(failReason.cause, UserAction.LOAD_IMAGE,
                                infoServiceName, imageUri, R.string.could_not_load_thumbnails)
                    }
                }
            }

            BaseFragment.imageLoader.displayImage(info.thumbnailUrl, thumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener)
        }

        if (!TextUtils.isEmpty(info.uploaderAvatarUrl)) {
            BaseFragment.imageLoader.displayImage(info.uploaderAvatarUrl, uploaderThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS)
        }
    }

    private fun initRelatedVideos(info: StreamInfo) {
        if (relatedStreamsView!!.childCount > 0) relatedStreamsView!!.removeAllViews()

        if (info.nextVideo != null && showRelatedStreams) {
            nextStreamTitle!!.visibility = View.VISIBLE
            relatedStreamsView!!.addView(
                    infoItemBuilder.buildView(relatedStreamsView!!, info.nextVideo))
            relatedStreamsView!!.addView(separatorView)
            setRelatedStreamsVisibility(View.VISIBLE)
        } else {
            nextStreamTitle!!.visibility = View.GONE
            setRelatedStreamsVisibility(View.GONE)
        }

        Log.d(TAG, "initRelatedVideos(): info = $info")
        if (info.relatedStreams != null && !info.relatedStreams.isEmpty() && showRelatedStreams) {
            //long first = System.nanoTime(), each;
            val initialSize = if (info.relatedStreams.size >= INITIAL_RELATED_VIDEOS)
                INITIAL_RELATED_VIDEOS
            else
                info.relatedStreams.size
            for (i in 0 until initialSize) {
                val item = info.relatedStreams[i]
                //each = System.nanoTime();
                relatedStreamsView!!.addView(infoItemBuilder.buildView(relatedStreamsView!!, item))
                //if (DEBUG) Log.d(TAG, "each took " + ((System.nanoTime() - each) / 1000000L) + "ms");
            }
            //if (DEBUG) Log.d(TAG, "Total time " + ((System.nanoTime() - first) / 1000000L) + "ms");

            setRelatedStreamsVisibility(View.VISIBLE)
            relatedStreamExpandButton!!.visibility = View.VISIBLE

            relatedStreamExpandButton!!.setImageDrawable(ContextCompat.getDrawable(
                    activity!!, ThemeHelper.resolveResourceIdFromAttr(activity!!, R.attr.expand)))
        } else {
            if (info.nextVideo == null) setRelatedStreamsVisibility(View.GONE)
            relatedStreamExpandButton!!.visibility = View.GONE
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        if (menu == null || inflater == null) return

        this.menu = menu

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsInflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.video_detail_menu, menu)

        updateMenuItemVisibility()

        val supportActionBar = activity!!.supportActionBar
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true)
            supportActionBar.setDisplayShowTitleEnabled(false)
        }
    }

    private fun updateMenuItemVisibility() {

        // show kodi if set in settings
        menu!!.findItem(R.id.action_play_with_kodi).isVisible = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                activity!!.getString(R.string.show_play_with_kodi_key), false)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item == null || isLoading.get()) {
            // if is still loading block menu
            return true
        }

        val id = item.itemId
        when (id) {
            R.id.menu_item_share -> {
                if (currentInfo != null) {
                    shareUrl(currentInfo!!.name, currentInfo!!.url)
                }
                return true
            }
            R.id.menu_item_openInBrowser -> {
                if (currentInfo != null) {
                    openUrlInBrowser(currentInfo!!.url)
                }
                return true
            }
            R.id.action_play_with_kodi -> {
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(
                            url?.replace("https", "http")))
                } catch (e: Exception) {
                    Log.i(TAG, "Failed to start kore", e)
                    showInstallKoreDialog(activity!!)
                }

                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun setupActionBarOnError(url: String) {
        Log.d(TAG, "setupActionBarHandlerOnError() called with: url = [$url]")
        Log.e("-----", "missing code")
    }

    private fun setupActionBar(info: StreamInfo) {
        Log.d(TAG, "setupActionBarHandler(): info = [$info]")
        val isExternalPlayerEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity!!.getString(R.string.use_external_video_player_key), false)

        sortedVideoStreams = ListHelper.getSortedStreamVideosList(activity!!, info.videoStreams, info.videoOnlyStreams, false)
        selectedVideoStreamIndex = ListHelper.getDefaultResolutionIndex(activity!!, sortedVideoStreams!!)

        val streamsAdapter = StreamItemAdapter(activity!!, StreamSizeWrapper(sortedVideoStreams!!), isExternalPlayerEnabled)
        toolbarSpinner!!.adapter = streamsAdapter
        toolbarSpinner!!.setSelection(selectedVideoStreamIndex)
        toolbarSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedVideoStreamIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun clearHistory() {
        stack.clear()
    }

    private fun pushToStack(serviceId: Int, videoUrl: String, name: String?) {
        Log.d(TAG, "pushToStack() called with: serviceId = [$serviceId], videoUrl = [$videoUrl], name = [$name]")

        if (stack.size > 0 && stack.peek().serviceId == serviceId && stack.peek().url == videoUrl) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = [$serviceId], videoUrl == peek.getUrl = [$videoUrl]")
        } else {
            Log.d(TAG, "pushToStack() wasn't equal")
            stack.push(StackItem(serviceId, videoUrl, name))
        }
    }

    private fun setTitleToUrl(serviceId: Int, videoUrl: String, name: String?) {
        if (name != null && !name.isEmpty()) {
            for (stackItem in stack) {
                if (stack.peek().serviceId == serviceId && stackItem.url == videoUrl) {
                    stackItem.title = name
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        Log.d(TAG, "onBackPressed() called")
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size <= 1) return false
        // Remove top
        stack.pop()
        // Get stack item getTabFrom the new top
        val peek = stack.peek()

        selectAndLoadVideo(peek.serviceId, peek.url, if (!TextUtils.isEmpty(peek.title)) peek.title!! else "")
        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    ///////////////////////////////////////////////////////////////////////////

    override fun doInitialLoadLogic() {
        if (currentInfo == null)
            prepareAndLoadInfo()
        else
            prepareAndHandleInfo(currentInfo!!, false)
    }

    fun selectAndLoadVideo(serviceId: Int, videoUrl: String?, name: String) {
        setInitialData(serviceId, videoUrl, name)
        prepareAndLoadInfo()
    }

    private fun prepareAndHandleInfo(info: StreamInfo, scrollToTop: Boolean) {
        Log.d(TAG, "prepareAndHandleInfo() called with: info = [$info], scrollToTop = [$scrollToTop]")

        setInitialData(info.serviceId, info.originalUrl, info.name)
        pushToStack(serviceId, url!!, name)
        showLoading()

        Log.d(TAG, "prepareAndHandleInfo() called parallaxScrollRootView.getScrollY(): ${parallaxScrollRootView!!.scrollY}")
        val greaterThanThreshold = parallaxScrollRootView!!.scrollY > (resources.displayMetrics.heightPixels * .1f).toInt()

        if (scrollToTop) parallaxScrollRootView!!.smoothScrollTo(0, 0)
        val durationInt = if (greaterThanThreshold) 250 else 0
        animateView(contentRootLayoutHiding!!,
                false,
                durationInt.toLong(),
                0,
                Runnable {
                    handleResult(info)
                    showContentWithAnimation(120, 0, .01f)
                })
    }

    protected fun prepareAndLoadInfo() {
        parallaxScrollRootView!!.smoothScrollTo(0, 0)
        pushToStack(serviceId, url!!, name)
        startLoading(false)
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)

        currentInfo = null
        currentWorker?.dispose()

        url?.let { url ->
            currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result: StreamInfo ->
                                isLoading.set(false)
                                currentInfo = result
                                showContentWithAnimation(120, 0, 0f)
                                handleResult(result)
                            },
                            { throwable: Throwable ->
                                isLoading.set(false)
                                onError(throwable)
                            })
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // Play Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun openBackgroundPlayer(append: Boolean) {
        val audioStream = currentInfo!!.audioStreams[ListHelper.getDefaultAudioFormat(activity!!, currentInfo!!.audioStreams)]

        val useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity!!.getString(R.string.use_external_audio_player_key), false)

        if (!useExternalAudioPlayer) { // not using external audio simpleExoPlayer
            openNormalBackgroundPlayer(append)
        } else {
            startOnExternalPlayer(activity!!, currentInfo!!, audioStream)
        }
    }

    private fun openPopupPlayer(append: Boolean) {
        if (!PermissionHelper.isPopupEnabled(activity!!)) {
            PermissionHelper.showPopupEnablementToast(activity!!)
            return
        }

        val itemQueue = SinglePlayQueue(currentInfo!!)
        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, itemQueue)
        } else {
            Toast.makeText(activity, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show()
            val intent = NavigationHelper.getPlayerIntent(
                    activity!!, PopupVideoPlayer::class.java, itemQueue, selectedVideoStream!!.resolution
            )
            activity!!.startService(intent)
        }
    }

    private fun openVideoPlayer() {
        val selectedVideoStream = selectedVideoStream
        val useExternalPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)

        if (useExternalPlayer && selectedVideoStream != null) {
            startOnExternalPlayer(activity!!, currentInfo!!, selectedVideoStream)
        } else {
            openNormalPlayer(selectedVideoStream)
        }
    }

    private fun openNormalBackgroundPlayer(append: Boolean) {
        val itemQueue = SinglePlayQueue(currentInfo!!)
        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, itemQueue)
        } else {
            NavigationHelper.playOnBackgroundPlayer(activity, itemQueue)
        }
    }

    private fun openNormalPlayer(selectedVideoStream: VideoStream?) {

        val useOldPlayer = PlayerHelper.isUsingOldPlayer(activity!!)

        val intent: Intent =
                if (!useOldPlayer) {
                    // using ExoPlayer
                    val playQueue = SinglePlayQueue(currentInfo!!)
                    NavigationHelper.getPlayerIntent(activity!!,
                            MainVideoPlayer::class.java,
                            playQueue,
                            selectedVideoStream!!.getResolution())
                } else {
                    // Internal Player
                    Intent(activity, PlayVideoActivity::class.java)
                            .putExtra(PlayVideoActivity.VIDEO_TITLE, currentInfo!!.name)
                            .putExtra(PlayVideoActivity.STREAM_URL, selectedVideoStream!!.getUrl())
                            .putExtra(PlayVideoActivity.VIDEO_URL, currentInfo!!.url)
                            .putExtra(PlayVideoActivity.START_POSITION, currentInfo!!.startPosition)
                }
        startActivity(intent)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    fun setAutoplay(autoplay: Boolean) {
        this.autoPlayEnabled = autoplay
    }

    private fun startOnExternalPlayer(context: Context,
                                      info: StreamInfo,
                                      selectedStream: Stream) {
        if (currentInfo == null) return
        NavigationHelper.playOnExternalPlayer(context, currentInfo!!.name,
                currentInfo!!.uploaderName, selectedStream)

        // store this info as a history viewed item.
        val recordManager = HistoryRecordManager(requireContext())
        compositeDisposable.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        {/* successful */ ignored -> },
                        { error -> Log.e(TAG, "Register view failure: ", error) }
                ))
    }

    private fun prepareDescription(descriptionHtml: String?) {
        Log.d(TAG, "descriptionHtml = $descriptionHtml")
        if (TextUtils.isEmpty(descriptionHtml)) return

        val d = Single.just(descriptionHtml)
                .map { description: String ->
                    val parsedDescription: Spanned = if (Build.VERSION.SDK_INT >= 24) {
                        Html.fromHtml(description, FROM_HTML_MODE_LEGACY)
                    } else {
                        Html.fromHtml(description)
                    }
                    parsedDescription
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { spanned: Spanned ->
                    videoDescriptionView!!.text = spanned
                    videoDescriptionView!!.visibility = View.VISIBLE
                }
        compositeDisposable.add(d)
    }

    private fun setHeightThumbnail() {
        val metrics = resources.displayMetrics
        val isPortrait = metrics.heightPixels > metrics.widthPixels
        val height = if (isPortrait)
            (metrics.widthPixels / (16.0f / 9.0f)).toInt()
        else
            (metrics.heightPixels / 2f).toInt()
        thumbnailImageView!!.layoutParams = FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height)
        thumbnailImageView!!.minimumHeight = height
    }

    private fun showContentWithAnimation(duration: Long,
                                         delay: Long,
                                         @FloatRange(from = 0.0, to = 1.0) translationPercent: Float) {
        val translationY = (resources.displayMetrics.heightPixels * if (translationPercent > 0.0f) translationPercent else .06f).toInt()

        contentRootLayoutHiding!!.animate().setListener(null).cancel()
        contentRootLayoutHiding!!.alpha = 0f
        contentRootLayoutHiding!!.translationY = translationY.toFloat()
        contentRootLayoutHiding!!.visibility = View.VISIBLE
        contentRootLayoutHiding!!.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()

        uploaderRootLayout!!.animate().setListener(null).cancel()
        uploaderRootLayout!!.alpha = 0f
        uploaderRootLayout!!.translationY = translationY.toFloat()
        uploaderRootLayout!!.visibility = View.VISIBLE
        uploaderRootLayout!!.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((duration * .5f).toLong() + delay)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()

        if (showRelatedStreams) {
            relatedStreamRootLayout!!.animate().setListener(null).cancel()
            relatedStreamRootLayout!!.alpha = 0f
            relatedStreamRootLayout!!.translationY = translationY.toFloat()
            relatedStreamRootLayout!!.visibility = View.VISIBLE
            relatedStreamRootLayout!!.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((duration * .8f).toLong() + delay)
                    .setDuration(duration)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
        }
    }

    private fun setInitialData(serviceId: Int, url: String?, name: String) {
        this.serviceId = serviceId
        this.url = url
        this.name = if (!TextUtils.isEmpty(name)) name else ""
    }

    private fun setErrorImage(imageResource: Int) {
        if (activity == null) return

        thumbnailImageView!!.setImageDrawable(ContextCompat.getDrawable(activity!!, imageResource))
        animateView(thumbnailImageView!!,
                false,
                0,
                0,
                Runnable {
                    animateView(thumbnailImageView!!, true, 500)
                }
        )
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        showError(message, showRetryButton, R.drawable.not_available_monkey)
    }

    private fun showError(message: String, showRetryButton: Boolean, @DrawableRes imageError: Int) {
        super.showError(message, showRetryButton)
        setErrorImage(imageError)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Contract
    ///////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()

        animateView(contentRootLayoutHiding!!, false, 200)
        animateView(toolbarSpinner!!, false, 200)
        animateView(thumbnailPlayButton!!, false, 50)
        animateView(detailDurationView!!, false, 100)

        videoTitleTextView!!.text = if (name != null) name else ""
        videoTitleTextView!!.maxLines = 1
        animateView(videoTitleTextView!!, true, 0)

        videoDescriptionRootLayout!!.visibility = View.GONE
        if (videoTitleToggleArrow != null) {    // for phone
            videoTitleToggleArrow!!.setImageResource(R.drawable.arrow_down)
            videoTitleToggleArrow!!.visibility = View.GONE
        } else {    // for tablet
            val related = relatedStreamRootLayout!!.parent as View
            //don`t need to hide it if related streams are disabled
            if (related.visibility == View.VISIBLE) {
                related.visibility = View.INVISIBLE
            }
        }
        videoTitleRoot!!.isClickable = false

        BaseFragment.imageLoader.cancelDisplayTask(thumbnailImageView!!)
        BaseFragment.imageLoader.cancelDisplayTask(uploaderThumb!!)
        thumbnailImageView!!.setImageBitmap(null)
        uploaderThumb!!.setImageBitmap(null)
    }

    override fun handleResult(info: StreamInfo) {
        super.handleResult(info)

        setInitialData(info.serviceId, info.originalUrl, info.name)
        pushToStack(serviceId, url!!, name)

        animateView(thumbnailPlayButton!!, true, 200)
        videoTitleTextView!!.text = name

        if (!TextUtils.isEmpty(info.uploaderName)) {
            uploaderTextView!!.text = info.uploaderName
            uploaderTextView!!.visibility = View.VISIBLE
            uploaderTextView!!.isSelected = true
        } else {
            uploaderTextView!!.visibility = View.GONE
        }
        uploaderThumb!!.setImageDrawable(ContextCompat.getDrawable(activity!!, R.drawable.buddy))

        if (info.viewCount >= 0) {
            videoCountView!!.text = Localization.localizeViewCount(activity!!, info.viewCount)
            videoCountView!!.visibility = View.VISIBLE
        } else {
            videoCountView!!.visibility = View.GONE
        }

        if (info.dislikeCount == -1L && info.likeCount == -1L) {
            thumbsDownImageView!!.visibility = View.VISIBLE
            thumbsUpImageView!!.visibility = View.VISIBLE
            thumbsUpTextView!!.visibility = View.GONE
            thumbsDownTextView!!.visibility = View.GONE

            thumbsDisabledTextView!!.visibility = View.VISIBLE
        } else {
            if (info.dislikeCount >= 0) {
                thumbsDownTextView!!.text = Localization.shortCount(activity!!, info.dislikeCount)
                thumbsDownTextView!!.visibility = View.VISIBLE
                thumbsDownImageView!!.visibility = View.VISIBLE
            } else {
                thumbsDownTextView!!.visibility = View.GONE
                thumbsDownImageView!!.visibility = View.GONE
            }

            if (info.likeCount >= 0) {
                thumbsUpTextView!!.text = Localization.shortCount(activity!!, info.likeCount)
                thumbsUpTextView!!.visibility = View.VISIBLE
                thumbsUpImageView!!.visibility = View.VISIBLE
            } else {
                thumbsUpTextView!!.visibility = View.GONE
                thumbsUpImageView!!.visibility = View.GONE
            }
            thumbsDisabledTextView!!.visibility = View.GONE
        }

        when {
            info.duration > 0 -> {
                detailDurationView!!.text = Localization.getDurationString(info.duration)
                detailDurationView!!.setBackgroundColor(ContextCompat.getColor(activity!!, R.color.duration_background_color))
                animateView(detailDurationView!!, true, 100)
            }

            info.streamType == StreamType.LIVE_STREAM -> {
                detailDurationView!!.setText(R.string.duration_live)
                detailDurationView!!.setBackgroundColor(ContextCompat.getColor(activity!!, R.color.live_duration_background_color))
                animateView(detailDurationView!!, true, 100)
            }

            else -> detailDurationView!!.visibility = View.GONE
        }

        videoDescriptionView!!.visibility = View.GONE
        if (videoTitleToggleArrow != null) { // for phone
            videoTitleRoot!!.isClickable = true
            videoTitleToggleArrow!!.visibility = View.VISIBLE
            videoTitleToggleArrow!!.setImageResource(R.drawable.arrow_down)
            videoDescriptionRootLayout!!.visibility = View.GONE
        } else { // for tablet
            videoDescriptionRootLayout!!.visibility = View.VISIBLE
        }
        if (!TextUtils.isEmpty(info.uploadDate)) {
            videoUploadDateView!!.text = Localization.localizeDate(activity!!, info.uploadDate)
        }
        prepareDescription(info.description)

        animateView(toolbarSpinner!!, true, 500)
        setupActionBar(info)
        initThumbnailViews(info)
        initRelatedVideos(info)
        if (wasRelatedStreamsExpanded) {
            toggleExpandRelatedVideos(currentInfo)
            wasRelatedStreamsExpanded = false
        }

        setTitleToUrl(info.serviceId, info.url, info.name)
        setTitleToUrl(info.serviceId, info.originalUrl, info.name)

        if (!info.errors.isEmpty()) {
            showSnackBarError(info.errors,
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.serviceId),
                    info.url,
                    0)
        }

        when (info.streamType) {
            StreamType.LIVE_STREAM,
            StreamType.AUDIO_LIVE_STREAM -> {
                detailControlsDownload!!.visibility = View.GONE
                toolbarSpinner!!.visibility = View.GONE
            }

            else -> {
                if (info.audioStreams.isEmpty()) detailControlsBackground!!.visibility = View.GONE

//                if (!info.videoStreams.isEmpty() || !info.videoOnlyStreams.isEmpty())
//                    break
                if (info.videoStreams.isEmpty() && info.videoOnlyStreams.isEmpty()) {
                    detailControlsPopup!!.visibility = View.GONE
                    toolbarSpinner!!.visibility = View.GONE
                    thumbnailPlayButton!!.setImageResource(R.drawable.ic_headset_white_24dp)
                }
            }
        }

        if (autoPlayEnabled) {
            openVideoPlayer()
            // Only auto play in the first open
            autoPlayEnabled = false
        }

        val related = relatedStreamRootLayout!!.parent
        if (related is ScrollView) {
            related.scrollTo(0, 0)
        }
    }


    fun openDownloadDialog() {
        try {
            val downloadDialog = DownloadDialog.newInstance(currentInfo!!).apply {
                setVideoStreams(sortedVideoStreams!!)
                setAudioStreams(currentInfo!!.audioStreams)
                setSelectedVideoStream(selectedVideoStreamIndex)
            }
//            downloadDialog.setVideoStreams(sortedVideoStreams!!)
//            downloadDialog.setAudioStreams(currentInfo!!.audioStreams)
//            downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex)

            downloadDialog.show(activity!!.supportFragmentManager, "downloadDialog")
        } catch (e: Exception) {
            Toast.makeText(activity,
                    R.string.could_not_setup_download_menu,
                    Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // Stream Results
    ///////////////////////////////////////////////////////////////////////////

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        when (exception) {
            is YoutubeStreamExtractor.GemaException -> onBlockedByGemaError()

            is ContentNotAvailableException -> showError(getString(R.string.content_not_available), false)

            else -> {
                val errorId = when (exception) {
                    is YoutubeStreamExtractor.DecryptException -> R.string.youtube_signature_decryption_error
                    is ParsingException -> R.string.parsing_error
                    else -> R.string.general_error
                }

                onUnrecoverableError(exception,
                        UserAction.REQUESTED_STREAM,
                        NewPipe.getNameOfService(serviceId),
                        url!!,
                        errorId)
            }
        }

        return true
    }

    private fun onBlockedByGemaError() {
        thumbnailBackgroundButton!!.setOnClickListener { v: View ->
            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(getString(R.string.c3s_url))
            }
//            intent.action = Intent.ACTION_VIEW
//            intent.data = Uri.parse(getString(R.string.c3s_url))
            startActivity(intent)
        }

        showError(getString(R.string.blocked_by_gema), false, R.drawable.gruese_die_gema)
    }

    private fun setRelatedStreamsVisibility(visibility: Int) {
        val parent = relatedStreamRootLayout!!.parent
        if (parent is ScrollView) {
            parent.visibility = visibility
        } else {
            relatedStreamRootLayout!!.visibility = visibility
        }
    }

    companion object {
        const val AUTO_PLAY = "auto_play"

        // Amount of videos to show on start
        private const val INITIAL_RELATED_VIDEOS = 8
        private const val RELATED_STREAMS_UPDATE_FLAG = 0x1
        private const val RESOLUTIONS_MENU_UPDATE_FLAG = 0x2
        private const val TOOLBAR_ITEMS_UPDATE_FLAG = 0x4


        //////////////////////////////////////////////////////////////////////////

        fun getInstance(serviceId: Int, videoUrl: String?, name: String): VideoDetailFragment {
            val instance = VideoDetailFragment()
            instance.setInitialData(serviceId, videoUrl, name)
            return instance
        }

        ///////////////////////////////////////////////////////////////////////////
        // State Saving
        ///////////////////////////////////////////////////////////////////////////

        private const val INFO_KEY = "info_key"
        private const val STACK_KEY = "stack_key"
        private const val WAS_RELATED_EXPANDED_KEY = "was_related_expanded_key"

        private fun showInstallKoreDialog(context: Context) {
            AlertDialog.Builder(context)
                    .setMessage(R.string.kore_not_found)
                    .setPositiveButton(R.string.install) { dialog: DialogInterface, which: Int -> NavigationHelper.installKore(context) }
                    .setNegativeButton(R.string.cancel) { dialog: DialogInterface, which: Int -> }
                    .create()
                    .show()
        }
    }
}