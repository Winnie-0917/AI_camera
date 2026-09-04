package com.example.ai_camera.camera

import android.hardware.camera2.params.RggbChannelVector
import kotlin.math.ln
import kotlin.math.pow

/**
 * Manual white-balance temperature support.
 *
 * COLOR_CORRECTION_GAINS operates in *raw sensor* space, not sRGB, and every sensor has its own
 * colour response (green dominates a Bayer array). Absolute gains computed from a black-body model
 * alone therefore render badly - unity-ish gains mean "no correction", which leaves a green cast.
 *
 * So instead of inventing absolute gains, the Kelvin slider shifts *relative to the gains the
 * device's own AWB produced*, which are already calibrated for this sensor. At [ANCHOR_KELVIN] the
 * result matches auto white balance; moving the slider warms or cools it by the ratio the
 * black-body model predicts between the two temperatures.
 */
object WhiteBalance {
    /** Slider position at which the manual result matches the camera's own auto white balance. */
    const val ANCHOR_KELVIN = 5500

    private const val MIN_GAIN = 0.25f
    private const val MAX_GAIN = 8f

    private data class Rgb(val r: Double, val g: Double, val b: Double)

    /**
     * @param baseline the most recent AWB-produced gains, or null if none observed yet.
     */
    fun gainsFor(kelvin: Int, baseline: RggbChannelVector?): RggbChannelVector {
        val anchor = blackBody(ANCHOR_KELVIN)
        val target = blackBody(kelvin)

        // Gains that neutralize a light are proportional to 1/light, so the change relative to the
        // anchor is anchor/target. Normalized against green so overall brightness is unaffected.
        val greenRatio = anchor.g / target.g
        val rMul = ((anchor.r / target.r) / greenRatio).toFloat()
        val bMul = ((anchor.b / target.b) / greenRatio).toFloat()

        val baseR = baseline?.red ?: 1f
        val baseGEven = baseline?.greenEven ?: 1f
        val baseGOdd = baseline?.greenOdd ?: 1f
        val baseB = baseline?.blue ?: 1f

        return RggbChannelVector(
            (baseR * rMul).coerceIn(MIN_GAIN, MAX_GAIN),
            baseGEven,
            baseGOdd,
            (baseB * bMul).coerceIn(MIN_GAIN, MAX_GAIN),
        )
    }

    /** Approximate RGB of a black-body radiator (Tanner Helland's approximation). */
    private fun blackBody(kelvin: Int): Rgb {
        val temp = kelvin.coerceIn(1000, 15000) / 100.0

        val red: Double
        val green: Double
        if (temp <= 66) {
            red = 255.0
            green = (99.4708025861 * ln(temp) - 161.1195681661).coerceIn(1.0, 255.0)
        } else {
            red = (329.698727446 * (temp - 60).pow(-0.1332047592)).coerceIn(1.0, 255.0)
            green = (288.1221695283 * (temp - 60).pow(-0.0755148492)).coerceIn(1.0, 255.0)
        }

        val blue = when {
            temp >= 66 -> 255.0
            temp <= 19 -> 1.0
            else -> (138.5177312231 * ln(temp - 10) - 305.0447927307).coerceIn(1.0, 255.0)
        }

        return Rgb(red, green, blue)
    }
}
