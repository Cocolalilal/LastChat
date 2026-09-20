package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import me.rerere.document.PortableZip
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.SkillExport
import me.rerere.rikkahub.data.skill.PortableSkillPackage
import okio.buffer
import okio.source
import java.io.File

/**
 * Utility for importing and exporting Claude Skills.
 * Supports SKILL.md format (YAML frontmatter + markdown) and app-native JSON.
 */
object SkillExportImport {

    private const val MAX_EDITABLE_FILE_BYTES = 1024 * 1024

    data class PackageEntry(
        val relativePath: String,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val isTextEditable: Boolean,
    )

    private val editableTextExtensions = setOf(
        "", "md", "txt", "json", "yaml", "yml", "xml", "csv", "tsv",
        "py", "sh", "bash", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "kt", "kts", "java", "c", "cc", "cpp", "h", "hpp", "rs", "go",
        "rb", "php", "swift", "toml", "ini", "cfg", "conf", "sql", "html", "css",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /**
     * Export a skill to SKILL.md format (YAML frontmatter + markdown body).
     * This is the standard Claude Skills format.
     */
    fun exportToSkillMd(skill: Skill): String = PortableSkillPackage.exportToSkillMd(
        name = skill.name,
        description = skill.description,
        instructions = skill.instructions,
        license = skill.license,
        compatibility = skill.compatibility,
        allowedTools = skill.allowedTools,
        metadata = skill.metadata,
        disableModelInvocation = skill.disableModelInvocation,
        userInvocable = skill.userInvocable,
        argumentHint = skill.argumentHint,
    )

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
            return portableImport(PortableSkillPackage.importFromString(trimmed))
        }

        // Try JSON as fallback anyway
        val jsonResult = tryImportJson(trimmed)
        if (jsonResult is ImportResult.Success) return jsonResult

        // Try SKILL.md as final fallback
        return portableImport(PortableSkillPackage.importFromString(trimmed))
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

    private fun portableImport(result: PortableSkillPackage.ImportResult): ImportResult {
        return when (result) {
            is PortableSkillPackage.ImportResult.Success -> ImportResult.Success(
                skill = result.parsed.toSkill(),
                format = result.format,
                packageFiles = result.packageFiles,
            )
            is PortableSkillPackage.ImportResult.Error -> ImportResult.Error(result.message)
        }
    }

    private fun PortableSkillPackage.ParsedSkillMd.toSkill(): Skill = Skill(
        name = name,
        description = description,
        instructions = instructions,
        disableModelInvocation = disableModelInvocation,
        userInvocable = userInvocable,
        argumentHint = argumentHint,
        license = license,
        compatibility = compatibility,
        metadata = metadata,
        allowedTools = allowedTools,
    )

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
        if (PortableZip.isArchive(bytes)) portableImport(PortableSkillPackage.importFromBytes(bytes))
        else importFromString(bytes.toString(Charsets.UTF_8))

    /** Keeps hand-authored skills executable from the workspace as real packages. */
    fun syncManagedSkill(context: Context, skill: Skill): Skill {
        val rootName = skill.safePackageRoot()
        val root = File(context.filesDir, "skills/$rootName").apply { mkdirs() }
        File(root, "SKILL.md").writeText(exportToSkillMd(skill))
        return skill.copy(packageRoot = rootName)
    }

    fun ensureManagedSkillPackage(context: Context, skill: Skill): Skill {
        val rootName = skill.safePackageRoot()
        val skillFile = File(context.filesDir, "skills/$rootName/SKILL.md")
        if (!skillFile.isFile) {
            skillFile.parentFile?.mkdirs()
            skillFile.writeText(exportToSkillMd(skill))
        }
        return skill.copy(packageRoot = rootName)
    }

    fun deletePackage(context: Context, skill: Skill) {
        skill.packageRoot
            ?.takeIf { it.matches(Regex("skill-[0-9a-f-]+")) }
            ?.let { File(context.filesDir, "skills/$it").deleteRecursively() }
    }

    fun listPackageEntries(context: Context, skill: Skill): List<PackageEntry> {
        val root = packageRoot(context, skill)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .drop(1)
            .filterNot { it.name.startsWith(".") }
            .map { file ->
                PackageEntry(
                    relativePath = file.relativeTo(root).invariantSeparatorsPath,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    isTextEditable = file.isFile &&
                        file.length() <= MAX_EDITABLE_FILE_BYTES &&
                        file.extension.lowercase() in editableTextExtensions,
                )
            }
            .sortedWith(
                compareBy<PackageEntry>(
                    { it.relativePath.substringBeforeLast('/', "") },
                    { !it.isDirectory },
                    { it.name != "SKILL.md" },
                    { it.name.lowercase() },
                )
            )
            .toList()
    }

    fun readPackageTextFile(context: Context, skill: Skill, relativePath: String): String {
        val target = resolvePackageFile(packageRoot(context, skill), relativePath)
            ?: error("Invalid skill file path")
        require(target.isFile) { "Skill file does not exist" }
        require(target.length() <= MAX_EDITABLE_FILE_BYTES) { "File is too large to edit" }
        val bytes = target.readBytes()
        require(bytes.none { it == 0.toByte() }) { "Binary files cannot be edited as text" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun savePackageTextFile(
        context: Context,
        skill: Skill,
        relativePath: String,
        content: String,
    ) {
        require(content.toByteArray().size <= MAX_EDITABLE_FILE_BYTES) { "File is too large to edit" }
        val root = packageRoot(context, skill).apply { mkdirs() }
        val target = resolvePackageFile(root, relativePath) ?: error("Invalid skill file path")
        require(target != root) { "A file name is required" }
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    fun deletePackageFile(context: Context, skill: Skill, relativePath: String): Boolean {
        require(relativePath != "SKILL.md") { "SKILL.md cannot be deleted" }
        val target = resolvePackageFile(packageRoot(context, skill), relativePath) ?: return false
        return target.isFile && target.delete()
    }

    fun exportPackage(context: Context, skill: Skill): ByteArray {
        val root = skill.packageRoot?.let { File(context.filesDir, "skills/$it") }
        val files = if (root?.isDirectory == true) {
            root.walkTopDown().filter { it.isFile }.associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to file.readBytes()
            }
        } else emptyMap()
        val normalizedFiles = if (files.containsKey("SKILL.md")) {
            files
        } else {
            files + mapOf("SKILL.md" to exportToSkillMd(skill).toByteArray())
        }
        return PortableSkillPackage.exportZip(skill.name.ifBlank { "skill" }, normalizedFiles)
    }

    internal fun resolvePackageFile(root: File, relativePath: String): File? {
        val normalized = relativePath.replace('\\', '/').trim()
        if (normalized.isBlank() || normalized.startsWith('/') || normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }
        val canonicalRoot = root.canonicalFile
        val target = canonicalRoot.resolve(normalized).canonicalFile
        return target.takeIf {
            it.path.startsWith(canonicalRoot.path + File.separator)
        }
    }

    private fun packageRoot(context: Context, skill: Skill): File =
        File(context.filesDir, "skills/${skill.safePackageRoot()}")

    fun getSuggestedFileName(skill: Skill, format: String = "skill_md"): String =
        PortableSkillPackage.suggestedFileName(skill.name, format)
}
