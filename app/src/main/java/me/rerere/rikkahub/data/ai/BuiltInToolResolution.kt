package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode

internal fun resolveActiveBuiltInTools(
    model: Model,
    assistant: Assistant,
): Set<BuiltInTools> {
    val activeTools = model.tools
        .filterNotTo(mutableSetOf()) { it is BuiltInTools.Search }

    if (shouldUseBuiltInSearch(model, assistant)) {
        activeTools += BuiltInTools.Search
    }

    return activeTools
}

internal fun shouldUseBuiltInSearch(
    model: Model,
    assistant: Assistant,
): Boolean {
    return BuiltInTools.Search in model.tools &&
        (
            assistant.preferBuiltInSearch ||
                assistant.searchMode is AssistantSearchMode.BuiltIn
            )
}
