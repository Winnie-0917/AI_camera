package com.example.ai_camera.ai

import com.example.ai_camera.camera.AspectRatioOption
import com.example.ai_camera.camera.CameraSpecs
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.ExposureMode
import com.example.ai_camera.camera.FlashMode
import com.example.ai_camera.camera.FocusMode
import com.example.ai_camera.camera.WbPreset
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A set of camera parameters the assistant proposes for a requested look. Every field is optional:
 * the model only fills in what it wants to change, and anything the current camera cannot do is
 * dropped at apply time rather than being forced onto the request.
 */
data class StyleSuggestion(
    val label: String,
    val exposureMode: String? = null,
    val iso: Int? = null,
    val shutterSeconds: Double? = null,
    val evCompensation: Double? = null,
    val whiteBalance: String? = null,
    val kelvin: Int? = null,
    val focusMode: String? = null,
    val focusDistanceMeters: Double? = null,
    val zoom: Double? = null,
    val flash: String? = null,
    val aspectRatio: String? = null,
    val jpegQuality: Int? = null,
    val saveRaw: Boolean? = null,
) {
    /** Result of applying: the new settings plus what was actually changed or dropped. */
    data class Applied(
        val settings: CaptureSettings,
        val applied: List<String>,
        val skipped: List<String>,
    )

    /**
     * Applies whatever this camera supports, clamped to its real ranges. Unsupported requests are
     * reported in [Applied.skipped] instead of being silently ignored.
     */
    fun applyTo(current: CaptureSettings, specs: CameraSpecs): Applied {
        var next = current
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // ISO and shutter imply manual exposure - Camera2 needs both once AE is off.
        val wantsManualExposure = exposureMode.equalsIgnoreCase("manual") ||
            iso != null || shutterSeconds != null
        if (wantsManualExposure) {
            if (specs.supportsManualExposure) {
                next = next.copy(exposureMode = ExposureMode.MANUAL)
                iso?.let {
                    val value = specs.isoRange?.let { r -> it.coerceIn(r.lower, r.upper) } ?: it
                    next = next.copy(iso = value)
                    applied += "ISO $value"
                }
                shutterSeconds?.let {
                    val nanos = (it * 1_000_000_000.0).roundToLong()
                    val value = specs.exposureTimeRangeNanos
                        ?.let { r -> nanos.coerceIn(r.lower, r.upper) } ?: nanos
                    next = next.copy(shutterSpeedNanos = value)
                    applied += "Shutter ${formatSeconds(value / 1_000_000_000.0)}"
                }
            } else {
                skipped += "manual exposure"
            }
        } else if (exposureMode.equalsIgnoreCase("auto")) {
            next = next.copy(exposureMode = ExposureMode.AUTO)
            applied += "Auto exposure"
        }

        evCompensation?.let { ev ->
            // EV compensation only has meaning while auto exposure is running.
            if (next.exposureMode == ExposureMode.MANUAL) {
                skipped += "exposure compensation (manual mode)"
            } else {
                val step = specs.aeCompensationStep.toDouble()
                val steps = if (step > 0) (ev / step).roundToInt() else 0
                val clamped = steps.coerceIn(
                    specs.aeCompensationRange.lower,
                    specs.aeCompensationRange.upper,
                )
                next = next.copy(evCompensationSteps = clamped)
                applied += "EV ${"%+.1f".format(clamped * step)}"
            }
        }

        whiteBalance?.let { wb ->
            if (wb.equals("kelvin", ignoreCase = true)) {
                if (specs.supportsManualPostProcessing) {
                    val k = (kelvin ?: 5500).coerceIn(2000, 10000)
                    next = next.copy(wbPreset = WbPreset.KELVIN, wbKelvin = k)
                    applied += "${k}K"
                } else {
                    skipped += "manual white balance"
                }
            } else {
                val preset = WbPreset.entries.firstOrNull {
                    it != WbPreset.KELVIN && it.name.equals(wb.replace(" ", "_"), ignoreCase = true)
                }
                if (preset != null && preset.awbMode in specs.awbAvailableModes) {
                    next = next.copy(wbPreset = preset)
                    applied += "WB ${preset.name.lowercase()}"
                } else {
                    skipped += "white balance \"$wb\""
                }
            }
        }

        if (focusMode.equalsIgnoreCase("manual") || focusDistanceMeters != null) {
            if (specs.supportsManualFocus) {
                // Camera2 focus distance is in diopters (1/metres); 0 means infinity.
                val meters = focusDistanceMeters
                val diopters = when {
                    meters == null -> next.focusDistanceDiopters
                    meters <= 0.0 -> 0f
                    else -> (1.0 / meters).toFloat()
                }.coerceIn(0f, specs.minFocusDistanceDiopters)
                next = next.copy(focusMode = FocusMode.MANUAL, focusDistanceDiopters = diopters)
                applied += "Manual focus"
            } else {
                skipped += "manual focus"
            }
        } else if (focusMode.equalsIgnoreCase("auto")) {
            next = next.copy(focusMode = FocusMode.AUTO)
            applied += "Autofocus"
        }

        zoom?.let {
            if (specs.maxDigitalZoom > 1f) {
                val value = it.toFloat().coerceIn(1f, specs.maxDigitalZoom)
                next = next.copy(zoomRatio = value)
                applied += "Zoom ${"%.1fx".format(value)}"
            } else {
                skipped += "zoom"
            }
        }

        flash?.let { mode ->
            val parsed = FlashMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
            if (parsed == null) {
                skipped += "flash \"$mode\""
            } else if (!specs.hasFlash && parsed != FlashMode.OFF) {
                skipped += "flash (no flash unit)"
            } else {
                next = next.copy(flashMode = parsed)
                applied += "Flash ${parsed.name.lowercase()}"
            }
        }

        aspectRatio?.let { ratio ->
            val parsed = when (ratio.lowercase().replace(" ", "")) {
                "full" -> AspectRatioOption.FULL
                "4:3", "4_3" -> AspectRatioOption.R4_3
                "16:9", "16_9" -> AspectRatioOption.R16_9
                "1:1", "1_1" -> AspectRatioOption.R1_1
                else -> null
            }
            if (parsed != null) {
                next = next.copy(aspectRatio = parsed)
                applied += ratio
            } else {
                skipped += "aspect ratio \"$ratio\""
            }
        }

        jpegQuality?.let {
            val value = it.coerceIn(50, 100)
            next = next.copy(jpegQuality = value)
            applied += "Quality $value"
        }

        saveRaw?.let {
            if (it && !specs.supportsRaw) {
                skipped += "RAW"
            } else {
                next = next.copy(saveRaw = it)
                applied += if (it) "RAW on" else "RAW off"
            }
        }

        return Applied(next, applied, skipped)
    }

    /** Human-readable summary of the proposal, for the suggestion card. */
    fun describe(): List<String> = buildList {
        exposureMode?.let { add(if (it.equals("manual", true)) "Manual exposure" else "Auto exposure") }
        iso?.let { add("ISO $it") }
        shutterSeconds?.let { add("Shutter ${formatSeconds(it)}") }
        evCompensation?.let { add("EV ${"%+.1f".format(it)}") }
        whiteBalance?.let { add(if (it.equals("kelvin", true)) "${kelvin ?: 5500}K" else "WB $it") }
        focusDistanceMeters?.let {
            add(if (it <= 0) "Focus ∞" else "Focus ${"%.2f".format(it)}m")
        } ?: focusMode?.let { add(if (it.equals("manual", true)) "Manual focus" else "Autofocus") }
        zoom?.let { add("Zoom ${"%.1fx".format(it)}") }
        flash?.let { add("Flash $it") }
        aspectRatio?.let { add(it) }
        jpegQuality?.let { add("Quality $it") }
        saveRaw?.let { add(if (it) "RAW on" else "RAW off") }
    }

    companion object {
        fun fromJson(json: JSONObject?): StyleSuggestion? {
            if (json == null) return null
            val label = json.optString("label").ifBlank { return null }
            return StyleSuggestion(
                label = label,
                exposureMode = json.optStringOrNull("exposureMode"),
                iso = json.optIntOrNull("iso"),
                shutterSeconds = json.optDoubleOrNull("shutterSeconds"),
                evCompensation = json.optDoubleOrNull("evCompensation"),
                whiteBalance = json.optStringOrNull("whiteBalance"),
                kelvin = json.optIntOrNull("kelvin"),
                focusMode = json.optStringOrNull("focusMode"),
                focusDistanceMeters = json.optDoubleOrNull("focusDistanceMeters"),
                zoom = json.optDoubleOrNull("zoom"),
                flash = json.optStringOrNull("flash"),
                aspectRatio = json.optStringOrNull("aspectRatio"),
                jpegQuality = json.optIntOrNull("jpegQuality"),
                saveRaw = if (json.has("saveRaw")) json.optBoolean("saveRaw") else null,
            ).takeIf { it.describe().isNotEmpty() }
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (has(key) && !isNull(key)) optInt(key) else null

        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

        internal fun formatSeconds(seconds: Double): String = when {
            seconds >= 1.0 -> "${"%.1f".format(seconds)}\""
            seconds > 0 -> "1/${(1.0 / seconds).roundToInt()}"
            else -> "-"
        }
    }
}

private fun String?.equalsIgnoreCase(other: String) = this?.equals(other, true) == true
