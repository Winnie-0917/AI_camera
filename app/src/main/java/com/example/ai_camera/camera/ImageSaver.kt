package com.example.ai_camera.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ExifMetadata(
    val iso: Int? = null,
    val exposureTimeNanos: Long? = null,
    val focalLengthMm: Float? = null,
    val fNumber: Float? = null,
    val orientationDegrees: Int = 0,
)

/** Persists captured JPEG/DNG bytes into the shared Pictures/AICamera album with EXIF tags. */
object ImageSaver {
    private const val ALBUM = "AICamera"
    private val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun saveJpeg(context: Context, bytes: ByteArray, exif: ExifMetadata): Uri =
        withContext(Dispatchers.IO) {
            val displayName = "IMG_${nameFormat.format(System.currentTimeMillis())}"
            val uri = writeFile(context, bytes, displayName, "jpg", "image/jpeg")
            applyExif(context, uri, exif)
            uri
        }

    suspend fun saveDng(context: Context, bytes: ByteArray): Uri =
        withContext(Dispatchers.IO) {
            val displayName = "RAW_${nameFormat.format(System.currentTimeMillis())}"
            writeFile(context, bytes, displayName, "dng", "image/x-adobe-dng")
        }

    private fun writeFile(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        extension: String,
        mimeType: String,
    ): Uri {
        val resolver = context.contentResolver
        val fileName = "$displayName.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Unable to open output stream for $uri")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        val albumDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!albumDir.exists()) albumDir.mkdirs()
        val file = File(albumDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        return uri ?: Uri.fromFile(file)
    }

    private fun applyExif(context: Context, uri: Uri, exif: ExifMetadata) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exifInterface = ExifInterface(pfd.fileDescriptor)
                    writeAttributes(exifInterface, exif)
                    exifInterface.saveAttributes()
                }
            } else {
                val path = uri.let { queryDataPath(context, it) } ?: return
                val exifInterface = ExifInterface(path)
                writeAttributes(exifInterface, exif)
                exifInterface.saveAttributes()
            }
        } catch (_: Exception) {
            // EXIF is best-effort metadata; a failure here must not lose the photo itself.
        }
    }

    private fun writeAttributes(exifInterface: ExifInterface, exif: ExifMetadata) {
        exif.iso?.let {
            exifInterface.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString())
        }
        exif.exposureTimeNanos?.let {
            val seconds = it / 1_000_000_000.0
            exifInterface.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, seconds.toString())
        }
        exif.fNumber?.let {
            exifInterface.setAttribute(ExifInterface.TAG_F_NUMBER, it.toString())
        }
        exif.focalLengthMm?.let {
            exifInterface.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(it * 10).toInt()}/10")
        }
        val orientation = when (exif.orientationDegrees) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
        exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
    }

    private fun queryDataPath(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return null
    }
}
