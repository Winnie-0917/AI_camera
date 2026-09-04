package com.example.ai_camera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.annotation.StringRes
import com.example.ai_camera.R

enum class ExposureMode { AUTO, MANUAL }
enum class FocusMode { AUTO, MANUAL }
enum class FlashMode { OFF, AUTO, ON, TORCH }
enum class TimerOption(val seconds: Int) { OFF(0), S2(2), S5(5), S10(10) }
enum class AspectRatioOption(val ratio: Float, @StringRes val labelRes: Int) {
    FULL(0f, R.string.aspect_full),
    R4_3(4f / 3f, R.string.aspect_4_3),
    R16_9(16f / 9f, R.string.aspect_16_9),
    R1_1(1f, R.string.aspect_1_1),
}

/** Auto white balance presets, mapped to Camera2's CONTROL_AWB_MODE constants. */
enum class WbPreset(val awbMode: Int, @StringRes val labelRes: Int) {
    AUTO(CameraMetadata.CONTROL_AWB_MODE_AUTO, R.string.wb_auto),
    INCANDESCENT(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, R.string.wb_incandescent),
    FLUORESCENT(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, R.string.wb_fluorescent),
    DAYLIGHT(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, R.string.wb_daylight),
    CLOUDY(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, R.string.wb_cloudy),
    SHADE(CameraMetadata.CONTROL_AWB_MODE_SHADE, R.string.wb_shade),
    TWILIGHT(CameraMetadata.CONTROL_AWB_MODE_TWILIGHT, R.string.wb_twilight),
    KELVIN(-1, R.string.wb_kelvin),
}

/**
 * The single source of truth for every adjustable parameter. Immutable + copy-on-change so
 * it can be diffed cheaply and safely shared between the Compose UI and [CameraController].
 */
data class CaptureSettings(
    val lensFacing: Int = CameraCharacteristics.LENS_FACING_BACK,
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int = 100,
    val shutterSpeedNanos: Long = 1_000_000_000L / 60,
    val evCompensationSteps: Int = 0,
    val focusMode: FocusMode = FocusMode.AUTO,
    val focusDistanceDiopters: Float = 0f,
    val wbPreset: WbPreset = WbPreset.AUTO,
    val wbKelvin: Int = 5500,
    val flashMode: FlashMode = FlashMode.OFF,
    val zoomRatio: Float = 1f,
    val timer: TimerOption = TimerOption.OFF,
    val aspectRatio: AspectRatioOption = AspectRatioOption.R4_3,
    val saveRaw: Boolean = false,
    val jpegQuality: Int = 95,
    val gridEnabled: Boolean = false,
    val histogramEnabled: Boolean = false,
    val levelEnabled: Boolean = false,
) {
    companion object {
        /** Sensible manual defaults once real ranges are known, clamped into range. */
        fun defaultsFor(specs: CameraSpecs, previous: CaptureSettings): CaptureSettings {
            val iso = specs.isoRange?.clamp(previous.iso) ?: previous.iso
            val shutter = specs.exposureTimeRangeNanos?.clamp(previous.shutterSpeedNanos)
                ?: previous.shutterSpeedNanos
            val focus = previous.focusDistanceDiopters.coerceIn(0f, specs.minFocusDistanceDiopters)
            val ev = specs.aeCompensationRange.clamp(previous.evCompensationSteps)
            return previous.copy(
                lensFacing = specs.lensFacing,
                iso = iso,
                shutterSpeedNanos = shutter,
                focusDistanceDiopters = focus,
                evCompensationSteps = ev,
                zoomRatio = previous.zoomRatio.coerceIn(1f, specs.maxDigitalZoom),
            )
        }
    }
}

private fun android.util.Range<Int>.clamp(value: Int): Int =
    value.coerceIn(lower, upper)

private fun android.util.Range<Long>.clamp(value: Long): Long =
    value.coerceIn(lower, upper)
