package org.schabi.newpipe.player.helper

import android.content.Context

import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.TransferListener

class PlayerDataSource(context: Context,
                       userAgent: String,
                       transferListener: TransferListener<in DataSource>) {

    private val cacheDataSourceFactory: DataSource.Factory
    private val cachelessDataSourceFactory: DataSource.Factory

    init {
        cacheDataSourceFactory = CacheFactory(context, userAgent, transferListener)
        cachelessDataSourceFactory = DefaultDataSourceFactory(context, userAgent, transferListener)
    }

    val liveSsMediaSourceFactory: SsMediaSource.Factory
        get() = SsMediaSource.Factory(DefaultSsChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY)
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS.toLong())

    val liveHlsMediaSourceFactory: HlsMediaSource.Factory
        get() = HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY)

    val liveDashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(DefaultDashChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setMinLoadableRetryCount(MANIFEST_MINIMUM_RETRY)
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS.toLong())

    val ssMediaSourceFactory: SsMediaSource.Factory
        get() = SsMediaSource.Factory(DefaultSsChunkSource.Factory(cacheDataSourceFactory), cacheDataSourceFactory)

    val hlsMediaSourceFactory: HlsMediaSource.Factory
        get() = HlsMediaSource.Factory(cacheDataSourceFactory)

    val dashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(DefaultDashChunkSource.Factory(cacheDataSourceFactory), cacheDataSourceFactory)

    private val extractorMediaSourceFactory: ExtractorMediaSource.Factory
        get() = ExtractorMediaSource.Factory(cacheDataSourceFactory)
                .setMinLoadableRetryCount(EXTRACTOR_MINIMUM_RETRY)

    val sampleMediaSourceFactory: SingleSampleMediaSource.Factory
        get() = SingleSampleMediaSource.Factory(cacheDataSourceFactory)


    fun getExtractorMediaSourceFactory(key: String): ExtractorMediaSource.Factory = extractorMediaSourceFactory.setCustomCacheKey(key)


    companion object {
        private const val MANIFEST_MINIMUM_RETRY = 5
        private const val EXTRACTOR_MINIMUM_RETRY = Integer.MAX_VALUE
        private const val LIVE_STREAM_EDGE_GAP_MILLIS = 10000
    }
}
