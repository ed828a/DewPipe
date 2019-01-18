package org.schabi.newpipe.settings

import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper
import java.util.*

/**
 * created by Edward 1/18/2019
 *
 * this class includes adapter and data model declaration.
 */
class SelectKioskFragment : DialogFragment() {

    var recyclerView: RecyclerView? = null
    var selectKioskAdapter: SelectKioskAdapter? = null

    private var onSelectedLisener: OnSelectedLisener? = null
    private var onCancelListener: OnCancelListener? = null

    ///////////////////////////////////////////////////////////////////////////
    // Interfaces
    ///////////////////////////////////////////////////////////////////////////

    interface OnSelectedLisener {
        fun onKioskSelected(serviceId: Int, kioskId: String, kioskName: String)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.select_kiosk_fragment, container, false)
        recyclerView = view.findViewById(R.id.items_list)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        try {
            selectKioskAdapter = SelectKioskAdapter()
        } catch (e: Exception) {
            onError(e)
        }

        recyclerView!!.adapter = selectKioskAdapter

        return view
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

    private fun clickedItem(entry: SelectKioskAdapter.Entry) {
        if (onSelectedLisener != null) {
            onSelectedLisener!!.onKioskSelected(entry.serviceId, entry.kioskId, entry.kioskName)
        }
        dismiss()
    }

    inner class SelectKioskAdapter @Throws(Exception::class)
    constructor() : RecyclerView.Adapter<SelectKioskAdapter.SelectKioskItemHolder>() {

        private val kioskList = Vector<Entry>()

        inner class Entry(internal val icon: Int, internal val serviceId: Int, internal val kioskId: String, internal val kioskName: String)

        init {
            Log.d(TAG, "NewPipe.getServices() = ${NewPipe.getServices()}")
            for (service in NewPipe.getServices()) {
                Log.d(TAG, "service.kioskList.availableKiosks = ${service.kioskList.availableKiosks}")

                if (service.serviceId != ServiceList.YouTube.serviceId && service.serviceId != ServiceList.SoundCloud.serviceId) {
                    Log.e(TAG, "there is wrong Service: ${service.serviceId}")
                    continue
                }

                for (kioskId in service.kioskList.availableKiosks) {
                    val name = String.format(getString(R.string.service_kiosk_string),
                            service.serviceInfo.name,
                            KioskTranslator.getTranslatedKioskName(kioskId, context!!))

                    val kioskEntry = Entry(
                            ServiceHelper.getIcon(service.serviceId),
                            service.serviceId,
                            kioskId,
                            name)

                    kioskList.add(kioskEntry)
                }
            }
        }

        override fun getItemCount(): Int = kioskList.size

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): SelectKioskItemHolder {
            val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.select_kiosk_item, parent, false)
            return SelectKioskItemHolder(item)
        }

        inner class SelectKioskItemHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val thumbnailView: ImageView = view.findViewById(R.id.itemThumbnailView)
            val titleView: TextView = view.findViewById(R.id.itemTitleView)
        }

        override fun onBindViewHolder(holder: SelectKioskItemHolder, position: Int) {
            val entry = kioskList[position]
            holder.titleView.text = entry.kioskName
            holder.thumbnailView.setImageDrawable(ContextCompat.getDrawable(context!!, entry.icon))
            holder.view.setOnClickListener { clickedItem(entry) }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Error
    ///////////////////////////////////////////////////////////////////////////

    private fun onError(e: Throwable) {
        val activity = activity
        activity?.let {fragmentActivity ->
            ErrorActivity.reportError(fragmentActivity, e,
                    fragmentActivity.javaClass, null,
                    ErrorInfo.make(UserAction.UI_ERROR,
                            "none", "", R.string.app_ui_crash))
        }

    }

    companion object {
        private const val TAG = "SelectKioskFragment"
    }
}
