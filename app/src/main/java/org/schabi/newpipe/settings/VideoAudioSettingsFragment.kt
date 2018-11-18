package org.schabi.newpipe.settings

import android.os.Bundle

import org.schabi.newpipe.R

class VideoAudioSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.video_audio_settings)
    }
}
