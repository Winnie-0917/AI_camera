package com.example.ai_camera.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai_camera.R
import com.example.ai_camera.camera.PhotoStyle

/**
 * The style picker.
 *
 * Every tile is the viewfinder as it looks right now with that grade applied, rather than a canned
 * sample image - the point of choosing a look is seeing it on the scene in front of you. The
 * snapshot is taken once when the sheet opens; a live feed in six tiles would cost six more
 * streams for no real gain.
 */
@Composable
fun StyleSheet(
    current: PhotoStyle,
    strength: Int,
    /** A frame grabbed from the viewfinder, already oriented the way the user sees it. */
    preview: Bitmap?,
    /**
     * How tall the sheet may be. Passed in because a dialog window here is positioned inside the
     * system bars yet still reports the whole screen as its height, and reports no insets of its
     * own - so measured from inside, the sheet believes it has 100dp more room than it does and
     * runs the Done button off the bottom edge.
     */
    availableHeight: Dp,
    onSelect: (PhotoStyle) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(availableHeight)
                .background(CameraPalette.Surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.settings_close),
                    tint = CameraPalette.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                )
                Text(
                    text = stringResource(R.string.style_title),
                    color = CameraPalette.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PhotoStyle.entries) { style ->
                    StyleTile(
                        style = style,
                        preview = preview,
                        // The tile shows the look at the strength it would actually be applied.
                        strength = strength,
                        selected = style == current,
                        onClick = { onSelect(style) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.style_strength),
                        color = CameraPalette.TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$strength",
                        color = CameraPalette.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Slider(
                    value = strength.toFloat(),
                    onValueChange = { onStrengthChange(it.toInt()) },
                    valueRange = 0f..100f,
                    // Nothing to dial back on the ungraded look.
                    enabled = current != PhotoStyle.NATURAL,
                    colors = SliderDefaults.colors(
                        thumbColor = CameraPalette.Surface,
                        activeTrackColor = CameraPalette.Accent,
                        inactiveTrackColor = CameraPalette.Divider,
                        disabledThumbColor = CameraPalette.Divider,
                        disabledActiveTrackColor = CameraPalette.Divider,
                        disabledInactiveTrackColor = CameraPalette.Divider,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.style_done),
                    color = CameraPalette.Surface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CameraPalette.Taupe)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StyleTile(
    style: PhotoStyle,
    preview: Bitmap?,
    strength: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val image = remember(preview) { preview?.asImageBitmap() }
    val filter = remember(style, strength) {
        if (style.isNoOp(strength)) null
        else ColorFilter.colorMatrix(ColorMatrix(style.matrixFor(strength)))
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CameraPalette.CreamDim)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) CameraPalette.Accent else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(CameraPalette.Cream),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = stringResource(style.labelRes),
            color = if (selected) CameraPalette.AccentDeep else CameraPalette.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
