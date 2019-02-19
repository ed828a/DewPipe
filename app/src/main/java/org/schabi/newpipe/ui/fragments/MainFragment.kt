package org.schabi.newpipe.ui.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
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
    private lateinit var viewPager: androidx.viewpager.widget.ViewPager
    private lateinit var pagerAdapter: SelectedTabsPagerAdapter
    private lateinit var tabLayout: TabLayout

    private val tabsList = ArrayList<Tab>()
    private lateinit var tabsManager: TabsManager

    private var hasTabsChanged = false

    ///////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        tabsManager = TabsManager.getTabsManager(activity as Context)
        tabsManager.setSavedTabsChangeListener(object : TabsManager.SavedTabsChangeListener {
            override fun onTabsChanged() {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: onTabsChanged called, isResumed = $isResumed")

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
        viewPager.adapter = pagerAdapter

        tabLayout.setupWithViewPager(viewPager)
        tabLayout.addOnTabSelectedListener(this)
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
        tabsManager.unsetSavedTabsListener()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu], inflater = [$inflater]")
        inflater?.inflate(R.menu.main_fragment_menu, menu)

        val supportActionBar = activity?.supportActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item!!.itemId) {
            R.id.action_search -> {
                try {
                    NavigationHelper.openSearchFragment(
                            fragmentManager,
                            ServiceHelper.getSelectedServiceId(activity!!),
                            "")
                } catch (e: Exception) {
                    val context = getActivity()
                    context?.let {
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
        tabsList.addAll(tabsManager.getTabs())
        pagerAdapter.notifyDataSetChanged()

        viewPager.offscreenPageLimit = pagerAdapter.count
        updateTabsIcon()
        updateCurrentTitle()
    }

    private fun updateTabsIcon() {
//        for (i in tabsList.indices) {
//            val tabToSet = tabLayout.getTabAt(i)
//            tabToSet?.setIcon(tabsList[i].getTabIconRes(activity!!))
//        }
        tabsList.forEach {
            tabLayout.getTabAt(tabsList.indexOf(it))?.setIcon(it.getTabIconRes(activity!!))
        }
    }

    private fun updateCurrentTitle() {
        setTitle(tabsList[viewPager.currentItem].getTabName(requireContext()))
    }

    ////////////////////////////////////////////////////////////////////////////
    // TabLayout.OnTabSelectedListener methods
    ////////////////////////////////////////////////////////////////////////////

    override fun onTabSelected(selectedTab: TabLayout.Tab) {
        Log.d(TAG, "onTabSelected() called with: selectedTab = [$selectedTab]")
        updateCurrentTitle()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {}

    override fun onTabReselected(tab: TabLayout.Tab) {
        Log.d(TAG, "onTabReselected() called with: tab = [$tab]")
        updateCurrentTitle()
    }

    inner class SelectedTabsPagerAdapter(fragmentManager: androidx.fragment.app.FragmentManager) : androidx.fragment.app.FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment {
            val tab = tabsList[position]

            var throwable: Throwable? = null
            var fragment: Fragment? = null
            try {
                fragment = tab.fragment
            } catch (e: ExtractionException) {
                throwable = e
            }

            if (throwable != null) {
//                val context = activity
                context?.let {
                    ErrorActivity.reportError(it, throwable, it.javaClass, null,
                            ErrorInfo.make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash))
                }

                return BlankFragment()
            }

            if (fragment is BaseFragment) {  // why ?
                fragment.useAsFrontPage(true)
            }

            return fragment!!
        }

        override fun getItemPosition(`object`: Any): Int {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return androidx.viewpager.widget.PagerAdapter.POSITION_NONE
        }

        override fun getCount(): Int = tabsList.size


        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            childFragmentManager
                    .beginTransaction()
                    .remove(`object` as androidx.fragment.app.Fragment)
                    .commitNowAllowingStateLoss()
        }
    }
}
