package org.schabi.newpipe.player.helper

import android.content.Context
import android.util.Log

import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

import java.io.File

internal class CacheFactory private constructor(context: Context,
                                                userAgent: String,
                                                transferListener: TransferListener<in DataSource>,
                                                maxCacheSize: Long,
                                                private val maxFileSize: Long) : DataSource.Factory {

    private val dataSourceFactory: DefaultDataSourceFactory = DefaultDataSourceFactory(context, userAgent, transferListener)
    private var cacheDir: File

    init {

        cacheDir = File(context.externalCacheDir, CACHE_FOLDER_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdir()
        }

        if (cache == null) {
            val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
            cache = SimpleCache(cacheDir, evictor)
        }
    }

    constructor(context: Context,
                userAgent: String,
                transferListener: TransferListener<in DataSource>) : this(context, userAgent, transferListener, PlayerHelper.getPreferredCacheSize(context),
            PlayerHelper.getPreferredFileSize(context)) {}

    override fun createDataSource(): DataSource {
        Log.d(TAG, "initExoPlayerCache: cacheDir = ${cacheDir.absolutePath}")

        val dataSource = dataSourceFactory.createDataSource()
        val fileSource = FileDataSource()
        val dataSink = CacheDataSink(cache!!, maxFileSize)

        return CacheDataSource(cache, dataSource, fileSource, dataSink, CACHE_FLAGS, null)
    }

    fun tryDeleteCacheFiles() {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return

        try {
            for (file in cacheDir.listFiles()) {
                val filePath = file.absolutePath
                val deleteSuccessful = file.delete()

                Log.d(TAG, "tryDeleteCacheFiles: $filePath deleted = $deleteSuccessful")
            }
        } catch (ignored: Exception) {
            Log.e(TAG, "Failed to delete file.", ignored)
        }

    }

    companion object {
        private const val TAG = "CacheFactory"
        private const val CACHE_FOLDER_NAME = "exoplayer"
        private const val CACHE_FLAGS = CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR

        // Creating cache on every instance may cause problems with multiple players when
        // sources are not ExtractorMediaSource
        // see: https://stackoverflow.com/questions/28700391/using-cache-in-exoplayer
        // todo: make this a singleton?
        private var cache: SimpleCache? = null

    }
}