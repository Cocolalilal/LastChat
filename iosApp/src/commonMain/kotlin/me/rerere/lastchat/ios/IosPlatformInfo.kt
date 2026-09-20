package me.rerere.lastchat.ios

internal const val IOS_APP_VERSION = "1.4.7"

internal data class IosPlatformInfo(
    val systemVersion: String,
    val device: String,
    val architecture: String,
)

internal expect fun currentIosPlatformInfo(): IosPlatformInfo

internal expect fun openIosExternalUrl(url: String)
