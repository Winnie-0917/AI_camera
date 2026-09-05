package com.example.ai_camera

import android.hardware.camera2.CameraCharacteristics
import com.example.ai_camera.ai.AngleDirection
import com.example.ai_camera.ai.AngleGuidance
import com.example.ai_camera.ai.AngleIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val BACK = CameraCharacteristics.LENS_FACING_BACK
private const val FRONT = CameraCharacteristics.LENS_FACING_FRONT

class AngleGuidanceTest {

    // Centring a subject means turning the camera towards it, not away: panning left brings
    // content at the left edge in towards the middle.
    @Test
    fun `back camera turns towards an off-centre subject`() {
        assertEquals(AngleDirection.LEFT, AngleGuidance.directionFor(AngleIssue.SUBJECT_LEFT, BACK))
        assertEquals(AngleDirection.RIGHT, AngleGuidance.directionFor(AngleIssue.SUBJECT_RIGHT, BACK))
    }

    @Test
    fun `back camera tilts towards a subject that is too high or low`() {
        assertEquals(AngleDirection.UP, AngleGuidance.directionFor(AngleIssue.SUBJECT_HIGH, BACK))
        assertEquals(AngleDirection.DOWN, AngleGuidance.directionFor(AngleIssue.SUBJECT_LOW, BACK))
    }

    // The front lens faces the photographer, so its left is their right.
    @Test
    fun `front camera mirrors left and right`() {
        assertEquals(AngleDirection.RIGHT, AngleGuidance.directionFor(AngleIssue.SUBJECT_LEFT, FRONT))
        assertEquals(AngleDirection.LEFT, AngleGuidance.directionFor(AngleIssue.SUBJECT_RIGHT, FRONT))
    }

    @Test
    fun `front camera mirrors the sense of rotation`() {
        assertEquals(AngleDirection.ROTATE_RIGHT, AngleGuidance.directionFor(AngleIssue.TILTED_CW, FRONT))
        assertEquals(AngleDirection.ROTATE_LEFT, AngleGuidance.directionFor(AngleIssue.TILTED_CCW, FRONT))
    }

    @Test
    fun `front and back disagree on horizontal direction`() {
        assertNotEquals(
            AngleGuidance.directionFor(AngleIssue.SUBJECT_LEFT, BACK),
            AngleGuidance.directionFor(AngleIssue.SUBJECT_LEFT, FRONT),
        )
    }

    // Which way the lens points does not flip vertically or along the depth axis.
    @Test
    fun `vertical and distance are the same on both lenses`() {
        listOf(AngleIssue.SUBJECT_HIGH, AngleIssue.SUBJECT_LOW, AngleIssue.TOO_CLOSE, AngleIssue.TOO_FAR)
            .forEach { issue ->
                assertEquals(
                    "$issue should not depend on lens facing",
                    AngleGuidance.directionFor(issue, BACK),
                    AngleGuidance.directionFor(issue, FRONT),
                )
            }
    }

    @Test
    fun `a clockwise tilt is corrected by rotating back anti-clockwise`() {
        assertEquals(AngleDirection.ROTATE_LEFT, AngleGuidance.directionFor(AngleIssue.TILTED_CW, BACK))
        assertEquals(AngleDirection.ROTATE_RIGHT, AngleGuidance.directionFor(AngleIssue.TILTED_CCW, BACK))
    }

    @Test
    fun `too close means step back`() {
        assertEquals(AngleDirection.FARTHER, AngleGuidance.directionFor(AngleIssue.TOO_CLOSE, BACK))
        assertEquals(AngleDirection.CLOSER, AngleGuidance.directionFor(AngleIssue.TOO_FAR, BACK))
    }

    @Test
    fun `good framing asks for no movement`() {
        assertEquals(AngleDirection.NONE, AngleGuidance.directionFor(AngleIssue.NONE, BACK))
        assertEquals(AngleDirection.NONE, AngleGuidance.directionFor(AngleIssue.NONE, FRONT))
    }
}
