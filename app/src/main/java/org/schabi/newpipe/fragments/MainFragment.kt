package org.schabi.newpipe.fragments

import android.content.Context
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.ErrorInfo
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import java.util.*

class MainFragment : BaseFragment(), TabLayout.OnTabSelectedListener {
    private var viewPager: ViewPager? = null
    private var pagerAdapter: SelectedTabsPagerAdapter? = null
    private var tabLayout: TabLayout? = null

    private val tabsList = ArrayList<Tab>()
    private var tabsManager: TabsManager? = null

    private var hasTabsChanged = false

    ///////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        tabsManager = TabsManager.getManager(activity as Context)
        tabsManager!!.setSavedTabsListener(object : TabsManager.SavedTabsChangeListener{
            override fun onTabsChanged() {
                if (DEBUG) {
                    Log.d(TAG, "TabsManager.SavedTabsChangeListener: onTabsChanged called, isResumed = $isResumed")
                }
                if (isResumed) {
                    updateTabs()
                } else {
                    hasTabsChanged = true
                }
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        tabLayout = rootView.findViewById(R.id.main_tab_layout)
        viewPager = rootView.findViewById(R.id.pager)

        /*  Nested fragment, use child fragment here to maintain backstack in view pager. */
        pagerAdapter = SelectedTabsPagerAdapter(childFragmentManager)
        viewPager!!.adapter = pagerAdapter

        tabLayout!!.setupWithViewPager(viewPager)
        tabLayout!!.addOnTabSelectedListener(this)
        updateTabs()
    }

    override fun onResume() {
        super.onResume()

        if (hasTabsChanged) {
            hasTabsChanged = false
            updateTabs()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tabsManager!!.unsetSavedTabsListener()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu], inflater = [$inflater]")
        inflater!!.inflate(R.menu.main_fragment_menu, menu)

        val supportActionBar = activity?.supportActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.action_search -> {
                try {

                    NavigationHelper.openSearchFragment(
                            fragmentManager,
                            ServiceHelper.getSelectedServiceId(activity!!),
                            "")
                } catch (e: Exception) {
                    val context = getActivity()
                    context?.let{
                        ErrorActivity.reportUiError(it as AppCompatActivity, e)
                    }
                }

                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tabs
    ///////////////////////////////////////////////////////////////////////////

    fun updateTabs() {
        tabsList.clear()
        tabsList.addAll(tabsManager!!.getTabs())
        pagerAdapter!!.notifyDataSetChanged()

        viewPager!!.offscreenPageLimit = pagerAdapter!!.count
        updateTabsIcon()
        updateCurrentTitle()
    }

    private fun updateTabsIcon() {
        for (i in tabsList.indices) {
            val tabToSet = tabLayout!!.getTabAt(i)
            tabToSet?.setIcon(tabsList[i].getTabIconRes(activity!!))
        }
    }

    private fun updateCurrentTitle() {
        setTitle(tabsList[viewPager!!.currentItem].getTabName(requireContext()))
    }

    override fun onTabSelected(selectedTab: TabLayout.Tab) {
        if (DEBUG) Log.d(TAG, "onTabSelected() called with: selectedTab = [$selectedTab]")
        updateCurrentTitle()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {}

    override fun onTabReselected(tab: TabLayout.Tab) {
        if (DEBUG) Log.d(TAG, "onTabReselected() called with: tab = [$tab]")
        updateCurrentTitle()
    }

    inner class SelectedTabsPagerAdapter (fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment? {
            val tab = tabsList[position]

            var throwable: Throwable? = null
            var fragment: Fragment? = null
            try {
                fragment = tab.fragment
            } catch (e: ExtractionException) {
                throwable = e
            }

            if (throwable != null) {
                val context = activity
                context?.let {
                    ErrorActivity.reportError(it as Context, throwable, it.javaClass, null,
                            ErrorInfo.make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash))
                }

                return BlankFragment()
            }

            if (fragment is BaseFragment) {
                fragment.useAsFrontPage(true)
            }

            return fragment
        }

        override fun getItemPosition(`object`: Any): Int {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return PagerAdapter.POSITION_NONE
        }

        override fun getCount(): Int {
            return tabsList.size
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            childFragmentManager
                    .beginTransaction()
                    .remove(`object` as Fragment)
                    .commitNowAllowingStateLoss()
        }
    }
}
