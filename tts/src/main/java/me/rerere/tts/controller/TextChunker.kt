package me.rerere.tts.controller

import kotlin.uuid.Uuid

/**
 * Split long text into speakable chunks with paragraph and sentence-aware grouping.
 * Preserves the natural cadence and prosody of speech without arbitrary micro-chunking.
 */
class TextChunker(
    private val maxChunkLength: Int = 360
) {
    fun split(text: String): List<TtsChunk> {
        if (text.isBlank()) return emptyList()

        // 1. Split into paragraphs/lines by newline characters
        val rawParagraphs = text.split(REGEX_PARAGRAPH_BREAK)

        val chunks = mutableListOf<String>()

        for (paragraph in rawParagraphs) {
            val trimmedPara = paragraph.trim()
            if (trimmedPara.isEmpty()) continue

            // 2. Split paragraph into complete sentences
            val sentences = splitParagraphIntoSentences(trimmedPara)

            // 3. Group sentences within the paragraph up to maxChunkLength
            var currentChunk = StringBuilder()

            for (sentence in sentences) {
                val trimmedSentence = sentence.trim()
                if (trimmedSentence.isEmpty()) continue

                if (trimmedSentence.length > maxChunkLength) {
                    // Flush accumulated chunk first
                    if (currentChunk.isNotBlank()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    // Break oversized sentence by clause or words
                    val subSentences = splitOversizedSentence(trimmedSentence, maxChunkLength)
                    chunks.addAll(subSentences)
                } else {
                    val additionalLength = if (currentChunk.isEmpty()) {
                        trimmedSentence.length
                    } else {
                        val needSpace = needsSpace(currentChunk.last(), trimmedSentence.first())
                        (if (needSpace) 1 else 0) + trimmedSentence.length
                    }

                    if (currentChunk.isNotEmpty() && currentChunk.length + additionalLength > maxChunkLength) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder(trimmedSentence)
                    } else {
                        if (currentChunk.isNotEmpty()) {
                            if (needsSpace(currentChunk.last(), trimmedSentence.first())) {
                                currentChunk.append(" ")
                            }
                            currentChunk.append(trimmedSentence)
                        } else {
                            currentChunk.append(trimmedSentence)
                        }
                    }
                }
            }

            if (currentChunk.isNotBlank()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
        }

        return chunks.filter { it.isNotBlank() }.mapIndexed { index, value ->
            TtsChunk(text = value, index = index)
        }
    }

    private fun splitParagraphIntoSentences(paragraph: String): List<String> {
        if (paragraph.length <= maxChunkLength && !containsSentenceTerminators(paragraph)) {
            return listOf(paragraph)
        }

        val result = mutableListOf<String>()
        var start = 0
        var i = 0
        val len = paragraph.length

        while (i < len) {
            val c = paragraph[i]

            // Check CJK sentence terminators: 。！？…
            if (c == '。' || c == '！' || c == '？' || c == '…') {
                var end = i + 1
                while (end < len && isClosingPunctuation(paragraph[end])) {
                    end++
                }
                val sentence = paragraph.substring(start, end).trim()
                if (sentence.isNotEmpty()) {
                    result.add(sentence)
                }
                start = end
                i = end
                continue
            }

            // Check Western sentence terminators: ! ?
            if (c == '!' || c == '?') {
                var end = i + 1
                while (end < len && (paragraph[end] == '!' || paragraph[end] == '?' || isClosingPunctuation(paragraph[end]))) {
                    end++
                }
                val sentence = paragraph.substring(start, end).trim()
                if (sentence.isNotEmpty()) {
                    result.add(sentence)
                }
                start = end
                i = end
                continue
            }

            if (c == '.') {
                if (isSentenceEndingPeriod(paragraph, i)) {
                    var end = i + 1
                    while (end < len && isClosingPunctuation(paragraph[end])) {
                        end++
                    }
                    val sentence = paragraph.substring(start, end).trim()
                    if (sentence.isNotEmpty()) {
                        result.add(sentence)
                    }
                    start = end
                    i = end
                    continue
                }
            }

            i++
        }

        if (start < len) {
            val remaining = paragraph.substring(start).trim()
            if (remaining.isNotEmpty()) {
                result.add(remaining)
            }
        }

        return if (result.isEmpty()) listOf(paragraph) else result
    }

    private fun isSentenceEndingPeriod(text: String, dotIndex: Int): Boolean {
        if (dotIndex == text.length - 1) return true

        val prevChar = if (dotIndex > 0) text[dotIndex - 1] else null
        val nextChar = text[dotIndex + 1]

        // If next char is a digit or prev char is a digit, it's a decimal number or version (e.g. 3.14, 1.4.5)
        if (prevChar?.isDigit() == true && nextChar.isDigit()) return false

        // Check common abbreviations
        val precedingWord = getPrecedingWord(text, dotIndex)
        if (precedingWord.isNotEmpty() && ABBREVIATIONS.contains(precedingWord.lowercase())) {
            return false
        }

        // Check if next char is whitespace, quote, bracket, or newline
        if (nextChar.isWhitespace() || isClosingPunctuation(nextChar) || nextChar == '\n') {
            return true
        }

        return false
    }

    private fun getPrecedingWord(text: String, dotIndex: Int): String {
        var start = dotIndex - 1
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) {
            start--
        }
        return text.substring(start + 1, dotIndex)
    }

    private fun splitOversizedSentence(sentence: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        val clauseRegex = "(?<=[;；:：,，、—–])".toRegex()
        val clauses = sentence.split(clauseRegex)

        var current = StringBuilder()
        for (clause in clauses) {
            val trimmed = clause.trim()
            if (trimmed.isEmpty()) continue

            if (current.isNotEmpty() && current.length + 1 + trimmed.length > maxLength) {
                result.add(current.toString().trim())
                current = StringBuilder(trimmed)
            } else {
                if (current.isNotEmpty()) {
                    if (needsSpace(current.last(), trimmed.first())) {
                        current.append(" ")
                    }
                    current.append(trimmed)
                } else {
                    current.append(trimmed)
                }
            }
        }
        if (current.isNotBlank()) {
            result.add(current.toString().trim())
        }

        val finalResult = mutableListOf<String>()
        for (item in result) {
            if (item.length <= maxLength) {
                finalResult.add(item)
            } else {
                finalResult.addAll(splitByWords(item, maxLength))
            }
        }
        return finalResult
    }

    private fun splitByWords(text: String, maxLength: Int): List<String> {
        val words = text.split(" ")
        val parts = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in words) {
            if (cur.isNotEmpty() && cur.length + 1 + word.length > maxLength) {
                parts.add(cur.toString().trim())
                cur = StringBuilder(word)
            } else {
                if (cur.isNotEmpty()) cur.append(" ")
                cur.append(word)
            }
        }
        if (cur.isNotBlank()) {
            parts.add(cur.toString().trim())
        }
        return if (parts.isEmpty()) listOf(text) else parts
    }

    private fun containsSentenceTerminators(text: String): Boolean {
        return text.any { it == '。' || it == '！' || it == '？' || it == '…' || it == '.' || it == '!' || it == '?' }
    }

    private fun isClosingPunctuation(c: Char): Boolean {
        return c == '"' || c == '\'' || c == '”' || c == '’' || c == ')' || c == ']' || c == '}' || c == '）' || c == '」' || c == '』'
    }

    private fun needsSpace(lastChar: Char, firstChar: Char): Boolean {
        if (isCjk(lastChar) || isCjk(firstChar)) return false
        if (lastChar == ' ' || firstChar == ' ') return false
        return true
    }

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    companion object {
        private val REGEX_PARAGRAPH_BREAK = Regex("(?:\r?\n)+")
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "vs", "etc",
            "eg", "e.g", "ie", "i.e", "approx", "dept", "est", "inc",
            "ltd", "co", "corp", "jan", "feb", "mar", "apr", "aug",
            "sept", "oct", "nov", "dec", "no", "vol", "p", "pp"
        )
    }
}

data class TtsChunk(
    val id: Uuid = Uuid.random(),
    val index: Int,
    val text: String
)


