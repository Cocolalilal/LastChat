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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import me.rerere.rikkahub.ui.core.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.core.components.chat.ActivityPillRow
import me.rerere.rikkahub.ui.core.components.chat.ActivityState
import me.rerere.rikkahub.ui.core.components.chat.ActivityType
import me.rerere.rikkahub.ui.core.components.chat.TimelineItem
import androidx.compose.ui.text.input.PasswordVisualTransformation
import me.rerere.lastchat.ios.models.IosBackupItem
import me.rerere.lastchat.ios.models.IosInjectionPosition
import me.rerere.lastchat.ios.models.IosLorebook
import me.rerere.lastchat.ios.models.IosLorebookActivationType
import me.rerere.lastchat.ios.models.IosLorebookEntry
import me.rerere.lastchat.ios.models.IosMcpCommonOptions
import me.rerere.lastchat.ios.models.IosMcpServerConfig
import me.rerere.lastchat.ios.models.IosMcpTool
import me.rerere.lastchat.ios.models.IosRestoreResult
import me.rerere.lastchat.ios.models.IosSkill
import me.rerere.lastchat.ios.models.IosWebDavBackupItem
import me.rerere.lastchat.ios.models.IosWebDavConfig
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationMethod
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformAttachmentOpener
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarMonth
import me.rerere.rikkahub.ui.components.chat.BubblePosition
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.ConversationRowSurface
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.TypingIndicator
import me.rerere.rikkahub.ui.components.chat.LastChatMessageTurn
import me.rerere.rikkahub.ui.components.chat.ToolCallPresentation
import me.rerere.rikkahub.ui.components.chat.AttachmentPresentation
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerDefaultActionContent
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddIcon
import me.rerere.rikkahub.ui.components.chat.LastChatComposerCapsule
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAttachmentRow
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
import me.rerere.rikkahub.ui.components.settings.PresetThemeButtonGroup
import me.rerere.rikkahub.ui.theme.rememberDefaultGenericalPainter
import me.rerere.lastchat.ios.ui.chat.ContextMeterAnchor
import me.rerere.lastchat.ios.ui.chat.IosContextUsage
import me.rerere.lastchat.ios.ui.chat.NewChatContent
import me.rerere.lastchat.ios.ui.menu.IosDrawerContent
import me.rerere.lastchat.ios.ui.assistant.IosAssistantDetailPage
import me.rerere.lastchat.ios.ui.settings.IosDisplayPage
import me.rerere.lastchat.ios.ui.settings.IosMcpPage
import me.rerere.lastchat.ios.ui.settings.IosStoragePage
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlinx.coroutines.launch

private enum class IosRoute { Chat, Settings, Statistics, ImageGeneration }

private enum class IosSettingsSection(val title: String) {
    Home("Settings"),
    Appearance("Display"),
    Assistant("Assistant"),
    Memory("Memory"),
    Tools("Tools"),
    PromptInjections("Prompt injections"),
    Mcp("MCP Servers"),
    Backup("Backup & restore"),
    Provider("Providers"),
    Models("Default model"),
    Search("Search service"),
    Tts("Text-to-speech"),
    Data("Data"),
    About("About"),
}

@Composable
fun LastChatIosApp(
    controller: IosAppController,
    platformHaptics: PlatformHaptics,
    filePicker: PlatformFilePicker,
    attachmentOpener: PlatformAttachmentOpener,
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
    val appFontFamily = if (state.appearance.usePhoneSystemFont) {
        FontFamily.Default
    } else {
        lastChatFontFamily
    }
    MaterialTheme(
        colorScheme = colorScheme.withLastChatAmoledSurface(useDarkTheme && state.appearance.amoledBlack),
        typography = buildLastChatTypography(appFontFamily),
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
                                onTogglePinConversation = controller::togglePinConversation,
                                onSelectAssistant = controller::selectAssistant,
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
                        onRegenerate = controller::regenerate,
                        onDeleteTurn = controller::deleteTurn,
                        onForkConversation = controller::forkConversation,
                        onNewChat = controller::newConversation,
                        onEditMessage = controller::editMessage,
                        onToggleSearch = controller::toggleSearch,
                        platformHaptics = platformHaptics,
                        attachmentOpener = attachmentOpener,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onOpenSettings = { route = IosRoute.Settings },
                    )
                }
                IosRoute.Settings -> SettingsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    onSaveProvider = controller::saveProvider,
                    onClearApiKey = controller::clearApiKey,
                    onSelectDefaultModel = controller::selectDefaultModel,
                    onSaveSearch = controller::saveSearch,
                    onClearSearchApiKey = controller::clearSearchApiKey,
                    onSaveTts = controller::saveTts,
                    onClearTtsApiKey = controller::clearTtsApiKey,
                    onSpeak = controller::speak,
                    onStopSpeaking = controller::stopTts,
                    onSaveImageGeneration = controller::saveImageGeneration,
                    onSaveAppearance = controller::saveAppearance,
                    onSaveFontSettings = controller::saveFontSettings,
                    onSaveUiCustomization = controller::saveUiCustomization,
                    onSaveRpStyleRules = controller::saveRpStyleRules,
                    onSaveAssistant = controller::saveAssistant,
                    onNewAssistant = controller::newAssistant,
                    onSelectAssistant = controller::selectAssistant,
                    onDeleteAssistant = controller::deleteAssistant,
                    onSaveMemorySettings = controller::saveMemorySettings,
                    onAddMemory = controller::addMemory,
                    onUpdateMemory = controller::updateMemory,
                    onDeleteMemory = controller::deleteMemory,
                    onRegenerateMemoryEmbeddings = controller::regenerateMemoryEmbeddings,
                    onSaveLocalTools = controller::saveLocalTools,
                    onSaveSkill = controller::saveSkill,
                    onDeleteSkill = controller::deleteSkill,
                    onToggleSkill = controller::toggleSkill,
                    onSaveLorebook = controller::saveLorebook,
                    onDeleteLorebook = controller::deleteLorebook,
                    onToggleLorebook = controller::toggleLorebook,
                    onSaveLorebookEntry = controller::saveLorebookEntry,
                    onDeleteLorebookEntry = controller::deleteLorebookEntry,
                    onSaveMcpServer = controller::saveMcpServer,
                    onDeleteMcpServer = controller::deleteMcpServer,
                    onToggleMcpServer = controller::toggleMcpServer,
                    onRefreshMcpTools = controller::refreshMcpTools,
                    onToggleMcpTool = controller::toggleMcpTool,
                    onUpdateWebDavConfig = controller::updateWebDavConfig,
                    onTestWebDav = controller::testWebDav,
                    onListWebDavBackups = controller::listWebDavBackups,
                    onBackupToWebDav = controller::backupToWebDav,
                    onRestoreFromWebDav = controller::restoreFromWebDav,
                    onExportBackup = controller::exportBackupToFile,
                    onRestorePickedBackup = controller::restoreFromPickedFile,
                    filePicker = filePicker,
                    attachmentOpener = attachmentOpener,
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
    }
}

private data class IosTurn(
    val startIndex: Int,
    val endIndex: Int,
    val role: MessageRole,
    val text: String,
    val toolCalls: List<ToolCallPresentation> = emptyList(),
    val attachments: List<AttachmentPresentation> = emptyList(),
    val senderName: String? = null,
    val avatarUrl: String? = null,
    val modelName: String? = null,
    val activityState: ActivityState = ActivityState.Hidden,
    val timelineItems: List<TimelineItem> = emptyList(),
)

private fun buildIosTurns(
    messages: List<UIMessage>,
    generating: Boolean,
    assistantName: String,
    assistantAvatar: String?,
    modelName: String,
): List<IosTurn> {
    if (messages.isEmpty()) return emptyList()

    val turns = mutableListOf<IosTurn>()
    var i = 0
    while (i < messages.size) {
        val currentMsg = messages[i]
        val isUser = currentMsg.role == MessageRole.USER
        val isSystem = currentMsg.role == MessageRole.SYSTEM

        if (isUser || isSystem) {
            val role = if (isSystem) MessageRole.SYSTEM else MessageRole.USER
            val rawText = currentMsg.toText()
            val attachments = mutableListOf<AttachmentPresentation>()
            GENERATED_MARKDOWN_IMAGE_REGEX.findAll(rawText).forEach { match ->
                attachments.add(AttachmentPresentation(match.groupValues[1], "Image", isImage = true))
            }
            currentMsg.parts.forEach { part ->
                when (part) {
                    is UIMessagePart.Image -> attachments.add(AttachmentPresentation(part.url, "Image", isImage = true))
                    is UIMessagePart.Video -> attachments.add(AttachmentPresentation(part.url, "Video", isImage = false))
                    is UIMessagePart.Audio -> attachments.add(AttachmentPresentation(part.url, "Audio", isImage = false))
                    is UIMessagePart.Document -> attachments.add(AttachmentPresentation(part.url, part.fileName, isImage = false))
                    else -> {}
                }
            }
            turns.add(
                IosTurn(
                    startIndex = i,
                    endIndex = i,
                    role = role,
                    text = rawText.replace(GENERATED_MARKDOWN_IMAGE_REGEX, "").trim(),
                    toolCalls = emptyList(),
                    attachments = attachments,
                    senderName = if (isUser) "You" else "System",
                    avatarUrl = null,
                    modelName = null,
                    activityState = ActivityState.Hidden,
                    timelineItems = emptyList(),
                )
            )
            i++
        } else {
            val turnStartIndex = i
            val turnMessages = mutableListOf<UIMessage>()
            while (i < messages.size && messages[i].role != MessageRole.USER && messages[i].role != MessageRole.SYSTEM) {
                turnMessages.add(messages[i])
                i++
            }
            val turnEndIndex = i - 1

            val textBuilder = StringBuilder()
            val allToolCalls = mutableListOf<UIMessagePart.ToolCall>()
            val allToolResults = mutableListOf<UIMessagePart.ToolResult>()
            val attachments = mutableListOf<AttachmentPresentation>()
            var hasReasoning = false
            var reasoningSnippet = ""

            for (msg in turnMessages) {
                val rawMsgText = msg.toText()
                GENERATED_MARKDOWN_IMAGE_REGEX.findAll(rawMsgText).forEach { match ->
                    attachments.add(AttachmentPresentation(match.groupValues[1], "Generated Image", isImage = true))
                }
                for (part in msg.parts) {
                    when (part) {
                        is UIMessagePart.Thinking -> {
                            if (part.thinking.isNotBlank()) {
                                hasReasoning = true
                                reasoningSnippet = part.thinking
                                textBuilder.append("<think>\n").append(part.thinking).append("\n</think>\n\n")
                            }
                        }
                        is UIMessagePart.Reasoning -> {
                            if (part.reasoning.isNotBlank()) {
                                hasReasoning = true
                                reasoningSnippet = part.reasoning
                                textBuilder.append("<think>\n").append(part.reasoning).append("\n</think>\n\n")
                            }
                        }
                        is UIMessagePart.Text -> {
                            textBuilder.append(part.text)
                        }
                        is UIMessagePart.ToolCall -> {
                            allToolCalls.add(part)
                        }
                        is UIMessagePart.ToolResult -> {
                            allToolResults.add(part)
                        }
                        is UIMessagePart.Image -> {
                            attachments.add(AttachmentPresentation(part.url, "Image", isImage = true))
                        }
                        is UIMessagePart.Video -> {
                            attachments.add(AttachmentPresentation(part.url, "Video", isImage = false))
                        }
                        is UIMessagePart.Audio -> {
                            attachments.add(AttachmentPresentation(part.url, "Audio", isImage = false))
                        }
                        is UIMessagePart.Document -> {
                            attachments.add(AttachmentPresentation(part.url, part.fileName, isImage = false))
                        }
                        else -> {}
                    }
                }
            }

            val isLastTurn = i >= messages.size
            val toolCallPresentations = allToolCalls.map { tc ->
                val matchingResult = allToolResults.firstOrNull { it.toolCallId == tc.toolCallId }
                    ?: allToolResults.firstOrNull { it.toolName == tc.toolName }
                val resultText = matchingResult?.content?.toString()
                val isLoading = isLastTurn && generating && matchingResult == null
                ToolCallPresentation(
                    name = tc.toolName,
                    arguments = tc.arguments,
                    result = resultText,
                    isLoading = isLoading,
                )
            }

            val timelineList = mutableListOf<TimelineItem>()
            if (hasReasoning) {
                timelineList.add(
                    TimelineItem(
                        title = "Reasoning",
                        description = reasoningSnippet.take(150),
                        type = ActivityType.REASONING,
                        isCompleted = !isLastTurn || !generating || textBuilder.isNotBlank() || toolCallPresentations.any { it.isLoading },
                    )
                )
            }
            toolCallPresentations.forEach { tc ->
                val type = when {
                    tc.name.contains("search", ignoreCase = true) -> ActivityType.SEARCH
                    tc.name.contains("memory", ignoreCase = true) -> ActivityType.MEMORY_RECALL
                    tc.name.startsWith("mcp", ignoreCase = true) -> ActivityType.MCP
                    else -> ActivityType.TOOL_OTHER
                }
                timelineList.add(
                    TimelineItem(
                        title = "Tool: ${tc.name}",
                        description = tc.arguments.take(150),
                        type = type,
                        isCompleted = !tc.isLoading,
                    )
                )
            }

            val nowMs = Clock.System.now().toEpochMilliseconds()
            val activityState: ActivityState = when {
                isLastTurn && generating -> {
                    val activeTool = toolCallPresentations.firstOrNull { it.isLoading }
                    when {
                        activeTool != null -> {
                            val toolType = when {
                                activeTool.name.contains("search", ignoreCase = true) -> ActivityType.SEARCH
                                activeTool.name.contains("memory", ignoreCase = true) -> ActivityType.MEMORY_RECALL
                                activeTool.name.startsWith("mcp", ignoreCase = true) -> ActivityType.MCP
                                else -> ActivityType.TOOL_OTHER
                            }
                            ActivityState.ToolUse(
                                toolName = activeTool.name,
                                displayName = activeTool.name,
                                startTimeMs = nowMs,
                                type = toolType,
                            )
                        }
                        hasReasoning && textBuilder.isBlank() -> {
                            ActivityState.Reasoning(
                                startTimeMs = nowMs,
                                reasoningText = reasoningSnippet,
                            )
                        }
                        textBuilder.isBlank() && toolCallPresentations.isEmpty() -> {
                            ActivityState.Waiting
                        }
                        timelineList.isNotEmpty() -> {
                            val types = timelineList.map { it.type }.distinct()
                            if (types.size == 1) {
                                ActivityState.CompletedSingle(types.first())
                            } else {
                                ActivityState.CompletedMultiple(activityTypes = types)
                            }
                        }
                        else -> ActivityState.Hidden
                    }
                }
                timelineList.isNotEmpty() -> {
                    val types = timelineList.map { it.type }.distinct()
                    if (types.size == 1) {
                        ActivityState.CompletedSingle(types.first())
                    } else {
                        ActivityState.CompletedMultiple(activityTypes = types)
                    }
                }
                else -> ActivityState.Hidden
            }

            val cleanedText = textBuilder.toString().replace(GENERATED_MARKDOWN_IMAGE_REGEX, "").trim()
            turns.add(
                IosTurn(
                    startIndex = turnStartIndex,
                    endIndex = turnEndIndex,
                    role = MessageRole.ASSISTANT,
                    text = cleanedText,
                    toolCalls = toolCallPresentations,
                    attachments = attachments,
                    senderName = assistantName,
                    avatarUrl = assistantAvatar,
                    modelName = modelName,
                    activityState = activityState,
                    timelineItems = timelineList,
                )
            )
        }
    }
    return turns
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onRegenerate: () -> Unit,
    onDeleteTurn: (Int, Int) -> Unit,
    onForkConversation: (Int) -> Unit,
    onNewChat: () -> Unit,
    onEditMessage: (Int, String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    platformHaptics: PlatformHaptics,
    attachmentOpener: PlatformAttachmentOpener,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val inputState = remember { TextFieldState() }
    var editingTurnIndex by remember { mutableStateOf<Int?>(null) }
    var editingTurnText by remember { mutableStateOf("") }
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
    val turns = remember(
        state.selectedConversation?.messages,
        state.generating,
        state.assistant.name,
        state.provider.modelId,
    ) {
        buildIosTurns(
            messages = state.selectedConversation?.messages.orEmpty(),
            generating = state.generating,
            assistantName = state.assistant.name,
            assistantAvatar = null,
            modelName = state.provider.modelId,
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
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            OneUITopAppBar(
                title = state.assistant.name,
                subtitle = state.provider.modelId,
                navigationIcon = {
                    LastChatMenuButton(
                        onClick = onOpenMenu,
                        contentDescription = "Messages",
                    )
                },
                actions = {
                    val estimatedTokens = remember(state.selectedConversation?.messages) {
                        val chars = state.selectedConversation?.messages?.sumOf { msg ->
                            msg.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }
                        } ?: 0
                        (chars / 4).coerceAtLeast(0)
                    }
                    val contextUsage = remember(estimatedTokens) {
                        IosContextUsage(
                            usedTokens = estimatedTokens,
                            totalTokens = 128000,
                            conversationTokens = estimatedTokens,
                        )
                    }
                    ContextMeterAnchor(
                        usage = contextUsage,
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                    )
                    IconButton(
                        onClick = {
                            platformHaptics.perform(PlatformHapticPattern.Pop)
                            onToggleSearch(!state.search.enabled)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Toggle search",
                            tint = if (state.search.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            platformHaptics.perform(PlatformHapticPattern.Pop)
                            onNewChat()
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "New chat")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                            // Inline chips for Model and Web Search
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 6.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = AppShapes.ButtonPill,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        onOpenSettings()
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = state.provider.modelId,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Surface(
                                    shape = AppShapes.ButtonPill,
                                    color = if (state.search.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        onToggleSearch(!state.search.enabled)
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (state.search.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = if (state.search.enabled) "Search ON" else "Search",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (state.search.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
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
                                    inputState.text.isNotBlank() || state.pendingAttachments.isNotEmpty() ->
                                        LastChatComposerAction.Send
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
                                            LastChatComposerAction.Picker -> {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onOpenSettings()
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
                                                text = state.provider.modelId.firstOrNull()?.uppercase() ?: "M",
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (turns.isEmpty()) {
                item {
                    NewChatContent(
                        assistantName = state.assistant.name,
                        onTemplateClick = { prompt ->
                            inputState.setTextAndPlaceCursorAtEnd(prompt)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 20.dp),
                    )
                }
            } else {
                items(turns, key = { it.startIndex }) { turn ->
                    LastChatMessageTurn(
                        role = turn.role,
                        text = turn.text,
                        senderName = turn.senderName,
                        avatarUrl = turn.avatarUrl,
                        modelName = turn.modelName,
                        toolCalls = turn.toolCalls,
                        attachments = turn.attachments,
                        activityState = turn.activityState,
                        timelineItems = turn.timelineItems,
                        onRegenerate = if (turn.role == MessageRole.ASSISTANT) onRegenerate else null,
                        onEdit = {
                            editingTurnIndex = turn.startIndex
                            editingTurnText = turn.text
                        },
                        onFork = { onForkConversation(turn.endIndex) },
                        onDelete = { onDeleteTurn(turn.startIndex, turn.endIndex) },
                        isSpeakingTts = state.ttsSpeaking,
                        onToggleTts = if (turn.role == MessageRole.ASSISTANT && state.tts.enabled && state.hasTtsApiKey) {
                            {
                                if (state.ttsSpeaking) onStopSpeaking() else onSpeak(turn.text)
                            }
                        } else null,
                        onAttachmentClick = { uri -> attachmentOpener.open(uri) },
                        fontSizeRatio = state.appearance.fontSizeRatio,
                        showAssistantBubble = state.appearance.showAssistantBubbles,
                    )
                }
            }
            if (state.generating && (turns.isEmpty() || turns.last().role == MessageRole.USER || (turns.last().text.isBlank() && turns.last().toolCalls.none { it.isLoading } && turns.last().activityState == ActivityState.Hidden))) {
                item {
                    GroupedMessageBubble(
                        position = BubblePosition.SINGLE,
                        role = BubbleRole.ACTIVITY,
                    ) { TypingIndicator() }
                }
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    editingTurnIndex?.let { editIdx ->
        AlertDialog(
            onDismissRequest = { editingTurnIndex = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editingTurnText,
                    onValueChange = { editingTurnText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditMessage(editIdx, editingTurnText)
                        platformHaptics.perform(PlatformHapticPattern.Pop)
                        editingTurnIndex = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTurnIndex = null }) {
                    Text("Cancel")
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPage(
    state: IosAppState,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onSelectAssistant: (String) -> Unit,
    darkTheme: Boolean,
    platformHaptics: PlatformHaptics,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
    onImageGeneration: () -> Unit,
) {
    IosDrawerContent(
        state = state,
        onSelectConversation = onSelectConversation,
        onRenameConversation = onRenameConversation,
        onDeleteConversation = onDeleteConversation,
        onTogglePinConversation = onTogglePinConversation,
        onSelectAssistant = onSelectAssistant,
        onDismiss = onDismiss,
        onSettings = onSettings,
        onStatistics = onStatistics,
        onImageGeneration = onImageGeneration,
        onAssistantDetail = onSettings,
        onHapticPop = { platformHaptics.perform(PlatformHapticPattern.Pop) },
        onHapticTick = { platformHaptics.perform(PlatformHapticPattern.Tick) },
    )
}

@Composable
private fun IosAssistantAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val isGenerical = name.equals("Generical", ignoreCase = true) || name.isBlank() || name.equals("Default", ignoreCase = true)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        if (isGenerical) {
            androidx.compose.foundation.Image(
                painter = rememberDefaultGenericalPainter(),
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "A",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
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
        state.imageGeneration.providerType != IosImageProviderType.COMFY_UI
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
    val messages = state.conversations.flatMap { it.messages }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val heatmapData = remember(messages) {
        messages
            .groupingBy { message -> message.createdAt.date }
            .eachCount()
            .map { (date, count) -> CalendarHeatmapDay(date, count) }
            .sortedBy { it.date }
    }
    val promptTokens = messages.sumOf { it.usage?.promptTokens?.toLong() ?: 0L }
    val completionTokens = messages.sumOf { it.usage?.completionTokens?.toLong() ?: 0L }
    val cachedTokens = messages.sumOf { it.usage?.cachedTokens?.toLong() ?: 0L }
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
                    showEmptyState = messages.isEmpty(),
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
                    value = formatCompactCount(state.conversations.size.toLong()),
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
                        value = formatCompactCount(messages.size.toLong()),
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
    isPinned: Boolean = false,
    platformHaptics: PlatformHaptics,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title.ifBlank { "New chat" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = AppShapes.ButtonRounded,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin chat" else "Pin to top") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    showMenu = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("Edit title") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    editedTitle = title
                    showMenu = false
                    showEditTitle = true
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveProvider: (IosProviderType, String, String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onSelectDefaultModel: (IosProviderType, String) -> Unit,
    onSaveSearch: (IosSearchProviderType, Boolean, Int, String) -> Unit,
    onClearSearchApiKey: () -> Unit,
    onSaveTts: (IosTtsPreferences, String) -> Unit,
    onClearTtsApiKey: () -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onSaveImageGeneration: (IosImageGenerationPreferences) -> Unit,
    onSaveAppearance: (String, IosColorMode, Boolean, Boolean) -> Unit,
    onSaveFontSettings: (Boolean) -> Unit,
    onSaveUiCustomization: (Boolean, Float) -> Unit,
    onSaveRpStyleRules: (List<IosRpStyleRule>) -> Unit,
    onSaveAssistant: (String, String) -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onSaveMemorySettings: (IosMemoryMode, IosProviderType, String, Float, Int) -> Unit,
    onAddMemory: (String) -> Unit,
    onUpdateMemory: (Int, String) -> Unit,
    onDeleteMemory: (Int) -> Unit,
    onRegenerateMemoryEmbeddings: () -> Unit,
    onSaveLocalTools: (Set<IosLocalToolOption>) -> Unit,
    onSaveSkill: (IosSkill) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onSaveLorebook: (IosLorebook) -> Unit,
    onDeleteLorebook: (String) -> Unit,
    onToggleLorebook: (String, Boolean) -> Unit,
    onSaveLorebookEntry: (String, IosLorebookEntry) -> Unit,
    onDeleteLorebookEntry: (String, String) -> Unit,
    onSaveMcpServer: (IosMcpServerConfig) -> Unit,
    onDeleteMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpTools: suspend (IosMcpServerConfig) -> Result<List<IosMcpTool>>,
    onToggleMcpTool: (String, String, Boolean) -> Unit,
    onUpdateWebDavConfig: (IosWebDavConfig) -> Unit,
    onTestWebDav: suspend (IosWebDavConfig) -> Result<Unit>,
    onListWebDavBackups: suspend (IosWebDavConfig) -> Result<List<IosWebDavBackupItem>>,
    onBackupToWebDav: suspend (IosWebDavConfig) -> Result<Unit>,
    onRestoreFromWebDav: suspend (IosWebDavConfig, String) -> Result<IosRestoreResult>,
    onExportBackup: suspend () -> Result<PlatformPickedFile>,
    onRestorePickedBackup: suspend (PlatformPickedFile) -> Result<IosRestoreResult>,
    filePicker: PlatformFilePicker,
    attachmentOpener: PlatformAttachmentOpener,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(IosSettingsSection.Home) }
    var activeDestinationId by remember { mutableStateOf("") }
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
    var ttsPreferences by remember(state.tts) { mutableStateOf(state.tts) }
    var imageGeneration by remember(state.imageGeneration) { mutableStateOf(state.imageGeneration) }
    var ttsApiKey by remember { mutableStateOf("") }
    var ttsSpeed by remember(state.tts.speed) { mutableStateOf(state.tts.speed.toString()) }
    var providerType by remember(state.provider.type) { mutableStateOf(state.provider.type) }
    var baseUrl by remember(state.provider.baseUrl) { mutableStateOf(state.provider.baseUrl) }
    var modelId by remember(state.provider.modelId) { mutableStateOf(state.provider.modelId) }
    var apiKey by remember { mutableStateOf("") }
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
    var rpStyleRules by remember(state.appearance.rpStyleRules) {
        mutableStateOf(state.appearance.rpStyleRules)
    }
    var editingRpStyleRule by remember { mutableStateOf<IosRpStyleRule?>(null) }
    var showAddRpStyleRuleDialog by remember { mutableStateOf(false) }
    var assistantName by remember(state.assistant.name) { mutableStateOf(state.assistant.name) }
    var systemPrompt by remember(state.assistant.systemPrompt) { mutableStateOf(state.assistant.systemPrompt) }
    var memoryMode by remember(state.assistant.memoryMode) { mutableStateOf(state.assistant.memoryMode) }
    var embeddingProviderType by remember(state.assistant.embeddingProviderType) {
        mutableStateOf(state.assistant.embeddingProviderType)
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

    var promptInjectionTab by remember { mutableStateOf(0) }
    var showAddSkillDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<IosSkill?>(null) }
    var showAddLorebookDialog by remember { mutableStateOf(false) }
    var editingLorebook by remember { mutableStateOf<IosLorebook?>(null) }
    var expandedLorebookId by remember { mutableStateOf<String?>(null) }
    var showAddLorebookEntryDialog by remember { mutableStateOf(false) }
    var editingLorebookEntry by remember { mutableStateOf<Pair<String, IosLorebookEntry?>?>(null) }

    var showAddMcpServerDialog by remember { mutableStateOf(false) }
    var editingMcpServer by remember { mutableStateOf<IosMcpServerConfig?>(null) }
    var mcpRefreshingServerId by remember { mutableStateOf<String?>(null) }
    var mcpStatusMessage by remember { mutableStateOf<String?>(null) }

    var backupTab by remember { mutableStateOf(0) }
    var webDavUrl by remember(state.webDavConfig.url) { mutableStateOf(state.webDavConfig.url) }
    var webDavPath by remember(state.webDavConfig.path) { mutableStateOf(state.webDavConfig.path) }
    var webDavUser by remember(state.webDavConfig.user) { mutableStateOf(state.webDavConfig.user) }
    var webDavPass by remember(state.webDavConfig.pass) { mutableStateOf(state.webDavConfig.pass) }
    var webDavStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingWebDav by remember { mutableStateOf(false) }
    var isBackingUpWebDav by remember { mutableStateOf(false) }
    var isLoadingRemoteBackups by remember { mutableStateOf(false) }
    var remoteBackupsList by remember { mutableStateOf<List<IosWebDavBackupItem>>(emptyList()) }
    var restoreResultDialog by remember { mutableStateOf<IosRestoreResult?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    fun openSettingsDestination(destinationId: String, title: String) {
        activeDestinationId = destinationId
        when (destinationId) {
            "Display", "Fonts", "UiCustomization", "RpOptimizations" ->
                section = IosSettingsSection.Appearance
            "Assistants" -> section = IosSettingsSection.Assistant
            "AssistantMemory" -> section = IosSettingsSection.Memory
            "AssistantTools" -> section = IosSettingsSection.Tools
            "PromptInjections", "Skills", "Lorebooks" -> {
                section = IosSettingsSection.PromptInjections
                if (destinationId == "Lorebooks") promptInjectionTab = 1
                else if (destinationId == "Skills") promptInjectionTab = 0
            }
            "Mcp" -> section = IosSettingsSection.Mcp
            "Backup", "BackupWebDav", "BackupLocal" -> {
                section = IosSettingsSection.Backup
                if (destinationId == "BackupLocal") backupTab = 1
                else if (destinationId == "BackupWebDav") backupTab = 0
            }
            "Providers", "ProviderModels" -> section = IosSettingsSection.Provider
            "Models" -> section = IosSettingsSection.Models
            "Search" -> section = IosSettingsSection.Search
            "Tts" -> section = IosSettingsSection.Tts
            "ChatStorage" -> section = IosSettingsSection.Data
            "About" -> section = IosSettingsSection.About
            else -> {}
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
            IosSettingsSection.PromptInjections -> if (promptInjectionTab == 1) "Lorebooks" else "Skills"
            IosSettingsSection.Mcp -> "Mcp"
            IosSettingsSection.Backup -> if (backupTab == 1) "BackupLocal" else "BackupWebDav"
            IosSettingsSection.Provider -> activeDestinationId.ifBlank { "Providers" }
            IosSettingsSection.Models -> "Models"
            IosSettingsSection.Search -> "Search"
            IosSettingsSection.Tts -> "Tts"
            IosSettingsSection.Data -> activeDestinationId.ifBlank { "ChatStorage" }
            IosSettingsSection.About -> "About"
            IosSettingsSection.Home -> ""
        }
        val selectedMainId = iosSettingsMainDestination(selectedPaneId)
        val paneGroups = remember { iosSettingsPaneGroups() }
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
                            title = paneGroups.titleFor(destinationId) ?: "",
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
                    if (
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
                            icon = { Icon(Icons.Rounded.Extension, null, Modifier.size(20.dp)) },
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
                            title = "MCP Servers",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Extension, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Mcp", "MCP Servers") },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "Data settings") {
                        LastChatSettingGroupItem(
                            title = "Data backup",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.CloudUpload, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { openSettingsDestination("Backup", "Data backup") },
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
                    }
                }
            }
            if (section == IosSettingsSection.Assistant) {
                item {
                    IosAssistantDetailPage(
                        state = state,
                        darkTheme = darkTheme,
                        onSaveAssistant = onSaveAssistant,
                        onNewAssistant = onNewAssistant,
                        onSelectAssistant = onSelectAssistant,
                        onDeleteAssistant = onDeleteAssistant,
                        onNavigateToMemory = { openSettingsDestination("AssistantMemory", "Memory") },
                        onNavigateToTools = { openSettingsDestination("AssistantTools", "Tools") },
                        onNavigateToModels = { openSettingsDestination("Models", "Models") },
                        onBack = { section = IosSettingsSection.Home },
                        onHapticPop = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                    )
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(IosProviderType.OPENAI, IosProviderType.GOOGLE).forEach { type ->
                                            if (embeddingProviderType == type) {
                                                Button(onClick = {}) { Text(type.displayName()) }
                                            } else {
                                                TextButton(onClick = {
                                                    embeddingProviderType = type
                                                    embeddingModelId = if (type == IosProviderType.OPENAI) {
                                                        "text-embedding-3-small"
                                                    } else {
                                                        "text-embedding-004"
                                                    }
                                                }) { Text(type.displayName()) }
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
                                embeddingProviderType,
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
                    }
                }
            }
            if (section == IosSettingsSection.PromptInjections) {
                item {
                    IosTabRow(
                        tabs = listOf(
                            "Skills (${state.skills.size})",
                            "Lorebooks (${state.lorebooks.size})"
                        ),
                        selectedIndex = promptInjectionTab,
                        onSelect = {
                            platformHaptics.perform(PlatformHapticPattern.Tick)
                            promptInjectionTab = it
                        },
                    )
                }
                if (promptInjectionTab == 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Skills",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Button(
                                onClick = {
                                    platformHaptics.perform(PlatformHapticPattern.Pop)
                                    editingSkill = null
                                    showAddSkillDialog = true
                                },
                                shape = AppShapes.ButtonPill,
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Skill")
                            }
                        }
                    }
                    if (state.skills.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.CardMedium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Rounded.Category,
                                        null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No skills configured",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Skills inject specialized system instructions or tool directives into conversations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.skills) { skill ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.CardMedium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (skill.enabled) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                skill.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (skill.description.isNotBlank()) {
                                                Text(
                                                    skill.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = skill.enabled,
                                            onCheckedChange = { checked ->
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onToggleSkill(skill.id, checked)
                                            },
                                        )
                                    }
                                    if (skill.instructions.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = AppShapes.CardSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                skill.instructions,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(8.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            shape = AppShapes.ButtonPill,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                skill.injectionPosition.displayName(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                editingSkill = skill
                                                showAddSkillDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Edit, "Edit", Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onDeleteSkill(skill.id)
                                            }) {
                                                Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Lorebooks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Button(
                                onClick = {
                                    platformHaptics.perform(PlatformHapticPattern.Pop)
                                    editingLorebook = null
                                    showAddLorebookDialog = true
                                },
                                shape = AppShapes.ButtonPill,
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Lorebook")
                            }
                        }
                    }
                    if (state.lorebooks.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.CardMedium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Rounded.Book,
                                        null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No lorebooks configured",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Lorebooks automatically inject context when keywords are mentioned in conversation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.lorebooks) { lb ->
                            val isExpanded = expandedLorebookId == lb.id
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.CardMedium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (lb.enabled) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                lb.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (lb.description.isNotBlank()) {
                                                Text(
                                                    lb.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = lb.enabled,
                                            onCheckedChange = { checked ->
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onToggleLorebook(lb.id, checked)
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Tick)
                                                expandedLorebookId = if (isExpanded) null else lb.id
                                            },
                                        ) {
                                            Text("${lb.entries.size} entries ${if (isExpanded) "▲" else "▼"}")
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                editingLorebookEntry = lb.id to null
                                                showAddLorebookEntryDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Add, "Add entry", Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                editingLorebook = lb
                                                showAddLorebookDialog = true
                                            }) {
                                                Icon(Icons.Rounded.Edit, "Edit", Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onDeleteLorebook(lb.id)
                                            }) {
                                                Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    if (isExpanded) {
                                        Spacer(Modifier.height(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            lb.entries.forEach { entry ->
                                                Surface(
                                                    shape = AppShapes.CardSmall,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                entry.name,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(
                                                                    onClick = {
                                                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                                                        editingLorebookEntry = lb.id to entry
                                                                        showAddLorebookEntryDialog = true
                                                                    },
                                                                    modifier = Modifier.size(32.dp),
                                                                ) {
                                                                    Icon(Icons.Rounded.Edit, "Edit entry", Modifier.size(16.dp))
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                                                        onDeleteLorebookEntry(lb.id, entry.id)
                                                                    },
                                                                    modifier = Modifier.size(32.dp),
                                                                ) {
                                                                    Icon(Icons.Rounded.Delete, "Delete entry", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                                }
                                                            }
                                                        }
                                                        if (entry.keywords.isNotEmpty()) {
                                                            Text(
                                                                "Keywords: ${entry.keywords.joinToString(", ")}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                        Text(
                                                            entry.prompt,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Mcp) {
                item {
                    IosMcpPage(
                        servers = state.mcpServers,
                        onSaveMcpServer = onSaveMcpServer,
                        onDeleteMcpServer = onDeleteMcpServer,
                        onToggleMcpServer = onToggleMcpServer,
                        onRefreshMcpTools = onRefreshMcpTools,
                        onToggleMcpTool = onToggleMcpTool,
                        onHapticPop = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        onHapticTick = { platformHaptics.perform(PlatformHapticPattern.Tick) },
                        onHapticSuccess = { platformHaptics.perform(PlatformHapticPattern.Success) },
                        onHapticError = { platformHaptics.perform(PlatformHapticPattern.Error) },
                    )
                }
            }
            if (section == IosSettingsSection.Models) {
                item {
                    LastChatSettingsGroup(
                        title = "Conversation",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
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
                                                    state.provider.modelId.firstOrNull()
                                                        ?.uppercase() ?: "M",
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                        }
                                        Text(
                                            state.provider.modelId,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                        )
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
                            LastChatFormItem(
                                label = { Text("Provider") },
                                description = {
                                    Text(
                                        if (imageGeneration.providerType == IosImageProviderType.COMFY_UI) {
                                            "Runs the imported API workflow on your ComfyUI server"
                                        } else {
                                            "Uses the provider endpoint and Keychain key configured above"
                                        }
                                    )
                                },
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IosImageProviderType.entries.forEach { type ->
                                        if (imageGeneration.providerType == type) {
                                            Button(onClick = {}) { Text(type.displayName()) }
                                        } else {
                                            TextButton(onClick = {
                                                imageGeneration = imageGeneration.copy(
                                                    providerType = type,
                                                    modelId = when (type) {
                                                        IosImageProviderType.OPENAI -> "gpt-image-1"
                                                        IosImageProviderType.GOOGLE -> "imagen-3.0-generate-002"
                                                        IosImageProviderType.COMFY_UI -> "model.safetensors"
                                                    },
                                                    method = if (type == IosImageProviderType.COMFY_UI) {
                                                        ImageGenerationMethod.DIFFUSION
                                                    } else {
                                                        imageGeneration.method
                                                    },
                                                )
                                            }) { Text(type.displayName()) }
                                        }
                                    }
                                }
                            }
                            if (imageGeneration.providerType != IosImageProviderType.COMFY_UI) {
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
                                                    imageGeneration = imageGeneration.copy(
                                                        method = method,
                                                        modelId = when {
                                                            method == ImageGenerationMethod.MULTIMODAL &&
                                                                imageGeneration.providerType == IosImageProviderType.GOOGLE ->
                                                                "gemini-2.0-flash-preview-image-generation"
                                                            method == ImageGenerationMethod.MULTIMODAL -> "gpt-4o"
                                                            imageGeneration.providerType == IosImageProviderType.GOOGLE ->
                                                                "imagen-3.0-generate-002"
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
                            if (imageGeneration.providerType == IosImageProviderType.COMFY_UI) {
                                LastChatFormItem(label = { Text("Base URL") }) {
                                    OutlinedTextField(
                                        value = imageGeneration.comfyUi.baseUrl,
                                        onValueChange = { value ->
                                            imageGeneration = imageGeneration.copy(
                                                comfyUi = imageGeneration.comfyUi.copy(baseUrl = value)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        singleLine = true,
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("API workflow JSON") },
                                    description = { Text("Export the workflow in ComfyUI's API format") },
                                ) {
                                    OutlinedTextField(
                                        value = imageGeneration.comfyUi.workflowJson,
                                        onValueChange = { value ->
                                            imageGeneration = imageGeneration.copy(
                                                comfyUi = imageGeneration.comfyUi.copy(workflowJson = value)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = AppShapes.InputField,
                                        minLines = 6,
                                        maxLines = 14,
                                    )
                                }
                                LastChatFormItem(
                                    label = { Text("Prompt node") },
                                    description = { Text("May be blank when the workflow has one detectable text node") },
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = imageGeneration.comfyUi.promptNodeId,
                                            onValueChange = { value ->
                                                imageGeneration = imageGeneration.copy(
                                                    comfyUi = imageGeneration.comfyUi.copy(promptNodeId = value)
                                                )
                                            },
                                            label = { Text("Node ID") },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = imageGeneration.comfyUi.promptInputName,
                                            onValueChange = { value ->
                                                imageGeneration = imageGeneration.copy(
                                                    comfyUi = imageGeneration.comfyUi.copy(promptInputName = value)
                                                )
                                            },
                                            label = { Text("Input") },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                    }
                                }
                                LastChatFormItem(
                                    label = { Text("Checkpoint node") },
                                    description = { Text("May be blank when the workflow has one detectable loader node") },
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = imageGeneration.comfyUi.modelNodeId,
                                            onValueChange = { value ->
                                                imageGeneration = imageGeneration.copy(
                                                    comfyUi = imageGeneration.comfyUi.copy(modelNodeId = value)
                                                )
                                            },
                                            label = { Text("Node ID") },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = imageGeneration.comfyUi.modelInputName,
                                            onValueChange = { value ->
                                                imageGeneration = imageGeneration.copy(
                                                    comfyUi = imageGeneration.comfyUi.copy(modelInputName = value)
                                                )
                                            },
                                            label = { Text("Input") },
                                            modifier = Modifier.weight(1f),
                                            shape = AppShapes.InputField,
                                            singleLine = true,
                                        )
                                    }
                                }
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
                item {
                    LastChatSettingsGroup(
                        title = "Configuration",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = providerType.displayName(),
                            subtitle = "Provider endpoint, model, and secure credentials",
                            darkTheme = darkTheme,
                        ) {
                            LastChatFormItem(label = { Text("Provider type") }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    IosProviderType.entries.forEach { type ->
                                        if (type == providerType) {
                                            Button(onClick = {}) { Text(type.displayName()) }
                                        } else {
                                            TextButton(onClick = {
                                                providerType = type
                                                val saved = state.providerConfigurations.firstOrNull {
                                                    it.type == type
                                                }
                                                baseUrl = saved?.baseUrl ?: type.defaultBaseUrl()
                                                modelId = saved?.modelId ?: type.defaultModelId()
                                                apiKey = ""
                                            }) { Text(type.displayName()) }
                                        }
                                    }
                                }
                            }
                            LastChatFormItem(label = { Text("Base URL") }) {
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            LastChatFormItem(label = { Text("Model ID") }) {
                                OutlinedTextField(
                                    value = modelId,
                                    onValueChange = { modelId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            LastChatFormItem(
                                label = {
                                    Text(
                                        if (state.hasApiKey && providerType == state.provider.type) {
                                            "API key (saved in Keychain)"
                                        } else {
                                            "API key"
                                        }
                                    )
                                },
                                description = {
                                    if (state.hasApiKey && providerType == state.provider.type) {
                                        Text("Leave blank to keep the current key")
                                    }
                                },
                            ) {
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.InputField,
                                    singleLine = true,
                                )
                            }
                            Button(
                                onClick = {
                                    onSaveProvider(providerType, baseUrl, modelId, apiKey)
                                    apiKey = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save provider") }
                            if (state.hasApiKey && providerType == state.provider.type) {
                                TextButton(onClick = onClearApiKey) {
                                    Text("Remove saved API key")
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
                            if (searchProvider != IosSearchProviderType.BING) {
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
                                    )
                                    searchApiKey = ""
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
                            if (ttsPreferences.type != IosTtsProviderType.ELEVENLABS) {
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
                            if (state.hasTtsApiKey) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        if (state.ttsSpeaking) {
                                            onStopSpeaking()
                                        } else {
                                            onSpeak("Hello! This is a test of the text to speech engine.")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        if (state.ttsSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.ttsSpeaking) "Stop audition" else "Audition voice")
                                }
                            }
                            if (state.hasTtsApiKey && ttsPreferences.type == state.tts.type) {
                                TextButton(onClick = onClearTtsApiKey) {
                                    Text("Remove saved API key")
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Appearance) {
                item {
                    IosDisplayPage(
                        appearance = state.appearance,
                        darkTheme = darkTheme,
                        onSaveAppearance = onSaveAppearance,
                        onSaveFontSettings = onSaveFontSettings,
                        onSaveUiCustomization = onSaveUiCustomization,
                        onHapticPop = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                    )
                }
            }
            if (section == IosSettingsSection.Backup) {
                item {
                    IosTabRow(
                        tabs = listOf("WebDAV Cloud", "Local File"),
                        selectedIndex = backupTab,
                        onSelect = {
                            platformHaptics.perform(PlatformHapticPattern.Tick)
                            backupTab = it
                        },
                    )
                }
                if (backupTab == 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.CardMedium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "WebDAV Server Settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                OutlinedTextField(
                                    value = webDavUrl,
                                    onValueChange = { webDavUrl = it },
                                    label = { Text("Server URL") },
                                    placeholder = { Text("https://dav.example.com/") },
                                    singleLine = true,
                                    shape = AppShapes.InputField,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = webDavPath,
                                    onValueChange = { webDavPath = it },
                                    label = { Text("Remote Path") },
                                    placeholder = { Text("LastChat") },
                                    singleLine = true,
                                    shape = AppShapes.InputField,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = webDavUser,
                                    onValueChange = { webDavUser = it },
                                    label = { Text("Username") },
                                    singleLine = true,
                                    shape = AppShapes.InputField,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = webDavPass,
                                    onValueChange = { webDavPass = it },
                                    label = { Text("Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = AppShapes.InputField,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (webDavStatusMessage != null) {
                                    Surface(
                                        shape = AppShapes.CardSmall,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            webDavStatusMessage.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            platformHaptics.perform(PlatformHapticPattern.Tick)
                                            val cfg = IosWebDavConfig(
                                                url = webDavUrl,
                                                path = webDavPath,
                                                user = webDavUser,
                                                pass = webDavPass,
                                            )
                                            onUpdateWebDavConfig(cfg)
                                            isTestingWebDav = true
                                            webDavStatusMessage = "Testing WebDAV connection..."
                                            scope.launch {
                                                onTestWebDav(cfg).onSuccess {
                                                    platformHaptics.perform(PlatformHapticPattern.Success)
                                                    webDavStatusMessage = "WebDAV connection successful!"
                                                }.onFailure { err ->
                                                    platformHaptics.perform(PlatformHapticPattern.Error)
                                                    webDavStatusMessage = "Connection failed: ${err.message}"
                                                }
                                                isTestingWebDav = false
                                            }
                                        },
                                        enabled = !isTestingWebDav,
                                        modifier = Modifier.weight(1f),
                                        shape = AppShapes.ButtonPill,
                                    ) {
                                        if (isTestingWebDav) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text("Test")
                                    }
                                    Button(
                                        onClick = {
                                            platformHaptics.perform(PlatformHapticPattern.Pop)
                                            val cfg = IosWebDavConfig(
                                                url = webDavUrl,
                                                path = webDavPath,
                                                user = webDavUser,
                                                pass = webDavPass,
                                            )
                                            onUpdateWebDavConfig(cfg)
                                            webDavStatusMessage = "WebDAV configuration saved"
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = AppShapes.ButtonPill,
                                    ) {
                                        Text("Save Config")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.CardMedium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "WebDAV Cloud Backups",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        val cfg = IosWebDavConfig(
                                            url = webDavUrl,
                                            path = webDavPath,
                                            user = webDavUser,
                                            pass = webDavPass,
                                        )
                                        onUpdateWebDavConfig(cfg)
                                        isBackingUpWebDav = true
                                        webDavStatusMessage = "Uploading backup archive to WebDAV..."
                                        scope.launch {
                                            onBackupToWebDav(cfg).onSuccess {
                                                platformHaptics.perform(PlatformHapticPattern.Success)
                                                webDavStatusMessage = "Backup successfully uploaded to WebDAV!"
                                                onListWebDavBackups(cfg).getOrNull()?.let { remoteBackupsList = it }
                                            }.onFailure { err ->
                                                platformHaptics.perform(PlatformHapticPattern.Error)
                                                webDavStatusMessage = "Backup failed: ${err.message}"
                                            }
                                            isBackingUpWebDav = false
                                        }
                                    },
                                    enabled = !isBackingUpWebDav,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    if (isBackingUpWebDav) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("Backup to WebDAV Now")
                                }
                                Button(
                                    onClick = {
                                        platformHaptics.perform(PlatformHapticPattern.Tick)
                                        val cfg = IosWebDavConfig(
                                            url = webDavUrl,
                                            path = webDavPath,
                                            user = webDavUser,
                                            pass = webDavPass,
                                        )
                                        isLoadingRemoteBackups = true
                                        scope.launch {
                                            onListWebDavBackups(cfg).onSuccess { items ->
                                                platformHaptics.perform(PlatformHapticPattern.Success)
                                                remoteBackupsList = items
                                                webDavStatusMessage = "Found ${items.size} backups on WebDAV"
                                            }.onFailure { err ->
                                                platformHaptics.perform(PlatformHapticPattern.Error)
                                                webDavStatusMessage = "Failed to list backups: ${err.message}"
                                            }
                                            isLoadingRemoteBackups = false
                                        }
                                    },
                                    enabled = !isLoadingRemoteBackups,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    if (isLoadingRemoteBackups) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("Check Remote Backups")
                                }
                                if (remoteBackupsList.isNotEmpty()) {
                                    Text(
                                        "Remote Backups (${remoteBackupsList.size}):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        remoteBackupsList.forEach { item ->
                                            Surface(
                                                shape = AppShapes.CardSmall,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            item.displayName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                    Button(
                                                        onClick = {
                                                            platformHaptics.perform(PlatformHapticPattern.Pop)
                                                            val cfg = IosWebDavConfig(
                                                                url = webDavUrl,
                                                                path = webDavPath,
                                                                user = webDavUser,
                                                                pass = webDavPass,
                                                            )
                                                            isRestoring = true
                                                            scope.launch {
                                                                onRestoreFromWebDav(cfg, item.href).onSuccess { res ->
                                                                    platformHaptics.perform(PlatformHapticPattern.Success)
                                                                    restoreResultDialog = res
                                                                }.onFailure { err ->
                                                                    platformHaptics.perform(PlatformHapticPattern.Error)
                                                                    webDavStatusMessage = "Restore failed: ${err.message}"
                                                                }
                                                                isRestoring = false
                                                            }
                                                        },
                                                        enabled = !isRestoring,
                                                        shape = AppShapes.ButtonPill,
                                                    ) {
                                                        Text("Restore")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.CardMedium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.FileUpload,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Export Backup Archive",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Creates a standard .zip backup package containing all conversations, assistants, memories, skills, lorebooks, and provider settings. You can save to iOS Files, AirDrop to a Mac, or restore on Android LastChat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        scope.launch {
                                            onExportBackup().onSuccess { picked ->
                                                platformHaptics.perform(PlatformHapticPattern.Success)
                                                attachmentOpener.open(picked.localUrl)
                                            }.onFailure { err ->
                                                platformHaptics.perform(PlatformHapticPattern.Error)
                                                webDavStatusMessage = "Export failed: ${err.message}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export Complete Backup")
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.CardMedium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.CloudUpload,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Restore Backup Archive",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Select an Android or iOS LastChat backup file (.zip or .json) from your device files to restore chats, assistants, prompt injections, and settings 1:1.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        filePicker.pickFile { pickedResult ->
                                            val picked = pickedResult.getOrNull() ?: return@pickFile
                                            scope.launch {
                                                isRestoring = true
                                                onRestorePickedBackup(picked).onSuccess { res ->
                                                    platformHaptics.perform(PlatformHapticPattern.Success)
                                                    restoreResultDialog = res
                                                }.onFailure { err ->
                                                    platformHaptics.perform(PlatformHapticPattern.Error)
                                                    webDavStatusMessage = "Restore failed: ${err.message}"
                                                }
                                                isRestoring = false
                                            }
                                        }
                                    },
                                    enabled = !isRestoring,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    if (isRestoring) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text("Select File to Restore")
                                }
                            }
                        }
                    }
                }
            }
            if (section == IosSettingsSection.Data) {
                item {
                    IosStoragePage(
                        state = state,
                        darkTheme = darkTheme,
                        onBack = { section = IosSettingsSection.Home },
                        onHapticPop = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        onHapticThud = { platformHaptics.perform(PlatformHapticPattern.Thud) },
                    )
                }
            }
            if (section == IosSettingsSection.About) {
                item {
                    val platformInfo = currentIosPlatformInfo()
                    LastChatAboutContent(
                        appName = "LastChat",
                        versionName = "1.4.5",
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
        if (showAddSkillDialog || editingSkill != null) {
            val current = editingSkill
            IosSkillDialog(
                skill = current,
                onDismiss = {
                    showAddSkillDialog = false
                    editingSkill = null
                },
                onSave = { saved ->
                    onSaveSkill(saved)
                    showAddSkillDialog = false
                    editingSkill = null
                },
            )
        }
        if (showAddLorebookDialog || editingLorebook != null) {
            val current = editingLorebook
            IosLorebookDialog(
                lorebook = current,
                onDismiss = {
                    showAddLorebookDialog = false
                    editingLorebook = null
                },
                onSave = { saved ->
                    onSaveLorebook(saved)
                    showAddLorebookDialog = false
                    editingLorebook = null
                },
            )
        }
        if (showAddLorebookEntryDialog || editingLorebookEntry != null) {
            val currentPair = editingLorebookEntry
            val lorebookId = currentPair?.first ?: expandedLorebookId ?: state.lorebooks.firstOrNull()?.id.orEmpty()
            val entry = currentPair?.second
            if (lorebookId.isNotBlank()) {
                IosLorebookEntryDialog(
                    entry = entry,
                    onDismiss = {
                        showAddLorebookEntryDialog = false
                        editingLorebookEntry = null
                    },
                    onSave = { saved ->
                        onSaveLorebookEntry(lorebookId, saved)
                        showAddLorebookEntryDialog = false
                        editingLorebookEntry = null
                    },
                )
            }
        }
        if (showAddMcpServerDialog || editingMcpServer != null) {
            val current = editingMcpServer
            IosMcpServerDialog(
                server = current,
                onDismiss = {
                    showAddMcpServerDialog = false
                    editingMcpServer = null
                },
                onSave = { saved ->
                    onSaveMcpServer(saved)
                    showAddMcpServerDialog = false
                    editingMcpServer = null
                },
            )
        }
        val currentRestoreResult = restoreResultDialog
        if (currentRestoreResult != null) {
            IosRestoreResultDialog(
                result = currentRestoreResult,
                onDismiss = { restoreResultDialog = null },
            )
        }
        if (showModelPicker) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val visibleConfigurations = state.providerConfigurations.filter { configuration ->
                modelSearchQuery.isBlank() ||
                    configuration.modelId.contains(modelSearchQuery, ignoreCase = true) ||
                    configuration.type.displayName().contains(modelSearchQuery, ignoreCase = true)
            }
            ModalBottomSheet(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onDismissRequest = { showModelPicker = false },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = {
                    IconButton(onClick = { showModelPicker = false }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(0.8f).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = AppShapes.SearchField,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text("Search models") },
                        singleLine = true,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        visibleConfigurations.forEach { configuration ->
                            item("model-provider-${configuration.type}") {
                                Text(
                                    configuration.type.displayName(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            item("model-${configuration.type}-${configuration.modelId}") {
                                LastChatGroupedModelRow(
                                    title = configuration.modelId,
                                    selected = configuration.type == state.provider.type &&
                                        configuration.modelId == state.provider.modelId,
                                    position = LastChatModelGroupPosition.Single,
                                    onClick = {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        onSelectDefaultModel(
                                            configuration.type,
                                            configuration.modelId,
                                        )
                                        showModelPicker = false
                                    },
                                    icon = {
                                        Surface(
                                            modifier = Modifier.size(32.dp),
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    configuration.type.displayName()
                                                        .first().uppercase(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                            }
                                        }
                                    },
                                    metadata = {
                                        Text(
                                            configuration.type.displayName(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        }
    }
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

private fun iosSettingsPaneGroups(): List<LastChatSettingsPaneGroup> {
    val assistantChildren = listOf(
        LastChatSettingsPaneEntry("AssistantMemory", "Memory", Icons.Rounded.Memory),
        LastChatSettingsPaneEntry("AssistantTools", "Tools", Icons.Rounded.Extension),
    )
    val displayChildren = listOf(
        LastChatSettingsPaneEntry("Fonts", "Fonts", Icons.Rounded.Tune),
        LastChatSettingsPaneEntry("UiCustomization", "UI customization", Icons.Rounded.Brush),
        LastChatSettingsPaneEntry("RpOptimizations", "Roleplay optimizations", Icons.Rounded.AutoAwesome),
    )
    val promptChildren = listOf(
        LastChatSettingsPaneEntry("Skills", "Skills", Icons.Rounded.Category),
        LastChatSettingsPaneEntry("Lorebooks", "Lorebooks", Icons.Rounded.Book),
    )
    val providerChildren = listOf(
        LastChatSettingsPaneEntry("ProviderModels", "Provider models", Icons.Rounded.Cloud),
        LastChatSettingsPaneEntry("Search", "Search service", Icons.Rounded.Public),
        LastChatSettingsPaneEntry("Tts", "Text-to-speech", Icons.AutoMirrored.Rounded.VolumeUp),
    )
    val backupChildren = listOf(
        LastChatSettingsPaneEntry("BackupWebDav", "WebDAV backup", Icons.Rounded.CloudUpload),
        LastChatSettingsPaneEntry("BackupLocal", "Local file backup", Icons.Rounded.FileUpload),
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
                LastChatSettingsPaneEntry("Mcp", "MCP Servers", Icons.Rounded.Extension),
            ),
        ),
        LastChatSettingsPaneGroup(
            id = "data",
            title = "Data",
            entries = listOf(
                LastChatSettingsPaneEntry("Backup", "Data backup", Icons.Rounded.CloudUpload, children = backupChildren),
                LastChatSettingsPaneEntry("ChatStorage", "Chat storage", Icons.Rounded.Storage),
            ),
        ),
        LastChatSettingsPaneGroup(
            id = "about",
            title = "About",
            entries = listOf(
                LastChatSettingsPaneEntry("About", "About", Icons.Rounded.Info),
            ),
        ),
    )
}

private fun iosSettingsMainDestination(destinationId: String): String = when (destinationId) {
    "AssistantMemory" -> "Assistants"
    "AssistantTools" -> "Assistants"
    "Fonts", "UiCustomization", "RpOptimizations" -> "Display"
    "Skills", "Lorebooks" -> "PromptInjections"
    "BackupWebDav", "BackupLocal" -> "Backup"
    "ProviderModels", "Search", "Tts" -> "Providers"
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
}

private fun IosProviderType.defaultModelId(): String = when (this) {
    IosProviderType.OPENAI -> "gpt-4.1-mini"
    IosProviderType.GOOGLE -> "gemini-2.5-flash"
    IosProviderType.CLAUDE -> "claude-sonnet-4-5"
}

@Composable
private fun IosTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.ButtonPill,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    onClick = { onSelect(index) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosSkillDialog(
    skill: IosSkill?,
    onDismiss: () -> Unit,
    onSave: (IosSkill) -> Unit,
) {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var instructions by remember(skill?.id) { mutableStateOf(skill?.instructions.orEmpty()) }
    var injectionPosition by remember(skill?.id) {
        mutableStateOf(skill?.injectionPosition ?: IosInjectionPosition.AFTER_SYSTEM)
    }
    var alwaysEnabled by remember(skill?.id) {
        mutableStateOf(skill?.alwaysEnabled ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) "New Skill" else "Edit Skill") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Prompt instructions") },
                    minLines = 3,
                    maxLines = 6,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Injection position",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IosInjectionPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = injectionPosition == pos,
                            onClick = { injectionPosition = pos },
                            label = { Text(pos.displayName(), style = MaterialTheme.typography.labelSmall) },
                            shape = AppShapes.Chip,
                        )
                    }
                }
                LastChatFormItem(
                    label = { Text("Always enabled") },
                    description = { Text("Active in all conversations") },
                ) {
                    Switch(
                        checked = alwaysEnabled,
                        onCheckedChange = { alwaysEnabled = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = skill ?: IosSkill()
                    onSave(
                        base.copy(
                            name = name.trim(),
                            description = description.trim(),
                            instructions = instructions.trim(),
                            injectionPosition = injectionPosition,
                            alwaysEnabled = alwaysEnabled,
                            enabled = true,
                            updatedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    )
                },
                enabled = name.isNotBlank(),
                shape = AppShapes.ButtonPill,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IosLorebookDialog(
    lorebook: IosLorebook?,
    onDismiss: () -> Unit,
    onSave: (IosLorebook) -> Unit,
) {
    var name by remember(lorebook?.id) { mutableStateOf(lorebook?.name.orEmpty()) }
    var description by remember(lorebook?.id) { mutableStateOf(lorebook?.description.orEmpty()) }
    var author by remember(lorebook?.id) { mutableStateOf(lorebook?.author.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lorebook == null) "New Lorebook" else "Edit Lorebook") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author (optional)") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = lorebook ?: IosLorebook()
                    onSave(
                        base.copy(
                            name = name.trim(),
                            description = description.trim(),
                            author = author.trim(),
                            enabled = true,
                        )
                    )
                },
                enabled = name.isNotBlank(),
                shape = AppShapes.ButtonPill,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IosLorebookEntryDialog(
    entry: IosLorebookEntry?,
    onDismiss: () -> Unit,
    onSave: (IosLorebookEntry) -> Unit,
) {
    var name by remember(entry?.id) { mutableStateOf(entry?.name.orEmpty()) }
    var prompt by remember(entry?.id) { mutableStateOf(entry?.prompt.orEmpty()) }
    var activationType by remember(entry?.id) {
        mutableStateOf(entry?.activationType ?: IosLorebookActivationType.KEYWORDS)
    }
    var keywordsText by remember(entry?.id) {
        mutableStateOf(entry?.keywords.orEmpty().joinToString(", "))
    }
    var injectionPosition by remember(entry?.id) {
        mutableStateOf(entry?.injectionPosition ?: IosInjectionPosition.AFTER_SYSTEM)
    }
    var scanDepth by remember(entry?.id) {
        mutableStateOf(entry?.scanDepth?.toString() ?: "10")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "New Entry" else "Edit Entry") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Content / Prompt") },
                    minLines = 3,
                    maxLines = 6,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Activation Trigger",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IosLorebookActivationType.entries.forEach { type ->
                        FilterChip(
                            selected = activationType == type,
                            onClick = { activationType = type },
                            label = { Text(type.displayName(), style = MaterialTheme.typography.labelSmall) },
                            shape = AppShapes.Chip,
                        )
                    }
                }
                if (activationType == IosLorebookActivationType.KEYWORDS) {
                    OutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        label = { Text("Keywords (comma separated)") },
                        placeholder = { Text("e.g. dragon, magic, sword") },
                        singleLine = true,
                        shape = AppShapes.InputField,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = scanDepth,
                        onValueChange = { scanDepth = it.filter(Char::isDigit) },
                        label = { Text("Scan Depth (messages)") },
                        singleLine = true,
                        shape = AppShapes.InputField,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Injection Position",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IosInjectionPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = injectionPosition == pos,
                            onClick = { injectionPosition = pos },
                            label = { Text(pos.displayName(), style = MaterialTheme.typography.labelSmall) },
                            shape = AppShapes.Chip,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = entry ?: IosLorebookEntry()
                    val kw = keywordsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(
                        base.copy(
                            name = name.trim(),
                            prompt = prompt.trim(),
                            activationType = activationType,
                            keywords = kw,
                            scanDepth = scanDepth.toIntOrNull() ?: 10,
                            injectionPosition = injectionPosition,
                            enabled = true,
                        )
                    )
                },
                enabled = name.isNotBlank(),
                shape = AppShapes.ButtonPill,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IosMcpServerDialog(
    server: IosMcpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (IosMcpServerConfig) -> Unit,
) {
    var name by remember(server?.id) { mutableStateOf(server?.commonOptions?.name.orEmpty()) }
    var url by remember(server?.id) { mutableStateOf(server?.url.orEmpty()) }
    var isStreamableHttp by remember(server?.id) {
        mutableStateOf(server is IosMcpServerConfig.StreamableHTTPServer)
    }
    var headersText by remember(server?.id) {
        mutableStateOf(
            server?.commonOptions?.headers.orEmpty()
                .joinToString("\n") { "${it.first}: ${it.second}" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "New MCP Server" else "Edit MCP Server") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g. My Remote Server") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://mcp.example.com/sse") },
                    singleLine = true,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Transport Protocol",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = !isStreamableHttp,
                        onClick = { isStreamableHttp = false },
                        label = { Text("SSE (Server-Sent Events)", style = MaterialTheme.typography.labelSmall) },
                        shape = AppShapes.Chip,
                    )
                    FilterChip(
                        selected = isStreamableHttp,
                        onClick = { isStreamableHttp = true },
                        label = { Text("Streamable HTTP", style = MaterialTheme.typography.labelSmall) },
                        shape = AppShapes.Chip,
                    )
                }
                OutlinedTextField(
                    value = headersText,
                    onValueChange = { headersText = it },
                    label = { Text("Custom HTTP Headers (optional)") },
                    placeholder = { Text("Authorization: Bearer token\nX-Custom-Key: value") },
                    minLines = 2,
                    maxLines = 4,
                    shape = AppShapes.InputField,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedHeaders = headersText.lines()
                        .mapNotNull { line ->
                            val parts = line.split(":", limit = 2)
                            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                        }
                    val common = (server?.commonOptions ?: IosMcpCommonOptions()).copy(
                        name = name.trim(),
                        headers = parsedHeaders,
                        enable = true,
                    )
                    val id = server?.id ?: kotlin.uuid.Uuid.random().toString()
                    val result = if (isStreamableHttp) {
                        IosMcpServerConfig.StreamableHTTPServer(
                            id = id,
                            commonOptions = common,
                            url = url.trim(),
                        )
                    } else {
                        IosMcpServerConfig.SseTransportServer(
                            id = id,
                            commonOptions = common,
                            url = url.trim(),
                        )
                    }
                    onSave(result)
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                shape = AppShapes.ButtonPill,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun IosRestoreResultDialog(
    result: IosRestoreResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Restore Completed")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Successfully imported and synced data:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardSmall,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (result.conversationsCount > 0) {
                            Text("• ${result.conversationsCount} Conversations", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.assistantsCount > 0) {
                            Text("• ${result.assistantsCount} Assistants", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.providersCount > 0) {
                            Text("• ${result.providersCount} AI Providers", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.skillsCount > 0) {
                            Text("• ${result.skillsCount} Skills", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.lorebooksCount > 0) {
                            Text("• ${result.lorebooksCount} Lorebooks", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.mcpServersCount > 0) {
                            Text("• ${result.mcpServersCount} MCP Servers", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.memoriesCount > 0) {
                            Text("• ${result.memoriesCount} Memories", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (result.notes.isNotEmpty()) {
                    Text(
                        "Notes:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    result.notes.forEach { note ->
                        Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = AppShapes.ButtonPill,
            ) { Text("OK") }
        },
    )
}

