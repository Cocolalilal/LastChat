package me.rerere.ai.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableMessageTemplateTest {
    @Test
    fun blankAndDefaultTemplateReturnMessage() {
        assertEquals("Hello", renderMessageTemplate("", mapOf("message" to "Hello")))
        assertEquals("Hello", renderMessageTemplate("{{ message }}", mapOf("message" to "Hello")))
    }

    @Test
    fun interpolatesKnownKeysAndDropsUnknown() {
        val rendered = renderMessageTemplate(
            "{{ role }} said {{ message }} at {{ time }} {{ missing }}",
            mapOf("role" to "user", "message" to "Hi", "time" to "10:15"),
        )
        assertEquals("user said Hi at 10:15 ", rendered)
    }

    @Test
    fun ifElseUsesTruthyAndEquality() {
        val truthy = renderMessageTemplate(
            "{% if role == 'user' %}ask{% else %}reply{% endif %}",
            mapOf("role" to "user"),
        )
        val falsy = renderMessageTemplate(
            "{% if role == 'user' %}ask{% else %}reply{% endif %}",
            mapOf("role" to "assistant"),
        )
        val present = renderMessageTemplate(
            "{% if message %}yes{% else %}no{% endif %}",
            mapOf("message" to "Hi"),
        )
        assertEquals("ask", truthy)
        assertEquals("reply", falsy)
        assertEquals("yes", present)
    }

    @Test
    fun elseifAndNotEmptyMatchPebbleControlFlow() {
        val assistant = renderMessageTemplate(
            "{% if role == 'user' %}ask{% elseif role == 'assistant' %}reply{% else %}other{% endif %}",
            mapOf("role" to "assistant"),
        )
        val empty = renderMessageTemplate(
            "{% if message is empty %}blank{% else %}{{ message }}{% endif %}",
            mapOf("message" to ""),
        )
        val notEmpty = renderMessageTemplate(
            "{% if message is not empty %}{{ message | trim }}{% endif %}",
            mapOf("message" to "  hi  "),
        )
        assertEquals("reply", assistant)
        assertEquals("blank", empty)
        assertEquals("hi", notEmpty)
    }

    @Test
    fun nestedIfAndComments() {
        val rendered = renderMessageTemplate(
            "{# greeting #}{% if role == 'user' %}{% if message %}{{ message | upper }}{% endif %}{% else %}skip{% endif %}",
            mapOf("role" to "user", "message" to "ok"),
        )
        assertEquals("OK", rendered)
    }

    @Test
    fun filtersCoverLastChatMessageTemplateUsage() {
        val rendered = renderMessageTemplate(
            "{{ message | trim | default('fallback') }} / {{ role | capitalize }} / {{ missing | default(role) }}",
            mapOf("message" to "  padded  ", "role" to "user"),
        )
        assertEquals("padded / User / user", rendered)
        assertEquals(
            "5",
            renderMessageTemplate("{{ message | length }}", mapOf("message" to "hello")),
        )
        assertEquals(
            "b a",
            renderMessageTemplate("{{ message | replace('a', 'b') | replace('c', 'a') }}", mapOf("message" to "a c")),
        )
    }

    @Test
    fun whitespaceControlTrimsAroundTags() {
        val rendered = renderMessageTemplate(
            "A\n{%- if role == 'user' -%}\n{{ message -}}\n{%- endif %}B",
            mapOf("role" to "user", "message" to "Hi"),
        )
        assertEquals("AHiB", rendered)
    }

    @Test
    fun pebbleFiltersAreAppliedOnNativeEngine() {
        val rendered = renderMessageTemplate(
            "{{ message | trim }}",
            mapOf("message" to "  padded  "),
        )
        assertEquals("padded", rendered)
        assertTrue(rendered.contains("padded"))
    }

    @Test
    fun simpleAliasStillWorks() {
        assertEquals("Hi", renderSimpleMessageTemplate("{{ message }}", mapOf("message" to "Hi")))
    }
}
