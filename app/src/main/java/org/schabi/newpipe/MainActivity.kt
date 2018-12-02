/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBar
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.util.Constants
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.ThemeHelper

class MainActivity : AppCompatActivity() {

    private var toggle: ActionBarDrawerToggle? = null
    private var drawer: DrawerLayout? = null
    private var drawerItems: NavigationView? = null
    private var headerServiceView: TextView? = null

    private var servicesShown = false
    private var serviceArrow: ImageView? = null

    ///////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [$savedInstanceState]")

        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val w = window
            w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }

        if (supportFragmentManager != null && supportFragmentManager.backStackEntryCount == 0) {
            initFragments()
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        try {
            setupDrawer()
        } catch (e: Exception) {
            ErrorActivity.reportUiError(this, e)
        }

    }

    @Throws(Exception::class)
    private fun setupDrawer() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        drawer = findViewById(R.id.drawer_layout)
        drawerItems = findViewById(R.id.navigation)

        //Tabs
        val currentServiceId = ServiceHelper.getSelectedServiceId(this)
        val service = NewPipe.getService(currentServiceId)

        var kioskId = 0

        for (ks in service.kioskList.availableKiosks) {
            drawerItems!!.menu
                    .add(R.id.menu_tabs_group, kioskId, 0, KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcons(ks, this))
            kioskId++
        }

        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER, R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_whats_new)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.rss))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.download))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.history))

        //Settings and About
        drawerItems!!.menu
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.settings))
        drawerItems!!.menu
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.info))

        toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close)
        toggle!!.syncState()
        drawer!!.addDrawerListener(toggle!!)
        drawer!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            private var lastService: Int = 0

            override fun onDrawerOpened(drawerView: View) {
                lastService = ServiceHelper.getSelectedServiceId(this@MainActivity)
            }

            override fun onDrawerClosed(drawerView: View) {
                if (servicesShown) {
                    toggleServices()
                }
                if (lastService != ServiceHelper.getSelectedServiceId(this@MainActivity)) {
                    Handler(Looper.getMainLooper()).post { this@MainActivity.recreate() }
                }
            }
        })

        drawerItems!!.setNavigationItemSelectedListener { this.drawerItemSelected(it) }
        setupDrawerHeader()
    }

    private fun drawerItemSelected(item: MenuItem): Boolean {
        when (item.groupId) {
            R.id.menu_services_group -> changeService(item)
            R.id.menu_tabs_group -> try {
                tabSelected(item)
            } catch (e: Exception) {
                ErrorActivity.reportUiError(this, e)
            }

            R.id.menu_options_about_group -> optionsAboutSelected(item)
            else -> return false
        }

        drawer!!.closeDrawers()
        return true
    }

    private fun changeService(item: MenuItem) {
        drawerItems!!.menu.getItem(ServiceHelper.getSelectedServiceId(this)).isChecked = false
        ServiceHelper.setSelectedServiceId(this, item.itemId)
        drawerItems!!.menu.getItem(ServiceHelper.getSelectedServiceId(this)).isChecked = true
    }

    @Throws(ExtractionException::class)
    private fun tabSelected(item: MenuItem) {
        when (item.itemId) {
            ITEM_ID_SUBSCRIPTIONS -> NavigationHelper.openSubscriptionFragment(supportFragmentManager)
            ITEM_ID_FEED -> NavigationHelper.openWhatsNewFragment(supportFragmentManager)
            ITEM_ID_BOOKMARKS -> NavigationHelper.openBookmarksFragment(supportFragmentManager)
            ITEM_ID_DOWNLOADS -> NavigationHelper.openDownloads(this)
            ITEM_ID_HISTORY -> NavigationHelper.openStatisticFragment(supportFragmentManager)
            else -> {
                val currentServiceId = ServiceHelper.getSelectedServiceId(this)
                val service = NewPipe.getService(currentServiceId)
                var serviceName = ""

                var kioskId = 0
                for (ks in service.kioskList.availableKiosks) {
                    if (kioskId == item.itemId) {
                        serviceName = ks
                    }
                    kioskId++
                }

                NavigationHelper.openKioskFragment(supportFragmentManager, currentServiceId, serviceName)
            }
        }
    }

    private fun optionsAboutSelected(item: MenuItem) {
        when (item.itemId) {
            ITEM_ID_SETTINGS -> NavigationHelper.openSettings(this)
            ITEM_ID_ABOUT -> NavigationHelper.openAbout(this)
        }
    }

    private fun setupDrawerHeader() {
        val navigationView = findViewById<NavigationView>(R.id.navigation)
        val hView = navigationView.getHeaderView(0)

        serviceArrow = hView.findViewById(R.id.drawer_arrow)
        headerServiceView = hView.findViewById(R.id.drawer_header_service_view)
        val action = hView.findViewById<View>(R.id.drawer_header_action_button)
        action.setOnClickListener { view -> toggleServices() }
    }

    private fun toggleServices() {
        servicesShown = !servicesShown

        drawerItems!!.menu.removeGroup(R.id.menu_services_group)
        drawerItems!!.menu.removeGroup(R.id.menu_tabs_group)
        drawerItems!!.menu.removeGroup(R.id.menu_options_about_group)

        if (servicesShown) {
            showServices()
        } else {
            try {
                showTabs()
            } catch (e: Exception) {
                ErrorActivity.reportUiError(this, e)
            }

        }
    }

    private fun showServices() {
        serviceArrow!!.setImageResource(R.drawable.ic_arrow_up_white)

        for (s in NewPipe.getServices()) {
            val title = s.serviceInfo.name + if (ServiceHelper.isBeta(s)) " (beta)" else ""

            drawerItems!!.menu
                    .add(R.id.menu_services_group, s.serviceId, ORDER, title)
                    .setIcon(ServiceHelper.getIcon(s.serviceId))
        }
        drawerItems!!.menu.getItem(ServiceHelper.getSelectedServiceId(this)).isChecked = true
    }

    @Throws(ExtractionException::class)
    private fun showTabs() {
        serviceArrow!!.setImageResource(R.drawable.ic_arrow_down_white)

        //Tabs
        val currentServiceId = ServiceHelper.getSelectedServiceId(this)
        val service = NewPipe.getService(currentServiceId)

        var kioskId = 0

        for (ks in service.kioskList.availableKiosks) {
            drawerItems!!.menu
                    .add(R.id.menu_tabs_group, kioskId, ORDER, KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcons(ks, this))
            kioskId++
        }

        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER, R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_whats_new)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.rss))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.download))
        drawerItems!!.menu
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.history))

        //Settings and About
        drawerItems!!.menu
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.settings))
        drawerItems!!.menu
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.info))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            StateSaver.clearStateFiles()
        }
    }

    override fun onResume() {
        super.onResume()

        // close drawer on return, and don't show animation, so its looks like the drawer isn't open
        // when the user returns to MainActivity
        drawer!!.closeDrawer(Gravity.START, false)
        try {
            val selectedServiceName = NewPipe.getService(
                    ServiceHelper.getSelectedServiceId(this)).serviceInfo.name
            headerServiceView!!.text = selectedServiceName
        } catch (e: Exception) {
            ErrorActivity.reportUiError(this, e)
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "Theme has changed, recreating activity...")
            sharedPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, false).apply()
            // https://stackoverflow.com/questions/10844112/runtimeexception-performing-pause-of-activity-that-is-not-resumed
            // Briefly, let the activity resume properly posting the recreate call to end of the message queue
            Handler(Looper.getMainLooper()).post { this@MainActivity.recreate() }
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "main page has changed, recreating main fragment...")
            sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply()
            NavigationHelper.openMainActivity(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [$intent]")
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            val action = intent.action
            if (action != null && action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return
        }

        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called")

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
        // If current fragment implements BackPressable (i.e. can/wanna handle back press) delegate the back press to it
        if (fragment is BackPressable) {
            if ((fragment as BackPressable).onBackPressed()) return
        }


        if (supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else
            super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        for (i in grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return
            }
        }
        when (requestCode) {
            PermissionHelper.DOWNLOADS_REQUEST_CODE -> NavigationHelper.openDownloads(this)
            PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
                if (fragment is VideoDetailFragment) {
                    fragment.openDownloadDialog()
                }
            }
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     * +---------------+
     * |  Main Screen  +----+
     * +-------+-------+    |
     * |            |
     * ▲ Up         | Search Button
     * |            |
     * +----+-----+      |
     * +------------+  Search  |◄-----+
     * |            +----+-----+
     * |   Open          |
     * |  something      ▲ Up
     * |                 |
     * |    +------------+-------------+
     * |    |                          |
     * |    |  Video    <->  Channel   |
     * +---►|  Channel  <->  Playlist  |
     * |  Video    <->  ....      |
     * |                          |
     * +--------------------------+
    </pre> *
     */
    private fun onHomeButtonPressed() {
        // If search fragment wasn't found in the backstack...
        if (!NavigationHelper.tryGotoSearchFragment(supportFragmentManager)) {
            // ...go to the main fragment
            NavigationHelper.gotoMainFragment(supportFragmentManager)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Menu
    ///////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [$menu]")
        super.onCreateOptionsMenu(menu)

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
        if (fragment !is VideoDetailFragment) {
            findViewById<View>(R.id.toolbar).findViewById<View>(R.id.toolbarSpinner).visibility = View.GONE
        }

        if (fragment !is SearchFragment) {
            findViewById<View>(R.id.toolbar).findViewById<View>(R.id.toolbar_search_container).visibility = View.GONE

            val inflater = menuInflater
            inflater.inflate(R.menu.main_menu, menu)
        }

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(false)

        updateDrawerNavigation()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (DEBUG) Log.d(TAG, "onOptionsItemSelected() called with: item = [$item]")
        val id = item.itemId

        when (id) {
            android.R.id.home -> {
                onHomeButtonPressed()
                return true
            }
            R.id.action_show_downloads -> return NavigationHelper.openDownloads(this)
            R.id.action_history -> {
                NavigationHelper.openStatisticFragment(supportFragmentManager)
                return true
            }
            R.id.action_settings -> {
                NavigationHelper.openSettings(this)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Init
    ///////////////////////////////////////////////////////////////////////////

    private fun initFragments() {
        if (DEBUG) Log.d(TAG, "initFragments() called")
        StateSaver.clearStateFiles()
        if (intent != null && intent.hasExtra(Constants.KEY_LINK_TYPE)) {
            handleIntent(intent)
        } else
            NavigationHelper.gotoMainFragment(supportFragmentManager)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun updateDrawerNavigation() {
        if (supportActionBar == null) return

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
        if (fragment is MainFragment) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
            if (toggle != null) {
                toggle!!.syncState()
                toolbar.setNavigationOnClickListener { v -> drawer.openDrawer(GravityCompat.START) }
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { v -> onHomeButtonPressed() }
        }
    }

    private fun handleIntent(intent: Intent?) {
        try {
            if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [$intent]")

            if (intent!!.hasExtra(Constants.KEY_LINK_TYPE)) {
                val url = intent.getStringExtra(Constants.KEY_URL)
                val serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0)
                val title = intent.getStringExtra(Constants.KEY_TITLE)
                when (intent.getSerializableExtra(Constants.KEY_LINK_TYPE) as StreamingService.LinkType) {
                    StreamingService.LinkType.STREAM -> {
                        val autoPlay = intent.getBooleanExtra(VideoDetailFragment.AUTO_PLAY, false)
                        NavigationHelper.openVideoDetailFragment(supportFragmentManager, serviceId, url, title, autoPlay)
                    }
                    StreamingService.LinkType.CHANNEL -> NavigationHelper.openChannelFragment(supportFragmentManager,
                            serviceId,
                            url,
                            title)
                    StreamingService.LinkType.PLAYLIST -> NavigationHelper.openPlaylistFragment(supportFragmentManager,
                            serviceId,
                            url,
                            title)
                }
            } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
                var searchString: String? = intent.getStringExtra(Constants.KEY_SEARCH_STRING)
                if (searchString == null) searchString = ""
                val serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0)
                NavigationHelper.openSearchFragment(
                        supportFragmentManager,
                        serviceId,
                        searchString)

            } else {
                NavigationHelper.gotoMainFragment(supportFragmentManager)
            }
        } catch (e: Exception) {
            ErrorActivity.reportUiError(this, e)
        }

    }

    companion object {
        private const val TAG = "MainActivity"
        @JvmField
        val DEBUG = BuildConfig.BUILD_TYPE != "release"

        private const val ITEM_ID_SUBSCRIPTIONS = -1
        private const val ITEM_ID_FEED = -2
        private const val ITEM_ID_BOOKMARKS = -3
        private const val ITEM_ID_DOWNLOADS = -4
        private const val ITEM_ID_HISTORY = -5
        private const val ITEM_ID_SETTINGS = 0
        private const val ITEM_ID_ABOUT = 1

        private const val ORDER = 0
    }
}
