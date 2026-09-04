package com.example.ai_camera.ui

import android.util.Rational
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

fun formatShutter(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    if (seconds <= 0.0) return "-"
    return if (seconds >= 1.0) {
        String.format(Locale.US, "%.1f\"", seconds)
    } else {
        "1/${(1.0 / seconds).roundToInt()}"
    }
}

fun formatEv(steps: Int, step: Rational): String {
    val ev = steps * step.toFloat()
    return String.format(Locale.US, "%+.1f", ev)
}

fun formatFocus(diopters: Float): String =
    if (diopters <= 0.05f) "∞" else String.format(Locale.US, "%.2fm", 1f / diopters)

fun formatZoom(zoom: Float): String = String.format(Locale.US, "%.1fx", zoom)

fun formatKelvin(kelvin: Int): String = "${kelvin}K"

/** Log-scale slider mapping - linear sliders feel wrong for ISO and shutter speed. */
fun logToValue(t: Float, min: Float, max: Float): Float {
    if (min <= 0f || max <= min) return min
    return min * (max / min).pow(t.coerceIn(0f, 1f))
}

fun valueToLog(value: Float, min: Float, max: Float): Float {
    if (min <= 0f || max <= min) return 0f
    return (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)
}
