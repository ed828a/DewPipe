package org.schabi.newpipe.util

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log

import org.schabi.newpipe.R

import java.util.regex.Pattern

object FilenameUtils {
    const val TAG = "FilenameUtils"

    /**
     * make sure that the filename does not contain illegal chars.
     * @param context the context to retrieve strings and preferences getTabFrom
     * @param title the title to create a filename getTabFrom
     * @return the filename
     */
    fun createFilename(context: Context, title: String): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.settings_file_charset_key)
        val value = sharedPreferences.getString(key, context.getString(R.string.default_file_charset_value))
        val pattern = Pattern.compile(value)
        Log.d(TAG, "createFilename(): value = $value, pattern = $pattern")
        val replacementChar = sharedPreferences.getString(context.getString(R.string.settings_file_replacement_character_key), "_") ?: "*"

        return createFilename(title, pattern, replacementChar)
    }

    /**
     * Create a valid filename
     * @param title the title to create a filename getTabFrom
     * @param invalidCharacters patter matching invalid characters -- useless in Kotlin
     * @param replacementChar the replacement
     * @return the filename
     */
    private fun createFilename(title: String, invalidCharacters: Pattern, replacementChar: String): String {
        val regex = Regex(invalidCharacters.pattern())

        return regex.replace(title, replacementChar)
    }


}