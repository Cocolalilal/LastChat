package me.rerere.lastchat.ios

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentIosPlatformInfo(): IosPlatformInfo = IosPlatformInfo(
    systemVersion = UIDevice.currentDevice.systemVersion,
    device = UIDevice.currentDevice.model,
    architecture = Platform.cpuArchitecture.name,
)

internal actual fun openIosExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}
