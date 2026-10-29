package me.rerere.rikkahub.data.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

private val memorySummaryJsonFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class MemorySummaryData(
    val title: String = "Your Summary",
    val subtitle: String = "A quick view of what I remember about you.",
    val sections: List<MemorySummarySection> = emptyList(),
    @SerialName("dive_deeper")
    val diveDeeper: List<DiveDeeperAction> = emptyList(),
)

@Serializable
data class MemorySummarySection(
    val icon: String = "star",
    val title: String,
    val content: String,
)

@Serializable
data class DiveDeeperAction(
    val icon: String = "code",
    val title: String,
    val subtitle: String = "",
    val prompt: String,
)

@Serializable
enum class MemorySummaryCadence {
    @SerialName("immediate")
    IMMEDIATE,

    @SerialName("debounce_10m")
    DEBOUNCE_10M,

    @SerialName("hourly")
    HOURLY,

    @SerialName("daily")
    DAILY,

    @SerialName("manual")
    MANUAL;

    companion object {
        fun fromString(value: String?): MemorySummaryCadence {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IMMEDIATE
        }
    }
}

@Serializable
enum class MemoryChangeType {
    @SerialName("created")
    CREATED,

    @SerialName("updated")
    UPDATED,

    @SerialName("deleted")
    DELETED,
}

@Serializable
data class RawMemoryAction(
    val action: String = "create", // "create", "update", "delete"
    val search: String? = null,
    val content: String? = null,
)

@Serializable
data class MemorySummaryWithUpdates(
    val title: String = "Your Summary",
    val subtitle: String = "A quick view of what I remember about you.",
    val sections: List<MemorySummarySection> = emptyList(),
    @SerialName("dive_deeper")
    val diveDeeper: List<DiveDeeperAction> = emptyList(),
    @SerialName("raw_memory_updates")
    val rawMemoryUpdates: List<RawMemoryAction> = emptyList(),
)

@Serializable
data class MemorySectionPatch(
    val action: String = "update_section", // "update_section", "add_section", "delete_section", "full_rewrite"
    @SerialName("target_section")
    val targetSection: String? = null,
    val section: MemorySummarySection? = null,
    @SerialName("raw_memory_updates")
    val rawMemoryUpdates: List<RawMemoryAction> = emptyList(),
    @SerialName("dive_deeper")
    val diveDeeper: List<DiveDeeperAction>? = null,
    @SerialName("full_summary")
    val fullSummary: MemorySummaryData? = null,
)

/**
 * Checks whether text appears to be raw JSON payload rather than clean markdown or conversational prose.
 */
fun looksLikeRawJson(text: String): Boolean {
    val trimmed = text.trim()
    return (trimmed.startsWith("{") && trimmed.contains("\"")) ||
            trimmed.contains("\"action\":") ||
            trimmed.contains("\"target_section\":") ||
            (trimmed.contains("\"section\":") && trimmed.contains("\"content\":"))
}

/**
 * Converts escaped JSON special sequences back to normal characters.
 */
fun unescapeJsonString(s: String): String {
    return s.replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

/**
 * Normalizes JSON by removing thoughts, extracting fences, and escaping literal newlines inside quotes.
 */
fun sanitizeJsonString(text: String): String {
    var clean = stripThinkingTags(text).trim()
    clean = extractJsonPayload(clean)

    val sb = StringBuilder(clean.length + 32)
    var inString = false
    var escape = false
    for (i in clean.indices) {
        val ch = clean[i]
        if (ch == '"' && !escape) {
            inString = !inString
            sb.append(ch)
        } else if (inString) {
            when (ch) {
                '\n' -> sb.append("\\n")
                '\r' -> { /* ignore */ }
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        } else {
            sb.append(ch)
        }

        if (ch == '\\' && !escape) {
            escape = true
        } else {
            escape = false
        }
    }
    return sb.toString().trim()
}

/**
 * Auto-heals section content that accidentally had raw JSON dumped into it.
 */
fun tryHealCorruptedJsonContent(rawContent: String): Pair<String?, String>? {
    val clean = sanitizeJsonString(rawContent)
    try {
        val element = memorySummaryJsonFormat.parseToJsonElement(clean)
        if (element is JsonObject) {
            val sectionObj = element["section"] as? JsonObject
            if (sectionObj != null) {
                val content = sectionObj["content"]?.jsonPrimitive?.contentOrNull
                val title = sectionObj["title"]?.jsonPrimitive?.contentOrNull
                    ?: element["target_section"]?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank()) {
                    return title to content
                }
            }
            val rootContent = element["content"]?.jsonPrimitive?.contentOrNull
            if (!rootContent.isNullOrBlank()) {
                val title = element["title"]?.jsonPrimitive?.contentOrNull
                    ?: element["target_section"]?.jsonPrimitive?.contentOrNull
                return title to rootContent
            }
        }
    } catch (_: Exception) {}

    // Fallback regex extraction if JSON parser failed
    val contentRegex = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    val contentMatch = contentRegex.find(clean)
    if (contentMatch != null) {
        val rawEscaped = contentMatch.groupValues[1]
        val unescaped = unescapeJsonString(rawEscaped)
        val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(clean)
            ?: Regex("\"target_section\"\\s*:\\s*\"([^\"]+)\"").find(clean)
        val title = titleMatch?.groupValues?.getOrNull(1)
        return title to unescaped
    }

    return null
}

/**
 * Sanitizes a single section, auto-healing any raw JSON corruption in its content.
 */
fun sanitizeMemorySummarySection(section: MemorySummarySection): MemorySummarySection {
    val trimmed = section.content.trim()
    if (looksLikeRawJson(trimmed)) {
        val healed = tryHealCorruptedJsonContent(trimmed)
        if (healed != null) {
            val newTitle = healed.first ?: section.title
            return section.copy(
                title = if (section.title.isBlank() || section.title.equals("Preferences", ignoreCase = true) || section.title.equals("Your Summary", ignoreCase = true)) {
                    newTitle
                } else {
                    newTitle
                },
                content = healed.second,
            )
        }
    }
    return section
}

/**
 * Sanitizes all sections in MemorySummaryData.
 */
fun sanitizeMemorySummaryData(data: MemorySummaryData): MemorySummaryData {
    val sanitized = data.sections.map { sanitizeMemorySummarySection(it) }
    return data.copy(sections = sanitized)
}

/**
 * Flexibly parses dive deeper actions from either strings or objects.
 */
fun parseDiveDeeperActions(element: JsonElement?): List<DiveDeeperAction> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> {
                val str = item.contentOrNull?.trim()
                if (!str.isNullOrBlank()) {
                    DiveDeeperAction(
                        icon = guessIconForTitle(str),
                        title = str.take(60),
                        subtitle = "",
                        prompt = str,
                    )
                } else null
            }
            is JsonObject -> {
                val prompt = item["prompt"]?.jsonPrimitive?.contentOrNull
                    ?: item["title"]?.jsonPrimitive?.contentOrNull
                if (!prompt.isNullOrBlank()) {
                    val title = item["title"]?.jsonPrimitive?.contentOrNull ?: prompt.take(60)
                    val subtitle = item["subtitle"]?.jsonPrimitive?.contentOrNull ?: ""
                    val icon = item["icon"]?.jsonPrimitive?.contentOrNull ?: guessIconForTitle(title)
                    DiveDeeperAction(
                        icon = icon,
                        title = title,
                        subtitle = subtitle,
                        prompt = prompt,
                    )
                } else null
            }
            else -> null
        }
    }
}

/**
 * Flexibly parses raw memory database actions from objects or strings.
 */
fun parseRawMemoryActions(element: JsonElement?): List<RawMemoryAction> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { item ->
        when (item) {
            is JsonObject -> {
                val action = item["action"]?.jsonPrimitive?.contentOrNull ?: "create"
                val search = item["search"]?.jsonPrimitive?.contentOrNull
                val content = item["content"]?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank() || !search.isNullOrBlank()) {
                    RawMemoryAction(action = action, search = search, content = content)
                } else null
            }
            is JsonPrimitive -> {
                val content = item.contentOrNull?.trim()
                if (!content.isNullOrBlank()) {
                    RawMemoryAction(action = "create", content = content)
                } else null
            }
            else -> null
        }
    }
}

/**
 * Extracts a MemorySummarySection from a JsonElement.
 */
fun parseSectionFromElement(
    element: JsonElement?,
    defaultTitle: String? = null,
): MemorySummarySection? {
    if (element !is JsonObject) return null
    val content = element["content"]?.jsonPrimitive?.contentOrNull?.trim()
    if (content.isNullOrBlank()) return null
    val title = element["title"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: defaultTitle
        ?: "Category"
    val icon = element["icon"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: guessIconForTitle(title)
    return MemorySummarySection(icon = icon, title = title, content = content)
}

/**
 * Applies a block-level delta patch to MemorySummaryData.
 */
fun applySectionPatch(
    current: MemorySummaryData,
    patch: MemorySectionPatch,
): MemorySummaryData {
    if (patch.fullSummary != null && patch.fullSummary.sections.isNotEmpty()) {
        return sanitizeMemorySummaryData(patch.fullSummary)
    }

    val updatedSections = current.sections.map { sanitizeMemorySummarySection(it) }.toMutableList()
    val action = patch.action.lowercase().trim()
    val newSection = patch.section?.let { sanitizeMemorySummarySection(it) }

    when {
        action in listOf("add_section", "create_section", "create", "add", "new_section", "new", "insert") -> {
            if (newSection != null) {
                val existingIndex = updatedSections.indexOfFirst {
                    it.title.equals(newSection.title, ignoreCase = true)
                }
                if (existingIndex >= 0) {
                    updatedSections[existingIndex] = newSection
                } else {
                    updatedSections.add(newSection)
                }
            }
        }
        action in listOf("delete_section", "remove_section", "delete", "remove", "drop") -> {
            val target = patch.targetSection ?: newSection?.title
            if (!target.isNullOrBlank()) {
                updatedSections.removeAll {
                    it.title.equals(target, ignoreCase = true) ||
                    it.title.contains(target, ignoreCase = true) ||
                    target.contains(it.title, ignoreCase = true)
                }
            }
        }
        else -> {
            // Default: update_section, edit, modify, or unspecified
            if (newSection != null) {
                val target = patch.targetSection ?: newSection.title
                val index = updatedSections.indexOfFirst {
                    it.title.equals(target, ignoreCase = true) ||
                    it.title.contains(target, ignoreCase = true) ||
                    target.contains(it.title, ignoreCase = true)
                }
                if (index >= 0) {
                    updatedSections[index] = newSection
                } else {
                    updatedSections.add(newSection)
                }
            }
        }
    }

    val updatedDiveDeeper = patch.diveDeeper ?: current.diveDeeper
    return current.copy(
        sections = updatedSections,
        diveDeeper = updatedDiveDeeper,
    )
}

/**
 * Parses response from the "Ask or update" bar, supporting block-level patches, dynamic JsonObjects, full summaries, and natural language fallbacks.
 */
fun parseAskOrUpdateResponse(
    raw: String,
    currentSummary: MemorySummaryData,
): Pair<MemorySummaryData, List<RawMemoryAction>>? {
    if (raw.isBlank()) return null
    val stripped = stripThinkingTags(raw).trim()
    val sanitized = sanitizeJsonString(stripped)

    // Stage 1: Try decoding with lenient JsonObject / JsonElement parsing
    try {
        val element = memorySummaryJsonFormat.parseToJsonElement(sanitized)
        if (element is JsonObject) {
            val action = element["action"]?.jsonPrimitive?.contentOrNull ?: "update_section"
            val targetSection = element["target_section"]?.jsonPrimitive?.contentOrNull
                ?: element["target"]?.jsonPrimitive?.contentOrNull
                ?: element["section_title"]?.jsonPrimitive?.contentOrNull

            val rawUpdates = parseRawMemoryActions(element["raw_memory_updates"] ?: element["rawMemoryUpdates"])
            val diveDeeper = parseDiveDeeperActions(element["dive_deeper"] ?: element["diveDeeper"])

            // Check if full summary with "sections"
            val sectionsEl = element["sections"]
            if (sectionsEl is JsonArray && sectionsEl.isNotEmpty()) {
                val parsedSections = sectionsEl.mapNotNull { secEl ->
                    parseSectionFromElement(secEl)
                }
                if (parsedSections.isNotEmpty()) {
                    if (parsedSections.size == 1 && action in listOf("update_section", "add_section")) {
                        val singleSection = parsedSections[0]
                        val patch = MemorySectionPatch(
                            action = action,
                            targetSection = targetSection ?: singleSection.title,
                            section = singleSection,
                            rawMemoryUpdates = rawUpdates,
                            diveDeeper = diveDeeper.ifEmpty { null },
                        )
                        return applySectionPatch(currentSummary, patch) to rawUpdates
                    } else {
                        val fullData = MemorySummaryData(
                            title = element["title"]?.jsonPrimitive?.contentOrNull ?: currentSummary.title,
                            subtitle = element["subtitle"]?.jsonPrimitive?.contentOrNull ?: currentSummary.subtitle,
                            sections = parsedSections,
                            diveDeeper = diveDeeper.ifEmpty { currentSummary.diveDeeper },
                        )
                        return sanitizeMemorySummaryData(fullData) to rawUpdates
                    }
                }
            }

            // Parse section object
            val sectionObj = element["section"] as? JsonObject
            var section = parseSectionFromElement(sectionObj, defaultTitle = targetSection ?: element["title"]?.jsonPrimitive?.contentOrNull)
            if (section == null) {
                // Flat content at root
                section = parseSectionFromElement(element, defaultTitle = targetSection)
            }
            if (section == null) {
                // String section
                val secPrim = (element["section"] as? JsonPrimitive)?.contentOrNull?.trim()
                if (!secPrim.isNullOrBlank()) {
                    val title = targetSection ?: element["title"]?.jsonPrimitive?.contentOrNull ?: "Category"
                    section = MemorySummarySection(icon = guessIconForTitle(title), title = title, content = secPrim)
                }
            }

            if (section != null || action in listOf("delete_section", "remove_section", "delete", "remove")) {
                val patch = MemorySectionPatch(
                    action = action,
                    targetSection = targetSection ?: section?.title,
                    section = section,
                    rawMemoryUpdates = rawUpdates,
                    diveDeeper = diveDeeper.ifEmpty { null },
                )
                return applySectionPatch(currentSummary, patch) to rawUpdates
            }
        }
    } catch (_: Exception) {}

    // Stage 2: Try decoding directly as MemorySectionPatch
    try {
        val patch = memorySummaryJsonFormat.decodeFromString<MemorySectionPatch>(sanitized)
        if (patch.action.isNotBlank() && (patch.section != null || patch.targetSection != null || patch.fullSummary != null)) {
            val updatedSummary = applySectionPatch(currentSummary, patch)
            return updatedSummary to patch.rawMemoryUpdates
        }
    } catch (_: Exception) {}

    // Stage 3: Try decoding directly as MemorySummaryWithUpdates
    try {
        val full = memorySummaryJsonFormat.decodeFromString<MemorySummaryWithUpdates>(sanitized)
        if (full.sections.isNotEmpty()) {
            val data = MemorySummaryData(
                title = full.title,
                subtitle = full.subtitle,
                sections = full.sections,
                diveDeeper = full.diveDeeper.ifEmpty { currentSummary.diveDeeper },
            )
            return sanitizeMemorySummaryData(data) to full.rawMemoryUpdates
        }
    } catch (_: Exception) {}

    // Stage 4: Regex fallback for malformed JSON deltas
    try {
        val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(sanitized)
        val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(sanitized)
        val targetMatch = Regex("\"target_section\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(sanitized)
        val contentMatch = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(sanitized)

        if (contentMatch != null) {
            val action = actionMatch?.groupValues?.getOrNull(1) ?: "update_section"
            val title = titleMatch?.groupValues?.getOrNull(1)
                ?: targetMatch?.groupValues?.getOrNull(1)
                ?: "Category"
            val target = targetMatch?.groupValues?.getOrNull(1) ?: title
            val content = unescapeJsonString(contentMatch.groupValues[1])
            val icon = guessIconForTitle(title)

            val patch = MemorySectionPatch(
                action = action,
                targetSection = target,
                section = MemorySummarySection(icon = icon, title = title, content = content),
            )
            return applySectionPatch(currentSummary, patch) to emptyList()
        }
    } catch (_: Exception) {}

    // Stage 5: Markdown header parser fallback
    val mdFallback = parseMarkdownFallback(raw)
    if (mdFallback != null && mdFallback.sections.isNotEmpty()) {
        return sanitizeMemorySummaryData(mdFallback) to emptyList()
    }

    // Stage 6: Conversational text fallback — STRICTLY DISALLOW RAW JSON STRINGS
    // If stripped text looks like JSON, do NOT dump it into content!
    if (looksLikeRawJson(stripped)) {
        return null
    }

    if (stripped.isNotBlank()) {
        val targetSection = currentSummary.sections.firstOrNull { sec ->
            stripped.contains(sec.title, ignoreCase = true)
        } ?: currentSummary.sections.firstOrNull()

        if (targetSection != null) {
            val updatedSections = currentSummary.sections.map { sec ->
                if (sec == targetSection) {
                    sec.copy(content = stripped)
                } else sec
            }
            return currentSummary.copy(sections = updatedSections) to emptyList()
        }
    }

    return null
}

/**
 * Parses raw LLM response text into MemorySummaryData alongside any raw memory updates.
 */
fun parseMemorySummaryWithUpdates(raw: String): Pair<MemorySummaryData, List<RawMemoryAction>>? {
    if (raw.isBlank()) return null
    val cleaned = sanitizeJsonString(raw)
    return try {
        val parsed = memorySummaryJsonFormat.decodeFromString<MemorySummaryWithUpdates>(cleaned)
        val data = MemorySummaryData(
            title = parsed.title,
            subtitle = parsed.subtitle,
            sections = parsed.sections,
            diveDeeper = parsed.diveDeeper,
        )
        sanitizeMemorySummaryData(data) to parsed.rawMemoryUpdates
    } catch (_: Exception) {
        val fallback = parseMarkdownFallback(raw) ?: return null
        sanitizeMemorySummaryData(fallback) to emptyList()
    }
}

/**
 * Parses raw LLM response text into MemorySummaryData.
 * Resilient against markdown code-fences (```json ... ```) and leading/trailing chatter.
 */
fun parseMemorySummaryJson(raw: String): MemorySummaryData? {
    if (raw.isBlank()) return null
    val cleaned = sanitizeJsonString(raw)
    return try {
        val parsed = memorySummaryJsonFormat.decodeFromString<MemorySummaryData>(cleaned)
        sanitizeMemorySummaryData(parsed)
    } catch (_: Exception) {
        parseMarkdownFallback(raw)?.let { sanitizeMemorySummaryData(it) }
    }
}

/**
 * Renders MemorySummaryData to clean markdown for injection into conversation context.
 */
fun renderMemorySummaryToMarkdown(data: MemorySummaryData): String {
    val clean = sanitizeMemorySummaryData(data)
    if (clean.sections.isEmpty()) return ""
    return buildString {
        appendLine("# ${clean.title}")
        if (clean.subtitle.isNotBlank()) {
            appendLine(clean.subtitle)
        }
        appendLine()
        clean.sections.forEach { section ->
            appendLine("## ${section.title}")
            appendLine(section.content.trim())
            appendLine()
        }
    }.trim()
}

/**
 * Strips reasoning/thinking tags (e.g. <think>...</think>) from raw LLM responses.
 */
fun stripThinkingTags(text: String): String {
    return text.replace(Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL), "").trim()
}

/**
 * Extracts JSON substring if surrounded by markdown code fences or arbitrary text.
 */
fun extractJsonPayload(text: String): String {
    val clean = stripThinkingTags(text).trim()
    val fenceStart = clean.indexOf("```json")
    if (fenceStart != -1) {
        val jsonStart = fenceStart + 7
        val fenceEnd = clean.indexOf("```", jsonStart)
        if (fenceEnd != -1) {
            return clean.substring(jsonStart, fenceEnd).trim()
        }
    }
    val genericFenceStart = clean.indexOf("```")
    if (genericFenceStart != -1) {
        val nextNewline = clean.indexOf('\n', genericFenceStart)
        if (nextNewline != -1) {
            val fenceEnd = clean.indexOf("```", nextNewline)
            if (fenceEnd != -1) {
                return clean.substring(nextNewline, fenceEnd).trim()
            }
        }
    }
    val firstBrace = clean.indexOf('{')
    val lastBrace = clean.lastIndexOf('}')
    if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
        return clean.substring(firstBrace, lastBrace + 1).trim()
    }
    return clean
}

/**
 * Fallback parser when LLM returns plain markdown headers rather than pure JSON.
 */
fun parseMarkdownFallback(raw: String): MemorySummaryData? {
    val lines = raw.lines()
    val sections = mutableListOf<MemorySummarySection>()
    var currentTitle: String? = null
    val currentContent = StringBuilder()

    fun flush() {
        val title = currentTitle?.trim()
        val content = currentContent.toString().trim()
        if (!title.isNullOrBlank() && content.isNotBlank()) {
            val icon = guessIconForTitle(title)
            sections += MemorySummarySection(icon = icon, title = title, content = content)
        }
        currentContent.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
            flush()
            currentTitle = trimmed.removePrefix("## ").removePrefix("# ").trim()
        } else if (trimmed.startsWith("**") && trimmed.endsWith("**") && trimmed.length < 50) {
            flush()
            currentTitle = trimmed.removeSurrounding("**").trim()
        } else {
            currentContent.appendLine(line)
        }
    }
    flush()

    if (sections.isEmpty()) return null
    return MemorySummaryData(
        title = "Your Summary",
        subtitle = "A quick view of what I remember about you.",
        sections = sections,
        diveDeeper = emptyList()
    )
}

fun guessIconForTitle(title: String): String {
    val lower = title.lowercase()
    return when {
        "pref" in lower || "habit" in lower -> "star"
        "interest" in lower || "entertainment" in lower || "hobby" in lower || "music" in lower || "anime" in lower || "bond" in lower || "emotion" in lower || "relat" in lower -> "heart"
        "tech" in lower || "code" in lower || "dev" in lower || "stack" in lower || "hardware" in lower -> "code"
        "device" in lower || "tablet" in lower || "phone" in lower || "pc" in lower -> "devices"
        "context" in lower || "goal" in lower || "current" in lower || "work" in lower || "study" in lower -> "target"
        else -> "sparkles"
    }
}

/**
 * Default system prompt for full memory synthesis.
 */
val DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT = """
You are the Memory Synthesizer. Your job is to analyze the user's recorded memories and produce a cohesive, structured baseline profile.

CORE PRINCIPLES:
1. JUSTICE TO ALL MEMORIES: Do not ignore or discard memories. Synthesize all provided information comprehensively into coherent sections. Preserve specific details, hardware specs, preferences, quirks, entertainment choices, and boundaries.
2. DYNAMIC SECTIONS: Do NOT impose rigid, predefined categories. Create section titles that naturally fit this specific user's life (e.g. Devices, Preferences, Entertainment, Technical Stack, Current Context, Communication Style). If a category is not present in memories, do not generate it.
3. ICON ASSIGNMENTS: For each section, assign an appropriate icon tag from: "star", "heart", "code", "target", "devices", "sparkles", "profile", "book", "music".
4. DIVE DEEPER: Provide 2 to 4 interactive prompts in 'dive_deeper' that allow exploring specific threads or facets of their profile in conversation.
5. JSON OUTPUT: Respond ONLY with a valid JSON object matching the requested schema. No conversational filler or markdown explanations outside the JSON.
""".trimIndent()

/**
 * Builds the prompt instructing the model to generate a full baseline summary.
 */
fun buildFullSummarizerUserPrompt(
    memories: List<String>,
    tokenBudget: Int
): String {
    return buildString {
        appendLine("Generate a comprehensive memory summary based on the following memories.")
        appendLine("TARGET TOKEN BUDGET: Approximately $tokenBudget tokens.")
        appendLine("Do justice to all memories while staying within the target budget.")
        appendLine()
        appendLine("USER MEMORIES (${memories.size} total):")
        memories.forEachIndexed { index, mem ->
            appendLine("[${index + 1}] $mem")
        }
        appendLine()
        appendLine(
            """
            Required JSON format:
            {
              "title": "Your Summary",
              "subtitle": "A quick view of what I remember about you.",
              "sections": [
                {
                  "icon": "devices",
                  "title": "Devices",
                  "content": "Detailed synthesis of user's hardware and setups..."
                },
                {
                  "icon": "heart",
                  "title": "Interests",
                  "content": "Detailed synthesis of entertainment and hobbies..."
                }
              ],
              "dive_deeper": [
                {
                  "icon": "code",
                  "title": "Review my technical preferences",
                  "subtitle": "See what I know about your tech stack, tools and workflows.",
                  "prompt": "Let's review my technical setup and developer preferences."
                }
              ]
            }
            """.trimIndent()
        )
    }
}

/**
 * Builds the incremental patch prompt when a single memory is added/edited/deleted.
 */
fun buildIncrementalSummarizerPrompt(
    currentSummary: MemorySummaryData,
    changeType: MemoryChangeType,
    memoryContent: String,
    tokenBudget: Int
): String {
    val cleanSummary = sanitizeMemorySummaryData(currentSummary)
    val currentJson = memorySummaryJsonFormat.encodeToString(MemorySummaryData.serializer(), cleanSummary)
    return buildString {
        appendLine("A memory change occurred. Update the existing memory summary incrementally.")
        appendLine("CHANGE TYPE: ${changeType.name}")
        appendLine("MEMORY CONTENT: \"$memoryContent\"")
        appendLine("TARGET TOKEN BUDGET: Approximately $tokenBudget tokens.")
        appendLine()
        appendLine("CURRENT SUMMARY JSON:")
        appendLine(currentJson)
        appendLine()
        appendLine("INSTRUCTIONS:")
        appendLine("1. Update, add to, or prune the relevant section(s) to accurately incorporate this memory change.")
        appendLine("2. Leave unaffected sections intact.")
        appendLine("3. Update or retain relevant 'dive_deeper' exploration prompts.")
        appendLine("4. Respond ONLY with the updated JSON object matching the same schema.")
    }
}

/**
 * Builds prompt for the conversational "Ask or update" bar at the bottom of the summary page.
 */
fun buildAskOrUpdatePrompt(
    currentSummary: MemorySummaryData,
    userInstruction: String,
    tokenBudget: Int,
    relevantMemories: List<String> = emptyList(),
): String {
    val cleanSummary = sanitizeMemorySummaryData(currentSummary)
    val sectionsOverview = cleanSummary.sections.joinToString("\n") { s ->
        "Section: [${s.title}] (icon: ${s.icon})\nContent: ${s.content}\n"
    }
    return buildString {
        appendLine("You are the Memory Profile Manager.")
        appendLine("The user has requested an update, elaboration, or category change to their memory summary.")
        appendLine("USER INSTRUCTION: \"$userInstruction\"")
        appendLine("TARGET TOKEN BUDGET: Approximately $tokenBudget tokens.")
        appendLine()
        if (relevantMemories.isNotEmpty()) {
            appendLine("RELEVANT STORED MEMORIES (retrieved from memory database via semantic search):")
            relevantMemories.forEachIndexed { i, mem ->
                appendLine("- $mem")
            }
            appendLine()
        }
        appendLine("CURRENT SECTIONS IN MEMORY SUMMARY:")
        appendLine(sectionsOverview.ifBlank { "No sections exist yet." })
        appendLine()
        appendLine("INSTRUCTIONS (BLOCK-LEVEL DELTA EFFICIENCY):")
        appendLine("Do NOT rewrite the entire summary document. Respond with a JSON delta affecting ONLY the relevant section:")
        appendLine("- To update/expand an existing section: {\"action\": \"update_section\", \"target_section\": \"Section Name\", \"section\": {\"icon\": \"icon_name\", \"title\": \"Section Name\", \"content\": \"Updated or elaborated content...\"}}")
        appendLine("- To add a new category: {\"action\": \"add_section\", \"section\": {\"icon\": \"icon_name\", \"title\": \"New Category Name\", \"content\": \"New category content...\"}}")
        appendLine("- To delete a category: {\"action\": \"delete_section\", \"target_section\": \"Section Name\"}")
        appendLine()
        appendLine("If the user instruction alters, creates, or deletes a durable factual detail (e.g. favorite color, school, project, location), also include 'raw_memory_updates' to synchronize the SQLite database:")
        appendLine("  \"raw_memory_updates\": [")
        appendLine("    {\"action\": \"create\", \"content\": \"User's favorite color is purple\"},")
        appendLine("    {\"action\": \"update\", \"search\": \"favorite color\", \"content\": \"User's favorite color is purple\"},")
        appendLine("    {\"action\": \"delete\", \"search\": \"old note to remove\"}")
        appendLine("  ]")
        appendLine()
        appendLine("Respond ONLY with the JSON object.")
    }
}
