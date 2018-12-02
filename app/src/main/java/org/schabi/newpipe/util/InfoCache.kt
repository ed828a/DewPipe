/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * InfoCache.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util

import android.support.v4.util.LruCache
import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG
import org.schabi.newpipe.extractor.Info


class InfoCache private constructor()//no instance
{
    private val TAG = javaClass.simpleName

    val size: Long
        get() = synchronized(lruCache) {
            return lruCache.size().toLong()
        }

    fun getFromKey(serviceId: Int, url: String): Info? {
        if (DEBUG) Log.d(TAG, "getFromKey() called with: serviceId = [$serviceId], url = [$url]")
        synchronized(lruCache) {
            return getInfo(keyOf(serviceId, url))
        }
    }

    fun putInfo(serviceId: Int, url: String, info: Info) {
        if (DEBUG) Log.d(TAG, "putInfo() called with: info = [$info]")

        val expirationMillis = ServiceHelper.getCacheExpirationMillis(info.serviceId)
        synchronized(lruCache) {
            val data = CacheData(info, expirationMillis)
            lruCache.put(keyOf(serviceId, url), data)
        }
    }

    fun removeInfo(serviceId: Int, url: String) {
        if (DEBUG) Log.d(TAG, "removeInfo() called with: serviceId = [$serviceId], url = [$url]")
        synchronized(lruCache) {
            lruCache.remove(keyOf(serviceId, url))
        }
    }

    fun clearCache() {
        if (DEBUG) Log.d(TAG, "clearCache() called")
        synchronized(lruCache) {
            lruCache.evictAll()
        }
    }

    fun trimCache() {
        if (DEBUG) Log.d(TAG, "trimCache() called")
        synchronized(lruCache) {
            removeStaleCache()
            lruCache.trimToSize(TRIM_CACHE_TO)
        }
    }

    private class CacheData (val info: Info, timeoutMillis: Long) {
        private val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

        val isExpired: Boolean
            get() = System.currentTimeMillis() > expireTimestamp

    }

    companion object {
//        private val DEBUG = MainActivity.DEBUG

        val instance = InfoCache()
        private const val MAX_ITEMS_ON_CACHE = 60
        /**
         * Trim the cache to this size
         */
        private val TRIM_CACHE_TO = 30

        private val lruCache = LruCache<String, CacheData>(MAX_ITEMS_ON_CACHE)

        private fun keyOf(serviceId: Int, url: String): String {
            return serviceId.toString() + url
        }

        private fun removeStaleCache() {
            for ((key, data) in InfoCache.lruCache.snapshot()) {
                if (data != null && data.isExpired) {
                    InfoCache.lruCache.remove(key)
                }
            }
        }

        private fun getInfo(key: String): Info? {
            val data = InfoCache.lruCache.get(key) ?: return null

            if (data.isExpired) {
                InfoCache.lruCache.remove(key)
                return null
            }

            return data.info
        }
    }
}
