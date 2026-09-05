package com.example.ai_camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ai_camera.R
import com.example.ai_camera.camera.AspectRatioOption
import com.example.ai_camera.camera.CaptureSettings
import com.example.ai_camera.camera.FlashMode
import com.example.ai_camera.camera.TimerOption

/**
 * The quick tools that used to sit as a row of icons across the top of the viewfinder.
 *
 * They moved behind one button because the icons alone never said what state they were in - an
 * outlined flash bolt could mean off or auto - whereas a labelled row shows the current setting
 * without being decoded first.
 */
@Composable
fun ToolsSheet(
    state: CameraUiState,
    onChange: ((CaptureSettings) -> CaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val specs = state.specs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(CameraPalette.Surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        PanelSection(stringResource(R.string.group_tools))

        if (specs?.hasFlash == true) {
            PanelRow(stringResource(R.string.panel_flash)) {
                PillGroup(
                    options = FlashMode.entries.map { flashLabel(it) to it },
                    isSelected = { it == settings.flashMode },
                    onSelect = { mode -> onChange { it.copy(flashMode = mode) } },
                )
            }
        }

        PanelRow(stringResource(R.string.panel_timer)) {
            PillGroup(
                options = TimerOption.entries.map {
                    (if (it == TimerOption.OFF) stringResource(R.string.mode_off) else "${it.seconds}s") to it
                },
                isSelected = { it == settings.timer },
                onSelect = { option -> onChange { it.copy(timer = option) } },
            )
        }

        PanelRow(stringResource(R.string.panel_aspect)) {
            PillGroup(
                options = AspectRatioOption.entries.map { stringResource(it.labelRes) to it },
                isSelected = { it == settings.aspectRatio },
                onSelect = { ratio -> onChange { it.copy(aspectRatio = ratio) } },
            )
        }

        PanelSection(stringResource(R.string.group_aids))

        PanelRow(stringResource(R.string.panel_grid)) {
            PillGroup(
                options = onOffOptions(),
                isSelected = { it == settings.gridEnabled },
                onSelect = { on -> onChange { it.copy(gridEnabled = on) } },
            )
        }

        PanelRow(stringResource(R.string.panel_level)) {
            PillGroup(
                options = onOffOptions(),
                isSelected = { it == settings.levelEnabled },
                onSelect = { on -> onChange { it.copy(levelEnabled = on) } },
            )
        }

        // Needs the analysis stream, which not every lens can spare.
        if (specs?.supportsAnalysisStream == true) {
            PanelRow(stringResource(R.string.panel_histogram)) {
                PillGroup(
                    options = onOffOptions(),
                    isSelected = { it == settings.histogramEnabled },
                    onSelect = { on -> onChange { it.copy(histogramEnabled = on) } },
                )
            }
        }
    }
}

@Composable
private fun onOffOptions(): List<Pair<String, Boolean>> = listOf(
    stringResource(R.string.mode_off) to false,
    stringResource(R.string.flash_on) to true,
)
