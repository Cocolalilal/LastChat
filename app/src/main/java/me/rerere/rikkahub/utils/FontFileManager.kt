package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.FontAxis
import me.rerere.rikkahub.data.datastore.FontFeature
import java.io.File
import java.io.FileOutputStream

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
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for font")
                return@withContext null
            }
            
            // Verify the font is valid by trying to create a Typeface
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
                        isVariable = isVariableFont(file),
                        supportedAxes = detectAxesTags(file)
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
            // Try to detect axes from font file
            val axes = mutableListOf<FontAxis>()
            
            // Read the font file and parse fvar table for variable fonts
            val axisInfo = parseFvarTable(file)
            if (axisInfo.isNotEmpty()) {
                axes.addAll(axisInfo)
            }
            
            axes
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
            // Parse GPOS/GSUB tables for OpenType features
            val features = parseOpenTypeFeatures(file)
            features
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
            
            destFile.writeBytes(bytes)
            
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
    
    private fun isVariableFont(file: File): Boolean {
        // Check if font has fvar table (variable font indicator)
        return try {
            val bytes = file.inputStream().use { it.readBytes() }
            findTable(bytes, "fvar") != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun detectAxesTags(file: File): List<String> {
        return try {
            parseFvarTable(file).map { it.tag }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Parse the 'fvar' table from a font file to extract variable font axes.
     * Reference: https://docs.microsoft.com/en-us/typography/opentype/spec/fvar
     */
    private fun parseFvarTable(file: File): List<FontAxis> {
        val bytes = file.inputStream().use { it.readBytes() }
        val fvarOffset = findTable(bytes, "fvar") ?: return emptyList()
        
        try {
            // fvar table structure:
            // uint16 majorVersion
            // uint16 minorVersion  
            // uint16 axesArrayOffset
            // uint16 reserved
            // uint16 axisCount
            // uint16 axisSize
            // ... axes data
            
            var offset = fvarOffset
            val majorVersion = readUInt16(bytes, offset)
            offset += 2
            val minorVersion = readUInt16(bytes, offset)
            offset += 2
            val axesArrayOffset = readUInt16(bytes, offset)
            offset += 2
            offset += 2 // reserved
            val axisCount = readUInt16(bytes, offset)
            offset += 2
            val axisSize = readUInt16(bytes, offset)
            
            val axes = mutableListOf<FontAxis>()
            var axisOffset = fvarOffset + axesArrayOffset
            
            repeat(axisCount) {
                // Each axis record:
                // Tag axisTag (4 bytes)
                // Fixed minValue (4 bytes)
                // Fixed defaultValue (4 bytes)
                // Fixed maxValue (4 bytes)
                // uint16 flags
                // uint16 axisNameID
                
                val tag = readTag(bytes, axisOffset)
                val minValue = readFixed(bytes, axisOffset + 4)
                val defaultValue = readFixed(bytes, axisOffset + 8)
                val maxValue = readFixed(bytes, axisOffset + 12)
                
                axes.add(FontAxis(
                    tag = tag,
                    name = getAxisName(tag),
                    minValue = minValue,
                    maxValue = maxValue,
                    defaultValue = defaultValue,
                    currentValue = defaultValue
                ))
                
                axisOffset += axisSize
            }
            
            return axes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse fvar table", e)
            return emptyList()
        }
    }
    
    /**
     * Parse OpenType features from GSUB and GPOS tables.
     */
    private fun parseOpenTypeFeatures(file: File): List<FontFeature> {
        val bytes = file.inputStream().use { it.readBytes() }
        val features = mutableSetOf<String>()
        
        // Check GSUB table (substitution features)
        findTable(bytes, "GSUB")?.let { gsubOffset ->
            features.addAll(parseFeatureList(bytes, gsubOffset))
        }
        
        // Check GPOS table (positioning features)
        findTable(bytes, "GPOS")?.let { gposOffset ->
            features.addAll(parseFeatureList(bytes, gposOffset))
        }
        
        return features.map { tag ->
            FontFeature(
                tag = tag,
                name = getFeatureName(tag),
                enabled = isFeatureEnabledByDefault(tag)
            )
        }
    }
    
    private fun parseFeatureList(bytes: ByteArray, tableOffset: Int): List<String> {
        try {
            // Table header structure (GSUB/GPOS):
            // uint16 majorVersion
            // uint16 minorVersion
            // Offset16 scriptListOffset
            // Offset16 featureListOffset
            // ...
            
            val featureListOffset = readUInt16(bytes, tableOffset + 6)
            val absFeatureListOffset = tableOffset + featureListOffset
            
            val featureCount = readUInt16(bytes, absFeatureListOffset)
            val features = mutableListOf<String>()
            
            repeat(featureCount) { i ->
                // FeatureRecord: Tag featureTag + Offset16 featureOffset
                val recordOffset = absFeatureListOffset + 2 + (i * 6)
                val tag = readTag(bytes, recordOffset)
                features.add(tag)
            }
            
            return features.distinct()
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    private fun findTable(bytes: ByteArray, tableTag: String): Int? {
        if (bytes.size < 12) return null
        
        val numTables = readUInt16(bytes, 4)
        var offset = 12
        
        repeat(numTables) {
            if (offset + 16 > bytes.size) return null
            val tag = readTag(bytes, offset)
            if (tag == tableTag) {
                return readUInt32(bytes, offset + 8).toInt()
            }
            offset += 16
        }
        return null
    }
    
    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
               (bytes[offset + 1].toInt() and 0xFF)
    }
    
    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
               ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
               ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
               (bytes[offset + 3].toLong() and 0xFF)
    }
    
    private fun readFixed(bytes: ByteArray, offset: Int): Float {
        if (offset < 0 || offset + 4 > bytes.size) return 0f
        // Handle signed integer properly for the integer part
        val intPart = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        val signedIntPart = if (intPart >= 32768) intPart - 65536 else intPart
        val fracPart = readUInt16(bytes, offset + 2)
        return signedIntPart + (fracPart / 65536f)
    }
    
    private fun readTag(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset + 4 > bytes.size) return ""
        return try {
            String(bytes, offset, 4, Charsets.US_ASCII).trim()
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun getAxisName(tag: String): String = when (tag.lowercase()) {
        "wght" -> "Weight"
        "wdth" -> "Width"
        "ital" -> "Italic"
        "slnt" -> "Slant"
        "opsz" -> "Optical Size"
        "grad" -> "Grade"
        "rond" -> "Roundness"
        "ytas" -> "Ascender Height"
        "ytde" -> "Descender Depth"
        "ytuc" -> "Uppercase Height"
        "ytlc" -> "Lowercase Height"
        "xhgt" -> "x-Height"
        "fill" -> "Fill"
        "wonk" -> "Wonky"
        else -> tag.uppercase()
    }
    
    private fun getFeatureName(tag: String): String = when (tag.lowercase()) {
        "liga" -> "Standard Ligatures"
        "dlig" -> "Discretionary Ligatures"
        "hlig" -> "Historical Ligatures"
        "calt" -> "Contextual Alternates"
        "kern" -> "Kerning"
        "smcp" -> "Small Capitals"
        "c2sc" -> "Capitals to Small Caps"
        "swsh" -> "Swash"
        "salt" -> "Stylistic Alternates"
        "ss01" -> "Stylistic Set 1"
        "ss02" -> "Stylistic Set 2"
        "ss03" -> "Stylistic Set 3"
        "ss04" -> "Stylistic Set 4"
        "ss05" -> "Stylistic Set 5"
        "ss06" -> "Stylistic Set 6"
        "ss07" -> "Stylistic Set 7"
        "ss08" -> "Stylistic Set 8"
        "ss09" -> "Stylistic Set 9"
        "ss10" -> "Stylistic Set 10"
        "frac" -> "Fractions"
        "numr" -> "Numerators"
        "dnom" -> "Denominators"
        "sups" -> "Superscript"
        "subs" -> "Subscript"
        "ordn" -> "Ordinals"
        "zero" -> "Slashed Zero"
        "onum" -> "Oldstyle Figures"
        "lnum" -> "Lining Figures"
        "tnum" -> "Tabular Figures"
        "pnum" -> "Proportional Figures"
        "case" -> "Case-Sensitive Forms"
        "cpsp" -> "Capital Spacing"
        "locl" -> "Localized Forms"
        "rlig" -> "Required Ligatures"
        "ccmp" -> "Glyph Composition"
        "aalt" -> "Access All Alternates"
        "titl" -> "Titling"
        else -> tag.uppercase()
    }
    
    private fun isFeatureEnabledByDefault(tag: String): Boolean = when (tag.lowercase()) {
        // These features are typically enabled by default
        "liga", "kern", "calt", "ccmp", "locl", "rlig" -> true
        // These are typically opt-in
        else -> false
    }
}

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
