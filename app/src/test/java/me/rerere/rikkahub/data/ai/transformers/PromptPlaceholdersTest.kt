package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.getInitialMessageNodes
import me.rerere.rikkahub.data.model.isInitialGreeting
import me.rerere.rikkahub.utils.applyPlaceholders
import org.junit.Assert.*
import org.junit.Test

class PromptPlaceholdersTest {
    private val runtime = PlaceholderRuntimeValues("date", "time", "datetime", "zone", "os", "device", "75", "place")
    private fun resolver(user: String = "Alex", assistant: Assistant = Assistant(name = "Mira")) =
        promptPlaceholderResolver(PlaceholderCtx(user, Model(modelId = "test-model"), assistant) { runtime })

    @Test fun allSupportedTagsResolveInBothFormsWithoutChangingUnknownTags() {
        val resolve = resolver()
        val expected = mapOf("user" to "Alex", "char" to "Mira", "cur_date" to "date", "cur_time" to "time",
            "cur_datetime" to "datetime", "timezone" to "zone", "system_version" to "os", "device_info" to "device",
            "battery_level" to "75", "location" to "place", "model_name" to Model(modelId = "test-model").displayName)
        expected.forEach { (tag, value) -> assertEquals("$value / $value", resolve("{{$tag}} / {${tag.uppercase()}}")) }
        assertEquals("{{unknown}} {unknown}", resolve("{{unknown}} {unknown}"))
    }

    @Test fun replacementValuesStayLiteralAndAreNotReinterpretedAsOtherTags() {
        val resolve = resolver(user = "{char} and $1", assistant = Assistant(name = "{{user}}"))
        assertEquals("{{user}} / {char} and $1", resolve("{{char}} / {{user}}"))
        assertEquals("{language} and {{user}} / French", "{source_text} / {language}".applyPlaceholders(
            "source_text" to "{language} and {{user}}", "language" to "French"))
    }

    @Test fun valuesResolveOnlyWhenUsedAndAreConsistentAcrossConfiguredSources() {
        var reads = 0
        val resolve = promptPlaceholderResolver(PlaceholderCtx("Alex", Model(), Assistant(name = "Mira")) {
            reads++
            runtime.copy(currentTime = "time-$reads")
        })
        assertEquals("Mira / Alex / {{unknown}}", resolve("{char} / {user} / {{unknown}}"))
        assertEquals(0, reads)
        assertEquals("time-1", resolve("{cur_time}"))
        assertEquals("time-1", resolve("{{CUR_TIME}}"))
        assertEquals(1, reads)
    }

    @Test fun configuredGreetingsResolveOnceWithoutChangingTheirTemplate() {
        val assistant = Assistant(name = "Mira", alternateGreetings = listOf("Hello {{user}}, I'm {{char}} at {{cur_time}}."))
        val greeting = assistant.getInitialMessageNodes(resolver(assistant = assistant)).single().currentMessage
        assertEquals("Hello Alex, I'm Mira at time.", greeting.toContentText())
        assertTrue(greeting.isInitialGreeting())
        assertEquals("Hello {{user}}, I'm {{char}} at {{cur_time}}.", assistant.alternateGreetings.single())

    }

    @Test fun emptyPersonaNamesUseStableFallbacks() {
        assertEquals("assistant / user", resolver("", Assistant(name = ""))("{{char}} / {user}"))
    }
}
