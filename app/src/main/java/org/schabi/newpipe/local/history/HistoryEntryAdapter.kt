package org.schabi.newpipe.local.history

import android.content.Context
import org.schabi.newpipe.util.Localization
import java.text.DateFormat
import java.util.*

// no usage of this class, Edward
/**
 * Adapter for history entries
 * @param <E> the type of the entries
 * @param <VH> the type of the view holder
</VH></E> */
abstract class HistoryEntryAdapter<E, VH : androidx.recyclerview.widget.RecyclerView.ViewHolder>(private val mContext: Context) : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {

    private val mEntries: ArrayList<E> = ArrayList()
    private val mDateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Localization.getPreferredLocale(mContext))
    private var onHistoryItemClickListener: OnHistoryItemClickListener<E>? = null

    val items: Collection<E>
        get() = mEntries

    val isEmpty: Boolean
        get() = mEntries.isEmpty()


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

    override fun getItemCount(): Int = mEntries.size


    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = mEntries[position]
        holder.itemView.setOnClickListener { v ->
                onHistoryItemClickListener?.onHistoryItemClick(entry)
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
