package me.rerere.rikkahub.data.datastore

import kotlin.uuid.Uuid

/** Changing the default starts a fresh assignment for every current and future character. */
internal fun Settings.withLorebookDefault(lorebookId: Uuid, enabled: Boolean): Settings = copy(
    lorebooks = lorebooks.map {
        if (it.id == lorebookId) it.copy(allAssistants = enabled, excludedAssistantIds = emptySet()) else it
    },
    assistants = assistants.map { it.copy(enabledLorebookIds = it.enabledLorebookIds - lorebookId) },
)

/** Store only an exception to the current default, retaining assignments for other lorebooks. */
internal fun Settings.withLorebookAssignment(lorebookId: Uuid, assistantId: Uuid, enabled: Boolean): Settings {
    val book = lorebooks.firstOrNull { it.id == lorebookId } ?: return this
    if (assistants.none { it.id == assistantId }) return this
    return copy(
        lorebooks = lorebooks.map {
            if (it.id == lorebookId) it.copy(excludedAssistantIds =
                if (book.allAssistants && !enabled) it.excludedAssistantIds + assistantId
                else it.excludedAssistantIds - assistantId) else it
        },
        assistants = assistants.map {
            if (it.id == assistantId) it.copy(enabledLorebookIds =
                if (!book.allAssistants && enabled) it.enabledLorebookIds + lorebookId
                else it.enabledLorebookIds - lorebookId) else it
        },
    )
}
