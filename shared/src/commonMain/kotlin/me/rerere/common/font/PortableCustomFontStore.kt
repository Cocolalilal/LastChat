package me.rerere.common.font

import me.rerere.common.platform.PlatformFileStore
import kotlin.time.Clock

data class PortableImportedFont(
    val storagePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val isVariable: Boolean,
    val axes: List<PortableFontAxis>,
)

/**
 * Path-keyed custom-font store. Android and iOS both persist under `custom_fonts/`
 * in [PlatformFileStore] so backup/import can share the same relative paths.
 */
class PortableCustomFontStore(
    private val files: PlatformFileStore,
    private val directory: String = DEFAULT_DIRECTORY,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun import(bytes: ByteArray, displayName: String): PortableImportedFont? {
        if (!OpenTypeFontTables.isOpenTypeFont(bytes)) return null
        val sanitized = displayName.replace(UNSAFE_NAME, "_").ifBlank { "CustomFont" }
        val extension = extensionOf(displayName).ifEmpty { "ttf" }
        val baseName = sanitized.substringBeforeLast('.').ifBlank { "CustomFont" }
        val storagePath = "$directory/${clock()}_$baseName.$extension"
        files.writeBytes(storagePath, bytes)
        return describe(storagePath, bytes, baseName)
    }

    suspend fun load(path: String): ByteArray? = files.readBytes(normalize(path))

    suspend fun delete(path: String): Boolean = files.delete(normalize(path))

    suspend fun list(): List<PortableImportedFont> {
        return files.listFiles(directory).mapNotNull { path ->
            val bytes = files.readBytes(path) ?: return@mapNotNull null
            if (!OpenTypeFontTables.isOpenTypeFont(bytes)) return@mapNotNull null
            describe(path, bytes, displayNameOf(path))
        }
    }

    private fun describe(
        storagePath: String,
        bytes: ByteArray,
        displayName: String,
    ): PortableImportedFont {
        val axes = OpenTypeFontTables.parseVariableAxes(bytes)
        return PortableImportedFont(
            storagePath = storagePath,
            displayName = displayName.replace("_", " "),
            sizeBytes = bytes.size.toLong(),
            isVariable = axes.isNotEmpty(),
            axes = axes,
        )
    }

    private fun normalize(path: String): String {
        val trimmed = path.replace('\\', '/').trimStart('/')
        return if (trimmed.startsWith("$directory/")) trimmed else "$directory/${trimmed.substringAfterLast('/')}"
    }

    private fun displayNameOf(path: String): String {
        val fileName = path.substringAfterLast('/')
        return fileName.substringAfter('_', fileName).substringBeforeLast('.')
    }

    private fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(dot + 1).lowercase() else ""
    }

    companion object {
        const val DEFAULT_DIRECTORY = "custom_fonts"
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}
