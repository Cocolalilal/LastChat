package me.rerere.lastchat.ios.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

@Serializable
enum class IosMcpAuthMode {
    @SerialName("none")
    NONE,

    @SerialName("oauth")
    OAUTH,

    @SerialName("custom_headers")
    CUSTOM_HEADERS,

    @SerialName("external_oauth_setup")
    EXTERNAL_OAUTH_SETUP;
}

@Serializable
data class IosMcpTool(
    val enable: Boolean = true,
    val name: String = "",
    val description: String? = null,
    val inputSchema: JsonElement? = null,
)

@Serializable
data class IosMcpCommonOptions(
    val enable: Boolean = true,
    val name: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val tools: List<IosMcpTool> = emptyList(),
    val authMode: IosMcpAuthMode = IosMcpAuthMode.CUSTOM_HEADERS,
    val presetId: String? = null,
)

@Serializable
sealed class IosMcpServerConfig {
    abstract val id: String
    abstract val commonOptions: IosMcpCommonOptions
    abstract val url: String

    abstract fun clone(
        id: String = this.id,
        commonOptions: IosMcpCommonOptions = this.commonOptions,
    ): IosMcpServerConfig

    @Serializable
    @SerialName("sse")
    data class SseTransportServer(
        override val id: String = Uuid.random().toString(),
        override val commonOptions: IosMcpCommonOptions = IosMcpCommonOptions(),
        override val url: String = "",
    ) : IosMcpServerConfig() {
        override fun clone(id: String, commonOptions: IosMcpCommonOptions): IosMcpServerConfig =
            copy(id = id, commonOptions = commonOptions)
    }

    @Serializable
    @SerialName("streamable_http")
    data class StreamableHTTPServer(
        override val id: String = Uuid.random().toString(),
        override val commonOptions: IosMcpCommonOptions = IosMcpCommonOptions(),
        override val url: String = "",
    ) : IosMcpServerConfig() {
        override fun clone(id: String, commonOptions: IosMcpCommonOptions): IosMcpServerConfig =
            copy(id = id, commonOptions = commonOptions)
    }
}
