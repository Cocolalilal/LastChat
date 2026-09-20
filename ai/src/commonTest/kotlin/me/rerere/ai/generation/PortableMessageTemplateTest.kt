package me.rerere.ai.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableMessageTemplateTest {
    @Test
    fun blankAndDefaultTemplateReturnMessage() {
        assertEquals("Hello", renderSimpleMessageTemplate("", mapOf("message" to "Hello")))
        assertEquals("Hello", renderSimpleMessageTemplate("{{ message }}", mapOf("message" to "Hello")))
    }

    @Test
    fun interpolatesKnownKeysAndDropsUnknown() {
        val rendered = renderSimpleMessageTemplate(
            "{{ role }} said {{ message }} at {{ time }} {{ missing }}",
            mapOf("role" to "user", "message" to "Hi", "time" to "10:15"),
        )
        assertEquals("user said Hi at 10:15 ", rendered)
    }

    @Test
    fun ifElseUsesTruthyAndEquality() {
        val truthy = renderSimpleMessageTemplate(
            "{% if role == 'user' %}ask{% else %}reply{% endif %}",
            mapOf("role" to "user"),
        )
        val falsy = renderSimpleMessageTemplate(
            "{% if role == 'user' %}ask{% else %}reply{% endif %}",
            mapOf("role" to "assistant"),
        )
        val present = renderSimpleMessageTemplate(
            "{% if message %}yes{% else %}no{% endif %}",
            mapOf("message" to "Hi"),
        )
        assertEquals("ask", truthy)
        assertEquals("reply", falsy)
        assertEquals("yes", present)
    }

    @Test
    fun pebbleFiltersAreIgnoredOnNativeSubset() {
        val rendered = renderSimpleMessageTemplate(
            "{{ message | trim }}",
            mapOf("message" to "  padded  "),
        )
        assertEquals("  padded  ", rendered)
        assertTrue(rendered.contains("padded"))
    }
}
