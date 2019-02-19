package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference

import org.schabi.newpipe.R
import org.schabi.newpipe.util.Constants

class AppearanceSettingsFragment : BasePreferenceFragment() {

    /**
     * Theme that was applied when the settings was opened (or recreated after a theme change)
     */
    private var startThemeKey: String? = null
    private var captionSettingsKey: String? = null

    private val themePreferenceChange = Preference.OnPreferenceChangeListener { preference, newValue ->
        defaultPreferences
                .edit()
                .putBoolean(Constants.KEY_THEME_CHANGE, true)
                .putString(getString(R.string.theme_key), newValue.toString())
                .apply()
//        defaultPreferences.edit().putString(getString(R.string.theme_key), newValue.toString()).apply()

        if (newValue != startThemeKey && activity != null) {
            // If it's not the current theme
            activity?.recreate()
        }

        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeKey = getString(R.string.theme_key)

        startThemeKey = defaultPreferences.getString(themeKey, getString(R.string.default_theme_value))
        findPreference(themeKey).onPreferenceChangeListener = themePreferenceChange

        captionSettingsKey = getString(R.string.caption_settings_key)
        if (!CAPTIONING_SETTINGS_ACCESSIBLE) {
            val captionSettings = findPreference(captionSettingsKey)
            preferenceScreen.removePreference(captionSettings)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.appearance_settings)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == captionSettingsKey && CAPTIONING_SETTINGS_ACCESSIBLE) {
            startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS))
        }

        return super.onPreferenceTreeClick(preference)
    }

    companion object {
        private val CAPTIONING_SETTINGS_ACCESSIBLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }
}
