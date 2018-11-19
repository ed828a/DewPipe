/*
 * Created by k3b on 07.01.2016.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * NewPipeSettings.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.preference.PreferenceManager

import org.schabi.newpipe.R

import java.io.File

/**
 * Helper for global settings
 */

/*
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * NewPipeSettings.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

object NewPipeSettings {

    fun initSettings(context: Context) {
        PreferenceManager.setDefaultValues(context, R.xml.appearance_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.content_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.download_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.history_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.main_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.video_audio_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.debug_settings, true)

        getVideoDownloadFolder(context)
        getAudioDownloadFolder(context)
    }

    fun getVideoDownloadFolder(context: Context): File {
        return getDir(context, R.string.download_path_key, Environment.DIRECTORY_MOVIES)
    }

    fun getVideoDownloadPath(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.download_path_key)
        return prefs.getString(key, Environment.DIRECTORY_MOVIES)
    }

    fun getAudioDownloadFolder(context: Context): File {
        return getDir(context, R.string.download_path_audio_key, Environment.DIRECTORY_MUSIC)
    }

    fun getAudioDownloadPath(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.download_path_audio_key)
        return prefs.getString(key, Environment.DIRECTORY_MUSIC)
    }

    private fun getDir(context: Context, keyID: Int, defaultDirectoryName: String): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(keyID)
        val downloadPath = prefs.getString(key, null)
        if (downloadPath != null && !downloadPath.isEmpty()) return File(downloadPath.trim { it <= ' ' })

        val dir = getDir(defaultDirectoryName)
        val spEditor = prefs.edit()
        spEditor.putString(key, getNewPipeChildFolderPathForDir(dir))
        spEditor.apply()
        return dir
    }

    private fun getDir(defaultDirectoryName: String): File {
        return File(Environment.getExternalStorageDirectory(), defaultDirectoryName)
    }

    fun resetDownloadFolders(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        resetDownloadFolder(prefs, context.getString(R.string.download_path_audio_key), Environment.DIRECTORY_MUSIC)
        resetDownloadFolder(prefs, context.getString(R.string.download_path_key), Environment.DIRECTORY_MOVIES)
    }

    private fun resetDownloadFolder(prefs: SharedPreferences, key: String, defaultDirectoryName: String) {
        val spEditor = prefs.edit()
        spEditor.putString(key, getNewPipeChildFolderPathForDir(getDir(defaultDirectoryName)))
        spEditor.apply()
    }

    private fun getNewPipeChildFolderPathForDir(dir: File): String {
        return File(dir, "NewPipe").absolutePath
    }
}
