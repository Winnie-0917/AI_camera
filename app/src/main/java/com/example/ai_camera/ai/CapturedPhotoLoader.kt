package com.example.ai_camera.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Reads a just-saved photo back for the assistant to look at.
 *
 * Two things matter here. A capture is 12MP, far larger than a vision request needs, so it is
 * sampled down while decoding rather than loaded whole. And the rotation stored in EXIF has to be
 * baked into the pixels: the model does not read EXIF, so a portrait shot would otherwise arrive
 * sideways and every observation about the pose would be wrong.
 */
object CapturedPhotoLoader {
    private const val MAX_EDGE = 1024
    private const val QUALITY = 85

    /**
     * A small copy of what was sent, for the chat bubble. Decoded from the same bytes the model
     * receives so the two can never disagree, and kept small because a transcript may hold several.
     */
    suspend fun thumbnail(jpeg: ByteArray, maxEdge: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= maxEdge }
            }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        }.getOrNull()
    }

    suspend fun load(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= MAX_EDGE }
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val upright = if (rotation == 0f) {
                decoded
            } else {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true,
                ).also { if (it !== decoded) decoded.recycle() }
            }

            ByteArrayOutputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                upright.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }
}
