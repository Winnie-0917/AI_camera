package com.example.ai_camera.ai

import com.example.ai_camera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val fromUser: Boolean, val text: String)

class GeminiException(message: String) : Exception(message)

/**
 * Minimal Gemini `generateContent` client.
 *
 * Uses HttpURLConnection + org.json rather than pulling in OkHttp/Retrofit: this is a single
 * JSON POST and the app otherwise has no networking stack.
 */
object GeminiClient {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val TIMEOUT_MS = 60_000

    val apiKey: String get() = BuildConfig.GEMINI_API_KEY
    val model: String get() = BuildConfig.GEMINI_MODEL
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * @param history prior turns, oldest first, excluding [prompt].
     * @param cameraContext current camera state, injected so answers can reference real settings.
     */
    suspend fun send(
        prompt: String,
        history: List<ChatMessage>,
        cameraContext: String,
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GeminiException("MISSING_KEY")

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt(cameraContext))),
                ),
            )
            put(
                "contents",
                JSONArray().apply {
                    history.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", if (message.fromUser) "user" else "model")
                                .put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("text", message.text)),
                                ),
                        )
                    }
                    put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                    )
                },
            )
        }

        val connection = (URL("$ENDPOINT/$model:generateContent").openConnection() as HttpURLConnection)
        connection.apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            // Header rather than a ?key= query param, so the key stays out of URLs and logs.
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status !in 200..299) throw GeminiException(parseError(response, status))
            parseReply(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun systemPrompt(cameraContext: String) = """
        You are a photography assistant built into a manual camera app. Give practical, specific
        advice about exposure, composition and the camera's manual controls. Prefer concrete
        settings over generalities, and keep answers short - a few sentences unless asked for more.
        Reply in the same language the user writes in.

        The user's camera is currently set to:
        $cameraContext
    """.trimIndent()

    private fun parseReply(response: String): String {
        val json = JSONObject(response)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            // A prompt blocked by safety filters comes back with no candidates.
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason")
            throw GeminiException(if (reason.isNullOrBlank()) "Empty response" else "Blocked: $reason")
        }
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw GeminiException("Empty response")

        return buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }.trim().ifBlank { throw GeminiException("Empty response") }
    }

    private fun parseError(response: String, status: Int): String {
        val message = runCatching {
            JSONObject(response).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return if (message.isNullOrBlank()) "HTTP $status" else "$message (HTTP $status)"
    }
}
