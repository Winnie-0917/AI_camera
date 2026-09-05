package com.example.ai_camera.emotion

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** How long one chunk is held, scaled by its length so longer lines linger. */
private const val MS_PER_CHAR = 90L
private const val MIN_HOLD_MS = 1_200L
private const val MAX_HOLD_MS = 4_000L

internal fun holdDurationFor(text: String): Long =
    (text.length * MS_PER_CHAR).coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)

/**
 * The assistant's face. It plays through [reply] chunk by chunk, showing the expression each one
 * classifies to, then settles back to the idle face.
 *
 * @param reply the assistant's latest message, or null when there is nothing to react to.
 */
@Composable
fun EmotionAvatar(
    reply: String?,
    classifier: EmotionClassifier,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    var emotion by remember { mutableStateOf(Emotion.IDLE) }

    LaunchedEffect(reply) {
        val chunks = SentenceSplitter.split(reply.orEmpty())
        if (chunks.isEmpty()) {
            emotion = Emotion.IDLE
            return@LaunchedEffect
        }
        for (chunk in chunks) {
            emotion = classifier.classify(chunk)
            delay(holdDurationFor(chunk))
        }
        emotion = Emotion.IDLE
    }

    val context = LocalContext.current
    // GIFs need a decoder registered explicitly; Coil does not include one by default.
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(emotion.assetPath)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}
