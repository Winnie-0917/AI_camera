package com.example.ai_camera

import com.example.ai_camera.emotion.WordPieceTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity with HuggingFace. The expected ids were produced by the real tokenizer against the same
 * checkpoint, so a mismatch here means the model would be fed something it was never trained on -
 * which shows up as confidently wrong predictions rather than as a crash.
 */
class WordPieceTokenizerTest {

    private val tokenizer: WordPieceTokenizer by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("vocab.txt")) {
            "vocab.txt missing from test resources"
        }
        stream.bufferedReader().useLines { WordPieceTokenizer.fromVocabLines(it) }
    }

    private fun ids(text: String): List<Long> {
        val encoded = tokenizer.encode(text, maxLength = 64)
        // Compare only the real tokens, ignoring padding.
        return encoded.inputIds.filterIndexed { i, _ -> encoded.attentionMask[i] == 1L }
    }

    @Test
    fun `chinese sentence matches huggingface`() {
        assertEquals(
            listOf<Long>(101, 6857, 2484, 4212, 4275, 4638, 3539, 1756, 2523, 1962, 4692, 102),
            ids("這張照片的構圖很好看"),
        )
    }

    @Test
    fun `chinese punctuation matches huggingface`() {
        assertEquals(
            listOf<Long>(101, 1045, 5221, 679, 6639, 8024, 2571, 7271, 1922, 2714, 749, 511, 102),
            ids("光線不足，快門太慢了。"),
        )
    }

    @Test
    fun `mixed latin and digits match huggingface`() {
        assertEquals(listOf<Long>(101, 8784, 8230, 3300, 7953, 7770, 102), ids("ISO 400 有點高"))
    }

    @Test
    fun `english is lowercased and split into word pieces`() {
        assertEquals(listOf<Long>(101, 8701, 8572, 102), ids("Hello World"))
    }

    @Test
    fun `chinese and english mixed together match huggingface`() {
        assertEquals(
            listOf<Long>(101, 2864, 4212, 9020, 3921, 1394, 10060, 102),
            ids("拍照 photo 混合 test"),
        )
    }

    @Test
    fun `full width punctuation matches huggingface`() {
        assertEquals(
            listOf<Long>(101, 1505, 6857, 738, 1922, 5401, 749, 1416, 8013, 102),
            ids("哇 這也太美了吧！"),
        )
        assertEquals(listOf<Long>(101, 8043, 8043, 8043, 102), ids("？？？"))
    }

    @Test
    fun `empty text is just the special tokens`() {
        assertEquals(listOf<Long>(101, 102), ids(""))
    }

    @Test
    fun `output is padded to the fixed length the model expects`() {
        val encoded = tokenizer.encode("可惜太暗了", maxLength = 64)
        assertEquals(64, encoded.inputIds.size)
        assertEquals(64, encoded.attentionMask.size)
        assertEquals(64, encoded.tokenTypeIds.size)
        assertEquals(0L, encoded.inputIds.last())
        assertEquals(0L, encoded.attentionMask.last())
        assertTrue(encoded.tokenTypeIds.all { it == 0L })
    }

    @Test
    fun `over-long input is truncated but keeps its closing separator`() {
        val encoded = tokenizer.encode("構圖".repeat(200), maxLength = 64)
        assertEquals(64, encoded.inputIds.size)
        assertEquals(101L, encoded.inputIds.first())
        assertEquals(102L, encoded.inputIds.last())
        assertTrue(encoded.attentionMask.all { it == 1L })
    }
}
