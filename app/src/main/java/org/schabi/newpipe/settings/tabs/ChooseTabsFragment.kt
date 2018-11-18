package org.schabi.newpipe.settings.tabs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.content.res.AppCompatResources
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.settings.SelectChannelFragment
import org.schabi.newpipe.settings.SelectKioskFragment
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem
import org.schabi.newpipe.settings.tabs.ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder
import org.schabi.newpipe.util.ThemeHelper

import java.util.ArrayList
import java.util.Collections

import org.schabi.newpipe.settings.tabs.Tab.typeFrom

class ChooseTabsFragment : Fragment() {

    private var tabsManager: TabsManager? = null
    private val tabList = ArrayList<Tab>()
    var selectedTabsAdapter: ChooseTabsFragment.SelectedTabsAdapter? = null

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        private const val MENU_ITEM_RESTORE_ID = 123456
    }


    private val itemTouchCallback: ItemTouchHelper.SimpleCallback
        get() = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.START or ItemTouchHelper.END) {
            override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView, viewSize: Int,
                                                      viewSizeOutOfBounds: Int, totalSize: Int,
                                                      msSinceStartScroll: Long): Int {
                val standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll)
                val minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed))
                return minimumAbsVelocity * Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
            }

            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                if (source.itemViewType != target.itemViewType || selectedTabsAdapter == null) {
                    return false
                }

                val sourceIndex = source.adapterPosition
                val targetIndex = target.adapterPosition
                selectedTabsAdapter!!.swapItems(sourceIndex, targetIndex)
                return true
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                val position = viewHolder.adapterPosition
                tabList.removeAt(position)
                selectedTabsAdapter!!.notifyItemRemoved(position)

                if (tabList.isEmpty()) {
                    tabList.add(Tab.Type.BLANK.tab)
                    selectedTabsAdapter!!.notifyItemInserted(0)
                }
            }
        }

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tabsManager = TabsManager.getManager(requireContext())
        updateTabList()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_choose_tabs, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        initButton(rootView)

        val listSelectedTabs = rootView.findViewById<RecyclerView>(R.id.selectedTabs)
        listSelectedTabs.layoutManager = LinearLayoutManager(requireContext())

        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper.attachToRecyclerView(listSelectedTabs)

        selectedTabsAdapter = SelectedTabsAdapter(requireContext(), itemTouchHelper)
        listSelectedTabs.adapter = selectedTabsAdapter
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    override fun onPause() {
        super.onPause()
        saveChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val restoreItem = menu!!.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE, R.string.restore_defaults)
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        val restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_restore_defaults)
        restoreItem.icon = AppCompatResources.getDrawable(requireContext(), restoreIcon)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == MENU_ITEM_RESTORE_ID) {
            restoreDefaults()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun updateTabList() {
        tabList.clear()
        tabList.addAll(tabsManager!!.tabs)
    }

    private fun updateTitle() {
        if (activity is AppCompatActivity) {
            val actionBar = (activity as AppCompatActivity).supportActionBar
            actionBar?.setTitle(R.string.main_page_content)
        }
    }

    private fun saveChanges() {
        tabsManager!!.saveTabs(tabList)
    }

    private fun restoreDefaults() {
        AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes) { dialog, which ->
                    tabsManager!!.resetTabs()
                    updateTabList()
                    selectedTabsAdapter!!.notifyDataSetChanged()
                }
                .show()
    }

    private fun initButton(rootView: View) {
        val fab = rootView.findViewById<FloatingActionButton>(R.id.addTabsButton)
        fab.setOnClickListener { v ->
            val availableTabs = getAvailableTabs(requireContext())

            if (availableTabs.isEmpty()) {
                //Toast.makeText(requireContext(), "No available tabs", Toast.LENGTH_SHORT).show();
                return@setOnClickListener
            }

            val actionListener = DialogInterface.OnClickListener { dialog, which ->
                val selected = availableTabs[which]
                addTab(selected.tabId)
            }

            AddTabDialog(requireContext(), availableTabs, actionListener)
                    .show()
        }
    }

    private fun addTab(tab: Tab) {
        tabList.add(tab)
        selectedTabsAdapter!!.notifyDataSetChanged()
    }

    private fun addTab(tabId: Int) {
        val type = typeFrom(tabId)

        if (type == null) {
            ErrorActivity.reportError(requireContext(), IllegalStateException("Tab id not found: $tabId"), null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none", "Choosing tabs on settings", 0))
            return
        }

        when (type) {
            Tab.Type.KIOSK -> {
                val selectFragment = SelectKioskFragment()
                selectFragment.setOnSelectedLisener { serviceId, kioskId, kioskName -> addTab(Tab.KioskTab(serviceId, kioskId)) }
                selectFragment.show(requireFragmentManager(), "select_kiosk")
                return
            }
            Tab.Type.CHANNEL -> {
                val selectFragment = SelectChannelFragment()
                selectFragment.setOnSelectedLisener { serviceId, url, name -> addTab(Tab.ChannelTab(serviceId, url, name)) }
                selectFragment.show(requireFragmentManager(), "select_channel")
                return
            }
            else -> addTab(type.tab)
        }
    }

    fun getAvailableTabs(context: Context): Array<ChooseTabListItem> {
        val returnList = ArrayList<ChooseTabListItem>()

        for (type in Tab.Type.values()) {
            val tab = type.tab
            when (type) {
                Tab.Type.BLANK -> if (!tabList.contains(tab)) {
                    returnList.add(ChooseTabListItem(tab.tabId, getString(R.string.blank_page_summary),
                            tab.getTabIconRes(context)))
                }
                Tab.Type.KIOSK -> returnList.add(ChooseTabListItem(tab.tabId, getString(R.string.kiosk_page_summary),
                        ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_hot)))
                Tab.Type.CHANNEL -> returnList.add(ChooseTabListItem(tab.tabId, getString(R.string.channel_page_summary),
                        tab.getTabIconRes(context)))
                else -> if (!tabList.contains(tab)) {
                    returnList.add(ChooseTabListItem(context, tab))
                }
            }
        }

        return returnList.toTypedArray()
    }

    ///////////////////////////////////////////////////////////////////////////
    // List Handling
    ///////////////////////////////////////////////////////////////////////////

    inner class SelectedTabsAdapter internal constructor(context: Context, private val itemTouchHelper: ItemTouchHelper?) : RecyclerView.Adapter<TabViewHolder>() {
        private val inflater: LayoutInflater = LayoutInflater.from(context)

        fun swapItems(fromPosition: Int, toPosition: Int) {
            Collections.swap(tabList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
            val view = inflater.inflate(R.layout.list_choose_tabs, parent, false)
            return TabViewHolder(view)
        }

        override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
            holder.bind(position, holder)
        }

        override fun getItemCount(): Int {
            return tabList.size
        }

        inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tabIconView: AppCompatImageView
            private val tabNameView: TextView
            private val handle: ImageView

            init {

                tabNameView = itemView.findViewById(R.id.tabName)
                tabIconView = itemView.findViewById(R.id.tabIcon)
                handle = itemView.findViewById(R.id.handle)
            }

            @SuppressLint("ClickableViewAccessibility")
            fun bind(position: Int, holder: TabViewHolder) {
                handle.setOnTouchListener(getOnTouchListener(holder))

                val tab = tabList[position]
                val type = Tab.typeFrom(tab.tabId) ?: return

                var tabName = tab.getTabName(requireContext())
                when (type) {
                    Tab.Type.BLANK -> tabName = requireContext().getString(R.string.blank_page_summary)
                    Tab.Type.KIOSK -> tabName = NewPipe.getNameOfService((tab as Tab.KioskTab).kioskServiceId) + "/" + tabName
                    Tab.Type.CHANNEL -> tabName = NewPipe.getNameOfService((tab as Tab.ChannelTab).channelServiceId) + "/" + tabName
                }


                tabNameView.text = tabName
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()))
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun getOnTouchListener(item: RecyclerView.ViewHolder): View.OnTouchListener {
                return View.OnTouchListener{ view, motionEvent ->
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && itemCount > 1) {
                            itemTouchHelper.startDrag(item)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        }
    }
}
