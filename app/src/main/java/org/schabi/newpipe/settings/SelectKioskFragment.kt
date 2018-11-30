package org.schabi.newpipe.settings

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper
import java.util.Vector

/**
 * Created by Christian Schabesberger on 09.10.17.
 * SelectKioskFragment.java is part of NewPipe.
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

class SelectKioskFragment : DialogFragment() {

    internal var recyclerView: RecyclerView? = null
    internal var selectKioskAdapter: SelectKioskAdapter? = null

    internal var onSelectedLisener: OnSelectedLisener? = null
    internal var onCancelListener: OnCancelListener? = null

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
        val v = inflater.inflate(R.layout.select_kiosk_fragment, container, false)
        recyclerView = v.findViewById(R.id.items_list)
        recyclerView!!.layoutManager = LinearLayoutManager(context)
        try {
            selectKioskAdapter = SelectKioskAdapter()
        } catch (e: Exception) {
            onError(e)
        }

        recyclerView!!.adapter = selectKioskAdapter

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

            for (service in NewPipe.getServices()) {
                //TODO: Multi-service support
                if (service.serviceId != ServiceList.YouTube.serviceId && !DEBUG) continue

                for (kioskId in service.kioskList.availableKiosks) {
                    val name = String.format(getString(R.string.service_kiosk_string),
                            service.serviceInfo.name,
                            KioskTranslator.getTranslatedKioskName(kioskId, context!!))
                    kioskList.add(Entry(
                            ServiceHelper.getIcon(service.serviceId),
                            service.serviceId,
                            kioskId,
                            name))
                }
            }
        }

        override fun getItemCount(): Int {
            return kioskList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): SelectKioskItemHolder {
            val item = LayoutInflater.from(parent.context)
                    .inflate(R.layout.select_kiosk_item, parent, false)
            return SelectKioskItemHolder(item)
        }

        inner class SelectKioskItemHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val thumbnailView: ImageView
            val titleView: TextView

            init {
                thumbnailView = view.findViewById(R.id.itemThumbnailView)
                titleView = view.findViewById(R.id.itemTitleView)
            }
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

    protected fun onError(e: Throwable) {
        val activity = activity
        ErrorActivity.reportError(activity!!, e,
                activity!!.javaClass, null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash))
    }

    companion object {

        private val DEBUG = MainActivity.DEBUG
    }
}
