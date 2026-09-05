package com.example.ai_camera.emotion

/**
 * The seven classes the bundled GIFs correspond to, matching the label ids the BERT model in
 * `emotion_model/` was trained with. [NEUTRAL] doubles as the idle face shown when nothing is
 * being said.
 */
enum class Emotion(val id: Int) {
    HAPPY(0),
    AFRAID(1),
    ANGRY(2),
    SAD(3),
    CURIOUS(4),
    SURPRISED(5),
    NEUTRAL(6);

    /** Assets are named by label id: `icon_gif/0.gif` … `icon_gif/6.gif`. */
    val assetPath: String get() = "file:///android_asset/icon_gif/$id.gif"

    companion object {
        val IDLE = NEUTRAL

        fun fromId(id: Int): Emotion = entries.firstOrNull { it.id == id } ?: NEUTRAL
    }
}

/**
 * Picks the expression for a chunk of the reply.
 *
 * Suspending because the intended implementation is the fine-tuned BERT model converted to
 * TFLite, which must not run on the main thread. [KeywordEmotionClassifier] stands in until that
 * conversion lands.
 */
interface EmotionClassifier {
    suspend fun classify(text: String): Emotion
}

/**
 * Placeholder classifier: matches emotion words directly.
 *
 * This is deliberately crude and is not the intended behaviour - it cannot read context, so
 * "不是生氣" reads as anger, which is precisely the weakness the BERT model was trained to fix.
 * It exists so the avatar is demonstrable before the model is converted, and should be swapped
 * out wholesale rather than extended.
 */
class KeywordEmotionClassifier : EmotionClassifier {
    private val cues = listOf(
        Emotion.HAPPY to listOf(
            "開心", "太好", "很棒", "漂亮", "美", "讚", "喜歡", "完美", "不錯", "成功",
            "happy", "great", "nice", "perfect", "lovely",
        ),
        Emotion.AFRAID to listOf(
            "小心", "注意", "危險", "糟", "怕", "擔心", "careful", "warning", "risk",
        ),
        Emotion.ANGRY to listOf(
            "不行", "錯誤", "失敗", "問題", "別", "不要", "wrong", "error", "fail",
        ),
        Emotion.SAD to listOf(
            "可惜", "抱歉", "遺憾", "太暗", "模糊", "沒辦法", "unfortunately", "sorry", "blurry",
        ),
        Emotion.CURIOUS to listOf(
            "試試", "建議", "可以", "或許", "想", "？", "?", "try", "maybe", "suggest",
        ),
        Emotion.SURPRISED to listOf(
            "哇", "居然", "竟然", "驚", "wow", "amazing",
        ),
    )

    /**
     * Scores every emotion by how many of its cues appear and takes the strongest, rather than
     * the first that matches at all. A chunk like "光線也不錯。可惜快門太慢了，照片會模糊。"
     * carries one positive cue against two negative ones, and first-match ordering would call
     * that happy purely because happiness is checked first.
     */
    override suspend fun classify(text: String): Emotion {
        val best = cues
            .map { (emotion, words) ->
                emotion to words.count { text.contains(it, ignoreCase = true) }
            }
            .filter { (_, hits) -> hits > 0 }
            .maxByOrNull { (_, hits) -> hits }
        return best?.first ?: Emotion.NEUTRAL
    }
}
