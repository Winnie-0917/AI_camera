package com.example.ai_camera.emotion

/**
 * Cuts a reply into the chunks the avatar reacts to, one emotion per chunk.
 *
 * Splitting on every punctuation mark alone produces fragments like "好的" or "嗯" that carry no
 * emotion worth showing and would make the avatar flicker, so a cut is only taken once the pending
 * text is longer than [MIN_CHARS]; otherwise the text keeps accumulating to the next mark.
 */
object SentenceSplitter {
    /** A chunk must be longer than this to be cut; shorter text waits for the next mark. */
    const val MIN_CHARS = 5

    private const val TERMINATORS = "，。！？、；：,.!?;:\n"

    fun split(text: String): List<String> {
        val chunks = mutableListOf<String>()
        val pending = StringBuilder()

        for (char in text) {
            pending.append(char)
            if (char in TERMINATORS && pending.countsAsFull()) {
                chunks += pending.toString().trim()
                pending.clear()
            }
        }

        // Whatever is left has no closing mark. Attach it to the previous chunk when it is too
        // short to stand alone, so a trailing "好嗎" does not become its own expression.
        val tail = pending.toString().trim()
        if (tail.isNotEmpty()) {
            if (chunks.isNotEmpty() && tail.meaningfulLength() <= MIN_CHARS) {
                chunks[chunks.lastIndex] = chunks.last() + tail
            } else {
                chunks += tail
            }
        }
        return chunks.filter { it.meaningfulLength() > 0 }
    }

    private fun StringBuilder.countsAsFull(): Boolean = toString().meaningfulLength() > MIN_CHARS

    /** Length ignoring punctuation and spaces, so "好的，" counts as two characters, not three. */
    private fun String.meaningfulLength(): Int =
        count { it !in TERMINATORS && !it.isWhitespace() }
}
