package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.common.platform.PlatformAttachmentOpener
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Presents Quick Look/Open In through UIKit while retaining the interaction controller. */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformAttachmentOpener(
    private val presentingViewController: () -> UIViewController?,
) : PlatformAttachmentOpener {
    private val delegate = AttachmentInteractionDelegate(presentingViewController)
    private var interactionController: UIDocumentInteractionController? = null

    override fun open(url: String): Boolean {
        val fileUrl = NSURL.URLWithString(url) ?: return false
        if (!fileUrl.isFileURL() && fileUrl.scheme !in listOf("http", "https")) return false
        val controller = UIDocumentInteractionController.interactionControllerWithURL(fileUrl)
        controller.delegate = delegate
        interactionController = controller
        return controller.presentPreviewAnimated(true)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AttachmentInteractionDelegate(
    private val presentingViewController: () -> UIViewController?,
) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
    override fun documentInteractionControllerViewControllerForPreview(
        controller: UIDocumentInteractionController,
    ): UIViewController = checkNotNull(presentingViewController()) {
        "The iOS window is not ready to preview an attachment."
    }
}
