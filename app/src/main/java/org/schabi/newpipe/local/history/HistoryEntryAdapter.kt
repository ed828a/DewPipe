package org.schabi.newpipe.local.history

import android.content.Context
import android.support.v7.widget.RecyclerView

import org.schabi.newpipe.util.Localization

import java.text.DateFormat
import java.util.ArrayList
import java.util.Date

// no usage of this class, Edward
/**
 * Adapter for history entries
 * @param <E> the type of the entries
 * @param <VH> the type of the view holder
</VH></E> */
abstract class HistoryEntryAdapter<E, VH : RecyclerView.ViewHolder>(private val mContext: Context) : RecyclerView.Adapter<VH>() {

    private val mEntries: ArrayList<E>
    private val mDateFormat: DateFormat
    private var onHistoryItemClickListener: OnHistoryItemClickListener<E>? = null

    val items: Collection<E>
        get() = mEntries

    val isEmpty: Boolean
        get() = mEntries.isEmpty()


    init {
        mEntries = ArrayList()
        mDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM,
                Localization.getPreferredLocale(mContext))
    }

    fun setEntries(historyEntries: Collection<E>) {
        mEntries.clear()
        mEntries.addAll(historyEntries)
        notifyDataSetChanged()
    }

    fun clear() {
        mEntries.clear()
        notifyDataSetChanged()
    }

    protected fun getFormattedDate(date: Date): String {
        return mDateFormat.format(date)
    }

    protected fun getFormattedViewString(viewCount: Long): String {
        return Localization.shortViewCount(mContext, viewCount)
    }

    override fun getItemCount(): Int {
        return mEntries.size
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = mEntries[position]
        holder.itemView.setOnClickListener { v ->
            if (onHistoryItemClickListener != null) {
                onHistoryItemClickListener!!.onHistoryItemClick(entry)
            }
        }

        holder.itemView.setOnLongClickListener { view ->
            if (onHistoryItemClickListener != null) {
                onHistoryItemClickListener!!.onHistoryItemLongClick(entry)
                return@setOnLongClickListener true
            }
            false
        }

        onBindViewHolder(holder, entry, position)
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.itemView.setOnClickListener(null)
    }

    internal abstract fun onBindViewHolder(holder: VH, entry: E, position: Int)

    fun setOnHistoryItemClickListener(onHistoryItemClickListener: OnHistoryItemClickListener<E>?) {
        this.onHistoryItemClickListener = onHistoryItemClickListener
    }

    interface OnHistoryItemClickListener<E> {
        fun onHistoryItemClick(item: E)
        fun onHistoryItemLongClick(item: E)
    }
}
