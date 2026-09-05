package com.example.ai_camera.camera

import android.hardware.camera2.CameraCharacteristics

/**
 * How a preview frame has to be transformed for display and for analysis.
 *
 * These values were measured on real hardware rather than derived, because the earlier attempts to
 * reason them out from `sensorOrientation` were wrong twice. On the tested devices (front sensor
 * reporting 270, back 90) the back buffer arrives ready to show, and the front buffer arrives
 * *vertically flipped* - not rotated. That single fact explains both rules below.
 */
object PreviewOrientation {
    /**
     * Rotation for the viewfinder. A half turn undoes the front buffer's vertical flip and leaves
     * a horizontal flip behind, which is exactly the mirrored, selfie-style view people expect
     * when framing themselves. Adding a separate mirror step on top cancels that and yields an
     * unmirrored preview, which is what it did before this was measured.
     */
    fun displayRotationFor(lensFacing: Int): Int =
        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 180 else 0

    /**
     * The angle guide must see the true, unmirrored view: its left/right advice is worked out in
     * [AngleGuidance] assuming an unmirrored frame, and feeding it the mirrored preview would
     * invert every horizontal instruction. Undoing the front buffer's vertical flip on its own
     * gives that, without the mirror the display keeps.
     */
    fun analysisFlipsVertically(lensFacing: Int): Boolean =
        lensFacing == CameraCharacteristics.LENS_FACING_FRONT

    /**
     * Maps a tap in displayed coordinates (0..1) back to the buffer, undoing the display rotation.
     * Without it a tap on a front preview focuses the opposite corner of the frame.
     */
    fun mapTapToBuffer(nx: Float, ny: Float, lensFacing: Int): Pair<Float, Float> =
        if (displayRotationFor(lensFacing) == 180) (1f - nx) to (1f - ny) else nx to ny
}
