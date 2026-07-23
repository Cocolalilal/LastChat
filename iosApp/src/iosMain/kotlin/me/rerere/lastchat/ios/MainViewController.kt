package me.rerere.lastchat.ios

import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.platform.ios.IosPlatformServices
import me.rerere.common.platform.ios.IosPlatformAttachmentOpener
import me.rerere.common.platform.ios.IosPlatformFilePicker
import me.rerere.search.PlatformBingSearchClient
import me.rerere.search.SearchService
import me.rerere.tts.controller.IosTtsAudioPlayer
import me.rerere.tts.controller.TtsController
import me.rerere.tts.provider.CloudTtsManager
import platform.UIKit.UIViewController

private val platformServices = IosPlatformServices()
private val controller = run {
    SearchService.installPlatformHttpClient(platformServices.httpClient)
    SearchService.installBingSearchClient(PlatformBingSearchClient(platformServices.httpClient))
    val ttsManager = CloudTtsManager(platformServices.httpClient)
    IosAppController(
        fileStore = platformServices.fileStore,
        secureStore = platformServices.secureSettingsStore,
        httpClient = platformServices.httpClient,
        javaScriptExecutor = IosJavaScriptCoreExecutor(),
        providerManager = ProviderManager(
            platformHttpClient = platformServices.httpClient,
            platformMediaEncoder = platformServices.mediaEncoder,
            platformJwtSigner = platformServices.jwtSigner,
        ),
        ttsController = TtsController(ttsManager, IosTtsAudioPlayer()),
        notificationPlatform = IosUserNotificationPlatform(),
    )
}

fun InstallIosAdaptiveMemoryBackgroundScheduler(
    schedule: (Long) -> Unit,
    cancel: () -> Unit,
) {
    controller.installAdaptiveBackgroundScheduler(schedule, cancel)
}

fun RunIosAdaptiveMemoryBackgroundMaintenance(completion: (Boolean) -> Unit) {
    controller.runAdaptiveBackgroundMaintenance(completion)
}

fun InstallIosScheduledMessageBackgroundScheduler(
    schedule: (Long) -> Unit,
    cancel: () -> Unit,
) {
    controller.installScheduledMessageBackgroundScheduler(schedule, cancel)
}

fun RunIosScheduledMessageBackgroundMaintenance(completion: (Boolean) -> Unit) {
    controller.runScheduledMessageBackgroundMaintenance(completion)
}

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
