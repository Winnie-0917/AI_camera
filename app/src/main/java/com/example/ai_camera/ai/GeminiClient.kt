package com.example.ai_camera.ai

import android.util.Base64
import com.example.ai_camera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val suggestion: StyleSuggestion? = null,
)

class GeminiException(message: String) : Exception(message)

/**
 * Minimal Gemini `generateContent` client.
 *
 * Uses HttpURLConnection + org.json rather than pulling in OkHttp/Retrofit: this is a single
 * JSON POST and the app otherwise has no networking stack.
 */
object GeminiClient {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val TIMEOUT_MS = 90_000

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
    ): ChatMessage = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GeminiException("MISSING_KEY")

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt(cameraContext))),
                ),
            )
            // Structured output: the prose and the machine-applicable settings come back as
            // separate fields, so the Apply button never depends on parsing free text.
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", RESPONSE_SCHEMA)
                    .apply {
                        // 2.5 models think before answering, which combined with JSON mode pushes
                        // latency past a comfortable wait for a quick camera tip. Flash accepts a
                        // zero budget to turn it off; Pro requires >= 128, so leave Pro alone.
                        if (model.contains("flash", ignoreCase = true)) {
                            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                        }
                    },
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

        parseReply(post(body, TIMEOUT_MS))
    }

    /** Shared HTTP POST to generateContent. */
    private fun post(body: JSONObject, timeoutMs: Int): String {
        val connection = URL("$ENDPOINT/$model:generateContent").openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
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
            return response
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Judges framing from a viewfinder frame. Kept on a short timeout because this runs on a
     * repeating timer - a slow reply is better dropped than queued behind the next one.
     */
    suspend fun analyzeAngle(jpeg: ByteArray, languageTag: String): AngleAdvice =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw GeminiException("MISSING_KEY")

            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", ANGLE_PROMPT.format(languageTag))),
                    ),
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", ANGLE_SCHEMA)
                        .apply {
                            if (model.contains("flash", ignoreCase = true)) {
                                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                            }
                        },
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "parts",
                            JSONArray()
                                .put(
                                    JSONObject().put(
                                        "inline_data",
                                        JSONObject()
                                            .put("mime_type", "image/jpeg")
                                            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)),
                                    ),
                                )
                                .put(JSONObject().put("text", "Judge the framing of this shot.")),
                        ),
                    ),
                )
            }

            val payload = JSONObject(rawText(post(body, ANGLE_TIMEOUT_MS)))
            AngleAdvice(
                perfect = payload.optBoolean("perfect"),
                issue = AngleIssue.fromTag(payload.optString("issue")),
                note = payload.optString("note").trim(),
            )
        }

    /** Pulls the model's text part out of a generateContent response. */
    private fun rawText(response: String): String {
        val candidates = JSONObject(response).optJSONArray("candidates")
            ?: throw GeminiException("Empty response")
        if (candidates.length() == 0) throw GeminiException("Empty response")
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw GeminiException("Empty response")
        return buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }.trim().ifBlank { throw GeminiException("Empty response") }
    }

    private const val ANGLE_TIMEOUT_MS = 20_000

    private val ANGLE_PROMPT = """
        You judge the framing of a photo from the live viewfinder frame you are given.

        Report ONLY what you observe about the image. Do NOT say what the photographer should do
        and do not mention moving the camera - the app works out the correction itself. Getting
        this wrong is the usual failure: describe where the problem IS, not how to fix it.

        Pick the single most important `issue`:
          subject_left / subject_right  - the main subject sits left / right of centre
          subject_high / subject_low    - it sits too high / too low, or the horizon does
          tilted_clockwise              - the scene looks rotated clockwise (horizon drops right)
          tilted_counter_clockwise      - the scene looks rotated anti-clockwise
          too_close / too_far           - the subject fills too much / too little of the frame
          none                          - the framing is good

        Set `perfect` to true only when the framing genuinely needs no change, and then use
        issue `none`.

        `note` is a very short description of what you see, at most about 6 words, e.g. "horizon
        is high" or "subject near the left edge". Never phrase it as an instruction. Write `note`
        in the language with BCP-47 tag %s.
    """.trimIndent()

    private val ANGLE_SCHEMA: JSONObject
        get() = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("perfect", JSONObject().put("type", "BOOLEAN"))
                    .put(
                        "issue",
                        JSONObject()
                            .put("type", "STRING")
                            .put("enum", JSONArray(AngleIssue.entries.map { it.tag })),
                    )
                    .put(
                        "note",
                        strField("Very short description of what is wrong, in the requested language."),
                    ),
            )
            .put("required", JSONArray().put("perfect").put("issue").put("note"))

    private fun systemPrompt(cameraContext: String) = """
        You are a photography assistant built into a manual camera app. Give practical, specific
        advice about exposure, composition and the camera's manual controls. Keep the `reply` short
        - a few sentences unless asked for more. Reply in the same language the user writes in.

        Whenever the user asks for a look or style ("make this warmer", "cinematic", "night shot",
        "how do I make this look better"), ALSO fill in `suggestion` with the concrete parameters
        that achieve it. Only include the fields you actually want to change, and give `label` a
        short name for the look, in the user's language.

        Never suggest something this camera cannot do - the capability list below tells you what is
        supported. If manual exposure is unsupported, adjust exposure compensation instead. Do not
        put the parameter values in `reply` as a list; the app displays `suggestion` as a card with
        an Apply button, so `reply` should read as an explanation of why.

        The user's camera is currently set to:
        $cameraContext
    """.trimIndent()

    private val RESPONSE_SCHEMA: JSONObject
        get() = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "reply",
                        strField("Conversational answer in the user's language."),
                    )
                    .put(
                        "suggestion",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("nullable", true)
                            .put(
                                "description",
                                "Camera settings that achieve the requested look. Omit entirely " +
                                    "when the user is not asking for a look or style.",
                            )
                            .put(
                                "properties",
                                JSONObject()
                                    .put("label", strField("Short name for the look."))
                                    .put("exposureMode", enumField(listOf("auto", "manual")))
                                    .put("iso", numField("INTEGER", "Sensor sensitivity."))
                                    .put(
                                        "shutterSeconds",
                                        numField("NUMBER", "Exposure time in seconds, e.g. 0.002 for 1/500."),
                                    )
                                    .put("evCompensation", numField("NUMBER", "Exposure compensation in EV."))
                                    .put(
                                        "whiteBalance",
                                        enumField(
                                            listOf(
                                                "auto", "incandescent", "fluorescent", "daylight",
                                                "cloudy", "shade", "twilight", "kelvin",
                                            ),
                                        ),
                                    )
                                    .put("kelvin", numField("INTEGER", "Colour temperature, 2000-10000, when whiteBalance is kelvin."))
                                    .put("focusMode", enumField(listOf("auto", "manual")))
                                    .put(
                                        "focusDistanceMeters",
                                        numField("NUMBER", "Focus distance in metres; 0 means infinity."),
                                    )
                                    .put("zoom", numField("NUMBER", "Zoom ratio, 1.0 = no zoom."))
                                    .put("flash", enumField(listOf("off", "auto", "on", "torch")))
                                    .put("aspectRatio", enumField(listOf("full", "4:3", "16:9", "1:1")))
                                    .put("jpegQuality", numField("INTEGER", "JPEG quality 50-100."))
                                    .put("saveRaw", JSONObject().put("type", "BOOLEAN"))
                            )
                            .put("required", JSONArray().put("label")),
                    ),
            )
            .put("required", JSONArray().put("reply"))

    private fun strField(description: String) =
        JSONObject().put("type", "STRING").put("description", description)

    private fun numField(type: String, description: String) =
        JSONObject().put("type", type).put("description", description)

    private fun enumField(values: List<String>) = JSONObject()
        .put("type", "STRING")
        .put("enum", JSONArray(values))

    private fun parseReply(response: String): ChatMessage {
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

        val raw = buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }.trim().ifBlank { throw GeminiException("Empty response") }

        // The schema guarantees JSON, but fall back to showing the raw text rather than failing
        // outright if a model ever returns something else.
        val payload = runCatching { JSONObject(raw) }.getOrNull()
            ?: return ChatMessage(fromUser = false, text = raw)

        val text = payload.optString("reply").ifBlank { raw }
        val suggestion = StyleSuggestion.fromJson(payload.optJSONObject("suggestion"))
        return ChatMessage(fromUser = false, text = text, suggestion = suggestion)
    }

    private fun parseError(response: String, status: Int): String {
        val message = runCatching {
            JSONObject(response).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return if (message.isNullOrBlank()) "HTTP $status" else "$message (HTTP $status)"
    }
}
