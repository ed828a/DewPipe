package org.schabi.newpipe.local

import android.app.Activity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.info_list.holder.FallbackViewHolder
import org.schabi.newpipe.info_list.holder.HeaderFooterHolder
import org.schabi.newpipe.local.holder.*
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.OnClickGesture
import java.text.DateFormat
import java.util.*


class LocalItemListAdapter(activity: Activity?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val localItemBuilder: LocalItemBuilder = LocalItemBuilder(activity)
    val itemsList: ArrayList<LocalItem> = ArrayList()
    private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Localization.getPreferredLocale(activity!!))

    private var showFooter = false
    private var useGridVariant = false
    private var header: View? = null
    private var footer: View? = null

    fun setSelectedListener(listener: OnClickGesture<LocalItem>) {
        localItemBuilder.onItemSelectedListener = listener
    }

    fun unsetSelectedListener() {
        localItemBuilder.onItemSelectedListener = null
    }

    fun addItems(data: List<LocalItem>?) {
        if (data != null) {

            Log.d(TAG, "addItems() before > localItems.size() = ${itemsList.size}, data.size() = ${data.size}")

            val offsetStart = sizeConsideringHeader()
            itemsList.addAll(data)

            Log.d(TAG, "addItems() after > offsetStart = $offsetStart, localItems.size() = ${itemsList.size}, header = $header, footer = $footer, showFooter = $showFooter")

            notifyItemRangeInserted(offsetStart, data.size)

            if (footer != null && showFooter) {
                val footerNow = sizeConsideringHeader()
                notifyItemMoved(offsetStart, footerNow)

                Log.d(TAG, "addItems() footer getTabFrom $offsetStart to $footerNow")
            }
        }
    }

    fun removeItem(data: LocalItem) {
        val index = itemsList.indexOf(data)

        itemsList.removeAt(index)
        notifyItemRemoved(index + if (header != null) 1 else 0)
    }

    fun swapItems(fromAdapterPosition: Int, toAdapterPosition: Int): Boolean {
        val actualFrom = adapterOffsetWithoutHeader(fromAdapterPosition)
        val actualTo = adapterOffsetWithoutHeader(toAdapterPosition)

        if (actualFrom < 0 || actualTo < 0) return false
        if (actualFrom >= itemsList.size || actualTo >= itemsList.size) return false

        itemsList.add(actualTo, itemsList.removeAt(actualFrom))
        notifyItemMoved(fromAdapterPosition, toAdapterPosition)
        return true
    }

    fun clearStreamItemList() {
        if (itemsList.isEmpty()) {
            return
        }
        itemsList.clear()
        notifyDataSetChanged()
    }

    fun setGridItemVariants(useGridVariant: Boolean) {
        this.useGridVariant = useGridVariant
    }

    fun setHeader(header: View) {
        val changed = header !== this.header
        this.header = header
        if (changed) notifyDataSetChanged()
    }

    fun setFooter(view: View) {
        this.footer = view
    }

    fun showFooter(show: Boolean) {
        Log.d(TAG, "showFooter() called with: show = [$show]")
        if (show == showFooter) return

        showFooter = show
        if (show)
            notifyItemInserted(sizeConsideringHeader())
        else
            notifyItemRemoved(sizeConsideringHeader())
    }

    private fun adapterOffsetWithoutHeader(offset: Int): Int {
        return offset - if (header != null) 1 else 0
    }

    private fun sizeConsideringHeader(): Int {
        return itemsList.size + if (header != null) 1 else 0
    }

    override fun getItemCount(): Int {
        var count = itemsList.size
        if (header != null) count++
        if (footer != null && showFooter) count++

        Log.d(TAG, "getItemCount() called, count = $count, localItems.size() = ${itemsList.size}, header = $header, footer = $footer, showFooter = $showFooter")

        return count
    }

    override fun getItemViewType(pos: Int): Int {
        var position = pos
        Log.d(TAG, "getItemViewType() called with: position = [$position]")

        if (header != null && position == 0) {
            return HEADER_TYPE
        } else if (header != null) {
            position--
        }
        if (footer != null && position == itemsList.size && showFooter) {
            return FOOTER_TYPE
        }
        val item = itemsList[position]

        return when (item.localItemType) {
            LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM -> if (useGridVariant) LOCAL_PLAYLIST_GRID_HOLDER_TYPE else LOCAL_PLAYLIST_HOLDER_TYPE
            LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM -> if (useGridVariant) REMOTE_PLAYLIST_GRID_HOLDER_TYPE else REMOTE_PLAYLIST_HOLDER_TYPE

            LocalItem.LocalItemType.PLAYLIST_STREAM_ITEM -> if (useGridVariant) STREAM_PLAYLIST_GRID_HOLDER_TYPE else STREAM_PLAYLIST_HOLDER_TYPE
            LocalItem.LocalItemType.STATISTIC_STREAM_ITEM -> if (useGridVariant) STREAM_STATISTICS_GRID_HOLDER_TYPE else STREAM_STATISTICS_HOLDER_TYPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {

        Log.d(TAG, "onCreateViewHolder() called with: parent = [$parent], type = [$type]")

        when (type) {
            HEADER_TYPE -> return HeaderFooterHolder(header!!)
            FOOTER_TYPE -> return HeaderFooterHolder(footer!!)
            LOCAL_PLAYLIST_HOLDER_TYPE -> return LocalPlaylistItemHolder(localItemBuilder, parent)
            LOCAL_PLAYLIST_GRID_HOLDER_TYPE -> return LocalPlaylistGridItemHolder(localItemBuilder, parent)
            REMOTE_PLAYLIST_HOLDER_TYPE -> return RemotePlaylistItemHolder(localItemBuilder, parent)
            REMOTE_PLAYLIST_GRID_HOLDER_TYPE -> return RemotePlaylistGridItemHolder(localItemBuilder, parent)
            STREAM_PLAYLIST_HOLDER_TYPE -> return LocalPlaylistStreamItemHolder(localItemBuilder, parent)
            STREAM_PLAYLIST_GRID_HOLDER_TYPE -> return LocalPlaylistStreamGridItemHolder(localItemBuilder, parent)
            STREAM_STATISTICS_HOLDER_TYPE -> return LocalStatisticStreamItemHolder(localItemBuilder, parent)
            STREAM_STATISTICS_GRID_HOLDER_TYPE -> return LocalStatisticStreamGridItemHolder(localItemBuilder, parent)
            else -> {
                Log.e(TAG, "No view type has been considered for holder: [$type]")
                return FallbackViewHolder(View(parent.context))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        var position = pos
        Log.d(TAG, "onBindViewHolder() called with: holder = [${holder.javaClass.simpleName}], position = [$position]")

        when {
            holder is LocalItemHolder -> {
                // If header isn't null, offset the items by -1
                if (header != null) position--

                holder.updateFromItem(itemsList[position], dateFormat)
            }

            holder is HeaderFooterHolder && position == 0 && header != null -> holder.view = header!!

            holder is HeaderFooterHolder && position == sizeConsideringHeader() && footer != null && showFooter -> holder.view = footer!!
        }
    }

    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val type = getItemViewType(position)
                return if (type == HEADER_TYPE || type == FOOTER_TYPE) spanCount else 1
            }
        }


    companion object {

        private val TAG = LocalItemListAdapter::class.java.simpleName

        private const val HEADER_TYPE = 0
        private const val FOOTER_TYPE = 1

        private const val STREAM_STATISTICS_HOLDER_TYPE = 0x1000
        private const val STREAM_PLAYLIST_HOLDER_TYPE = 0x1001
        private const val STREAM_STATISTICS_GRID_HOLDER_TYPE = 0x1002
        private const val STREAM_PLAYLIST_GRID_HOLDER_TYPE = 0x1004
        private const val LOCAL_PLAYLIST_HOLDER_TYPE = 0x2000
        private const val REMOTE_PLAYLIST_HOLDER_TYPE = 0x2001
        private const val LOCAL_PLAYLIST_GRID_HOLDER_TYPE = 0x2002
        private const val REMOTE_PLAYLIST_GRID_HOLDER_TYPE = 0x2004
    }
}
