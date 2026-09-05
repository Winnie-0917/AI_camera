package com.example.ai_camera.emotion

import java.text.Normalizer

/**
 * The BERT tokenizer the emotion model was trained with, reimplemented for Android.
 *
 * HuggingFace's tokenizer is Python-only, so this has to reproduce its behaviour exactly: any
 * difference in how text becomes token ids feeds the model something it was not trained on, and it
 * fails quietly with plausible-looking predictions rather than an error. The settings mirror
 * `tokenizer.json`: lowercase, accents stripped, CJK characters split individually, WordPiece with
 * a `##` continuation prefix.
 *
 * @param vocab token -> id, in the order the model was trained with.
 */
class WordPieceTokenizer(private val vocab: Map<String, Int>) {

    private val unkId = vocab[UNK] ?: 100
    private val clsId = vocab[CLS] ?: 101
    private val sepId = vocab[SEP] ?: 102
    private val padId = vocab[PAD] ?: 0

    data class Encoded(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    /** Encodes to a fixed [maxLength], padding or truncating, with [CLS]/[SEP] added. */
    fun encode(text: String, maxLength: Int): Encoded {
        val pieces = tokenize(text).take(maxLength - 2)
        val ids = ArrayList<Int>(maxLength).apply {
            add(clsId)
            pieces.forEach { add(vocab[it] ?: unkId) }
            add(sepId)
        }
        val length = ids.size

        val inputIds = LongArray(maxLength) { i -> (ids.getOrNull(i) ?: padId).toLong() }
        val attentionMask = LongArray(maxLength) { i -> if (i < length) 1L else 0L }
        // Single-sentence input, so every token belongs to segment 0.
        val tokenTypeIds = LongArray(maxLength)
        return Encoded(inputIds, attentionMask, tokenTypeIds)
    }

    /** Text -> word pieces, without special tokens. Exposed for parity testing. */
    fun tokenize(text: String): List<String> =
        basicTokenize(text).flatMap { wordPiece(it) }

    /**
     * Whitespace/punctuation splitting with CJK characters isolated, matching BertNormalizer with
     * `handle_chinese_chars`. Each Chinese character is its own token, which is why "這張照片"
     * becomes four tokens rather than one unknown word.
     */
    private fun basicTokenize(text: String): List<String> {
        val cleaned = buildString {
            for (char in text) {
                val code = char.code
                when {
                    code == 0 || code == 0xFFFD || char.isControlChar() -> Unit
                    char.isWhitespace() -> append(' ')
                    char.isCjk() -> append(' ').append(char).append(' ')
                    else -> append(char)
                }
            }
        }

        return cleaned.split(' ')
            .filter { it.isNotEmpty() }
            .flatMap { token ->
                val lowered = token.lowercase()
                val stripped = stripAccents(lowered)
                splitOnPunctuation(stripped)
            }
            .filter { it.isNotEmpty() }
    }

    private fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

    private fun splitOnPunctuation(token: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (char in token) {
            if (char.isPunctuation()) {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
                out += char.toString()
            } else {
                current.append(char)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** Greedy longest-match-first, the standard WordPiece algorithm. */
    private fun wordPiece(word: String): List<String> {
        if (word.length > MAX_CHARS_PER_WORD) return listOf(UNK)

        val pieces = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var match: String? = null
            while (start < end) {
                val candidate = if (start == 0) {
                    word.substring(start, end)
                } else {
                    "##" + word.substring(start, end)
                }
                if (vocab.containsKey(candidate)) {
                    match = candidate
                    break
                }
                end--
            }
            // No prefix of the remainder is in the vocabulary, so the whole word is unknown -
            // not just this piece.
            if (match == null) return listOf(UNK)
            pieces += match
            start = end
        }
        return pieces
    }

    private fun Char.isControlChar(): Boolean {
        if (this == '\t' || this == '\n' || this == '\r') return false
        return when (Character.getType(this)) {
            Character.CONTROL.toInt(), Character.FORMAT.toInt() -> true
            else -> false
        }
    }

    /** The CJK blocks BERT treats as individual tokens. */
    private fun Char.isCjk(): Boolean {
        val cp = code
        return (cp in 0x4E00..0x9FFF) ||
            (cp in 0x3400..0x4DBF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x2E80..0x2EFF) ||
            (cp in 0x3000..0x303F && cp != 0x3000)
    }

    private fun Char.isPunctuation(): Boolean {
        val cp = code
        // BERT counts the ASCII symbol ranges as punctuation even though Unicode does not.
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(this)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
    }

    companion object {
        const val PAD = "[PAD]"
        const val UNK = "[UNK]"
        const val CLS = "[CLS]"
        const val SEP = "[SEP]"
        private const val MAX_CHARS_PER_WORD = 100

        /** Reads `vocab.txt`, one token per line, id = line number. */
        fun fromVocabLines(lines: Sequence<String>): WordPieceTokenizer {
            val vocab = HashMap<String, Int>(24_000)
            lines.forEachIndexed { index, line -> vocab[line] = index }
            return WordPieceTokenizer(vocab)
        }
    }
}
