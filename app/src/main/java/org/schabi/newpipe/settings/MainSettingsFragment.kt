package org.schabi.newpipe.settings

import android.os.Bundle
import android.support.v7.preference.Preference

import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R

class MainSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.main_settings)

        if (!DEBUG) {
            val debug = findPreference(getString(R.string.debug_pref_screen_key))
            preferenceScreen.removePreference(debug)
        }
    }

    companion object {
        const val DEBUG = BuildConfig.BUILD_TYPE != "release"
    }
}
