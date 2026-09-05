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
        assertEquals(0, PreviewOrientation.rotationFor(BACK))
    }

    // The correction is 180 for every front lens, whether its sensor reports 90 or 270. Deriving
    // it from the sensor angle was the bug: it happens to be right at 270 and wrong at 90.
    @Test
    fun `front preview always needs a half turn`() {
        assertEquals(180, PreviewOrientation.rotationFor(FRONT))
    }

    @Test
    fun `an external lens is treated like a back lens`() {
        assertEquals(0, PreviewOrientation.rotationFor(CameraCharacteristics.LENS_FACING_EXTERNAL))
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

    // Mirror then half turn cancel horizontally and leave a vertical flip, so a tap on a front
    // preview keeps its side of the frame but swaps top for bottom.
    @Test
    fun `a front tap keeps its side and flips vertically`() {
        assertEquals(0.25f to 0.25f, PreviewOrientation.mapTapToBuffer(0.25f, 0.75f, FRONT))
        assertEquals(0.8f to 0.7f, PreviewOrientation.mapTapToBuffer(0.8f, 0.3f, FRONT))
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
