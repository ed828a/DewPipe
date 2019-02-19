package org.schabi.newpipe.settings.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.settings.SelectChannelFragment
import org.schabi.newpipe.settings.SelectKioskFragment
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem
import org.schabi.newpipe.settings.tabs.Tab.Companion.getTypeFrom
import org.schabi.newpipe.util.ThemeHelper
import java.util.*


class ChooseTabsFragment : androidx.fragment.app.Fragment() {

    private lateinit var tabsManager: TabsManager
    private val tabList = ArrayList<Tab>()
    lateinit var selectedTabsAdapter: ChooseTabsFragment.SelectedTabsAdapter

    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tabsManager = TabsManager.getTabsManager(requireContext())  // requireContext() is better than getContext()
        updateTabList()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_choose_tabs, container, false)

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        initButton(rootView)

        val listSelectedTabs = rootView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.selectedTabs)
        listSelectedTabs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        val itemTouchHelper = ItemTouchHelper(getItemTouchCallback())
        itemTouchHelper.attachToRecyclerView(listSelectedTabs)

        selectedTabsAdapter = SelectedTabsAdapter(tabList, itemTouchHelper)
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


    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        private const val MENU_ITEM_RESTORE_ID = 123456
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        val restoreItem = menu?.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE, R.string.restore_defaults)
        restoreItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        val restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_restore_defaults)
        restoreItem?.icon = AppCompatResources.getDrawable(requireContext(), restoreIcon)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            if (MENU_ITEM_RESTORE_ID == item.itemId) {
                restoreDefaults()
                true
            } else {
                super.onOptionsItemSelected(item)
            }


    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun updateTabList() {
        tabList.clear()
        tabList.addAll(tabsManager.getTabs())
    }

    private fun updateTitle() {
        if (activity is AppCompatActivity) {
            val actionBar = (activity as AppCompatActivity).supportActionBar
            actionBar?.setTitle(R.string.main_page_content)
        }
    }

    private fun saveChanges() {
        tabsManager.saveTabs(tabList) // save tabList to SharedPreference
    }

    private fun restoreDefaults() {
        AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes) { dialog, which ->
                    tabsManager.resetTabs()  // just remove the saved List<Tab> in SharedPreference
                    updateTabList()  // in this case, updateTabList will get the default list.
                    selectedTabsAdapter.notifyDataSetChanged()
                }
                .show()
    }

    private fun initButton(rootView: View) {
        val fab = rootView.findViewById<FloatingActionButton>(R.id.addTabsButton)
        fab.setOnClickListener { v ->
            val availableTabs = getAvailableTabs(requireContext())

            if (availableTabs.isEmpty()) {
                Toast.makeText(requireContext(), "No available tabs", Toast.LENGTH_SHORT).show();
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
        selectedTabsAdapter.notifyDataSetChanged()
    }

    private fun addTab(tabId: Int) {
        val type = getTypeFrom(tabId)

        if (type == null) {
            ErrorActivity.reportError(requireContext(), IllegalStateException("Tab id not found: $tabId"), null, null,
                    ErrorInfo.make(UserAction.SOMETHING_ELSE, "none", "Choosing tabs on settings", 0))
            return
        }

        when (type) {
            Tab.Type.KIOSK -> {
                val selectFragment = SelectKioskFragment()
                selectFragment.setOnSelectedLisener(object : SelectKioskFragment.OnSelectedLisener {
                    override fun onKioskSelected(serviceId: Int, kioskId: String, kioskName: String) {
                        addTab(Tab.KioskTab(serviceId, kioskId))
                    }
                })

                selectFragment.show(requireFragmentManager(), "select_kiosk")
                return
            }
            Tab.Type.CHANNEL -> {
                val selectFragment = SelectChannelFragment()
                selectFragment.setOnSelectedLisener(object : SelectChannelFragment.OnSelectedLisener {
                    override fun onChannelSelected(serviceId: Int, url: String, name: String) {
                        addTab(Tab.ChannelTab(serviceId, url, name))
                    }
                })

                selectFragment.show(requireFragmentManager(), "select_channel")
                return
            }
            else -> addTab(type.tab)
        }
    }

    private fun getAvailableTabs(context: Context): Array<ChooseTabListItem> {
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

        // ArrayList.toTypedArray: convert ArrayList to Array
        return returnList.toTypedArray()
    }


    ///////////////////////////////////////////////////////////////////////////
    // List Handling
    ///////////////////////////////////////////////////////////////////////////

    inner class SelectedTabsAdapter(val tabList: List<Tab>, private val itemTouchHelper: ItemTouchHelper?) : androidx.recyclerview.widget.RecyclerView.Adapter<SelectedTabsAdapter.TabViewHolder>() {

        fun swapItems(fromPosition: Int, toPosition: Int) {
            Collections.swap(tabList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_choose_tabs, parent, false)
            return TabViewHolder(view)
        }

        override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
            holder.bind(position, holder)
        }

        override fun getItemCount(): Int = tabList.size

        inner class TabViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val tabIconView: AppCompatImageView = itemView.findViewById(R.id.tabIcon)
            private val tabNameView: TextView = itemView.findViewById(R.id.tabName)
            private val handle: ImageView = itemView.findViewById(R.id.handle)

            @SuppressLint("ClickableViewAccessibility")
            fun bind(position: Int, holder: TabViewHolder) {
                handle.setOnTouchListener(getOnTouchListener(holder))

                val tab = tabList[position]
                val type = Tab.getTypeFrom(tab.tabId) ?: return
//                var tabName = tab.getTabName(requireContext())
                var tabName = tab.getTabName(itemView.context)
                when (type) {
                    Tab.Type.BLANK -> tabName = requireContext().getString(R.string.blank_page_summary)
                    Tab.Type.KIOSK -> tabName = "${NewPipe.getNameOfService((tab as Tab.KioskTab).kioskServiceId)}/$tabName"
                    Tab.Type.CHANNEL -> tabName = "${NewPipe.getNameOfService((tab as Tab.ChannelTab).channelServiceId)}/$tabName"
                }

                tabNameView.text = tabName
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()))
            }

            @SuppressLint("ClickableViewAccessibility")
            private fun getOnTouchListener(item: androidx.recyclerview.widget.RecyclerView.ViewHolder): View.OnTouchListener =
                    View.OnTouchListener { view, motionEvent ->
                        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
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


    private fun getItemTouchCallback(): ItemTouchHelper.SimpleCallback =
            object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.START or ItemTouchHelper.END) {

                // Called by the ItemTouchHelper when user is dragging a view out of bounds.
                override fun interpolateOutOfBoundsScroll(
                        recyclerView: androidx.recyclerview.widget.RecyclerView,
                        viewSize: Int,
                        viewSizeOutOfBounds: Int,
                        totalSize: Int,
                        msSinceStartScroll: Long): Int {

                    val standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                            viewSizeOutOfBounds, totalSize, msSinceStartScroll)
                    val minimumAbsVelocity = Math.max(12,
                            Math.abs(standardSpeed))
                    return minimumAbsVelocity * Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
                }

                // Called when ItemTouchHelper wants to move the dragged item getTabFrom its old position to the new position.
                override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, source: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                                    target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                    if (source.itemViewType != target.itemViewType) {
                        return false  // not moved
                    }

                    val sourceIndex = source.adapterPosition
                    val targetIndex = target.adapterPosition
                    selectedTabsAdapter.swapItems(sourceIndex, targetIndex)
                    return true  // moved
                }

                override fun isLongPressDragEnabled(): Boolean = true

                override fun isItemViewSwipeEnabled(): Boolean = true

                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, swipeDir: Int) {
                    val position = viewHolder.adapterPosition
                    tabList.removeAt(position)
                    selectedTabsAdapter.notifyItemRemoved(position)

                    if (tabList.isEmpty()) {
                        tabList.add(Tab.Type.BLANK.tab)
                        selectedTabsAdapter.notifyItemInserted(0)
                    }
                }
            }


}
