package me.rerere.lastchat.ios

import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.platform.ios.IosPlatformServices
import me.rerere.common.platform.ios.IosPlatformAttachmentOpener
import me.rerere.common.platform.ios.IosPlatformFilePicker
import me.rerere.common.platform.ios.IosPlatformShareSheet
import me.rerere.common.platform.ios.IosPlatformWidgetStore
import me.rerere.common.runtime.UnavailableOnDeviceLlmRuntime
import me.rerere.common.runtime.UnavailableOnDeviceWorkspaceRuntime
import me.rerere.search.PlatformBingSearchClient
import me.rerere.search.SearchService
import me.rerere.tts.controller.IosTtsAudioPlayer
import me.rerere.tts.controller.TtsController
import me.rerere.tts.provider.CloudTtsManager
import me.rerere.tts.provider.ios.IosPlatformSystemTts
import platform.UIKit.UIViewController

private val platformServices = IosPlatformServices()
private val systemTts = IosPlatformSystemTts()
private val presentingController = object {
    var value: UIViewController? = null
}
private val controller = run {
    SearchService.installPlatformHttpClient(platformServices.httpClient)
    SearchService.installBingSearchClient(PlatformBingSearchClient(platformServices.httpClient))
    val ttsManager = CloudTtsManager(platformServices.httpClient, systemTts)
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
        speechRecorder = platformServices.speechRecorder,
        documentParser = platformServices.documentParser,
        systemTts = systemTts,
        shareSheet = IosPlatformShareSheet { presentingController.value },
        widgetStore = IosPlatformWidgetStore(),
        onDeviceLlm = UnavailableOnDeviceLlmRuntime(),
        onDeviceWorkspace = UnavailableOnDeviceWorkspaceRuntime(),
        imageOcr = platformServices.imageOcr,
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

fun InstallIosSpontaneousBackgroundScheduler(
    schedule: (Long) -> Unit,
    cancel: () -> Unit,
) {
    controller.installSpontaneousBackgroundScheduler(schedule, cancel)
}

fun RunIosSpontaneousBackgroundMaintenance(completion: (Boolean) -> Unit) {
    controller.runSpontaneousBackgroundMaintenance(completion)
}

fun InstallIosStorageBackgroundScheduler(
    schedule: (Long) -> Unit,
    cancel: () -> Unit,
) {
    controller.installStorageBackgroundScheduler(schedule, cancel)
}

fun RunIosStorageBackgroundMaintenance(completion: (Boolean) -> Unit) {
    controller.runStorageBackgroundMaintenance(completion)
}

fun HandleIosAssistantOverlayDeepLink(text: String?) {
    controller.openOverlayFromExternal(text.orEmpty())
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
            audioPlayer = platformServices.attachmentAudioPlayer,
        )
    }
    presentingController.value = viewController
    return viewController
}
