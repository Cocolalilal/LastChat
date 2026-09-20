package me.rerere.lastchat.ios

import kotlinx.serialization.Serializable
import me.rerere.common.runtime.UnavailableOnDeviceLlmRuntime
import me.rerere.common.runtime.UnavailableOnDeviceWorkspaceRuntime

@Serializable
data class IosSttPreferences(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "whisper-1",
    val language: String = "Auto",
)

@Serializable
data class IosWebPreferences(
    val enabled: Boolean = false,
    val port: Int = 8080,
    val listenAllInterfaces: Boolean = true,
)

@Serializable
data class IosWebDavPreferences(
    val url: String = "",
    val username: String = "",
    val path: String = "lastchat_backups",
)

@Serializable
data class IosWorkspacePreferences(
    val enabled: Boolean = false,
)

internal const val ANDROID_INTEGRATION_UNAVAILABLE_REASON =
    "Android share trampolines remain activity-owned. iOS hosts the assistant overlay in-process and ingests clipboard share-in through PortableSharePayload."

internal const val WORKSPACE_UNAVAILABLE_REASON =
    UnavailableOnDeviceWorkspaceRuntime.DEFAULT_UNAVAILABLE_REASON

internal const val LOCAL_LLM_UNAVAILABLE_REASON =
    UnavailableOnDeviceLlmRuntime.DEFAULT_UNAVAILABLE_REASON
