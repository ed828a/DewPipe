package org.schabi.newpipe.download.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat

class ProgressDrawable(context: Context, @ColorRes background: Int, @ColorRes foreground: Int) : Drawable() {
    private val mBackgroundColor: Int = ContextCompat.getColor(context, background)
    private val mForegroundColor: Int = ContextCompat.getColor(context, foreground)
    private var mProgress: Float = 0.toFloat()


    fun setProgress(progress: Float) {
        mProgress = progress
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
//        val width = canvas.width
//        val height = canvas.height
        val width = bounds.width()
        val height = bounds.height()

        val paint = Paint()

        paint.color = mBackgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = mForegroundColor
        val tem = mProgress * width
        canvas.drawRect(0f, 0f, mProgress * width, height.toFloat(), paint)
//        canvas.drawRect(0f, 0f, (mProgress * width).toInt().toFloat(), height.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        // Unsupported
    }

    override fun setColorFilter(filter: ColorFilter?) {
        // Unsupported
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE

}
