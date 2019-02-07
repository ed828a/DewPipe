package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.preference.PreferenceManager

import com.nostra13.universalimageloader.core.download.BaseImageDownloader
import org.schabi.newpipe.R

import org.schabi.newpipe.extractor.NewPipe

import java.io.IOException
import java.io.InputStream

class ImageDownloader(context: Context) : BaseImageDownloader(context) {
    private val resources: Resources = context.resources
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val downloadThumbnailKey: String = context.getString(R.string.download_thumbnail_key)

    private val isDownloadingThumbnail: Boolean
        get() = preferences.getBoolean(downloadThumbnailKey, true)

    @SuppressLint("ResourceType")
    @Throws(IOException::class)
    override fun getStream(imageUri: String, extra: Any?): InputStream {
        return if (isDownloadingThumbnail) {
            super.getStream(imageUri, extra)
        } else {
            resources.openRawResource(R.drawable.dummy_thumbnail_dark)
        }
    }

    @Throws(IOException::class)
    override fun getStreamFromNetwork(imageUri: String, extra: Any?): InputStream {
        val downloader = NewPipe.getDownloader() as Downloader
        return downloader.stream(imageUri)
    }
}
