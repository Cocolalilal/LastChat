package me.rerere.rikkahub.data.skill

import me.rerere.document.PortableZip
import me.rerere.rikkahub.data.prompt.PortableSkill
import kotlin.uuid.Uuid

/**
 * Claude / LastChat skill package: YAML-frontmatter `SKILL.md` plus optional
 * zip assets. Shared by Android [me.rerere.rikkahub.utils.SkillExportImport]
 * and iOS import/export.
 */
object PortableSkillPackage {
    fun exportToSkillMd(
        name: String,
        description: String,
        instructions: String,
        license: String? = null,
        compatibility: String? = null,
        allowedTools: String? = null,
        metadata: Map<String, String> = emptyMap(),
        disableModelInvocation: Boolean = false,
        userInvocable: Boolean = true,
        argumentHint: String? = null,
    ): String = buildString {
        appendLine("---")
        appendLine("name: $name")
        appendLine("description: ${yamlEscape(description)}")
        license?.takeIf { it.isNotBlank() }?.let { appendLine("license: ${yamlEscape(it)}") }
        compatibility?.takeIf { it.isNotBlank() }?.let { appendLine("compatibility: ${yamlEscape(it)}") }
        allowedTools?.takeIf { it.isNotBlank() }?.let { appendLine("allowed-tools: ${yamlEscape(it)}") }
        if (disableModelInvocation) appendLine("disable-model-invocation: true")
        if (!userInvocable) appendLine("user-invocable: false")
        argumentHint?.takeIf { it.isNotBlank() }?.let { appendLine("argument-hint: ${yamlEscape(it)}") }
        if (metadata.isNotEmpty()) {
            appendLine("metadata:")
            metadata.toSortedMap().forEach { (key, value) ->
                appendLine("  $key: ${yamlEscape(value)}")
            }
        }
        appendLine("---")
        appendLine()
        append(instructions)
    }

    fun exportToSkillMd(skill: PortableSkill): String = exportToSkillMd(
        name = skill.name,
        description = skill.description,
        instructions = skill.instructions,
        license = skill.license,
        compatibility = skill.compatibility,
        allowedTools = skill.allowedTools,
        metadata = skill.metadata,
        disableModelInvocation = skill.disableModelInvocation,
    )

    fun exportZip(skillName: String, files: Map<String, ByteArray>): ByteArray {
        val prefix = skillName.ifBlank { "skill" }
        val normalized = if (files.containsKey("SKILL.md")) files else {
            files + ("SKILL.md" to exportToSkillMd(name = prefix, description = "", instructions = "").encodeToByteArray())
        }
        val prefixed = normalized.toSortedMap().mapKeys { (path, _) -> "$prefix/$path" }
        return PortableZip.writeStoreArchive(prefixed)
    }

    fun importFromBytes(bytes: ByteArray): ImportResult {
        return if (PortableZip.isArchive(bytes)) importZip(bytes) else importFromString(bytes.decodeToString())
    }

    fun importFromString(content: String): ImportResult {
        return try {
            val parsed = parseSkillMd(content) ?: return ImportResult.Error("Could not parse SKILL.md format")
            ImportResult.Success(
                parsed = parsed,
                format = "skill_md",
                packageFiles = mapOf("SKILL.md" to content.encodeToByteArray()),
            )
        } catch (error: Exception) {
            ImportResult.Error("Failed to parse SKILL.md: ${error.message}")
        }
    }

    fun suggestedFileName(name: String, format: String = "skill_md"): String {
        val baseName = name.ifBlank { "skill" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "json" -> "$baseName.json"
            "zip" -> "$baseName.zip"
            else -> "${baseName}_SKILL.md"
        }
    }

    fun safePackageRoot(id: String, packageRoot: String? = null): String {
        val candidate = packageRoot?.takeIf { it.matches(Regex("skill-[0-9a-f-]+")) }
        return candidate ?: "skill-$id"
    }

    sealed class ImportResult {
        data class Success(
            val parsed: ParsedSkillMd,
            val format: String,
            val packageFiles: Map<String, ByteArray>,
        ) : ImportResult() {
            fun toPortableSkill(id: String = Uuid.random().toString()): PortableSkill {
                val root = safePackageRoot(id)
                val bundled = packageFiles.keys
                    .filterNot { it.equals("SKILL.md", ignoreCase = true) }
                    .sorted()
                    .take(64)
                return PortableSkill(
                    id = id,
                    name = parsed.name,
                    description = parsed.description,
                    instructions = parsed.instructions,
                    disableModelInvocation = parsed.disableModelInvocation,
                    compatibility = parsed.compatibility,
                    license = parsed.license,
                    allowedTools = parsed.allowedTools,
                    metadata = parsed.metadata,
                    packageRoot = root,
                    workspaceDirectory = "/skills/$root",
                    bundledResources = bundled.map { "/skills/$root/$it" },
                )
            }
        }

        data class Error(val message: String) : ImportResult()
    }

    data class ParsedSkillMd(
        val name: String,
        val description: String,
        val instructions: String,
        val license: String? = null,
        val compatibility: String? = null,
        val allowedTools: String? = null,
        val metadata: Map<String, String> = emptyMap(),
        val disableModelInvocation: Boolean = false,
        val userInvocable: Boolean = true,
        val argumentHint: String? = null,
    )

    private fun importZip(bytes: ByteArray): ImportResult {
        return try {
            val entries = PortableZip.extractEntries(bytes)
            val skillEntries = entries.filterKeys { it == "SKILL.md" || it.endsWith("/SKILL.md") }
            require(skillEntries.size == 1) { "A skill package must contain exactly one SKILL.md" }
            val rawSkillPath = skillEntries.keys.single()
            val prefix = rawSkillPath.removeSuffix("SKILL.md")
            require(entries.keys.all { it.startsWith(prefix) }) { "Skill package must contain one skill root" }
            val packageFiles = entries.mapKeys { (path, _) -> path.removePrefix(prefix) }
            when (val parsed = importFromString(packageFiles.getValue("SKILL.md").decodeToString())) {
                is ImportResult.Success -> parsed.copy(format = "skill_package", packageFiles = packageFiles)
                is ImportResult.Error -> parsed
            }
        } catch (error: Exception) {
            ImportResult.Error("Invalid skill package: ${error.message ?: "unknown error"}")
        }
    }

    private fun parseSkillMd(content: String): ParsedSkillMd? {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) return null
        val closingIndex = trimmed.indexOf("---", startIndex = 3)
        if (closingIndex < 0) return null
        val frontmatter = trimmed.substring(3, closingIndex).trim()
        val body = trimmed.substring(closingIndex + 3).trim()
        val yamlMap = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        var inMetadata = false
        for (line in frontmatter.lines()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (line.trim() == "metadata:") {
                inMetadata = true
                continue
            }
            if (!line.startsWith(' ') && !line.startsWith('\t')) inMetadata = false
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = yamlValue(line.substring(colonIndex + 1))
                if (inMetadata && (line.startsWith(' ') || line.startsWith('\t'))) {
                    metadata[key] = value
                } else {
                    yamlMap[key] = value
                }
            }
        }
        val name = yamlMap["name"] ?: return null
        return ParsedSkillMd(
            name = name,
            description = yamlMap["description"].orEmpty(),
            instructions = body,
            license = yamlMap["license"]?.takeIf { it.isNotBlank() },
            compatibility = yamlMap["compatibility"]?.takeIf { it.isNotBlank() },
            allowedTools = yamlMap["allowed-tools"]?.takeIf { it.isNotBlank() },
            metadata = metadata,
            disableModelInvocation = yamlMap["disable-model-invocation"]?.lowercase() == "true",
            userInvocable = yamlMap["user-invocable"]?.lowercase() != "false",
            argumentHint = yamlMap["argument-hint"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun yamlValue(raw: String): String = raw.trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .replace("\\n", "\n")

    private fun yamlEscape(value: String): String {
        return if (value.contains(':') || value.contains('#') || value.contains('\n') ||
            value.contains('"') || value.contains('\'') || value.startsWith(' ') ||
            value.endsWith(' ')
        ) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        } else {
            value
        }
    }
}
