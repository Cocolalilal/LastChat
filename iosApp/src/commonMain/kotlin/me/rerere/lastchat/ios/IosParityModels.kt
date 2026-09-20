package me.rerere.lastchat.ios

import kotlinx.serialization.Serializable

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
    "Android integration (digital assistant, share sheet, widgets, and system TTS) is Android-only."

internal const val WORKSPACE_UNAVAILABLE_REASON =
    "The Linux PRoot workspace sandbox is Android-only. iOS keeps a sandboxed app-container file area for attachments and generated media instead of a full on-device Linux environment."
