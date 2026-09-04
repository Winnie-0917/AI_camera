package com.example.ai_camera.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai_camera.R
import com.example.ai_camera.camera.CameraSpecs
import com.example.ai_camera.camera.CaptureSettings
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFFD60A)
private val Panel = Color(0xFF1C1C1E)

/**
 * @param cameraContext a short description of the current camera state, sent with every prompt.
 */
@Composable
fun AiAssistantSheet(
    cameraContext: String,
    specs: CameraSpecs?,
    /** Hoisted so the conversation and the undo affordance survive closing the window. */
    messages: SnapshotStateList<ChatMessage>,
    appliedSuggestion: StyleSuggestion?,
    onApply: (StyleSuggestion) -> StyleSuggestion.Applied,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var appliedNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val missingKeyMessage = stringResource(R.string.ai_no_key)
    val revertedMessage = stringResource(R.string.ai_reverted)

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || loading) return
        val history = messages.toList()
        messages += ChatMessage(fromUser = true, text = prompt)
        input = ""
        error = null
        appliedNotice = null
        loading = true
        scope.launch {
            try {
                messages += GeminiClient.send(prompt, history, cameraContext)
            } catch (e: Exception) {
                error = if (e is GeminiException && e.message == "MISSING_KEY") {
                    missingKeyMessage
                } else {
                    e.message ?: e::class.java.simpleName
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .imePadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ai_title),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_close),
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_empty),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages) { message ->
                            Column {
                                MessageBubble(message)
                                message.suggestion?.let { suggestion ->
                                    Spacer(Modifier.height(6.dp))
                                    SuggestionCard(
                                        suggestion = suggestion,
                                        enabled = specs != null,
                                        isApplied = suggestion == appliedSuggestion,
                                        onApply = {
                                            appliedNotice = buildNotice(onApply(suggestion))
                                        },
                                        onRevert = {
                                            onRevert()
                                            appliedNotice = revertedMessage
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            error?.let { text ->
                Text(
                    text = text,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            appliedNotice?.let { text ->
                Text(
                    text = text,
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.ai_hint),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Accent,
                    ),
                )
                Spacer(Modifier.size(8.dp))
                if (loading) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    IconButton(onClick = { send() }) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.ai_send),
                            tint = if (input.isBlank()) Color.White.copy(alpha = 0.3f) else Accent,
                        )
                    }
                }
            }
        }
    }
}

/** The proposed settings, with a one-tap Apply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(
    suggestion: StyleSuggestion,
    enabled: Boolean,
    isApplied: Boolean,
    onApply: () -> Unit,
    onRevert: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Text(
            text = suggestion.label,
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestion.describe().forEach { item ->
                Text(
                    text = item,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(if (isApplied) R.string.ai_revert else R.string.ai_apply),
            color = if (isApplied) Accent else Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isApplied) Color.White.copy(alpha = 0.12f) else Accent)
                .clickable(enabled = enabled) { if (isApplied) onRevert() else onApply() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun buildNotice(result: StyleSuggestion.Applied): String {
    val applied = result.applied.joinToString(" · ")
    val unsupported = "this camera does not support: ${result.skipped.joinToString(", ")}"
    return when {
        result.skipped.isEmpty() -> applied
        result.applied.isEmpty() -> unsupported.replaceFirstChar { it.uppercase() }
        else -> "$applied — $unsupported"
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.text,
            color = if (message.fromUser) Color.Black else Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (message.fromUser) Accent else Color.White.copy(alpha = 0.1f)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
