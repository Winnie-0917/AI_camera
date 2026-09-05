package com.example.ai_camera.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Camera viewfinder. The frame is letterboxed rather than center-cropped so the user always
 * sees the entire captured image - a cropped viewfinder would hide parts of the final photo,
 * which is unacceptable for manual framing.
 *
 * @param contentAspect displayed width/height of the camera frame, or null while unknown.
 * @param onTapFocus receives the tap position normalized to the visible frame (0..1).
 */
@Composable
fun CameraPreview(
    contentAspect: Float?,
    /**
     * Extra rotation the displayed frame needs, in degrees. Front sensors usually report 270
     * where back sensors report 90, and the buffer arrives oriented for the latter, so a front
     * preview comes out upside down without this.
     */
    previewRotation: Int,
    onSurfaceAvailable: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTapFocus: (Float, Float) -> Unit,
    onZoomDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Hands out the view so callers can snapshot the live frame via TextureView.getBitmap(). */
    onViewReady: (TextureView) -> Unit = {},
) {
    var viewSize by remember { mutableStateOf(0 to 0) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            viewSize = width to height
                            onSurfaceAvailable(surface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            viewSize = width to height
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            onSurfaceDestroyed()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                    onViewReady(this)
                }
            },
            update = { view ->
                if (contentAspect != null && view.width > 0 && view.height > 0) {
                    applyLetterboxTransform(
                        view,
                        contentAspect,
                        view.width,
                        view.height,
                        previewRotation,
                    )
                }
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(contentAspect, viewSize) {
                    detectTapGestures { offset ->
                        val point = mapToFrame(offset.x, offset.y, contentAspect, viewSize)
                        if (point != null) onTapFocus(point.first, point.second)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) onZoomDelta(zoom)
                    }
                }
        )
    }
}

/** Maps a view-space touch into 0..1 coordinates inside the letterboxed frame, or null if outside. */
private fun mapToFrame(
    x: Float,
    y: Float,
    contentAspect: Float?,
    viewSize: Pair<Int, Int>,
): Pair<Float, Float>? {
    val (viewWidth, viewHeight) = viewSize
    if (contentAspect == null || viewWidth <= 0 || viewHeight <= 0) return null
    val viewAspect = viewWidth.toFloat() / viewHeight
    val contentWidth: Float
    val contentHeight: Float
    if (contentAspect > viewAspect) {
        contentWidth = viewWidth.toFloat()
        contentHeight = viewWidth / contentAspect
    } else {
        contentHeight = viewHeight.toFloat()
        contentWidth = viewHeight * contentAspect
    }
    val nx = (x - (viewWidth - contentWidth) / 2f) / contentWidth
    val ny = (y - (viewHeight - contentHeight) / 2f) / contentHeight
    return if (nx in 0f..1f && ny in 0f..1f) nx to ny else null
}

private fun applyLetterboxTransform(
    view: TextureView,
    contentAspect: Float,
    viewWidth: Int,
    viewHeight: Int,
    rotationDegrees: Int,
) {
    if (viewWidth <= 0 || viewHeight <= 0 || contentAspect <= 0f) return
    val viewAspect = viewWidth.toFloat() / viewHeight
    val matrix = Matrix()
    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f
    if (contentAspect > viewAspect) {
        matrix.setScale(1f, viewAspect / contentAspect, centerX, centerY)
    } else {
        matrix.setScale(contentAspect / viewAspect, 1f, centerX, centerY)
    }
    // Only 180 is applied: it leaves the frame's aspect alone, so the letterboxing above still
    // holds. A 90/270 correction would also have to swap the aspect, and no device here reports
    // one, so it is left unhandled rather than written blind.
    if (rotationDegrees == 180) {
        matrix.postRotate(180f, centerX, centerY)
    }
    view.setTransform(matrix)
}
