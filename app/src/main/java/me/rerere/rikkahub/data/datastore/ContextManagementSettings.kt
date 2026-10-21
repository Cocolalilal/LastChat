package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.effectiveContextManagementMode
import me.rerere.rikkahub.data.model.withContextManagementMode

internal fun Settings.normalizeContextManagement(): Settings = copy(
    assistants = assistants.map { assistant ->
        assistant.withContextManagementMode(assistant.effectiveContextManagementMode).copy(
            contextRecentTurns = assistant.contextRecentTurns?.coerceIn(2, 24),
            contextSummaryInstructions = assistant.contextSummaryInstructions.take(4_000),
        )
    },
)
