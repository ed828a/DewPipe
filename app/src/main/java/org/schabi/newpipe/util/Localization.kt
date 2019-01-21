package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.annotation.PluralsRes
import android.support.annotation.StringRes
import android.text.TextUtils

import org.schabi.newpipe.R

import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

/*
 * Created by chschtsch on 12/29/15.
 *
 * Copyright (C) Gregory Arkhipov 2015
 * Localization.java is part of NewPipe.
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

object Localization {

    val DOT_SEPARATOR = " • "

    fun concatenateStrings(vararg strings: String): String {
        return concatenateStrings(Arrays.asList(*strings))
    }

    fun concatenateStrings(strings: List<String>): String {
        if (strings.isEmpty()) return ""

        val stringBuilder = StringBuilder()
        stringBuilder.append(strings[0])

        for (i in 1 until strings.size) {
            val string = strings[i]
            if (!TextUtils.isEmpty(string)) {
                stringBuilder.append(DOT_SEPARATOR).append(strings[i])
            }
        }

        return stringBuilder.toString()
    }

    fun getPreferredLocale(context: Context): Locale {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        val languageCode = sp.getString(context.getString(R.string.search_language_key), context.getString(R.string.default_language_value))

        try {
            if (languageCode!!.length == 2) {
                return Locale(languageCode)
            } else if (languageCode.contains("_")) {
                val country = languageCode.substring(languageCode.indexOf("_"), languageCode.length)
                return Locale(languageCode.substring(0, 2), country)
            }
        } catch (ignored: Exception) {
        }

        return Locale.getDefault()
    }

    fun localizeNumber(context: Context, number: Long): String {
        val locale = getPreferredLocale(context)
        val nf = NumberFormat.getInstance(locale)
        return nf.format(number)
    }

    private fun formatDate(context: Context, date: String): String {
        val locale = getPreferredLocale(context)
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        var datum: Date? = null
        try {
            datum = formatter.parse(date)
        } catch (e: ParseException) {
            e.printStackTrace()
        }

        val df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)

        return df.format(datum)
    }

    fun localizeDate(context: Context, date: String): String {
        val res = context.resources
        val dateString = res.getString(R.string.upload_date_text)

        val formattedDate = formatDate(context, date)
        return String.format(dateString, formattedDate)
    }

    fun localizeViewCount(context: Context, viewCount: Long): String {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount, localizeNumber(context, viewCount))
    }

    fun localizeSubscribersCount(context: Context, subscriberCount: Long): String {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount, localizeNumber(context, subscriberCount))
    }

    fun localizeStreamCount(context: Context, streamCount: Long): String {
        return getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount, localizeNumber(context, streamCount))
    }

    fun shortCount(context: Context, count: Long): String {
        return if (count >= 1000000000) {
            java.lang.Long.toString(count / 1000000000) + context.getString(R.string.short_billion)
        } else if (count >= 1000000) {
            java.lang.Long.toString(count / 1000000) + context.getString(R.string.short_million)
        } else if (count >= 1000) {
            java.lang.Long.toString(count / 1000) + context.getString(R.string.short_thousand)
        } else {
            java.lang.Long.toString(count)
        }
    }

    fun shortViewCount(context: Context, viewCount: Long): String {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount, shortCount(context, viewCount))
    }

    fun shortSubscriberCount(context: Context, subscriberCount: Long): String {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount, shortCount(context, subscriberCount))
    }

    private fun getQuantity(context: Context, @PluralsRes pluralId: Int, @StringRes zeroCaseStringId: Int, count: Long, formattedCount: String): String {
        if (count == 0L) return context.getString(zeroCaseStringId)

        // As we use the already formatted count, is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category, or some language have some specific rule... then we have to change it)
        val safeCount = when {
            count > Integer.MAX_VALUE -> Integer.MAX_VALUE
            count < Integer.MIN_VALUE -> Integer.MIN_VALUE
            else -> count.toInt()
        }
        return context.resources.getQuantityString(pluralId, safeCount, formattedCount)
    }

    fun getDurationString(duration: Long): String {
        var duration = duration
        if (duration < 0) {
            duration = 0
        }
        val output: String
        val days = duration / (24 * 60 * 60L) /* greater than a day */
        duration %= 24 * 60 * 60L
        val hours = duration / (60 * 60L) /* greater than an hour */
        duration %= 60 * 60L
        val minutes = duration / 60L
        val seconds = duration % 60L

        //handle days
        if (days > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds)
        } else if (hours > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            output = String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
        return output
    }
}
