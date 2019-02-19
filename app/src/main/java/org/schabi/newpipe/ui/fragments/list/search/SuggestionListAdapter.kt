package org.schabi.newpipe.ui.fragments.list.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import org.schabi.newpipe.R
import java.util.*

class SuggestionListAdapter(private val context: Context) : androidx.recyclerview.widget.RecyclerView.Adapter<SuggestionListAdapter.SuggestionItemHolder>() {
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

    fun setShowSuggestionHistory(visible: Boolean) {
        showSuggestionHistory = visible
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

    class SuggestionItemHolder (rootView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(rootView) {
        private val itemSuggestionQuery: TextView = rootView.findViewById(R.id.item_suggestion_query)
        private val suggestionIcon: ImageView = rootView.findViewById(R.id.item_suggestion_icon)
        val queryView: View = rootView.findViewById(R.id.suggestion_search)
        val insertView: View = rootView.findViewById(R.id.suggestion_insert)

        // Cache some ids, as they can potentially be constantly updated/recycled
        private val historyResId: Int
        private val searchResId: Int

        init {

            historyResId = resolveResourceIdFromAttr(rootView.context, R.attr.history)
            searchResId = resolveResourceIdFromAttr(rootView.context, R.attr.search)
        }

        fun updateFrom(item: SuggestionItem) {
            suggestionIcon.setImageResource(if (item.fromHistory) historyResId else searchResId)
            itemSuggestionQuery.text = item.query
        }

        private fun resolveResourceIdFromAttr(context: Context, @AttrRes attr: Int): Int {
            val typedArray = context.theme.obtainStyledAttributes(intArrayOf(attr))
            val attributeResourceId = typedArray.getResourceId(0, 0)
            typedArray.recycle()
            return attributeResourceId
        }
    }
}
