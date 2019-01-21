package org.schabi.newpipe.local

import android.content.Context
import android.widget.ImageView

import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader

import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.util.OnClickGesture


class LocalItemBuilder(val context: Context?) {
    private val imageLoader = ImageLoader.getInstance()

    var onItemSelectedListener: OnClickGesture<LocalItem>? = null

    fun displayImage(url: String,
                     view: ImageView,
                     options: DisplayImageOptions) {
        imageLoader.displayImage(url, view, options)
    }

}
