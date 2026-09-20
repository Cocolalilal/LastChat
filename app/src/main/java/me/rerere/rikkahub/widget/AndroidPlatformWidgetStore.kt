package me.rerere.rikkahub.widget

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.widget.AssistantWidgetSnapshot
import me.rerere.rikkahub.data.widget.PlatformWidgetStore

class AndroidPlatformWidgetStore(
    context: Context,
) : PlatformWidgetStore {
    private val json = Json { encodeDefaults = true }
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun publish(snapshot: AssistantWidgetSnapshot) {
        prefs.edit()
            .putString(AssistantWidgetSnapshot.USER_DEFAULTS_KEY, json.encodeToString(snapshot))
            .putString("assistant_id", snapshot.assistantId)
            .putString("assistant_name", snapshot.assistantName)
            .putString("avatar_type", snapshot.avatarType)
            .putString("avatar_data", snapshot.avatarData)
            .putString("conversation_title", snapshot.conversationTitle)
            .putLong("updated_at", snapshot.updatedAtEpochMs)
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(AssistantWidgetSnapshot.USER_DEFAULTS_KEY)
            .remove("assistant_id")
            .remove("assistant_name")
            .remove("avatar_type")
            .remove("avatar_data")
            .remove("conversation_title")
            .remove("updated_at")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "assistant_widget_snapshot"
    }
}
