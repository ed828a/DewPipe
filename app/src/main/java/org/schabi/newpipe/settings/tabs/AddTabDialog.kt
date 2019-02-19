package org.schabi.newpipe.settings.tabs

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.annotation.DrawableRes
import kotlinx.android.synthetic.main.list_choose_tabs_dialog.view.*
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

class AddTabDialog(context: Context,
                   items: Array<ChooseTabListItem>,
                   actions: DialogInterface.OnClickListener) {

    private val dialog: AlertDialog

    init {

        dialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tab_choose))
                .setAdapter(DialogListAdapter(context, items), actions)
                .create()
    }

    fun show() {
        dialog.show()
    }

    data class ChooseTabListItem(internal val tabId: Int,
                                 internal val itemName: String,
                                 @param:DrawableRes
                                 @field:DrawableRes
                                 val itemIcon: Int) {

        internal constructor(context: Context, tab: Tab) : this(tab.tabId, tab.getTabName(context), tab.getTabIconRes(context)) {}
    }

    private class DialogListAdapter(context: Context, private val items: Array<ChooseTabListItem>) : BaseAdapter() {
        @DrawableRes
        private val fallbackIcon: Int = ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_hot)

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): ChooseTabListItem = items[position]

        override fun getItemId(position: Int): Long = getItem(position).tabId.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.list_choose_tabs_dialog, parent, false)

            val item = getItem(position)

            view.tabIcon.setImageResource(if (item.itemIcon > 0) item.itemIcon else fallbackIcon)
            view.tabName.text = item.itemName

            return view
        }
    }
}
