package com.example.ai_camera

import android.hardware.camera2.CameraCharacteristics
import com.example.ai_camera.camera.PreviewOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

private const val BACK = CameraCharacteristics.LENS_FACING_BACK
private const val FRONT = CameraCharacteristics.LENS_FACING_FRONT

class PreviewOrientationTest {

    @Test
    fun `back preview needs no extra rotation`() {
        assertEquals(0, PreviewOrientation.displayRotationFor(BACK))
    }

    // The correction is 180 for every front lens, whether its sensor reports 90 or 270. Deriving
    // it from the sensor angle was the bug: it happens to be right at 270 and wrong at 90.
    @Test
    fun `front preview always needs a half turn`() {
        assertEquals(180, PreviewOrientation.displayRotationFor(FRONT))
    }

    @Test
    fun `an external lens is treated like a back lens`() {
        assertEquals(0, PreviewOrientation.displayRotationFor(CameraCharacteristics.LENS_FACING_EXTERNAL))
    }

    // Measured on hardware: the front buffer arrives vertically flipped, so the analysis frame
    // undoes exactly that, leaving the model an unmirrored view while the display keeps the
    // mirror that a half turn produces.
    @Test
    fun `only the front analysis frame is flipped`() {
        assertEquals(true, PreviewOrientation.analysisFlipsVertically(FRONT))
        assertEquals(false, PreviewOrientation.analysisFlipsVertically(BACK))
    }

    @Test
    fun `a back tap maps straight through`() {
        assertEquals(0.25f to 0.75f, PreviewOrientation.mapTapToBuffer(0.25f, 0.75f, BACK))
    }

    // The front preview is displayed through a half turn, so a tap has to be turned back.
    @Test
    fun `a front tap is turned back through the half turn`() {
        assertEquals(0.75f to 0.25f, PreviewOrientation.mapTapToBuffer(0.25f, 0.75f, FRONT))

        // Compared with a tolerance: 1f - 0.8f is not exactly 0.2f in binary floating point.
        val (x, y) = PreviewOrientation.mapTapToBuffer(0.8f, 0.3f, FRONT)
        assertEquals(0.2f, x, 1e-6f)
        assertEquals(0.7f, y, 1e-6f)
    }

    @Test
    fun `the centre is unmoved on both lenses`() {
        assertEquals(0.5f to 0.5f, PreviewOrientation.mapTapToBuffer(0.5f, 0.5f, FRONT))
        assertEquals(0.5f to 0.5f, PreviewOrientation.mapTapToBuffer(0.5f, 0.5f, BACK))
    }

    @Test
    fun `mapping a tap twice returns it to where it started`() {
        listOf(BACK, FRONT).forEach { facing ->
            val (x, y) = PreviewOrientation.mapTapToBuffer(0.3f, 0.9f, facing)
            assertEquals(0.3f to 0.9f, PreviewOrientation.mapTapToBuffer(x, y, facing))
        }
    }
}
