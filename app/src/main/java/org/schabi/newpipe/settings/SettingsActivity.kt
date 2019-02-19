package org.schabi.newpipe.settings

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper


class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceBundle: Bundle?) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this))

        super.onCreate(savedInstanceBundle)
        setContentView(R.layout.settings_layout)

        // don't use kotlin-extensions here because toolbar layout is shared.
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (savedInstanceBundle == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, MainSettingsFragment())
                    .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val actionBar = supportActionBar
        actionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(true)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount == 0) {
                finish()
            } else
                supportFragmentManager.popBackStack()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, preference: Preference): Boolean {
        val fragment = androidx.fragment.app.Fragment.instantiate(this, preference.fragment, preference.extras)
        supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, fragment)
                .addToBackStack(null)
                .commit()
        return true
    }

    companion object {

        fun initSettings(context: Context) {
            NewPipeSettings.initSettings(context)
        }
    }
}
