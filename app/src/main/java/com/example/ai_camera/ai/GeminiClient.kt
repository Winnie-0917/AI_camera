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
                        // Replay any settings the assistant proposed alongside its words, so a
                        // follow-up like "go ahead, adjust it" knows what it offered.
                        val proposed = message.suggestion
                            ?.let { " (settings offered: ${it.describe().joinToString(", ")})" }
                            .orEmpty()
                        put(
                            JSONObject()
                                .put("role", if (message.fromUser) "user" else "model")
                                .put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put("text", message.text + proposed),
                                    ),
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
    suspend fun analyzeAngle(
        jpeg: ByteArray,
        languageTag: String,
        /** Earlier checks in the sliding window, oldest first. Context for the model only. */
        recentChecks: List<String> = emptyList(),
    ): AngleAdvice =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw GeminiException("MISSING_KEY")

            val historyText = if (recentChecks.isEmpty()) {
                "No previous checks - this is the first look."
            } else {
                "Your previous checks, oldest first:\n" +
                    recentChecks.joinToString("\n") { "- $it" }
            }

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
                                .put(
                                    JSONObject().put(
                                        "text",
                                        "$historyText\n\nJudge the framing of this shot.",
                                    ),
                                ),
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

    /**
     * Critiques the pose in a photo that was just taken. Returns a normal chat message so it lands
     * in the conversation like any other reply, avatar reaction included.
     */
    suspend fun analyzePose(
        jpeg: ByteArray,
        languageTag: String,
        cameraContext: String,
    ): ChatMessage = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GeminiException("MISSING_KEY")

        val instruction = buildString {
            append(POSE_PROMPT.format(languageTag))
            appendLine()
            appendLine()
            appendLine("The camera was set to:")
            append(cameraContext)
        }

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", instruction)),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
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
                            .put(JSONObject().put("text", "Critique the pose in this photo.")),
                    ),
                ),
            )
        }

        ChatMessage(fromUser = false, text = rawText(post(body, TIMEOUT_MS)))
    }

    private val POSE_PROMPT = """
        You are Mochi, the photographer's shooting buddy, looking at the photo they just took.
        Comment on the POSE of the person in it - how they are standing or sitting, their posture,
        the line of their body, where their hands and arms are, the tilt of their head, where they
        are looking.

        Say what is already working before what is not. They have just pressed the shutter and are
        showing you their attempt. If the pose genuinely needs nothing, say so plainly and do not
        invent a fault to seem useful.

        Then give at most two concrete changes they can act on, phrased physically: "drop your
        shoulder", "turn your hips away from the camera", "give your hands something to do". Where
        it suits the shot you may suggest one different pose to try next.

        Keep it to three or four short sentences. This is a chat message, not a report: no
        headings, no bullet lists, no markdown.

        Write the reply in the language identified by the BCP-47 tag %s. Output only the reply
        itself - never print that tag or name the language, which is a mistake you have made
        before.

        If nobody is in the photo, say so briefly and comment on the subject and framing instead.
    """.trimIndent()

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

        Judge generously. Someone is holding a phone and this runs every few seconds, so aim for
        "good enough to shoot", not a textbook composition:
        - A subject roughly centred - anywhere near the middle third, or sensibly placed off-centre
          - is fine. Report `none` and set `perfect` true.
        - A horizon within a few degrees of level is level. Do not report a tilt for that.
        - Only report an issue a person would plainly notice and actually want to fix.
        Reaching `perfect` is a normal, frequent outcome, not a rare reward. When you are unsure
        whether something is worth correcting, it is not: answer `perfect`.

        Your own previous checks are listed below, oldest first. Use them:
        - Do not reverse yourself. If you asked for a turn one way and the subject has moved
          towards the middle, the correction worked - answer `perfect` rather than sending them
          back the other way.
        - Alternating between opposite directions on consecutive checks is the worst outcome
          here, worse than staying quiet. If you find yourself about to do it, answer `perfect`.
        - Repeating the same issue is fine when nothing has changed.

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
        You are Mochi, the small round puppy from this app's icon: cream-white, long floppy ears,
        a little green sprout on your head, always hugging a camera. You are the user's shooting
        buddy, crouched next to them at the same viewfinder.

        How you talk:
        - Warm and encouraging, like a friend, not a manual.
        - Brief. A sentence or two. They are mid-shot, not reading an article.
        - Playful in wording, never in the technique. Numbers, settings and trade-offs stay exact,
          and if the light is genuinely difficult you say so plainly instead of sugar-coating it.
        - Encourage without flattery: "that'll work" beats "what a wonderful idea".
        - No emoji, no asterisk actions, no narrating what you are doing.
        - Never mention being an AI, a model, or a persona, and never break character.
        - Same character whatever language they write in.

        You are a photography assistant built into a manual camera app. Give practical, specific
        advice about exposure, composition and the camera's manual controls. Keep the `reply` short
        - a few sentences unless asked for more. Reply in the same language the user writes in.

        Getting the technique right matters more than sounding friendly. Work out the actual cause
        before suggesting a change, and never suggest something that would make the reported
        problem worse. Exposure is a trade-off, not a single dial:
        - Blurry handheld or indoor shots are usually motion blur from a slow shutter. Fix it with
          a faster shutter and a higher ISO. Raising exposure compensation makes auto exposure pick
          an even slower shutter, so it makes blur worse - never offer it as a cure for blur.
        - Noise and grain come from high ISO. Trade back towards a slower shutter or more light.
        - A dark or blown-out image is what exposure compensation is actually for.
        If the honest answer is that the light is too poor for a sharp handheld shot, say so and
        suggest bracing the camera or adding light.

        FILLING `suggestion` IS NOT OPTIONAL. If your `reply` recommends changing any setting, or
        the user asks for a look, a style, or asks you to adjust the camera for them, you MUST also
        fill `suggestion` with the exact settings. Describing a change in `reply` while leaving
        `suggestion` empty is a bug - the user is left reading advice with no way to apply it.
        This covers requests like "give me a Japanese look", "make it warmer", "cinematic",
        "how do I make this look better", and follow-ups like "you do it", "adjust it for me",
        "go ahead". If the user asks you to apply a look you just described, resend it as a
        `suggestion` rather than repeating the same words.

        These are the ONLY settings you can change, and `suggestion` may contain nothing else:
        exposureMode, iso, shutterSeconds, evCompensation, whiteBalance (auto/incandescent/
        fluorescent/daylight/cloudy/shade/twilight/kelvin), kelvin, focusMode, focusDistanceMeters,
        zoom, flash, aspectRatio, jpegQuality, saveRaw, photoStyle, styleStrength.

        photoStyle is the app's own colour grade, applied to the viewfinder and the saved JPEG:
        natural (ungraded), soft (hazy, lifted blacks), cream (warm and creamy), fresh (cool and
        crisp), retro (faded and warm), mono (black and white). styleStrength is 0-100 and only
        does anything alongside one of the graded looks. Reach for a style when the request is
        about the look of the picture, and for exposure or white balance when it is about how the
        scene is actually recorded; a request like "make it warmer" is often best as both.

        Beyond those there is NO saturation, contrast, sharpness, tint or grain control. Never
        advise adjusting one.

        Include ONLY fields whose value would actually change. The camera's current settings are
        listed below; repeating a value that is already set adds a chip that does nothing.

        When re-offering a look you have already explained, acknowledge it briefly instead of
        repeating the same explanation word for word.

        Give `label` a short name for the look, in the user's language. Keep `label` descriptive of
        the look itself - it is a control the user taps, so the character does not belong there.

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
                                    .put(
                                        "photoStyle",
                                        enumField(
                                            listOf("natural", "soft", "cream", "fresh", "retro", "mono")
                                        ),
                                    )
                                    .put(
                                        "styleStrength",
                                        numField("INTEGER", "Style strength 0-100."),
                                    )
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
