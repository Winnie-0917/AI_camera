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

    // Verified against a face in shot: buffers arrive upright, so rotating here inverted the
    // preview. Comparing preview against photo had hidden this - both were wrong together.
    @Test
    fun `no lens needs the viewfinder to rotate`() {
        assertEquals(0, PreviewOrientation.displayRotationFor(FRONT))
        assertEquals(0, PreviewOrientation.displayRotationFor(BACK))
    }

    @Test
    fun `an external lens is treated like a back lens`() {
        assertEquals(0, PreviewOrientation.displayRotationFor(CameraCharacteristics.LENS_FACING_EXTERNAL))
        assertEquals(false, PreviewOrientation.isMirrored(CameraCharacteristics.LENS_FACING_EXTERNAL))
    }

    // The angle guide gets the buffer untouched; only the display is mirrored.
    @Test
    fun `the analysis frame is never transformed`() {
        assertEquals(false, PreviewOrientation.analysisFlipsVertically(FRONT))
        assertEquals(false, PreviewOrientation.analysisFlipsVertically(BACK))
    }

    @Test
    fun `only the front preview is mirrored`() {
        assertEquals(true, PreviewOrientation.isMirrored(FRONT))
        assertEquals(false, PreviewOrientation.isMirrored(BACK))
    }

    @Test
    fun `a back tap maps straight through`() {
        assertEquals(0.25f to 0.75f, PreviewOrientation.mapTapToBuffer(0.25f, 0.75f, BACK))
    }

    // The front preview is mirrored, so a tap's horizontal position has to be flipped back while
    // its vertical position passes through.
    @Test
    fun `a front tap is flipped horizontally only`() {
        assertEquals(0.75f to 0.75f, PreviewOrientation.mapTapToBuffer(0.25f, 0.75f, FRONT))

        // Compared with a tolerance: 1f - 0.8f is not exactly 0.2f in binary floating point.
        val (x, y) = PreviewOrientation.mapTapToBuffer(0.8f, 0.3f, FRONT)
        assertEquals(0.2f, x, 1e-6f)
        assertEquals(0.3f, y, 1e-6f)
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
