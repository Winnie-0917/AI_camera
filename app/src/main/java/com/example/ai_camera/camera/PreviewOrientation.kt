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

    /**
     * Front previews are shown mirrored, the selfie convention: people framing themselves expect
     * a mirror, and moving left should move their reflection left. Only the preview is mirrored -
     * the saved photo keeps the true, unmirrored image, and the frames sent to the angle guide
     * stay unmirrored too so its left/right reasoning is unaffected.
     */
    fun isMirrored(lensFacing: Int): Boolean =
        lensFacing == CameraCharacteristics.LENS_FACING_FRONT

    /**
     * Maps a tap in displayed coordinates (0..1) back to the buffer, undoing the mirror and
     * rotation the viewfinder applies. Without it a tap on a front preview focuses the opposite
     * corner of the frame.
     */
    fun mapTapToBuffer(nx: Float, ny: Float, lensFacing: Int): Pair<Float, Float> {
        var x = nx
        var y = ny
        if (isMirrored(lensFacing)) x = 1f - x
        if (rotationFor(lensFacing) == 180) {
            x = 1f - x
            y = 1f - y
        }
        return x to y
    }
}
