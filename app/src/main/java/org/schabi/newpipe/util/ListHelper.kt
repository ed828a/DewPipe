package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.preference.PreferenceManager
import android.support.annotation.StringRes

import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap

object ListHelper {

    // Video format in order of quality. 0=lowest quality, n=highest quality
    private val VIDEO_FORMAT_QUALITY_RANKING = Arrays.asList(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4)

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private val AUDIO_FORMAT_QUALITY_RANKING = Arrays.asList(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A)
    // Audio format in order of efficiency. 0=most efficient, n=least efficient
    private val AUDIO_FORMAT_EFFICIENCY_RANKING = Arrays.asList(MediaFormat.WEBMA, MediaFormat.M4A, MediaFormat.MP3)

    private val HIGH_RESOLUTION_LIST = Arrays.asList("1440p", "2160p", "1440p60", "2160p60")

    /**
     * @see .getDefaultResolutionIndex
     */
    fun getDefaultResolutionIndex(context: Context, videoStreams: List<VideoStream>): Int {
        val defaultResolution = computeDefaultResolution(context,
                R.string.default_resolution_key, R.string.default_resolution_value)
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @see .getDefaultResolutionIndex
     */
    fun getResolutionIndex(context: Context, videoStreams: List<VideoStream>, defaultResolution: String): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @see .getDefaultResolutionIndex
     */
    fun getPopupDefaultResolutionIndex(context: Context, videoStreams: List<VideoStream>): Int {
        val defaultResolution = computeDefaultResolution(context,
                R.string.default_popup_resolution_key, R.string.default_popup_resolution_value)
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @see .getDefaultResolutionIndex
     */
    fun getPopupResolutionIndex(context: Context, videoStreams: List<VideoStream>, defaultResolution: String): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    fun getDefaultAudioFormat(context: Context, audioStreams: List<AudioStream>): Int {
        val defaultFormat = getDefaultFormat(context, R.string.default_audio_format_key,
                R.string.default_audio_format_value)

        // If the user has chosen to limit resolution to conserve mobile data
        // usage then we should also limit our audio usage.
        return if (isLimitingDataUsage(context)) {
            getMostCompactAudioIndex(defaultFormat, audioStreams)
        } else {
            getHighestQualityAudioIndex(defaultFormat, audioStreams)
        }
    }

    /**
     * Join the two lists of video streams (video_only and normal videos), and sort them according with default format
     * chosen by the user
     *
     * @param context          context to search for the format to give preference
     * @param videoStreams     normal videos list
     * @param videoOnlyStreams video only stream list
     * @param ascendingOrder   true -> smallest to greatest | false -> greatest to smallest
     * @return the sorted list
     */
    fun getSortedStreamVideosList(context: Context, videoStreams: List<VideoStream>, videoOnlyStreams: List<VideoStream>?, ascendingOrder: Boolean): List<VideoStream> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val showHigherResolutions = preferences.getBoolean(context.getString(R.string.show_higher_resolutions_key), false)
        val defaultFormat = getDefaultFormat(context, R.string.default_video_format_key, R.string.default_video_format_value)

        return getSortedStreamVideosList(defaultFormat, showHigherResolutions, videoStreams, videoOnlyStreams, ascendingOrder)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private fun computeDefaultResolution(context: Context, key: Int, value: Int): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Load the prefered resolution otherwise the best available
        var resolution: String? = if (preferences != null)
            preferences.getString(context.getString(key), context.getString(value))
        else
            context.getString(R.string.best_resolution_key)

        val maxResolution = getResolutionLimit(context)
        if (maxResolution != null && compareVideoStreamResolution(maxResolution, resolution!!) < 1) {
            resolution = maxResolution
        }
        return resolution!!
    }

    /**
     * Return the index of the default stream in the list, based on the parameters
     * defaultResolution and defaultFormat
     *
     * @return index of the default resolution&format
     */
    internal fun getDefaultResolutionIndex(defaultResolution: String, bestResolutionKey: String,
                                           defaultFormat: MediaFormat?, videoStreams: List<VideoStream>?): Int {
        if (videoStreams == null || videoStreams.isEmpty()) return -1

        sortStreamList(videoStreams, false)
        if (defaultResolution == bestResolutionKey) {
            return 0
        }

        val defaultStreamIndex = getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams)

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        return if (defaultStreamIndex == -1) {
            0
        } else defaultStreamIndex
    }

    /**
     * Join the two lists of video streams (video_only and normal videos), and sort them according with default format
     * chosen by the user
     *
     * @param defaultFormat       format to give preference
     * @param showHigherResolutions show >1080p resolutions
     * @param videoStreams          normal videos list
     * @param videoOnlyStreams      video only stream list
     * @param ascendingOrder        true -> smallest to greatest | false -> greatest to smallest    @return the sorted list
     * @return the sorted list
     */
    internal fun getSortedStreamVideosList(defaultFormat: MediaFormat?, showHigherResolutions: Boolean, videoStreams: List<VideoStream>?, videoOnlyStreams: List<VideoStream>?, ascendingOrder: Boolean): List<VideoStream> {
        val retList = ArrayList<VideoStream>()
        val hashMap = HashMap<String, VideoStream>()

        if (videoOnlyStreams != null) {
            for (stream in videoOnlyStreams) {
                if (!showHigherResolutions && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) continue
                retList.add(stream)
            }
        }
        if (videoStreams != null) {
            for (stream in videoStreams) {
                if (!showHigherResolutions && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) continue
                retList.add(stream)
            }
        }

        // Add all to the hashmap
        for (videoStream in retList) hashMap[videoStream.getResolution()] = videoStream

        // Override the values when the key == resolution, with the defaultFormat
        for (videoStream in retList) {
            if (videoStream.getFormat() == defaultFormat) hashMap[videoStream.getResolution()] = videoStream
        }

        retList.clear()
        retList.addAll(hashMap.values)
        sortStreamList(retList, ascendingOrder)
        return retList
    }

    /**
     * Sort the streams list depending on the parameter ascendingOrder;
     *
     *
     * It works like that:<br></br>
     * - Take a string resolution, remove the letters, replace "0p60" (for 60fps videos) with "1"
     * and sort by the greatest:<br></br>
     * <blockquote><pre>
     * 720p     ->  720
     * 720p60   ->  721
     * 360p     ->  360
     * 1080p    ->  1080
     * 1080p60  ->  1081
     * <br></br>
     * ascendingOrder  ? 360 < 720 < 721 < 1080 < 1081
     * !ascendingOrder ? 1081 < 1080 < 721 < 720 < 360</pre></blockquote>
     *
     * @param videoStreams   list that the sorting will be applied
     * @param ascendingOrder true -> smallest to greatest | false -> greatest to smallest
     */
    private fun sortStreamList(videoStreams: List<VideoStream>, ascendingOrder: Boolean) {
        Collections.sort(videoStreams) { o1, o2 ->
            val result = compareVideoStreamResolution(o1, o2)
            if (result == 0) 0 else if (ascendingOrder) result else -result
        }
    }

    /**
     * Get the audio from the list with the highest quality. Format will be ignored if it yields
     * no results.
     *
     * @param audioStreams list the audio streams
     * @return index of the audio with the highest average bitrate of the default format
     */
    internal fun getHighestQualityAudioIndex(format: MediaFormat?, audioStreams: List<AudioStream>?): Int {
        var format = format
        var result = -1
        if (audioStreams != null) {
            while (result == -1) {
                var prevStream: AudioStream? = null
                for (idx in audioStreams.indices) {
                    val stream = audioStreams[idx]
                    if ((format == null || stream.getFormat() == format) && (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
                                    AUDIO_FORMAT_QUALITY_RANKING) < 0)) {
                        prevStream = stream
                        result = idx
                    }
                }
                if (result == -1 && format == null) {
                    break
                }
                format = null
            }
        }
        return result
    }

    /**
     * Get the audio from the list with the lowest bitrate and efficient format. Format will be
     * ignored if it yields no results.
     *
     * @param format The target format type or null if it doesn't matter
     * @param audioStreams list the audio streams
     * @return index of the audio stream that can produce the most compact results or -1 if not found.
     */
    internal fun getMostCompactAudioIndex(format: MediaFormat?, audioStreams: List<AudioStream>?): Int {
        var format = format
        var result = -1
        if (audioStreams != null) {
            while (result == -1) {
                var prevStream: AudioStream? = null
                for (idx in audioStreams.indices) {
                    val stream = audioStreams[idx]
                    if ((format == null || stream.getFormat() == format) && (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
                                    AUDIO_FORMAT_EFFICIENCY_RANKING) > 0)) {
                        prevStream = stream
                        result = idx
                    }
                }
                if (result == -1 && format == null) {
                    break
                }
                format = null
            }
        }
        return result
    }

    /**
     * Locates a possible match for the given resolution and format in the provided list.
     * In this order:
     * 1. Find a format and resolution match
     * 2. Find a format and resolution match and ignore the refresh
     * 3. Find a resolution match
     * 4. Find a resolution match and ignore the refresh
     * 5. Find a resolution just below the requested resolution and ignore the refresh
     * 6. Give up
     */
    internal fun getVideoStreamIndex(targetResolution: String, targetFormat: MediaFormat?,
                                     videoStreams: List<VideoStream>): Int {
        var fullMatchIndex = -1
        var fullMatchNoRefreshIndex = -1
        var resMatchOnlyIndex = -1
        var resMatchOnlyNoRefreshIndex = -1
        var lowerResMatchNoRefreshIndex = -1
        val targetResolutionNoRefresh = targetResolution.replace("p\\d+$".toRegex(), "p")

        for (idx in videoStreams.indices) {
            val format = if (targetFormat == null) null else videoStreams[idx].getFormat()
            val resolution = videoStreams[idx].getResolution()
            val resolutionNoRefresh = resolution.replace("p\\d+$".toRegex(), "p")

            if (format == targetFormat && resolution == targetResolution) {
                fullMatchIndex = idx
            }

            if (format == targetFormat && resolutionNoRefresh == targetResolutionNoRefresh) {
                fullMatchNoRefreshIndex = idx
            }

            if (resMatchOnlyIndex == -1 && resolution == targetResolution) {
                resMatchOnlyIndex = idx
            }

            if (resMatchOnlyNoRefreshIndex == -1 && resolutionNoRefresh == targetResolutionNoRefresh) {
                resMatchOnlyNoRefreshIndex = idx
            }

            if (lowerResMatchNoRefreshIndex == -1 && compareVideoStreamResolution(resolutionNoRefresh, targetResolutionNoRefresh) < 0) {
                lowerResMatchNoRefreshIndex = idx
            }
        }

        if (fullMatchIndex != -1) {
            return fullMatchIndex
        }
        if (fullMatchNoRefreshIndex != -1) {
            return fullMatchNoRefreshIndex
        }
        if (resMatchOnlyIndex != -1) {
            return resMatchOnlyIndex
        }
        return if (resMatchOnlyNoRefreshIndex != -1) {
            resMatchOnlyNoRefreshIndex
        } else lowerResMatchNoRefreshIndex
    }

    /**
     * Fetches the desired resolution or returns the default if it is not found. The resolution
     * will be reduced if video chocking is active.
     */
    private fun getDefaultResolutionWithDefaultFormat(context: Context, defaultResolution: String, videoStreams: List<VideoStream>): Int {
        val defaultFormat = getDefaultFormat(context, R.string.default_video_format_key, R.string.default_video_format_value)
        return getDefaultResolutionIndex(defaultResolution, context.getString(R.string.best_resolution_key), defaultFormat, videoStreams)
    }

    private fun getDefaultFormat(context: Context, @StringRes defaultFormatKey: Int, @StringRes defaultFormatValueKey: Int): MediaFormat? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val defaultFormat = context.getString(defaultFormatValueKey)
        val defaultFormatString = preferences.getString(context.getString(defaultFormatKey), defaultFormat)

        var defaultMediaFormat = getMediaFormatFromKey(context, defaultFormatString!!)
        if (defaultMediaFormat == null) {
            preferences.edit().putString(context.getString(defaultFormatKey), defaultFormat).apply()
            defaultMediaFormat = getMediaFormatFromKey(context, defaultFormat)
        }

        return defaultMediaFormat
    }

    private fun getMediaFormatFromKey(context: Context, formatKey: String): MediaFormat? {
        var format: MediaFormat? = null
        if (formatKey == context.getString(R.string.video_webm_key)) {
            format = MediaFormat.WEBM
        } else if (formatKey == context.getString(R.string.video_mp4_key)) {
            format = MediaFormat.MPEG_4
        } else if (formatKey == context.getString(R.string.video_3gp_key)) {
            format = MediaFormat.v3GPP
        } else if (formatKey == context.getString(R.string.audio_webm_key)) {
            format = MediaFormat.WEBMA
        } else if (formatKey == context.getString(R.string.audio_m4a_key)) {
            format = MediaFormat.M4A
        }
        return format
    }

    // Compares the quality of two audio streams
    private fun compareAudioStreamBitrate(streamA: AudioStream?, streamB: AudioStream?,
                                          formatRanking: List<MediaFormat>): Int {
        if (streamA == null) {
            return -1
        }
        if (streamB == null) {
            return 1
        }
        if (streamA.averageBitrate < streamB.averageBitrate) {
            return -1
        }
        return if (streamA.averageBitrate > streamB.averageBitrate) {
            1
        } else formatRanking.indexOf(streamA.getFormat()) - formatRanking.indexOf(streamB.getFormat())

        // Same bitrate and format
    }

    private fun compareVideoStreamResolution(r1: String, r2: String): Int {
        val res1 = Integer.parseInt(r1.replace("0p\\d+$".toRegex(), "1")
                .replace("[^\\d.]".toRegex(), ""))
        val res2 = Integer.parseInt(r2.replace("0p\\d+$".toRegex(), "1")
                .replace("[^\\d.]".toRegex(), ""))
        return res1 - res2
    }

    // Compares the quality of two video streams.
    private fun compareVideoStreamResolution(streamA: VideoStream?, streamB: VideoStream?): Int {
        if (streamA == null) {
            return -1
        }
        if (streamB == null) {
            return 1
        }

        val resComp = compareVideoStreamResolution(streamA.getResolution(), streamB.getResolution())
        return if (resComp != 0) {
            resComp
        } else ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamA.getFormat()) - ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamB.getFormat())

        // Same bitrate and format
    }


    private fun isLimitingDataUsage(context: Context): Boolean {
        return getResolutionLimit(context) != null
    }

    /**
     * The maximum resolution allowed
     * @param context App context
     * @return maximum resolution allowed or null if there is no maximum
     */
    private fun getResolutionLimit(context: Context): String? {
        var resolutionLimit: String? = null
        if (!isWifiActive(context)) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val defValue = context.getString(R.string.limit_data_usage_none_key)
            val value = preferences.getString(
                    context.getString(R.string.limit_mobile_data_usage_key), defValue)
            resolutionLimit = if (value == defValue) null else value
        }
        return resolutionLimit
    }

    /**
     * Are we connected to wifi?
     * @param context App context
     * @return `true` if connected to wifi
     */
    private fun isWifiActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager != null && manager.activeNetworkInfo != null && manager.activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
    }
}
