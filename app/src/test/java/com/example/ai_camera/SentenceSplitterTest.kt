package com.example.ai_camera

import com.example.ai_camera.emotion.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun `splits a long reply at its punctuation`() {
        val chunks = SentenceSplitter.split("室內光線比較暗，快門速度太慢了。試著提高 ISO 吧。")
        assertEquals(
            listOf("室內光線比較暗，", "快門速度太慢了。", "試著提高 ISO 吧。"),
            chunks,
        )
    }

    // The rule you asked for: a mark inside a short run is not a cut, the text keeps going.
    @Test
    fun `a mark inside a short run does not cut`() {
        val chunks = SentenceSplitter.split("好的，我來幫你調整參數。")
        assertEquals(listOf("好的，我來幫你調整參數。"), chunks)
    }

    @Test
    fun `every chunk clears the minimum, except a tail that cannot`() {
        val chunks = SentenceSplitter.split("嗯，好，是，這樣的光線需要更快的快門。")
        chunks.forEach { chunk ->
            val letters = chunk.count { it.isLetterOrDigit() }
            assertTrue("too short: $chunk", letters > SentenceSplitter.MIN_CHARS)
        }
    }

    @Test
    fun `a short tail is folded into the previous chunk`() {
        val chunks = SentenceSplitter.split("這張照片的構圖很好看，可以")
        assertEquals(1, chunks.size)
        assertTrue(chunks.single().endsWith("可以"))
    }

    @Test
    fun `a tail with no preceding chunk still stands alone`() {
        assertEquals(listOf("好"), SentenceSplitter.split("好"))
    }

    @Test
    fun `english punctuation splits too`() {
        val chunks = SentenceSplitter.split("The light is low. Raise the ISO a little.")
        assertEquals(listOf("The light is low.", "Raise the ISO a little."), chunks)
    }

    @Test
    fun `empty input yields nothing to show`() {
        assertEquals(emptyList<String>(), SentenceSplitter.split(""))
        assertEquals(emptyList<String>(), SentenceSplitter.split("   "))
    }

    @Test
    fun `no text is lost`() {
        val reply = "光線不足，快門太慢，照片會糊。提高 ISO 就好"
        val rejoined = SentenceSplitter.split(reply).joinToString("")
        assertEquals(reply.filterNot { it.isWhitespace() }, rejoined.filterNot { it.isWhitespace() })
    }
}
