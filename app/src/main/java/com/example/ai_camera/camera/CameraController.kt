package com.example.ai_camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

data class CapturedPhoto(val jpegUri: Uri, val rawUri: Uri?, val rawError: String? = null)

data class LiveReadout(val iso: Int?, val exposureNanos: Long?, val afState: Int?)

/**
 * Thin, explicit wrapper around android.hardware.camera2. This intentionally bypasses CameraX:
 * full manual ISO/shutter/focus/white-balance control needs direct CaptureRequest access, which
 * CameraX's Camera2Interop layer only partially and unreliably exposes.
 */
class CameraController(private val appContext: Context) {
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null

    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var histogramReader: ImageReader? = null

    private var pendingJpegDeferred: CompletableDeferred<ByteArray>? = null
    private var pendingRawImageDeferred: CompletableDeferred<Image>? = null
    private var pendingResultDeferred: CompletableDeferred<TotalCaptureResult>? = null

    private var lastAppliedSettings: CaptureSettings? = null
    private var histogramFrameCounter = 0
    private var histogramWanted = false

    // Latest colour correction the HAL's own AWB produced. Manual Kelvin is applied relative to
    // these, because they are already calibrated for this sensor.
    private var awbGains: RggbChannelVector? = null
    private var awbTransform: ColorSpaceTransform? = null

    var specs: CameraSpecs? = null
        private set

    private val _liveReadout = MutableStateFlow(LiveReadout(null, null, null))
    val liveReadout: StateFlow<LiveReadout> = _liveReadout.asStateFlow()

    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram.asStateFlow()

    fun startBackgroundThread() {
        if (backgroundThread != null) return
        val thread = HandlerThread("CameraBackground").apply { start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: InterruptedException) {
        }
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    suspend fun open(lensFacing: Int): CameraSpecs {
        close()
        val id = CameraSpecs.findCameraId(cameraManager, lensFacing)
            ?: cameraManager.cameraIdList.firstOrNull()
            ?: error("No cameras available")
        val resolvedSpecs = CameraSpecs.from(cameraManager, id)
        specs = resolvedSpecs
        characteristics = cameraManager.getCameraCharacteristics(id)
        cameraDevice = openDevice(id)
        return resolvedSpecs
    }

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(id: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            try {
                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (cont.isActive) cont.resume(device)
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera disconnected"))
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        if (cont.isActive) cont.resumeWithException(RuntimeException("Camera error $error"))
                    }
                }, backgroundHandler)
            } catch (e: CameraAccessException) {
                cont.resumeWithException(e)
            }
        }

    suspend fun startSession(surfaceTexture: SurfaceTexture, previewSize: Size, settings: CaptureSettings) {
        val device = cameraDevice ?: error("Camera not opened")
        val activeSpecs = specs ?: error("Camera specs missing")

        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(surfaceTexture)
        previewSurface = surface

        val jpegSize = activeSpecs.jpegSizes.maxByOrNull { it.width.toLong() * it.height }
            ?: previewSize
        val reader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ r -> onJpegAvailable(r) }, backgroundHandler)
        jpegReader = reader

        val surfaces = mutableListOf(surface, reader.surface)

        if (activeSpecs.supportsRaw && activeSpecs.rawSizes.isNotEmpty()) {
            val rawSize = activeSpecs.rawSizes.maxByOrNull { it.width.toLong() * it.height }!!
            val raw = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
            raw.setOnImageAvailableListener({ r -> onRawAvailable(r) }, backgroundHandler)
            rawReader = raw
            surfaces += raw.surface
        }

        if (activeSpecs.supportsAnalysisStream) {
            // Smallest supported YUV size: this stream only feeds the histogram, and a hardcoded
            // size would risk configuring an output the device does not actually support.
            val analysisSize = activeSpecs.yuvSizes.minByOrNull { it.width.toLong() * it.height }!!
            val histReader =
                ImageReader.newInstance(analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 2)
            histReader.setOnImageAvailableListener({ r -> onHistogramFrame(r) }, backgroundHandler)
            histogramReader = histReader
            surfaces += histReader.surface
        }

        val session = createSession(device, surfaces)
        captureSession = session

        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            histogramReader?.let { addTarget(it.surface) }
        }
        previewRequestBuilder = builder
        histogramWanted = settings.histogramEnabled
        applySettings(builder, settings, activeSpecs)
        session.setRepeatingRequest(builder.build(), previewCallback, backgroundHandler)
        lastAppliedSettings = settings
    }

    private suspend fun createSession(
        device: CameraDevice,
        surfaces: List<Surface>,
    ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        try {
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cont.isActive) cont.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException("Session configure failed"))
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            cont.resumeWithException(e)
        }
    }

    /** Pushes a settings change onto the live repeating preview request. Cheap no-op if unchanged. */
    fun updateSettings(settings: CaptureSettings) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val activeSpecs = specs ?: return
        if (settings == lastAppliedSettings) return
        histogramWanted = settings.histogramEnabled
        applySettings(builder, settings, activeSpecs)
        try {
            session.setRepeatingRequest(builder.build(), previewCallback, backgroundHandler)
            lastAppliedSettings = settings
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Failed to update repeating request", e)
        }
    }

    private fun applySettings(builder: CaptureRequest.Builder, s: CaptureSettings, specs: CameraSpecs) {
        // --- Exposure: manual ISO + shutter, or AE with compensation/flash mode ---
        if (s.exposureMode == ExposureMode.MANUAL && specs.supportsManualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, specs.isoRange!!.clampInt(s.iso))
            builder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                specs.exposureTimeRangeNanos!!.clampLong(s.shutterSpeedNanos),
            )
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (s.flashMode == FlashMode.TORCH) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF,
            )
        } else {
            val aeMode = when {
                !specs.hasFlash -> CameraMetadata.CONTROL_AE_MODE_ON
                s.flashMode == FlashMode.AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                s.flashMode == FlashMode.ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            builder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                specs.aeCompensationRange.clampInt(s.evCompensationSteps),
            )
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (s.flashMode == FlashMode.TORCH) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF,
            )
        }

        // --- Focus ---
        if (s.focusMode == FocusMode.MANUAL && specs.supportsManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.set(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                s.focusDistanceDiopters.coerceIn(0f, specs.minFocusDistanceDiopters),
            )
        } else {
            val afMode = when {
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in specs.afAvailableModes ->
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                CameraMetadata.CONTROL_AF_MODE_AUTO in specs.afAvailableModes ->
                    CameraMetadata.CONTROL_AF_MODE_AUTO
                else -> CameraMetadata.CONTROL_AF_MODE_OFF
            }
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
        }

        // --- White balance ---
        if (s.wbPreset == WbPreset.KELVIN && specs.supportsManualPostProcessing) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, WhiteBalance.gainsFor(s.wbKelvin, awbGains))
            // Keep the device's calibrated sensor->sRGB matrix. Forcing identity here would drop
            // colour rendering entirely and leave the raw, green-dominant sensor response.
            awbTransform?.let { builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, it) }
        } else {
            val mode = if (s.wbPreset.awbMode in specs.awbAvailableModes) {
                s.wbPreset.awbMode
            } else {
                CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            builder.set(CaptureRequest.CONTROL_AWB_MODE, mode)
        }

        // --- Zoom ---
        builder.set(CaptureRequest.SCALER_CROP_REGION, computeCropRegion(specs.activeArraySize, s.zoomRatio))
    }

    fun triggerTapToFocus(nx: Float, ny: Float) {
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val activeSpecs = specs ?: return
        if (CameraMetadata.CONTROL_AF_MODE_AUTO !in activeSpecs.afAvailableModes) return

        val mapped = mapTapToSensor(nx, ny, activeSpecs.sensorOrientation, activeSpecs.lensFacing)
        val region = arrayOf(meteringRectangleAt(activeSpecs.activeArraySize, mapped.x, mapped.y))

        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, region)
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, region)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        try {
            session.capture(builder.build(), null, backgroundHandler)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), previewCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Tap-to-focus failed", e)
        }
    }

    /** Rotation baked into JPEG/DNG for a portrait-locked activity. */
    fun jpegOrientation(): Int {
        val activeSpecs = specs ?: return 0
        return JpegOrientation.forCapture(
            sensorOrientation = activeSpecs.sensorOrientation,
            lensFacing = activeSpecs.lensFacing,
        )
    }

    suspend fun captureStill(settings: CaptureSettings): CapturedPhoto {
        val device = cameraDevice ?: error("Camera not opened")
        val session = captureSession ?: error("Session not started")
        val activeSpecs = specs ?: error("Camera specs missing")
        val jpeg = jpegReader ?: error("JPEG reader missing")
        val wantsRaw = settings.saveRaw && rawReader != null && activeSpecs.supportsRaw

        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        applySettings(builder, settings, activeSpecs)
        builder.addTarget(jpeg.surface)
        if (wantsRaw) builder.addTarget(rawReader!!.surface)
        builder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
        builder.set(CaptureRequest.JPEG_QUALITY, settings.jpegQuality.toByte())
        if (settings.exposureMode == ExposureMode.MANUAL && settings.flashMode == FlashMode.ON && activeSpecs.hasFlash) {
            builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE)
        }

        val jpegDeferred = CompletableDeferred<ByteArray>()
        val rawImageDeferred = if (wantsRaw) CompletableDeferred<Image>() else null
        val resultDeferred = CompletableDeferred<TotalCaptureResult>()
        pendingJpegDeferred = jpegDeferred
        pendingRawImageDeferred = rawImageDeferred
        pendingResultDeferred = resultDeferred

        session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                resultDeferred.complete(result)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: android.hardware.camera2.CaptureFailure,
            ) {
                val error = RuntimeException("Capture failed: reason=${failure.reason}")
                if (!jpegDeferred.isCompleted) jpegDeferred.completeExceptionally(error)
                if (!resultDeferred.isCompleted) resultDeferred.completeExceptionally(error)
            }
        }, backgroundHandler)

        val jpegBytes = jpegDeferred.await()
        val totalResult = resultDeferred.await()

        val exif = ExifMetadata(
            iso = totalResult.get(CaptureResult.SENSOR_SENSITIVITY),
            exposureTimeNanos = totalResult.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            focalLengthMm = totalResult.get(CaptureResult.LENS_FOCAL_LENGTH),
            fNumber = totalResult.get(CaptureResult.LENS_APERTURE),
            // Pixels stay in sensor orientation, so the rotation lives in EXIF. Re-encoding for an
            // aspect crop strips the tag the camera wrote, so it always has to be restored here.
            orientationDegrees = jpegOrientation(),
        )
        val finalJpeg = if (settings.aspectRatio.ratio > 0f) {
            ImageProcessing.cropToAspect(jpegBytes, settings.aspectRatio.ratio, settings.jpegQuality)
        } else {
            jpegBytes
        }
        val jpegUri = ImageSaver.saveJpeg(appContext, finalJpeg, exif)

        var rawUri: Uri? = null
        var rawError: String? = null
        val rawDeferred = rawImageDeferred
        if (rawDeferred != null) {
            val rawImage = rawDeferred.await()
            try {
                val dngCreator = DngCreator(characteristics!!, totalResult)
                dngCreator.setOrientation(jpegOrientation().toExifOrientation())
                val out = ByteArrayOutputStream()
                dngCreator.writeImage(out, rawImage)
                dngCreator.close()
                rawUri = ImageSaver.saveDng(appContext, out.toByteArray())
            } catch (e: Exception) {
                // The JPEG is already on disk; a DNG failure (some HALs ship incomplete RAW
                // metadata) must not make the whole capture look like it failed.
                Log.w(TAG, "DNG write failed", e)
                rawError = "RAW not saved: ${e.message}"
            } finally {
                rawImage.close()
            }
        }

        return CapturedPhoto(jpegUri, rawUri, rawError)
    }

    fun close() {
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null
        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null
        previewSurface?.release()
        previewSurface = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        histogramReader?.close()
        histogramReader = null
        previewRequestBuilder = null
        lastAppliedSettings = null
        characteristics = null
    }

    private var lastReadoutIso: Int? = null
    private var lastReadoutExposure: Long? = null

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            // Remember the colour correction AWB is producing, to anchor manual Kelvin against it.
            if (result.get(CaptureResult.CONTROL_AWB_MODE) != CameraMetadata.CONTROL_AWB_MODE_OFF) {
                result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { awbGains = it }
                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let { awbTransform = it }
            }

            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val af = result.get(CaptureResult.CONTROL_AF_STATE)
            if (iso != lastReadoutIso || exposure != lastReadoutExposure) {
                lastReadoutIso = iso
                lastReadoutExposure = exposure
                _liveReadout.value = LiveReadout(iso, exposure, af)
            }
        }
    }

    private fun onJpegAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            pendingJpegDeferred?.let { if (!it.isCompleted) it.complete(bytes) }
        } finally {
            image.close()
        }
    }

    private fun onRawAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val deferred = pendingRawImageDeferred
        if (deferred != null && !deferred.isCompleted) {
            deferred.complete(image)
        } else {
            image.close()
        }
    }

    private fun onHistogramFrame(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            if (!histogramWanted) return
            histogramFrameCounter++
            if (histogramFrameCounter % 4 != 0) return
            val yPlane = image.planes[0]
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val bins = IntArray(256)
            var row = 0
            while (row < image.height) {
                var col = 0
                val rowStart = row * rowStride
                while (col < image.width) {
                    val index = rowStart + col * pixelStride
                    if (index < buffer.capacity()) {
                        val value = buffer.get(index).toInt() and 0xFF
                        bins[value]++
                    }
                    col += 2
                }
                row += 2
            }
            _histogram.value = bins
        } finally {
            image.close()
        }
    }

    companion object {
        private const val TAG = "CameraController"

        private fun computeCropRegion(sensorArray: Rect, zoom: Float): Rect {
            val z = zoom.coerceAtLeast(1f)
            val centerX = sensorArray.width() / 2
            val centerY = sensorArray.height() / 2
            val deltaX = (0.5f * sensorArray.width() / z).toInt()
            val deltaY = (0.5f * sensorArray.height() / z).toInt()
            return Rect(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY)
        }

        private fun meteringRectangleAt(activeArray: Rect, nx: Float, ny: Float): MeteringRectangle {
            val fraction = 0.15f
            val regionWidth = max(1, (activeArray.width() * fraction).toInt())
            val regionHeight = max(1, (activeArray.height() * fraction).toInt())
            val centerX = activeArray.left + (nx * activeArray.width()).toInt()
            val centerY = activeArray.top + (ny * activeArray.height()).toInt()
            val left = centerX.coerceIn(activeArray.left, activeArray.right - regionWidth) - regionWidth / 2
            val top = centerY.coerceIn(activeArray.top, activeArray.bottom - regionHeight) - regionHeight / 2
            val safeLeft = left.coerceIn(activeArray.left, activeArray.right - regionWidth)
            val safeTop = top.coerceIn(activeArray.top, activeArray.bottom - regionHeight)
            return MeteringRectangle(safeLeft, safeTop, regionWidth, regionHeight, MeteringRectangle.METERING_WEIGHT_MAX)
        }

        /** Maps a normalized tap point in the preview view to a normalized point in sensor space. */
        private fun mapTapToSensor(nx: Float, ny: Float, sensorOrientation: Int, lensFacing: Int): PointF {
            var x = nx
            var y = ny
            when (sensorOrientation) {
                90 -> {
                    val tmp = x
                    x = y
                    y = 1f - tmp
                }
                180 -> {
                    x = 1f - x
                    y = 1f - y
                }
                270 -> {
                    val tmp = x
                    x = 1f - y
                    y = tmp
                }
            }
            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                x = 1f - x
            }
            return PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }

        private fun Range<Int>.clampInt(value: Int) = value.coerceIn(lower, upper)
        private fun Range<Long>.clampLong(value: Long) = value.coerceIn(lower, upper)
        private fun Int.toExifOrientation(): Int = when (this) {
            90 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
            180 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180
            270 -> androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
            else -> androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        }
    }
}
