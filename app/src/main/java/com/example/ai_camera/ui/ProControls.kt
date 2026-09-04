package com.example.ai_camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_camera.R
import com.example.ai_camera.camera.CameraSpecs
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.ExposureMode
import com.example.ai_camera.camera.FocusMode
import com.example.ai_camera.camera.WbPreset

private val Accent = Color(0xFFFFD60A)
private val PanelBackground = Color.Black.copy(alpha = 0.55f)

enum class ProParam(@StringRes val labelRes: Int) {
    ISO(R.string.param_iso),
    SHUTTER(R.string.param_shutter),
    WHITE_BALANCE(R.string.param_wb),
    EXPOSURE_COMP(R.string.param_ev),
    FOCUS(R.string.param_focus),
    ZOOM(R.string.param_zoom),
    QUALITY(R.string.param_quality),
}

@Composable
fun ProControls(
    state: CameraUiState,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specs = state.specs ?: return
    val settings = state.settings
    var selected by remember { mutableStateOf<ProParam?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Switching cameras can drop a control that is currently open (the front lens may offer
        // manual focus where the back one does not), so re-check against the active camera.
        selected?.takeIf { it.isAdjustable(specs) }?.let { param ->
            ParameterEditor(
                param = param,
                specs = specs,
                settings = settings,
                readout = state.liveReadout,
                onChange = onChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProParam.entries.forEach { param ->
                if (param.isAdjustable(specs)) {
                    ParameterChip(
                        param = param,
                        value = param.valueLabel(settings, specs, state),
                        isAuto = param.isAuto(settings),
                        selected = selected == param,
                        onClick = { selected = if (selected == param) null else param },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterChip(
    param: ProParam,
    value: String,
    isAuto: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else PanelBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .width(64.dp),
    ) {
        Text(
            text = stringResource(param.labelRes),
            color = if (selected) Accent else Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = if (isAuto) Color.White.copy(alpha = 0.85f) else Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ParameterEditor(
    param: ProParam,
    specs: CameraSpecs,
    settings: CaptureSettings,
    readout: com.example.ai_camera.camera.LiveReadout,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (param) {
            ProParam.ISO -> IsoEditor(specs, settings, readout, onChange)
            ProParam.SHUTTER -> ShutterEditor(specs, settings, readout, onChange)
            ProParam.WHITE_BALANCE -> WhiteBalanceEditor(specs, settings, onChange)
            ProParam.EXPOSURE_COMP -> ExposureCompEditor(specs, settings, onChange)
            ProParam.FOCUS -> FocusEditor(specs, settings, onChange)
            ProParam.ZOOM -> ZoomEditor(specs, settings, onChange)
            ProParam.QUALITY -> QualityEditor(settings, onChange)
        }
    }
}

@Composable
private fun EditorHeader(
    title: String,
    value: String,
    autoLabel: String? = null,
    isAuto: Boolean = false,
    onToggleAuto: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (autoLabel != null && onToggleAuto != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = autoLabel,
                    color = if (isAuto) Accent else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAuto) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                        .clickable { onToggleAuto() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            disabledThumbColor = Color.White.copy(alpha = 0.3f),
            disabledActiveTrackColor = Color.White.copy(alpha = 0.2f),
        ),
    )
}

@Composable
private fun IsoEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    readout: com.example.ai_camera.camera.LiveReadout,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val range = specs.isoRange
    val isManual = settings.exposureMode == ExposureMode.MANUAL
    val displayed = if (isManual) settings.iso.toString() else (readout.iso?.toString() ?: "AUTO")

    EditorHeader(
        title = stringResource(R.string.editor_iso),
        value = displayed,
        autoLabel = stringResource(R.string.toggle_auto),
        isAuto = !isManual,
        onToggleAuto = {
            onChange { s ->
                if (s.exposureMode == ExposureMode.MANUAL) {
                    s.copy(exposureMode = ExposureMode.AUTO)
                } else {
                    // Seed manual values from what auto exposure last chose, so switching
                    // to manual does not visibly jump the exposure.
                    s.copy(
                        exposureMode = ExposureMode.MANUAL,
                        iso = readout.iso ?: s.iso,
                        shutterSpeedNanos = readout.exposureNanos ?: s.shutterSpeedNanos,
                    )
                }
            }
        },
    )

    if (range != null) {
        CameraSlider(
            value = valueToLog(settings.iso.toFloat(), range.lower.toFloat(), range.upper.toFloat()),
            enabled = isManual,
            onValueChange = { t ->
                val iso = logToValue(t, range.lower.toFloat(), range.upper.toFloat())
                onChange { it.copy(iso = iso.toInt().coerceIn(range.lower, range.upper)) }
            },
        )
        RangeLabels("${range.lower}", "${range.upper}")
    }
}

@Composable
private fun ShutterEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    readout: com.example.ai_camera.camera.LiveReadout,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val range = specs.exposureTimeRangeNanos
    val isManual = settings.exposureMode == ExposureMode.MANUAL
    val displayed = if (isManual) {
        formatShutter(settings.shutterSpeedNanos)
    } else {
        readout.exposureNanos?.let { formatShutter(it) } ?: "AUTO"
    }

    EditorHeader(
        title = stringResource(R.string.editor_shutter),
        value = displayed,
        autoLabel = stringResource(R.string.toggle_auto),
        isAuto = !isManual,
        onToggleAuto = {
            onChange { s ->
                if (s.exposureMode == ExposureMode.MANUAL) {
                    s.copy(exposureMode = ExposureMode.AUTO)
                } else {
                    s.copy(
                        exposureMode = ExposureMode.MANUAL,
                        iso = readout.iso ?: s.iso,
                        shutterSpeedNanos = readout.exposureNanos ?: s.shutterSpeedNanos,
                    )
                }
            }
        },
    )

    if (range != null) {
        CameraSlider(
            value = valueToLog(
                settings.shutterSpeedNanos.toFloat(),
                range.lower.toFloat(),
                range.upper.toFloat(),
            ),
            enabled = isManual,
            onValueChange = { t ->
                val nanos = logToValue(t, range.lower.toFloat(), range.upper.toFloat()).toLong()
                onChange { it.copy(shutterSpeedNanos = nanos.coerceIn(range.lower, range.upper)) }
            },
        )
        RangeLabels(formatShutter(range.lower), formatShutter(range.upper))
    }
}

@Composable
private fun WhiteBalanceEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val kelvinSelected = settings.wbPreset == WbPreset.KELVIN

    EditorHeader(
        title = stringResource(R.string.editor_wb),
        value = if (kelvinSelected) {
            formatKelvin(settings.wbKelvin)
        } else {
            stringResource(settings.wbPreset.labelRes)
        },
    )
    Spacer(Modifier.height(6.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WbPreset.entries.forEach { preset ->
            if (preset.isAvailable(specs)) {
                val isSelected = settings.wbPreset == preset
                Text(
                    text = stringResource(preset.labelRes),
                    color = if (isSelected) Accent else Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) Accent.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.1f)
                        )
                        .clickable { onChange { it.copy(wbPreset = preset) } }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }

    if (kelvinSelected) {
        Spacer(Modifier.height(4.dp))
        CameraSlider(
            value = (settings.wbKelvin - 2000) / 8000f,
            onValueChange = { t ->
                val kelvin = (2000 + t * 8000).toInt() / 100 * 100
                onChange { it.copy(wbKelvin = kelvin.coerceIn(2000, 10000)) }
            },
        )
        RangeLabels("2000K", "10000K")
    }
}

@Composable
private fun ExposureCompEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val range = specs.aeCompensationRange
    val isManualExposure = settings.exposureMode == ExposureMode.MANUAL
    val stepCount = (range.upper - range.lower)

    EditorHeader(
        title = if (isManualExposure) {
            stringResource(R.string.editor_ev_disabled)
        } else {
            stringResource(R.string.editor_ev)
        },
        value = "${formatEv(settings.evCompensationSteps, specs.aeCompensationStep)} EV",
    )
    if (stepCount > 0) {
        CameraSlider(
            value = (settings.evCompensationSteps - range.lower).toFloat() / stepCount,
            enabled = !isManualExposure,
            steps = (stepCount - 1).coerceAtLeast(0),
            onValueChange = { t ->
                val steps = (range.lower + (t * stepCount)).toInt().coerceIn(range.lower, range.upper)
                onChange { it.copy(evCompensationSteps = steps) }
            },
        )
        RangeLabels(
            formatEv(range.lower, specs.aeCompensationStep),
            formatEv(range.upper, specs.aeCompensationStep),
        )
    }
}

@Composable
private fun FocusEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val isManual = settings.focusMode == FocusMode.MANUAL
    val maxDiopters = specs.minFocusDistanceDiopters

    EditorHeader(
        title = stringResource(R.string.editor_focus),
        value = if (isManual) {
            formatFocus(settings.focusDistanceDiopters)
        } else {
            stringResource(R.string.toggle_af)
        },
        autoLabel = stringResource(R.string.toggle_af),
        isAuto = !isManual,
        onToggleAuto = {
            onChange {
                it.copy(
                    focusMode = if (it.focusMode == FocusMode.MANUAL) FocusMode.AUTO else FocusMode.MANUAL
                )
            }
        },
    )

    if (maxDiopters > 0f) {
        CameraSlider(
            value = settings.focusDistanceDiopters / maxDiopters,
            enabled = isManual,
            onValueChange = { t ->
                onChange { it.copy(focusDistanceDiopters = (t * maxDiopters).coerceIn(0f, maxDiopters)) }
            },
        )
        RangeLabels(
            stringResource(R.string.focus_infinity),
            stringResource(R.string.focus_macro),
        )
    }
}

@Composable
private fun ZoomEditor(
    specs: CameraSpecs,
    settings: CaptureSettings,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    val maxZoom = specs.maxDigitalZoom.coerceAtLeast(1f)
    EditorHeader(title = stringResource(R.string.editor_zoom), value = formatZoom(settings.zoomRatio))
    CameraSlider(
        value = (settings.zoomRatio - 1f) / (maxZoom - 1f).coerceAtLeast(0.01f),
        onValueChange = { t ->
            onChange { it.copy(zoomRatio = (1f + t * (maxZoom - 1f)).coerceIn(1f, maxZoom)) }
        },
    )
    RangeLabels("1.0x", formatZoom(maxZoom))
}

@Composable
private fun QualityEditor(
    settings: CaptureSettings,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
) {
    EditorHeader(title = stringResource(R.string.editor_quality), value = "${settings.jpegQuality}")
    CameraSlider(
        value = (settings.jpegQuality - 50) / 50f,
        steps = 9,
        onValueChange = { t ->
            onChange { it.copy(jpegQuality = (50 + t * 50).toInt().coerceIn(50, 100)) }
        },
    )
    RangeLabels("50", "100")
}

@Composable
private fun RangeLabels(start: String, end: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        Text(end, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
    }
}

/** Whether this white balance option can be selected on the current camera. */
private fun WbPreset.isAvailable(specs: CameraSpecs): Boolean =
    if (this == WbPreset.KELVIN) {
        specs.supportsManualPostProcessing
    } else {
        awbMode in specs.awbAvailableModes
    }

/**
 * A chip appears only when the user can actually change the value on this camera. Values that
 * exist but are not controllable (the ISO auto exposure picked on a camera without MANUAL_SENSOR,
 * say) are still shown in the HUD readout above - they just do not get a dead control.
 */
private fun ProParam.isAdjustable(specs: CameraSpecs): Boolean = when (this) {
    ProParam.ISO -> specs.isoAdjustable
    ProParam.SHUTTER -> specs.shutterAdjustable
    // One available preset means there is nothing to switch between.
    ProParam.WHITE_BALANCE -> WbPreset.entries.count { it.isAvailable(specs) } > 1
    ProParam.EXPOSURE_COMP -> specs.evAdjustable
    ProParam.FOCUS -> specs.focusAdjustable
    ProParam.ZOOM -> specs.zoomAdjustable
    // JPEG quality is applied by the app, so it is adjustable on every device.
    ProParam.QUALITY -> true
}

private fun ProParam.isAuto(settings: CaptureSettings): Boolean = when (this) {
    ProParam.ISO, ProParam.SHUTTER -> settings.exposureMode == ExposureMode.AUTO
    ProParam.FOCUS -> settings.focusMode == FocusMode.AUTO
    ProParam.WHITE_BALANCE -> settings.wbPreset == WbPreset.AUTO
    else -> false
}

@Composable
private fun ProParam.valueLabel(
    settings: CaptureSettings,
    specs: CameraSpecs,
    state: CameraUiState,
): String = when (this) {
    ProParam.ISO -> if (settings.exposureMode == ExposureMode.MANUAL) {
        settings.iso.toString()
    } else {
        state.liveReadout.iso?.toString() ?: stringResource(R.string.toggle_auto)
    }
    ProParam.SHUTTER -> if (settings.exposureMode == ExposureMode.MANUAL) {
        formatShutter(settings.shutterSpeedNanos)
    } else {
        state.liveReadout.exposureNanos?.let { formatShutter(it) }
            ?: stringResource(R.string.toggle_auto)
    }
    ProParam.WHITE_BALANCE -> if (settings.wbPreset == WbPreset.KELVIN) {
        formatKelvin(settings.wbKelvin)
    } else {
        stringResource(settings.wbPreset.labelRes)
    }
    ProParam.EXPOSURE_COMP -> formatEv(settings.evCompensationSteps, specs.aeCompensationStep)
    ProParam.FOCUS -> if (settings.focusMode == FocusMode.MANUAL) {
        formatFocus(settings.focusDistanceDiopters)
    } else {
        stringResource(R.string.toggle_af)
    }
    ProParam.ZOOM -> formatZoom(settings.zoomRatio)
    ProParam.QUALITY -> "${settings.jpegQuality}"
}
