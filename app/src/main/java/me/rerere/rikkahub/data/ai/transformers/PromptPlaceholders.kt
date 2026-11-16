package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.resolveTemplatePlaceholders

/** Apply only at configured-prompt boundaries, before mixing prompts with literal history. */
internal fun promptPlaceholderResolver(
    context: Context,
    settings: Settings,
    assistant: Assistant,
    model: Model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: Model(),
): (String) -> String {
    val runtime by lazy { AndroidPlaceholderRuntimeValues.from(context) }
    val ctx = PlaceholderCtx(settings.displaySetting.userNickname, model, assistant) { runtime }
    return promptPlaceholderResolver(ctx)
}

internal fun promptPlaceholderResolver(ctx: PlaceholderCtx): (String) -> String {
    val values = mutableMapOf<String, String>()
    return { template ->
        template.resolveTemplatePlaceholders { key ->
            DefaultPlaceholderProvider.placeholders[key]?.let { info ->
                values.getOrPut(key) { info.resolver(ctx) }
            }
        }
    }
}
