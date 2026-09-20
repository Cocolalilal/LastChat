package me.rerere.rikkahub.data.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.rerere.rikkahub.data.prompt.LorebookActivationKind

class PortableCharacterCardParserTest {
    @Test
    fun parseV1CardMapsDescriptionPersonalityAndGreetings() {
        val json = """
        {
            "name": "V1 Character",
            "description": "A test character",
            "personality": "Friendly",
            "first_message": "Hello there!",
            "example_dialogs": "User: Hi\nChar: Hello",
            "post_history_instructions": "Always be polite.",
            "alternate_greetings": ["Hi!", "Greetings!"]
        }
        """.trimIndent()
        val imported = assertNotNull(PortableCharacterCardParser.parseJson(json))
        assertEquals("V1 Character", imported.name)
        assertEquals("Hello there!", imported.firstMes)
        assertEquals(listOf("Hi!", "Greetings!"), imported.alternateGreetings)
        assertTrue(imported.systemPrompt.contains("A test character"))
        assertTrue(imported.systemPrompt.contains("Friendly"))
        assertTrue(imported.systemPrompt.contains("User: Hi"))
        assertTrue(imported.systemPrompt.contains("Always be polite."))
    }

    @Test
    fun parseV2CardReadsNestedDataAndFirstMes() {
        val json = """
        {
            "spec": "chara_card_v2",
            "data": {
                "name": "V2 Character",
                "system_prompt": "System instructions here.",
                "first_mes": "Initial greeting",
                "alternate_greetings": ["Alt1"],
                "character_book": {
                    "name": "World",
                    "entries": [
                        { "name": "Town", "content": "A quiet town", "keys": ["town", "village"] }
                    ]
                }
            }
        }
        """.trimIndent()
        val imported = assertNotNull(PortableCharacterCardParser.parseJson(json))
        assertEquals("V2 Character", imported.name)
        assertEquals("Initial greeting", imported.firstMes)
        assertEquals(listOf("Alt1"), imported.alternateGreetings)
        assertTrue(imported.systemPrompt.startsWith("System instructions here."))
        val lore = assertNotNull(imported.lorebook)
        assertEquals("World", lore.name)
        assertEquals(LorebookActivationKind.KEYWORDS, lore.entries.single().activationType)
        assertEquals(listOf("town", "village"), lore.entries.single().keywords)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun parsePngReadsCharaTextChunk() {
        val card = """{"name":"Png Character","first_mes":"Hi from PNG","description":"From a tEXt chunk"}"""
        val encoded = Base64.Default.encode(card.encodeToByteArray())
        val png = pngWithTextChunk("chara", encoded)
        val imported = assertNotNull(PortableCharacterCardParser.parse(png, "card.png"))
        assertEquals("Png Character", imported.name)
        assertEquals("Hi from PNG", imported.firstMes)
        assertTrue(imported.systemPrompt.contains("From a tEXt chunk"))
        assertNotNull(imported.avatarBytes)
    }

    @Test
    fun parsePngReadsCharaZtxtChunk() {
        val png = pngWithZtxtChunk(
            "chara",
            hexBytes("78daab56ca4bcc4d55b2528a2aa9285170ce482c4a4c2e492d52d2514acb2c2a2e89cf4d2d064a7a642aa415e5e72a5485449400a552528b938b320b4a32f3f380926e209944b09c427246695eb6522d00f6991d56"),
        )
        val imported = assertNotNull(PortableCharacterCardParser.parse(png, "card.png"))
        assertEquals("Ztxt Character", imported.name)
        assertEquals("Hi from zTXt", imported.firstMes)
        assertTrue(imported.systemPrompt.contains("From a zTXt chunk"))
    }

    @Test
    fun parsePngReadsCompressedItxtChunk() {
        val png = pngWithCompressedItxtChunk(
            "chara",
            hexBytes("78daab56ca4bcc4d55b252f22ca9285170ce482c4a4c2e492d52d2514acb2c2a2e89cf4d2d064a7a642aa415e5e72a6486449428d50200d1a5119a"),
        )
        val imported = assertNotNull(PortableCharacterCardParser.parse(png, "card.png"))
        assertEquals("Itxt Character", imported.name)
        assertEquals("Hi from iTXt", imported.firstMes)
    }
}

private fun pngWithTextChunk(keyword: String, text: String): ByteArray {
    val textPayload = (keyword + "\u0000" + text).encodeToByteArray()
    return pngWithChunk("tEXt", textPayload)
}

private fun pngWithZtxtChunk(keyword: String, zlibPayload: ByteArray): ByteArray {
    val keywordBytes = keyword.encodeToByteArray()
    val payload = keywordBytes + byteArrayOf(0, 0) + zlibPayload
    return pngWithChunk("zTXt", payload)
}

private fun pngWithCompressedItxtChunk(keyword: String, zlibPayload: ByteArray): ByteArray {
    val keywordBytes = keyword.encodeToByteArray()
    val payload = keywordBytes + byteArrayOf(0, 1, 0, 0, 0) + zlibPayload
    return pngWithChunk("iTXt", payload)
}

private fun pngWithChunk(type: String, data: ByteArray): ByteArray {
    val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val ihdrData = ByteArray(13)
    ihdrData[3] = 1
    ihdrData[7] = 1
    ihdrData[8] = 8
    return header + pngChunk("IHDR", ihdrData) + pngChunk(type, data) + pngChunk("IEND", byteArrayOf())
}

private fun hexBytes(hex: String): ByteArray =
    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun pngChunk(type: String, data: ByteArray): ByteArray {
    val typeBytes = type.encodeToByteArray()
    val length = ByteArray(4)
    val size = data.size
    length[0] = ((size ushr 24) and 0xFF).toByte()
    length[1] = ((size ushr 16) and 0xFF).toByte()
    length[2] = ((size ushr 8) and 0xFF).toByte()
    length[3] = (size and 0xFF).toByte()
    val crcInput = typeBytes + data
    val crc = pngCrc32(crcInput)
    val crcBytes = byteArrayOf(
        ((crc ushr 24) and 0xFF).toByte(),
        ((crc ushr 16) and 0xFF).toByte(),
        ((crc ushr 8) and 0xFF).toByte(),
        (crc and 0xFF).toByte(),
    )
    return length + typeBytes + data + crcBytes
}

private fun pngCrc32(bytes: ByteArray): Int {
    var crc = 0xFFFFFFFF.toInt()
    for (b in bytes) {
        val index = (crc xor (b.toInt() and 0xFF)) and 0xFF
        crc = PNG_CRC_TABLE[index] xor (crc ushr 8)
    }
    return crc.inv()
}

private val PNG_CRC_TABLE: IntArray = IntArray(256) { n ->
    var c = n
    repeat(8) {
        c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
    }
    c
}
