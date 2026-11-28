package me.rerere.rikkahub.data.browser

/**
 * Longest-first plain-string redactor for secret values in browser tool outputs.
 * Skips values shorter than [MIN_MATCH_LEN] (5 chars) to prevent false positives on
 * common short words.
 */
object BrowserSecretRedactor {
    const val MIN_MATCH_LEN = 5

    const val SYSTEM_REMINDER =
        "<system-reminder>Secret redaction is ON: one or more stored secret values were masked in this output. " +
            "Use {{SECRET:KEY}} in `type` or `restore_cookies_from_secret(key)` rather than echoing raw secrets.</system-reminder>"

    fun mask(value: String): String {
        val n = value.length
        if (n < 8) return "*".repeat(n)
        return value.substring(0, 2) + "*".repeat(n - 4) + value.substring(n - 2, n)
    }

    fun redact(output: String, values: Collection<String>): Pair<String, Int> {
        val candidates = values
            .filter { it.length >= MIN_MATCH_LEN }
            .toSet()
            .sortedByDescending { it.length }
        if (candidates.isEmpty() || output.isEmpty()) return output to 0

        var current = output
        var hits = 0
        for (v in candidates) {
            if (current.contains(v)) {
                current = current.replace(v, mask(v))
                hits++
            }
        }
        return current to hits
    }

    fun redactIfEnabled(
        output: String,
        enabled: Boolean,
        secretsStore: BrowserSecretsStore?,
    ): Pair<String, Int> {
        if (!enabled || secretsStore == null || output.isEmpty()) return output to 0
        val values = secretsStore.allSecretValues()
        if (values.isEmpty()) return output to 0
        val (masked, hits) = redact(output, values)
        if (hits == 0) return masked to 0
        return "$masked\n\n$SYSTEM_REMINDER" to hits
    }
}
