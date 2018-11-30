package org.schabi.newpipe.util

import android.content.Context

import org.schabi.newpipe.R

/**
 * Created by Chrsitian Schabesberger on 28.09.17.
 * KioskTranslator.java is part of NewPipe.
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
 * along with NewPipe.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

object KioskTranslator {
    fun getTranslatedKioskName(kioskId: String, c: Context): String {
        return when (kioskId) {
            "Trending" -> c.getString(R.string.trending)
            "Top 50" -> c.getString(R.string.top_50)
            "New & hot" -> c.getString(R.string.new_and_hot)
            else -> kioskId
        }
    }

    fun getKioskIcons(kioskId: String, c: Context): Int {
        return when (kioskId) {
            "Trending" -> ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot)
            "Top 50" -> ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot)
            "New & hot" -> ThemeHelper.resolveResourceIdFromAttr(c, R.attr.ic_hot)
            else -> 0
        }
    }
}
