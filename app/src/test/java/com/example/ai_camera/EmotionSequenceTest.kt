package com.example.ai_camera

import com.example.ai_camera.emotion.Emotion
import com.example.ai_camera.emotion.KeywordEmotionClassifier
import com.example.ai_camera.emotion.SentenceSplitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The path the avatar actually runs: split a reply, then classify each chunk in turn. */
class EmotionSequenceTest {
    private val classifier = KeywordEmotionClassifier()

    private fun sequenceFor(reply: String): List<Emotion> = runBlocking {
        SentenceSplitter.split(reply).map { classifier.classify(it) }
    }

    @Test
    fun `a reply drives one expression per chunk`() {
        val reply = "這張構圖很漂亮，光線也不錯。可惜快門太慢了，照片會模糊。"
        val sequence = sequenceFor(reply)

        assertEquals(SentenceSplitter.split(reply).size, sequence.size)
        assertTrue("expected the mood to change across the reply", sequence.distinct().size > 1)
    }

    @Test
    fun `praise and disappointment map to different faces`() {
        assertEquals(Emotion.HAPPY, sequenceFor("這張照片真的很漂亮呢。").single())
        assertEquals(Emotion.SAD, sequenceFor("可惜光線實在太暗了。").single())
    }

    @Test
    fun `unremarkable text falls back to the idle face`() {
        assertEquals(Emotion.NEUTRAL, sequenceFor("目前的設定是這樣子的。").single())
    }

    @Test
    fun `an empty reply shows nothing, leaving the avatar idle`() {
        assertTrue(sequenceFor("").isEmpty())
    }

    @Test
    fun `every emotion maps to a bundled gif`() {
        Emotion.entries.forEach { emotion ->
            assertEquals("file:///android_asset/icon_gif/${emotion.id}.gif", emotion.assetPath)
        }
    }

    // The idle face is 6.gif, as asked.
    @Test
    fun `the idle face is the sixth gif`() {
        assertEquals(6, Emotion.IDLE.id)
        assertEquals("file:///android_asset/icon_gif/6.gif", Emotion.IDLE.assetPath)
    }
}
