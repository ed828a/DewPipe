package us.shandian.giga.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat

class ProgressDrawable(private val mBackgroundColor: Int, private val mForegroundColor: Int) : Drawable() {
    private var mProgress: Float = 0.toFloat()

    constructor(context: Context?, @ColorRes background: Int, @ColorRes foreground: Int) : this(ContextCompat.getColor(context!!, background), ContextCompat.getColor(context!!, foreground)) {}

    fun setProgress(progress: Float) {
        mProgress = progress
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val width = canvas.width
        val height = canvas.height

        val paint = Paint()

        paint.color = mBackgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = mForegroundColor
        canvas.drawRect(0f, 0f, (mProgress * width).toInt().toFloat(), height.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        // Unsupported
    }

    override fun setColorFilter(filter: ColorFilter?) {
        // Unsupported
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

}
