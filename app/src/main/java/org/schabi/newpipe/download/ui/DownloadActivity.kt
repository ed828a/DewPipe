package org.schabi.newpipe.download.ui


import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.download.background.DeleteDownloadManager
import org.schabi.newpipe.download.fragment.DownloadMissionsFragment
import org.schabi.newpipe.download.service.DownloadManagerService
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.ThemeHelper

class DownloadActivity : AppCompatActivity() {
    private lateinit var mDeleteDownloadManager: DeleteDownloadManager
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        // just initialize DownloadManagerService so that DownloadFragment can bind to the service.
        // no actual downloading to start yet.
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
        mDeleteDownloadManager.restoreState(savedInstanceState)

        val fragment = supportFragmentManager.findFragmentByTag(DOWNLOAD_MISSIONS_FRAGMENT_TAG) as DownloadMissionsFragment?
        if (fragment != null) {
            fragment.setDeleteManager(mDeleteDownloadManager)
        } else {
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    updateFragments()
                    window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mDeleteDownloadManager.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun updateFragments() {
        val fragment = DownloadMissionsFragment()
        fragment.setDeleteManager(mDeleteDownloadManager)

        supportFragmentManager.beginTransaction()
                .replace(R.id.downloader_frame, fragment, DOWNLOAD_MISSIONS_FRAGMENT_TAG)
                .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.download_menu, menu)

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
                compositeDisposable.add(deletePending())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        compositeDisposable.add(deletePending())
    }

    private fun deletePending() =
        Completable.fromAction { mDeleteDownloadManager.deletePending() }
                .subscribeOn(Schedulers.io())
                .subscribe()

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }

    companion object {

        private const val DOWNLOAD_MISSIONS_FRAGMENT_TAG = "download_missions_fragment_tag"
    }
}
