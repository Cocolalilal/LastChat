package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object PlaceholderTransformer : InputMessageTransformer, KoinComponent {
    private val defaultProvider = DefaultPlaceholderProvider

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val settingsStore = get<SettingsStore>()
        val placeholderCtx = PlaceholderCtx(
            settingsStore = settingsStore,
            model = ctx.model,
            assistant = ctx.assistant,
            runtimeValues = AndroidPlaceholderRuntimeValues.from(ctx.context),
        )
        return messages.map {
            it.copy(
                parts = it.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = replacePlaceholders(text = part.text, ctx = placeholderCtx)
                        )
                    } else {
                        part
                    }
                }
            )
        }
    }

    private fun replacePlaceholders(
        text: String,
        ctx: PlaceholderCtx,
    ): String {
        var result = text
        defaultProvider.placeholders.forEach { (key, placeholderInfo) ->
            val value = placeholderInfo.resolver(ctx)
            result = result
                .replace(oldValue = "{{$key}}", newValue = value, ignoreCase = true)
                .replace(oldValue = "{$key}", newValue = value, ignoreCase = true)
        }
        return result
    }
}
