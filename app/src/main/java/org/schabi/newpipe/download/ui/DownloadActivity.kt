package org.schabi.newpipe.download.ui

import android.app.FragmentTransaction
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.download.giga.service.DownloadManagerService
import org.schabi.newpipe.download.giga.gigaui.fragment.AllMissionsFragment
import org.schabi.newpipe.download.giga.gigaui.fragment.MissionsFragment

class DownloadActivity : AppCompatActivity() {
    private var mDeleteDownloadManager: DeleteDownloadManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Service
        val intent = Intent()
        intent.setClass(this, DownloadManagerService::class.java)
        startService(intent)

        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloader)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.downloads_title)
            actionBar.setDisplayShowTitleEnabled(true)
        }

        mDeleteDownloadManager = DeleteDownloadManager(this)
        mDeleteDownloadManager!!.restoreState(savedInstanceState)

        val fragment = fragmentManager.findFragmentByTag(MISSIONS_FRAGMENT_TAG) as MissionsFragment?
        if (fragment != null) {
            fragment.setDeleteManager(mDeleteDownloadManager!!)
        } else {
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    updateFragments()
                    window.decorView.viewTreeObserver.removeGlobalOnLayoutListener(this)
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mDeleteDownloadManager!!.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun updateFragments() {
        val fragment = AllMissionsFragment()
        fragment.setDeleteManager(mDeleteDownloadManager!!)

        fragmentManager.beginTransaction()
                .replace(R.id.frame, fragment, MISSIONS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater

        inflater.inflate(R.menu.download_menu, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                deletePending()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        deletePending()
    }

    private fun deletePending() {
        Completable.fromAction { mDeleteDownloadManager!!.deletePending() }
                .subscribeOn(Schedulers.io())
                .subscribe()

//        Completable.fromAction(Action { mDeleteDownloadManager!!.deletePending() })
//                .subscribeOn(Schedulers.io())
//                .subscribe()
    }

    companion object {

        private const val MISSIONS_FRAGMENT_TAG = "fragment_tag"
    }
}
