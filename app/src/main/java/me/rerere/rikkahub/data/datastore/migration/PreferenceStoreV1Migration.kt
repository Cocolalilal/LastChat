package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV1Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 2
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        // 清理老的没有设置@SerialName的字段
        prefs[SettingsStore.MCP_SERVERS] = prefs[SettingsStore.MCP_SERVERS]?.let { json ->
            val element = JsonInstant.parseToJsonElement(json).jsonArray.map { element ->
                val jsonObj = element.jsonObject.toMutableMap()
                val type = jsonObj["type"]?.jsonPrimitive?.content ?: ""
                when(type) {
                    "me.rerere.rikkahub.data.mcp.McpServerConfig.SseTransportServer" -> {
                        jsonObj["type"] = JsonPrimitive("sse")
                    }
                    "me.rerere.rikkahub.data.mcp.McpServerConfig.StreamableHTTPServer" -> {
                        jsonObj["type"] = JsonPrimitive("streamable_http")
                    }
                }
                JsonObject(jsonObj)
            }
            JsonInstant.encodeToString(element)
        } ?: "[]"

        // 更新版本
        prefs[SettingsStore.PROVIDERS] = prefs[SettingsStore.PROVIDERS]?.let(::stripLocalProviders) ?: "[]"
        prefs[SettingsStore.VERSION] = 2

        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}

    private fun stripLocalProviders(json: String): String {
        return runCatching {
            val providers = JsonInstant.parseToJsonElement(json).jsonArray
            val filtered = buildJsonArray {
                providers.forEach { element ->
                    val type = (element as? JsonObject)
                        ?.get("type")
                        ?.jsonPrimitive
                        ?.contentOrNull
                    if (type != "local") {
                        add(element)
                    }
                }
            }
            JsonInstant.encodeToString(filtered)
        }.getOrDefault("[]")
    }
}
