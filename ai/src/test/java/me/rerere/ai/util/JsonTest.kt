package me.rerere.ai.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.CustomBody
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonTest {

    @Test
    fun `mergeCustomBody with empty list should return original object`() {
        val originalJson = buildJsonObject {
            put("key1", "value1")
            put("key2", 2)
        }

        val result = originalJson.mergeCustomBody(emptyList())

        assertEquals(originalJson, result)
    }

    @Test
    fun `mergeCustomBody with simple keys should merge correctly`() {
        val originalJson = buildJsonObject {
            put("existingKey", "existingValue")
        }

        val customBodies = listOf(
            CustomBody("newKey", JsonPrimitive("newValue")),
            CustomBody("numberKey", JsonPrimitive(42))
        )

        val result = originalJson.mergeCustomBody(customBodies)

        assertEquals("existingValue", result["existingKey"]?.toString()?.trim('"'))
        assertEquals("newValue", result["newKey"]?.toString()?.trim('"'))
        assertEquals("42", result["numberKey"]?.toString())
    }

    @Test
    fun `mergeCustomBody should override existing simple keys`() {
        val originalJson = buildJsonObject {
            put("key1", "oldValue")
        }

        val customBodies = listOf(
            CustomBody("key1", JsonPrimitive("newValue"))
        )

        val result = originalJson.mergeCustomBody(customBodies)

        assertEquals("newValue", result["key1"]?.toString()?.trim('"'))
    }

    @Test
    fun `mergeCustomBody should merge nested JsonObjects`() {
        val originalJson = buildJsonObject {
            put("config", buildJsonObject {
                put("setting1", "value1")
                put("setting2", "value2")
            })
            put("simpleKey", "simpleValue")
        }

        val nestedJsonValue = buildJsonObject {
            put("setting2", "updatedValue")
            put("setting3", "newValue")
        }

        val customBodies = listOf(
            CustomBody("config", nestedJsonValue)
        )

        val result = originalJson.mergeCustomBody(customBodies)

        val config = result["config"] as JsonObject
        assertEquals("value1", config["setting1"]?.toString()?.trim('"'))
        assertEquals("updatedValue", config["setting2"]?.toString()?.trim('"'))
        assertEquals("newValue", config["setting3"]?.toString()?.trim('"'))
        assertEquals("simpleValue", result["simpleKey"]?.toString()?.trim('"'))
    }

    @Test
    fun `mergeCustomBody should handle deeply nested JsonObjects`() {
        val originalJson = buildJsonObject {
            put("level1", buildJsonObject {
                put("level2", buildJsonObject {
                    put("setting1", "original")
                })
            })
        }

        val nestedValue = buildJsonObject {
            put("level2", buildJsonObject {
                put("setting1", "updated")
                put("setting2", "new")
            })
        }

        val customBodies = listOf(
            CustomBody("level1", nestedValue)
        )

        val result = originalJson.mergeCustomBody(customBodies)

        val level1 = result["level1"] as JsonObject
        val level2 = level1["level2"] as JsonObject

        assertEquals("updated", level2["setting1"]?.toString()?.trim('"'))
        assertEquals("new", level2["setting2"]?.toString()?.trim('"'))
    }

    @Test
    fun `mergeCustomBody should ignore empty keys`() {
        val originalJson = buildJsonObject {
            put("key1", "value1")
        }

        val customBodies = listOf(
            CustomBody("", JsonPrimitive("ignored")),
            CustomBody("key2", JsonPrimitive("value2"))
        )

        val result = originalJson.mergeCustomBody(customBodies)

        assertEquals(2, result.size)
        assertEquals("value1", result["key1"]?.toString()?.trim('"'))
        assertEquals("value2", result["key2"]?.toString()?.trim('"'))
    }
}
