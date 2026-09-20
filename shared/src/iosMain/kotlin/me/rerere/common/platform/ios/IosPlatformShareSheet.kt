package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.common.platform.PlatformShareSheet
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
class IosPlatformShareSheet(
    private val presentingViewController: () -> UIViewController?,
) : PlatformShareSheet {
    override val available: Boolean = true

    override fun shareText(title: String, text: String) {
        if (text.isBlank()) return
        dispatch_async(dispatch_get_main_queue()) {
            val host = presentingViewController() ?: return@dispatch_async
            val items = if (title.isBlank()) listOf(text) else listOf(title, text)
            val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
            host.presentViewController(controller, animated = true, completion = null)
        }
    }
}
