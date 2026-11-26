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
    val id: String = "",
    val icon: String = "star",
    val title: String,
    val content: String,
    @SerialName("cited_memory_ids")
    val citedMemoryIds: List<Int> = emptyList(),
) {
    /** Stable key used for surgical matching even if casing or spacing shifts slightly. */
    fun normalizedKey(): String =
        (id.ifBlank { title })
            .lowercase()
            .replace(NON_ALNUM_REGEX, "_")
            .trim('_')

    companion object {
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
    }
}

@Serializable
data class DiveDeeperAction(
    val icon: String = "sparkles",
    val title: String = "",
    val subtitle: String = "",
    val context: String = "",
    val prompt: String = "",
) {
    fun displayTitle(): String = title.ifBlank { context }.ifBlank { prompt.take(60) }
    fun displaySubtitle(): String = subtitle.ifBlank { if (title.isNotBlank()) context else "" }
}

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
    val id: Int? = null,
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
data class SectionLineEdit(
    val op: String, // "replace", "append", "remove"
    val find: String? = null,
    val replace: String? = null,
)

@Serializable
data class SummaryPatchOperation(
    val action: String, // "update_section", "add_section", "delete_section"
    @SerialName("target_section")
    val targetSection: String? = null,
    val section: MemorySummarySection? = null,
    @SerialName("line_edits")
    val lineEdits: List<SectionLineEdit> = emptyList(),
)

/**
 * Backward-compatible delta patch envelope.
 * Supports multi-section surgical operations via [operations], single-section legacy fields,
 * and read-only question replies via action = "no_op".
 */
@Serializable
data class MemorySectionPatch(
    val action: String = "update_section", // "no_op", "patch_operations", "update_section", "add_section", "delete_section", "full_rewrite"
    @SerialName("assistant_reply")
    val assistantReply: String? = null,
    val operations: List<SummaryPatchOperation> = emptyList(),
    @SerialName("target_section")
    val targetSection: String? = null,
    val section: MemorySummarySection? = null,
    @SerialName("line_edits")
    val lineEdits: List<SectionLineEdit> = emptyList(),
    @SerialName("raw_memory_updates")
    val rawMemoryUpdates: List<RawMemoryAction> = emptyList(),
    @SerialName("dive_deeper")
    val diveDeeper: List<DiveDeeperAction>? = null,
    @SerialName("full_summary")
    val fullSummary: MemorySummaryData? = null,
)

val ALLOWED_SUMMARY_ICONS = setOf(
    "star", "heart", "code", "target", "devices", "sparkles", "profile", "book", "music"
)

/**
 * Checks whether text appears to be raw JSON payload rather than clean markdown or conversational prose.
 */
fun looksLikeRawJson(text: String): Boolean {
    val trimmed = stripThinkingTags(text).trim()
    val unfenced = trimmed
        .removePrefix("```json")
        .removePrefix("```")
        .trim()
    return (unfenced.startsWith("{") && unfenced.contains("\"")) ||
        trimmed.contains("\"action\":") ||
        trimmed.contains("\"target_section\":") ||
        (trimmed.contains("\"sections\"") && trimmed.contains("{") && trimmed.contains("\"content\"")) ||
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
 * Sanitizes all sections in MemorySummaryData, auto-healing legacy single-section
 * raw JSON dumps (e.g. an "Overview" card wrapping an entire serialized summary).
 */
fun sanitizeMemorySummaryData(data: MemorySummaryData): MemorySummaryData {
    if (data.sections.size == 1 && looksLikeRawJson(data.sections[0].content)) {
        val healedFull = parseMemorySummaryJson(data.sections[0].content)
        if (healedFull != null && healedFull.sections.isNotEmpty()) {
            return MemoryPointerParser.hydrateProvenance(
                healedFull.copy(
                    title = healedFull.title.ifBlank { data.title },
                    subtitle = healedFull.subtitle.ifBlank { data.subtitle },
                    diveDeeper = healedFull.diveDeeper.ifEmpty { data.diveDeeper },
                )
            )
        }
    }
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
                val rawPrompt = item["prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val rawTitle = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val rawContext = item["context"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val rawSubtitle = item["subtitle"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val resolvedPrompt = rawPrompt.ifBlank { rawTitle }.ifBlank { rawContext }
                if (resolvedPrompt.isNotBlank()) {
                    val resolvedTitle = rawTitle.ifBlank { rawContext }.ifBlank { resolvedPrompt.take(60) }
                    val resolvedSubtitle = rawSubtitle.ifBlank { if (rawTitle.isNotBlank()) rawContext else "" }
                    val rawIcon = item["icon"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val icon = if (rawIcon in ALLOWED_SUMMARY_ICONS) rawIcon else guessIconForTitle(resolvedTitle)
                    DiveDeeperAction(
                        icon = icon,
                        title = resolvedTitle,
                        subtitle = resolvedSubtitle,
                        context = rawContext,
                        prompt = resolvedPrompt,
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
                val id = item["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val search = item["search"]?.jsonPrimitive?.contentOrNull
                val content = item["content"]?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank() || !search.isNullOrBlank() || id != null) {
                    RawMemoryAction(action = action, id = id, search = search, content = content)
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
    val contentEl = element["content"]
    val content = when (contentEl) {
        is JsonPrimitive -> contentEl.contentOrNull?.trim()
        is JsonArray -> contentEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { if (it.startsWith("- ") || it.startsWith("* ")) it else "- $it" }
            .trim()
        else -> null
    }
    if (content.isNullOrBlank()) return null
    val title = (element["title"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?: defaultTitle
        ?: "Category"
    val icon = (element["icon"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?: guessIconForTitle(title)
    val id = (element["id"] as? JsonPrimitive)?.contentOrNull?.trim() ?: ""
    val safeIcon = if (icon in ALLOWED_SUMMARY_ICONS) icon else guessIconForTitle(title)
    return MemorySummarySection(
        id = id,
        icon = safeIcon,
        title = title,
        content = content,
        citedMemoryIds = MemoryPointerParser.extractIds(content),
    )
}

/**
 * Parses line edit operations from a JSON array element.
 */
fun parseLineEdits(element: JsonElement?): List<SectionLineEdit> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { item ->
        if (item is JsonObject) {
            val op = (item["op"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["action"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: "replace"
            val find = (item["find"] as? JsonPrimitive)?.contentOrNull
                ?: (item["match"] as? JsonPrimitive)?.contentOrNull
                ?: (item["old"] as? JsonPrimitive)?.contentOrNull
                ?: (item["target"] as? JsonPrimitive)?.contentOrNull
            val replace = (item["replace"] as? JsonPrimitive)?.contentOrNull
                ?: (item["content"] as? JsonPrimitive)?.contentOrNull
                ?: (item["new"] as? JsonPrimitive)?.contentOrNull
                ?: (item["value"] as? JsonPrimitive)?.contentOrNull
                ?: (item["text"] as? JsonPrimitive)?.contentOrNull
            SectionLineEdit(op = op, find = find, replace = replace)
        } else null
    }
}

/**
 * Parses summary patch operations from a JSON array element.
 */
fun parsePatchOperations(element: JsonElement?): List<SummaryPatchOperation> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { item ->
        if (item is JsonObject) {
            val opAction = (item["action"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["op"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["type"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: "update_section"
            val opTarget = (item["target_section"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["target"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["title"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (item["section_title"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val opSec = parseSectionFromElement(item["section"] as? JsonObject, defaultTitle = opTarget)
                ?: parseSectionFromElement(item, defaultTitle = opTarget)
            val opLineEdits = parseLineEdits(item["line_edits"] ?: item["lineEdits"])
            SummaryPatchOperation(
                action = opAction,
                targetSection = opTarget,
                section = opSec,
                lineEdits = opLineEdits,
            )
        } else null
    }
}

/**
 * Leniently parses a [MemorySectionPatch] from raw LLM JSON output, tolerating
 * key aliases such as `op`, `title`, `section_title`, `match`, and `content`.
 */
fun parseMemorySectionPatch(raw: String): MemorySectionPatch? {
    if (raw.isBlank()) return null
    val sanitized = sanitizeJsonString(raw)
    if (sanitized.isBlank()) return null

    runCatching {
        val element = memorySummaryJsonFormat.parseToJsonElement(sanitized)
        if (element is JsonObject) {
            val rawAction = (element["action"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (element["op"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val assistantReply = (element["assistant_reply"] as? JsonPrimitive)?.contentOrNull
                ?: (element["assistantReply"] as? JsonPrimitive)?.contentOrNull
            val operations = parsePatchOperations(element["operations"])
            val lineEdits = parseLineEdits(element["line_edits"] ?: element["lineEdits"])
            val rawMemoryUpdates = parseRawMemoryActions(element["raw_memory_updates"] ?: element["rawMemoryUpdates"])
            val diveDeeperEl = element["dive_deeper"] ?: element["diveDeeper"]
            val diveDeeper = if (diveDeeperEl != null) {
                parseDiveDeeperActions(diveDeeperEl).ifEmpty { null }
            } else {
                null
            }
            val fullSummaryObj = (element["full_summary"] ?: element["fullSummary"]) as? JsonObject
            val fullSummary = fullSummaryObj?.let(::parseMemorySummaryFromJsonObject)
            val targetSection = (element["target_section"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (element["target"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (element["section_title"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: (element["title"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

            val hasSectionsArray = element["sections"] is JsonArray
            val section = parseSectionFromElement(element["section"] as? JsonObject, defaultTitle = targetSection)
                ?: if (!hasSectionsArray && operations.isEmpty() && fullSummary == null) {
                    parseSectionFromElement(element, defaultTitle = targetSection)
                } else {
                    null
                }

            val resolvedAction = rawAction ?: when {
                operations.isNotEmpty() -> "patch_operations"
                fullSummary != null -> "full_rewrite"
                lineEdits.isNotEmpty() || section != null -> "update_section"
                else -> null
            }

            if (resolvedAction == null && rawMemoryUpdates.isEmpty() && assistantReply.isNullOrBlank()) {
                return null
            }
            if (hasSectionsArray && rawAction == null && operations.isEmpty() && lineEdits.isEmpty() && fullSummary == null && section == null) {
                return null
            }

            val patch = MemorySectionPatch(
                action = resolvedAction ?: "update_section",
                assistantReply = assistantReply,
                operations = operations,
                targetSection = targetSection ?: section?.title,
                section = section,
                lineEdits = lineEdits,
                rawMemoryUpdates = rawMemoryUpdates,
                diveDeeper = diveDeeper,
                fullSummary = fullSummary,
            )
            val hasPayload = patch.operations.isNotEmpty() ||
                patch.lineEdits.isNotEmpty() ||
                patch.section != null ||
                patch.targetSection != null ||
                patch.fullSummary != null ||
                patch.rawMemoryUpdates.isNotEmpty() ||
                !patch.assistantReply.isNullOrBlank() ||
                patch.action.equals("no_op", ignoreCase = true)
            if (hasPayload) {
                return patch
            }
        }
    }

    return runCatching {
        val decoded = memorySummaryJsonFormat.decodeFromString<MemorySectionPatch>(sanitized)
        val hasPayload = decoded.operations.isNotEmpty() ||
            decoded.lineEdits.isNotEmpty() ||
            decoded.section != null ||
            decoded.targetSection != null ||
            decoded.fullSummary != null ||
            decoded.rawMemoryUpdates.isNotEmpty() ||
            !decoded.assistantReply.isNullOrBlank() ||
            decoded.action.equals("no_op", ignoreCase = true)
        if (hasPayload) decoded else null
    }.getOrNull()
}

/**
 * Applies a block-level delta patch to MemorySummaryData.
 */
fun applySectionPatch(
    current: MemorySummaryData,
    patch: MemorySectionPatch,
): MemorySummaryData {
    return SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false).updated
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

            if (action.equals("no_op", ignoreCase = true)) {
                return currentSummary to rawUpdates
            }

            val operations = parsePatchOperations(element["operations"])
            if (operations.isNotEmpty()) {
                val patch = MemorySectionPatch(
                    action = "patch_operations",
                    operations = operations,
                    rawMemoryUpdates = rawUpdates,
                    diveDeeper = diveDeeper.ifEmpty { null },
                )
                return applySectionPatch(currentSummary, patch) to rawUpdates
            }

            val lineEdits = parseLineEdits(element["line_edits"] ?: element["lineEdits"])
            if (lineEdits.isNotEmpty()) {
                val patch = MemorySectionPatch(
                    action = action,
                    targetSection = targetSection,
                    lineEdits = lineEdits,
                    rawMemoryUpdates = rawUpdates,
                    diveDeeper = diveDeeper.ifEmpty { null },
                )
                return applySectionPatch(currentSummary, patch) to rawUpdates
            }

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
        if (patch.action.isNotBlank() && (patch.section != null || patch.targetSection != null || patch.fullSummary != null || patch.operations.isNotEmpty() || patch.lineEdits.isNotEmpty() || patch.action.equals("no_op", ignoreCase = true))) {
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
 * Leniently extracts a [MemorySummaryData] from a [JsonObject] using [parseSectionFromElement]
 * and [parseDiveDeeperActions], tolerating partial schema drift.
 */
fun parseMemorySummaryFromJsonObject(element: JsonObject): MemorySummaryData? {
    val sectionsEl = element["sections"] ?: return null
    if (sectionsEl !is JsonArray) return null
    val sections = sectionsEl.mapNotNull { parseSectionFromElement(it) }
    if (sections.isEmpty() && sectionsEl.isNotEmpty()) return null
    val title = element["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        ?: "Your Summary"
    val subtitle = element["subtitle"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: "A quick view of what I remember about you."
    val diveDeeper = parseDiveDeeperActions(element["dive_deeper"] ?: element["diveDeeper"])
    return sanitizeMemorySummaryData(
        MemorySummaryData(
            title = title,
            subtitle = subtitle,
            sections = sections,
            diveDeeper = diveDeeper,
        )
    )
}

/**
 * Parses raw LLM response text into MemorySummaryData.
 * Resilient against markdown code-fences (```json ... ```), partial schema drift,
 * and leading/trailing chatter. Never dumps raw JSON into a markdown fallback section.
 */
fun parseMemorySummaryJson(raw: String): MemorySummaryData? {
    if (raw.isBlank()) return null
    val cleaned = sanitizeJsonString(raw)

    // 1. Lenient JsonObject parsing first (handles missing DiveDeeperAction fields, icon aliases, etc.)
    runCatching {
        val element = memorySummaryJsonFormat.parseToJsonElement(cleaned)
        if (element is JsonObject) {
            parseMemorySummaryFromJsonObject(element)?.let { return it }
        }
    }

    // 2. Direct kotlinx.serialization decoding fallback
    runCatching {
        val parsed = memorySummaryJsonFormat.decodeFromString<MemorySummaryData>(cleaned)
        return sanitizeMemorySummaryData(parsed)
    }

    // 3. Never fall back to markdown parsing if the payload looks like raw JSON
    if (looksLikeRawJson(raw)) {
        return null
    }

    return parseMarkdownFallback(raw)?.let { sanitizeMemorySummaryData(it) }
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
 * Extracts JSON substring if surrounded by markdown code fences or arbitrary text,
 * and auto-heals unterminated strings, trailing commas, and unclosed arrays/objects.
 */
fun extractJsonPayload(text: String): String {
    if (text.isBlank()) return ""
    var clean = stripThinkingTags(text).trim()

    val fenceMatch = Regex("(?s)```(?:json)?\\s*([\\{\\[].*?)(?:```|$)").find(clean)
    if (fenceMatch != null) {
        clean = fenceMatch.groupValues[1].trim()
    }

    val firstBrace = clean.indexOf('{')
    val firstBracket = clean.indexOf('[')
    val start = when {
        firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
        firstBrace != -1 -> firstBrace
        firstBracket != -1 -> firstBracket
        else -> return clean
    }
    clean = clean.substring(start).trim()

    val sb = StringBuilder(clean.length + 32)
    val stack = ArrayDeque<Char>()
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
                '\r' -> { /* drop */ }
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        } else {
            when (ch) {
                '{' -> {
                    stack.addLast('}')
                    sb.append(ch)
                }
                '[' -> {
                    stack.addLast(']')
                    sb.append(ch)
                }
                '}', ']' -> {
                    if (stack.isNotEmpty() && stack.last() == ch) {
                        stack.removeLast()
                    }
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }

        if (ch == '\\' && !escape) {
            escape = true
        } else {
            escape = false
        }
    }

    if (inString) {
        if (sb.isNotEmpty() && sb.last() == '\\') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append('"')
    }

    var result = sb.toString().trim()
    while (stack.isNotEmpty()) {
        result = result.trim()
        if (result.endsWith(',')) {
            result = result.substring(0, result.length - 1).trim()
        } else if (result.endsWith(':')) {
            result = "$result null"
        }
        val closer = stack.removeLast()
        result += closer
    }

    return result
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
You are the Memory Synthesizer for LastChat. Your job is to synthesize the user's raw memories into a compact, structured Hierarchical Pointer Index.

CORE PRINCIPLES:
1. PROVENANCE POINTERS (MANDATORY):
   - Every input memory is prefixed with its integer database ID, e.g., [#79].
   - Every distilled bullet or factual statement in your summary MUST end with its backing provenance pointer(s) using exact bracket notation:
     - Single source: `- Uses a Lenovo Tab M9 tablet for mobile-only development [#79]`
     - Multiple sources: `- Prefers dry, deadpan humor and dislikes corporate jargon [#182, #240]`
   - NEVER invent, guess, alter, or extrapolate IDs. Only cite numeric IDs present in the provided input memories.
   - Do not merge unrelated facts into a single vague claim if it obscures which pointer backs which detail.
2. COMPACT INDEX, NOT VERBATIM DUMP:
   - Summarize concisely using Markdown bullet points (`- `). Preserve exact names, hardware specs, preferences, and hard boundaries, while leaving verbose anecdotes in raw storage for on-demand lookup via [#ID].
3. DYNAMIC SECTIONS:
   - Create section titles that fit this specific user organically. Do not force empty categories.
4. ICON ASSIGNMENTS:
   - Assign `icon` strictly from: "star", "heart", "code", "target", "devices", "sparkles", "profile", "book", "music".
   - NEVER use emoji characters anywhere in titles, subtitles, bullets, or dive_deeper prompts.
5. DIVE DEEPER:
   - Provide 2 to 4 concise follow-up prompts in `dive_deeper` without emojis.
6. JSON OUTPUT:
   - Respond ONLY with a valid JSON object matching the requested schema. No markdown fences or prose outside JSON.
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
        appendLine("  Alternatively, use surgical line_edits for 1-3 bullet modifications:")
        appendLine("  {\"action\": \"update_section\", \"target_section\": \"Section Name\", \"line_edits\": [{\"op\": \"replace\", \"find\": \"old bullet\", \"replace\": \"new bullet [#ID]\"}]}")
        appendLine("- To add a new category: {\"action\": \"add_section\", \"section\": {\"icon\": \"icon_name\", \"title\": \"New Category Name\", \"content\": \"New category content...\"}}")
        appendLine("- To delete a category: {\"action\": \"delete_section\", \"target_section\": \"Section Name\"}")
        appendLine("- To answer a user question without modifying the summary: {\"action\": \"no_op\", \"assistant_reply\": \"Concise answer here.\"}")
        appendLine("- For multi-section edits: {\"action\": \"patch_operations\", \"operations\": [...]}")
        appendLine()
        appendLine("PROVENANCE RULES:")
        appendLine("Preserve existing [#ID] citations verbatim. Never invent new IDs.")
        appendLine("Zero emoji characters anywhere in output.")
        appendLine()
        appendLine("If the user instruction alters, creates, or deletes a durable factual detail (e.g. favorite color, school, project, location), also include 'raw_memory_updates' to synchronize the SQLite database:")
        appendLine("  \"raw_memory_updates\": [")
        appendLine("    {\"action\": \"create\", \"content\": \"User's favorite color is purple\"},")
        appendLine("    {\"action\": \"update\", \"id\": 79, \"search\": \"favorite color\", \"content\": \"User's favorite color is purple\"},")
        appendLine("    {\"action\": \"delete\", \"id\": 82, \"search\": \"old note to remove\"}")
        appendLine("  ]")
        appendLine()
        appendLine("Respond ONLY with the JSON object.")
    }
}
