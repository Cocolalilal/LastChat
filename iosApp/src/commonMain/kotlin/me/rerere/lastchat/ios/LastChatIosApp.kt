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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformAttachmentOpener
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.lastchat.ios.backup.IosBackupImportReport
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
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
import kotlinx.coroutines.launch

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
    Data("Data"),
    Backup("Backup"),
    About("About"),
    Unavailable("Unavailable"),
}

private data class DisplayMessage(
    val text: String,
    val outgoing: Boolean,
    val position: BubblePosition = BubblePosition.SINGLE,
    val parts: List<UIMessagePart> = emptyList(),
)

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
        colorScheme = colorScheme.withLastChatAmoledSurface(useDarkTheme),
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
    }
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
    platformHaptics: PlatformHaptics,
    attachmentOpener: PlatformAttachmentOpener,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val inputState = remember { TextFieldState() }
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
    val conversationMessages = state.selectedConversation?.messages.orEmpty()
        .filter { message ->
            message.toText().isNotBlank() || message.parts.any {
                it is UIMessagePart.Image || it is UIMessagePart.Video ||
                    it is UIMessagePart.Audio || it is UIMessagePart.Document
            }
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
        DisplayMessage(
            text = rawText.replace(GENERATED_MARKDOWN_IMAGE_REGEX, "").trim(),
            outgoing = outgoing,
            position = position,
            parts = message.parts + markdownImages,
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
            TopAppBar(
                title = { Text(state.assistant.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    LastChatMenuButton(
                        onClick = onOpenMenu,
                        contentDescription = "Messages",
                    )
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
            } else if (messages.isEmpty()) {
                item {
                    MessageBubble(
                        message = DisplayMessage("How can I help?", outgoing = false),
                        attachmentOpener = attachmentOpener,
                        platformHaptics = platformHaptics,
                        isTtsSpeaking = state.ttsSpeaking,
                        isTtsAvailable = state.tts.enabled && state.hasTtsApiKey,
                        showAssistantBubbles = state.appearance.showAssistantBubbles,
                        fontSizeRatio = state.appearance.fontSizeRatio,
                        rpStyleRules = state.appearance.rpStyleRules,
                        onSpeak = onSpeak,
                        onStopSpeaking = onStopSpeaking,
                    )
                }
            }
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    attachmentOpener = attachmentOpener,
                    platformHaptics = platformHaptics,
                    isTtsSpeaking = state.ttsSpeaking,
                    isTtsAvailable = state.tts.enabled && state.hasTtsApiKey,
                    showAssistantBubbles = state.appearance.showAssistantBubbles,
                    fontSizeRatio = state.appearance.fontSizeRatio,
                    rpStyleRules = state.appearance.rpStyleRules,
                    onSpeak = onSpeak,
                    onStopSpeaking = onStopSpeaking,
                )
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
    platformHaptics: PlatformHaptics,
    isTtsSpeaking: Boolean,
    isTtsAvailable: Boolean,
    showAssistantBubbles: Boolean,
    fontSizeRatio: Float,
    rpStyleRules: List<IosRpStyleRule>,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val styledText = remember(message.text, rpStyleRules) {
        buildIosRoleplayText(message.text, rpStyleRules)
    }
    val attachments = message.parts.filter { part ->
        part is UIMessagePart.Image || part is UIMessagePart.Video ||
            part is UIMessagePart.Audio || part is UIMessagePart.Document
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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
                        is UIMessagePart.Audio -> LastChatDocumentAttachmentTile(
                            fileName = "Audio",
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
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
            if (!message.outgoing && !showAssistantBubbles) {
                Text(
                    text = styledText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontSizeRatio,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontSizeRatio,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                )
            } else {
                GroupedMessageBubble(
                    position = message.position,
                    role = if (message.outgoing) BubbleRole.USER else BubbleRole.ASSISTANT,
                    modifier = Modifier.fillMaxWidth(0.86f),
                ) {
                    Text(
                        text = styledText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontSizeRatio,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontSizeRatio,
                        ),
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
            avatar = { item, modifier -> IosAssistantAvatar(item.name, modifier) },
        )
    }
}

@Composable
private fun IosAssistantAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "A",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
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
    onSaveImageGeneration: (IosImageGenerationPreferences) -> Unit,
    onSaveAppearance: (String, IosColorMode) -> Unit,
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
            "ChatStorage" -> section = IosSettingsSection.Data
            "Backup", "BackupLocal" -> section = IosSettingsSection.Backup
            "About" -> section = IosSettingsSection.About
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
            IosSettingsSection.Data -> activeDestinationId.ifBlank { "ChatStorage" }
            IosSettingsSection.Backup -> activeDestinationId.ifBlank { "Backup" }
            IosSettingsSection.About -> "About"
            IosSettingsSection.Unavailable -> unavailableDestinationId
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
                                                onSaveFontSettings(enabled)
                                            },
                                        )
                                    },
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
                                subtitle = "Native monospace font",
                                darkTheme = darkTheme,
                            ) {
                                Text(
                                    "fun main() = println(\"LastChat\")",
                                    fontFamily = FontFamily.Monospace,
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
                            }
                        } else {
                            LastChatSettingGroupInputItem(
                                title = "UI customization",
                                subtitle = "Chat presentation and content scale",
                                darkTheme = darkTheme,
                            ) {
                                LastChatFormItem(
                                    label = { Text("Assistant message bubbles") },
                                    description = { Text("Show a filled surface behind assistant messages") },
                                    tail = {
                                        Switch(
                                            checked = showAssistantBubbles,
                                            onCheckedChange = { enabled ->
                                                showAssistantBubbles = enabled
                                                onSaveUiCustomization(enabled, fontSizeRatio)
                                            },
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
                                                onSaveUiCustomization(showAssistantBubbles, fontSizeRatio)
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
                        }
                    }
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
            if (section == IosSettingsSection.Backup) {
                item {
                    LastChatSettingsGroup(
                        title = "Restore from Android",
                        horizontalPadding = 0.dp,
                        titleStartPadding = 0.dp,
                    ) {
                        LastChatSettingGroupInputItem(
                            title = "Restore backup",
                            subtitle = "Import providers, assistants, appearance, search and TTS settings from a LastChat Android backup (.zip).",
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
                    }
                }
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
    val providerChildren = listOf(
        LastChatSettingsPaneEntry("ProviderModels", "Provider models", Icons.Rounded.Cloud),
        LastChatSettingsPaneEntry("Search", "Search service", Icons.Rounded.Public),
        LastChatSettingsPaneEntry("Tts", "Text-to-speech", Icons.AutoMirrored.Rounded.VolumeUp),
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
    "ProviderModels", "Search", "Tts" -> "Providers"
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
