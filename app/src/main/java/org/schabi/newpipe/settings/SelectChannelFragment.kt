package org.schabi.newpipe.settings

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionService
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import java.util.*


class SelectChannelFragment : androidx.fragment.app.DialogFragment() {
    private val imageLoader: ImageLoader = ImageLoader.getInstance()

    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView

    private var subscriptions: List<SubscriptionEntity> = Vector()
    internal var onSelectedLisener: OnSelectedLisener? = null
    internal var onCancelListener: OnCancelListener? = null

    private val subscriptionObserver: Observer<List<SubscriptionEntity>>
        get() = object : Observer<List<SubscriptionEntity>> {
            override fun onSubscribe(d: Disposable) {

            }

            override fun onNext(subscriptions: List<SubscriptionEntity>) {
                displayChannels(subscriptions)
            }

            override fun onError(exception: Throwable) {
                this@SelectChannelFragment.onError(exception)
            }

            override fun onComplete() {}
        }

    ///////////////////////////////////////////////////////////////////////////
    // Interfaces
    ///////////////////////////////////////////////////////////////////////////

    interface OnSelectedLisener {
        fun onChannelSelected(serviceId: Int, url: String, name: String)
    }

    fun setOnSelectedLisener(listener: OnSelectedLisener) {
        onSelectedLisener = listener
    }

    interface OnCancelListener {
        fun onCancel()
    }

    fun setOnCancelListener(listener: OnCancelListener) {
        onCancelListener = listener
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.select_channel_fragment, container, false)
        recyclerView = view.findViewById(R.id.items_list)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val channelAdapter = SelectChannelAdapter()
        recyclerView.adapter = channelAdapter

        progressBar = view.findViewById(R.id.progressBar)
        emptyView = view.findViewById(R.id.empty_state_view)
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE


        val subscriptionService = SubscriptionService.getInstance(context!!)
        subscriptionService.subscription.toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriptionObserver)

        return view
    }


    ///////////////////////////////////////////////////////////////////////////
    // Handle actions
    ///////////////////////////////////////////////////////////////////////////

    override fun onCancel(dialogInterface: DialogInterface) {
        super.onCancel(dialogInterface)
        if (onCancelListener != null) {
            onCancelListener?.onCancel()
        }
    }

    private fun clickedItem(position: Int) {
        if (onSelectedLisener != null) {
            val entry = subscriptions[position]
            if (entry.url != null && entry.name != null) {
                onSelectedLisener?.onChannelSelected(entry.serviceId, entry.url!!, entry.name!!)
            }
        }
        dismiss()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Item handling
    ///////////////////////////////////////////////////////////////////////////

    private fun displayChannels(subscriptions: List<SubscriptionEntity>) {
        this.subscriptions = subscriptions
        progressBar.visibility = View.GONE
        if (subscriptions.isEmpty()) {
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
        }

    }

    private inner class SelectChannelAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<SelectChannelAdapter.SelectChannelViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectChannelViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.select_channel_item, parent, false)

            return SelectChannelViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelectChannelViewHolder, position: Int) {
            val entry = subscriptions[position]
            holder.titleView.text = entry.name
            holder.view.setOnClickListener { clickedItem(position) }
            imageLoader.displayImage(entry.avatarUrl, holder.thumbnailView, DISPLAY_IMAGE_OPTIONS)
        }

        override fun getItemCount(): Int = subscriptions.size

        inner class SelectChannelViewHolder(val view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val thumbnailView: CircleImageView = view.findViewById(R.id.itemThumbnailView)
            val titleView: TextView = view.findViewById(R.id.itemTitleView)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error
    ///////////////////////////////////////////////////////////////////////////

    private fun onError(e: Throwable) {
        val activity = activity
        activity?.let { fragmentActivity ->
            ErrorActivity.reportError(activity!!, e,
                    fragmentActivity.javaClass, null,
                    ErrorInfo.make(UserAction.UI_ERROR,
                            "none", "", R.string.app_ui_crash))
        }

    }

    companion object {
        private const val TAG = "SelectChannelFragment"
        ///////////////////////////////////////////////////////////////////////////
        // ImageLoaderOptions
        ///////////////////////////////////////////////////////////////////////////
        /**
         * Base display options
         */
        val DISPLAY_IMAGE_OPTIONS: DisplayImageOptions = DisplayImageOptions.Builder()
                .cacheInMemory(true)
                .build()
    }
}
