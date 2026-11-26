package me.rerere.rikkahub.data.memory

sealed interface PointerSegment {
    data class Text(val value: String) : PointerSegment
    data class Pointer(val memoryId: Int, val rawTag: String) : PointerSegment
}

object MemoryPointerParser {
    // Matches [#79], [#182, #240], and gracefully recovers LLM drift like [#182, 240]
    private val POINTER_GROUP_REGEX = Regex("""\[(#\d+(?:\s*,\s*#?\d+)*)\]""")
    private val ID_DIGITS_REGEX = Regex("""\d+""")
    private val SPACE_BEFORE_PUNCT_REGEX = Regex("""[ \t]+([.,;:!?])""")
    private val MULTI_SPACE_REGEX = Regex("""[ \t]{2,}""")
    private val TRAILING_SPACE_LINE_REGEX = Regex("""[ \t]+$""", RegexOption.MULTILINE)

    /**
     * Tokenizes [content] into alternating text runs and individual clickable pointer segments.
     * Example: "Prefers dry humor [#182, #240]." ->
     *   [Text("Prefers dry humor "), Pointer(182, "[#182]"), Pointer(240, "[#240]"), Text(".")]
     */
    fun parseSegments(content: String): List<PointerSegment> {
        if (content.isEmpty()) return emptyList()
        val segments = ArrayList<PointerSegment>(16)
        var cursor = 0

        for (match in POINTER_GROUP_REGEX.findAll(content)) {
            val range = match.range
            if (range.first > cursor) {
                segments.add(PointerSegment.Text(content.substring(cursor, range.first)))
            }
            val inner = match.groupValues[1]
            val ids = ID_DIGITS_REGEX.findAll(inner)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it > 0 }
                .distinct()
                .toList()

            if (ids.isEmpty()) {
                segments.add(PointerSegment.Text(match.value))
            } else {
                ids.forEachIndexed { idx, id ->
                    if (idx > 0) segments.add(PointerSegment.Text(" "))
                    segments.add(PointerSegment.Pointer(memoryId = id, rawTag = "[#$id]"))
                }
            }
            cursor = range.last + 1
        }

        if (cursor < content.length) {
            segments.add(PointerSegment.Text(content.substring(cursor)))
        }
        return segments
    }

    /** Extracts all distinct positive memory IDs in order of appearance. */
    fun extractIds(content: String): List<Int> {
        if (content.isBlank()) return emptyList()
        val result = LinkedHashSet<Int>()
        for (match in POINTER_GROUP_REGEX.findAll(content)) {
            for (digits in ID_DIGITS_REGEX.findAll(match.groupValues[1])) {
                digits.value.toIntOrNull()?.takeIf { it > 0 }?.let(result::add)
            }
        }
        return result.toList()
    }

    /** Alias for [extractIds]. */
    fun extractCitedMemoryIds(content: String): List<Int> = extractIds(content)

    /** Strips all [#ID] citations cleanly for system clipboard copy or quote formatting. */
    fun stripPointers(content: String): String {
        if (content.isEmpty()) return content
        val removed = POINTER_GROUP_REGEX.replace(content, "")
        return removed
            .replace(SPACE_BEFORE_PUNCT_REGEX, "$1")
            .replace(MULTI_SPACE_REGEX, " ")
            .replace(TRAILING_SPACE_LINE_REGEX, "")
            .trim()
    }

    /**
     * Surgically removes a single deleted [targetId] from citation groups without corrupting
     * sibling IDs: "[#182, #240]" minus 182 -> "[#240]"; "[#79]" minus 79 -> "".
     */
    fun removePointerId(content: String, targetId: Int): String {
        if (content.isEmpty()) return content
        val replaced = POINTER_GROUP_REGEX.replace(content) { match ->
            val remaining = ID_DIGITS_REGEX.findAll(match.groupValues[1])
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it > 0 && it != targetId }
                .distinct()
                .toList()
            if (remaining.isEmpty()) {
                ""
            } else {
                remaining.joinToString(prefix = "[", postfix = "]", separator = ", ") { "#$it" }
            }
        }
        return replaced
            .replace(SPACE_BEFORE_PUNCT_REGEX, "$1")
            .replace(MULTI_SPACE_REGEX, " ")
            .replace(TRAILING_SPACE_LINE_REGEX, "")
    }

    /**
     * Resolves pointer IDs from a user's selected substring. If the user highlighted words
     * immediately preceding a chip without capturing the chip itself, falls back to the
     * enclosing line in [fullSectionContent].
     */
    fun resolveIdsForSelection(selectedText: String, fullSectionContent: String): List<Int> {
        val direct = extractIds(selectedText)
        if (direct.isNotEmpty()) return direct

        val probe = stripPointers(selectedText).trim()
        if (probe.length < 2) return emptyList()

        val probeLines = probe.lineSequence()
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.length >= 2 }
            .toList()
        if (probeLines.isEmpty()) return emptyList()

        val matchedLines = fullSectionContent.lineSequence()
            .filter { line ->
                val cleanLine = stripPointers(line)
                cleanLine.contains(probe, ignoreCase = true) ||
                    probeLines.any { sub -> cleanLine.contains(sub, ignoreCase = true) }
            }
            .toList()

        return matchedLines.flatMap { extractIds(it) }.distinct()
    }

    /** Formats a raw memory row for ingestion into the synthesizer prompt. */
    fun formatForIngestion(id: Int, content: String, isoDate: String?): String {
        val datePrefix = if (!isoDate.isNullOrBlank()) " ($isoDate)" else ""
        return "[#$id]$datePrefix ${content.replace('\n', ' ').trim()}"
    }

    /** Recomputes cached citedMemoryIds and normalizes section keys/icons. */
    fun hydrateProvenance(data: MemorySummaryData): MemorySummaryData {
        val normalizedSections = data.sections.map { section ->
            val safeIcon = if (section.icon in ALLOWED_SUMMARY_ICONS) section.icon else "star"
            val ids = extractIds(section.content)
            section.copy(
                id = section.normalizedKey(),
                icon = safeIcon,
                citedMemoryIds = ids,
            )
        }
        return data.copy(sections = normalizedSections)
    }
}

/**
 * Deterministic bidirectional Markdown codec for Room raw_markdown storage and User Manual Edit Mode.
 */
object MemorySummaryMarkdownCodec {
    private val HEADING_REGEX = Regex("""^##\s+(.+)$""")

    fun encodeSectionsToMarkdown(data: MemorySummaryData): String = buildString {
        data.sections.forEachIndexed { index, section ->
            if (index > 0) append("\n\n")
            append("## ").append(section.title.trim()).append('\n')
            append(section.content.trimEnd())
        }
    }

    fun encodeFullDocument(data: MemorySummaryData): String = buildString {
        append("# ").append(data.title.trim()).append('\n')
        if (data.subtitle.isNotBlank()) {
            append(data.subtitle.trim()).append("\n\n")
        } else {
            append('\n')
        }
        append(encodeSectionsToMarkdown(data))
    }

    /**
     * Parses edited section Markdown back into [MemorySummaryData], preserving existing
     * section icons, title, subtitle, and diveDeeper actions.
     */
    fun decodeSectionsFromMarkdown(
        markdown: String,
        base: MemorySummaryData,
        editedTitle: String = base.title,
        editedSubtitle: String = base.subtitle,
    ): MemorySummaryData {
        val iconByKey = base.sections.associate { it.normalizedKey() to it.icon }
        val sections = mutableListOf<MemorySummarySection>()

        var currentTitle: String? = null
        val buffer = StringBuilder()

        fun flush() {
            val title = currentTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val body = buffer.toString().trim()
            if (body.isNotEmpty()) {
                val temp = MemorySummarySection(title = title, content = body)
                val preservedIcon = iconByKey[temp.normalizedKey()] ?: "star"
                sections.add(
                    temp.copy(
                        id = temp.normalizedKey(),
                        icon = preservedIcon,
                        citedMemoryIds = MemoryPointerParser.extractIds(body),
                    )
                )
            }
            buffer.clear()
        }

        for (rawLine in markdown.lines()) {
            val headingMatch = HEADING_REGEX.matchEntire(rawLine.trim())
            if (headingMatch != null) {
                flush()
                currentTitle = headingMatch.groupValues[1].trim()
            } else {
                if (currentTitle == null && rawLine.isNotBlank()) {
                    currentTitle = "Overview"
                }
                if (currentTitle != null) {
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(rawLine)
                }
            }
        }
        flush()

        return MemoryPointerParser.hydrateProvenance(
            base.copy(
                title = editedTitle.trim().ifBlank { base.title },
                subtitle = editedSubtitle.trim(),
                sections = sections,
            )
        )
    }

    /** Alias for [decodeSectionsFromMarkdown]. */
    fun decodeMarkdownToSections(
        markdown: String,
        base: MemorySummaryData = MemorySummaryData(),
    ): MemorySummaryData = decodeSectionsFromMarkdown(markdown = markdown, base = base)
}
