package me.rerere.common.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OpenTypeFontTablesTest {
    @Test
    fun rejectsNonFontBytes() {
        assertFalse(OpenTypeFontTables.isOpenTypeFont(byteArrayOf(1, 2, 3, 4)))
        assertTrue(OpenTypeFontTables.parseVariableAxes(byteArrayOf()).isEmpty())
    }

    @Test
    fun parsesTrueTypeMagicAndFvarWidthAxis() {
        val bytes = syntheticVariableFont(tag = "wdth", min = 75f, default = 100f, max = 125f)
        assertTrue(OpenTypeFontTables.isOpenTypeFont(bytes))
        assertTrue(OpenTypeFontTables.isVariableFont(bytes))
        val axes = OpenTypeFontTables.parseVariableAxes(bytes)
        assertEquals(1, axes.size)
        assertEquals("wdth", axes[0].tag)
        assertEquals("Width", axes[0].name)
        assertEquals(75f, axes[0].minValue)
        assertEquals(100f, axes[0].defaultValue)
        assertEquals(125f, axes[0].maxValue)
    }

    @Test
    fun clampHelpersMatchAndroidFontRanges() {
        assertEquals(75f, clampFontWidth(10f))
        assertEquals(125f, clampFontWidth(200f))
        assertEquals(0f, clampFontRoundness(-4f))
        assertEquals(100f, clampFontRoundness(140f))
        assertEquals(-50f, clampFontGrade(-80f))
        assertEquals(150f, clampFontGrade(200f))
    }
}

class PortableCustomFontStoreTest {
    @Test
    fun importRejectsInvalidBytesAndStoresOpenType() = runBlocking {
        val store = PortableCustomFontStore(MemoryFontFileStore(), clock = { 42L })
        assertEquals(null, store.import(byteArrayOf(0, 1, 2), "bad.bin"))
        val font = syntheticVariableFont("ROND", 0f, 50f, 100f)
        val imported = assertNotNull(store.import(font, "My Font.ttf"))
        assertEquals("custom_fonts/42_My_Font.ttf", imported.storagePath)
        assertEquals("My Font", imported.displayName)
        assertTrue(imported.isVariable)
        assertEquals("ROND", imported.axes.single().tag)
        assertTrue(store.load(imported.storagePath)?.contentEquals(font) == true)
        assertEquals(1, store.list().size)
        assertTrue(store.delete(imported.storagePath))
        assertEquals(null, store.load(imported.storagePath))
    }
}

private class MemoryFontFileStore : me.rerere.common.platform.PlatformFileStore {
    private val files = mutableMapOf<String, ByteArray>()
    override suspend fun readBytes(path: String): ByteArray? = files[path.trimStart('/')]
    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        files[path.trimStart('/')] = bytes
    }
    override suspend fun delete(path: String): Boolean = files.remove(path.trimStart('/')) != null
    override suspend fun exists(path: String): Boolean = files.containsKey(path.trimStart('/'))
    override suspend fun lastModified(path: String): Long? = if (exists(path)) 1L else null
    override suspend fun listFiles(path: String): List<String> {
        val prefix = path.replace('\\', '/').trimStart('/').trimEnd('/')
        return files.keys.filter { it == prefix || it.startsWith("$prefix/") }
    }
}

internal fun syntheticVariableFont(
    tag: String,
    min: Float,
    default: Float,
    max: Float,
): ByteArray {
    val headerSize = 12
    val recordSize = 16
    val fvarHeader = 16
    val axisSize = 20
    val fvarOffset = headerSize + recordSize
    val total = fvarOffset + fvarHeader + axisSize
    val bytes = ByteArray(total)
    writeU16(bytes, 0, 0x0001)
    writeU16(bytes, 2, 0x0000)
    writeU16(bytes, 4, 1)
    writeU16(bytes, 6, 16)
    writeU16(bytes, 8, 0)
    writeU16(bytes, 10, 0)
    writeTag(bytes, 12, "fvar")
    writeU32(bytes, 20, fvarOffset.toLong())
    writeU32(bytes, 24, (fvarHeader + axisSize).toLong())
    writeU16(bytes, fvarOffset, 1)
    writeU16(bytes, fvarOffset + 2, 0)
    writeU16(bytes, fvarOffset + 4, 16)
    writeU16(bytes, fvarOffset + 6, 0)
    writeU16(bytes, fvarOffset + 8, 1)
    writeU16(bytes, fvarOffset + 10, axisSize)
    val axisOffset = fvarOffset + 16
    writeTag(bytes, axisOffset, tag.padEnd(4).take(4))
    writeFixed(bytes, axisOffset + 4, min)
    writeFixed(bytes, axisOffset + 8, default)
    writeFixed(bytes, axisOffset + 12, max)
    return bytes
}

private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = ((value ushr 8) and 0xFF).toByte()
    bytes[offset + 1] = (value and 0xFF).toByte()
}

private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
    bytes[offset] = ((value ushr 24) and 0xFF).toByte()
    bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    bytes[offset + 3] = (value and 0xFF).toByte()
}

private fun writeTag(bytes: ByteArray, offset: Int, tag: String) {
    tag.padEnd(4).take(4).forEachIndexed { index, char ->
        bytes[offset + index] = char.code.toByte()
    }
}

private fun writeFixed(bytes: ByteArray, offset: Int, value: Float) {
    val intPart = value.toInt()
    val frac = ((value - intPart) * 65536f).toInt().coerceIn(0, 65535)
    writeU16(bytes, offset, intPart and 0xFFFF)
    writeU16(bytes, offset + 2, frac)
}
