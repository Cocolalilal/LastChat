package me.rerere.common.http

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExpressionTest {
    @Test
    fun testFastPathSingleIdentifiers() {
        val root = buildJsonObject {
            put("title", "LastChat")
            put("integerVal", 42)
            put("wholeDouble", 42.0)
            put("pi", 3.14159)
            put("isActive", true)
            put("nullField", JsonNull)
            put("childObj", buildJsonObject { put("nested", "value") })
            put("childArr", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
        }

        // String primitive
        assertEquals("LastChat", evaluateJsonExpr("title", root))
        // Numbers: integer representation formatting
        assertEquals("42", evaluateJsonExpr("integerVal", root))
        assertEquals("42", evaluateJsonExpr("wholeDouble", root))
        // Numbers: 2 decimal places rounding
        assertEquals("3.14", evaluateJsonExpr("pi", root))
        // Boolean
        assertEquals("true", evaluateJsonExpr("isActive", root))
        // Null or missing field
        assertEquals("", evaluateJsonExpr("nullField", root))
        assertEquals("", evaluateJsonExpr("nonExistentField", root))
        // Object and array
        assertEquals("{\"nested\":\"value\"}", evaluateJsonExpr("childObj", root))
        assertEquals("[1]", evaluateJsonExpr("childArr", root))
    }

    @Test
    fun testComplexExpressions() {
        val root = buildJsonObject {
            put("count", 10)
            put("price", 2.5)
            put("name", "User")
        }

        assertEquals("25", evaluateJsonExpr("count * price", root))
        assertEquals("Welcome, User!", evaluateJsonExpr("\"Welcome, \" ++ name ++ \"!\"", root))
        assertEquals("12.5", evaluateJsonExpr("count + price", root))
        assertTrue(isJsonExprValid("count * price + 5"))
    }
}
