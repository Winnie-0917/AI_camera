package com.example.ai_camera.camera

import android.graphics.ColorMatrix
import androidx.annotation.StringRes
import com.example.ai_camera.R

/**
 * The look applied on top of whatever the sensor gives us - the equivalent of a film simulation.
 *
 * Deliberately a colour grade rather than a Camera2 EFFECT_MODE: the hardware effects are a fixed,
 * garish set (sepia, negative, posterise) that vary between devices and cannot be dialled back,
 * while a colour matrix is the same on every phone, previews exactly as it will be saved, and
 * takes a strength.
 *
 * Applied to the viewfinder and to the JPEG. Never to the DNG - the point of raw is that it is
 * ungraded.
 */
enum class PhotoStyle(
    @StringRes val labelRes: Int,
    private val saturation: Float,
    private val contrast: Float,
    /** Added to every channel, in 0..255 units: raises the black point for a faded look. */
    private val lift: Float,
    private val redGain: Float,
    private val greenGain: Float,
    private val blueGain: Float,
) {
    NATURAL(R.string.style_natural, 1f, 1f, 0f, 1f, 1f, 1f),
    SOFT(R.string.style_soft, 0.95f, 0.86f, 20f, 1.02f, 1.00f, 1.00f),
    CREAM(R.string.style_cream, 0.85f, 0.92f, 14f, 1.07f, 1.01f, 0.92f),
    FRESH(R.string.style_fresh, 1.14f, 1.06f, 4f, 0.97f, 1.02f, 1.05f),
    RETRO(R.string.style_retro, 0.62f, 0.90f, 18f, 1.10f, 1.00f, 0.86f),
    MONO(R.string.style_mono, 0f, 1.10f, 0f, 1f, 1f, 1f),
    ;

    /** The full-strength grade, computed once. */
    private val target: FloatArray by lazy {
        val saturated = ColorMatrix().apply { setSaturation(saturation) }
        val offset = 128f * (1f - contrast) + lift
        ColorMatrix(
            floatArrayOf(
                contrast * redGain, 0f, 0f, 0f, offset,
                0f, contrast * greenGain, 0f, 0f, offset,
                0f, 0f, contrast * blueGain, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )
        ).apply {
            // preConcat runs the saturation pass first, then the tone curve and channel gains.
            preConcat(saturated)
        }.array
    }

    /**
     * @param strength 0..100, as shown on the slider. Interpolated against identity so half
     *   strength really is half the effect rather than a second, differently-tuned grade.
     */
    fun matrixFor(strength: Int): FloatArray {
        if (this == NATURAL) return IDENTITY.copyOf()
        val t = (strength.coerceIn(0, 100)) / 100f
        return FloatArray(20) { i -> IDENTITY[i] * (1f - t) + target[i] * t }
    }

    /** True when the grade would leave the image untouched, so the work can be skipped entirely. */
    fun isNoOp(strength: Int): Boolean = this == NATURAL || strength <= 0

    companion object {
        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
