package com.example.ai_camera.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Rational
import android.util.Size

/**
 * Snapshot of everything the UI needs to know about a physical camera's manual-control
 * capabilities, resolved once from [CameraCharacteristics] when the camera is opened.
 * Devices vary wildly here (many budget phones lack MANUAL_SENSOR entirely), so every
 * screen that offers a manual control must check the relevant flag/range first.
 */
data class CameraSpecs(
    val cameraId: String,
    val lensFacing: Int,
    val sensorOrientation: Int,
    val activeArraySize: Rect,
    val hardwareLevel: Int,
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRaw: Boolean,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNanos: Range<Long>?,
    val minFocusDistanceDiopters: Float,
    val afAvailableModes: List<Int>,
    val awbAvailableModes: List<Int>,
    val aeCompensationRange: Range<Int>,
    val aeCompensationStep: Rational,
    val maxDigitalZoom: Float,
    val hasFlash: Boolean,
    val jpegSizes: List<Size>,
    val rawSizes: List<Size>,
    val previewSizes: List<Size>,
    val yuvSizes: List<Size>,
) {
    val supportsManualFocus: Boolean get() = minFocusDistanceDiopters > 0f
    val supportsManualIso: Boolean get() = supportsManualSensor && isoRange != null
    val supportsManualShutter: Boolean get() = supportsManualSensor && exposureTimeRangeNanos != null
    val supportsManualExposure: Boolean get() = supportsManualIso && supportsManualShutter

    // "Adjustable" is stricter than "supported": the control must exist AND offer more than one
    // value, otherwise the UI would show a dead slider the user cannot move.
    // ISO and shutter are one unit here because Camera2 requires both to be driven manually once
    // AE is switched off - neither can be adjusted alone.
    val isoAdjustable: Boolean
        get() = supportsManualExposure && isoRange != null && isoRange.upper > isoRange.lower

    val shutterAdjustable: Boolean
        get() = supportsManualExposure && exposureTimeRangeNanos != null &&
            exposureTimeRangeNanos.upper > exposureTimeRangeNanos.lower

    val evAdjustable: Boolean get() = aeCompensationRange.upper > aeCompensationRange.lower

    val focusAdjustable: Boolean get() = supportsManualFocus

    val zoomAdjustable: Boolean get() = maxDigitalZoom > 1f

    /**
     * Whether a 4th YUV analysis stream (used for the live histogram) can be configured alongside
     * preview + JPEG + RAW. Camera2 only guarantees that combination on LEVEL_3 hardware, so on a
     * RAW-capable FULL device the analysis stream is dropped rather than risking a session that
     * fails to configure at all.
     */
    val supportsAnalysisStream: Boolean
        get() = yuvSizes.isNotEmpty() &&
            (!supportsRaw || hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)

    companion object {
        fun from(manager: CameraManager, cameraId: String): CameraSpecs {
            val c = manager.getCameraCharacteristics(cameraId)

            val capabilities =
                c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val supportsManualSensor =
                capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val supportsManualPostProcessing =
                capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
            val supportsRaw =
                capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
            val rawSizes = if (supportsRaw) {
                map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
            } else {
                emptyList()
            }

            val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                ?.toList().orEmpty()
            val awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                ?.toList().orEmpty()

            return CameraSpecs(
                cameraId = cameraId,
                lensFacing = c.get(CameraCharacteristics.LENS_FACING)
                    ?: CameraCharacteristics.LENS_FACING_BACK,
                sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                activeArraySize = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: Rect(0, 0, 4000, 3000),
                hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                supportsManualSensor = supportsManualSensor,
                supportsManualPostProcessing = supportsManualPostProcessing,
                supportsRaw = supportsRaw,
                isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureTimeRangeNanos = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocusDistanceDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    ?: 0f,
                afAvailableModes = afModes,
                awbAvailableModes = awbModes,
                aeCompensationRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                    ?: Range(0, 0),
                aeCompensationStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                    ?: Rational(1, 1),
                maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    ?: 1f,
                hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false,
                jpegSizes = jpegSizes,
                rawSizes = rawSizes,
                previewSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                    ?.toList().orEmpty(),
                yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty(),
            )
        }

        fun findCameraId(manager: CameraManager, lensFacing: Int): String? {
            return manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == lensFacing
            }
        }

    }

    /**
     * Picks a preview (SurfaceTexture) size matching the capture aspect ratio, capped at
     * [maxArea] pixels for smooth rendering, falling back to the largest available size.
     */
    fun chooseOptimalPreviewSize(maxArea: Int = 1920 * 1080): Size {
        if (previewSizes.isEmpty()) return Size(1280, 720)
        val targetAspect = jpegSizes.maxByOrNull { it.width.toLong() * it.height }
            ?.let { it.width.toFloat() / it.height }
            ?: (4f / 3f)

        val matchingAspect = previewSizes.filter {
            kotlin.math.abs((it.width.toFloat() / it.height) - targetAspect) < 0.05f
        }
        val pool = matchingAspect.ifEmpty { previewSizes }

        return pool.filter { it.width.toLong() * it.height <= maxArea }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: pool.minByOrNull { it.width.toLong() * it.height }
            ?: previewSizes.first()
    }
}
