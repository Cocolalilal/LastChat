package me.rerere.rikkahub.data.widget

import kotlinx.serialization.Serializable

/**
 * Portable home-screen widget payload. Android Glance and the iOS WidgetKit shell
 * both read this snapshot; neither should duplicate assistant lookup logic.
 */
@Serializable
data class AssistantWidgetSnapshot(
    val assistantId: String,
    val assistantName: String,
    val avatarType: String = "emoji",
    val avatarData: String = "💬",
    val conversationTitle: String? = null,
    val updatedAtEpochMs: Long = 0L,
) {
    companion object {
        const val STORAGE_PATH = "state/widget-snapshot.json"
        const val USER_DEFAULTS_SUITE = "group.lastchat.rikkafork.cocolal"
        const val USER_DEFAULTS_KEY = "assistant_widget_snapshot"
    }
}

interface PlatformWidgetStore {
    fun publish(snapshot: AssistantWidgetSnapshot)

    fun clear()
}

class NoOpWidgetStore : PlatformWidgetStore {
    override fun publish(snapshot: AssistantWidgetSnapshot) = Unit

    override fun clear() = Unit
}
