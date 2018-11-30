package org.schabi.newpipe.util

import android.graphics.Bitmap

import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.assist.ImageScaleType
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer

import org.schabi.newpipe.R

object ImageDisplayConstants {
    private const val BITMAP_FADE_IN_DURATION_MILLIS = 250

    /**
     * Base display options
     */
    private val BASE_DISPLAY_IMAGE_OPTIONS = DisplayImageOptions.Builder()
            .cacheInMemory(true)
            .cacheOnDisk(true)
            .resetViewBeforeLoading(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .imageScaleType(ImageScaleType.EXACTLY)
            .displayer(FadeInBitmapDisplayer(BITMAP_FADE_IN_DURATION_MILLIS))
            .build()

    ///////////////////////////////////////////////////////////////////////////
    // DisplayImageOptions default configurations
    ///////////////////////////////////////////////////////////////////////////

    val DISPLAY_AVATAR_OPTIONS: DisplayImageOptions = DisplayImageOptions.Builder()
            .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
            .showImageForEmptyUri(R.drawable.buddy)
            .showImageOnFail(R.drawable.buddy)
            .build()

    val DISPLAY_THUMBNAIL_OPTIONS: DisplayImageOptions = DisplayImageOptions.Builder()
            .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
            .showImageForEmptyUri(R.drawable.dummy_thumbnail)
            .showImageOnFail(R.drawable.dummy_thumbnail)
            .build()

    val DISPLAY_BANNER_OPTIONS: DisplayImageOptions = DisplayImageOptions.Builder()
            .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
            .showImageForEmptyUri(R.drawable.channel_banner)
            .showImageOnFail(R.drawable.channel_banner)
            .build()

    val DISPLAY_PLAYLIST_OPTIONS: DisplayImageOptions = DisplayImageOptions.Builder()
            .cloneFrom(BASE_DISPLAY_IMAGE_OPTIONS)
            .showImageForEmptyUri(R.drawable.dummy_thumbnail_playlist)
            .showImageOnFail(R.drawable.dummy_thumbnail_playlist)
            .build()
}
