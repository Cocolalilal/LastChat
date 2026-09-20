package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.font.OpenTypeFontTables
import me.rerere.common.font.PortableFontAxis
import me.rerere.rikkahub.data.datastore.FontAxis
import me.rerere.rikkahub.data.datastore.FontFeature
import okio.buffer
import okio.sink
import java.io.File

private const val TAG = "FontFileManager"

/**
 * Manages custom font files for the app.
 * Handles importing fonts from content URIs, storing them internally,
 * and detecting font features/axes.
 */
class FontFileManager(private val context: Context) {
    
    private val fontsDir: File = context.filesDir.resolve("custom_fonts").also { it.mkdirs() }
    
    /**
     * Import a font file from a content URI to internal storage.
     * @param uri The content URI of the font file
     * @param displayName The display name for the font
     * @return The internal file path, or null on failure
     */
    suspend fun importFont(uri: Uri, displayName: String): String? = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = getExtension(displayName).ifEmpty { "ttf" }
            val fileName = "${System.currentTimeMillis()}_$sanitizedName.$extension"
            val destFile = File(fontsDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.sink().buffer().outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for font")
                return@withContext null
            }
            
            // Verify the font is a real OpenType file and Android can create a Typeface.
            val importedBytes = destFile.readBytes()
            if (!OpenTypeFontTables.isOpenTypeFont(importedBytes)) {
                Log.e(TAG, "Rejected non-OpenType font file")
                destFile.delete()
                return@withContext null
            }
            try {
                val typeface = Typeface.createFromFile(destFile)
                if (typeface == null) {
                    Log.e(TAG, "Failed to create typeface from font file")
                    destFile.delete()
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate font file", e)
                destFile.delete()
                return@withContext null
            }
            
            Log.d(TAG, "Imported font: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import font", e)
            null
        }
    }
    
    /**
     * Delete a custom font file.
     */
    fun deleteFont(internalPath: String): Boolean {
        return try {
            File(internalPath).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete font: $internalPath", e)
            false
        }
    }
    
    /**
     * List all custom fonts stored in internal storage.
     */
    fun listFonts(): List<CustomFontInfo> {
        return fontsDir.listFiles()?.mapNotNull { file ->
            try {
                val typeface = Typeface.createFromFile(file)
                if (typeface != null) {
                    CustomFontInfo(
                        path = file.absolutePath,
                        displayName = extractFontName(file.name),
                        size = file.length(),
                        isVariable = OpenTypeFontTables.isVariableFont(file.readBytes()),
                        supportedAxes = OpenTypeFontTables.parseVariableAxes(file.readBytes()).map { it.tag }
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
    
    /**
     * Detect variable font axes from a font file.
     * Uses font table parsing for Android 8.0+ or falls back to common axes.
     */
    fun detectFontAxes(fontPath: String): List<FontAxis> {
        val file = File(fontPath)
        if (!file.exists()) return emptyList()
        return try {
            OpenTypeFontTables.parseVariableAxes(file.readBytes()).map { it.toFontAxis() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect axes for $fontPath", e)
            emptyList()
        }
    }
    
    /**
     * Detect OpenType features from a font file.
     */
    fun detectFontFeatures(fontPath: String): List<FontFeature> {
        val file = File(fontPath)
        if (!file.exists()) return emptyList()
        return try {
            OpenTypeFontTables.parseOpenTypeFeatures(file.readBytes()).map { feature ->
                FontFeature(tag = feature.tag, name = feature.name, enabled = feature.enabled)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect features for $fontPath", e)
            emptyList()
        }
    }
    
    /**
     * Get the file bytes for backup/export purposes.
     */
    fun getFontBytes(fontPath: String): ByteArray? {
        return try {
            File(fontPath).readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read font bytes: $fontPath", e)
            null
        }
    }
    
    /**
     * Restore a font from backup bytes.
     */
    suspend fun restoreFontFromBytes(bytes: ByteArray, displayName: String): String? = withContext(Dispatchers.IO) {
        try {
            val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "${System.currentTimeMillis()}_$sanitizedName"
            val destFile = File(fontsDir, fileName)
            
            destFile.sink().buffer().use { output ->
                output.write(bytes)
            }
            
            // Verify the font is valid
            val typeface = Typeface.createFromFile(destFile)
            if (typeface == null) {
                destFile.delete()
                return@withContext null
            }
            
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore font", e)
            null
        }
    }
    
    // ---- Private helpers ----
    
    private fun getExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) fileName.substring(dotIndex + 1).lowercase() else ""
    }
    
    private fun extractFontName(fileName: String): String {
        // Remove timestamp prefix and extension
        return fileName
            .substringAfter("_")
            .substringBeforeLast(".")
            .replace("_", " ")
    }
}

private fun PortableFontAxis.toFontAxis(): FontAxis = FontAxis(
    tag = tag,
    name = name,
    minValue = minValue,
    maxValue = maxValue,
    defaultValue = defaultValue,
    currentValue = currentValue,
)

/**
 * Information about a custom font stored in internal storage.
 */
data class CustomFontInfo(
    val path: String,
    val displayName: String,
    val size: Long,
    val isVariable: Boolean,
    val supportedAxes: List<String>
)
