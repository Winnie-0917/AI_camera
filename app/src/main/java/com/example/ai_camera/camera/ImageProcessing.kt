package com.example.ai_camera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * Post-capture JPEG work: the aspect crop a fixed-size sensor output cannot do by itself, and the
 * chosen [PhotoStyle]. Both happen in one decode/encode pass so a graded crop is not compressed
 * twice.
 */
object ImageProcessing {
    fun process(
        jpegBytes: ByteArray,
        targetRatio: Float,
        style: PhotoStyle,
        styleStrength: Int,
        quality: Int,
    ): ByteArray {
        val cropping = targetRatio > 0f
        val grading = !style.isNoOp(styleStrength)
        if (!cropping && !grading) return jpegBytes

        val source = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return jpegBytes
        try {
            val cropped = if (cropping) cropTo(source, targetRatio) else source
            try {
                val graded = if (grading) grade(cropped, style, styleStrength) else cropped
                try {
                    val out = ByteArrayOutputStream()
                    graded.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    return out.toByteArray()
                } finally {
                    if (graded !== cropped) graded.recycle()
                }
            } finally {
                if (cropped !== source) cropped.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun cropTo(source: Bitmap, targetRatio: Float): Bitmap {
        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        return when {
            kotlin.math.abs(sourceRatio - targetRatio) < 0.01f -> source
            sourceRatio > targetRatio -> {
                val newWidth = (source.height * targetRatio).toInt().coerceAtMost(source.width)
                Bitmap.createBitmap(source, (source.width - newWidth) / 2, 0, newWidth, source.height)
            }
            else -> {
                val newHeight = (source.width / targetRatio).toInt().coerceAtMost(source.height)
                Bitmap.createBitmap(source, 0, (source.height - newHeight) / 2, source.width, newHeight)
            }
        }
    }

    private fun grade(source: Bitmap, style: PhotoStyle, strength: Int): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(style.matrixFor(strength)))
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
