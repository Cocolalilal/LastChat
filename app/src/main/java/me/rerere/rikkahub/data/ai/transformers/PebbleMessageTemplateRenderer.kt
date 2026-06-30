package me.rerere.rikkahub.data.ai.transformers

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.Loader
import me.rerere.ai.core.MessageRole
import me.rerere.ai.util.MessageTemplateContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.toLocalDate
import me.rerere.rikkahub.utils.toLocalTime
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant

interface MessageTemplateCache {
    fun invalidateAll()
}

interface MessageTemplateClock {
    fun now(): Instant
}

object AndroidMessageTemplateClock : MessageTemplateClock {
    override fun now(): Instant = Instant.now()
}

class AndroidMessageTemplateContextFactory(
    private val clock: MessageTemplateClock = AndroidMessageTemplateClock,
) : MessageTemplateContextFactory {
    override fun build(message: String, role: MessageRole): Map<String, Any?> {
        val now = clock.now()
        return MessageTemplateContext.build(
            message = message,
            role = role,
            time = now.toLocalTime(),
            date = now.toLocalDate(),
        ).asMap()
    }
}

class PebbleMessageTemplateRenderer(
    private val engine: PebbleEngine,
) : MessageTemplateRenderer, MessageTemplateCache {
    override fun render(templateId: String, context: Map<String, Any?>): String {
        val output = StringWriter()
        engine.getTemplate(templateId).evaluate(output, context)
        return output.toString()
    }

    override fun invalidateAll() {
        engine.templateCache.invalidateAll()
    }
}

class AssistantTemplateLoader(private val settingsStore: SettingsStore) : Loader<String> {
    override fun getReader(cacheKey: String?): Reader? {
        val content = settingsStore.settingsFlow.value.assistants
            .find { it.id.toString() == cacheKey }?.messageTemplate
            ?: return null
        return StringReader(content)
    }

    override fun setCharset(charset: String?) {}

    override fun setPrefix(prefix: String?) {}

    override fun setSuffix(suffix: String?) {}

    override fun resolveRelativePath(
        relativePath: String?,
        anchorPath: String?,
    ): String? {
        return relativePath
    }

    override fun createCacheKey(templateName: String?): String? {
        return templateName
    }

    override fun resourceExists(templateName: String?): Boolean {
        return settingsStore.settingsFlow.value.assistants.any { it.id.toString() == templateName }
    }
}
