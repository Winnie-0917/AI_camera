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
}
