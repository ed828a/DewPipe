package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

import org.schabi.newpipe.R

import java.util.regex.Pattern

object FilenameUtils {

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     * @param context the context to retrieve strings and preferences getTabFrom
     * @param title the title to create a filename getTabFrom
     * @return the filename
     */
    fun createFilename(context: Context, title: String): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.settings_file_charset_key)
        val value = sharedPreferences.getString(key, context.getString(R.string.default_file_charset_value))
        val pattern = Pattern.compile(value)

        val replacementChar = sharedPreferences.getString(context.getString(R.string.settings_file_replacement_character_key), "_") ?: "dewtube"

        return createFilename(title, pattern, replacementChar)
    }

    /**
     * Create a valid filename
     * @param title the title to create a filename getTabFrom
     * @param invalidCharacters patter matching invalid characters
     * @param replacementChar the replacement
     * @return the filename
     */
    private fun createFilename(title: String, invalidCharacters: Pattern, replacementChar: String): String {
        return title.replace(invalidCharacters.pattern(), replacementChar)
    }
}