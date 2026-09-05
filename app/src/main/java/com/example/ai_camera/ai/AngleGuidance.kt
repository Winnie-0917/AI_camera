package com.example.ai_camera.ai

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.StringRes
import com.example.ai_camera.R

/**
 * What is wrong with the framing, stated purely as an observation about the image. The model is
 * only asked to describe what it sees - never what the photographer should do - because asking it
 * for the corrective action gets the direction backwards: with a subject left of centre it tends
 * to answer "move right", reasoning about pushing the subject rightwards rather than about moving
 * the camera.
 */
enum class AngleIssue(val tag: String) {
    NONE("none"),
    SUBJECT_LEFT("subject_left"),
    SUBJECT_RIGHT("subject_right"),
    SUBJECT_HIGH("subject_high"),
    SUBJECT_LOW("subject_low"),
    TILTED_CW("tilted_clockwise"),
    TILTED_CCW("tilted_counter_clockwise"),
    TOO_CLOSE("too_close"),
    TOO_FAR("too_far");

    companion object {
        fun fromTag(tag: String?): AngleIssue =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: NONE
    }
}

/**
 * The correction to show the photographer. Rendered as a vector icon rather than an arrow
 * character: glyphs like U+21BA/U+21BB are missing from many system fonts and come out as tofu
 * boxes, which looks like mojibake.
 */
enum class AngleDirection(@StringRes val labelRes: Int) {
    NONE(R.string.ai_angle_hold),
    LEFT(R.string.ai_angle_left),
    RIGHT(R.string.ai_angle_right),
    UP(R.string.ai_angle_up),
    DOWN(R.string.ai_angle_down),
    ROTATE_LEFT(R.string.ai_angle_rotate_left),
    ROTATE_RIGHT(R.string.ai_angle_rotate_right),
    CLOSER(R.string.ai_angle_closer),
    FARTHER(R.string.ai_angle_farther),
}

data class AngleAdvice(
    val perfect: Boolean,
    val issue: AngleIssue,
    /** Short context from the model, in the user's language. May be blank. */
    val note: String,
)

object AngleGuidance {
    /**
     * Turns an image-space observation into the movement to instruct.
     *
     * Two things this gets right that a model asked for the action directly does not:
     *
     * 1. To centre a subject you turn the camera *towards* it - panning left brings content that
     *    sits at the left edge in towards the middle. Same for tilting.
     * 2. The front camera faces the photographer, so its own left is the photographer's right.
     *    Left/right and the sense of rotation are therefore mirrored when phrasing the
     *    instruction; up/down and closer/farther are unaffected by which way the lens points.
     */
    fun directionFor(issue: AngleIssue, lensFacing: Int): AngleDirection {
        val direction = when (issue) {
            AngleIssue.NONE -> AngleDirection.NONE
            AngleIssue.SUBJECT_LEFT -> AngleDirection.LEFT
            AngleIssue.SUBJECT_RIGHT -> AngleDirection.RIGHT
            AngleIssue.SUBJECT_HIGH -> AngleDirection.UP
            AngleIssue.SUBJECT_LOW -> AngleDirection.DOWN
            AngleIssue.TILTED_CW -> AngleDirection.ROTATE_LEFT
            AngleIssue.TILTED_CCW -> AngleDirection.ROTATE_RIGHT
            AngleIssue.TOO_CLOSE -> AngleDirection.FARTHER
            AngleIssue.TOO_FAR -> AngleDirection.CLOSER
        }
        return if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            mirrored(direction)
        } else {
            direction
        }
    }

    private fun mirrored(direction: AngleDirection): AngleDirection = when (direction) {
        AngleDirection.LEFT -> AngleDirection.RIGHT
        AngleDirection.RIGHT -> AngleDirection.LEFT
        AngleDirection.ROTATE_LEFT -> AngleDirection.ROTATE_RIGHT
        AngleDirection.ROTATE_RIGHT -> AngleDirection.ROTATE_LEFT
        else -> direction
    }
}

/**
 * Poll intervals for the live angle guide. Once the framing is right there is nothing to correct,
 * so checks slow down; they speed back up as soon as it drifts. This is purely to cut API calls
 * while the shot is already good.
 */
object AnglePolling {
    const val ACTIVE_MS = 5_000L
    const val SETTLED_MS = 8_000L

    /** First wait after a failure, then doubling up to [MAX_BACKOFF_MS]. */
    const val BACKOFF_START_MS = 15_000L
    const val MAX_BACKOFF_MS = 120_000L

    fun intervalFor(advice: AngleAdvice?): Long =
        if (advice?.perfect == true) SETTLED_MS else ACTIVE_MS

    /**
     * Wait after [consecutiveFailures] failed checks. A rate-limited or offline API must not be
     * polled on the normal cadence - that burns quota and battery for nothing - so failures back off
     * exponentially and recover as soon as one call succeeds.
     */
    fun backoffFor(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return ACTIVE_MS
        val shift = (consecutiveFailures - 1).coerceAtMost(8)
        return (BACKOFF_START_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }
}
