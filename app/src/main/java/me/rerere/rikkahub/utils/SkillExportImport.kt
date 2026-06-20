package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.SkillExport
import okio.buffer
import okio.source

/**
 * Utility for importing and exporting Claude Skills.
 * Supports SKILL.md format (YAML frontmatter + markdown) and app-native JSON.
 */
object SkillExportImport {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /**
     * Export a skill to SKILL.md format (YAML frontmatter + markdown body).
     * This is the standard Claude Skills format.
     */
    fun exportToSkillMd(skill: Skill): String {
        return buildString {
            appendLine("---")
            appendLine("name: ${skill.name}")
            appendLine("description: ${yamlEscapeString(skill.description)}")
            appendLine("---")
            appendLine()
            append(skill.instructions)
        }
    }

    /**
     * Export a skill to app-native JSON format.
     */
    fun exportToJson(skill: Skill): String {
        val export = SkillExport(skill = skill)
        return json.encodeToString(SkillExport.serializer(), export)
    }

    /**
     * Result of importing a skill.
     */
    sealed class ImportResult {
        data class Success(val skill: Skill, val format: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Import a skill from a string. Auto-detects format (SKILL.md or JSON).
     */
    fun importFromString(content: String): ImportResult {
        val trimmed = content.trim()

        // Try JSON first
        if (trimmed.startsWith("{")) {
            return tryImportJson(trimmed)
        }

        // Try SKILL.md format (starts with YAML frontmatter ---)
        if (trimmed.startsWith("---")) {
            return tryImportSkillMd(trimmed)
        }

        // Try JSON as fallback anyway
        val jsonResult = tryImportJson(trimmed)
        if (jsonResult is ImportResult.Success) return jsonResult

        // Try SKILL.md as final fallback
        return tryImportSkillMd(trimmed)
    }

    /**
     * Import a skill from a URI.
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Error("Could not open file")

            val content = inputStream.use { input ->
                input.source().buffer().use { source ->
                    source.readUtf8()
                }
            }

            importFromString(content)
        } catch (e: Exception) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }

    private fun tryImportJson(content: String): ImportResult {
        return try {
            val export = json.decodeFromString(SkillExport.serializer(), content)
            if (export.format == "lastchat_skill") {
                ImportResult.Success(export.skill, "json")
            } else {
                ImportResult.Error("Unsupported JSON format")
            }
        } catch (e: Exception) {
            ImportResult.Error("Invalid JSON: ${e.message}")
        }
    }

    private fun tryImportSkillMd(content: String): ImportResult {
        return try {
            val result = parseSkillMd(content)
            if (result != null) {
                ImportResult.Success(result, "skill_md")
            } else {
                ImportResult.Error("Could not parse SKILL.md format")
            }
        } catch (e: Exception) {
            ImportResult.Error("Failed to parse SKILL.md: ${e.message}")
        }
    }

    /**
     * Parse a SKILL.md file: YAML frontmatter between --- delimiters, then markdown body.
     */
    private fun parseSkillMd(content: String): Skill? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) return null

        // Find the closing --- delimiter
        val closingIndex = trimmed.indexOf("---", startIndex = 3)
        if (closingIndex < 0) return null

        val frontmatter = trimmed.substring(3, closingIndex).trim()
        val body = trimmed.substring(closingIndex + 3).trim()

        // Parse YAML frontmatter (simple key: value pairs)
        val yamlMap = mutableMapOf<String, String>()
        for (line in frontmatter.lines()) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                yamlMap[key] = value
            }
        }

        val name = yamlMap["name"] ?: return null
        val description = yamlMap["description"] ?: ""

        return Skill(
            name = name,
            description = description,
            instructions = body,
            disableModelInvocation = yamlMap["disable-model-invocation"]?.lowercase() == "true",
            userInvocable = yamlMap["user-invocable"]?.lowercase() != "false",
            argumentHint = yamlMap["argument-hint"]?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Escape a string for YAML output. Wraps in quotes if it contains special chars.
     */
    private fun yamlEscapeString(value: String): String {
        return if (value.contains(':') || value.contains('#') || value.contains('\n') ||
            value.contains('"') || value.contains('\'') || value.startsWith(' ') ||
            value.endsWith(' ')
        ) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        } else {
            value
        }
    }

    /**
     * Get file name suggestion based on skill name.
     */
    fun getSuggestedFileName(skill: Skill, format: String = "skill_md"): String {
        val baseName = skill.name.ifEmpty { "skill" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "json" -> "${baseName}.json"
            else -> "${baseName}_SKILL.md"
        }
    }
}
