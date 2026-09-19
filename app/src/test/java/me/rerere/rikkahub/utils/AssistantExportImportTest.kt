package me.rerere.rikkahub.utils

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.Assistant
import java.lang.reflect.Method

class AssistantExportImportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun invokeParseCharacterCard(jsonContent: String): Pair<Assistant, Any?>? {
        val method: Method = AssistantExportImport::class.java.getDeclaredMethod(
            "parseCharacterCard",
            String::class.java,
            ByteArray::class.java,
            Context::class.java
        )
        method.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        return method.invoke(AssistantExportImport, jsonContent, null, null) as? Pair<Assistant, Any?>
    }

    @Test
    fun testParseV1Card() {
        val jsonContent = """
        {
            "name": "V1 Character",
            "description": "A test character",
            "personality": "Friendly",
            "first_message": "Hello there!",
            "example_dialogs": "User: Hi\nChar: Hello",
            "post_history_instructions": "Always be polite.",
            "alternate_greetings": ["Hi!", "Greetings!"]
        }
        """

        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("V1 Character", assistant.name)
        assertTrue(assistant.presetMessages.isNotEmpty())
        assertEquals("Hello there!", assistant.presetMessages.first().parts.first().let { if (it is me.rerere.ai.ui.UIMessagePart.Text) it.text else "" })
        assertEquals(2, assistant.alternateGreetings.size)
        assertEquals("Hi!", assistant.alternateGreetings[0])
        
        assertTrue(assistant.systemPrompt.contains("A test character"))
        assertTrue(assistant.systemPrompt.contains("Friendly"))
        assertTrue(assistant.systemPrompt.contains("User: Hi"))
        assertTrue(assistant.systemPrompt.contains("Always be polite."))
    }

    @Test
    fun testParseV2Card_Robustly() {
        val jsonContent = """
        {
            "spec": "chara_card_v2",
            "data": {
                "name": "V2 Character",
                "system_prompt": "System instructions here.",
                "first_mes": "Initial greeting",
                "alternate_greetings": ["Alt1"]
            }
        }
        """

        val result = try {
            invokeParseCharacterCard(jsonContent)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("V2 Character", assistant.name)
        assertEquals("Initial greeting", assistant.presetMessages.first().parts.first().let { if (it is me.rerere.ai.ui.UIMessagePart.Text) it.text else "" })
        assertEquals(1, assistant.alternateGreetings.size)
        assertTrue(assistant.systemPrompt.contains("System instructions here."))
    }

    @Test
    fun testParseChubWrapper_Definition() {
        val jsonContent = """
        {
            "definition": {
                "name": "Chub Character",
                "description": "Nested character description"
            }
        }
        """

        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("Chub Character", assistant.name)
        assertTrue(assistant.systemPrompt.contains("Nested character description"))
    }
    
    @Test
    fun testParseChubWrapper_CharacterDefinition() {
        val jsonContent = """
        {
            "character": {
                "definition": {
                    "name": "Deep Chub Character",
                    "personality": "Deeply nested"
                }
            }
        }
        """

        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("Deep Chub Character", assistant.name)
        assertTrue(assistant.systemPrompt.contains("Deeply nested"))
    }

    @Test
    fun testParseStringifiedData() {
        val jsonContent = """
        {
            "data": "{\"name\": \"Stringified Character\", \"description\": \"Parsed from string\"}"
        }
        """

        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("Stringified Character", assistant.name)
        assertTrue(assistant.systemPrompt.contains("Parsed from string"))
    }
    
    @Test
    fun testParseArrayWrapper() {
        val jsonContent = """
        [
            {
                "name": "Array Character",
                "description": "First in array"
            },
            {
                "name": "Second",
                "description": "Ignored"
            }
        ]
        """

        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        val assistant = result!!.first

        assertEquals("Array Character", assistant.name)
        assertTrue(assistant.systemPrompt.contains("First in array"))
    }

    @Test
    fun testParseCardWithNullCharacterBook() {
        val jsonContent = """
        {
            "spec": "chara_card_v2",
            "data": {
                "name": "Yuko",
                "first_mes": "Hello",
                "character_book": null
            }
        }
        """
        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        assertEquals("Yuko", result!!.first.name)
        assertEquals("Hello", result.first.presetMessages.first().parts.first().let { if (it is me.rerere.ai.ui.UIMessagePart.Text) it.text else "" })
    }

    @Test
    fun testParseCardWithComplexCharacterBook() {
        val jsonContent = """
        {
            "spec": "chara_card_v2",
            "data": {
                "name": "Tilly",
                "first_mes": "Hello brother",
                "alternate_greetings": ["Alt 1", {"content": "Alt 2"}],
                "character_book": {
                    "name": "Tilly Lore",
                    "description": "Lorebook description",
                    "entries": [
                        {
                            "id": 1,
                            "keys": ["Key1", "Key2"],
                            "name": "Entry 1",
                            "content": "Lore content here",
                            "enabled": true,
                            "position": null,
                            "priority": 2,
                            "extensions": {
                                "depth": 4,
                                "linked": false,
                                "weight": 10
                            }
                        }
                    ]
                }
            }
        }
        """
        val result = invokeParseCharacterCard(jsonContent)
        assertNotNull(result)
        assertEquals("Tilly", result!!.first.name)
        assertEquals(2, result.first.alternateGreetings.size)
        assertEquals("Alt 1", result.first.alternateGreetings[0])
        assertEquals("Alt 2", result.first.alternateGreetings[1])
        assertNotNull(result.second)
        val lorebooks = (result.second as? AssistantExportV1)?.lorebooks
        assertNotNull(lorebooks)
        assertEquals(1, lorebooks!!.size)
        assertEquals(1, lorebooks[0].lorebook.entries.size)
        assertEquals("Lore content here", lorebooks[0].lorebook.entries[0].prompt)
    }

    @Test
    fun testParseRealDownloadedCards() {
        val files = listOf(
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\NeoHex900 -- Yuko Akiyama [Pepper0].card.png"),
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\Lisatago -- Harumi_ Your Tsundare Sister Nurse.card.png"),
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\PapuPapu0 -- Tina - Your Crush ❤️.card.png"),
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\SzainX -- Sonya - Mommy's Love School.card.png"),
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\MWhittz -- Tilly.card.png"),
            java.io.File("C:\\Users\\Julian Gander\\Downloads\\main_hoi-60e905767a77_spec_v2.png")
        )

        for (file in files) {
            if (!file.exists()) continue
            val bytes = file.readBytes()
            val chunksMethod = AssistantExportImport::class.java.getDeclaredMethod("extractPngChunks", ByteArray::class.java)
            chunksMethod.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val chunks = chunksMethod.invoke(AssistantExportImport, bytes) as Map<String, String>
            val charData = chunks["chara"] ?: chunks["Chara"] ?: chunks["card"]
            assertNotNull("Chunk chara not found in ${file.name}", charData)

            val decodedBytes = decodeBase64OrNull(charData!!)
            val jsonStr = if (decodedBytes != null) String(decodedBytes, Charsets.UTF_8) else charData

            val result = invokeParseCharacterCard(jsonStr)
            assertNotNull("Failed to parse card from ${file.name}", result)
            assertTrue("Assistant name is blank in ${file.name}", result!!.first.name.isNotBlank())
            assertTrue("System prompt is blank in ${file.name}", result.first.systemPrompt.isNotBlank())
        }
    }
}
