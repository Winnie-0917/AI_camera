package com.example.ai_camera

import android.hardware.camera2.CameraCharacteristics
import com.example.ai_camera.camera.JpegOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

private const val BACK = CameraCharacteristics.LENS_FACING_BACK
private const val FRONT = CameraCharacteristics.LENS_FACING_FRONT

class JpegOrientationTest {

    // The regression this locks down: a front sensor reporting 270 was tagged 90, exactly half a
    // turn out, so every selfie was saved upside down.
    @Test
    fun `a front capture is tagged with the sensor angle, not its complement`() {
        assertEquals(270, JpegOrientation.forCapture(sensorOrientation = 270, lensFacing = FRONT))
        assertEquals(90, JpegOrientation.forCapture(sensorOrientation = 90, lensFacing = FRONT))
    }

    @Test
    fun `a back capture is tagged with the sensor angle`() {
        assertEquals(90, JpegOrientation.forCapture(sensorOrientation = 90, lensFacing = BACK))
        assertEquals(270, JpegOrientation.forCapture(sensorOrientation = 270, lensFacing = BACK))
    }

    // With the activity locked to portrait the device never rotates, so both lenses agree.
    @Test
    fun `both lenses agree while the device does not rotate`() {
        listOf(0, 90, 180, 270).forEach { sensor ->
            assertEquals(
                JpegOrientation.forCapture(sensor, BACK),
                JpegOrientation.forCapture(sensor, FRONT),
            )
        }
    }

    // The lens flips the sign of the device's rotation - the one place facing actually matters.
    @Test
    fun `device rotation is applied in opposite directions per lens`() {
        assertEquals(180, JpegOrientation.forCapture(90, BACK, deviceRotationDegrees = 90))
        assertEquals(0, JpegOrientation.forCapture(90, FRONT, deviceRotationDegrees = 90))
    }

    @Test
    fun `the result always stays within a single turn`() {
        listOf(0, 90, 180, 270).forEach { sensor ->
            listOf(0, 90, 180, 270).forEach { device ->
                listOf(BACK, FRONT).forEach { facing ->
                    val result = JpegOrientation.forCapture(sensor, facing, device)
                    assert(result in 0..359) { "got $result" }
                    assertEquals(0, result % 90)
                }
            }
        }
    }
}
