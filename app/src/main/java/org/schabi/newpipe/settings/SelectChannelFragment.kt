package org.schabi.newpipe.settings

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView

import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader

import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.local.subscription.SubscriptionService
import java.util.Vector

import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.report.ErrorInfo


/**
 * Created by Christian Schabesberger on 26.09.17.
 * SelectChannelFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

class SelectChannelFragment : DialogFragment() {
    private val imageLoader = ImageLoader.getInstance()

    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null
    private var recyclerView: RecyclerView? = null

    private var subscriptions: List<SubscriptionEntity> = Vector()
    internal var onSelectedLisener: OnSelectedLisener? = null
    internal var onCancelListener: OnCancelListener? = null

    private val subscriptionObserver: Observer<List<SubscriptionEntity>>
        get() = object : Observer<List<SubscriptionEntity>> {
            override fun onSubscribe(d: Disposable) {}

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
        val v = inflater.inflate(R.layout.select_channel_fragment, container, false)
        recyclerView = v.findViewById(R.id.items_list)
        recyclerView!!.layoutManager = LinearLayoutManager(context)
        val channelAdapter = SelectChannelAdapter()
        recyclerView!!.adapter = channelAdapter

        progressBar = v.findViewById(R.id.progressBar)
        emptyView = v.findViewById(R.id.empty_state_view)
        progressBar!!.visibility = View.VISIBLE
        recyclerView!!.visibility = View.GONE
        emptyView!!.visibility = View.GONE


        val subscriptionService = SubscriptionService.getInstance(context!!)
        subscriptionService.subscription.toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriptionObserver)

        return v
    }


    ///////////////////////////////////////////////////////////////////////////
    // Handle actions
    ///////////////////////////////////////////////////////////////////////////

    override fun onCancel(dialogInterface: DialogInterface?) {
        super.onCancel(dialogInterface)
        if (onCancelListener !=
                null) {
            onCancelListener!!.onCancel()
        }
    }

    private fun clickedItem(position: Int) {
        if (onSelectedLisener != null) {
            val entry = subscriptions[position]
            onSelectedLisener!!.onChannelSelected(entry.serviceId, entry.url!!, entry.name!!)
        }
        dismiss()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Item handling
    ///////////////////////////////////////////////////////////////////////////

    private fun displayChannels(subscriptions: List<SubscriptionEntity>) {
        this.subscriptions = subscriptions
        progressBar!!.visibility = View.GONE
        if (subscriptions.isEmpty()) {
            emptyView!!.visibility = View.VISIBLE
            return
        }
        recyclerView!!.visibility = View.VISIBLE

    }

    private inner class SelectChannelAdapter : RecyclerView.Adapter<SelectChannelAdapter.SelectChannelItemHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectChannelItemHolder {
            val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.select_channel_item, parent, false)
            return SelectChannelItemHolder(item)
        }

        override fun onBindViewHolder(holder: SelectChannelItemHolder, position: Int) {
            val entry = subscriptions[position]
            holder.titleView.text = entry.name
            holder.view.setOnClickListener { clickedItem(position) }
            imageLoader.displayImage(entry.avatarUrl, holder.thumbnailView, DISPLAY_IMAGE_OPTIONS)
        }

        override fun getItemCount(): Int {
            return subscriptions.size
        }

        inner class SelectChannelItemHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val thumbnailView: CircleImageView
            val titleView: TextView

            init {
                thumbnailView = view.findViewById(R.id.itemThumbnailView)
                titleView = view.findViewById(R.id.itemTitleView)
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error
    ///////////////////////////////////////////////////////////////////////////

    protected fun onError(e: Throwable) {
        val activity = activity
        ErrorActivity.reportError(activity!!, e,
                activity!!.javaClass, null,
                ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash))
    }

    companion object {


        ///////////////////////////////////////////////////////////////////////////
        // ImageLoaderOptions
        ///////////////////////////////////////////////////////////////////////////

        /**
         * Base display options
         */
        val DISPLAY_IMAGE_OPTIONS = DisplayImageOptions.Builder()
                .cacheInMemory(true)
                .build()!!
    }
}
