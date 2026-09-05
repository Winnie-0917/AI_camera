package com.example.ai_camera.camera

import android.hardware.camera2.CameraCharacteristics

/**
 * Extra rotation the viewfinder needs on top of what the buffer producer already applies.
 *
 * The producer orients preview buffers by `sensorOrientation`, which is the right convention for a
 * back lens. A front lens needs `(360 - sensorOrientation)` instead, because it faces the user.
 * Those two differ by 180 for every value the sensor can report:
 *
 *     sensorOrientation  90 -> back needs  90, front needs 270  (180 apart)
 *     sensorOrientation 270 -> back needs 270, front needs  90  (180 apart)
 *
 * So the correction depends on which way the lens points, not on the sensor angle. Deriving it
 * from the sensor angle instead happens to give the right answer on a device whose front sensor
 * reports 270 and the wrong one where it reports 90, leaving that preview upside down.
 */
object PreviewOrientation {
    fun rotationFor(lensFacing: Int): Int =
        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 180 else 0
}
