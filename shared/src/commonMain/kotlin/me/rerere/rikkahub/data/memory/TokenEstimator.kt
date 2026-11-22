package me.rerere.rikkahub.data.memory

import kotlin.math.ceil

object TokenEstimator {
    /**
     * Fast, zero-allocation-overhead token estimator tuned for mixed Latin/code/CJK text.
     * Latin/ASCII averages ~4 chars/token; CJK ideographs average ~1.5 chars/token.
     */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var asciiChars = 0
        var nonAsciiChars = 0
        for (ch in text) {
            if (ch.code < 128) asciiChars++ else nonAsciiChars++
        }
        val estimated = (asciiChars / 3.8) + (nonAsciiChars / 1.5)
        return ceil(estimated).toInt().coerceAtLeast(1)
    }

    fun truncateToTokenBudget(text: String, maxTokens: Int): String {
        if (estimate(text) <= maxTokens) return text
        val lines = text.lines()
        val out = StringBuilder()
        var usedTokens = 0
        for (line in lines) {
            val lineTokens = estimate(line) + 1
            if (usedTokens + lineTokens > maxTokens - 20) {
                out.append("\n[Summary truncated to fit context budget. Use get_memories or manage_memory_summary(action=\"read\") for omitted sections.]")
                break
            }
            if (out.isNotEmpty()) out.append('\n')
            out.append(line)
            usedTokens += lineTokens
        }
        return out.toString()
    }
}
