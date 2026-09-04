package com.example.ai_camera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** Post-capture JPEG cropping so a fixed-size sensor output can still honor a chosen aspect ratio. */
object ImageProcessing {
    fun cropToAspect(jpegBytes: ByteArray, targetRatio: Float, quality: Int): ByteArray {
        val source = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return jpegBytes
        try {
            val sourceRatio = source.width.toFloat() / source.height.toFloat()
            val cropped = if (kotlin.math.abs(sourceRatio - targetRatio) < 0.01f) {
                source
            } else if (sourceRatio > targetRatio) {
                val newWidth = (source.height * targetRatio).toInt().coerceAtMost(source.width)
                Bitmap.createBitmap(source, (source.width - newWidth) / 2, 0, newWidth, source.height)
            } else {
                val newHeight = (source.width / targetRatio).toInt().coerceAtMost(source.height)
                Bitmap.createBitmap(source, 0, (source.height - newHeight) / 2, source.width, newHeight)
            }
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (cropped !== source) cropped.recycle()
            return out.toByteArray()
        } finally {
            source.recycle()
        }
    }
}
