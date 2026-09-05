package com.example.ai_camera.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.SurfaceTexture
import android.os.Build
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai_camera.camera.PhotoStyle

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
     * Extra rotation for the displayed frame. See PreviewOrientation for why a front preview
     * needs a half turn and why that also gives the mirrored selfie view.
     */
    previewRotation: Int,
    /** Mirror the displayed frame horizontally, the selfie convention for a front lens. */
    mirrorPreview: Boolean,
    /** Graded live so the viewfinder shows the look that will actually be saved. */
    style: PhotoStyle,
    styleStrength: Int,
    onSurfaceAvailable: (SurfaceTexture) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTapFocus: (Float, Float) -> Unit,
    onZoomDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Hands out the view so callers can snapshot the live frame via TextureView.getBitmap(). */
    onViewReady: (TextureView) -> Unit = {},
) {
    // Written only from onSizeChanged. The surface callbacks report the view's size too, but they
    // arrive on their own schedule, and whichever wrote last decided how the frame was
    // transformed - which is how the picture ended up squeezed in one mode and not the other.
    var viewSize by remember { mutableStateOf(0 to 0) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it.width to it.height },
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            onSurfaceAvailable(surface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = Unit

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
                applyStyle(view, style, styleStrength)
                val (width, height) = viewSize
                // The size Compose laid out, not view.width/height: update runs during
                // composition, before layout, so the view's own dimensions are a frame behind.
                // Reading them left the picture transformed for the size it used to be - a
                // squeeze of a few percent that moved every time the chrome changed height.
                if (contentAspect != null && width > 0 && height > 0) {
                    applyLetterboxTransform(
                        view,
                        contentAspect,
                        width,
                        height,
                        previewRotation,
                        mirrorPreview,
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

/**
 * Grades the viewfinder itself, so what is framed is what gets saved.
 *
 * RenderEffect only exists from API 31. Below that the preview stays ungraded and the style is
 * applied at capture; the alternative - rebuilding the viewfinder on GLSurfaceView with a shader -
 * is a lot of machinery for the handful of remaining Android 11 devices.
 */
private fun applyStyle(view: TextureView, style: PhotoStyle, strength: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    view.setRenderEffect(
        if (style.isNoOp(strength)) {
            null
        } else {
            RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix(style.matrixFor(strength)))
            )
        }
    )
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
    mirror: Boolean,
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
    if (mirror) {
        matrix.postScale(-1f, 1f, centerX, centerY)
    }
    if (rotationDegrees == 180) {
        matrix.postRotate(180f, centerX, centerY)
    }
    view.setTransform(matrix)
}
