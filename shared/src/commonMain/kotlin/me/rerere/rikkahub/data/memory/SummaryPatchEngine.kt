package me.rerere.rikkahub.data.memory

val DELTA_PATCH_SYSTEM_PROMPT = """
You are the Memory Summary Patch Engine. You modify an existing Hierarchical Pointer Memory Summary with minimal, surgical precision.

HARD RULES:
1. NEVER EMIT A FULL DOCUMENT FOR MINOR CHANGES:
   - Use `action: "patch_operations"` with one or more targeted `operations` (`update_section`, `add_section`, `delete_section`).
   - Do NOT repeat or echo untouched sections.
   - `action: "full_rewrite"` is STRICTLY FORBIDDEN unless the user explicitly commands a complete restructuring, total rewrite, or full language translation of the entire profile.
2. LINE-LEVEL SURGICAL EDITS FIRST:
   - Inside `update_section`, prefer `line_edits` (`op: "replace" | "append" | "remove"`) over rewriting the full `section.content` whenever modifying 1-3 bullets inside an existing section.
   - Preserve all unaffected bullets and their existing [#ID] citations verbatim.
3. QUESTION-ONLY MODE:
   - If the user is only asking a question about their summary without requesting any edit, return `action: "no_op"` with `assistant_reply` answering concisely.
4. PROVENANCE INTEGRITY:
   - Never fabricate new [#ID] numbers. Keep existing [#ID] pointers intact unless that specific fact is removed or superseded.
5. NO EMOJIS:
   - Zero emoji characters anywhere in JSON values or `assistant_reply`.
6. OUTPUT FORMAT:
   - Return ONLY a valid JSON object matching the `MemorySectionPatch` schema.
""".trimIndent()

data class PatchApplyResult(
    val updated: MemorySummaryData,
    val modifiedSectionKeys: Set<String>,
    val rejectedFullRewrite: Boolean = false,
    val warnings: List<String> = emptyList(),
)

object SummaryPatchEngine {
    private val FULL_REWRITE_INTENT_REGEX = Regex(
        """\b(rewrite\s+(the\s+)?(whole|entire|full)|rebuild\s+from\s+scratch|start\s+over|reorganize\s+everything|translate\s+(the\s+)?entire)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun userExplicitlyRequestedFullRewrite(userInstruction: String): Boolean =
        FULL_REWRITE_INTENT_REGEX.containsMatchIn(userInstruction)

    fun applyPatch(
        current: MemorySummaryData,
        patch: MemorySectionPatch,
        allowFullRewrite: Boolean,
    ): PatchApplyResult {
        val warnings = mutableListOf<String>()

        if (patch.action.equals("no_op", ignoreCase = true)) {
            return PatchApplyResult(updated = current, modifiedSectionKeys = emptySet())
        }

        // Blast-radius guard against monolithic rewrite drift
        if (patch.action.equals("full_rewrite", ignoreCase = true) ||
            (patch.fullSummary != null && patch.fullSummary.sections.isNotEmpty() && patch.operations.isEmpty() && patch.section == null && patch.targetSection == null)
        ) {
            val candidate = patch.fullSummary
            if (candidate == null || candidate.sections.isEmpty()) {
                warnings.add("Ignored empty full_rewrite payload.")
                return PatchApplyResult(current, emptySet(), rejectedFullRewrite = true, warnings = warnings)
            }
            if (!allowFullRewrite && current.sections.size >= 2) {
                // Convert unauthorized full_rewrite into a safe section diff preserving untouched originals
                return mergeUnauthorizedRewriteSafely(current, candidate, warnings)
            }
            val hydrated = MemoryPointerParser.hydrateProvenance(candidate)
            return PatchApplyResult(
                updated = hydrated,
                modifiedSectionKeys = hydrated.sections.map { it.normalizedKey() }.toSet(),
                warnings = warnings,
            )
        }

        val ops = buildList {
            if (patch.operations.isNotEmpty()) {
                addAll(patch.operations)
            } else if (patch.action in setOf("update_section", "add_section", "delete_section")) {
                add(
                    SummaryPatchOperation(
                        action = patch.action,
                        targetSection = patch.targetSection,
                        section = patch.section,
                        lineEdits = patch.lineEdits,
                    )
                )
            }
        }

        if (ops.isEmpty()) {
            warnings.add("Patch contained no executable operations.")
            return PatchApplyResult(current, emptySet(), warnings = warnings)
        }

        val mutableSections = current.sections.toMutableList()
        val touchedKeys = linkedSetOf<String>()

        for (op in ops) {
            when (op.action.lowercase()) {
                "update_section" -> {
                    val targetName = op.targetSection ?: op.section?.title
                    val idx = findSectionIndex(mutableSections, targetName)
                    if (idx < 0) {
                        // Graceful fallback: if LLM targeted a non-existent section with content, append it
                        val fallbackSection = op.section
                        if (fallbackSection != null && fallbackSection.content.isNotBlank()) {
                            mutableSections.add(fallbackSection)
                            touchedKeys.add(fallbackSection.normalizedKey())
                            warnings.add("Section '$targetName' not found; added as new section.")
                        } else {
                            warnings.add("Target section '$targetName' not found; skipped.")
                        }
                    } else {
                        val existing = mutableSections[idx]
                        val updatedContent = when {
                            op.lineEdits.isNotEmpty() -> applyLineEdits(existing.content, op.lineEdits, warnings)
                            !op.section?.content.isNullOrBlank() -> op.section!!.content
                            else -> existing.content
                        }
                        val updatedSection = existing.copy(
                            title = op.section?.title?.takeIf { it.isNotBlank() } ?: existing.title,
                            icon = op.section?.icon?.takeIf { it in ALLOWED_SUMMARY_ICONS } ?: existing.icon,
                            content = updatedContent,
                        )
                        mutableSections[idx] = updatedSection
                        touchedKeys.add(existing.normalizedKey())
                        touchedKeys.add(updatedSection.normalizedKey())
                    }
                }

                "add_section" -> {
                    val sec = op.section
                    if (sec == null || sec.title.isBlank() || sec.content.isBlank()) {
                        warnings.add("Skipped invalid add_section with blank title/content.")
                    } else {
                        val existingIdx = findSectionIndex(mutableSections, sec.title)
                        if (existingIdx >= 0) {
                            // Merge into existing section rather than creating duplicate headings
                            val existing = mutableSections[existingIdx]
                            val merged = existing.copy(
                                content = (existing.content.trimEnd() + "\n" + sec.content.trimStart()).trim()
                            )
                            mutableSections[existingIdx] = merged
                            touchedKeys.add(merged.normalizedKey())
                        } else {
                            mutableSections.add(sec)
                            touchedKeys.add(sec.normalizedKey())
                        }
                    }
                }

                "delete_section" -> {
                    val idx = findSectionIndex(mutableSections, op.targetSection)
                    if (idx >= 0) {
                        val removed = mutableSections.removeAt(idx)
                        touchedKeys.add(removed.normalizedKey())
                    } else {
                        warnings.add("Section '${op.targetSection}' not found for deletion.")
                    }
                }

                else -> warnings.add("Unknown operation '${op.action}'.")
            }
        }

        // Prevent accidental total data wipe
        if (mutableSections.isEmpty() && current.sections.isNotEmpty()) {
            warnings.add("Blocked patch that would empty the entire summary.")
            return PatchApplyResult(current, emptySet(), warnings = warnings)
        }

        val updated = MemoryPointerParser.hydrateProvenance(
            current.copy(
                sections = mutableSections,
                diveDeeper = patch.diveDeeper?.takeIf { it.isNotEmpty() } ?: current.diveDeeper,
            )
        )
        return PatchApplyResult(updated = updated, modifiedSectionKeys = touchedKeys, warnings = warnings)
    }

    fun findSectionIndex(sections: List<MemorySummarySection>, query: String?): Int {
        if (query.isNullOrBlank()) return -1
        val trimmed = query.trim()
        // 1. Exact title match
        sections.indexOfFirst { it.title == trimmed }.takeIf { it >= 0 }?.let { return it }
        // 2. Case-insensitive title match
        sections.indexOfFirst { it.title.equals(trimmed, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        // 3. Normalized key match
        val queryKey = MemorySummarySection(title = trimmed, content = "").normalizedKey()
        sections.indexOfFirst { it.normalizedKey() == queryKey }.takeIf { it >= 0 }?.let { return it }
        // 4. Unique substring match
        val partialMatches = sections.withIndex().filter { (_, s) ->
            s.title.contains(trimmed, ignoreCase = true) || trimmed.contains(s.title, ignoreCase = true)
        }
        return if (partialMatches.size == 1) partialMatches.first().index else -1
    }

    private fun applyLineEdits(
        original: String,
        edits: List<SectionLineEdit>,
        warnings: MutableList<String>,
    ): String {
        var result = original
        for (edit in edits) {
            when (edit.op.lowercase()) {
                "replace" -> {
                    val needle = edit.find
                    val replacement = edit.replace ?: ""
                    if (needle.isNullOrEmpty()) {
                        warnings.add("Skipped replace edit without 'find'.")
                    } else if (result.contains(needle)) {
                        result = result.replaceFirst(needle, replacement)
                    } else {
                        // Whitespace-tolerant line match fallback
                        val normalizedNeedle = needle.trim()
                        val lines = result.lines().toMutableList()
                        val lineIdx = lines.indexOfFirst { it.trim() == normalizedNeedle }
                        if (lineIdx >= 0) {
                            lines[lineIdx] = replacement
                            result = lines.joinToString("\n")
                        } else {
                            warnings.add("Could not locate block to replace: '${needle.take(40)}'.")
                        }
                    }
                }
                "append" -> {
                    val addition = edit.replace?.trim()
                    if (!addition.isNullOrEmpty()) {
                        result = if (result.isBlank()) addition else "${result.trimEnd()}\n$addition"
                    }
                }
                "remove" -> {
                    val needle = edit.find?.trim()
                    if (!needle.isNullOrEmpty()) {
                        result = result.lines()
                            .filterNot { it.trim() == needle || it.contains(needle) }
                            .joinToString("\n")
                            .trim()
                    }
                }
            }
        }
        return result.trim()
    }

    private fun mergeUnauthorizedRewriteSafely(
        current: MemorySummaryData,
        candidate: MemorySummaryData,
        warnings: MutableList<String>,
    ): PatchApplyResult {
        warnings.add("Model attempted unauthorized full_rewrite; merged changed sections conservatively.")
        val currentByKey = current.sections.associateBy { it.normalizedKey() }
        val merged = current.sections.toMutableList()
        val touched = linkedSetOf<String>()

        for (sec in candidate.sections) {
            val key = sec.normalizedKey()
            val existing = currentByKey[key]
            if (existing == null) {
                merged.add(sec)
                touched.add(key)
            } else if (existing.content.trim() != sec.content.trim()) {
                val idx = merged.indexOfFirst { it.normalizedKey() == key }
                if (idx >= 0) {
                    merged[idx] = existing.copy(content = sec.content)
                    touched.add(key)
                }
            }
        }
        return PatchApplyResult(
            updated = MemoryPointerParser.hydrateProvenance(current.copy(sections = merged)),
            modifiedSectionKeys = touched,
            rejectedFullRewrite = true,
            warnings = warnings,
        )
    }
}
