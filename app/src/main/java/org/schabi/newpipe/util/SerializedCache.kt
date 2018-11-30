package org.schabi.newpipe.util

import android.support.v4.util.LruCache
import android.util.Log
import org.schabi.newpipe.BuildConfig.DEBUG

import org.schabi.newpipe.MainActivity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID

class SerializedCache private constructor()//no instance
{
    private val TAG = javaClass.simpleName

    fun <T> take(key: String, type: Class<T>): T? {
        if (DEBUG) Log.d(TAG, "take() called with: key = [$key]")
        synchronized(lruCache) {
            return if (lruCache.get(key) != null) getItem(lruCache.remove(key)!!, type) else null
        }
    }

    operator fun <T> get(key: String, type: Class<T>): T? {
        if (DEBUG) Log.d(TAG, "get() called with: key = [$key]")
        synchronized(lruCache) {
            val data = lruCache.get(key)
            return if (data != null) getItem(data, type) else null
        }
    }

    fun <T : Serializable> put(item: T, type: Class<T>): String? {
        val key = UUID.randomUUID().toString()
        return if (put(key, item, type)) key else null
    }

    fun <T : Serializable> put(key: String, item: T,
                               type: Class<T>): Boolean {
        if (DEBUG) Log.d(TAG, "put() called with: key = [$key], item = [$item]")
        synchronized(lruCache) {
            try {
                lruCache.put(key, CacheData(clone(item, type), type))
                return true
            } catch (error: Exception) {
                Log.e(TAG, "Serialization failed for: ", error)
            }

        }
        return false
    }

    fun clear() {
        if (DEBUG) Log.d(TAG, "clear() called")
        synchronized(lruCache) {
            lruCache.evictAll()
        }
    }

    fun size(): Long {
        synchronized(lruCache) {
            return lruCache.size().toLong()
        }
    }

    private fun <T> getItem(data: CacheData<*>, type: Class<T>): T? {
        return if (type.isAssignableFrom(data.type)) type.cast(data.item) else null
    }

    @Throws(Exception::class)
    private fun <T : Serializable> clone(item: T,
                                         type: Class<T>): T {
        val bytesOutput = ByteArrayOutputStream()
        ObjectOutputStream(bytesOutput).use { objectOutput ->
            objectOutput.writeObject(item)
            objectOutput.flush()
        }
        val clone = ObjectInputStream(
                ByteArrayInputStream(bytesOutput.toByteArray())).readObject()
        return type.cast(clone)
    }

    private class CacheData<T> (val item: T, val type: Class<T>)

    companion object {
        val instance = SerializedCache()
        private const val MAX_ITEMS_ON_CACHE = 5

        private val lruCache = LruCache<String, CacheData<*>>(MAX_ITEMS_ON_CACHE)
    }
}
