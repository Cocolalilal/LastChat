package me.rerere.common.font

import kotlinx.serialization.Serializable

@Serializable
data class PortableFontAxis(
    val tag: String,
    val name: String,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val currentValue: Float = defaultValue,
)

@Serializable
data class PortableFontFeature(
    val tag: String,
    val name: String,
    val enabled: Boolean = true,
)

/**
 * Portable OpenType table reader used by Android [FontFileManager] and iOS custom-font import.
 * Parses `fvar` axes and GSUB/GPOS feature tags without Android Typeface APIs.
 */
object OpenTypeFontTables {
    fun isOpenTypeFont(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val tag = readTag(bytes, 0)
        return tag == "OTTO" ||
            tag == "true" ||
            tag == "ttcf" ||
            bytes[0] == 0.toByte() && bytes[1] == 1.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()
    }

    fun isVariableFont(bytes: ByteArray): Boolean = findTable(bytes, "fvar") != null

    fun parseVariableAxes(bytes: ByteArray): List<PortableFontAxis> {
        val fvarOffset = findTable(bytes, "fvar") ?: return emptyList()
        if (fvarOffset + 16 > bytes.size) return emptyList()
        val axesArrayOffset = readUInt16(bytes, fvarOffset + 4)
        val axisCount = readUInt16(bytes, fvarOffset + 8)
        val axisSize = readUInt16(bytes, fvarOffset + 10).coerceAtLeast(20)
        val axes = mutableListOf<PortableFontAxis>()
        var axisOffset = fvarOffset + axesArrayOffset
        repeat(axisCount) {
            if (axisOffset + 16 > bytes.size) return@repeat
            val tag = readTag(bytes, axisOffset)
            axes += PortableFontAxis(
                tag = tag,
                name = axisName(tag),
                minValue = readFixed(bytes, axisOffset + 4),
                defaultValue = readFixed(bytes, axisOffset + 8),
                maxValue = readFixed(bytes, axisOffset + 12),
                currentValue = readFixed(bytes, axisOffset + 8),
            )
            axisOffset += axisSize
        }
        return axes
    }

    fun parseOpenTypeFeatures(bytes: ByteArray): List<PortableFontFeature> {
        val tags = linkedSetOf<String>()
        findTable(bytes, "GSUB")?.let { tags += parseFeatureList(bytes, it) }
        findTable(bytes, "GPOS")?.let { tags += parseFeatureList(bytes, it) }
        return tags.map { tag ->
            PortableFontFeature(
                tag = tag,
                name = featureName(tag),
                enabled = tag.lowercase() in DEFAULT_ON_FEATURES,
            )
        }
    }

    fun findTable(bytes: ByteArray, tableTag: String): Int? {
        if (bytes.size < 12) return null
        val numTables = readUInt16(bytes, 4)
        var offset = 12
        repeat(numTables) {
            if (offset + 16 > bytes.size) return null
            if (readTag(bytes, offset) == tableTag) {
                return readUInt32(bytes, offset + 8).toInt()
            }
            offset += 16
        }
        return null
    }

    fun axisName(tag: String): String = when (tag.lowercase()) {
        "wght" -> "Weight"
        "wdth" -> "Width"
        "ital" -> "Italic"
        "slnt" -> "Slant"
        "opsz" -> "Optical Size"
        "grad" -> "Grade"
        "rond" -> "Roundness"
        else -> tag.uppercase()
    }

    fun featureName(tag: String): String = when (tag.lowercase()) {
        "liga" -> "Standard Ligatures"
        "dlig" -> "Discretionary Ligatures"
        "calt" -> "Contextual Alternates"
        "kern" -> "Kerning"
        "smcp" -> "Small Capitals"
        else -> tag.uppercase()
    }

    private val DEFAULT_ON_FEATURES = setOf("liga", "kern", "calt", "ccmp", "locl", "rlig")

    private fun parseFeatureList(bytes: ByteArray, tableOffset: Int): List<String> {
        if (tableOffset + 8 > bytes.size) return emptyList()
        val featureListOffset = readUInt16(bytes, tableOffset + 6)
        val absFeatureListOffset = tableOffset + featureListOffset
        if (absFeatureListOffset + 2 > bytes.size) return emptyList()
        val featureCount = readUInt16(bytes, absFeatureListOffset)
        val features = mutableListOf<String>()
        repeat(featureCount) { index ->
            val recordOffset = absFeatureListOffset + 2 + (index * 6)
            if (recordOffset + 4 <= bytes.size) features += readTag(bytes, recordOffset)
        }
        return features.distinct()
    }

    internal fun readUInt16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    internal fun readUInt32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    internal fun readFixed(bytes: ByteArray, offset: Int): Float {
        if (offset < 0 || offset + 4 > bytes.size) return 0f
        val intPart = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        val signedIntPart = if (intPart >= 32768) intPart - 65536 else intPart
        val fracPart = readUInt16(bytes, offset + 2)
        return signedIntPart + (fracPart / 65536f)
    }

    internal fun readTag(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset + 4 > bytes.size) return ""
        return buildString(4) {
            repeat(4) { index ->
                append((bytes[offset + index].toInt() and 0xFF).toChar())
            }
        }.trim()
    }
}

fun clampFontWidth(width: Float): Float = width.coerceIn(75f, 125f)

fun clampFontRoundness(roundness: Float): Float = roundness.coerceIn(0f, 100f)

fun clampFontGrade(grade: Float): Float = grade.coerceIn(-50f, 150f)
