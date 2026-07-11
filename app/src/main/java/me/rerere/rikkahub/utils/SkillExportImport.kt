package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.SkillExport
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Utility for importing and exporting Claude Skills.
 * Supports SKILL.md format (YAML frontmatter + markdown) and app-native JSON.
 */
object SkillExportImport {

    private const val MAX_PACKAGE_FILES = 256
    private const val MAX_PACKAGE_BYTES = 32 * 1024 * 1024
    private const val MAX_SINGLE_FILE_BYTES = 8 * 1024 * 1024

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
            skill.license?.takeIf { it.isNotBlank() }?.let { appendLine("license: ${yamlEscapeString(it)}") }
            skill.compatibility?.takeIf { it.isNotBlank() }?.let { appendLine("compatibility: ${yamlEscapeString(it)}") }
            skill.allowedTools?.takeIf { it.isNotBlank() }?.let { appendLine("allowed-tools: ${yamlEscapeString(it)}") }
            if (skill.metadata.isNotEmpty()) {
                appendLine("metadata:")
                skill.metadata.toSortedMap().forEach { (key, value) ->
                    appendLine("  $key: ${yamlEscapeString(value)}")
                }
            }
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
        data class Success(
            val skill: Skill,
            val format: String,
            /** Files relative to the skill root, including SKILL.md. */
            val packageFiles: Map<String, ByteArray> = emptyMap(),
        ) : ImportResult()
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

            val bytes = inputStream.use { input ->
                input.source().buffer().use { source ->
                    source.readByteArray()
                }
            }
            importFromBytes(bytes)
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
                ImportResult.Success(result, "skill_md", mapOf("SKILL.md" to content.toByteArray()))
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
        val description = yamlMap["description"] ?: ""

        return Skill(
            name = name,
            description = description,
            instructions = body,
            disableModelInvocation = yamlMap["disable-model-invocation"]?.lowercase() == "true",
            userInvocable = yamlMap["user-invocable"]?.lowercase() != "false",
            argumentHint = yamlMap["argument-hint"]?.takeIf { it.isNotBlank() },
            license = yamlMap["license"]?.takeIf { it.isNotBlank() },
            compatibility = yamlMap["compatibility"]?.takeIf { it.isNotBlank() },
            metadata = metadata,
            allowedTools = yamlMap["allowed-tools"]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Installs imported files under a private, stable directory and returns the
     * persisted skill. Archive paths are already validated by [tryImportZip].
     */
    fun installPackage(context: Context, imported: ImportResult.Success): Skill {
        val rootName = "skill-${imported.skill.id}"
        val root = File(context.filesDir, "skills/$rootName")
        val staging = File(context.filesDir, "skills/.$rootName-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val files = imported.packageFiles.ifEmpty {
                mapOf("SKILL.md" to exportToSkillMd(imported.skill).toByteArray())
            }
            files.forEach { (relativePath, bytes) ->
                val target = File(staging, relativePath)
                require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            root.deleteRecursively()
            require(staging.renameTo(root)) { "Could not install skill package" }
            return imported.skill.copy(packageRoot = rootName, updatedAt = System.currentTimeMillis())
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun importFromBytes(bytes: ByteArray): ImportResult =
        if (bytes.isZipArchive()) tryImportZip(bytes) else importFromString(bytes.toString(Charsets.UTF_8))

    /** Keeps hand-authored skills executable from the workspace as real packages. */
    fun syncManagedSkill(context: Context, skill: Skill): Skill {
        val rootName = skill.safePackageRoot()
        val root = File(context.filesDir, "skills/$rootName").apply { mkdirs() }
        File(root, "SKILL.md").writeText(exportToSkillMd(skill))
        return skill.copy(packageRoot = rootName)
    }

    fun deletePackage(context: Context, skill: Skill) {
        skill.packageRoot
            ?.takeIf { it.matches(Regex("skill-[0-9a-f-]+")) }
            ?.let { File(context.filesDir, "skills/$it").deleteRecursively() }
    }

    fun exportPackage(context: Context, skill: Skill): ByteArray {
        val root = skill.packageRoot?.let { File(context.filesDir, "skills/$it") }
        val files = if (root?.isDirectory == true) {
            root.walkTopDown().filter { it.isFile }.associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to file.readBytes()
            }
        } else emptyMap()
        val normalizedFiles = files + mapOf("SKILL.md" to exportToSkillMd(skill).toByteArray())
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                normalizedFiles.toSortedMap().forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry("${skill.name.ifBlank { "skill" }}/$path"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun tryImportZip(bytes: ByteArray): ImportResult {
        return try {
            val entries = linkedMapOf<String, ByteArray>()
            var totalBytes = 0
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    require(entries.size < MAX_PACKAGE_FILES) { "Skill package contains too many files" }
                    val path = entry.name.normalizeArchivePath()
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        require(totalBytes <= MAX_PACKAGE_BYTES) { "Skill package is too large" }
                        require(output.size() + read <= MAX_SINGLE_FILE_BYTES) { "$path is too large" }
                        output.write(buffer, 0, read)
                    }
                    entries[path] = output.toByteArray()
                }
            }
            val skillEntries = entries.filterKeys { it == "SKILL.md" || it.endsWith("/SKILL.md") }
            require(skillEntries.size == 1) { "A skill package must contain exactly one SKILL.md" }
            val rawSkillPath = skillEntries.keys.single()
            val prefix = rawSkillPath.removeSuffix("SKILL.md")
            // A package may contain either SKILL.md at the archive root or one skill root.
            require(entries.keys.all { it.startsWith(prefix) }) { "Skill package must contain one skill root" }
            val packageFiles = entries.mapKeys { (path, _) -> path.removePrefix(prefix) }
            when (val parsed = importFromString(packageFiles.getValue("SKILL.md").toString(Charsets.UTF_8))) {
                is ImportResult.Success -> parsed.copy(format = "skill_package", packageFiles = packageFiles)
                is ImportResult.Error -> parsed
            }
        } catch (error: Exception) {
            ImportResult.Error("Invalid skill package: ${error.message ?: "unknown error"}")
        }
    }

    private fun ByteArray.isZipArchive(): Boolean = size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte()

    private fun String.normalizeArchivePath(): String {
        val normalized = replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && !normalized.split('/').any { it == ".." || it.isBlank() }) { "Unsafe archive path" }
        return normalized
    }

    private fun yamlValue(raw: String): String = raw.trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .replace("\\n", "\n")

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
