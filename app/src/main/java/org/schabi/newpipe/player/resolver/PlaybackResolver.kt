package org.schabi.newpipe.player.resolver

import android.net.Uri
import android.text.TextUtils

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Util

import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerDataSource

interface PlaybackResolver : Resolver<StreamInfo, MediaSource> {

    fun maybeBuildLiveMediaSource(dataSource: PlayerDataSource,
                                  info: StreamInfo): MediaSource? {
        val streamType = info.streamType
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM || streamType == StreamType.LIVE_STREAM)) {
            return null
        }

        val tag = MediaSourceTag(info)
        if (!info.hlsUrl.isEmpty()) {
            return buildLiveMediaSource(dataSource, info.hlsUrl, C.TYPE_HLS, tag)
        } else if (!info.dashMpdUrl.isEmpty()) {
            return buildLiveMediaSource(dataSource, info.dashMpdUrl, C.TYPE_DASH, tag)
        }

        return null
    }

    fun buildLiveMediaSource(dataSource: PlayerDataSource,
                             sourceUrl: String,
                             @C.ContentType type: Int,
                             metadata: MediaSourceTag): MediaSource {
        val uri = Uri.parse(sourceUrl)
        when (type) {
            C.TYPE_SS -> return dataSource.liveSsMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            C.TYPE_DASH -> return dataSource.liveDashMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            C.TYPE_HLS -> return dataSource.liveHlsMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }

    fun buildMediaSource(dataSource: PlayerDataSource,
                         sourceUrl: String,
                         cacheKey: String,
                         overrideExtension: String,
                         metadata: MediaSourceTag): MediaSource {
        val uri = Uri.parse(sourceUrl)
        @C.ContentType val type = if (TextUtils.isEmpty(overrideExtension))
            Util.inferContentType(uri)
        else
            Util.inferContentType(".$overrideExtension")

        return when (type) {
            C.TYPE_SS -> dataSource.liveSsMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            C.TYPE_DASH -> dataSource.dashMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            C.TYPE_HLS -> dataSource.hlsMediaSourceFactory.setTag(metadata)
                    .createMediaSource(uri)
            C.TYPE_OTHER -> dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                    .createMediaSource(uri)
            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }
}
