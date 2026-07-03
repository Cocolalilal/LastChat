package me.rerere.rikkahub.data.memory

/**
 * Pure text helpers for the memory store's lexical layer.
 *
 * These are the *authoritative* signals for the dedup gate (§5.3): vectors are only ever a
 * candidate generator, so identity/restatement decisions must be reproducible and model-independent.
 * Kept dependency-free so they can be unit-tested on the JVM without Room or Android.
 */
object MemoryText {

    // A deliberately tiny stopword set. Big stoplists hurt short factual statements
    // ("user is from Graz") where nearly every token matters.
    private val STOPWORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "am",
        "to", "of", "in", "on", "at", "for", "and", "or", "but", "with", "as",
        "that", "this", "it", "its", "they", "them", "their", "he", "she", "his",
        "her", "you", "your", "i", "me", "my", "we", "our", "do", "does", "did",
        "has", "have", "had", "s", "re", "ll", "ve", "t",
    )

    /** Lowercased, punctuation-stripped, whitespace-collapsed form for exact-match comparison. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var lastSpace = true
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }

    /** All normalized word tokens (stopwords kept) — used for FTS query construction. */
    fun tokens(text: String): List<String> {
        val n = normalize(text)
        if (n.isEmpty()) return emptyList()
        return n.split(' ').filter { it.isNotBlank() }
    }

    /**
     * Content tokens: normalized words with stopwords and 1-char tokens removed. This is the set
     * used for restatement/overlap decisions so filler words don't make two different facts look
     * identical or two identical facts look different.
     */
    fun contentTokens(text: String): Set<String> =
        tokens(text).filter { it.length > 1 && it !in STOPWORDS }.toSet()

    fun normalizedEquals(a: String, b: String): Boolean = normalize(a) == normalize(b)

    /**
     * True when [candidate] adds no information over [existing]: either a normalized exact match, or
     * the candidate's content tokens are a (non-empty) subset of the existing node's — a pure
     * restatement. This is the ONLY condition under which the dedup gate collapses an ADD into a
     * REINFORCE. "Similar but different" (new/changed tokens) is never a restatement.
     */
    fun isRestatement(candidate: String, existing: String): Boolean {
        if (normalizedEquals(candidate, existing)) return true
        val cand = contentTokens(candidate)
        val exist = contentTokens(existing)
        if (cand.isEmpty() || exist.isEmpty()) return false
        return exist.containsAll(cand)
    }

    /**
     * Overlap coefficient of content tokens in [0,1]: |A∩B| / min(|A|,|B|). Used to decide whether
     * two nodes are "similar but different" (shared subject, overlapping wording) and should be
     * flagged for sleep-pass adjudication rather than merged at write time.
     */
    fun overlap(a: String, b: String): Float {
        val ta = contentTokens(a)
        val tb = contentTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.count { it in tb }
        return inter.toFloat() / minOf(ta.size, tb.size).toFloat()
    }

    /**
     * Build a safe FTS4 MATCH query that ORs the given terms. Each term is wrapped in double quotes
     * (with embedded quotes doubled) so punctuation/operators in user text can't corrupt the query
     * or throw. Returns null when there is nothing searchable.
     */
    fun ftsOrQuery(terms: Collection<String>): String? {
        val cleaned = terms
            .map { it.trim() }
            .filter { it.length > 1 }
            .map { "\"" + it.replace("\"", "\"\"") + "\"" }
            .distinct()
        if (cleaned.isEmpty()) return null
        return cleaned.joinToString(" OR ")
    }
}
