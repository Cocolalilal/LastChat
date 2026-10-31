package me.rerere.rikkahub.utils

const val LONG_TEXT_PASTE_THRESHOLD = 8_000
const val LONG_MESSAGE_CHUNK_SIZE = 4_000

internal fun isLongTextPaste(text: CharSequence): Boolean {
    if (text.length >= LONG_TEXT_PASTE_THRESHOLD) return true
    var lines = 1
    for (character in text) {
        if (character == '\n' && ++lines >= 120) return true
    }
    return false
}

/** Bound layout by both characters and lines, including text containing thousands of blank lines. */
internal fun longMessagePreviewEnd(
    text: String,
    start: Int = 0,
    maxChars: Int = LONG_MESSAGE_CHUNK_SIZE,
    maxLines: Int = 60,
): Int {
    var end = minOf(text.length, start + maxChars)
    var lines = 0
    for (index in start until end) {
        if (text[index] == '\n' && ++lines == maxLines) {
            end = index + 1
            break
        }
    }
    // Never cut a UTF-16 surrogate pair in half.
    if (end < text.length && end > start && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
        end--
    }
    return end
}
