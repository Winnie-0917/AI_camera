package com.example.ai_camera.emotion

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * Runs the fine-tuned Chinese BERT on-device.
 *
 * The checkpoint is 409MB in fp32, so what ships is a dynamically int8-quantized ONNX export
 * (~103MB, produced by `emotion_model/export_onnx.py`). ONNX Runtime rather than TFLite because
 * transformers 5 dropped TensorFlow, leaving no first-party path to a `.tflite` from this
 * checkpoint; the remaining ONNX->TF converters are an extra lossy hop for no gain here.
 *
 * Everything is lazy: the session costs well over a hundred megabytes of mapped file, so it is
 * only built the first time an emotion is actually needed, and never on the main thread.
 */
class BertEmotionClassifier(
    private val context: Context,
    private val fallback: EmotionClassifier = KeywordEmotionClassifier(),
) : EmotionClassifier {

    private val loadLock = Mutex()
    private var session: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null
    private var unavailable = false

    override suspend fun classify(text: String): Emotion = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext Emotion.NEUTRAL
        val ready = ensureLoaded()
        if (!ready) return@withContext fallback.classify(text)

        runCatching { infer(text) }
            .getOrElse { error ->
                Log.w(TAG, "inference failed, falling back", error)
                fallback.classify(text)
            }
    }

    private suspend fun ensureLoaded(): Boolean {
        if (session != null) return true
        if (unavailable) return false
        return loadLock.withLock {
            if (session != null) return@withLock true
            if (unavailable) return@withLock false
            runCatching { load() }
                .onFailure { error ->
                    // A missing or corrupt model must not take the assistant down with it; the
                    // keyword classifier keeps the avatar working.
                    Log.w(TAG, "could not load emotion model", error)
                    unavailable = true
                }
                .isSuccess
        }
    }

    private fun load() {
        tokenizer = context.assets.open(VOCAB_ASSET).bufferedReader().useLines {
            WordPieceTokenizer.fromVocabLines(it)
        }

        // ONNX Runtime needs a real file, and assets are not one, so the model is copied out once
        // and reused. The copy is refreshed whenever the bundled asset differs in size: without
        // that check an app update ships a new model but keeps running the old copy forever,
        // which fails silently as slightly wrong predictions.
        val modelFile = File(context.filesDir, MODEL_FILE)
        val bundledSize = context.assets.openFd(MODEL_ASSET).use { it.length }
        if (modelFile.length() != bundledSize) {
            context.assets.open(MODEL_ASSET).use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "copied model out of assets (${bundledSize / 1_000_000}MB)")
        }

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = OrtEnvironment.getEnvironment().createSession(modelFile.absolutePath, options)
        Log.i(TAG, "emotion model ready (${modelFile.length() / 1_000_000}MB)")
    }

    private fun infer(text: String): Emotion {
        val session = session ?: return Emotion.NEUTRAL
        val tokenizer = tokenizer ?: return Emotion.NEUTRAL
        val encoded = tokenizer.encode(text, MAX_LEN)
        val env = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, MAX_LEN.toLong())

        val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.inputIds), shape)
        val attentionMask = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.attentionMask), shape)
        val tokenTypeIds = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.tokenTypeIds), shape)

        return try {
            val inputs = mapOf(
                "input_ids" to inputIds,
                "attention_mask" to attentionMask,
                "token_type_ids" to tokenTypeIds,
            )
            session.run(inputs).use { results ->
                @Suppress("UNCHECKED_CAST")
                val logits = (results[0].value as Array<FloatArray>)[0]
                Emotion.fromId(logits.indices.maxByOrNull { logits[it] } ?: Emotion.NEUTRAL.id)
            }
        } finally {
            inputIds.close()
            attentionMask.close()
            tokenTypeIds.close()
        }
    }

    companion object {
        private const val TAG = "BertEmotion"
        private const val MAX_LEN = 64
        private const val MODEL_ASSET = "emotion/emotion_int8.onnx"
        private const val VOCAB_ASSET = "emotion/vocab.txt"
        private const val MODEL_FILE = "emotion_int8.onnx"
    }
}
