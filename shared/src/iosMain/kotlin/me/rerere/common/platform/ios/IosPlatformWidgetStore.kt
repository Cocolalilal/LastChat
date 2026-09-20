package me.rerere.common.platform.ios

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.widget.AssistantWidgetSnapshot
import me.rerere.rikkahub.data.widget.PlatformWidgetStore

class IosPlatformWidgetStore : PlatformWidgetStore {
    private val json = Json { encodeDefaults = true }
    private val defaults = IosAppGroupDefaults.userDefaults

    override fun publish(snapshot: AssistantWidgetSnapshot) {
        val encoded = json.encodeToString(AssistantWidgetSnapshot.serializer(), snapshot)
        defaults.setObject(encoded, forKey = AssistantWidgetSnapshot.USER_DEFAULTS_KEY)
        defaults.setObject(snapshot.assistantId, forKey = "assistant_id")
        defaults.setObject(snapshot.assistantName, forKey = "assistant_name")
        defaults.setObject(snapshot.avatarType, forKey = "avatar_type")
        defaults.setObject(snapshot.avatarData, forKey = "avatar_data")
        snapshot.conversationTitle?.let { defaults.setObject(it, forKey = "conversation_title") }
        defaults.synchronize()
        IosAppGroupDefaults.writeText(AssistantWidgetSnapshot.USER_DEFAULTS_KEY, encoded)
    }

    override fun clear() {
        listOf(
            AssistantWidgetSnapshot.USER_DEFAULTS_KEY,
            "assistant_id",
            "assistant_name",
            "avatar_type",
            "avatar_data",
            "conversation_title",
        ).forEach { key ->
            defaults.removeObjectForKey(key)
            IosAppGroupDefaults.remove(key)
        }
        defaults.synchronize()
    }

    override fun consumePendingShareText(): String? = IosAppGroupDefaults.consumePendingShareText()

    override fun consumePendingOverlayPrompt(): String? = IosAppGroupDefaults.consumePendingOverlayPrompt()
}
