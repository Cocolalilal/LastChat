package me.rerere.lastchat.ios

import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.platform.ios.IosPlatformServices
import me.rerere.common.platform.ios.IosPlatformAttachmentOpener
import me.rerere.common.platform.ios.IosPlatformFilePicker
import platform.UIKit.UIViewController

private val platformServices = IosPlatformServices()
private val controller = IosAppController(
    fileStore = platformServices.fileStore,
    secureStore = platformServices.secureSettingsStore,
    providerManager = ProviderManager(
        platformHttpClient = platformServices.httpClient,
        platformMediaEncoder = platformServices.mediaEncoder,
        platformJwtSigner = platformServices.jwtSigner,
    ),
)

fun MainViewController(): UIViewController {
    lateinit var viewController: UIViewController
    val filePicker = IosPlatformFilePicker(platformServices.fileStore) { viewController }
    val attachmentOpener = IosPlatformAttachmentOpener { viewController }
    viewController = ComposeUIViewController {
        LastChatIosApp(
            controller = controller,
            platformHaptics = platformServices.haptics,
            filePicker = filePicker,
            attachmentOpener = attachmentOpener,
        )
    }
    return viewController
}
