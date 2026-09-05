package com.example.ai_camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_camera.R
import com.example.ai_camera.camera.AspectRatioOption
import com.example.ai_camera.camera.CameraSpecs
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.ExposureMode
import com.example.ai_camera.camera.FlashMode
import com.example.ai_camera.camera.FocusMode
import com.example.ai_camera.camera.TimerOption
import com.example.ai_camera.camera.WbPreset

/**
 * The full parameter panel, for when someone wants to set things deliberately rather than through
 * the quick chips over the viewfinder.
 *
 * Grouped by what the photographer is deciding - the image itself, then exposure, focus, colour,
 * and finally the framing aids - rather than by which Camera2 key each one happens to set. Rows
 * appear only when the active lens actually supports them, so the panel is shorter on a camera
 * that cannot do manual exposure rather than showing controls that do nothing.
 */
@Composable
fun ProPanel(
    state: CameraUiState,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specs = state.specs ?: return
    val settings = state.settings
    val readout = state.liveReadout

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(CameraPalette.Surface)
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        if (specs.supportsRaw) {
            PanelSection(stringResource(R.string.group_image))
            PanelRow(stringResource(R.string.panel_format)) {
                PillGroup(
                    options = listOf(
                        stringResource(R.string.format_jpg) to false,
                        stringResource(R.string.format_raw_jpg) to true,
                    ),
                    isSelected = { it == settings.saveRaw },
                    onSelect = { raw -> onChange { it.copy(saveRaw = raw) } },
                )
            }
        }

        PanelRow(stringResource(R.string.panel_aspect)) {
            PillGroup(
                options = AspectRatioOption.entries.map { stringResource(it.labelRes) to it },
                isSelected = { it == settings.aspectRatio },
                onSelect = { ratio -> onChange { it.copy(aspectRatio = ratio) } },
            )
        }

        PanelSection(stringResource(R.string.group_exposure))

        if (specs.isoAdjustable) {
            val manual = settings.exposureMode == ExposureMode.MANUAL
            PanelRow(stringResource(R.string.param_iso)) {
                PillGroup(
                    options = listOf(
                        stringResource(R.string.toggle_auto) to false,
                        (if (manual) settings.iso else readout.iso ?: settings.iso).toString() to true,
                    ),
                    isSelected = { it == manual },
                    onSelect = { wantManual ->
                        onChange { s ->
                            if (wantManual) {
                                // Seed from what auto exposure last chose so nothing jumps.
                                s.copy(
                                    exposureMode = ExposureMode.MANUAL,
                                    iso = readout.iso ?: s.iso,
                                    shutterSpeedNanos = readout.exposureNanos ?: s.shutterSpeedNanos,
                                )
                            } else {
                                s.copy(exposureMode = ExposureMode.AUTO)
                            }
                        }
                    },
                )
            }
            if (manual) {
                specs.isoRange?.let { range ->
                    PanelSlider(
                        value = valueToLog(
                            settings.iso.toFloat(),
                            range.lower.toFloat(),
                            range.upper.toFloat(),
                        ),
                        onValueChange = { t ->
                            val iso = logToValue(t, range.lower.toFloat(), range.upper.toFloat())
                            onChange { it.copy(iso = iso.toInt()) }
                        },
                        left = "${range.lower}",
                        right = "${range.upper}",
                    )
                }
            }

            PanelRow(stringResource(R.string.param_shutter)) {
                val shown = if (manual) settings.shutterSpeedNanos else readout.exposureNanos
                PillGroup(
                    options = listOf(
                        stringResource(R.string.toggle_auto) to false,
                        (shown?.let { formatShutter(it) } ?: "--") to true,
                    ),
                    isSelected = { it == manual },
                    onSelect = { wantManual ->
                        onChange { s ->
                            if (wantManual) {
                                s.copy(
                                    exposureMode = ExposureMode.MANUAL,
                                    iso = readout.iso ?: s.iso,
                                    shutterSpeedNanos = readout.exposureNanos ?: s.shutterSpeedNanos,
                                )
                            } else {
                                s.copy(exposureMode = ExposureMode.AUTO)
                            }
                        }
                    },
                )
            }
            if (manual) {
                specs.exposureTimeRangeNanos?.let { range ->
                    PanelSlider(
                        value = valueToLog(
                            settings.shutterSpeedNanos.toFloat(),
                            range.lower.toFloat(),
                            range.upper.toFloat(),
                        ),
                        onValueChange = { t ->
                            val nanos = logToValue(
                                t, range.lower.toFloat(), range.upper.toFloat(),
                            ).toLong()
                            onChange { it.copy(shutterSpeedNanos = nanos) }
                        },
                        left = formatShutter(range.lower),
                        right = formatShutter(range.upper),
                    )
                }
            }
        }

        if (specs.evAdjustable) {
            val manualExposure = settings.exposureMode == ExposureMode.MANUAL
            PanelRow(stringResource(R.string.param_ev)) {
                Stepper(
                    value = formatEv(settings.evCompensationSteps, specs.aeCompensationStep),
                    // Exposure compensation steers auto exposure, so it does nothing once ISO and
                    // shutter are both pinned by hand.
                    enabled = !manualExposure,
                    onDecrease = {
                        onChange {
                            it.copy(
                                evCompensationSteps = (it.evCompensationSteps - 1)
                                    .coerceAtLeast(specs.aeCompensationRange.lower),
                            )
                        }
                    },
                    onIncrease = {
                        onChange {
                            it.copy(
                                evCompensationSteps = (it.evCompensationSteps + 1)
                                    .coerceAtMost(specs.aeCompensationRange.upper),
                            )
                        }
                    },
                )
            }
        }

        if (specs.hasFlash) {
            PanelRow(stringResource(R.string.panel_flash)) {
                PillGroup(
                    options = FlashMode.entries.map { flashLabel(it) to it },
                    isSelected = { it == settings.flashMode },
                    onSelect = { mode -> onChange { it.copy(flashMode = mode) } },
                )
            }
        }

        if (specs.focusAdjustable) {
            PanelSection(stringResource(R.string.group_focus))
            val manualFocus = settings.focusMode == FocusMode.MANUAL
            PanelRow(stringResource(R.string.param_focus)) {
                PillGroup(
                    options = listOf(
                        stringResource(R.string.focus_mf) to FocusMode.MANUAL,
                        stringResource(R.string.toggle_af) to FocusMode.AUTO,
                    ),
                    isSelected = { it == settings.focusMode },
                    onSelect = { mode -> onChange { it.copy(focusMode = mode) } },
                )
            }
            if (manualFocus) {
                PanelSlider(
                    value = settings.focusDistanceDiopters / specs.minFocusDistanceDiopters,
                    onValueChange = { t ->
                        onChange {
                            it.copy(focusDistanceDiopters = t * specs.minFocusDistanceDiopters)
                        }
                    },
                    left = stringResource(R.string.focus_infinity),
                    right = stringResource(R.string.focus_macro),
                )
            }
        }

        PanelSection(stringResource(R.string.group_colour))
        PanelRow(stringResource(R.string.param_wb)) {
            val kelvin = settings.wbPreset == WbPreset.KELVIN
            PillGroup(
                options = buildList {
                    add(stringResource(R.string.wb_auto) to false)
                    if (specs.supportsManualPostProcessing) {
                        add(formatKelvin(settings.wbKelvin) to true)
                    }
                },
                isSelected = { it == kelvin },
                onSelect = { wantKelvin ->
                    onChange {
                        it.copy(wbPreset = if (wantKelvin) WbPreset.KELVIN else WbPreset.AUTO)
                    }
                },
            )
        }
        if (settings.wbPreset == WbPreset.KELVIN) {
            PanelSlider(
                value = (settings.wbKelvin - 2000) / 8000f,
                onValueChange = { t ->
                    onChange { it.copy(wbKelvin = (2000 + t * 8000).toInt()) }
                },
                left = "2000K",
                right = "10000K",
            )
        }

        PanelSection(stringResource(R.string.group_aids))
        PanelRow(stringResource(R.string.panel_timer)) {
            PillGroup(
                options = TimerOption.entries.map {
                    (if (it == TimerOption.OFF) stringResource(R.string.mode_off) else "${it.seconds}s") to it
                },
                isSelected = { it == settings.timer },
                onSelect = { option -> onChange { it.copy(timer = option) } },
            )
        }
        PanelRow(stringResource(R.string.panel_overlays)) {
            PillGroup(
                options = listOf(
                    stringResource(R.string.panel_grid) to Overlay.GRID,
                    stringResource(R.string.panel_level) to Overlay.LEVEL,
                ),
                isSelected = {
                    when (it) {
                        Overlay.GRID -> settings.gridEnabled
                        Overlay.LEVEL -> settings.levelEnabled
                    }
                },
                onSelect = { overlay ->
                    onChange {
                        when (overlay) {
                            Overlay.GRID -> it.copy(gridEnabled = !it.gridEnabled)
                            Overlay.LEVEL -> it.copy(levelEnabled = !it.levelEnabled)
                        }
                    }
                },
            )
        }
    }
}

private enum class Overlay { GRID, LEVEL }

@Composable
private fun flashLabel(mode: FlashMode): String = stringResource(
    when (mode) {
        FlashMode.OFF -> R.string.mode_off
        FlashMode.AUTO -> R.string.toggle_auto
        FlashMode.ON -> R.string.flash_on
        FlashMode.TORCH -> R.string.flash_torch
    }
)

@Composable
private fun PanelSection(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = title,
        color = CameraPalette.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PanelRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = CameraPalette.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.width(72.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { content() }
    }
}

@Composable
private fun <T> PillGroup(
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CameraPalette.CreamDim)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val selected = isSelected(value)
            Text(
                text = label,
                color = if (selected) CameraPalette.TextPrimary else CameraPalette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) CameraPalette.Surface else CameraPalette.CreamDim)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CameraPalette.CreamDim)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", enabled, onDecrease)
        Text(
            text = value,
            color = if (enabled) CameraPalette.TextPrimary else CameraPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CameraPalette.Surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        StepperButton("+", enabled, onIncrease)
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = if (enabled) CameraPalette.TextSecondary else CameraPalette.Divider,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PanelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    left: String,
    right: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = CameraPalette.Accent,
                activeTrackColor = CameraPalette.Accent,
                inactiveTrackColor = CameraPalette.Divider,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(left, color = CameraPalette.TextSecondary, fontSize = 11.sp)
            Text(right, color = CameraPalette.TextSecondary, fontSize = 11.sp)
        }
    }
}
