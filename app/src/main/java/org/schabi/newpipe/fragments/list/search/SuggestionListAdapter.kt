package org.schabi.newpipe.fragments.list.search

import android.content.Context
import android.content.res.TypedArray
import android.support.annotation.AttrRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R

import java.util.ArrayList

class SuggestionListAdapter(private val context: Context) : RecyclerView.Adapter<SuggestionListAdapter.SuggestionItemHolder>() {
    private val items = ArrayList<SuggestionItem>()
    private var listener: OnSuggestionItemSelected? = null
    private var showSuggestionHistory = true

    val isEmpty: Boolean
        get() = itemCount == 0

    interface OnSuggestionItemSelected {
        fun onSuggestionItemSelected(item: SuggestionItem)
        fun onSuggestionItemInserted(item: SuggestionItem)
        fun onSuggestionItemLongClick(item: SuggestionItem)
    }

    fun setItems(items: List<SuggestionItem>) {
        this.items.clear()
        if (showSuggestionHistory) {
            this.items.addAll(items)
        } else {
            // remove history items if history is disabled
            for (item in items) {
                if (!item.fromHistory) {
                    this.items.add(item)
                }
            }
        }
        notifyDataSetChanged()
    }

    fun setListener(listener: OnSuggestionItemSelected) {
        this.listener = listener
    }

    fun setShowSuggestionHistory(v: Boolean) {
        showSuggestionHistory = v
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionItemHolder {
        return SuggestionItemHolder(LayoutInflater.from(context).inflate(R.layout.item_search_suggestion, parent, false))
    }

    override fun onBindViewHolder(holder: SuggestionItemHolder, position: Int) {
        val currentItem = getItem(position)
        holder.updateFrom(currentItem)
        holder.queryView.setOnClickListener { v -> if (listener != null) listener!!.onSuggestionItemSelected(currentItem) }
        holder.queryView.setOnLongClickListener { v ->
            if (listener != null) listener!!.onSuggestionItemLongClick(currentItem)
            true
        }
        holder.insertView.setOnClickListener { v -> if (listener != null) listener!!.onSuggestionItemInserted(currentItem) }
    }

    private fun getItem(position: Int): SuggestionItem {
        return items[position]
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class SuggestionItemHolder (rootView: View) : RecyclerView.ViewHolder(rootView) {
        private val itemSuggestionQuery: TextView
        private val suggestionIcon: ImageView
        val queryView: View
        val insertView: View

        // Cache some ids, as they can potentially be constantly updated/recycled
        private val historyResId: Int
        private val searchResId: Int

        init {
            suggestionIcon = rootView.findViewById(R.id.item_suggestion_icon)
            itemSuggestionQuery = rootView.findViewById(R.id.item_suggestion_query)

            queryView = rootView.findViewById(R.id.suggestion_search)
            insertView = rootView.findViewById(R.id.suggestion_insert)

            historyResId = resolveResourceIdFromAttr(rootView.context, R.attr.history)
            searchResId = resolveResourceIdFromAttr(rootView.context, R.attr.search)
        }

        fun updateFrom(item: SuggestionItem) {
            suggestionIcon.setImageResource(if (item.fromHistory) historyResId else searchResId)
            itemSuggestionQuery.text = item.query
        }

        private fun resolveResourceIdFromAttr(context: Context, @AttrRes attr: Int): Int {
            val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
            val attributeResourceId = a.getResourceId(0, 0)
            a.recycle()
            return attributeResourceId
        }
    }
}
