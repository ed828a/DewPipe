package org.schabi.newpipe.settings.tabs

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.support.annotation.DrawableRes
import android.support.v7.widget.AppCompatImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

class AddTabDialog internal constructor(context: Context,
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

    class ChooseTabListItem internal constructor(internal val tabId: Int, internal val itemName: String, @param:DrawableRes @field:DrawableRes internal val itemIcon: Int) {

        internal constructor(context: Context, tab: Tab) : this(tab.tabId, tab.getTabName(context), tab.getTabIconRes(context)) {}
    }

    private class DialogListAdapter (context: Context, private val items: Array<ChooseTabListItem>) : BaseAdapter() {
        private val inflater: LayoutInflater = LayoutInflater.from(context)

        @DrawableRes
        private val fallbackIcon: Int = ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_hot)

        override fun getCount(): Int {
            return items.size
        }

        override fun getItem(position: Int): ChooseTabListItem {
            return items[position]
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).tabId.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false)
            }

            val item = getItem(position)
            val tabIconView = convertView!!.findViewById<AppCompatImageView>(R.id.tabIcon)
            val tabNameView = convertView.findViewById<TextView>(R.id.tabName)

            tabIconView.setImageResource(if (item.itemIcon > 0) item.itemIcon else fallbackIcon)
            tabNameView.text = item.itemName

            return convertView
        }
    }
}
