package me.rerere.lastchat.ios

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.ui.text.TextStyle
import me.rerere.rikkahub.utils.TtsFilterMode
import me.rerere.rikkahub.utils.TtsTextFilterRule
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.utils.formatUpdateFileSize
import me.rerere.rikkahub.utils.shouldShowUpdatePill
import me.rerere.rikkahub.utils.Version
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.versionSelectionIndices
import me.rerere.ai.ui.versionSelectionPosition
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformAttachmentOpener
import me.rerere.common.platform.PlatformAttachmentAudioPlayer
import me.rerere.common.platform.UnavailableAttachmentAudioPlayer
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.lastchat.ios.backup.IosBackupImportReport
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
import me.rerere.ai.generation.PortableConversationQueries
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarMonth
import me.rerere.rikkahub.ui.components.chat.BubblePosition
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.ConversationRowSurface
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.TypingIndicator
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerDefaultActionContent
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddIcon
import me.rerere.rikkahub.ui.components.chat.LastChatComposerCapsule
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAttachmentRow
import me.rerere.rikkahub.ui.components.chat.LastChatAudioAttachmentTile
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAudioIcon
import me.rerere.rikkahub.ui.components.chat.LastChatDocumentAttachmentTile
import me.rerere.rikkahub.ui.components.chat.LastChatComposerImageAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatComposerMediaAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatMessageAttachmentRow
import me.rerere.rikkahub.ui.components.message.LastChatTtsAction
import me.rerere.rikkahub.ui.components.memory.LastChatMemoryGroupPosition
import me.rerere.rikkahub.ui.components.memory.LastChatMemoryModeCard
import me.rerere.rikkahub.ui.components.memory.LastChatMemoryRow
import me.rerere.rikkahub.ui.components.memory.LastChatMemorySettingsItem
import me.rerere.rikkahub.ui.components.chat.LastChatComposerRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerVideoIcon
import me.rerere.rikkahub.ui.components.ai.LastChatAssistantPickerItem
import me.rerere.rikkahub.ui.components.ai.LastChatAssistantPickerSheet
import me.rerere.rikkahub.ui.components.nav.LastChatBackButton
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerActionIcon
import me.rerere.rikkahub.ui.components.nav.LastChatBarChartIcon
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickActionGroup
import me.rerere.rikkahub.ui.components.nav.LastChatMenuButton
import me.rerere.rikkahub.ui.components.nav.LastChatModalDrawerSheet
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerSearch
import me.rerere.rikkahub.ui.components.stats.LastChatStatCard
import me.rerere.rikkahub.ui.components.stats.LastChatActivityHeatmapCard
import me.rerere.rikkahub.ui.components.stats.LastChatStatIcon
import me.rerere.rikkahub.ui.components.stats.LastChatStatIconGlyph
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsNavigationPane
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsPaneEntry
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsPaneGroup
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem
import me.rerere.rikkahub.ui.components.settings.LastChatAboutContent
import me.rerere.rikkahub.ui.components.settings.LastChatGroupedModelRow
import me.rerere.rikkahub.ui.components.settings.LastChatModelFeatureCard
import me.rerere.rikkahub.ui.components.settings.LastChatModelGroupPosition
import me.rerere.rikkahub.ui.components.settings.LastChatProviderTab
import me.rerere.rikkahub.ui.components.settings.LastChatProvidersBottomBar
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.buildLastChatTypography
import me.rerere.rikkahub.ui.theme.presetColorScheme
import me.rerere.rikkahub.ui.theme.rememberLastChatFontFamily
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.models.ModelCatalogSource
import me.rerere.rikkahub.data.ai.models.ModelCatalogStatus

private enum class IosRoute { Chat, Settings, Statistics, ImageGeneration }

private enum class IosSettingsSection(val title: String) {
    Home("Settings"),
    Appearance("Display"),
    Assistant("Assistant"),
    Memory("Memory"),
    Tools("Tools"),
    Provider("Providers"),
    Models("Default model"),
    Search("Search service"),
    Tts("Text-to-speech"),
    Speech("Speech-to-text"),
    Skills("Skills"),
    Lorebooks("Lorebooks"),
    Mcp("MCP"),
    Web("Web server"),
    Workspaces("Workspaces"),
    AndroidIntegration("Android integration"),
    Data("Data"),
    Backup("Backup"),
    BackupWebDav("WebDAV backup"),
    About("About"),
    Developer("Developer"),
    Unavailable("Unavailable"),
}

private data class DisplayMessage(
    val text: String,
    val outgoing: Boolean,
    val position: BubblePosition = BubblePosition.SINGLE,
    val parts: List<UIMessagePart> = emptyList(),
    val messageId: String? = null,
    val branchNode: MessageNode? = null,
    val canRegenerate: Boolean = false,
    val reasoning: String = "",
    val usage: TokenUsage? = null,
    val modelName: String? = null,
    val contextStack: IosContextStackSummary = IosContextStackSummary(0, 0, 0),
)

@Composable
fun LastChatIosApp(
    controller: IosAppController,
    platformHaptics: PlatformHaptics,
    filePicker: PlatformFilePicker,
    attachmentOpener: PlatformAttachmentOpener,
    audioPlayer: PlatformAttachmentAudioPlayer = UnavailableAttachmentAudioPlayer(),
    darkTheme: Boolean? = null,
) {
    LaunchedEffect(controller) { controller.initialize() }
    val state by controller.state.collectAsState()
    val useDarkTheme = darkTheme ?: when (state.appearance.colorMode) {
        IosColorMode.SYSTEM -> isSystemInDarkTheme()
        IosColorMode.LIGHT -> false
        IosColorMode.DARK -> true
    }
    val colorScheme = presetColorScheme(state.appearance.themeId, useDarkTheme)
    val lastChatFontFamily = rememberLastChatFontFamily()
    val fontSettings = state.appearance.fontSettings.normalize()
    val appFontFamily = iosFontFamilyChoice(
        lastChatFontFamily,
        fontSettings,
        state.appearance.usePhoneSystemFont,
    )
    MaterialTheme(
        colorScheme = colorScheme.withLastChatAmoledSurface(useDarkTheme),
        typography = buildLastChatTypography(appFontFamily).withIosFontConfig(
            fontSettings.headerFont,
            state.appearance.fontSizeRatio,
        ),
        shapes = Shapes,
    ) {
        var route by remember { mutableStateOf(IosRoute.Chat) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "lastchat-route",
        ) { destination ->
            when (destination) {
                IosRoute.Chat -> ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        LastChatModalDrawerSheet(
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) {
                            MenuPage(
                                state = state,
                                onSelectConversation = controller::selectConversation,
                                onRenameConversation = controller::renameConversation,
                                onDeleteConversation = controller::deleteConversation,
                                onSelectAssistant = controller::selectAssistant,
                                onSaveUserProfile = controller::saveUserProfile,
                                onPickUserAvatar = { filePicker.pickFile(it) },
                                darkTheme = useDarkTheme,
                                platformHaptics = platformHaptics,
                                onDismiss = { scope.launch { drawerState.close() } },
                                onSettings = {
                                    scope.launch {
                                        drawerState.close()
                                        route = IosRoute.Settings
                                    }
                                },
                                onStatistics = {
                                    scope.launch {
                                        drawerState.close()
                                        route = IosRoute.Statistics
                                    }
                                },
                                onImageGeneration = {
                                    scope.launch {
                                        drawerState.close()
                                        route = IosRoute.ImageGeneration
                                    }
                                },
                            )
                        }
                    },
                ) {
                    ChatPage(
                        state = state,
                        onSend = controller::send,
                        onCancelGeneration = controller::cancelGeneration,
                        onSubmitQuestionnaire = controller::submitQuestionnaire,
                        onPickFile = { filePicker.pickFile(controller::handlePickedFile) },
                        onRemovePendingAttachment = controller::removePendingAttachment,
                        onSpeak = controller::speak,
                        onStopSpeaking = controller::stopTts,
                        onStartSpeech = controller::startSpeechRecognition,
                        onStopSpeech = controller::stopSpeechRecognition,
                        onShareConversation = { controller.shareConversation() },
                        onOpenOverlay = controller::showAssistantOverlay,
                        platformHaptics = platformHaptics,
                        attachmentOpener = attachmentOpener,
                        audioPlayer = audioPlayer,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onOpenSettings = { route = IosRoute.Settings },
                        onSelectDefaultModel = controller::selectDefaultModel,
                        onOpenImageGeneration = { route = IosRoute.ImageGeneration },
                        onIgnoreUpdate = controller::ignoreUpdate,
                        onSelectVersion = { nodeId, index ->
                            state.selectedConversationId?.let { id ->
                                controller.updateNodeSelection(id, nodeId, index)
                            }
                        },
                        onRegenerate = { state.selectedConversationId?.let(controller::regenerateResponse) },
                        onEditMessage = { messageId, parts ->
                            state.selectedConversationId?.let { id ->
                                controller.editMessage(id, messageId, parts)
                            }
                        },
                        onDeleteMessage = { messageId ->
                            state.selectedConversationId?.let { id ->
                                controller.deleteMessage(id, messageId)
                            }
                        },
                        onForkMessage = { messageId ->
                            state.selectedConversationId?.let { id ->
                                controller.forkConversation(id, messageId)
                            }
                        },
                    )
                }
                IosRoute.Settings -> SettingsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    onSaveProvider = controller::saveProvider,
                    onAddProvider = controller::addProvider,
                    onDeleteProvider = controller::deleteProvider,
                    onAddModel = controller::addModelToProvider,
                    onRemoveModel = controller::removeModelFromProvider,
                    onClearProviderApiKey = controller::clearProviderApiKey,
                    onClearApiKey = controller::clearApiKey,
                    onSelectDefaultModel = controller::selectDefaultModel,
                    onSaveSearch = controller::saveSearch,
                    onClearSearchApiKey = controller::clearSearchApiKey,
                    onSaveTts = controller::saveTts,
                    onClearTtsApiKey = controller::clearTtsApiKey,
                    onSaveImageGeneration = controller::saveImageGeneration,
                    onSaveAppearance = controller::saveAppearance,
                    onSaveFontSettings = controller::saveFontSettings,
                    onSaveUiCustomization = controller::saveUiCustomization,
                    onSaveDisplayKnobs = controller::saveDisplayKnobs,
                    onSaveAppearancePreferences = { controller.saveAppearancePreferences(it) },
                    onRefreshUpdateCheck = { controller.refreshUpdateCheck(force = true) },
                    onRefreshModelCatalog = controller::refreshModelCatalog,
                    onDownloadLocalLlm = controller::downloadLocalLlm,
                    onDownloadLocalStt = controller::downloadLocalStt,
                    onCancelLocalDownload = controller::cancelLocalDownload,
                    onDeleteLocalLlm = controller::deleteLocalLlm,
                    onDeleteLocalStt = controller::deleteLocalStt,
                    onRequestNotificationPermission = controller::requestNotificationPermission,
                    onSaveRpStyleRules = controller::saveRpStyleRules,
                    onSaveAssistant = controller::saveAssistant,
                    onSaveAssistantAvatar = controller::saveAssistantAvatar,
                    onSaveAssistantUiSettings = controller::saveAssistantUiSettings,
                    onPickAvatarFile = { filePicker.pickFile(it) },
                    onSaveSpontaneousSettings = controller::saveSpontaneousSettings,
                    onSaveComfyUiProvider = controller::saveComfyUiProvider,
                    onImportComfyUiWorkflow = controller::importComfyUiWorkflow,
                    onRunStorageMaintenance = { controller.runStorageBackgroundMaintenance {} },
                    onNewAssistant = controller::newAssistant,
                    onSelectAssistant = controller::selectAssistant,
                    onDeleteAssistant = controller::deleteAssistant,
                    onSaveMemorySettings = controller::saveMemorySettings,
                    onAddMemory = controller::addMemory,
                    onUpdateMemory = controller::updateMemory,
                    onDeleteMemory = controller::deleteMemory,
                    onRegenerateMemoryEmbeddings = controller::regenerateMemoryEmbeddings,
                    onSaveLocalTools = controller::saveLocalTools,
                    onSavePromptInjections = controller::savePromptInjections,
                    onSaveMcpServers = controller::saveMcpServers,
                    onRefreshMcpTools = controller::refreshMcpTools,
                    onSaveStt = controller::saveStt,
                    onClearSttApiKey = controller::clearSttApiKey,
                    onSaveWeb = controller::saveWebPreferences,
                    onSaveWebDav = controller::saveWebDav,
                    onTestWebDav = controller::testWebDav,
                    onListWebDav = controller::listWebDavBackups,
                    onBackupWebDav = controller::backupToWebDav,
                    onRestoreWebDav = controller::restoreFromWebDav,
                    onDeleteWebDav = controller::deleteWebDavBackup,
                    onSaveOverlaySettings = controller::saveOverlaySettings,
                    onSaveDeveloperMode = controller::saveDeveloperMode,
                    onPickBackupFile = { filePicker.pickFile(it) },
                    onRestoreBackup = controller::restoreAndroidBackup,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                )
                IosRoute.Statistics -> StatisticsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                )
                IosRoute.ImageGeneration -> IosImageGenerationPage(
                    state = state,
                    onGenerate = controller::generateImages,
                    onPickImage = { callback -> filePicker.pickFile(callback) },
                    onCancel = controller::cancelImageGeneration,
                    onDelete = controller::deleteGeneratedImage,
                    onOpenSettings = { route = IosRoute.Settings },
                    attachmentOpener = attachmentOpener,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                )
            }
        }
        if (state.overlayVisible) {
            IosAssistantOverlaySheet(
                state = state,
                onDismiss = controller::hideAssistantOverlay,
                onSend = controller::send,
                onIngestShareText = controller::ingestShareText,
                platformHaptics = platformHaptics,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatPage(
    state: IosAppState,
    onSend: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onSubmitQuestionnaire: (Map<String, String>, Map<String, String>, Boolean) -> Unit,
    onPickFile: () -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartSpeech: () -> Unit,
    onStopSpeech: ((String) -> Unit) -> Unit,
    onShareConversation: () -> Unit = {},
    onOpenOverlay: () -> Unit = {},
    platformHaptics: PlatformHaptics,
    attachmentOpener: PlatformAttachmentOpener,
    audioPlayer: PlatformAttachmentAudioPlayer,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDefaultModel: (String, String) -> Unit = { _, _ -> },
    onOpenImageGeneration: () -> Unit = {},
    onIgnoreUpdate: (String) -> Unit = {},
    onSelectVersion: (String, Int) -> Unit,
    onRegenerate: () -> Unit,
    onEditMessage: (String, List<UIMessagePart>) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onForkMessage: (String) -> Unit,
) {
    val inputState = remember { TextFieldState() }
    val playingAudioUrl by audioPlayer.playingUrl.collectAsState()
    val clipboard = LocalClipboardManager.current
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    val pendingQuestionnaire = state.pendingQuestionnaire
    var questionnaireIndex by remember(pendingQuestionnaire?.toolCallId) { mutableStateOf(0) }
    var questionnaireSelectedOptions by remember(pendingQuestionnaire?.toolCallId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var questionnaireCustomAnswers by remember(pendingQuestionnaire?.toolCallId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val currentQuestion = pendingQuestionnaire?.questions?.getOrNull(questionnaireIndex)
    LaunchedEffect(currentQuestion?.id) {
        inputState.setTextAndPlaceCursorAtEnd(
            currentQuestion?.let { questionnaireCustomAnswers[it.id].orEmpty() }.orEmpty()
        )
    }
    LaunchedEffect(currentQuestion?.id, inputState.text.toString()) {
        currentQuestion?.let { question ->
            questionnaireCustomAnswers = questionnaireCustomAnswers +
                (question.id to inputState.text.toString())
        }
    }
    val orderedPendingAttachments = remember(state.pendingAttachments) {
        PlatformPickedFileKind.entries.flatMap { kind ->
            state.pendingAttachments.filter { attachment -> attachment.kind == kind }
        }
    }
    val conversationMessages = state.selectedConversation?.currentMessages.orEmpty()
        .filter { message ->
            message.toText().isNotBlank() || message.parts.any {
                it is UIMessagePart.Image || it is UIMessagePart.Video ||
                    it is UIMessagePart.Audio || it is UIMessagePart.Document
            }
        }
    val branchNodesByMessageId = remember(state.selectedConversation?.messageNodes) {
        val nodesByMessageId = mutableMapOf<String, MessageNode>()
        state.selectedConversation?.messageNodes?.forEach { node ->
            node.messages.forEach { message -> nodesByMessageId[message.id.toString()] = node }
        }
        nodesByMessageId
    }
    val messages = conversationMessages.mapIndexed { index, message ->
        val rawText = message.toText()
        val markdownImages = GENERATED_MARKDOWN_IMAGE_REGEX.findAll(rawText).map { match ->
            UIMessagePart.Image(match.groupValues[1])
        }.toList()
        val outgoing = message.role == MessageRole.USER
        val sameBefore = conversationMessages.getOrNull(index - 1)?.role == message.role
        val sameAfter = conversationMessages.getOrNull(index + 1)?.role == message.role
        val position = when {
            !sameBefore && !sameAfter -> BubblePosition.SINGLE
            !sameBefore -> BubblePosition.FIRST
            !sameAfter -> BubblePosition.LAST
            else -> BubblePosition.MIDDLE
        }
        val node = branchNodesByMessageId[message.id.toString()]
        val reasoning = message.parts.mapNotNull { part ->
            when (part) {
                is UIMessagePart.Reasoning -> part.reasoning
                is UIMessagePart.Thinking -> part.thinking
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
        DisplayMessage(
            text = rawText.replace(GENERATED_MARKDOWN_IMAGE_REGEX, "").trim(),
            outgoing = outgoing,
            position = position,
            parts = message.parts + markdownImages,
            messageId = message.id.toString(),
            branchNode = if (!outgoing) node else null,
            canRegenerate = !outgoing && !state.generating &&
                index == conversationMessages.lastIndex && node != null,
            reasoning = reasoning,
            usage = message.usage,
            modelName = state.selectedChatModel?.second?.displayName
                ?: state.selectedChatModel?.second?.modelId,
            contextStack = IosContextStackSummary(
                lore = message.usedLorebookEntries.orEmpty().size,
                modes = message.usedModes.orEmpty().size,
                memories = message.usedMemories.orEmpty().size,
            ),
        )
    }
    fun send() {
        val text = inputState.text.toString().trim()
        if (text.isEmpty() && state.pendingAttachments.isEmpty()) return
        onSend(text)
        inputState.setTextAndPlaceCursorAtEnd("")
        platformHaptics.perform(PlatformHapticPattern.Send)
    }
    fun submitQuestionnaire(dismissed: Boolean) {
        val currentCustomAnswers = currentQuestion?.let { question ->
            questionnaireCustomAnswers + (question.id to inputState.text.toString())
        } ?: questionnaireCustomAnswers
        onSubmitQuestionnaire(questionnaireSelectedOptions, currentCustomAnswers, dismissed)
        inputState.setTextAndPlaceCursorAtEnd("")
        platformHaptics.perform(if (dismissed) PlatformHapticPattern.Pop else PlatformHapticPattern.Send)
    }
    val appearance = state.appearance.withAssistantUi(state.assistant.uiSettings)
    var showChatModelPicker by remember { mutableStateOf(false) }
    var chatModelSearchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    val isNewChat = messages.isEmpty()
    var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
    val showUpdatePill = shouldShowUpdatePill(
        checkForUpdates = appearance.checkForUpdates,
        forceCheck = false,
        isNewChat = isNewChat,
        currentVersion = IOS_APP_VERSION,
        latest = state.updateInfo,
        ignoredVersion = appearance.ignoredUpdateVersion.takeIf { it.isNotBlank() },
        ignoredTimeEpochMs = appearance.ignoredUpdateTimeEpochMs,
        nowEpochMs = Clock.System.now().toEpochMilliseconds(),
        dismissedVersion = dismissedUpdateVersion,
    )
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedConversationId, appearance.enableMessageGenerationHapticEffect) {
        if (!appearance.enableMessageGenerationHapticEffect) return@LaunchedEffect
        var previousLength = 0
        snapshotFlow {
            if (!state.generating) {
                0
            } else {
                messages.lastOrNull { !it.outgoing }?.text?.length ?: 0
            }
        }.collect { length ->
            if (length == 0) {
                previousLength = 0
            } else if (length > previousLength + 24) {
                platformHaptics.perform(PlatformHapticPattern.ScrollEdge)
                previousLength = length
            } else if (length < previousLength) {
                previousLength = length
            }
        }
    }
    val chromeBlur = if (appearance.enableBlurEffect) Modifier.blur(12.dp) else Modifier
    val toolbar: @Composable () -> Unit = {
        TopAppBar(
            title = {
                Column {
                    Text(state.assistant.name, fontWeight = FontWeight.SemiBold)
                    if (appearance.showContextTokenSummary) {
                        val usage = messages.lastOrNull { it.usage != null }?.usage
                        val label = if (usage != null) {
                            iosTokenUsageLabel(usage.promptTokens, usage.completionTokens, usage.totalTokens)
                        } else {
                            "Context"
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                LastChatMenuButton(
                    onClick = onOpenMenu,
                    contentDescription = "Messages",
                )
            },
            actions = {
                if (showUpdatePill) {
                    Surface(
                        onClick = { showUpdateDialog = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("New Update", style = MaterialTheme.typography.labelLarge)
                            IconButton(
                                onClick = { dismissedUpdateVersion = state.updateInfo?.version },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = onOpenOverlay,
                        onLongClick = onOpenOverlay,
                    ),
                ) {
                    Icon(Icons.Rounded.Assistant, contentDescription = "Assistant overlay")
                }
                IconButton(onClick = onShareConversation) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share conversation")
                }
            },
            modifier = chromeBlur,
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = { if (!appearance.chatToolbarAtBottom) toolbar() },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .then(chromeBlur)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (appearance.chatToolbarAtBottom) toolbar()
                pendingQuestionnaire?.let { questionnaire ->
                    IosCharacterQuestionsCard(
                        questionnaire = questionnaire,
                        currentIndex = questionnaireIndex,
                        selectedOptionLabel = currentQuestion?.let { questionnaireSelectedOptions[it.id] },
                        onPrevious = { questionnaireIndex = (questionnaireIndex - 1).coerceAtLeast(0) },
                        onNext = {
                            questionnaireIndex = (questionnaireIndex + 1)
                                .coerceAtMost(questionnaire.questions.lastIndex)
                        },
                        onDismiss = { submitQuestionnaire(dismissed = true) },
                        onSelectOption = { option ->
                            currentQuestion?.let { question ->
                                questionnaireSelectedOptions = questionnaireSelectedOptions +
                                    (question.id to option.label)
                            }
                            platformHaptics.perform(PlatformHapticPattern.Pop)
                        },
                    )
                }
                LastChatComposerRow {
                    LastChatComposerAddButton(
                        onClick = {
                            if (pendingQuestionnaire == null) {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                onPickFile()
                            }
                        },
                        onLongClick = {},
                    ) {
                        LastChatComposerAddIcon(contentDescription = "Attach file")
                    }
                    LastChatComposerCapsule {
                        Column(Modifier.fillMaxWidth()) {
                            if (state.pendingAttachments.isNotEmpty()) {
                                LastChatComposerAttachmentRow {
                                    items(
                                        items = orderedPendingAttachments,
                                        key = { attachment -> attachment.storagePath },
                                    ) { attachment ->
                                        val remove = {
                                            platformHaptics.perform(PlatformHapticPattern.Pop)
                                            onRemovePendingAttachment(attachment.storagePath)
                                        }
                                        when (attachment.kind) {
                                            PlatformPickedFileKind.Image -> {
                                                LastChatComposerImageAttachment(
                                                    onClick = {},
                                                    onRemove = remove,
                                                    removeContentDescription = "Remove attachment",
                                                ) {
                                                    AsyncImage(
                                                        model = attachment.localUrl,
                                                        contentDescription = attachment.displayName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }
                                            PlatformPickedFileKind.Video -> {
                                                LastChatComposerMediaAttachment(onRemove = remove) {
                                                    LastChatComposerVideoIcon()
                                                }
                                            }
                                            PlatformPickedFileKind.Audio -> {
                                                LastChatComposerMediaAttachment(onRemove = remove) {
                                                    LastChatComposerAudioIcon()
                                                }
                                            }
                                            PlatformPickedFileKind.Document -> {
                                                LastChatDocumentAttachmentTile(
                                                    fileName = attachment.displayName,
                                                    modifier = Modifier.size(60.dp),
                                                    onRemove = remove,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth()) {
                                TextField(
                                    state = inputState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 1.dp),
                                    placeholder = {
                                        Text(
                                            if (pendingQuestionnaire != null) "Type another answer"
                                            else "Message ${state.assistant.name}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        top = 12.dp,
                                        end = 52.dp,
                                        bottom = 12.dp,
                                    ),
                                    colors = TextFieldDefaults.colors().copy(
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                    ),
                                )
                                val action = when {
                                    pendingQuestionnaire != null &&
                                        questionnaireIndex < pendingQuestionnaire.questions.lastIndex ->
                                        LastChatComposerAction.QuestionnaireNext
                                    pendingQuestionnaire != null -> LastChatComposerAction.QuestionnaireSubmit
                                    state.generating -> LastChatComposerAction.Loading
                                    state.sttRecording -> LastChatComposerAction.SttRecording
                                    inputState.text.isNotBlank() || state.pendingAttachments.isNotEmpty() ->
                                        LastChatComposerAction.Send
                                    state.stt.enabled && state.hasSttApiKey -> LastChatComposerAction.Stt
                                    appearance.sttReplaceModelIcon && state.stt.enabled -> LastChatComposerAction.Stt
                                    else -> LastChatComposerAction.Picker
                                }
                                LastChatComposerActionButton(
                                    action = action,
                                    onClick = {
                                        when (action) {
                                            LastChatComposerAction.Loading -> {
                                                onCancelGeneration()
                                                platformHaptics.perform(PlatformHapticPattern.Cancel)
                                            }
                                            LastChatComposerAction.Send -> send()
                                            LastChatComposerAction.QuestionnaireNext -> {
                                                questionnaireIndex = (questionnaireIndex + 1)
                                                    .coerceAtMost(pendingQuestionnaire?.questions?.lastIndex ?: 0)
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                            }
                                            LastChatComposerAction.QuestionnaireSubmit ->
                                                submitQuestionnaire(dismissed = false)
                                            LastChatComposerAction.Stt -> {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onStartSpeech()
                                            }
                                            LastChatComposerAction.SttRecording -> {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onStopSpeech { transcript ->
                                                    if (transcript.isNotBlank()) {
                                                        inputState.setTextAndPlaceCursorAtEnd(
                                                            listOf(inputState.text.toString(), transcript)
                                                                .filter { it.isNotBlank() }
                                                                .joinToString(" "),
                                                        )
                                                    }
                                                }
                                            }
                                            LastChatComposerAction.Picker -> {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                showChatModelPicker = true
                                            }
                                            else -> Unit
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .size(36.dp),
                                    content = { currentAction ->
                                        LastChatComposerDefaultActionContent(currentAction) {
                                            Text(
                                                text = state.selectedChatModel?.second?.modelId?.firstOrNull()?.uppercase() ?: "M",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val showNewChatContent = isNewChat &&
                !state.loading &&
                (
                    appearance.newChatHeaderStyle != IosNewChatHeaderStyle.NONE ||
                        appearance.newChatContentStyle != IosNewChatContentStyle.NONE
                    )
            if (showNewChatContent) {
                IosNewChatEmpty(
                    appearance = appearance,
                    assistantName = state.assistant.name,
                    assistantAvatar = state.assistant.avatar,
                    onTemplateClick = { prompt ->
                        inputState.setTextAndPlaceCursorAtEnd(prompt)
                        platformHaptics.perform(PlatformHapticPattern.Pop)
                    },
                    onOpenImageGeneration = onOpenImageGeneration,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                if (state.loading) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                items(messages) { message ->
                    Column {
                        MessageBubble(
                            message = message,
                            attachmentOpener = attachmentOpener,
                            audioPlayer = audioPlayer,
                            playingAudioUrl = playingAudioUrl,
                            platformHaptics = platformHaptics,
                            isTtsSpeaking = state.ttsSpeaking,
                            isTtsAvailable = state.tts.enabled && (state.hasTtsApiKey || state.tts.type == IosTtsProviderType.SYSTEM),
                            appearance = appearance,
                            generating = state.generating && message.messageId == messages.lastOrNull()?.messageId,
                            userAvatar = state.appearance.userAvatar,
                            userNickname = state.appearance.userNickname,
                            assistantAvatar = if (state.assistant.useAssistantAvatar) {
                                state.assistant.avatar
                            } else {
                                IosAvatar.Dummy
                            },
                            onSpeak = onSpeak,
                            onStopSpeaking = onStopSpeaking,
                        )
                        IosMessageActionsRow(
                            message = message,
                            clipboard = clipboard,
                            platformHaptics = platformHaptics,
                            onEdit = {
                                message.messageId?.let { id ->
                                    editingMessageId = id
                                    editingText = message.text
                                }
                            },
                            onDelete = { message.messageId?.let(onDeleteMessage) },
                            onFork = { message.messageId?.let(onForkMessage) },
                            onSelect = { index ->
                                message.branchNode?.let { node ->
                                    onSelectVersion(node.id.toString(), index)
                                }
                            },
                            onRegenerate = onRegenerate,
                        )
                    }
                }
                if (state.generating) item {
                    GroupedMessageBubble(
                        position = BubblePosition.SINGLE,
                        role = BubbleRole.ACTIVITY,
                    ) { TypingIndicator() }
                }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (appearance.showMessageJumper && messages.isNotEmpty()) {
                IosMessageJumper(
                    onLeft = appearance.messageJumperOnLeft,
                    listState = listState,
                    scope = chatScope,
                )
            }
        }
    }
    if (showUpdateDialog) {
        val info = state.updateInfo
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(if (info != null) "Update ${info.version}" else "Updates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info != null) {
                        Text("Current version $IOS_APP_VERSION")
                        if (info.changelog.isNotBlank()) Text(info.changelog)
                        info.downloads.firstOrNull()?.let { download ->
                            Text("${download.name} · ${download.size}")
                        }
                    } else {
                        Text(state.updateCheckError ?: "No GitHub release is available.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = info?.downloads?.firstOrNull()?.url
                            ?: "https://github.com/Cocolalilal/LastChat/releases/latest"
                        openIosExternalUrl(url)
                        showUpdateDialog = false
                    },
                ) { Text("Open release") }
            },
            dismissButton = {
                Row {
                    if (info != null) {
                        TextButton(
                            onClick = {
                                onIgnoreUpdate(info.version)
                                showUpdateDialog = false
                            },
                        ) { Text("Ignore for a week") }
                    }
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Close") }
                }
            },
        )
    }
    editingMessageId?.let { id ->
        AlertDialog(
            onDismissRequest = { editingMessageId = null },
            title = { Text("Edit message") },
            text = {
                TextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parts = buildList {
                            add(UIMessagePart.Text(editingText))
                            state.selectedConversation?.messageNodes
                                ?.flatMap { it.messages }
                                ?.firstOrNull { it.id.toString() == id }
                                ?.parts
                                ?.filter { it !is UIMessagePart.Text }
                                ?.let { addAll(it) }
                        }
                        onEditMessage(id, parts)
                        editingMessageId = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageId = null }) { Text("Cancel") }
            },
        )
    }
    if (showChatModelPicker) {
        IosChatModelPickerSheet(
            state = state,
            searchQuery = chatModelSearchQuery,
            onSearchQueryChange = { chatModelSearchQuery = it },
            onSelect = { providerId, modelId ->
                onSelectDefaultModel(providerId, modelId)
                showChatModelPicker = false
            },
            onDismiss = { showChatModelPicker = false },
            platformHaptics = platformHaptics,
        )
    }
}

@Composable
private fun IosCharacterQuestionsCard(
    questionnaire: IosPendingQuestionnaire,
    currentIndex: Int,
    selectedOptionLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onSelectOption: (IosAskUserOption) -> Unit,
) {
    val question = questionnaire.questions.getOrNull(currentIndex) ?: return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onPrevious, enabled = currentIndex > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous")
                    }
                    Text(
                        "${currentIndex + 1} of ${questionnaire.questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = onNext,
                        enabled = currentIndex < questionnaire.questions.lastIndex,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next")
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                }
            }
            Text(question.question, style = MaterialTheme.typography.titleMedium)
            question.options.forEach { option ->
                IosCharacterQuestionOptionRow(
                    option = option,
                    selected = selectedOptionLabel == option.label,
                    onClick = { onSelectOption(option) },
                )
            }
        }
    }
}

@Composable
private fun IosCharacterQuestionOptionRow(
    option: IosAskUserOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "question_option_scale",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            option.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: DisplayMessage,
    attachmentOpener: PlatformAttachmentOpener,
    audioPlayer: PlatformAttachmentAudioPlayer,
    playingAudioUrl: String?,
    platformHaptics: PlatformHaptics,
    isTtsSpeaking: Boolean,
    isTtsAvailable: Boolean,
    appearance: IosAppearancePreferences,
    generating: Boolean = false,
    userAvatar: IosAvatar = IosAvatar.Dummy,
    userNickname: String = "",
    assistantAvatar: IosAvatar = IosAvatar.Dummy,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val styledText = remember(message.text, appearance.rpStyleRules) {
        buildIosRoleplayText(message.text, appearance.rpStyleRules)
    }
    val segments = remember(message.text) { splitIosChatText(message.text) }
    val attachments = message.parts.filter { part ->
        part is UIMessagePart.Image || part is UIMessagePart.Video ||
            part is UIMessagePart.Audio || part is UIMessagePart.Document
    }
    val fontSizeRatio = appearance.fontSizeRatio
    var reasoningExpanded by remember(message.messageId, appearance.autoCloseThinking, generating) {
        mutableStateOf(!appearance.autoCloseThinking || generating)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!message.outgoing && (appearance.showModelIcon || appearance.showModelName)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (appearance.showModelIcon) {
                    IosAvatarView(
                        avatar = assistantAvatar,
                        letter = iosAvatarLetter(message.modelName ?: "A", "A"),
                        size = 28.dp,
                    )
                }
                if (appearance.showModelName) {
                    Text(
                        text = message.modelName?.ifBlank { "Assistant" } ?: "Assistant",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (message.outgoing && appearance.showUserAvatar) {
            IosAvatarView(
                avatar = userAvatar,
                letter = iosAvatarLetter(userNickname, "Y"),
                size = 28.dp,
            )
        }
        if (!message.outgoing && message.reasoning.isNotBlank()) {
            Surface(
                onClick = { reasoningExpanded = !reasoningExpanded },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        if (reasoningExpanded) "Thinking" else "Thought",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val preview = if (reasoningExpanded) {
                        message.reasoning
                    } else if (appearance.reasoningPreviewEnabled) {
                        iosReasoningPreview(message.reasoning)
                    } else {
                        null
                    }
                    preview?.let { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (reasoningExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (attachments.isNotEmpty()) {
            LastChatMessageAttachmentRow(alignEnd = message.outgoing) {
                items(
                    items = attachments,
                    key = { part -> part.hashCode() },
                ) { part ->
                    when (part) {
                        is UIMessagePart.Image -> AsyncImage(
                            model = part.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .size(72.dp)
                                .clickable {
                                    platformHaptics.perform(PlatformHapticPattern.Pop)
                                    attachmentOpener.open(part.url)
                                },
                        )
                        is UIMessagePart.Video -> LastChatDocumentAttachmentTile(
                            fileName = "Video",
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
                        )
                        is UIMessagePart.Audio -> LastChatAudioAttachmentTile(
                            fileName = "Audio",
                            playing = playingAudioUrl == part.url,
                            onToggle = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                audioPlayer.toggle(part.url)
                            },
                            modifier = Modifier.size(72.dp),
                        )
                        is UIMessagePart.Document -> LastChatDocumentAttachmentTile(
                            fileName = part.fileName,
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
                        )
                        else -> Unit
                    }
                }
            }
        }
        if (message.text.isNotBlank()) {
            if (!message.outgoing && !appearance.showAssistantBubbles) {
                IosChatTextBody(
                    segments = segments,
                    styledText = styledText,
                    fontSizeRatio = fontSizeRatio,
                    wrapCode = appearance.codeBlockAutoWrap,
                    collapseCode = appearance.codeBlockAutoCollapse,
                    codeFontFamily = iosCodeFontFamily(appearance.fontSettings),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                )
            } else {
                GroupedMessageBubble(
                    position = message.position,
                    role = if (message.outgoing) BubbleRole.USER else BubbleRole.ASSISTANT,
                    modifier = Modifier.fillMaxWidth(0.86f),
                ) {
                    IosChatTextBody(
                        segments = segments,
                        styledText = styledText,
                        fontSizeRatio = fontSizeRatio,
                        wrapCode = appearance.codeBlockAutoWrap,
                        collapseCode = appearance.codeBlockAutoCollapse,
                        codeFontFamily = iosCodeFontFamily(appearance.fontSettings),
                    )
                }
            }
            if (!message.outgoing) {
                LastChatTtsAction(
                    isSpeaking = isTtsSpeaking,
                    isAvailable = isTtsAvailable,
                    contentDescription = "Text to speech",
                    onClick = {
                        platformHaptics.perform(PlatformHapticPattern.Pop)
                        if (isTtsSpeaking) onStopSpeaking() else onSpeak(message.text)
                    },
                )
            }
        }
        if (!message.outgoing && appearance.showContextStacks && message.contextStack.total > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    message.contextStack.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (!message.outgoing && appearance.showTokenUsage) {
            message.usage?.let { usage ->
                Text(
                    iosTokenUsageLabel(usage.promptTokens, usage.completionTokens, usage.totalTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IosAvatarView(
    avatar: IosAvatar,
    letter: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (avatar) {
                is IosAvatar.Emoji -> Text(avatar.content, style = MaterialTheme.typography.titleMedium)
                is IosAvatar.Image -> AsyncImage(
                    model = avatar.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                IosAvatar.Dummy -> Text(letter, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun IosAvatarGlyph(label: String, size: androidx.compose.ui.unit.Dp) {
    IosAvatarView(avatar = IosAvatar.Dummy, letter = label, size = size)
}

@Composable
private fun IosChatTextBody(
    segments: List<IosChatTextSegment>,
    styledText: AnnotatedString,
    fontSizeRatio: Float,
    wrapCode: Boolean,
    collapseCode: Boolean,
    codeFontFamily: FontFamily = FontFamily.Monospace,
    modifier: Modifier = Modifier,
) {
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontSizeRatio,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontSizeRatio,
    )
    if (segments.none { it is IosChatTextSegment.Code }) {
        Text(text = styledText, style = bodyStyle, modifier = modifier)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is IosChatTextSegment.Text -> {
                    if (segment.value.isNotBlank()) {
                        Text(
                            text = buildIosRoleplayText(segment.value, emptyList()),
                            style = bodyStyle,
                        )
                    }
                }
                is IosChatTextSegment.Code -> IosChatCodeBlock(
                    language = segment.language,
                    code = segment.value,
                    wrap = wrapCode,
                    collapse = collapseCode,
                    fontFamily = codeFontFamily,
                )
            }
        }
    }
}

@Composable
private fun IosChatCodeBlock(
    language: String,
    code: String,
    wrap: Boolean,
    collapse: Boolean,
    fontFamily: FontFamily = FontFamily.Monospace,
) {
    var expanded by remember(code) { mutableStateOf(!collapse) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (collapse) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Collapse" else "Expand")
                    }
                }
            }
            if (expanded) {
                val codeModifier = if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontFamily),
                    modifier = codeModifier,
                    softWrap = wrap,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IosNewChatEmpty(
    appearance: IosAppearancePreferences,
    assistantName: String,
    assistantAvatar: IosAvatar = IosAvatar.Dummy,
    onTemplateClick: (String) -> Unit,
    onOpenImageGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (appearance.newChatHeaderStyle) {
            IosNewChatHeaderStyle.NONE -> Unit
            IosNewChatHeaderStyle.GREETING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (appearance.newChatShowAvatar) {
                        IosAvatarView(
                            avatar = assistantAvatar,
                            letter = iosAvatarLetter(assistantName, "A"),
                            size = 44.dp,
                        )
                    }
                    Text(
                        iosGreeting(hour, assistantName),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            IosNewChatHeaderStyle.BIG_ICON -> {
                if (appearance.newChatShowAvatar) {
                    IosAvatarView(
                        avatar = assistantAvatar,
                        letter = iosAvatarLetter(assistantName, "A"),
                        size = 80.dp,
                    )
                }
                Text(
                    assistantName.ifBlank { "Assistant" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        when (appearance.newChatContentStyle) {
            IosNewChatContentStyle.NONE -> Unit
            IosNewChatContentStyle.TEMPLATES -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IosNewChatTemplateCard("Write", Icons.Rounded.Edit) { onTemplateClick(IOS_NEW_CHAT_WRITE_PROMPT) }
                    IosNewChatTemplateCard("Code", Icons.Rounded.Code) { onTemplateClick(IOS_NEW_CHAT_CODE_PROMPT) }
                    IosNewChatTemplateCard("Brainstorm", Icons.Rounded.Lightbulb) {
                        onTemplateClick(IOS_NEW_CHAT_BRAINSTORM_PROMPT)
                    }
                    IosNewChatTemplateCard("Learn", Icons.AutoMirrored.Rounded.MenuBook) {
                        onTemplateClick(IOS_NEW_CHAT_LEARN_PROMPT)
                    }
                }
            }
            IosNewChatContentStyle.ACTIONS -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IosNewChatActionPill("Create image", Icons.Rounded.Image, onOpenImageGeneration)
                    IosNewChatActionPill("Brainstorm", Icons.Rounded.Lightbulb) {
                        onTemplateClick(IOS_NEW_CHAT_BRAINSTORM_PROMPT)
                    }
                    IosNewChatActionPill("Code", Icons.Rounded.Code) { onTemplateClick(IOS_NEW_CHAT_CODE_PROMPT) }
                    IosNewChatActionPill("Write", Icons.Rounded.Edit) { onTemplateClick(IOS_NEW_CHAT_WRITE_PROMPT) }
                }
            }
        }
    }
}

@Composable
private fun IosNewChatTemplateCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IosNewChatActionPill(title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.IosMessageJumper(
    onLeft: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    AnimatedVisibility(
        visible = true,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(initialOffsetX = { if (onLeft) -it * 2 else it * 2 }),
        exit = slideOutHorizontally(targetOffsetX = { if (onLeft) -it * 2 else it * 2 }),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IosJumperButton(Icons.Rounded.KeyboardDoubleArrowUp, "Jump to top") {
                scope.launch { listState.animateScrollToItem(0) }
            }
            IosJumperButton(Icons.Rounded.KeyboardArrowUp, "Previous message") {
                scope.launch {
                    listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                }
            }
            IosJumperButton(Icons.Rounded.KeyboardArrowDown, "Next message") {
                scope.launch {
                    val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(last))
                }
            }
            IosJumperButton(Icons.Rounded.KeyboardDoubleArrowDown, "Jump to bottom") {
                scope.launch {
                    val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(last)
                }
            }
        }
    }
}

@Composable
private fun IosJumperButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.padding(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPage(
    state: IosAppState,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onSaveUserProfile: (String, IosAvatar) -> Unit = { _, _ -> },
    onPickUserAvatar: ((Result<PlatformPickedFile?>) -> Unit) -> Unit = {},
    darkTheme: Boolean,
    platformHaptics: PlatformHaptics,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
    onImageGeneration: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showAssistantPicker by remember { mutableStateOf(false) }
    var showUserProfile by remember { mutableStateOf(false) }
    var nicknameDraft by remember(state.appearance.userNickname) {
        mutableStateOf(state.appearance.userNickname)
    }
    val filteredConversations = remember(state.conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { conversation ->
                conversation.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            item {
                Surface(
                    onClick = { showUserProfile = true },
                    shape = AppShapes.CardSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IosAvatarView(
                            avatar = state.appearance.userAvatar,
                            letter = iosAvatarLetter(state.appearance.userNickname, "Y"),
                            size = 40.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.appearance.userNickname.ifBlank { "You" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Tap to set nickname and avatar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            item {
                LastChatDrawerSearch(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    placeholder = "Search conversations",
                    hint = "Search titles",
                )
            }
            item {
                AnimatedVisibility(
                    visible = !searchExpanded,
                    enter = fadeIn(animationSpec = spring(stiffness = 300f)) +
                        expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)),
                    exit = fadeOut(animationSpec = spring(stiffness = 500f)) +
                        shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)),
                ) {
                    LastChatDrawerQuickActionGroup {
                        LastChatDrawerQuickAction(
                            label = "Imagine",
                            onClick = onImageGeneration,
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Tick) },
                            icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                        )
                        LastChatDrawerQuickAction(
                            label = "Statistics",
                            onClick = onStatistics,
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Tick) },
                            icon = { LastChatBarChartIcon(contentDescription = null) },
                        )
                    }
                }
            }
            if (filteredConversations.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = AppShapes.CardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "No conversations",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            items(filteredConversations, key = { it.id }) { conversation ->
                ConversationListRow(
                    title = conversation.title,
                    selected = conversation.id == state.selectedConversationId,
                    platformHaptics = platformHaptics,
                    onRename = { title -> onRenameConversation(conversation.id, title) },
                    onDelete = { onDeleteConversation(conversation.id) },
                ) {
                    onSelectConversation(conversation.id)
                    onDismiss()
                }
            }
            item {
                val actionButtonSize = 42.dp
                val assistantAvatarSize = 30.dp
                val itemColor = MaterialTheme.colorScheme.surfaceContainerHighest
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = itemColor,
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1f).height(actionButtonSize),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        if (state.assistants.size > 1) showAssistantPicker = true
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = state.assistant.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IosAssistantAvatar(
                                name = state.assistant.name,
                                avatar = state.assistant.avatar,
                                modifier = Modifier
                                    .size(assistantAvatarSize)
                                    .clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        onSettings()
                                    },
                            )
                        }
                    }
                    LastChatDrawerAction(
                        onClick = onSettings,
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        containerColor = itemColor,
                        size = actionButtonSize,
                    ) { containerSize, iconSize ->
                        LastChatDrawerActionIcon(
                            containerSize = containerSize,
                            iconSize = iconSize,
                            contentDescription = "Settings",
                        )
                    }
                }
            }
    }
    if (showAssistantPicker) {
        val pickerItems = state.assistants.map { assistant ->
            LastChatAssistantPickerItem(
                id = assistant.id,
                name = assistant.name,
                systemPrompt = assistant.systemPrompt,
            )
        }
        LastChatAssistantPickerSheet(
            assistants = pickerItems,
            currentAssistantId = state.assistant.id,
            title = "Assistants",
            noSystemPromptLabel = "No system prompt",
            onAssistantSelected = onSelectAssistant,
            onNavigate = {
                showAssistantPicker = false
                onDismiss()
            },
            onEdit = {
                showAssistantPicker = false
                onSettings()
            },
            onDismiss = { showAssistantPicker = false },
            onPopHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
            onThudHaptic = { platformHaptics.perform(PlatformHapticPattern.Thud) },
            avatar = { item, modifier ->
                val assistant = state.assistants.firstOrNull { it.id == item.id }
                IosAssistantAvatar(item.name, assistant?.avatar ?: IosAvatar.Dummy, modifier)
            },
        )
    }
    if (showUserProfile) {
        IosAvatarPickerDialog(
            title = "You",
            nickname = nicknameDraft,
            avatar = state.appearance.userAvatar,
            onNicknameChange = { nicknameDraft = it },
            onSelectEmoji = { emoji ->
                onSaveUserProfile(nicknameDraft, IosAvatar.Emoji(emoji))
            },
            onSelectImage = {
                onPickUserAvatar { result ->
                    val picked = result.getOrNull() ?: return@onPickUserAvatar
                    if (picked.kind == PlatformPickedFileKind.Image) {
                        onSaveUserProfile(
                            nicknameDraft,
                            IosAvatar.Image(picked.localUrl.ifBlank { picked.storagePath }),
                        )
                    }
                }
            },
            onSelectDummy = { onSaveUserProfile(nicknameDraft, IosAvatar.Dummy) },
            onSave = {
                onSaveUserProfile(nicknameDraft, state.appearance.userAvatar)
                showUserProfile = false
            },
            onDismiss = { showUserProfile = false },
        )
    }
}

@Composable
private fun IosAssistantAvatar(
    name: String,
    avatar: IosAvatar = IosAvatar.Dummy,
    modifier: Modifier = Modifier,
) {
    IosAvatarView(
        avatar = avatar,
        letter = iosAvatarLetter(name, "A"),
        size = 30.dp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosImageGenerationPage(
    state: IosAppState,
    onGenerate: (String, String, Int, PlatformPickedFile?) -> Unit,
    onPickImage: ((Result<PlatformPickedFile?>) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    attachmentOpener: PlatformAttachmentOpener,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    var showGallery by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("square") }
    var count by remember { mutableStateOf(1) }
    var inputImage by remember { mutableStateOf<PlatformPickedFile?>(null) }
    val supportsInputImage = state.imageGeneration.method == ImageGenerationMethod.MULTIMODAL &&
        state.providers.firstOrNull { it.id.toString() == state.imageGeneration.providerId }
            ?.let { it is ProviderSetting.ComfyUI } != true
    val images = state.generatedImages.asReversed()
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            TopAppBar(
                navigationIcon = { LastChatBackButton(onClick = onBack, contentDescription = "Back") },
                title = { Text(if (showGallery) "Gallery" else "Imagine") },
                actions = {
                    IconButton(onClick = {
                        platformHaptics.perform(PlatformHapticPattern.Tick)
                        showGallery = !showGallery
                    }) {
                        Icon(
                            if (showGallery) Icons.Rounded.AutoAwesome else Icons.Rounded.Collections,
                            contentDescription = if (showGallery) "Imagine" else "Gallery",
                        )
                    }
                    if (!showGallery) IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Generation settings")
                    }
                },
            )
        },
        bottomBar = {
            if (!showGallery) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (supportsInputImage) {
                            if (inputImage == null) {
                                IconButton(
                                    onClick = {
                                        onPickImage { result ->
                                            result.getOrNull()
                                                ?.takeIf { it.kind == PlatformPickedFileKind.Image }
                                                ?.let { inputImage = it }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.Image, "Add input image")
                                }
                            } else {
                                Box(Modifier.size(48.dp)) {
                                    AsyncImage(
                                        model = inputImage?.localUrl,
                                        contentDescription = inputImage?.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                    Surface(
                                        onClick = { inputImage = null },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                                    ) {
                                        Icon(Icons.Rounded.Close, "Remove input image")
                                    }
                                }
                            }
                        }
                        TextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { Text("Describe an image") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 5,
                            colors = TextFieldDefaults.colors().copy(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Surface(
                            onClick = {
                                if (state.imageGenerating) {
                                    onCancel()
                                    platformHaptics.perform(PlatformHapticPattern.Cancel)
                                } else if (prompt.isNotBlank()) {
                                    onGenerate(prompt, aspectRatio, count, inputImage)
                                    platformHaptics.perform(PlatformHapticPattern.Send)
                                }
                            },
                            shape = CircleShape,
                            color = if (state.imageGenerating) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.imageGenerating) Icon(Icons.Rounded.Stop, "Stop")
                                else Icon(Icons.Rounded.AutoAwesome, "Generate")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = showGallery,
            transitionSpec = { fadeIn(spring(dampingRatio = 0.6f, stiffness = 400f)) togetherWith
                fadeOut(spring(dampingRatio = 0.6f, stiffness = 400f)) },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "image_view_crossfade",
        ) { gallery ->
            if (gallery) {
                if (images.isEmpty()) IosImageEmptyState("Your generated images will appear here")
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(images, key = { it.path }) { image ->
                        Box {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = image.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { attachmentOpener.open(image.uri) },
                            )
                            IconButton(onClick = { onDelete(image.path) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Rounded.Delete, "Delete")
                            }
                        }
                    }
                }
            } else if (images.isEmpty()) IosImageEmptyState("Describe what you want to imagine")
            else LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(images, key = { it.path }) { image ->
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { attachmentOpener.open(image.uri) },
                    )
                }
            }
        }
    }
    if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Generation settings", style = MaterialTheme.typography.titleLarge)
            Text("Aspect ratio", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("square", "landscape", "portrait").forEach { ratio ->
                    if (aspectRatio == ratio) Button(onClick = {}) { Text(ratio.replaceFirstChar { it.uppercase() }) }
                    else TextButton(onClick = { aspectRatio = ratio }) { Text(ratio.replaceFirstChar { it.uppercase() }) }
                }
            }
            Text("Images", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { value ->
                    if (count == value) Button(onClick = {}) { Text(value.toString()) }
                    else TextButton(onClick = { count = value }) { Text(value.toString()) }
                }
            }
            TextButton(onClick = onOpenSettings) { Text("Configure image model") }
        }
    }
}

@Composable
private fun IosImageEmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 160.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsPage(
    state: IosAppState,
    darkTheme: Boolean,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val heatmapData = remember(state.dailyActivity) {
        state.dailyActivity.mapNotNull { entry ->
            val date = PortableConversationQueries.parseIsoDate(entry.date) ?: return@mapNotNull null
            CalendarHeatmapDay(date, entry.messageCount)
        }.sortedBy { it.date }
    }
    val totals = state.usageTotals
    val promptTokens = totals.inputTokens
    val completionTokens = totals.outputTokens
    val cachedTokens = totals.cachedTokens
    val emptyStats = heatmapData.none { it.count > 0 } &&
        totals.conversationCount == 0L &&
        totals.messageCount == 0L
    val neutralContainer = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IosBackButton(platformHaptics, onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LastChatActivityHeatmapCard(
                    heatmapData = heatmapData,
                    today = today,
                    activityTitle = "Activity",
                    emptyText = "No activity yet",
                    weekdayLabels = listOf("Mon", "", "Wed", "", "Fri", "", "Sun"),
                    lessLabel = "Less",
                    moreLabel = "More",
                    fallbackMonthLabel = "Activity timeline",
                    monthName = { month, abbreviated -> englishMonthName(month, abbreviated) },
                    messageCountText = { count ->
                        "${formatCompactCount(count)} ${if (count == 1L) "message" else "messages"}"
                    },
                    showEmptyState = emptyStats,
                    darkTheme = darkTheme,
                    onMonthSelected = {
                        platformHaptics.perform(PlatformHapticPattern.Pop)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LastChatStatCard(
                    title = "Conversations",
                    value = formatCompactCount(totals.conversationCount),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    icon = { LastChatStatIconGlyph(LastChatStatIcon.Conversations) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LastChatStatCard(
                        title = "Messages",
                        value = formatCompactCount(totals.messageCount),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.Messages) },
                    )
                    LastChatStatCard(
                        title = "Input tokens",
                        value = formatCompactCount(promptTokens),
                        containerColor = neutralContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.InputTokens) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LastChatStatCard(
                        title = "Output tokens",
                        value = formatCompactCount(completionTokens),
                        containerColor = neutralContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.OutputTokens) },
                    )
                    LastChatStatCard(
                        title = "Cached tokens",
                        value = formatCompactCount(cachedTokens),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.CachedTokens) },
                    )
                }
            }
        }
    }
}

private fun englishMonthName(month: CalendarMonth, abbreviated: Boolean): String {
    val name = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )[month.monthNumber - 1]
    return if (abbreviated) name.take(3) else name
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "${(value / 100_000_000.0).toLong() / 10.0}B"
    value >= 1_000_000 -> "${(value / 100_000.0).toLong() / 10.0}M"
    value >= 1_000 -> "${(value / 100.0).toLong() / 10.0}K"
    else -> value.toString()
}

private val GENERATED_MARKDOWN_IMAGE_REGEX = Regex("!\\[[^]]*]\\(([^)]+)\\)")

@Composable
private fun ConversationListRow(
    title: String,
    selected: Boolean,
    platformHaptics: PlatformHaptics,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(title) { mutableStateOf(title) }
    Box {
        ConversationRowSurface(
            selected = selected,
            onClick = {
                platformHaptics.perform(PlatformHapticPattern.Tick)
                onClick()
            },
            onLongClick = {
                platformHaptics.perform(PlatformHapticPattern.Buildup)
                showMenu = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.ifBlank { "New chat" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = AppShapes.ButtonRounded,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("Edit title") },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    editedTitle = title
                    showMenu = false
                    showEditTitle = true
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Error)
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
    if (showEditTitle) {
        AlertDialog(
            onDismissRequest = { showEditTitle = false },
            title = { Text("Edit title") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    singleLine = true,
                    shape = AppShapes.InputField,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(editedTitle)
                    showEditTitle = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitle = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveProvider: (String, String, String, String) -> Unit,
    onAddProvider: (String, IosProviderType) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onAddModel: (String, String) -> Unit,
    onRemoveModel: (String, String) -> Unit,
    onClearProviderApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSelectDefaultModel: (String, String) -> Unit,
    onSaveSearch: (
        IosSearchProviderType,
        Boolean,
        Int,
        String,
        String,
        String,
        String,
        String,
        String,
    ) -> Unit,
    onClearSearchApiKey: () -> Unit,
    onSaveTts: (IosTtsPreferences, String) -> Unit,
    onClearTtsApiKey: () -> Unit,
    onSaveImageGeneration: (IosImageGenerationPreferences) -> Unit,
    onSaveAppearance: (String, IosColorMode) -> Unit,
    onSaveFontSettings: (Boolean) -> Unit,
    onSaveUiCustomization: (Boolean, Float, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSaveDisplayKnobs: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSaveAppearancePreferences: (IosAppearancePreferences) -> Unit,
    onRefreshUpdateCheck: () -> Unit,
    onRefreshModelCatalog: () -> Unit,
    onDownloadLocalLlm: (String) -> Unit,
    onDownloadLocalStt: (String) -> Unit,
    onCancelLocalDownload: (String) -> Unit,
    onDeleteLocalLlm: (String) -> Unit,
    onDeleteLocalStt: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSaveRpStyleRules: (List<IosRpStyleRule>) -> Unit,
    onSaveAssistant: (String, String) -> Unit,
    onSaveAssistantAvatar: (IosAvatar, Boolean?) -> Unit = { _, _ -> },
    onSaveAssistantUiSettings: (IosAssistantUiSettings) -> Unit = {},
    onPickAvatarFile: ((Result<PlatformPickedFile?>) -> Unit) -> Unit = {},
    onSaveSpontaneousSettings: (Boolean, Int, Int, Int, String) -> Unit,
    onSaveComfyUiProvider: (String, String, String, String, String, String, String, String) -> Unit,
    onImportComfyUiWorkflow: (String, String) -> Unit,
    onRunStorageMaintenance: () -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onSaveMemorySettings: (IosMemoryMode, String?, String, Float, Int) -> Unit,
    onAddMemory: (String) -> Unit,
    onUpdateMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int) -> Unit,
    onRegenerateMemoryEmbeddings: () -> Unit,
    onSaveLocalTools: (Set<IosLocalToolOption>) -> Unit,
    onSavePromptInjections: (List<me.rerere.rikkahub.data.prompt.PortableSkill>, List<me.rerere.rikkahub.data.prompt.PortableLorebook>, Set<String>, Set<String>) -> Unit,
    onSaveMcpServers: (List<me.rerere.rikkahub.data.mcp.PortableMcpServer>, Set<String>) -> Unit,
    onRefreshMcpTools: (String) -> Unit,
    onSaveStt: (IosSttPreferences, String) -> Unit,
    onClearSttApiKey: () -> Unit,
    onSaveWeb: (IosWebPreferences, String) -> Unit,
    onSaveWebDav: (IosWebDavPreferences, String) -> Unit,
    onTestWebDav: () -> Unit,
    onListWebDav: () -> Unit,
    onBackupWebDav: () -> Unit,
    onRestoreWebDav: (String) -> Unit,
    onDeleteWebDav: (String) -> Unit,
    onSaveOverlaySettings: (String?, Boolean, Boolean, Boolean) -> Unit,
    onSaveDeveloperMode: (Boolean) -> Unit,
    onPickBackupFile: ((Result<PlatformPickedFile?>) -> Unit) -> Unit,
    onRestoreBackup: (String, (Result<IosBackupImportReport>) -> Unit) -> Unit,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf(IosSettingsSection.Home) }
    var activeDestinationId by remember { mutableStateOf("") }
    var unavailableDestinationId by remember { mutableStateOf("") }
    var unavailableDestinationTitle by remember { mutableStateOf("Unavailable") }
    var showModelPicker by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }
    var searchProvider by remember(state.search.provider) {
        mutableStateOf(state.search.provider)
    }
    var searchEnabled by remember(state.search.enabled) {
        mutableStateOf(state.search.enabled)
    }
    var searchResultSize by remember(state.search.resultSize) {
        mutableStateOf(state.search.resultSize.toString())
    }
    var searchApiKey by remember { mutableStateOf("") }
    var searxngUrl by remember(state.search.searxngUrl) { mutableStateOf(state.search.searxngUrl) }
    var searxngEngines by remember(state.search.searxngEngines) { mutableStateOf(state.search.searxngEngines) }
    var searxngLanguage by remember(state.search.searxngLanguage) { mutableStateOf(state.search.searxngLanguage) }
    var searxngUsername by remember(state.search.searxngUsername) { mutableStateOf(state.search.searxngUsername) }
    var searxngPassword by remember { mutableStateOf("") }
    var ttsPreferences by remember(state.tts) { mutableStateOf(state.tts) }
    var imageGeneration by remember(state.imageGeneration) { mutableStateOf(state.imageGeneration) }
    var ttsApiKey by remember { mutableStateOf("") }
    var ttsSpeed by remember(state.tts.speed) { mutableStateOf(state.tts.speed.toString()) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var providerName by remember { mutableStateOf("") }
    var providerBaseUrl by remember { mutableStateOf("") }
    var providerApiKey by remember { mutableStateOf("") }
    var newModelId by remember { mutableStateOf("") }
    var showAddProvider by remember { mutableStateOf(false) }
    var newProviderName by remember { mutableStateOf("") }
    var newProviderType by remember { mutableStateOf(IosProviderType.OPENAI) }
    var themeId by remember(state.appearance.themeId) { mutableStateOf(state.appearance.themeId) }
    var colorMode by remember(state.appearance.colorMode) { mutableStateOf(state.appearance.colorMode) }
    var usePhoneSystemFont by remember(state.appearance.usePhoneSystemFont) {
        mutableStateOf(state.appearance.usePhoneSystemFont)
    }
    var showAssistantBubbles by remember(state.appearance.showAssistantBubbles) {
        mutableStateOf(state.appearance.showAssistantBubbles)
    }
    var fontSizeRatio by remember(state.appearance.fontSizeRatio) {
        mutableStateOf(state.appearance.fontSizeRatio)
    }
    var showModelIcon by remember(state.appearance.showModelIcon) {
        mutableStateOf(state.appearance.showModelIcon)
    }
    var showTokenUsage by remember(state.appearance.showTokenUsage) {
        mutableStateOf(state.appearance.showTokenUsage)
    }
    var autoCloseThinking by remember(state.appearance.autoCloseThinking) {
        mutableStateOf(state.appearance.autoCloseThinking)
    }
    var enableUIHaptics by remember(state.appearance.enableUIHaptics) {
        mutableStateOf(state.appearance.enableUIHaptics)
    }
    var notifyOnGeneration by remember(state.appearance.enableNotificationOnMessageGeneration) {
        mutableStateOf(state.appearance.enableNotificationOnMessageGeneration)
    }
    var checkForUpdates by remember(state.appearance.checkForUpdates) {
        mutableStateOf(state.appearance.checkForUpdates)
    }
    var createNewConversationOnStart by remember(state.appearance.createNewConversationOnStart) {
        mutableStateOf(state.appearance.createNewConversationOnStart)
    }
    var ttsAutoplay by remember(state.appearance.ttsAutoplay) {
        mutableStateOf(state.appearance.ttsAutoplay)
    }
    var showMessageJumper by remember(state.appearance.showMessageJumper) {
        mutableStateOf(state.appearance.showMessageJumper)
    }
    var messageJumperOnLeft by remember(state.appearance.messageJumperOnLeft) {
        mutableStateOf(state.appearance.messageJumperOnLeft)
    }
    var enableBlurEffect by remember(state.appearance.enableBlurEffect) {
        mutableStateOf(state.appearance.enableBlurEffect)
    }
    var codeBlockAutoWrap by remember(state.appearance.codeBlockAutoWrap) {
        mutableStateOf(state.appearance.codeBlockAutoWrap)
    }
    var codeBlockAutoCollapse by remember(state.appearance.codeBlockAutoCollapse) {
        mutableStateOf(state.appearance.codeBlockAutoCollapse)
    }
    var showContextStacks by remember(state.appearance.showContextStacks) {
        mutableStateOf(state.appearance.showContextStacks)
    }
    var newChatHeaderStyle by remember(state.appearance.newChatHeaderStyle) {
        mutableStateOf(state.appearance.newChatHeaderStyle)
    }
    var newChatContentStyle by remember(state.appearance.newChatContentStyle) {
        mutableStateOf(state.appearance.newChatContentStyle)
    }
    var newChatShowAvatar by remember(state.appearance.newChatShowAvatar) {
        mutableStateOf(state.appearance.newChatShowAvatar)
    }
    var enableGenerationHaptics by remember(state.appearance.enableMessageGenerationHapticEffect) {
        mutableStateOf(state.appearance.enableMessageGenerationHapticEffect)
    }
    var showUserAvatar by remember(state.appearance.showUserAvatar) {
        mutableStateOf(state.appearance.showUserAvatar)
    }
    var showModelName by remember(state.appearance.showModelName) {
        mutableStateOf(state.appearance.showModelName)
    }
    var showContextTokenSummary by remember(state.appearance.showContextTokenSummary) {
        mutableStateOf(state.appearance.showContextTokenSummary)
    }
    var reasoningPreviewEnabled by remember(state.appearance.reasoningPreviewEnabled) {
        mutableStateOf(state.appearance.reasoningPreviewEnabled)
    }
    var chatToolbarAtBottom by remember(state.appearance.chatToolbarAtBottom) {
        mutableStateOf(state.appearance.chatToolbarAtBottom)
    }
    var sttReplaceModelIcon by remember(state.appearance.sttReplaceModelIcon) {
        mutableStateOf(state.appearance.sttReplaceModelIcon)
    }
    var userNickname by remember(state.appearance.userNickname) {
        mutableStateOf(state.appearance.userNickname)
    }
    var userAvatar by remember(state.appearance.userAvatar) {
        mutableStateOf(state.appearance.userAvatar)
    }
    var fontSettings by remember(state.appearance.fontSettings) {
        mutableStateOf(state.appearance.fontSettings)
    }
    var ttsTextFilterRules by remember(state.appearance.ttsTextFilterRules) {
        mutableStateOf(state.appearance.ttsTextFilterRules)
    }
    var providerViewMode by remember(state.appearance.providerViewMode) {
        mutableStateOf(state.appearance.providerViewMode)
    }
    var editingTtsFilter by remember { mutableStateOf<TtsTextFilterRule?>(null) }
    var showAddTtsFilter by remember { mutableStateOf(false) }
    fun appearanceDraft(): IosAppearancePreferences = state.appearance.copy(
        showAssistantBubbles = showAssistantBubbles,
        fontSizeRatio = fontSizeRatio,
        showModelIcon = showModelIcon,
        showTokenUsage = showTokenUsage,
        autoCloseThinking = autoCloseThinking,
        enableUIHaptics = enableUIHaptics,
        enableNotificationOnMessageGeneration = notifyOnGeneration,
        checkForUpdates = checkForUpdates,
        createNewConversationOnStart = createNewConversationOnStart,
        ttsAutoplay = ttsAutoplay,
        showMessageJumper = showMessageJumper,
        messageJumperOnLeft = messageJumperOnLeft,
        enableBlurEffect = enableBlurEffect,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        showContextStacks = showContextStacks,
        newChatHeaderStyle = newChatHeaderStyle,
        newChatContentStyle = newChatContentStyle,
        newChatShowAvatar = newChatShowAvatar,
        enableMessageGenerationHapticEffect = enableGenerationHaptics,
        showUserAvatar = showUserAvatar,
        showModelName = showModelName,
        showContextTokenSummary = showContextTokenSummary,
        reasoningPreviewEnabled = reasoningPreviewEnabled,
        chatToolbarAtBottom = chatToolbarAtBottom,
        sttReplaceModelIcon = sttReplaceModelIcon,
        userNickname = userNickname,
        userAvatar = userAvatar,
        fontSettings = fontSettings,
        ttsTextFilterRules = ttsTextFilterRules,
        providerViewMode = providerViewMode,
    )
    fun persistAppearance(transform: IosAppearancePreferences.() -> IosAppearancePreferences) {
        val next = appearanceDraft().transform()
        showAssistantBubbles = next.showAssistantBubbles
        fontSizeRatio = next.fontSizeRatio
        showModelIcon = next.showModelIcon
        showTokenUsage = next.showTokenUsage
        autoCloseThinking = next.autoCloseThinking
        enableUIHaptics = next.enableUIHaptics
        notifyOnGeneration = next.enableNotificationOnMessageGeneration
        checkForUpdates = next.checkForUpdates
        createNewConversationOnStart = next.createNewConversationOnStart
        ttsAutoplay = next.ttsAutoplay
        showMessageJumper = next.showMessageJumper
        messageJumperOnLeft = next.messageJumperOnLeft
        enableBlurEffect = next.enableBlurEffect
        codeBlockAutoWrap = next.codeBlockAutoWrap
        codeBlockAutoCollapse = next.codeBlockAutoCollapse
        showContextStacks = next.showContextStacks
        newChatHeaderStyle = next.newChatHeaderStyle
        newChatContentStyle = next.newChatContentStyle
        newChatShowAvatar = next.newChatShowAvatar
        enableGenerationHaptics = next.enableMessageGenerationHapticEffect
        showUserAvatar = next.showUserAvatar
        showModelName = next.showModelName
        showContextTokenSummary = next.showContextTokenSummary
        reasoningPreviewEnabled = next.reasoningPreviewEnabled
        chatToolbarAtBottom = next.chatToolbarAtBottom
        sttReplaceModelIcon = next.sttReplaceModelIcon
        userNickname = next.userNickname
        userAvatar = next.userAvatar
        fontSettings = next.fontSettings
        ttsTextFilterRules = next.ttsTextFilterRules
        providerViewMode = next.providerViewMode
        onSaveAppearancePreferences(next)
    }
    var rpStyleRules by remember(state.appearance.rpStyleRules) {
        mutableStateOf(state.appearance.rpStyleRules)
    }
    var editingRpStyleRule by remember { mutableStateOf<IosRpStyleRule?>(null) }
    var showAddRpStyleRuleDialog by remember { mutableStateOf(false) }
    var assistantName by remember(state.assistant.name) { mutableStateOf(state.assistant.name) }
    var systemPrompt by remember(state.assistant.systemPrompt) { mutableStateOf(state.assistant.systemPrompt) }
    var enableSpontaneous by remember(state.assistant.id, state.assistant.enableSpontaneous) {
        mutableStateOf(state.assistant.enableSpontaneous)
    }
    var spontaneousStartHour by remember(state.assistant.id, state.assistant.notificationStartHour) {
        mutableStateOf(state.assistant.notificationStartHour.toString())
    }
    var spontaneousEndHour by remember(state.assistant.id, state.assistant.notificationEndHour) {
        mutableStateOf(state.assistant.notificationEndHour.toString())
    }
    var spontaneousFrequency by remember(state.assistant.id, state.assistant.notificationFrequencyHours) {
        mutableStateOf(state.assistant.notificationFrequencyHours.coerceIn(1, 24).toFloat())
    }
    var spontaneousPrompt by remember(state.assistant.id, state.assistant.spontaneousPrompt) {
        mutableStateOf(state.assistant.spontaneousPrompt)
    }
    var comfyWorkflowJson by remember { mutableStateOf("") }
    var comfyPromptNodeId by remember { mutableStateOf("") }
    var comfyPromptInputName by remember { mutableStateOf("text") }
    var comfyModelNodeId by remember { mutableStateOf("") }
    var comfyModelInputName by remember { mutableStateOf("ckpt_name") }
    var comfyShowAdvanced by remember { mutableStateOf(false) }
    var memoryMode by remember(state.assistant.memoryMode) { mutableStateOf(state.assistant.memoryMode) }
    var embeddingProviderId by remember(state.assistant.embeddingProviderId) {
        mutableStateOf(state.assistant.embeddingProviderId)
    }
    var embeddingModelId by remember(state.assistant.embeddingModelId) {
        mutableStateOf(state.assistant.embeddingModelId)
    }
    var memoryThreshold by remember(state.assistant.ragSimilarityThreshold) {
        mutableStateOf(state.assistant.ragSimilarityThreshold.toString())
    }
    var memoryLimit by remember(state.assistant.ragLimit) {
        mutableStateOf(state.assistant.ragLimit.toString())
    }
    var localTools by remember(state.assistant.localTools) {
        mutableStateOf(state.assistant.localTools)
    }
    var memorySearch by remember { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<IosMemoryRecord?>(null) }
    var editingMemoryContent by remember { mutableStateOf("") }
    var pendingBackupRestorePath by remember { mutableStateOf<String?>(null) }
    var backupRestoreReport by remember { mutableStateOf<IosBackupImportReport?>(null) }
    var backupRestoreError by remember { mutableStateOf<String?>(null) }
    var restoringBackup by remember { mutableStateOf(false) }
    fun openSettingsDestination(destinationId: String, title: String) {
        activeDestinationId = destinationId
        when (destinationId) {
            "Display", "Fonts", "UiCustomization", "RpOptimizations" ->
                section = IosSettingsSection.Appearance
            "Assistants" -> section = IosSettingsSection.Assistant
            "AssistantMemory" -> section = IosSettingsSection.Memory
            "AssistantTools" -> section = IosSettingsSection.Tools
            "Providers", "ProviderModels" -> section = IosSettingsSection.Provider
            "Models" -> section = IosSettingsSection.Models
            "Search" -> section = IosSettingsSection.Search
            "Tts" -> section = IosSettingsSection.Tts
            "SpeechToText" -> section = IosSettingsSection.Speech
            "PromptInjections", "Skills" -> section = IosSettingsSection.Skills
            "Lorebooks" -> section = IosSettingsSection.Lorebooks
            "Mcp" -> section = IosSettingsSection.Mcp
            "Web" -> section = IosSettingsSection.Web
            "Workspaces" -> section = IosSettingsSection.Workspaces
            "AndroidIntegration" -> section = IosSettingsSection.AndroidIntegration
            "ChatStorage" -> section = IosSettingsSection.Data
            "Backup", "BackupLocal" -> section = IosSettingsSection.Backup
            "BackupWebDav" -> section = IosSettingsSection.BackupWebDav
            "About" -> section = IosSettingsSection.About
            "Developer" -> section = IosSettingsSection.Developer
            else -> {
                unavailableDestinationId = destinationId
                unavailableDestinationTitle = title
                section = IosSettingsSection.Unavailable
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useWideLayout = maxWidth >= 840.dp && maxHeight >= 600.dp &&
            section != IosSettingsSection.Home
        val selectedPaneId = when (section) {
            IosSettingsSection.Appearance -> activeDestinationId.ifBlank { "Display" }
            IosSettingsSection.Assistant -> activeDestinationId.ifBlank { "Assistants" }
            IosSettingsSection.Memory -> "AssistantMemory"
            IosSettingsSection.Tools -> "AssistantTools"
            IosSettingsSection.Provider -> activeDestinationId.ifBlank { "Providers" }
            IosSettingsSection.Models -> "Models"
            IosSettingsSection.Search -> "Search"
            IosSettingsSection.Tts -> "Tts"
            IosSettingsSection.Speech -> "SpeechToText"
            IosSettingsSection.Skills -> "Skills"
            IosSettingsSection.Lorebooks -> "Lorebooks"
            IosSettingsSection.Mcp -> "Mcp"
            IosSettingsSection.Web -> "Web"
            IosSettingsSection.Workspaces -> "Workspaces"
            IosSettingsSection.AndroidIntegration -> "AndroidIntegration"
            IosSettingsSection.Data -> activeDestinationId.ifBlank { "ChatStorage" }
            IosSettingsSection.Backup -> activeDestinationId.ifBlank { "Backup" }
            IosSettingsSection.BackupWebDav -> "BackupWebDav"
            IosSettingsSection.About -> "About"
            IosSettingsSection.Developer -> "Developer"
            IosSettingsSection.Unavailable -> unavailableDestinationId
            IosSettingsSection.Home -> ""
        }
        val selectedMainId = iosSettingsMainDestination(selectedPaneId)
        val paneGroups = remember(state.appearance.developerMode) {
            iosSettingsPaneGroups(developerMode = state.appearance.developerMode)
        }
        Row(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            if (useWideLayout) {
                LastChatSettingsNavigationPane(
                    groups = paneGroups,
                    selectedId = selectedPaneId,
                    selectedMainId = selectedMainId,
                    title = "Settings",
                    onBack = onBack,
                    onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                    onNavigate = { destinationId ->
                        openSettingsDestination(
                            destinationId = destinationId,
                            title = paneGroups.titleFor(destinationId) ?: "Unavailable",
                        )
                    },
                )
            }
    Scaffold(
        modifier = (if (useWideLayout) {
            Modifier.weight(1f).fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        }).windowInsetsPadding(WindowInsets.safeContent),
        topBar = { TopAppBar(
            title = {
                Text(
                    if (section == IosSettingsSection.Unavailable) {
                        unavailableDestinationTitle
                    } else if (
                        section == IosSettingsSection.Appearance &&
                        activeDestinationId == "UiCustomization"
                    ) {
                        "UI customization"
                    } else if (
                        section == IosSettingsSection.Appearance &&
                        activeDestinationId == "Fonts"
                    ) {
                        "Fonts"
                    } else if (
                        section == IosSettingsSection.Appearance &&
                        activeDestinationId == "RpOptimizations"
                    ) {
                        "Roleplay optimizations"
                    } else {
                        section.title
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = {
                if (!useWideLayout) {
                    IosBackButton(platformHaptics) {
                        if (section == IosSettingsSection.Home) onBack()
                        else section = IosSettingsSection.Home
                    }
                }
            },
        ) },
        bottomBar = {
            val selectedProviderTab = when {
                section == IosSettingsSection.Provider -> "Models"
                section == IosSettingsSection.Search -> "Search"
                section == IosSettingsSection.Tts -> "Tts"
                else -> null
            }
            if (selectedProviderTab != null) {
                LastChatProvidersBottomBar(
                    tabs = listOf(
                        LastChatProviderTab("Models", Icons.Rounded.Cloud),
                        LastChatProviderTab("Search", Icons.Rounded.Public),
                        LastChatProviderTab("Tts", Icons.AutoMirrored.Rounded.VolumeUp),
                    ),
                    selectedId = selectedProviderTab,
                    useWideLayout = useWideLayout,
                    onHaptic = {
                        platformHaptics.perform(PlatformHapticPattern.Tick)
                    },
                    onSelect = { tab ->
                        when (tab) {
                            "Models" -> openSettingsDestination("Providers", "Providers")
                            "Search" -> openSettingsDestination("Search", "Search service")
                            "Tts" -> openSettingsDestination("Tts", "Text-to-speech")
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (section == IosSettingsSection.Home) Modifier
                    else Modifier.padding(16.dp)
                ),
            verticalArrangement = Arrangement.spacedBy(
                if (section == IosSettingsSection.Home) 0.dp else 10.dp
            ),
        ) {
            if (section == IosSettingsSection.Home) {
                item {
                    LastChatSettingsGroup(title = "General settings") {
                        LastChatSettingGroupItem(
                            title = "Display",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Display", "Display") },
                        )
                        LastChatSettingGroupItem(
                            title = "Assistant",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Group, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Assistants", "Assistant") },
                        )
                        LastChatSettingGroupItem(
                            title = "Prompt injections",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Category, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("PromptInjections", "Prompt injections") },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "Models & services") {
                        LastChatSettingGroupItem(
                            title = "Default model",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.AccountTree, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Models", "Default model") },
                        )
                        LastChatSettingGroupItem(
                            title = "Providers",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Cloud, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Providers", "Providers") },
                        )
                        LastChatSettingGroupItem(
                            title = "MCP",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Code, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Mcp", "MCP") },
                        )
                        LastChatSettingGroupItem(
                            title = "Web server",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Language, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Web", "Web server") },
                        )
                        LastChatSettingGroupItem(
                            title = "Android integration",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("AndroidIntegration", "Android integration") },
                        )
                        LastChatSettingGroupItem(
                            title = "Workspaces",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Code, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Workspaces", "Workspaces") },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "Data settings") {
                        LastChatSettingGroupItem(
                            title = "Backup",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.CloudUpload, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Backup", "Backup") },
                        )
                        LastChatSettingGroupItem(
                            title = "Chat storage",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Storage, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("ChatStorage", "Chat storage") },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "About") {
                        LastChatSettingGroupItem(
                            title = "About",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Info, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("About", "About") },
                        )
                        if (state.appearance.developerMode) {
                            LastChatSettingGroupItem(
                                title = "Developer",
                                darkTheme = darkTheme,
                                icon = { Icon(Icons.Rounded.Build, null, Modifier.size(20.dp)) },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                onClick = { openSettingsDestination("Developer", "Developer") },
                            )
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Assistant) {
                item {
                    LastChatSettingsGroup(
                        title = "Configuration",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = "Assistant",
                            subtitle = "Profile and system instructions",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(label = { Text("Profile") }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    state.assistants.forEach { assistant ->
                                        if (assistant.id == state.selectedAssistantId) {
                                            Button(onClick = {}) { Text(assistant.name) }
                                        } else {
                                            TextButton(onClick = { onSelectAssistant(assistant.id) }) {
                                                Text(assistant.name)
                                            }
                                        }
                                    }
                                    TextButton(onClick = onNewAssistant) { Text("New assistant") }
                                }
                            }
                            LastChatFormItem(label = { Text("Name") }) {
                                OutlinedTextField(
                                    value = assistantName,
                                    onValueChange = { assistantName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            LastChatFormItem(label = { Text("System prompt") }) {
                                OutlinedTextField(
                                    value = systemPrompt,
                                    onValueChange = { systemPrompt = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    minLines = 3,
                                    maxLines = 8,
                                )
                            }
                            Button(
                                onClick = { onSaveAssistant(assistantName, systemPrompt) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save assistant") }
                            LastChatFormItem(label = { Text("Avatar") }) {
                                IosAvatarPickerInline(
                                    nickname = assistantName,
                                    avatar = state.assistant.avatar,
                                    onSelectEmoji = { onSaveAssistantAvatar(IosAvatar.Emoji(it), null) },
                                    onSelectImage = {
                                        onPickAvatarFile { result ->
                                            val picked = result.getOrNull() ?: return@onPickAvatarFile
                                            if (picked.kind == PlatformPickedFileKind.Image) {
                                                onSaveAssistantAvatar(
                                                    IosAvatar.Image(picked.localUrl.ifBlank { picked.storagePath }),
                                                    null,
                                                )
                                            }
                                        }
                                    },
                                    onSelectDummy = { onSaveAssistantAvatar(IosAvatar.Dummy, null) },
                                )
                            }
                            LastChatFormItem(
                                label = { Text("Use assistant avatar in chat") },
                                description = { Text("Replace the model letter glyph with this assistant's avatar") },
                                tail = {
                                    Switch(
                                        checked = state.assistant.useAssistantAvatar,
                                        onCheckedChange = {
                                            onSaveAssistantAvatar(state.assistant.avatar, it)
                                        },
                                    )
                                },
                            )
                            LastChatSettingGroupInputItem(
                                title = "UI overrides",
                                subtitle = "Null means use the global DisplaySetting, matching Android AssistantUISettings",
                                darkTheme = darkTheme,
                            ) {
                                val ui = state.assistant.uiSettings
                                IosTriStateRow("User avatar", ui.showUserAvatar, showUserAvatar) {
                                    onSaveAssistantUiSettings(ui.copy(showUserAvatar = it))
                                }
                                IosTriStateRow("Character avatar", ui.showAssistantAvatar, showModelIcon) {
                                    onSaveAssistantUiSettings(ui.copy(showAssistantAvatar = it))
                                }
                                IosTriStateRow("Assistant bubbles", ui.showAssistantBubbles, showAssistantBubbles) {
                                    onSaveAssistantUiSettings(ui.copy(showAssistantBubbles = it))
                                }
                                IosTriStateRow("Token usage", ui.showTokenUsage, showTokenUsage) {
                                    onSaveAssistantUiSettings(ui.copy(showTokenUsage = it))
                                }
                                IosTriStateRow("Auto-collapse thinking", ui.autoCloseThinking, autoCloseThinking) {
                                    onSaveAssistantUiSettings(ui.copy(autoCloseThinking = it))
                                }
                                IosTriStateRow("Message jumper", ui.showMessageJumper, showMessageJumper) {
                                    onSaveAssistantUiSettings(ui.copy(showMessageJumper = it))
                                }
                                IosTriStateRow("Code wrap", ui.codeBlockAutoWrap, codeBlockAutoWrap) {
                                    onSaveAssistantUiSettings(ui.copy(codeBlockAutoWrap = it))
                                }
                                IosTriStateRow("Context stacks", ui.showContextStacks, showContextStacks) {
                                    onSaveAssistantUiSettings(ui.copy(showContextStacks = it))
                                }
                                Text("New chat header", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (listOf<IosNewChatHeaderStyle?>(null) + IosNewChatHeaderStyle.entries).forEach { style ->
                                        val label = style?.name ?: "Global"
                                        if (ui.newChatHeaderStyle == style) {
                                            Button(onClick = {}) { Text(label) }
                                        } else {
                                            TextButton(onClick = {
                                                onSaveAssistantUiSettings(ui.copy(newChatHeaderStyle = style))
                                            }) { Text(label) }
                                        }
                                    }
                                }
                                Text("New chat content", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (listOf<IosNewChatContentStyle?>(null) + IosNewChatContentStyle.entries).forEach { style ->
                                        val label = style?.name ?: "Global"
                                        if (ui.newChatContentStyle == style) {
                                            Button(onClick = {}) { Text(label) }
                                        } else {
                                            TextButton(onClick = {
                                                onSaveAssistantUiSettings(ui.copy(newChatContentStyle = style))
                                            }) { Text(label) }
                                        }
                                    }
                                }
                            }
                            LastChatFormItem(
                                label = { Text("Spontaneous messages") },
                                description = {
                                    Text("Lets this assistant send in-app follow-ups through the shared scheduler")
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Enable spontaneous messages")
                                    Switch(
                                        checked = enableSpontaneous,
                                        onCheckedChange = { enableSpontaneous = it },
                                    )
                                }
                            }
                            if (enableSpontaneous) {
                                LastChatFormItem(label = { Text("Active hours (0-23)") }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = spontaneousStartHour,
                                            onValueChange = { spontaneousStartHour = it.filter(Char::isDigit).take(2) },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("Start") },
                                        )
                                        OutlinedTextField(
                                            value = spontaneousEndHour,
                                            onValueChange = { spontaneousEndHour = it.filter(Char::isDigit).take(2) },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("End") },
                                        )
                                    }
                                }
                                LastChatFormItem(
                                    label = { Text("Minimum gap: ${spontaneousFrequency.toInt()}h") },
                                    description = { Text("Wait at least this many hours between messages") },
                                ) {
                                    Slider(
                                        value = spontaneousFrequency,
                                        onValueChange = { spontaneousFrequency = it },
                                        valueRange = 1f..24f,
                                        steps = 22,
                                    )
                                }
                                LastChatFormItem(label = { Text("Optional prompt") }) {
                                    OutlinedTextField(
                                        value = spontaneousPrompt,
                                        onValueChange = { spontaneousPrompt = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        minLines = 2,
                                        maxLines = 5,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveSpontaneousSettings(
                                        enableSpontaneous,
                                        spontaneousStartHour.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                                        spontaneousEndHour.toIntOrNull()?.coerceIn(0, 23) ?: 22,
                                        spontaneousFrequency.toInt().coerceIn(1, 24),
                                        spontaneousPrompt,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save spontaneous settings") }
                            TextButton(
                                onClick = { openSettingsDestination("AssistantMemory", "Memory") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp))
                                Text("Manage memory")
                            }
                            TextButton(
                                onClick = { openSettingsDestination("AssistantTools", "Tools") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Extension, null, Modifier.size(18.dp))
                                Text("Manage tools")
                            }
                            if (state.assistants.size > 1) {
                                TextButton(onClick = { onDeleteAssistant(state.assistant.id) }) {
                                    Text("Delete assistant")
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Memory) {
                item {
                    val modeTitle = when (memoryMode) {
                        IosMemoryMode.OFF -> "Memory: Off"
                        IosMemoryMode.BASIC -> "Memory: Basic"
                        IosMemoryMode.SEARCHABLE -> "Memory: Searchable"
                        IosMemoryMode.ADAPTIVE -> "Memory: Adaptive"
                    }
                    val modeDescription = when (memoryMode) {
                        IosMemoryMode.OFF -> "No memories are added to this assistant's context"
                        IosMemoryMode.BASIC -> "All core memories are included in the stable system prompt"
                        IosMemoryMode.SEARCHABLE -> "Relevant memories are retrieved with provider embeddings"
                        IosMemoryMode.ADAPTIVE -> "Searchable recall plus automatic evidence-bound episodic memory"
                    }
                    LastChatMemoryModeCard(
                        enabled = memoryMode != IosMemoryMode.OFF,
                        title = modeTitle,
                        description = modeDescription,
                        darkTheme = darkTheme,
                    )
                }
                item {
                    LastChatSettingsGroup(
                        title = "Memory settings",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatMemorySettingsItem(
                            title = "Memory mode",
                            subtitle = "Stored separately for ${state.assistant.name}",
                            darkTheme = darkTheme,
                            position = LastChatMemoryGroupPosition.Single,
                            trailing = {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    listOf(
                                        IosMemoryMode.OFF to "Off",
                                        IosMemoryMode.BASIC to "Basic",
                                        IosMemoryMode.SEARCHABLE to "Searchable",
                                        IosMemoryMode.ADAPTIVE to "Adaptive",
                                    ).forEach { (mode, label) ->
                                        if (memoryMode == mode) {
                                            Button(onClick = {}) { Text(label) }
                                        } else {
                                            TextButton(onClick = { memoryMode = mode }) { Text(label) }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                if (memoryMode == IosMemoryMode.SEARCHABLE || memoryMode == IosMemoryMode.ADAPTIVE) {
                    item {
                        LastChatSettingsGroup(
                            title = "Recall settings",
                            horizontalPadding = 0.dp,
                            titleStartPadding = 0.dp,
                        ) {
                            LastChatSettingGroupInputItem(
                                title = "Embeddings",
                                subtitle = "Vectors are stored with each memory in the iOS app container",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(label = { Text("Embedding provider") }) {
                                    val embeddableProviders = state.providers.filter { provider ->
                                        chatProviderType(provider) == IosProviderType.OPENAI ||
                                            chatProviderType(provider) == IosProviderType.GOOGLE
                                    }
                                    if (embeddableProviders.isEmpty()) {
                                        Text(
                                            "Add an OpenAI or Google provider in Settings → Providers first",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            embeddableProviders.forEach { provider ->
                                                val id = provider.id.toString()
                                                val selected = embeddingProviderId == id
                                                if (selected) {
                                                    Button(onClick = {}) { Text(provider.name) }
                                                } else {
                                                    TextButton(onClick = {
                                                        embeddingProviderId = id
                                                        embeddingModelId = if (
                                                            chatProviderType(provider) == IosProviderType.OPENAI
                                                        ) {
                                                            "text-embedding-3-small"
                                                        } else {
                                                            "text-embedding-004"
                                                        }
                                                    }) { Text(provider.name) }
                                                }
                                            }
                                        }
                                    }
                                }
                                LastChatFormItem(label = { Text("Embedding model") }) {
                                    OutlinedTextField(
                                        value = embeddingModelId,
                                        onValueChange = { embeddingModelId = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("Similarity threshold") },
                                    description = { Text("Between 0 and 1") },
                                ) {
                                    OutlinedTextField(
                                        value = memoryThreshold,
                                        onValueChange = { value ->
                                            memoryThreshold = value.filter { it.isDigit() || it == '.' }.take(4)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("Maximum recalled items") },
                                    description = { Text("Between 1 and 20") },
                                ) {
                                    OutlinedTextField(
                                        value = memoryLimit,
                                        onValueChange = { value -> memoryLimit = value.filter(Char::isDigit).take(2) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            onSaveMemorySettings(
                                memoryMode,
                                embeddingProviderId,
                                embeddingModelId,
                                memoryThreshold.toFloatOrNull() ?: 0.45f,
                                memoryLimit.toIntOrNull() ?: 10,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save memory settings") }
                }
                item {
                    val visibleMemories = state.assistantMemories
                        .filter { memorySearch.isBlank() || it.content.contains(memorySearch, ignoreCase = true) }
                        .sortedByDescending(IosMemoryRecord::timestampEpochMs)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Manage memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row {
                                if (memoryMode == IosMemoryMode.SEARCHABLE || memoryMode == IosMemoryMode.ADAPTIVE) {
                                    IconButton(onClick = onRegenerateMemoryEmbeddings) {
                                        Icon(Icons.Rounded.Refresh, "Regenerate embeddings")
                                    }
                                }
                                IconButton(onClick = {
                                    editingMemory = IosMemoryRecord(
                                        id = 0,
                                        assistantId = state.assistant.id,
                                        content = "",
                                    )
                                    editingMemoryContent = ""
                                }) {
                                    Icon(Icons.Rounded.Add, "Add memory")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = memorySearch,
                            onValueChange = { memorySearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.SearchField,
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            placeholder = { Text("Search memories") },
                            singleLine = true,
                        )
                        Column(
                            modifier = Modifier.clip(AppShapes.CardMedium),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            visibleMemories.forEachIndexed { index, memory ->
                                val position = when {
                                    visibleMemories.size == 1 -> LastChatMemoryGroupPosition.Single
                                    index == 0 -> LastChatMemoryGroupPosition.First
                                    index == visibleMemories.lastIndex -> LastChatMemoryGroupPosition.Last
                                    else -> LastChatMemoryGroupPosition.Middle
                                }
                                val searchableMemory = memoryMode == IosMemoryMode.SEARCHABLE ||
                                    memoryMode == IosMemoryMode.ADAPTIVE
                                val missingEmbedding = searchableMemory && memory.embeddings.isNullOrEmpty()
                                val outdatedEmbedding = searchableMemory &&
                                    !missingEmbedding && memory.embeddingModelId != embeddingModelId
                                LastChatMemoryRow(
                                    content = memory.content,
                                    darkTheme = darkTheme,
                                    position = position,
                                    onEdit = {
                                        editingMemory = memory
                                        editingMemoryContent = memory.content
                                    },
                                    onDelete = if (memory.type == 0) ({ onDeleteMemory(memory.id) }) else null,
                                    deleteTitle = "Delete",
                                    deleteLabel = "Delete",
                                    cancelLabel = "Cancel",
                                    deleteConfirmation = "Delete this memory?",
                                    embeddingWarning = when {
                                        missingEmbedding -> "No embedding"
                                        outdatedEmbedding -> "Outdated embedding"
                                        else -> null
                                    },
                                    embeddingWarningIsError = missingEmbedding,
                                    typeLabel = if (memory.type == 1) "Episodic" else "Core",
                                    typeIsCore = memory.type == 0,
                                    onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                )
                            }
                            if (visibleMemories.isEmpty()) {
                                Surface(
                                    color = if (darkTheme) MaterialTheme.colorScheme.surfaceContainerLow
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = AppShapes.CardMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (memorySearch.isBlank()) "No memories yet" else "No matching memories",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Tools) {
                item {
                    LastChatSettingsGroup(
                        title = "Local tools",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupItem(
                            title = "JavaScript",
                            subtitle = "Run calculations and data transforms in an isolated local JavaScript runtime",
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.JAVASCRIPT in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.JAVASCRIPT
                                        } else localTools - IosLocalToolOption.JAVASCRIPT
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                        LastChatSettingGroupItem(
                            title = "Notifications",
                            subtitle = "Allow this assistant to post LastChat notifications",
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.NOTIFICATIONS in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.NOTIFICATIONS
                                        } else localTools - IosLocalToolOption.NOTIFICATIONS
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                        LastChatSettingGroupItem(
                            title = "Text-to-speech",
                            subtitle = "Allow spoken output through the selected TTS provider",
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.TTS in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.TTS
                                        } else localTools - IosLocalToolOption.TTS
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                        LastChatSettingGroupItem(
                            title = "Character questions",
                            subtitle = "Allow structured interactive questions in the composer",
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.ASK_USER in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.ASK_USER
                                        } else localTools - IosLocalToolOption.ASK_USER
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                        LastChatSettingGroupItem(
                            title = "Image generation",
                            subtitle = "Allow the selected image model to create gallery images",
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.IMAGE_GENERATION in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.IMAGE_GENERATION
                                        } else localTools - IosLocalToolOption.IMAGE_GENERATION
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                        LastChatSettingGroupItem(
                            title = "Workspace",
                            subtitle = if (state.onDeviceWorkspaceAvailable) {
                                "Read, write, and shell tools in the on-device sandbox"
                            } else {
                                "Registered in the shared tool loop; currently unavailable on this device"
                            },
                            darkTheme = darkTheme,
                            trailing = {
                                Switch(
                                    checked = IosLocalToolOption.WORKSPACE in localTools,
                                    onCheckedChange = { enabled ->
                                        localTools = if (enabled) {
                                            localTools + IosLocalToolOption.WORKSPACE
                                        } else localTools - IosLocalToolOption.WORKSPACE
                                        onSaveLocalTools(localTools)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            if (section == IosSettingsSection.Models) {
                item {
                    LastChatSettingsGroup(
                        title = "Conversation",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        val selectedModel = state.selectedChatModel?.second
                        LastChatModelFeatureCard(
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) },
                            title = { Text("Default chat model", maxLines = 1) },
                            description = { Text("Model used for new conversations") },
                            actions = {
                                Box(Modifier.weight(1f)) {
                                    TextButton(onClick = { showModelPicker = true }) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    selectedModel?.modelId?.firstOrNull()?.uppercase() ?: "M",
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                        }
                                        Text(
                                            selectedModel?.displayName?.ifBlank { selectedModel.modelId } ?: "Select model",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                        )
                        LastChatModelFeatureCard(
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Mic, null) },
                            title = { Text("Speech-to-text", maxLines = 1) },
                            description = { Text("OpenAI-compatible transcriptions") },
                            actions = {
                                Box(Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = {
                                            openSettingsDestination("SpeechToText", "Speech-to-text")
                                        },
                                    ) {
                                        Text(
                                            if (state.stt.enabled) {
                                                state.stt.model.ifBlank { "Configured" }
                                            } else {
                                                "Configure"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                        )
                        LastChatSettingGroupInputItem(
                            title = "Speech composer",
                            subtitle = "Replace the model picker with STT when idle",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(
                                label = { Text("STT replaces model icon") },
                                description = { Text("Same sttReplaceModelIcon key Android uses on the Default model page") },
                                tail = {
                                    Switch(
                                        checked = sttReplaceModelIcon,
                                        onCheckedChange = { persistAppearance { copy(sttReplaceModelIcon = it) } },
                                    )
                                },
                            )
                        }
                        LastChatSettingGroupInputItem(
                            title = "Image generation model",
                            subtitle = if (imageGeneration.enabled) imageGeneration.modelId
                            else "Not configured",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(
                                label = { Text("Enable image generation") },
                                tail = {
                                    Switch(
                                        checked = imageGeneration.enabled,
                                        onCheckedChange = { enabled ->
                                            imageGeneration = imageGeneration.copy(enabled = enabled)
                                        },
                                    )
                                },
                            )
                            val imageProviders = state.providers.filter {
                                it is ProviderSetting.OpenAI || it is ProviderSetting.Google || it is ProviderSetting.ComfyUI
                            }
                            LastChatFormItem(
                                label = { Text("Provider") },
                                description = {
                                    val selectedProvider = imageProviders.firstOrNull { it.id.toString() == imageGeneration.providerId }
                                    Text(
                                        if (selectedProvider is ProviderSetting.ComfyUI) {
                                            "Runs the configured API workflow on your ComfyUI server"
                                        } else {
                                            "Uses the provider endpoint and Keychain key configured in Providers"
                                        }
                                    )
                                },
                            ) {
                                if (imageProviders.isEmpty()) {
                                    Text(
                                        "Add an OpenAI, Google, or ComfyUI provider in Settings → Providers first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        imageProviders.forEach { provider ->
                                            val id = provider.id.toString()
                                            val selected = imageGeneration.providerId == id
                                            if (selected) {
                                                Button(onClick = {}) { Text(provider.name) }
                                            } else {
                                                TextButton(onClick = {
                                                    val isComfy = provider is ProviderSetting.ComfyUI
                                                    val isGoogle = provider is ProviderSetting.Google
                                                    imageGeneration = imageGeneration.copy(
                                                        providerId = id,
                                                        modelId = when {
                                                            isComfy -> "model.safetensors"
                                                            isGoogle -> "imagen-3.0-generate-002"
                                                            else -> "gpt-image-1"
                                                        },
                                                        method = if (isComfy) ImageGenerationMethod.DIFFUSION else imageGeneration.method,
                                                    )
                                                }) { Text(provider.name) }
                                            }
                                        }
                                    }
                                }
                            }
                            val selectedProvider = imageProviders.firstOrNull { it.id.toString() == imageGeneration.providerId }
                            if (selectedProvider !is ProviderSetting.ComfyUI) {
                                LastChatFormItem(
                                    label = { Text("Generation method") },
                                    description = {
                                        Text(
                                            if (imageGeneration.method == ImageGenerationMethod.DIFFUSION) {
                                                "Use the provider's dedicated image generation endpoint"
                                            } else {
                                                "Use a chat model that returns images, with optional image-to-image input"
                                            }
                                        )
                                    },
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ImageGenerationMethod.entries.forEach { method ->
                                            if (imageGeneration.method == method) {
                                                Button(onClick = {}) {
                                                    Text(method.displayName())
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    val isGoogle = selectedProvider is ProviderSetting.Google
                                                    imageGeneration = imageGeneration.copy(
                                                        method = method,
                                                        modelId = when {
                                                            method == ImageGenerationMethod.MULTIMODAL && isGoogle ->
                                                                "gemini-2.0-flash-preview-image-generation"
                                                            method == ImageGenerationMethod.MULTIMODAL -> "gpt-4o"
                                                            isGoogle -> "imagen-3.0-generate-002"
                                                            else -> "gpt-image-1"
                                                        },
                                                    )
                                                }) {
                                                    Text(method.displayName())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LastChatFormItem(label = { Text("Model ID") }) {
                                OutlinedTextField(
                                    value = imageGeneration.modelId,
                                    onValueChange = { imageGeneration = imageGeneration.copy(modelId = it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            Button(
                                onClick = { onSaveImageGeneration(imageGeneration) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save image model") }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Provider) {
                val editingProvider = state.providers.firstOrNull {
                    it.id.toString() == selectedProviderId
                }
                if (editingProvider == null) {
                    item {
                        LastChatSettingsGroup(
                            title = "Providers",
                            horizontalPadding = 0.dp,
                            titleStartPadding = 0.dp,
                        ) {
                            LastChatFormItem(
                                label = { Text("View") },
                                tail = {
                                    IconButton(
                                        onClick = {
                                            val next = if (providerViewMode == IosProviderViewMode.LIST) {
                                                IosProviderViewMode.GRID
                                            } else {
                                                IosProviderViewMode.LIST
                                            }
                                            persistAppearance { copy(providerViewMode = next) }
                                        },
                                    ) {
                                        Icon(
                                            if (providerViewMode == IosProviderViewMode.LIST) {
                                                Icons.Rounded.GridView
                                            } else {
                                                Icons.AutoMirrored.Rounded.ViewList
                                            },
                                            contentDescription = "Toggle provider view",
                                        )
                                    }
                                },
                            )
                            val openProviderEditor: (ProviderSetting) -> Unit = { provider ->
                                selectedProviderId = provider.id.toString()
                                providerName = provider.name
                                when (provider) {
                                    is ProviderSetting.OpenAI -> providerBaseUrl = provider.baseUrl
                                    is ProviderSetting.Google -> providerBaseUrl = provider.baseUrl
                                    is ProviderSetting.Claude -> providerBaseUrl = provider.baseUrl
                                    is ProviderSetting.ComfyUI -> {
                                        providerBaseUrl = provider.baseUrl
                                        comfyWorkflowJson = provider.workflowJson
                                        comfyPromptNodeId = provider.promptNodeId
                                        comfyPromptInputName = provider.promptInputName.ifBlank { "text" }
                                        comfyModelNodeId = provider.modelNodeId
                                        comfyModelInputName = provider.modelInputName.ifBlank { "ckpt_name" }
                                        comfyShowAdvanced = provider.promptNodeId.isNotBlank() ||
                                            provider.modelNodeId.isNotBlank()
                                    }
                                    else -> providerBaseUrl = ""
                                }
                                providerApiKey = ""
                                newModelId = ""
                            }
                            if (providerViewMode == IosProviderViewMode.GRID) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.providers.forEach { provider ->
                                        Surface(
                                            onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                openProviderEditor(provider)
                                            },
                                            shape = AppShapes.CardSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.width(148.dp).height(96.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(Icons.Rounded.Cloud, null)
                                                Text(
                                                    provider.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                state.providers.forEach { provider ->
                                    LastChatSettingGroupItem(
                                        title = provider.name,
                                        darkTheme = darkTheme,
                                        icon = { Icon(Icons.Rounded.Cloud, null, Modifier.size(20.dp)) },
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                        onClick = { openProviderEditor(provider) },
                                    )
                                }
                            }
                            LastChatSettingGroupItem(
                                title = "Add provider",
                                darkTheme = darkTheme,
                                icon = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)) },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                onClick = {
                                    newProviderName = ""
                                    newProviderType = IosProviderType.OPENAI
                                    showAddProvider = true
                                },
                            )
                        }
                    }
                } else {
                    item {
                        LastChatSettingsGroup(
                            title = "Provider settings",
                            horizontalPadding = 0.dp,
                            titleStartPadding = 0.dp,
                        ) {
                            LastChatSettingGroupInputItem(
                                title = editingProvider.name,
                                subtitle = "Endpoint and credentials for this provider",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(label = { Text("Name") }) {
                                    OutlinedTextField(
                                        value = providerName,
                                        onValueChange = { providerName = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                if (editingProvider is ProviderSetting.ComfyUI) {
                                    LastChatFormItem(label = { Text("Server URL") }) {
                                        OutlinedTextField(
                                            value = providerBaseUrl,
                                            onValueChange = { providerBaseUrl = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                    }
                                    Text(
                                        if (comfyWorkflowJson.isBlank()) {
                                            "Workflow missing — import an API-format ComfyUI workflow JSON"
                                        } else {
                                            "Workflow ready (${comfyWorkflowJson.length} characters)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (comfyWorkflowJson.isBlank()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                    Button(
                                        onClick = {
                                            onPickBackupFile { result ->
                                                val picked = result.getOrNull() ?: return@onPickBackupFile
                                                onImportComfyUiWorkflow(
                                                    editingProvider.id.toString(),
                                                    picked.storagePath,
                                                )
                                                comfyWorkflowJson = "imported"
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Import workflow JSON") }
                                    LastChatFormItem(
                                        label = { Text("Workflow JSON") },
                                        description = { Text("Paste API-format workflow JSON if you are not importing a file") },
                                    ) {
                                        OutlinedTextField(
                                            value = if (comfyWorkflowJson == "imported") "" else comfyWorkflowJson,
                                            onValueChange = { comfyWorkflowJson = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            minLines = 3,
                                            maxLines = 8,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Advanced node mapping")
                                            Text(
                                                "Override prompt and checkpoint node IDs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = comfyShowAdvanced,
                                            onCheckedChange = { comfyShowAdvanced = it },
                                        )
                                    }
                                    if (comfyShowAdvanced) {
                                        OutlinedTextField(
                                            value = comfyPromptNodeId,
                                            onValueChange = { comfyPromptNodeId = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("Prompt node ID") },
                                        )
                                        OutlinedTextField(
                                            value = comfyPromptInputName,
                                            onValueChange = { comfyPromptInputName = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("Prompt input name") },
                                        )
                                        OutlinedTextField(
                                            value = comfyModelNodeId,
                                            onValueChange = { comfyModelNodeId = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("Model node ID") },
                                        )
                                        OutlinedTextField(
                                            value = comfyModelInputName,
                                            onValueChange = { comfyModelInputName = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                            label = { Text("Model input name") },
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onSaveComfyUiProvider(
                                                editingProvider.id.toString(),
                                                providerName,
                                                providerBaseUrl,
                                                if (comfyWorkflowJson == "imported") {
                                                    (editingProvider as ProviderSetting.ComfyUI).workflowJson
                                                } else {
                                                    comfyWorkflowJson
                                                },
                                                comfyPromptNodeId,
                                                comfyPromptInputName,
                                                comfyModelNodeId,
                                                comfyModelInputName,
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Save ComfyUI workflow") }
                                }
                                if (editingProvider !is ProviderSetting.ComfyUI &&
                                    editingProvider !is ProviderSetting.LiteRtLocal
                                ) {
                                    LastChatFormItem(label = { Text("Base URL") }) {
                                        OutlinedTextField(
                                            value = providerBaseUrl,
                                            onValueChange = { providerBaseUrl = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                    }
                                    LastChatFormItem(
                                        label = { Text("API key (saved in Keychain)") },
                                        description = { Text("Leave blank to keep the current key") },
                                    ) {
                                        OutlinedTextField(
                                            value = providerApiKey,
                                            onValueChange = { providerApiKey = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        onSaveProvider(
                                            editingProvider.id.toString(),
                                            providerName,
                                            providerBaseUrl,
                                            providerApiKey,
                                        )
                                        providerApiKey = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Save provider") }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    TextButton(onClick = { selectedProviderId = null }) {
                                        Text("Back to providers")
                                    }
                                    TextButton(
                                        onClick = {
                                            val idToDelete = editingProvider.id.toString()
                                            selectedProviderId = null
                                            onDeleteProvider(idToDelete)
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Text("Delete provider")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        LastChatSettingsGroup(
                            title = "Models",
                            horizontalPadding = 0.dp,
                            titleStartPadding = 0.dp,
                        ) {
                            LastChatSettingGroupInputItem(
                                title = "Configured models",
                                subtitle = "Models available from this provider",
                                darkTheme = darkTheme,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (editingProvider.models.isEmpty()) {
                                        Text(
                                            "No models configured for this provider yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        editingProvider.models.forEach { model ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        model.displayName.ifBlank { model.modelId },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    if (model.displayName.isNotBlank() && model.displayName != model.modelId) {
                                                        Text(
                                                            model.modelId,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onRemoveModel(editingProvider.id.toString(), model.id.toString())
                                                    },
                                                ) {
                                                    Icon(Icons.Rounded.Delete, "Remove model")
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = newModelId,
                                            onValueChange = { newModelId = it },
                                            placeholder = { Text("Model ID (e.g. gpt-4o)") },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                        IconButton(
                                            onClick = {
                                                if (newModelId.isNotBlank()) {
                                                    onAddModel(editingProvider.id.toString(), newModelId.trim())
                                                    newModelId = ""
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Rounded.Add, "Add model")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Search) {
                item {
                    LastChatSettingsGroup(
                        title = "Configuration",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = searchProvider.displayName(),
                            subtitle = "Web search tool available to chat models",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(
                                label = { Text("Enable web search") },
                                description = {
                                    Text("The model decides when current information is needed")
                                },
                                tail = {
                                    Switch(
                                        checked = searchEnabled,
                                        onCheckedChange = { searchEnabled = it },
                                    )
                                },
                            )
                            LastChatFormItem(label = { Text("Search provider") }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IosSearchProviderType.entries.forEach { type ->
                                        if (type == searchProvider) {
                                            Button(onClick = {}) { Text(type.displayName()) }
                                        } else {
                                            TextButton(onClick = {
                                                searchProvider = type
                                                searchApiKey = ""
                                            }) { Text(type.displayName()) }
                                        }
                                    }
                                }
                            }
                            LastChatFormItem(
                                label = { Text("Result count") },
                                description = { Text("Between 1 and 10 results") },
                            ) {
                                OutlinedTextField(
                                    value = searchResultSize,
                                    onValueChange = { value ->
                                        searchResultSize = value.filter(Char::isDigit).take(2)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            if (searchProvider == IosSearchProviderType.SEARXNG) {
                                LastChatFormItem(label = { Text("SearXNG URL") }) {
                                    OutlinedTextField(
                                        value = searxngUrl,
                                        onValueChange = { searxngUrl = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(label = { Text("Engines") }) {
                                    OutlinedTextField(
                                        value = searxngEngines,
                                        onValueChange = { searxngEngines = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(label = { Text("Language") }) {
                                    OutlinedTextField(
                                        value = searxngLanguage,
                                        onValueChange = { searxngLanguage = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(label = { Text("Username") }) {
                                    OutlinedTextField(
                                        value = searxngUsername,
                                        onValueChange = { searxngUsername = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(label = { Text("Password (Keychain)") }) {
                                    OutlinedTextField(
                                        value = searxngPassword,
                                        onValueChange = { searxngPassword = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (searchProvider != IosSearchProviderType.BING &&
                                searchProvider != IosSearchProviderType.SEARXNG &&
                                searchProvider != IosSearchProviderType.KEYLESS
                            ) {
                                LastChatFormItem(
                                    label = {
                                        Text(
                                            if (state.hasSearchApiKey &&
                                                searchProvider == state.search.provider
                                            ) {
                                                "API key (saved in Keychain)"
                                            } else {
                                                "API key"
                                            },
                                        )
                                    },
                                    description = {
                                        if (state.hasSearchApiKey &&
                                            searchProvider == state.search.provider
                                        ) {
                                            Text("Leave blank to keep the current key")
                                        }
                                    },
                                ) {
                                    OutlinedTextField(
                                        value = searchApiKey,
                                        onValueChange = { searchApiKey = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveSearch(
                                        searchProvider,
                                        searchEnabled,
                                        searchResultSize.toIntOrNull() ?: 5,
                                        searchApiKey,
                                        searxngUrl,
                                        searxngEngines,
                                        searxngLanguage,
                                        searxngUsername,
                                        searxngPassword,
                                    )
                                    searchApiKey = ""
                                    searxngPassword = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save search settings") }
                            if (searchProvider != IosSearchProviderType.BING &&
                                state.hasSearchApiKey && searchProvider == state.search.provider
                            ) {
                                TextButton(onClick = onClearSearchApiKey) {
                                    Text("Remove saved API key")
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Tts) {
                item {
                    LastChatSettingsGroup(
                        title = "Configuration",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = ttsPreferences.type.displayName(),
                            subtitle = "Cloud speech synthesis and native playback",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(
                                label = { Text("Enable text-to-speech") },
                                description = { Text("Adds Android's speech action to assistant messages") },
                                tail = {
                                    Switch(
                                        checked = ttsPreferences.enabled,
                                        onCheckedChange = { enabled ->
                                            ttsPreferences = ttsPreferences.copy(enabled = enabled)
                                        },
                                    )
                                },
                            )
                            LastChatFormItem(label = { Text("TTS provider") }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IosTtsProviderType.entries.forEach { type ->
                                        if (type == ttsPreferences.type) {
                                            Button(onClick = {}) { Text(type.displayName()) }
                                        } else {
                                            TextButton(onClick = {
                                                val enabled = ttsPreferences.enabled
                                                ttsPreferences = type.defaultPreferences().copy(
                                                    enabled = enabled,
                                                )
                                                ttsSpeed = ttsPreferences.speed.toString()
                                                ttsApiKey = ""
                                            }) { Text(type.displayName()) }
                                        }
                                    }
                                }
                            }
                            if (ttsPreferences.type != IosTtsProviderType.ELEVENLABS &&
                                ttsPreferences.type != IosTtsProviderType.SYSTEM
                            ) {
                                LastChatFormItem(label = { Text("Base URL") }) {
                                    OutlinedTextField(
                                        value = ttsPreferences.baseUrl,
                                        onValueChange = { value ->
                                            ttsPreferences = ttsPreferences.copy(baseUrl = value)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            LastChatFormItem(
                                label = {
                                    Text(
                                        if (ttsPreferences.type == IosTtsProviderType.PLAY_HT) {
                                            "Voice engine"
                                        } else {
                                            "Model"
                                        }
                                    )
                                },
                            ) {
                                OutlinedTextField(
                                    value = ttsPreferences.model,
                                    onValueChange = { value ->
                                        ttsPreferences = ttsPreferences.copy(model = value)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            LastChatFormItem(
                                label = {
                                    Text(
                                        when (ttsPreferences.type) {
                                            IosTtsProviderType.FISH_AUDIO -> "Reference ID"
                                            IosTtsProviderType.PLAY_HT -> "Voice manifest URI"
                                            else -> "Voice"
                                        }
                                    )
                                },
                            ) {
                                OutlinedTextField(
                                    value = ttsPreferences.voice,
                                    onValueChange = { value ->
                                        ttsPreferences = ttsPreferences.copy(voice = value)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            if (ttsPreferences.type == IosTtsProviderType.PLAY_HT) {
                                LastChatFormItem(label = { Text("User ID") }) {
                                    OutlinedTextField(
                                        value = ttsPreferences.secondary,
                                        onValueChange = { value ->
                                            ttsPreferences = ttsPreferences.copy(secondary = value)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (ttsPreferences.type in setOf(
                                    IosTtsProviderType.QWEN,
                                    IosTtsProviderType.FISH_AUDIO,
                                    IosTtsProviderType.CARTESIA,
                                )
                            ) {
                                LastChatFormItem(
                                    label = {
                                        Text(
                                            if (ttsPreferences.type == IosTtsProviderType.FISH_AUDIO) {
                                                "Audio format"
                                            } else {
                                                "Language"
                                            }
                                        )
                                    },
                                ) {
                                    OutlinedTextField(
                                        value = ttsPreferences.language,
                                        onValueChange = { value ->
                                            ttsPreferences = ttsPreferences.copy(language = value)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (ttsPreferences.type == IosTtsProviderType.MINIMAX ||
                                ttsPreferences.type == IosTtsProviderType.CARTESIA
                            ) {
                                LastChatFormItem(label = { Text("Emotion") }) {
                                    OutlinedTextField(
                                        value = ttsPreferences.emotion,
                                        onValueChange = { value ->
                                            ttsPreferences = ttsPreferences.copy(emotion = value)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (ttsPreferences.type in setOf(
                                    IosTtsProviderType.MINIMAX,
                                    IosTtsProviderType.FISH_AUDIO,
                                    IosTtsProviderType.CARTESIA,
                                    IosTtsProviderType.PLAY_HT,
                                )
                            ) {
                                LastChatFormItem(
                                    label = { Text("Speed") },
                                    description = { Text("Between 0.5 and 2.0") },
                                ) {
                                    OutlinedTextField(
                                        value = ttsSpeed,
                                        onValueChange = { value ->
                                            ttsSpeed = value.filter { it.isDigit() || it == '.' }.take(4)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (ttsPreferences.type == IosTtsProviderType.SYSTEM) {
                                LastChatFormItem(
                                    label = { Text("Speed") },
                                    description = { Text("Between 0.5 and 2.0") },
                                ) {
                                    OutlinedTextField(
                                        value = ttsSpeed,
                                        onValueChange = { value ->
                                            ttsSpeed = value.filter { it.isDigit() || it == '.' }.take(4)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("Pitch") },
                                    description = { Text("Between 0.5 and 2.0") },
                                ) {
                                    OutlinedTextField(
                                        value = ttsPreferences.pitch.toString(),
                                        onValueChange = { value ->
                                            val pitch = value.filter { it.isDigit() || it == '.' }.toFloatOrNull()
                                            if (pitch != null) {
                                                ttsPreferences = ttsPreferences.copy(pitch = pitch)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                            }
                            if (ttsPreferences.type != IosTtsProviderType.SYSTEM) {
                            LastChatFormItem(
                                label = {
                                    Text(
                                        if (state.hasTtsApiKey &&
                                            ttsPreferences.type == state.tts.type
                                        ) {
                                            "API key (saved in Keychain)"
                                        } else {
                                            "API key"
                                        }
                                    )
                                },
                                description = {
                                    if (state.hasTtsApiKey &&
                                        ttsPreferences.type == state.tts.type
                                    ) {
                                        Text("Leave blank to keep the current key")
                                    }
                                },
                            ) {
                                OutlinedTextField(
                                    value = ttsApiKey,
                                    onValueChange = { ttsApiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            }
                            Button(
                                onClick = {
                                    onSaveTts(
                                        ttsPreferences.copy(
                                            speed = ttsSpeed.toFloatOrNull() ?: 1.0f,
                                        ),
                                        ttsApiKey,
                                    )
                                    ttsApiKey = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save TTS provider") }
                            if (state.hasTtsApiKey && ttsPreferences.type == state.tts.type) {
                                TextButton(onClick = onClearTtsApiKey) {
                                    Text("Remove saved API key")
                                }
                            }
                            LastChatFormItem(
                                label = { Text("Playback filters") },
                                description = {
                                    Text("SKIP removes paired text; ONLY_READ speaks only captures. Applied before markdown stripping, same as Android.")
                                },
                            ) {
                                Button(
                                    onClick = { showAddTtsFilter = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Add filter")
                                }
                            }
                            ttsTextFilterRules.forEach { rule ->
                                LastChatSettingGroupInputItem(
                                    title = "${rule.mode.name}: ${rule.pattern}",
                                    subtitle = if (rule.enabled) "Enabled" else "Disabled",
                                    darkTheme = darkTheme,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(onClick = { editingTtsFilter = rule }) { Text("Edit") }
                                        IconButton(
                                            onClick = {
                                                persistAppearance {
                                                    copy(ttsTextFilterRules = ttsTextFilterRules.filterNot { it.id == rule.id })
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                        }
                                        Switch(
                                            checked = rule.enabled,
                                            onCheckedChange = { enabled ->
                                                persistAppearance {
                                                    copy(
                                                        ttsTextFilterRules = ttsTextFilterRules.map {
                                                            if (it.id == rule.id) it.copy(enabled = enabled) else it
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    LastChatSettingGroupItem(
                        title = "Speech-to-text",
                        subtitle = "OpenAI-compatible transcriptions",
                        darkTheme = darkTheme,
                        icon = { Icon(Icons.Rounded.Mic, null, Modifier.size(20.dp)) },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        onClick = { openSettingsDestination("SpeechToText", "Speech-to-text") },
                    )
                }
            }
            if (section == IosSettingsSection.Appearance) {
                item {
                    LastChatSettingsGroup(
                        title = "Configuration",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        if (activeDestinationId == "Fonts") {
                            LastChatSettingGroupInputItem(
                                title = "General",
                                subtitle = "Choose the app-wide typeface",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(
                                    label = { Text("Use phone system font") },
                                    description = {
                                        Text("Use the native iOS system font throughout LastChat")
                                    },
                                    tail = {
                                        Switch(
                                            checked = usePhoneSystemFont,
                                            onCheckedChange = { enabled ->
                                                usePhoneSystemFont = enabled
                                                persistAppearance {
                                                    copy(
                                                        usePhoneSystemFont = enabled,
                                                        fontSettings = fontSettings.copy(
                                                            usePhoneSystemFont = enabled,
                                                        ).normalize(),
                                                    )
                                                }
                                                onSaveFontSettings(enabled)
                                            },
                                        )
                                    },
                                )
                            }
                            LastChatSettingGroupInputItem(
                                title = "Content font",
                                subtitle = "Weight, size, line height, and tracking. Header uses the same values as Android.",
                                darkTheme = darkTheme,
                            ) {
                                val header = fontSettings.headerFont
                                Text("Weight ${header.weight.toInt()}")
                                Slider(
                                    value = header.weight,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(weight = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = 100f..900f,
                                )
                                Text("Size ×${iosFormatDecimal(header.fontSize)}")
                                Slider(
                                    value = header.fontSize,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(fontSize = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = 0.5f..2f,
                                )
                                Text("Line height ×${iosFormatDecimal(header.lineHeight)}")
                                Slider(
                                    value = header.lineHeight,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(lineHeight = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = 0.75f..2f,
                                )
                                Text("Letter spacing ${iosFormatDecimal(header.letterSpacing, 3)}")
                                Slider(
                                    value = header.letterSpacing,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(letterSpacing = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = -0.05f..0.1f,
                                )
                                Text("Width ${header.width.toInt()}")
                                Slider(
                                    value = header.width,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(width = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = 75f..125f,
                                )
                                Text("Roundness ${header.roundness.toInt()}")
                                Slider(
                                    value = header.roundness,
                                    onValueChange = { value ->
                                        persistAppearance {
                                            copy(
                                                fontSettings = fontSettings.copy(
                                                    headerFont = header.copy(roundness = value),
                                                ).normalize(),
                                            )
                                        }
                                    },
                                    valueRange = 0f..100f,
                                )
                                Text(
                                    "Width and roundness persist for Android backup parity. Compose Multiplatform applies weight, size, line height, and tracking.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LastChatSettingGroupInputItem(
                                title = "App font",
                                subtitle = if (usePhoneSystemFont) {
                                    "iOS system font"
                                } else {
                                    "Google Sans Flex · Material 3 Expressive"
                                },
                                darkTheme = darkTheme,
                            ) {
                                Text(
                                    "LastChat",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Text(
                                    "The quick brown fox jumps over the lazy dog.",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "0123456789  !?  Aa Bb Cc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LastChatSettingGroupInputItem(
                                title = "Code blocks",
                                subtitle = if (fontSettings.codeFont.fontSource == IosFontSource.SYSTEM_CODE) {
                                    "Native monospace font"
                                } else {
                                    "System font"
                                },
                                darkTheme = darkTheme,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        IosFontSource.SYSTEM_CODE to "Mono",
                                        IosFontSource.SYSTEM to "System",
                                    ).forEach { (source, label) ->
                                        if (fontSettings.codeFont.fontSource == source) {
                                            Button(onClick = {}) { Text(label) }
                                        } else {
                                            TextButton(onClick = {
                                                persistAppearance {
                                                    copy(
                                                        fontSettings = fontSettings.copy(
                                                            codeFont = fontSettings.codeFont.copy(
                                                                fontSource = source,
                                                            ),
                                                        ),
                                                    )
                                                }
                                            }) { Text(label) }
                                        }
                                    }
                                }
                                Text(
                                    "fun main() = println(\"LastChat\")",
                                    fontFamily = iosCodeFontFamily(fontSettings),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else if (activeDestinationId == "RpOptimizations") {
                            LastChatSettingGroupInputItem(
                                title = "Custom text styling",
                                subtitle = "Color roleplay and Markdown patterns in chat",
                                darkTheme = darkTheme,
                            ) {
                                Text(
                                    "Supported: * italic, ** bold, ~~ strikethrough, ` inline code, " +
                                        "# through ###### headings, > blockquotes, and custom paired delimiters.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { showAddRpStyleRuleDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Add rule")
                                }
                            }
                            if (rpStyleRules.isEmpty()) {
                                LastChatSettingGroupInputItem(
                                    title = "No style rules",
                                    subtitle = "Add a rule to color matching chat text",
                                    darkTheme = darkTheme,
                                ) {}
                            } else {
                                rpStyleRules.forEach { rule ->
                                    val previewColor =
                                        iosColorFromHex(rule.colorHex)
                                            ?: MaterialTheme.colorScheme.onSurface
                                    LastChatSettingGroupInputItem(
                                        title = "Pattern ${rule.pattern}",
                                        subtitle = rule.colorHex,
                                        darkTheme = darkTheme,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { editingRpStyleRule = rule },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(previewColor)
                                            )
                                            Text(
                                                "${rule.pattern}Example text${rule.pattern}",
                                                color = previewColor,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = {
                                                    rpStyleRules =
                                                        rpStyleRules.filterNot { it.id == rule.id }
                                                    onSaveRpStyleRules(rpStyleRules)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            Switch(
                                                checked = rule.enabled,
                                                onCheckedChange = { enabled ->
                                                    rpStyleRules = rpStyleRules.map {
                                                        if (it.id == rule.id) {
                                                            it.copy(enabled = enabled)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                    onSaveRpStyleRules(rpStyleRules)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (activeDestinationId != "UiCustomization") {
                            LastChatSettingGroupInputItem(
                                title = "Appearance",
                                subtitle = "Theme and system color mode",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(label = { Text("Theme") }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        listOf(
                                            "seafoam_mint" to "Seafoam",
                                            "ocean" to "Ocean",
                                            "sakura" to "Sakura",
                                            "spring" to "Spring",
                                            "autumn" to "Autumn",
                                            "black" to "Black",
                                        ).forEach { (id, label) ->
                                            if (themeId == id) {
                                                Button(onClick = {}) { Text(label) }
                                            } else {
                                                TextButton(onClick = {
                                                    themeId = id
                                                    onSaveAppearance(themeId, colorMode)
                                                }) { Text(label) }
                                            }
                                        }
                                    }
                                }
                                LastChatFormItem(label = { Text("Color mode") }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        IosColorMode.entries.forEach { mode ->
                                            val label = mode.name.lowercase().replaceFirstChar {
                                                it.uppercase()
                                            }
                                            if (colorMode == mode) {
                                                Button(onClick = {}) { Text(label) }
                                            } else {
                                                TextButton(onClick = {
                                                    colorMode = mode
                                                    onSaveAppearance(themeId, colorMode)
                                                }) { Text(label) }
                                            }
                                        }
                                    }
                                }
                                LastChatFormItem(
                                    label = { Text("AMOLED dark surfaces") },
                                    description = {
                                        Text("Dark mode uses true black for background and surface, matching Android's always-on OLED canvas. Compose Multiplatform applies this without Material You.")
                                    },
                                    tail = {
                                        Switch(checked = true, onCheckedChange = { }, enabled = false)
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Developer mode") },
                                    description = { Text("Unlocks the Developer destination for overlay and analytics diagnostics") },
                                    tail = {
                                        Switch(
                                            checked = state.appearance.developerMode,
                                            onCheckedChange = onSaveDeveloperMode,
                                        )
                                    },
                                )
                            }
                            LastChatSettingGroupItem(
                                title = "Fonts",
                                subtitle = "Choose the app-wide typeface",
                                darkTheme = darkTheme,
                                icon = { Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp)) },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                onClick = { openSettingsDestination("Fonts", "Fonts") },
                            )
                            LastChatSettingGroupItem(
                                title = "UI customization",
                                subtitle = "Chat presentation and content scale",
                                darkTheme = darkTheme,
                                icon = { Icon(Icons.Rounded.Brush, null, Modifier.size(20.dp)) },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                onClick = { openSettingsDestination("UiCustomization", "UI customization") },
                            )
                            LastChatSettingGroupItem(
                                title = "Roleplay optimizations",
                                subtitle = "Color roleplay and Markdown patterns in chat",
                                darkTheme = darkTheme,
                                icon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp)) },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                                onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                                onClick = { openSettingsDestination("RpOptimizations", "Roleplay optimizations") },
                            )
                            LastChatSettingGroupInputItem(
                                title = "Basic settings",
                                subtitle = "Startup, notifications, catalog, and TTS autoplay",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(
                                    label = { Text("New chat on start") },
                                    description = { Text("Open a blank conversation when LastChat launches") },
                                    tail = {
                                        Switch(
                                            checked = createNewConversationOnStart,
                                            onCheckedChange = { enabled ->
                                                createNewConversationOnStart = enabled
                                                onSaveDisplayKnobs(
                                                    notifyOnGeneration,
                                                    checkForUpdates,
                                                    enabled,
                                                    ttsAutoplay,
                                                )
                                            },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Notify when a reply is ready") },
                                    description = {
                                        Text("Posts a UserNotifications alert on the chat_completed channel")
                                    },
                                    tail = {
                                        Switch(
                                            checked = notifyOnGeneration,
                                            onCheckedChange = { enabled ->
                                                notifyOnGeneration = enabled
                                                if (enabled) onRequestNotificationPermission()
                                                onSaveDisplayKnobs(
                                                    enabled,
                                                    checkForUpdates,
                                                    createNewConversationOnStart,
                                                    ttsAutoplay,
                                                )
                                            },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Check for updates") },
                                    description = {
                                        Text("Fetches GitHub releases/latest with the same Accept header Android uses")
                                    },
                                    tail = {
                                        Switch(
                                            checked = checkForUpdates,
                                            onCheckedChange = { enabled ->
                                                persistAppearance { copy(checkForUpdates = enabled) }
                                            },
                                        )
                                    },
                                )
                                state.updateInfo?.let { info ->
                                    Text(
                                        "Latest GitHub release: ${info.version}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    info.downloads.firstOrNull()?.let { download ->
                                        Text(
                                            "${download.name} · ${download.size.ifBlank { formatUpdateFileSize(0) }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                state.updateCheckError?.let { error ->
                                    Text(error, color = MaterialTheme.colorScheme.error)
                                }
                                Button(
                                    onClick = onRefreshUpdateCheck,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Check GitHub now") }
                                LastChatFormItem(
                                    label = { Text("TTS autoplay") },
                                    description = { Text("Read assistant replies automatically after generation") },
                                    tail = {
                                        Switch(
                                            checked = ttsAutoplay,
                                            onCheckedChange = { enabled ->
                                                ttsAutoplay = enabled
                                                onSaveDisplayKnobs(
                                                    notifyOnGeneration,
                                                    checkForUpdates,
                                                    createNewConversationOnStart,
                                                    enabled,
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                            LastChatSettingGroupInputItem(
                                title = "Model catalog",
                                subtitle = iosCatalogSubtitle(state.catalogStatus),
                                darkTheme = darkTheme,
                            ) {
                                Button(
                                    onClick = onRefreshModelCatalog,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.catalogStatus.isRefreshing,
                                ) { Text("Refresh catalog") }
                                if (state.catalogStatus.isRefreshing) {
                                    Text(
                                        "Downloading lastchat_catalog.json…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            LastChatSettingGroupInputItem(
                                title = "UI customization",
                                subtitle = "Chat presentation and content scale",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(label = { Text("Nickname") }) {
                                    OutlinedTextField(
                                        value = userNickname,
                                        onValueChange = { persistAppearance { copy(userNickname = it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                        placeholder = { Text("You") },
                                    )
                                }
                                LastChatFormItem(label = { Text("User avatar") }) {
                                    IosAvatarPickerInline(
                                        nickname = userNickname,
                                        avatar = userAvatar,
                                        onSelectEmoji = { persistAppearance { copy(userAvatar = IosAvatar.Emoji(it)) } },
                                        onSelectImage = {
                                            onPickAvatarFile { result ->
                                                val picked = result.getOrNull() ?: return@onPickAvatarFile
                                                if (picked.kind == PlatformPickedFileKind.Image) {
                                                    persistAppearance {
                                                        copy(
                                                            userAvatar = IosAvatar.Image(
                                                                picked.localUrl.ifBlank { picked.storagePath },
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onSelectDummy = { persistAppearance { copy(userAvatar = IosAvatar.Dummy) } },
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("New chat header") },
                                    description = { Text("GREETING, BIG_ICON, or NONE — same key as Android") },
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IosNewChatHeaderStyle.entries.forEach { style ->
                                            if (newChatHeaderStyle == style) {
                                                Button(onClick = {}) { Text(style.name) }
                                            } else {
                                                TextButton(onClick = {
                                                    persistAppearance { copy(newChatHeaderStyle = style) }
                                                }) { Text(style.name) }
                                            }
                                        }
                                    }
                                }
                                if (newChatHeaderStyle != IosNewChatHeaderStyle.NONE) {
                                    LastChatFormItem(
                                        label = { Text("Avatar in new-chat header") },
                                        tail = {
                                            Switch(
                                                checked = newChatShowAvatar,
                                                onCheckedChange = { persistAppearance { copy(newChatShowAvatar = it) } },
                                            )
                                        },
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("New chat content") },
                                    description = { Text("TEMPLATES, ACTIONS, or NONE") },
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IosNewChatContentStyle.entries.forEach { style ->
                                            if (newChatContentStyle == style) {
                                                Button(onClick = {}) { Text(style.name) }
                                            } else {
                                                TextButton(onClick = {
                                                    persistAppearance { copy(newChatContentStyle = style) }
                                                }) { Text(style.name) }
                                            }
                                        }
                                    }
                                }
                                LastChatFormItem(
                                    label = { Text("Assistant message bubbles") },
                                    description = { Text("Show a filled surface behind assistant messages") },
                                    tail = {
                                        Switch(
                                            checked = showAssistantBubbles,
                                            onCheckedChange = { persistAppearance { copy(showAssistantBubbles = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Show character avatar") },
                                    description = { Text("Display the model/character icon on assistant messages") },
                                    tail = {
                                        Switch(
                                            checked = showModelIcon,
                                            onCheckedChange = { persistAppearance { copy(showModelIcon = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Show model name") },
                                    tail = {
                                        Switch(
                                            checked = showModelName,
                                            onCheckedChange = { persistAppearance { copy(showModelName = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Show user avatar") },
                                    tail = {
                                        Switch(
                                            checked = showUserAvatar,
                                            onCheckedChange = { persistAppearance { copy(showUserAvatar = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Show token usage") },
                                    description = { Text("Show input/output token counts after a reply") },
                                    tail = {
                                        Switch(
                                            checked = showTokenUsage,
                                            onCheckedChange = { persistAppearance { copy(showTokenUsage = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Context token summary") },
                                    description = { Text("Show the last-turn token meter in the chat toolbar") },
                                    tail = {
                                        Switch(
                                            checked = showContextTokenSummary,
                                            onCheckedChange = { persistAppearance { copy(showContextTokenSummary = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Auto-collapse thinking") },
                                    description = { Text("Collapse reasoning blocks when generation finishes") },
                                    tail = {
                                        Switch(
                                            checked = autoCloseThinking,
                                            onCheckedChange = { persistAppearance { copy(autoCloseThinking = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Reasoning preview") },
                                    description = { Text("Keep a short snippet visible when thinking is collapsed") },
                                    tail = {
                                        Switch(
                                            checked = reasoningPreviewEnabled,
                                            onCheckedChange = { persistAppearance { copy(reasoningPreviewEnabled = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Context stacks") },
                                    description = { Text("Show lorebook, skill, and memory counts on assistant turns") },
                                    tail = {
                                        Switch(
                                            checked = showContextStacks,
                                            onCheckedChange = { persistAppearance { copy(showContextStacks = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Blur chrome") },
                                    description = { Text("Soft-focus the chat toolbar and composer") },
                                    tail = {
                                        Switch(
                                            checked = enableBlurEffect,
                                            onCheckedChange = { persistAppearance { copy(enableBlurEffect = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Toolbar at bottom") },
                                    tail = {
                                        Switch(
                                            checked = chatToolbarAtBottom,
                                            onCheckedChange = { persistAppearance { copy(chatToolbarAtBottom = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Message jumper") },
                                    description = { Text("Overlay jump-to-top/bottom controls on the chat list") },
                                    tail = {
                                        Switch(
                                            checked = showMessageJumper,
                                            onCheckedChange = { persistAppearance { copy(showMessageJumper = it) } },
                                        )
                                    },
                                )
                                if (showMessageJumper) {
                                    LastChatFormItem(
                                        label = { Text("Jumper on the left") },
                                        tail = {
                                            Switch(
                                                checked = messageJumperOnLeft,
                                                onCheckedChange = { persistAppearance { copy(messageJumperOnLeft = it) } },
                                            )
                                        },
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("Generation haptics") },
                                    description = { Text("Pulse while the assistant reply is streaming") },
                                    tail = {
                                        Switch(
                                            checked = enableGenerationHaptics,
                                            onCheckedChange = {
                                                persistAppearance { copy(enableMessageGenerationHapticEffect = it) }
                                            },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("UI haptics") },
                                    description = { Text("Play PremiumHaptics patterns for toggles and presses") },
                                    tail = {
                                        Switch(
                                            checked = enableUIHaptics,
                                            onCheckedChange = { persistAppearance { copy(enableUIHaptics = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Wrap code blocks") },
                                    tail = {
                                        Switch(
                                            checked = codeBlockAutoWrap,
                                            onCheckedChange = { persistAppearance { copy(codeBlockAutoWrap = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Collapse code blocks") },
                                    tail = {
                                        Switch(
                                            checked = codeBlockAutoCollapse,
                                            onCheckedChange = { persistAppearance { copy(codeBlockAutoCollapse = it) } },
                                        )
                                    },
                                )
                                LastChatFormItem(
                                    label = { Text("Chat font size") },
                                    description = { Text("${(fontSizeRatio * 100).toInt()}%") },
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Slider(
                                            value = fontSizeRatio,
                                            onValueChange = { fontSizeRatio = it },
                                            onValueChangeFinished = {
                                                persistAppearance { copy(fontSizeRatio = fontSizeRatio) }
                                            },
                                            valueRange = 0.5f..2f,
                                            steps = 11,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            text = "The quick brown fox jumps over the lazy dog.",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontSizeRatio,
                                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontSizeRatio,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Data) {
                item {
                    LastChatSettingsGroup(
                        title = "Storage",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = "Data",
                            subtitle = "Conversations are stored in the iOS app container",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(
                                label = { Text("App data") },
                                description = {
                                    Text("Provider credentials are stored separately in Keychain")
                                },
                            )
                            Button(
                                onClick = onRunStorageMaintenance,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Run storage maintenance") }
                            Text(
                                "Removes unreferenced uploads, images, and attachments from the app container. Also runs daily in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (section == IosSettingsSection.About) {
                item {
                    val platformInfo = currentIosPlatformInfo()
                    LastChatAboutContent(
                        appName = "LastChat",
                        versionName = IOS_APP_VERSION,
                        darkTheme = darkTheme,
                        platformTitle = "iOS Version",
                        platformSubtitle = platformInfo.systemVersion,
                        platformIcon = Icons.Rounded.PhoneIphone,
                        deviceSubtitle = platformInfo.device,
                        deviceIcon = Icons.Rounded.PhoneIphone,
                        architectureSubtitle = platformInfo.architecture,
                        architectureIcon = Icons.Rounded.Memory,
                        onSourceCode = {
                            openIosExternalUrl("https://github.com/Cocolalilal/LastChat")
                        },
                        onHaptic = {
                            platformHaptics.perform(PlatformHapticPattern.Pop)
                        },
                    )
                }
            }
            if (section == IosSettingsSection.Backup) {
                item {
                    LastChatSettingsGroup(
                        title = "Restore from Android",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = "Restore backup",
                            subtitle = "Import providers, assistants, appearance, search, TTS, skills, lorebooks, MCP, and WebDAV settings from a LastChat Android backup (.zip).",
                            darkTheme = darkTheme,
                        ) {
                            Button(
                                enabled = !restoringBackup,
                                shape = AppShapes.ButtonRounded,
                                onClick = {
                                    onPickBackupFile { result ->
                                        val picked = result.getOrNull()
                                        if (picked != null && !restoringBackup) {
                                            pendingBackupRestorePath = picked.storagePath
                                        }
                                    }
                                },
                            ) {
                                Text(if (restoringBackup) "Restoring…" else "Restore from file")
                            }
                        }
                        LastChatSettingGroupItem(
                            title = "WebDAV backup",
                            subtitle = "Cloud sync using the same portable archive as Android",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.CloudUpload, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("BackupWebDav", "WebDAV backup") },
                        )
                    }
                }
            }
            if (section == IosSettingsSection.BackupWebDav) {
                item {
                    IosWebDavSettings(
                        state = state,
                        darkTheme = darkTheme,
                        onSave = onSaveWebDav,
                        onTest = onTestWebDav,
                        onList = onListWebDav,
                        onBackup = onBackupWebDav,
                        onRestore = onRestoreWebDav,
                        onDelete = onDeleteWebDav,
                    )
                }
            }
            if (section == IosSettingsSection.Skills) {
                item { IosSkillsSettings(state, darkTheme, onSavePromptInjections) }
                item {
                    LastChatSettingGroupItem(
                        title = "Lorebooks",
                        subtitle = "World info and keyword activation",
                        darkTheme = darkTheme,
                        icon = { Icon(Icons.Rounded.Folder, null, Modifier.size(20.dp)) },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        onClick = { openSettingsDestination("Lorebooks", "Lorebooks") },
                    )
                }
            }
            if (section == IosSettingsSection.Lorebooks) {
                item { IosLorebookSettings(state, darkTheme, onSavePromptInjections) }
                item {
                    LastChatSettingGroupItem(
                        title = "Skills",
                        subtitle = "Prompt skills and manage_skills",
                        darkTheme = darkTheme,
                        icon = { Icon(Icons.Rounded.Code, null, Modifier.size(20.dp)) },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        onClick = { openSettingsDestination("Skills", "Skills") },
                    )
                }
            }
            if (section == IosSettingsSection.Mcp) {
                item { IosMcpSettings(state, darkTheme, onSaveMcpServers, onRefreshMcpTools) }
            }
            if (section == IosSettingsSection.Speech) {
                item { IosSttSettings(state, darkTheme, onSaveStt, onClearSttApiKey) }
            }
            if (section == IosSettingsSection.Web) {
                item { IosWebSettings(state, darkTheme, onSaveWeb) }
            }
            if (section == IosSettingsSection.Workspaces) {
                item {
                    IosWorkspaceSettings(
                        state = state,
                        darkTheme = darkTheme,
                        onDownloadLlm = onDownloadLocalLlm,
                        onDownloadStt = onDownloadLocalStt,
                        onCancelDownload = onCancelLocalDownload,
                        onDeleteLlm = onDeleteLocalLlm,
                        onDeleteStt = onDeleteLocalStt,
                    )
                }
            }
            if (section == IosSettingsSection.AndroidIntegration) {
                item { IosAndroidIntegrationSettings(state, darkTheme, onSaveOverlaySettings) }
            }
            if (section == IosSettingsSection.Developer) {
                item { IosDeveloperSettings(state, darkTheme, onSaveDeveloperMode) }
            }
            if (section == IosSettingsSection.Unavailable) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.CardMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                unavailableDestinationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "This settings destination is not available on iOS yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        pendingBackupRestorePath?.let { path ->
            AlertDialog(
                onDismissRequest = { pendingBackupRestorePath = null },
                title = { Text("Restore backup?") },
                text = {
                    Text(
                        "This replaces the current iOS providers, assistants, appearance, search and TTS settings with the backup contents. Conversations on this device are kept.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingBackupRestorePath = null
                            restoringBackup = true
                            onRestoreBackup(path) { restoreResult ->
                                restoringBackup = false
                                restoreResult.fold(
                                    onSuccess = { backupRestoreReport = it },
                                    onFailure = { backupRestoreError = it.message ?: "Restore failed" },
                                )
                            }
                        },
                    ) { Text("Restore") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingBackupRestorePath = null }) { Text("Cancel") }
                },
            )
        }
        backupRestoreReport?.let { report ->
            AlertDialog(
                onDismissRequest = { backupRestoreReport = null },
                title = { Text("Backup restored") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportSection("Imported", report.applied)
                        ReportSection("Notes", report.warnings)
                        ReportSection("Not imported yet", report.skipped)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { backupRestoreReport = null }) { Text("OK") }
                },
            )
        }
        backupRestoreError?.let { message ->
            AlertDialog(
                onDismissRequest = { backupRestoreError = null },
                title = { Text("Restore failed") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { backupRestoreError = null }) { Text("OK") }
                },
            )
        }
        editingMemory?.let { memory ->
            AlertDialog(
                onDismissRequest = { editingMemory = null },
                title = { Text("Manage memory") },
                text = {
                    OutlinedTextField(
                        value = editingMemoryContent,
                        onValueChange = { editingMemoryContent = it },
                        minLines = 1,
                        maxLines = 8,
                        shape = AppShapes.InputField,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val content = editingMemoryContent.trim()
                            if (content.isNotBlank()) {
                                if (memory.id == 0) onAddMemory(content)
                                else onUpdateMemory(memory.id, content)
                            }
                            editingMemory = null
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editingMemory = null }) { Text("Cancel") }
                },
            )
        }
        if (showAddProvider) {
            AlertDialog(
                onDismissRequest = { showAddProvider = false },
                title = { Text("Add Provider") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newProviderName,
                            onValueChange = { newProviderName = it },
                            label = { Text("Provider Name") },
                            shape = AppShapes.InputField,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Type", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IosProviderType.entries.forEach { type ->
                                if (newProviderType == type) {
                                    Button(onClick = {}) { Text(type.displayName()) }
                                } else {
                                    TextButton(onClick = { newProviderType = type }) { Text(type.displayName()) }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newProviderName.isNotBlank()) {
                                onAddProvider(newProviderName.trim(), newProviderType)
                                showAddProvider = false
                                newProviderName = ""
                            }
                        },
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddProvider = false }) { Text("Cancel") }
                },
            )
        }
        if (showAddRpStyleRuleDialog || editingRpStyleRule != null) {
            val existingRule = editingRpStyleRule
            IosRpStyleRuleDialog(
                rule = existingRule,
                onDismiss = {
                    showAddRpStyleRuleDialog = false
                    editingRpStyleRule = null
                },
                onSave = { savedRule ->
                    rpStyleRules = if (existingRule == null) {
                        rpStyleRules + savedRule
                    } else {
                        rpStyleRules.map { rule ->
                            if (rule.id == existingRule.id) savedRule else rule
                        }
                    }
                    onSaveRpStyleRules(rpStyleRules)
                    showAddRpStyleRuleDialog = false
                    editingRpStyleRule = null
                },
            )
        }
        if (showAddTtsFilter || editingTtsFilter != null) {
            IosTtsFilterDialog(
                rule = editingTtsFilter,
                onDismiss = {
                    showAddTtsFilter = false
                    editingTtsFilter = null
                },
                onSave = { saved ->
                    persistAppearance {
                        copy(
                            ttsTextFilterRules = if (editingTtsFilter == null) {
                                ttsTextFilterRules + saved
                            } else {
                                ttsTextFilterRules.map { if (it.id == saved.id) saved else it }
                            },
                        )
                    }
                    showAddTtsFilter = false
                    editingTtsFilter = null
                },
            )
        }
        if (showModelPicker) {
            IosChatModelPickerSheet(
                state = state,
                searchQuery = modelSearchQuery,
                onSearchQueryChange = { modelSearchQuery = it },
                onSelect = { providerId, modelId ->
                    onSelectDefaultModel(providerId, modelId)
                    showModelPicker = false
                },
                onDismiss = { showModelPicker = false },
                platformHaptics = platformHaptics,
            )
        }
    }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosChatModelPickerSheet(
    state: IosAppState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit,
    platformHaptics: PlatformHaptics,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedChatModel = state.selectedChatModel
    val visibleProviders = state.providers.mapNotNull { provider ->
        val matchingModels = provider.models.filter { model ->
            model.type == ModelType.CHAT && (
                searchQuery.isBlank() ||
                    model.modelId.contains(searchQuery, ignoreCase = true) ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    provider.name.contains(searchQuery, ignoreCase = true)
                )
        }
        if (matchingModels.isEmpty()) null else provider to matchingModels
    }
    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.8f).imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = AppShapes.SearchField,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search models") },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visibleProviders.forEach { (provider, models) ->
                    item("model-provider-${provider.id}") {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(models, key = { "model-${provider.id}-${it.id}" }) { model ->
                        val selected = selectedChatModel?.first?.id == provider.id &&
                            selectedChatModel?.second?.id == model.id
                        LastChatGroupedModelRow(
                            title = model.displayName.ifBlank { model.modelId },
                            selected = selected,
                            position = LastChatModelGroupPosition.Single,
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                onSelect(provider.id.toString(), model.modelId)
                            },
                            icon = {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            provider.name.firstOrNull()?.uppercase() ?: "P",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IosAvatarPickerInline(
    nickname: String,
    avatar: IosAvatar,
    onSelectEmoji: (String) -> Unit,
    onSelectImage: () -> Unit,
    onSelectDummy: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IosAvatarView(
            avatar = avatar,
            letter = iosAvatarLetter(nickname, "Y"),
            size = 56.dp,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IOS_AVATAR_EMOJI_PRESETS.forEach { emoji ->
                Surface(
                    onClick = { onSelectEmoji(emoji) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(emoji, modifier = Modifier.padding(8.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectImage) { Text("Photo") }
            TextButton(onClick = onSelectDummy) { Text("Letter") }
        }
    }
}

@Composable
private fun IosAvatarPickerDialog(
    title: String,
    nickname: String,
    avatar: IosAvatar,
    onNicknameChange: (String) -> Unit,
    onSelectEmoji: (String) -> Unit,
    onSelectImage: () -> Unit,
    onSelectDummy: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    label = { Text("Nickname") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                IosAvatarPickerInline(
                    nickname = nickname,
                    avatar = avatar,
                    onSelectEmoji = onSelectEmoji,
                    onSelectImage = onSelectImage,
                    onSelectDummy = onSelectDummy,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun IosTriStateRow(
    title: String,
    value: Boolean?,
    globalValue: Boolean,
    onValueChange: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Global is ${if (globalValue) "on" else "off"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf<Boolean?>(null, true, false).forEach { option ->
                val label = when (option) {
                    null -> "Global"
                    true -> "On"
                    false -> "Off"
                }
                if (value == option) {
                    Button(onClick = {}) { Text(label) }
                } else {
                    TextButton(onClick = { onValueChange(option) }) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun IosTtsFilterDialog(
    rule: TtsTextFilterRule?,
    onDismiss: () -> Unit,
    onSave: (TtsTextFilterRule) -> Unit,
) {
    var pattern by remember(rule?.id) { mutableStateOf(rule?.pattern ?: "*") }
    var mode by remember(rule?.id) { mutableStateOf(rule?.mode ?: TtsFilterMode.SKIP) }
    var enabled by remember(rule?.id) { mutableStateOf(rule?.enabled ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "Add TTS filter" else "Edit TTS filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TtsFilterMode.entries.forEach { entry ->
                        if (mode == entry) {
                            Button(onClick = {}) { Text(entry.name) }
                        } else {
                            TextButton(onClick = { mode = entry }) { Text(entry.name) }
                        }
                    }
                }
                LastChatFormItem(
                    label = { Text("Enabled") },
                    tail = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(
                            (rule ?: TtsTextFilterRule()).copy(
                                pattern = pattern,
                                mode = mode,
                                enabled = enabled,
                            ),
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Typography.withIosFontConfig(config: IosFontConfig, fontSizeRatio: Float): Typography {
    val sizeMul = (config.fontSize * fontSizeRatio).coerceIn(0.5f, 2.5f)
    val lineMul = config.lineHeight.coerceIn(0.75f, 2.0f)
    val extraLetter = config.letterSpacing.coerceIn(-0.05f, 0.1f)
    val weight = FontWeight(config.weight.toInt().coerceIn(1, 1000))
    fun TextStyle.scaled(): TextStyle = copy(
        fontWeight = weight,
        fontSize = fontSize * sizeMul,
        lineHeight = lineHeight * lineMul,
        letterSpacing = androidx.compose.ui.unit.TextUnit(
            extraLetter,
            androidx.compose.ui.unit.TextUnitType.Em,
        ),
    )
    return copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}

private fun iosFormatDecimal(value: Float, digits: Int = 2): String {
    var factor = 1
    repeat(digits) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor) / factor
    return scaled.toString()
}

@Composable
private fun IosRpStyleRuleDialog(
    rule: IosRpStyleRule?,
    onDismiss: () -> Unit,
    onSave: (IosRpStyleRule) -> Unit,
) {
    var pattern by remember(rule?.id) { mutableStateOf(rule?.pattern.orEmpty()) }
    var colorHex by remember(rule?.id) { mutableStateOf(rule?.colorHex ?: "#808080") }
    val initialColor = iosColorFromHex(colorHex) ?: Color.Gray
    var red by remember(rule?.id) { mutableStateOf((initialColor.red * 255).toInt()) }
    var green by remember(rule?.id) { mutableStateOf((initialColor.green * 255).toInt()) }
    var blue by remember(rule?.id) { mutableStateOf((initialColor.blue * 255).toInt()) }

    fun syncHexFromRgb() {
        fun Int.hexByte() = coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
        colorHex = "#${red.hexByte()}${green.hexByte()}${blue.hexByte()}"
    }

    fun selectColor(hex: String) {
        colorHex = hex
        iosColorFromHex(hex)?.let { color ->
            red = (color.red * 255).toInt()
            green = (color.green * 255).toInt()
            blue = (color.blue * 255).toInt()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "Add rule" else "Edit rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    placeholder = { Text("*") },
                    supportingText = {
                        Text(
                            when (pattern) {
                                "*" -> "Italic text"
                                "**" -> "Bold text"
                                "~~" -> "Strikethrough text"
                                "`" -> "Inline code"
                                ">" -> "Blockquotes"
                                "#", "##", "###", "####", "#####", "######" ->
                                    "Heading level ${pattern.length}"
                                "" -> "Enter a pattern"
                                else -> "Custom pattern: $pattern"
                            }
                        )
                    },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { selectColor(it) },
                    label = { Text("Color (hex)") },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(iosColorFromHex(colorHex) ?: Color.Gray)
                        )
                    },
                    isError = normalizeIosColorHex(colorHex) == null,
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "#808080",
                        "#FFD700",
                        "#87CEEB",
                        "#90EE90",
                        "#FFB6C1",
                        "#FF6B6B",
                    ).forEach { hex ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(iosColorFromHex(hex) ?: Color.Gray)
                                .clickable { selectColor(hex) }
                        )
                    }
                }
                listOf(
                    Triple("R", Color.Red, red),
                    Triple("G", Color.Green, green),
                    Triple("B", Color.Blue, blue),
                ).forEach { (label, tint, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = tint, modifier = Modifier.size(20.dp))
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { updated ->
                                when (label) {
                                    "R" -> red = updated.toInt()
                                    "G" -> green = updated.toInt()
                                    else -> blue = updated.toInt()
                                }
                                syncHexFromRgb()
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(value.toString(), modifier = Modifier.widthIn(min = 36.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && normalizeIosColorHex(colorHex) != null,
                onClick = {
                    onSave(
                        IosRpStyleRule(
                            id = rule?.id ?: kotlin.uuid.Uuid.random().toString(),
                            pattern = pattern.trim(),
                            colorHex = normalizeIosColorHex(colorHex) ?: "#808080",
                            enabled = rule?.enabled ?: true,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IosMessageActionsRow(
    message: DisplayMessage,
    clipboard: ClipboardManager,
    platformHaptics: PlatformHaptics,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onSelect: (Int) -> Unit,
    onRegenerate: () -> Unit,
) {
    val node = message.branchNode
    val versionIndices = node?.let { it.versionSelectionIndices() }.orEmpty()
    val position = node?.let { it.versionSelectionPosition() } ?: -1
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionIcon(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = "Copy",
            onClick = {
                clipboard.setText(AnnotatedString(message.text))
                platformHaptics.perform(PlatformHapticPattern.Tick)
            },
        )
        if (message.canRegenerate) {
            ActionIcon(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Regenerate",
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    onRegenerate()
                },
            )
        }
        if (message.messageId != null) {
            ActionIcon(
                icon = Icons.Rounded.Edit,
                contentDescription = "Edit",
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    onEdit()
                },
            )
            ActionIcon(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                contentDescription = "Fork from here",
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    onFork()
                },
            )
            ActionIcon(
                icon = Icons.Rounded.Delete,
                contentDescription = "Delete",
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Thud)
                    onDelete()
                },
            )
        }
        if (node != null && versionIndices.size > 1 && position >= 0) {
            Spacer(Modifier.width(4.dp))
            val canGoPrev = position > 0
            val canGoNext = position < versionIndices.lastIndex
            ActionIcon(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = "Previous version",
                enabled = canGoPrev,
                onClick = {
                    versionIndices.getOrNull(position - 1)?.let(onSelect)
                    platformHaptics.perform(PlatformHapticPattern.Tick)
                },
            )
            Text("${position + 1}/${versionIndices.size}", style = MaterialTheme.typography.bodySmall)
            ActionIcon(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "Next version",
                enabled = canGoNext,
                onClick = {
                    versionIndices.getOrNull(position + 1)?.let(onSelect)
                    platformHaptics.perform(PlatformHapticPattern.Tick)
                },
            )
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}

@Composable
private fun ReportSection(title: String, entries: List<String>) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        entries.take(10).forEach { entry ->
            Text("• $entry", style = MaterialTheme.typography.bodySmall)
        }
        if (entries.size > 10) {
            Text("+${entries.size - 10} more", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun iosSettingsPaneGroups(developerMode: Boolean = false): List<LastChatSettingsPaneGroup> {
    val assistantChildren = listOf(
        LastChatSettingsPaneEntry("AssistantMemory", "Memory", Icons.Rounded.Memory),
        LastChatSettingsPaneEntry("AssistantTools", "Tools", Icons.Rounded.Extension),
    )
    val displayChildren = listOf(
        LastChatSettingsPaneEntry("Fonts", "Fonts", Icons.Rounded.Tune),
        LastChatSettingsPaneEntry("UiCustomization", "UI customization", Icons.Rounded.Brush),
        LastChatSettingsPaneEntry("RpOptimizations", "Roleplay optimizations", Icons.Rounded.AutoAwesome),
    )
    val providerChildren = listOf(
        LastChatSettingsPaneEntry("ProviderModels", "Provider models", Icons.Rounded.Cloud),
        LastChatSettingsPaneEntry("Search", "Search service", Icons.Rounded.Public),
        LastChatSettingsPaneEntry("Tts", "Text-to-speech", Icons.AutoMirrored.Rounded.VolumeUp),
        LastChatSettingsPaneEntry("SpeechToText", "Speech-to-text", Icons.Rounded.Mic),
    )
    val promptChildren = listOf(
        LastChatSettingsPaneEntry("Skills", "Skills", Icons.Rounded.Code),
        LastChatSettingsPaneEntry("Lorebooks", "Lorebooks", Icons.Rounded.Folder),
    )
    val backupChildren = listOf(
        LastChatSettingsPaneEntry("BackupWebDav", "WebDAV backup", Icons.Rounded.CloudUpload),
        LastChatSettingsPaneEntry("BackupLocal", "Import and export", Icons.Rounded.FileUpload),
    )
    return listOf(
        LastChatSettingsPaneGroup(
            id = "general",
            title = "General settings",
            entries = listOf(
                LastChatSettingsPaneEntry("Display", "Display", Icons.Rounded.Tune, children = displayChildren),
                LastChatSettingsPaneEntry(
                    "Assistants",
                    "Assistant",
                    Icons.Rounded.Group,
                    children = assistantChildren,
                ),
                LastChatSettingsPaneEntry(
                    "PromptInjections",
                    "Prompt injections",
                    Icons.Rounded.Extension,
                    children = promptChildren,
                ),
            ),
        ),
        LastChatSettingsPaneGroup(
            id = "models_services",
            title = "Models & services",
            entries = listOf(
                LastChatSettingsPaneEntry("Models", "Default model", Icons.Rounded.AccountTree),
                LastChatSettingsPaneEntry("Providers", "Providers", Icons.Rounded.Cloud, children = providerChildren),
                LastChatSettingsPaneEntry("Mcp", "MCP", Icons.Rounded.Code),
                LastChatSettingsPaneEntry("Web", "Web server", Icons.Rounded.Language),
                LastChatSettingsPaneEntry("AndroidIntegration", "Android integration", Icons.Rounded.PhoneAndroid),
                LastChatSettingsPaneEntry("Workspaces", "Workspaces", Icons.Rounded.Code),
            ),
        ),
        LastChatSettingsPaneGroup(
            id = "data",
            title = "Data",
            entries = listOf(
                LastChatSettingsPaneEntry("Backup", "Backup", Icons.Rounded.CloudUpload, children = backupChildren),
                LastChatSettingsPaneEntry("ChatStorage", "Chat storage", Icons.Rounded.Storage),
            ),
        ),
        LastChatSettingsPaneGroup(
            id = "about",
            title = "About",
            entries = buildList {
                add(LastChatSettingsPaneEntry("About", "About", Icons.Rounded.Info))
                if (developerMode) {
                    add(LastChatSettingsPaneEntry("Developer", "Developer", Icons.Rounded.Build))
                }
            },
        ),
    )
}

private fun iosSettingsMainDestination(destinationId: String): String = when (destinationId) {
    "AssistantMemory" -> "Assistants"
    "AssistantTools" -> "Assistants"
    "Fonts", "UiCustomization", "RpOptimizations" -> "Display"
    "ProviderModels", "Search", "Tts", "SpeechToText" -> "Providers"
    "Skills", "Lorebooks" -> "PromptInjections"
    "BackupWebDav", "BackupLocal" -> "Backup"
    else -> destinationId
}

private fun List<LastChatSettingsPaneGroup>.titleFor(destinationId: String): String? {
    fun LastChatSettingsPaneEntry.findTitle(): String? {
        if (id == destinationId) return title
        for (child in children) child.findTitle()?.let { return it }
        return null
    }
    for (group in this) {
        for (entry in group.entries) entry.findTitle()?.let { return it }
    }
    return null
}

@Composable
private fun IosBackButton(
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    LastChatBackButton(
        onClick = onBack,
        contentDescription = "Back",
        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
    )
}

private fun IosProviderType.displayName(): String = when (this) {
    IosProviderType.OPENAI -> "OpenAI"
    IosProviderType.GOOGLE -> "Google"
    IosProviderType.CLAUDE -> "Claude"
    IosProviderType.LOCAL -> "On-device"
    IosProviderType.COMFY -> "ComfyUI"
}

private fun IosImageProviderType.displayName(): String = when (this) {
    IosImageProviderType.OPENAI -> "OpenAI"
    IosImageProviderType.GOOGLE -> "Google"
    IosImageProviderType.COMFY_UI -> "ComfyUI"
}

private fun ImageGenerationMethod.displayName(): String = when (this) {
    ImageGenerationMethod.DIFFUSION -> "Diffusion"
    ImageGenerationMethod.MULTIMODAL -> "Multimodal"
}

private fun IosProviderType.defaultBaseUrl(): String = when (this) {
    IosProviderType.OPENAI -> "https://api.openai.com/v1"
    IosProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
    IosProviderType.CLAUDE -> "https://api.anthropic.com/v1"
    IosProviderType.LOCAL -> "on-device"
    IosProviderType.COMFY -> "http://127.0.0.1:8188"
}

private fun IosProviderType.defaultModelId(): String = when (this) {
    IosProviderType.OPENAI -> "gpt-4.1-mini"
    IosProviderType.GOOGLE -> "gemini-2.5-flash"
    IosProviderType.CLAUDE -> "claude-sonnet-4-5"
    IosProviderType.LOCAL -> "on-device"
    IosProviderType.COMFY -> "workflow"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosAssistantOverlaySheet(
    state: IosAppState,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onIngestShareText: (String) -> Unit,
    platformHaptics: PlatformHaptics,
) {
    val clipboard = LocalClipboardManager.current
    var overlayText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Assistant overlay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Same composer as Android's digital assistant overlay. Siri \"Ask LastChat\", lastchat://overlay, and long-press on the overlay icon all open this sheet. Share-in pastes clipboard text into a new chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = overlayText,
                onValueChange = { overlayText = it },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.InputField,
                minLines = 2,
                maxLines = 6,
                placeholder = { Text("Ask ${state.assistant.name}") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val text = overlayText.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            overlayText = ""
                            platformHaptics.perform(PlatformHapticPattern.Send)
                            onDismiss()
                        }
                    },
                ) { Text("Send") }
                TextButton(
                    onClick = {
                        val clipped = clipboard.getText()?.text.orEmpty()
                        if (clipped.isNotBlank()) {
                            onIngestShareText(clipped)
                            platformHaptics.perform(PlatformHapticPattern.Send)
                        }
                    },
                ) { Text("Share in from clipboard") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

internal fun iosCatalogSubtitle(status: ModelCatalogStatus): String {
    val source = when (status.source) {
        ModelCatalogSource.BUNDLED -> "bundled"
        ModelCatalogSource.DOWNLOADED -> "downloaded"
    }
    val refreshing = if (status.isRefreshing) " · refreshing" else ""
    return "$source · ${status.entryCount} models · ${status.providerCount} providers$refreshing"
}
