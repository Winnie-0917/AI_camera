package com.example.ai_camera.camera

import android.hardware.camera2.CameraCharacteristics

/**
 * Rotation baked into a captured image, as `CaptureRequest.JPEG_ORIENTATION`.
 *
 * This is Camera2's documented formula. It is easy to reach for `(360 - sensorOrientation)` on a
 * front lens, but that is the Camera1 *preview display* formula, which carries a correction for
 * the mirrored viewfinder. A capture is not mirrored, so borrowing it turns every selfie upside
 * down: a front sensor reporting 270 gets tagged 90.
 *
 * The lens only changes the sign of the device's own rotation, which is why a portrait-locked
 * activity ends up tagging both lenses with the sensor angle itself.
 */
object JpegOrientation {
    fun forCapture(
        sensorOrientation: Int,
        lensFacing: Int,
        deviceRotationDegrees: Int = 0,
    ): Int {
        val sign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) -1 else 1
        return (sensorOrientation + sign * deviceRotationDegrees + 360) % 360
    }
}
