package com.example.ai_camera.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_camera.R
import com.example.ai_camera.camera.PhotoStyle

/**
 * The style picker. Every tile is the mascot with that grade applied, so the six looks are told
 * apart by the same subject rather than by whatever the camera happened to be pointing at.
 *
 * Drawn straight into the camera screen rather than in a Dialog. A dialog window here sat inside
 * the system bars while still reporting the whole screen as its height, and reported no insets of
 * its own, so no arithmetic from inside it could reliably say where the bottom of the screen was -
 * and the Done button kept ending up under it.
 *
 * The six tiles are sized by what is left over rather than by their own content, so the whole
 * picker always fits on one screen and never has to be scrolled to reach Done.
 */
@Composable
fun StyleSheet(
    current: PhotoStyle,
    strength: Int,
    onSelect: (PhotoStyle) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val demo = rememberDemoImage()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraPalette.Surface)
            // Swallows taps so nothing reaches the viewfinder underneath.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
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

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PhotoStyle.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { style ->
                        StyleTile(
                            style = style,
                            demo = demo,
                            // The tile shows the look at the strength it would be applied at.
                            strength = strength,
                            selected = style == current,
                            onClick = { onSelect(style) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
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
            Spacer(Modifier.height(4.dp))
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
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Decoded once and held for the life of the composition; it is a 480px JPEG in assets. */
@Composable
private fun rememberDemoImage(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.assets.open(DEMO_ASSET).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }
}

private const val DEMO_ASSET = "Filter_icon.jpg"

@Composable
private fun StyleTile(
    style: PhotoStyle,
    demo: ImageBitmap?,
    strength: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = remember(style, strength) {
        if (style.isNoOp(strength)) null
        else ColorFilter.colorMatrix(ColorMatrix(style.matrixFor(strength)))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CameraPalette.CreamDim)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) CameraPalette.Accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Takes whatever the row has left after the label, so the tile shrinks to fit the
                // screen instead of the six of them deciding how tall the picker is.
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(CameraPalette.Cream),
        ) {
            if (demo != null) {
                Image(
                    bitmap = demo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
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
                .padding(vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}
