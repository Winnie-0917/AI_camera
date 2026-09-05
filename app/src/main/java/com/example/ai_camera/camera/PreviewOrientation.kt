package com.example.ai_camera.camera

import android.hardware.camera2.CameraCharacteristics

/**
 * How a preview frame is transformed for display and for analysis.
 *
 * Measured on hardware with a face in shot, which is the only unambiguous reference: a room scene
 * has no inherent up, and comparing the preview against a captured photo proves nothing when both
 * are wrong the same way - that mistake is what kept earlier attempts here looking correct.
 *
 * The result: preview buffers already arrive upright on both lenses, so no rotation is applied.
 * The capture path is separate and is handled by [JpegOrientation].
 */
object PreviewOrientation {
    /** Buffers arrive upright, so the viewfinder adds no rotation of its own. */
    fun displayRotationFor(lensFacing: Int): Int = 0

    /**
     * Front previews are shown mirrored, the selfie convention: people framing themselves expect
     * a mirror. Only the display is mirrored - the saved photo keeps the true image.
     */
    fun isMirrored(lensFacing: Int): Boolean =
        lensFacing == CameraCharacteristics.LENS_FACING_FRONT

    /**
     * The angle guide is fed the unmirrored buffer as it arrives: its left/right advice is worked
     * out in [AngleGuidance] against an unmirrored frame, and handing it the mirrored preview
     * would invert every horizontal instruction.
     */
    fun analysisFlipsVertically(lensFacing: Int): Boolean = false

    /**
     * Maps a tap in displayed coordinates (0..1) back to the buffer, undoing the mirror. Without
     * it a tap on a front preview focuses the opposite side of the frame.
     */
    fun mapTapToBuffer(nx: Float, ny: Float, lensFacing: Int): Pair<Float, Float> =
        if (isMirrored(lensFacing)) (1f - nx) to ny else nx to ny
}
