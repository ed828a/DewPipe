package org.schabi.newpipe.util

import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

import java.util.ArrayList
import java.util.Arrays

import org.junit.Assert.assertEquals

class ListHelperTest {

    @Test
    fun getSortedStreamVideosListTest() {
        var result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true, videoStreamsTestList, videoOnlyStreamsTestList, true)

        var expected = Arrays.asList("144p", "240p", "360p", "480p", "720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60")
        //for (VideoStream videoStream : result) System.out.println(videoStream.resolution + " > " + MediaFormat.getSuffixById(videoStream.format) + " > " + videoStream.isVideoOnly);

        assertEquals(result.size.toLong(), expected.size.toLong())
        for (i in result.indices) {
            assertEquals(result[i].resolution, expected[i])
        }

        ////////////////////
        // Reverse Order //
        //////////////////

        result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true, videoStreamsTestList, videoOnlyStreamsTestList, false)
        expected = Arrays.asList("2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p")
        assertEquals(result.size.toLong(), expected.size.toLong())
        for (i in result.indices) assertEquals(result[i].resolution, expected[i])
    }

    @Test
    fun getSortedStreamVideosExceptHighResolutionsTest() {
        ////////////////////////////////////
        // Don't show Higher resolutions //
        //////////////////////////////////

        val result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, false, videoStreamsTestList, videoOnlyStreamsTestList, false)
        val expected = Arrays.asList("1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p")
        assertEquals(result.size.toLong(), expected.size.toLong())
        for (i in result.indices) assertEquals(result[i].resolution, expected[i])
    }

    @Test
    fun getDefaultResolutionTest() {
        val testList = Arrays.asList(
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p"),
                VideoStream("", MediaFormat.v3GPP, /**/ "240p"),
                VideoStream("", MediaFormat.WEBM, /**/ "480p"),
                VideoStream("", MediaFormat.WEBM, /**/ "240p"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "240p"),
                VideoStream("", MediaFormat.WEBM, /**/ "144p"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "360p"),
                VideoStream("", MediaFormat.WEBM, /**/ "360p"))
        var result = testList[ListHelper.getDefaultResolutionIndex("720p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList)]
        assertEquals("720p", result.resolution)
        assertEquals(MediaFormat.MPEG_4, result.getFormat())

        // Have resolution and the format
        result = testList[ListHelper.getDefaultResolutionIndex("480p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("480p", result.resolution)
        assertEquals(MediaFormat.WEBM, result.getFormat())

        // Have resolution but not the format
        result = testList[ListHelper.getDefaultResolutionIndex("480p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList)]
        assertEquals("480p", result.resolution)
        assertEquals(MediaFormat.WEBM, result.getFormat())

        // Have resolution and the format
        result = testList[ListHelper.getDefaultResolutionIndex("240p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("240p", result.resolution)
        assertEquals(MediaFormat.WEBM, result.getFormat())

        // The best resolution
        result = testList[ListHelper.getDefaultResolutionIndex(BEST_RESOLUTION_KEY, BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("720p", result.resolution)
        assertEquals(MediaFormat.MPEG_4, result.getFormat())

        // Doesn't have the 60fps variant and format
        result = testList[ListHelper.getDefaultResolutionIndex("720p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("720p", result.resolution)
        assertEquals(MediaFormat.MPEG_4, result.getFormat())

        // Doesn't have the 60fps variant
        result = testList[ListHelper.getDefaultResolutionIndex("480p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("480p", result.resolution)
        assertEquals(MediaFormat.WEBM, result.getFormat())

        // Doesn't have the resolution, will return the best one
        result = testList[ListHelper.getDefaultResolutionIndex("2160p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
        assertEquals("720p", result.resolution)
        assertEquals(MediaFormat.MPEG_4, result.getFormat())
    }

    @Test
    fun getHighestQualityAudioFormatTest() {
        var stream = audioStreamsTestList[ListHelper.getHighestQualityAudioIndex(MediaFormat.M4A, audioStreamsTestList)]
        assertEquals(320, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.M4A, stream.getFormat())

        stream = audioStreamsTestList[ListHelper.getHighestQualityAudioIndex(MediaFormat.WEBMA, audioStreamsTestList)]
        assertEquals(320, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())

        stream = audioStreamsTestList[ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, audioStreamsTestList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.MP3, stream.getFormat())
    }

    @Test
    fun getHighestQualityAudioFormatPreferredAbsent() {

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        var testList: MutableList<AudioStream> = Arrays.asList(
                AudioStream("", MediaFormat.M4A, /**/ 128),
                AudioStream("", MediaFormat.WEBMA, /**/ 192))
        // List doesn't contains this format, it should fallback to the highest bitrate audio no matter what format it is
        var stream = testList[ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = ArrayList(Arrays.asList(
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 192)))
        // List doesn't contains this format, it should fallback to the highest bitrate audio and
        // the highest quality format.
        stream = testList[ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.M4A, stream.getFormat())

        // Adding a new format and bitrate. Adding another stream will have no impact since
        // it's not a prefered format.
        testList.add(AudioStream("", MediaFormat.WEBMA, /**/ 192))
        stream = testList[ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.M4A, stream.getFormat())
    }

    @Test
    fun getHighestQualityAudioNull() {
        assertEquals(-1, ListHelper.getHighestQualityAudioIndex(null, null).toLong())
        assertEquals(-1, ListHelper.getHighestQualityAudioIndex(null, ArrayList()).toLong())
    }

    @Test
    fun getLowestQualityAudioFormatTest() {
        var stream = audioStreamsTestList[ListHelper.getMostCompactAudioIndex(MediaFormat.M4A, audioStreamsTestList)]
        assertEquals(128, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.M4A, stream.getFormat())

        stream = audioStreamsTestList[ListHelper.getMostCompactAudioIndex(MediaFormat.WEBMA, audioStreamsTestList)]
        assertEquals(64, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())

        stream = audioStreamsTestList[ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, audioStreamsTestList)]
        assertEquals(64, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.MP3, stream.getFormat())
    }

    @Test
    fun getLowestQualityAudioFormatPreferredAbsent() {

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        var testList: MutableList<AudioStream> = ArrayList(Arrays.asList(
                AudioStream("", MediaFormat.M4A, /**/ 128),
                AudioStream("", MediaFormat.WEBMA, /**/ 192)))
        // List doesn't contains this format, it should fallback to the most compact audio no matter what format it is.
        var stream = testList[ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(128, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.M4A, stream.getFormat())

        // WEBMA is more compact than M4A
        testList.add(AudioStream("", MediaFormat.WEBMA, /**/ 128))
        stream = testList[ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(128, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = ArrayList(Arrays.asList(
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 256),
                AudioStream("", MediaFormat.M4A, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 192)))
        // List doesn't contains this format, it should fallback to the most compact audio no matter what format it is.
        stream = testList[ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, testList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())

        // Should be same as above
        stream = testList[ListHelper.getMostCompactAudioIndex(null, testList)]
        assertEquals(192, stream.average_bitrate.toLong())
        assertEquals(MediaFormat.WEBMA, stream.getFormat())
    }

    @Test
    fun getLowestQualityAudioNull() {
        assertEquals(-1, ListHelper.getMostCompactAudioIndex(null, null).toLong())
        assertEquals(-1, ListHelper.getMostCompactAudioIndex(null, ArrayList()).toLong())
    }

    @Test
    fun getVideoDefaultStreamIndexCombinations() {
        val testList = Arrays.asList(
                VideoStream("", MediaFormat.MPEG_4, /**/ "1080p"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p60"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p"),
                VideoStream("", MediaFormat.WEBM, /**/ "480p"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "360p"),
                VideoStream("", MediaFormat.WEBM, /**/ "360p"),
                VideoStream("", MediaFormat.v3GPP, /**/ "240p60"),
                VideoStream("", MediaFormat.WEBM, /**/ "144p"))

        // exact matches
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, testList).toLong())
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.MPEG_4, testList).toLong())

        // match but not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.MPEG_4, testList).toLong())
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.v3GPP, testList).toLong())

        // match but not format
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.WEBM, testList).toLong())
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.WEBM, testList).toLong())
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", null, testList).toLong())
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", null, testList).toLong())

        // match but not format and not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.WEBM, testList).toLong())
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.WEBM, testList).toLong())
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", null, testList).toLong())
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", null, testList).toLong())

        // match closest lower resolution
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.WEBM, testList).toLong())
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.WEBM, testList).toLong())
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.MPEG_4, testList).toLong())
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.MPEG_4, testList).toLong())
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", null, testList).toLong())
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", null, testList).toLong())

        // Can't find a match
        assertEquals(-1, ListHelper.getVideoStreamIndex("100p", null, testList).toLong())
    }

    companion object {
        private val BEST_RESOLUTION_KEY = "best_resolution"
        private val audioStreamsTestList = Arrays.asList(
                AudioStream("", MediaFormat.M4A, /**/ 128),
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.MP3, /**/ 64),
                AudioStream("", MediaFormat.WEBMA, /**/ 192),
                AudioStream("", MediaFormat.M4A, /**/ 128),
                AudioStream("", MediaFormat.MP3, /**/ 128),
                AudioStream("", MediaFormat.WEBMA, /**/ 64),
                AudioStream("", MediaFormat.M4A, /**/ 320),
                AudioStream("", MediaFormat.MP3, /**/ 192),
                AudioStream("", MediaFormat.WEBMA, /**/ 320))

        private val videoStreamsTestList = Arrays.asList(
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p"),
                VideoStream("", MediaFormat.v3GPP, /**/ "240p"),
                VideoStream("", MediaFormat.WEBM, /**/ "480p"),
                VideoStream("", MediaFormat.v3GPP, /**/ "144p"),
                VideoStream("", MediaFormat.MPEG_4, /**/ "360p"),
                VideoStream("", MediaFormat.WEBM, /**/ "360p"))

        private val videoOnlyStreamsTestList = Arrays.asList(
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "2160p", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "1440p60", true),
                VideoStream("", MediaFormat.WEBM, /**/ "720p60", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "2160p60", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "720p60", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "1080p", true),
                VideoStream("", MediaFormat.MPEG_4, /**/ "1080p60", true))
    }
}