package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.common.platform.PlatformShareSheet
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
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

    override fun shareFile(title: String, fileName: String, mimeType: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        dispatch_async(dispatch_get_main_queue()) {
            val host = presentingViewController() ?: return@dispatch_async
            val safeName = fileName.replace('/', '_').ifBlank { "skill.zip" }
            val path = NSTemporaryDirectory() + safeName
            val data = if (bytes.isEmpty()) {
                NSData()
            } else {
                bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
            }
            if (!data.writeToFile(path, atomically = true)) return@dispatch_async
            val url = NSURL.fileURLWithPath(path)
            val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            host.presentViewController(controller, animated = true, completion = null)
        }
    }
}
