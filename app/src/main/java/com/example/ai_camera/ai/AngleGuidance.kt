package com.example.ai_camera.ai

/** A physical correction the photographer can make right now. */
enum class AngleDirection(val tag: String) {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    UP("up"),
    DOWN("down"),
    TILT_LEFT("tilt_left"),
    TILT_RIGHT("tilt_right"),
    CLOSER("closer"),
    FARTHER("farther");

    companion object {
        fun fromTag(tag: String?): AngleDirection =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: NONE
    }
}

data class AngleAdvice(
    val perfect: Boolean,
    val direction: AngleDirection,
    val hint: String,
)

/**
 * Poll intervals for the live angle guide. Once the framing is right there is nothing to correct,
 * so checks slow down; they speed back up as soon as it drifts. This is purely to cut API calls
 * while the shot is already good.
 */
object AnglePolling {
    const val ACTIVE_MS = 3_000L
    const val SETTLED_MS = 5_000L

    /** First wait after a failure, then doubling up to [MAX_BACKOFF_MS]. */
    const val BACKOFF_START_MS = 15_000L
    const val MAX_BACKOFF_MS = 120_000L

    fun intervalFor(advice: AngleAdvice?): Long =
        if (advice?.perfect == true) SETTLED_MS else ACTIVE_MS

    /**
     * Wait after [consecutiveFailures] failed checks. A rate-limited or offline API must not be
     * polled every 3s - that burns quota and battery for nothing - so failures back off
     * exponentially and recover as soon as one call succeeds.
     */
    fun backoffFor(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return ACTIVE_MS
        val shift = (consecutiveFailures - 1).coerceAtMost(8)
        return (BACKOFF_START_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }
}
