package org.schabi.newpipe.player.resolver

import android.content.Context

import com.google.android.exoplayer2.source.MediaSource

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.util.ListHelper

class AudioPlaybackResolver(private val context: Context,
                            private val dataSource: PlayerDataSource) : PlaybackResolver {

    override fun resolve(info: StreamInfo): MediaSource? {
        val liveSource = maybeBuildLiveMediaSource(dataSource, info)
        if (liveSource != null) return liveSource

        val index = ListHelper.getDefaultAudioFormat(context, info.audioStreams)
        if (index < 0 || index >= info.audioStreams.size) return null

        val audio = info.audioStreams[index]
        val tag = MediaSourceTag(info)
        return buildMediaSource(dataSource, audio.getUrl(), PlayerHelper.cacheKeyOf(info, audio),
                MediaFormat.getSuffixById(audio.formatId), tag)
    }
}
