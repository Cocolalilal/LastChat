package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Test

class PebblePortableTemplateRuntimeTest {
    @Test
    fun pebbleAdapterMatchesPortableEngineForDocumentedSyntax() {
        val pebble = PebblePortableTemplateRuntime()
        val context = mapOf(
            "message" to "  Hello  ",
            "role" to "user",
            "time" to "10:15",
            "date" to "Sep 20",
        )
        val template = "{% if role == 'user' %}{{ message | trim }} at {{ time }}{% else %}skip{% endif %}"
        assertEquals(
            me.rerere.ai.generation.renderMessageTemplate(template, context),
            pebble.render(template, context),
        )
    }

    @Test
    fun pebbleAdapterFallsBackForUnknownNativeSubset() {
        val pebble = PebblePortableTemplateRuntime()
        val rendered = pebble.render("{{ message | default('x') }}", mapOf("message" to ""))
        assertEquals("x", rendered)
    }
}
