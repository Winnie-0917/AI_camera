package com.example.ai_camera.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.hardware.camera2.CameraCharacteristics
import com.example.ai_camera.R
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import com.example.ai_camera.ai.AiAssistantSheet
import com.example.ai_camera.ai.AngleAdvice
import com.example.ai_camera.ai.AngleDirection
import com.example.ai_camera.ai.AngleGuidance
import com.example.ai_camera.ai.AnglePolling
import com.example.ai_camera.ai.GeminiClient
import com.example.ai_camera.ai.GeminiException
import com.example.ai_camera.ai.ChatMessage
import com.example.ai_camera.ai.StyleSuggestion
import com.example.ai_camera.settings.SettingsSheet
import com.example.ai_camera.camera.AspectRatioOption
import com.example.ai_camera.camera.FocusMode
import com.example.ai_camera.camera.WbPreset
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.ExposureMode
import com.example.ai_camera.camera.FlashMode
import com.example.ai_camera.camera.TimerOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Accent = Color(0xFFFFD60A)

@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val specs = state.specs
    val contentAspect = remember(specs) {
        specs?.chooseOptimalPreviewSize()?.let { size ->
            // Camera preview buffers arrive sensor-oriented; in a portrait-locked activity the
            // displayed frame is the buffer rotated 90 degrees, so width/height swap.
            size.height.toFloat() / size.width.toFloat()
        }
    }

    // Preview buffers arrive oriented for a 90-degree sensor. A front lens typically reports 270,
    // which is that upside down.
    val previewRotation = remember(specs) {
        specs?.let { ((it.sensorOrientation - 90 + 360) % 360) } ?: 0
    }

    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var previewSizePx by remember { mutableStateOf(0 to 0) }
    var showSettings by remember { mutableStateOf(false) }
    var showAssistant by remember { mutableStateOf(false) }
    // Owned here, not inside the dialog, so the conversation and the one-tap undo survive
    // closing the assistant to look at the viewfinder.
    val assistantMessages = remember { mutableStateListOf<ChatMessage>() }
    var appliedSuggestion by remember { mutableStateOf<StyleSuggestion?>(null) }
    var settingsBeforeSuggestion by remember { mutableStateOf<CaptureSettings?>(null) }

    var angleGuideOn by remember { mutableStateOf(false) }
    var angleAdvice by remember { mutableStateOf<AngleAdvice?>(null) }
    var angleError by remember { mutableStateOf<String?>(null) }
    var previewView by remember { mutableStateOf<TextureView?>(null) }
    val missingKeyMessage = stringResource(R.string.ai_no_key)
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()

    // Live angle guide: sample the viewfinder on a timer and ask the model for one correction.
    // Cadence backs off once the framing is right, purely to cut API calls.
    LaunchedEffect(angleGuideOn) {
        if (!angleGuideOn) return@LaunchedEffect
        var failures = 0
        // Held here rather than in UI state: the model needs the recent checks to stay consistent,
        // but the viewfinder only ever shows the latest one.
        var window = emptyList<AngleAdvice>()
        while (isActive) {
            val startedAt = SystemClock.elapsedRealtime()
            val jpeg = previewView?.let { grabPreviewJpeg(it, previewRotation) }
            if (jpeg != null) {
                try {
                    val advice = GeminiClient.analyzeAngle(
                        jpeg,
                        languageTag,
                        window.map { past ->
                            val told = AngleGuidance.directionFor(past.issue, state.settings.lensFacing)
                            "saw ${past.issue.tag}, told the user: ${told.name.lowercase()}"
                        },
                    )
                    window = AngleGuidance.slidingWindow(window, advice)
                    angleAdvice = advice
                    angleError = null
                    failures = 0
                } catch (e: Exception) {
                    failures++
                    angleError = if (e is GeminiException && e.message == "MISSING_KEY") {
                        missingKeyMessage
                    } else {
                        e.message ?: e::class.java.simpleName
                    }
                }
            }
            // Measured from the start of the round trip, so the period is the requested interval
            // rather than interval + however long the model took.
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val target = if (failures > 0) {
                AnglePolling.backoffFor(failures)
            } else {
                AnglePolling.intervalFor(angleAdvice)
            }
            val wait = (target - elapsed).coerceAtLeast(0L)
            Log.d("AngleGuide", "perfect=${angleAdvice?.perfect} failures=$failures wait=${wait}ms")
            delay(wait)
        }
    }

    LaunchedEffect(focusRingPosition) {
        if (focusRingPosition != null) {
            delay(1200)
            focusRingPosition = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraPreview(
            contentAspect = contentAspect,
            previewRotation = previewRotation,
            onSurfaceAvailable = viewModel::onSurfaceTextureAvailable,
            onSurfaceDestroyed = viewModel::onSurfaceTextureDestroyed,
            onTapFocus = { nx, ny ->
                // The tap is in displayed coordinates; undo the preview rotation before mapping
                // it back to the sensor, or a tap on a rotated preview focuses the opposite spot.
                if (previewRotation == 180) {
                    viewModel.tapToFocus(1f - nx, 1f - ny)
                } else {
                    viewModel.tapToFocus(nx, ny)
                }
                val (w, h) = previewSizePx
                if (w > 0 && h > 0 && contentAspect != null) {
                    focusRingPosition = frameOffsetToView(nx, ny, contentAspect, w, h)
                }
            },
            onZoomDelta = { zoom ->
                val max = specs?.maxDigitalZoom ?: 1f
                viewModel.updateSettings { s ->
                    s.copy(zoomRatio = (s.zoomRatio * zoom).coerceIn(1f, max))
                }
            },
            onViewReady = { previewView = it },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewSizePx = it.width to it.height },
        )

        // Overlays are constrained to the letterboxed frame so the grid matches the real image.
        if (contentAspect != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewAspect = maxWidth / maxHeight
                val frameModifier = if (contentAspect > viewAspect) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxHeight()
                }
                Box(
                    modifier = frameModifier
                        .aspectRatio(contentAspect)
                        .align(Alignment.Center)
                ) {
                    if (state.settings.gridEnabled) GridOverlay()
                    if (state.settings.levelEnabled) LevelOverlay(rollDegrees = rememberDeviceRoll())
                }
            }
        }

        focusRingPosition?.let { FocusRing(position = it) }

        if (state.isCapturing) ShutterFlashOverlay(visible = true)

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onChange = viewModel::updateSettings,
                onOpenSettings = { showSettings = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            AiAssistantButton(
                onClick = { showAssistant = true },
                onLongClick = {
                    angleGuideOn = !angleGuideOn
                    if (!angleGuideOn) angleAdvice = null
                    angleError = null
                },
                active = angleGuideOn,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (angleGuideOn) {
                AngleGuideBanner(
                    advice = angleAdvice,
                    error = angleError,
                    lensFacing = state.settings.lensFacing,
                    onStop = {
                        angleGuideOn = false
                        angleAdvice = null
                        angleError = null
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (state.settings.histogramEnabled) {
                HistogramOverlay(
                    bins = state.histogram,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.timerCountdown > 0) {
                Text(
                    text = "${state.timerCountdown}",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
            ) {
                ReadoutHud(state = state, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                ProControls(state = state, onChange = viewModel::updateSettings)
                Spacer(Modifier.height(14.dp))
                ShutterRow(
                    isCapturing = state.isCapturing,
                    onCapture = viewModel::capturePhoto,
                    onSwitchCamera = viewModel::switchCamera,
                    onOpenGallery = {
                        state.lastSavedUri?.let { uri ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri).apply {
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                )
                            }
                        }
                    },
                    hasPhoto = state.lastSavedUri != null,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }

        if (showSettings) {
            SettingsSheet(onDismiss = { showSettings = false })
        }

        if (showAssistant) {
            AiAssistantSheet(
                cameraContext = cameraContextOf(state),
                specs = state.specs,
                messages = assistantMessages,
                appliedSuggestion = appliedSuggestion,
                onRevert = {
                    settingsBeforeSuggestion?.let { previous ->
                        viewModel.updateSettings { previous }
                    }
                    appliedSuggestion = null
                    settingsBeforeSuggestion = null
                },
                onApply = { suggestion ->
                    val specs = state.specs
                    if (specs == null) {
                        StyleSuggestion.Applied(
                            state.settings,
                            state.settings,
                            emptyList(),
                            emptyList(),
                        )
                    } else {
                        // Applied to the live settings inside the transform and clamped to this
                        // camera's real ranges; the result reports what the hardware could not do.
                        lateinit var result: StyleSuggestion.Applied
                        viewModel.updateSettings { current ->
                            result = suggestion.applyTo(current, specs)
                            result.settings
                        }
                        // Only offer undo when something actually changed.
                        if (result.applied.isNotEmpty()) {
                            appliedSuggestion = suggestion
                            settingsBeforeSuggestion = result.previous
                        }
                        result
                    }
                },
                onDismiss = { showAssistant = false },
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCCB3261E))
                    .clickable { viewModel.consumeError() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TopBar(
    state: CameraUiState,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val specs = state.specs

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggle(
            icon = Icons.Default.Settings,
            active = false,
            contentDescription = stringResource(R.string.action_settings),
            onClick = onOpenSettings,
        )

        if (specs?.hasFlash == true) {
            IconToggle(
                icon = when (settings.flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.TORCH -> Icons.Default.Highlight
                },
                active = settings.flashMode != FlashMode.OFF,
                onClick = {
                    onChange { s ->
                        s.copy(
                            flashMode = when (s.flashMode) {
                                FlashMode.OFF -> FlashMode.AUTO
                                FlashMode.AUTO -> FlashMode.ON
                                FlashMode.ON -> FlashMode.TORCH
                                FlashMode.TORCH -> FlashMode.OFF
                            }
                        )
                    }
                },
            )
        }

        IconToggle(
            icon = Icons.Default.Timer,
            active = settings.timer != TimerOption.OFF,
            badge = if (settings.timer != TimerOption.OFF) "${settings.timer.seconds}" else null,
            onClick = {
                onChange { s ->
                    s.copy(
                        timer = when (s.timer) {
                            TimerOption.OFF -> TimerOption.S2
                            TimerOption.S2 -> TimerOption.S5
                            TimerOption.S5 -> TimerOption.S10
                            TimerOption.S10 -> TimerOption.OFF
                        }
                    )
                }
            },
        )

        IconToggle(
            icon = Icons.Default.GridOn,
            active = settings.gridEnabled,
            onClick = { onChange { it.copy(gridEnabled = !it.gridEnabled) } },
        )

        if (specs?.supportsAnalysisStream == true) {
            IconToggle(
                icon = Icons.Default.BarChart,
                active = settings.histogramEnabled,
                onClick = { onChange { it.copy(histogramEnabled = !it.histogramEnabled) } },
            )
        }

        IconToggle(
            icon = Icons.Default.Straighten,
            active = settings.levelEnabled,
            onClick = { onChange { it.copy(levelEnabled = !it.levelEnabled) } },
        )

        TextToggle(
            label = stringResource(settings.aspectRatio.labelRes),
            active = settings.aspectRatio != AspectRatioOption.FULL,
            onClick = {
                onChange { s ->
                    s.copy(
                        aspectRatio = when (s.aspectRatio) {
                            AspectRatioOption.FULL -> AspectRatioOption.R4_3
                            AspectRatioOption.R4_3 -> AspectRatioOption.R16_9
                            AspectRatioOption.R16_9 -> AspectRatioOption.R1_1
                            AspectRatioOption.R1_1 -> AspectRatioOption.FULL
                        }
                    )
                }
            },
        )

        if (specs?.supportsRaw == true) {
            TextToggle(
                label = "RAW",
                active = settings.saveRaw,
                onClick = { onChange { it.copy(saveRaw = !it.saveRaw) } },
            )
        }
    }
}

/**
 * Snapshots the viewfinder for the angle guide. Downscaled hard: the model only needs to judge
 * framing, and a small frame keeps upload latency and token cost down on a repeating timer.
 */
private suspend fun grabPreviewJpeg(
    view: TextureView,
    rotationDegrees: Int,
    maxEdge: Int = 640,
): ByteArray? {
    // getBitmap must run on the UI thread; JPEG encoding is pushed off it.
    val bitmap = withContext(Dispatchers.Main) {
        if (view.width <= 0 || view.height <= 0 || !view.isAvailable) return@withContext null
        val scale = maxEdge.toFloat() / maxOf(view.width, view.height)
        val w = if (scale < 1f) (view.width * scale).toInt() else view.width
        val h = if (scale < 1f) (view.height * scale).toInt() else view.height
        runCatching { view.getBitmap(w, h) }.getOrNull()
    } ?: return null

    return withContext(Dispatchers.IO) {
        // getBitmap returns the raw surface texture and ignores the view's transform, so the
        // preview rotation has to be reapplied here - otherwise the model judges a front-camera
        // frame upside down and every up/down call comes back inverted.
        val oriented = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        oriented.compress(Bitmap.CompressFormat.JPEG, 80, out)
        oriented.recycle()
        out.toByteArray()
    }
}

@Composable
private fun AngleGuideBanner(
    advice: AngleAdvice?,
    error: String?,
    lensFacing: Int,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val perfect = advice?.perfect == true
    // The instruction is derived here rather than taken from the model, so left/right is correct
    // for the lens in use.
    val direction = advice?.let { AngleGuidance.directionFor(it.issue, lensFacing) }
    val tint = when {
        error != null -> Color(0xFFFF6B6B)
        perfect -> Color(0xFF34C759)
        else -> Accent
    }
    val text = when {
        error != null -> error
        advice == null -> stringResource(R.string.ai_angle_analyzing)
        perfect || direction == null || direction == AngleDirection.NONE ->
            stringResource(R.string.ai_angle_perfect)
        else -> stringResource(direction.labelRes)
    }
    val icon = when {
        advice == null -> Icons.Default.HourglassEmpty
        perfect || direction == null -> Icons.Default.Check
        else -> when (direction) {
            AngleDirection.LEFT -> Icons.Default.West
            AngleDirection.RIGHT -> Icons.Default.East
            AngleDirection.UP -> Icons.Default.North
            AngleDirection.DOWN -> Icons.Default.South
            AngleDirection.ROTATE_LEFT -> Icons.Default.RotateLeft
            AngleDirection.ROTATE_RIGHT -> Icons.Default.RotateRight
            AngleDirection.CLOSER -> Icons.Default.ZoomIn
            AngleDirection.FARTHER -> Icons.Default.ZoomOut
            AngleDirection.NONE -> Icons.Default.Check
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(max = 190.dp),
            )
            val note = advice?.note.orEmpty()
            if (error == null && note.isNotBlank() && !perfect) {
                Text(
                    text = note,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.widthIn(max = 190.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.ai_angle_stop),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onStop)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AiAssistantButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF34C759) else Accent.copy(alpha = 0.9f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = stringResource(R.string.action_ai),
            tint = Color.Black,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Snapshot of the current camera state, sent to the assistant so its advice can reference the
 * settings actually in effect rather than generic guidance.
 */
private fun cameraContextOf(state: CameraUiState): String {
    val s = state.settings
    val specs = state.specs
    val manual = s.exposureMode == ExposureMode.MANUAL
    val iso = if (manual) s.iso else state.liveReadout.iso
    val shutter = if (manual) s.shutterSpeedNanos else state.liveReadout.exposureNanos

    return buildString {
        appendLine("- Exposure mode: ${if (manual) "manual" else "auto"}")
        appendLine("- ISO: ${iso ?: "unknown"}")
        appendLine("- Shutter speed: ${shutter?.let { formatShutter(it) } ?: "unknown"}")
        specs?.let {
            appendLine(
                "- Exposure compensation: ${formatEv(s.evCompensationSteps, it.aeCompensationStep)} EV"
            )
        }
        appendLine(
            "- White balance: " + if (s.wbPreset == WbPreset.KELVIN) {
                formatKelvin(s.wbKelvin)
            } else {
                s.wbPreset.name.lowercase()
            }
        )
        appendLine(
            "- Focus: " + if (s.focusMode == FocusMode.MANUAL) {
                "manual at ${formatFocus(s.focusDistanceDiopters)}"
            } else {
                "autofocus"
            }
        )
        appendLine("- Zoom: ${formatZoom(s.zoomRatio)}")
        appendLine("- Flash: ${s.flashMode.name.lowercase()}")
        appendLine("- Aspect ratio: ${s.aspectRatio.name.removePrefix("R").replace('_', ':')}")
        appendLine("- RAW capture: ${if (s.saveRaw) "on" else "off"}")
        appendLine("- Self timer: ${s.timer.seconds}s")
        appendLine(
            "- Lens: " + if (s.lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                "front"
            } else {
                "back"
            }
        )
        specs?.let {
            appendLine(
                "- This camera supports: manual exposure=${it.supportsManualExposure}, " +
                    "manual focus=${it.supportsManualFocus}, RAW=${it.supportsRaw}, " +
                    "ISO range=${it.isoRange}, max zoom=${it.maxDigitalZoom}x"
            )
        }
    }
}

@Composable
private fun ReadoutHud(state: CameraUiState, modifier: Modifier = Modifier) {
    val settings = state.settings
    val specs = state.specs ?: return
    val isManual = settings.exposureMode == ExposureMode.MANUAL
    val iso = if (isManual) settings.iso else state.liveReadout.iso
    val shutter = if (isManual) settings.shutterSpeedNanos else state.liveReadout.exposureNanos
    val modeLabel = stringResource(if (isManual) R.string.mode_manual else R.string.mode_auto)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = buildString {
                append(modeLabel)
                append("  ·  ISO ")
                append(iso?.toString() ?: "--")
                append("  ·  ")
                append(shutter?.let { formatShutter(it) } ?: "--")
                append("  ·  ")
                append(formatEv(settings.evCompensationSteps, specs.aeCompensationStep))
                append(" EV")
                if (settings.saveRaw) append("  ·  RAW")
            },
            color = if (isManual) Accent else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ShutterRow(
    isCapturing: Boolean,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(enabled = hasPhoto, onClick = onOpenGallery),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = stringResource(R.string.action_open_last_photo),
                tint = Color.White.copy(alpha = if (hasPhoto) 0.9f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (isCapturing) Color.White.copy(alpha = 0.5f) else Color.White)
                .clickable(enabled = !isCapturing, onClick = onCapture)
                .alpha(if (isCapturing) 0.6f else 1f)
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onSwitchCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = stringResource(R.string.action_switch_camera),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun IconToggle(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Accent else Color.White,
            modifier = Modifier.size(20.dp),
        )
        if (badge != null) {
            Text(
                text = badge,
                color = Accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            )
        }
    }
}

@Composable
private fun TextToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Accent else Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

/** Converts a normalized point inside the camera frame back into view pixel coordinates. */
private fun frameOffsetToView(
    nx: Float,
    ny: Float,
    contentAspect: Float,
    viewWidth: Int,
    viewHeight: Int,
): Offset {
    val viewAspect = viewWidth.toFloat() / viewHeight
    val contentWidth: Float
    val contentHeight: Float
    if (contentAspect > viewAspect) {
        contentWidth = viewWidth.toFloat()
        contentHeight = viewWidth / contentAspect
    } else {
        contentHeight = viewHeight.toFloat()
        contentWidth = viewHeight * contentAspect
    }
    return Offset(
        x = (viewWidth - contentWidth) / 2f + nx * contentWidth,
        y = (viewHeight - contentHeight) / 2f + ny * contentHeight,
    )
}
