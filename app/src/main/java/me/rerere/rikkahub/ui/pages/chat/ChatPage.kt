package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff

import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.ui.components.chat.NewChatContent

import me.rerere.rikkahub.ui.components.ui.UpdateDialog
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.components.ui.Tooltip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.TtsAutoplayMode
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getEffectiveTTSProvider
import me.rerere.rikkahub.data.datastore.getEffectiveTtsAutoplayMode
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.navigation.ChatRouteTarget
import me.rerere.rikkahub.data.repository.ChatAttachmentManager
import me.rerere.rikkahub.ui.components.ai.MinimalChatInput
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.service.ChatPersistenceMode
import me.rerere.rikkahub.ui.theme.AssistantChatTheme
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.navigateToChatPage
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.draw.clipToBounds
import me.rerere.rikkahub.ui.modifier.LastChatBlur
import me.rerere.rikkahub.ui.modifier.LocalLastChatBlur
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatBlurSource
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import androidx.compose.ui.draw.clip

internal fun hasConversationMessages(conversation: Conversation): Boolean {
    return conversation.messageNodes.isNotEmpty()
}

internal fun hasConversationPresetMessages(conversation: Conversation, assistant: Assistant): Boolean {
    if (assistant.presetMessages.isEmpty()) return false
    val presetIds = assistant.presetMessages.map { it.id }.toSet()
    return conversation.currentMessages.any { it.id in presetIds }
}

@Composable
private fun ChatTopFadeOverlay(
    fadeHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundColor = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .background(backgroundColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.98f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun ChatWidePanelEdgeFadeOverlay(
    width: Dp,
    placement: ChatToolbarPlacement,
    showBottomFade: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topFadeHeight = if (placement == ChatToolbarPlacement.Top) 96.dp else 36.dp
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(backgroundColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topFadeHeight)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.98f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomFade,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                        brush = if (placement == ChatToolbarPlacement.Bottom) {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    backgroundColor.copy(alpha = 0.72f),
                                    backgroundColor.copy(alpha = 0.97f)
                                )
                            )
                        } else {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    backgroundColor.copy(alpha = 0.92f)
                                )
                            )
                        }
                        )
                )
            }
        }
    }
}

internal data class AssistantSwitchNavigation(
    val initText: String?,
    val initFiles: List<String>,
    val persistenceMode: String?,
)

internal fun buildAssistantSwitchNavigation(
    persistenceMode: ChatPersistenceMode,
): AssistantSwitchNavigation {
    return AssistantSwitchNavigation(
        initText = null,
        initFiles = emptyList(),
        persistenceMode = persistenceMode.takeIf { it != ChatPersistenceMode.NORMAL }?.routeValue,
    )
}

internal fun decodeChatRouteText(text: String?): String {
    return text?.let { encoded ->
        runCatching { encoded.base64Decode() }.getOrDefault("")
    }.orEmpty()
}

internal data class ChatInputDraft(
    val text: String = "",
    val messageContent: List<UIMessagePart> = emptyList(),
    val editingMessage: Uuid? = null,
) {
    val isEmpty: Boolean
        get() = text.isEmpty() && messageContent.isEmpty() && editingMessage == null

    fun forAssistantSwitch(): ChatInputDraft {
        return copy(editingMessage = null)
    }
}

internal object ChatSessionDraftStore {
    private val drafts = mutableMapOf<Uuid, ChatInputDraft>()

    fun get(conversationId: Uuid): ChatInputDraft? {
        return drafts[conversationId]
    }

    fun put(conversationId: Uuid, draft: ChatInputDraft) {
        if (draft.isEmpty) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = draft
        }
    }

    fun moveDraft(fromConversationId: Uuid, toConversationId: Uuid, draft: ChatInputDraft) {
        drafts.remove(fromConversationId)
        put(toConversationId, draft.forAssistantSwitch())
    }

    fun clear() {
        drafts.clear()
    }
}

internal fun ChatInputState.toDraft(): ChatInputDraft {
    return ChatInputDraft(
        text = textContent.text.toString(),
        messageContent = messageContent,
        editingMessage = editingMessage,
    )
}

internal fun ChatInputState.applyDraft(draft: ChatInputDraft) {
    clearInput()
    if (draft.text.isNotEmpty()) {
        setMessageText(draft.text)
    }
    if (draft.messageContent.isNotEmpty()) {
        messageContent = draft.messageContent
    }
    editingMessage = draft.editingMessage
}

internal fun shouldShowNewChatContent(
    isTemporaryChat: Boolean,
    hasConversationMessages: Boolean,
    hasAnyPresetMessages: Boolean,
    showNewChatContent: Boolean,
    hasTextInput: Boolean,
    isKeyboardOpen: Boolean,
): Boolean {
    return !isTemporaryChat &&
        !hasConversationMessages &&
        !hasAnyPresetMessages &&
        showNewChatContent &&
        !hasTextInput &&
        !isKeyboardOpen
}

internal fun chatTopBarPlacement(settings: Settings): ChatToolbarPlacement {
    return if (settings.displaySetting.chatToolbarAtBottom) {
        ChatToolbarPlacement.Bottom
    } else {
        ChatToolbarPlacement.Top
    }
}

internal fun chatListTopPadding(placement: ChatToolbarPlacement): androidx.compose.ui.unit.Dp {
    return if (placement == ChatToolbarPlacement.Top) 88.dp else 16.dp
}

internal fun chatListBottomPadding(placement: ChatToolbarPlacement): androidx.compose.ui.unit.Dp {
    return if (placement == ChatToolbarPlacement.Bottom) 204.dp else 140.dp
}

private fun chatToolbarOverflowMenuTransformOrigin(placement: ChatToolbarPlacement): TransformOrigin {
    return if (placement == ChatToolbarPlacement.Bottom) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(1f, 0f)
    }
}

private fun latestAssistantSpeechMessage(conversation: Conversation): UIMessage? {
    return conversation.currentMessages.lastOrNull { message ->
        message.role == MessageRole.ASSISTANT && message.toContentText().isNotBlank()
    }
}

private fun speakablePrefixLength(text: String, final: Boolean): Int {
    val trimmedEnd = text.indexOfLast { !it.isWhitespace() }
    if (trimmedEnd < 0) return 0

    val paragraphBreak = text.indexOf("\n\n")
    if (paragraphBreak >= 0) return paragraphBreak + 2

    val sentenceBoundary = text.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '。' || it == '！' || it == '？' }
    if (sentenceBoundary >= 0) return sentenceBoundary + 1

    val softBoundary = text.indexOfFirst { it == '\n' || it == ';' || it == '；' }
    if (softBoundary >= 0) return softBoundary + 1

    return if (final) trimmedEnd + 1 else 0
}

@Composable
private fun ChatTtsAutoplayEffect(
    settings: Settings,
    assistant: Assistant,
    conversation: Conversation,
    loadingJob: Job?,
) {
    val tts = LocalTTSState.current
    val mode = settings.getEffectiveTtsAutoplayMode(assistant)
    val provider = remember(
        settings.ttsProviders,
        settings.selectedTTSVoiceId,
        settings.selectedTTSProviderId,
        assistant.ttsVoiceId,
    ) {
        settings.getEffectiveTTSProvider(assistant)
    }
    var completedMessageId by remember(conversation.id) { mutableStateOf<Uuid?>(null) }
    var streamingMessageId by remember(conversation.id) { mutableStateOf<Uuid?>(null) }
    var generationBaselineMessageId by remember(conversation.id) { mutableStateOf<Uuid?>(null) }
    var generationBaselineText by remember(conversation.id) { mutableStateOf("") }
    var spokenLength by remember(conversation.id) { mutableStateOf(0) }
    var wasGenerating by remember(conversation.id) { mutableStateOf(false) }

    LaunchedEffect(conversation.id, conversation.currentMessages, loadingJob, mode, provider) {
        if (mode == TtsAutoplayMode.OFF || provider == null) return@LaunchedEffect
        if (loadingJob == null) {
            if (!wasGenerating) return@LaunchedEffect
            wasGenerating = false
            val latestMessage = latestAssistantSpeechMessage(conversation) ?: return@LaunchedEffect
            val text = latestMessage.toContentText()
            if (latestMessage.id == generationBaselineMessageId && text == generationBaselineText) {
                return@LaunchedEffect
            }
            if (completedMessageId == latestMessage.id && spokenLength >= text.length) {
                return@LaunchedEffect
            }
            if (streamingMessageId != latestMessage.id || spokenLength > text.length) {
                streamingMessageId = latestMessage.id
                spokenLength = 0
            }
            val remaining = text.drop(spokenLength)
            val length = speakablePrefixLength(remaining, final = true)
            val segment = remaining.take(length).trim()
            if (segment.isNotBlank()) {
                tts.speak(
                    text = segment,
                    flushCalled = spokenLength == 0,
                    overrideSetting = provider,
                )
                spokenLength += length
            }
            completedMessageId = latestMessage.id
            return@LaunchedEffect
        }

        if (!wasGenerating) {
            val baselineMessage = latestAssistantSpeechMessage(conversation)
            generationBaselineMessageId = baselineMessage?.id
            generationBaselineText = baselineMessage?.toContentText().orEmpty()
            wasGenerating = true
        }
        val latestMessage = latestAssistantSpeechMessage(conversation) ?: return@LaunchedEffect
        if (latestMessage.id == generationBaselineMessageId && latestMessage.toContentText() == generationBaselineText) {
            return@LaunchedEffect
        }
        val text = latestMessage.toContentText()
        if (streamingMessageId != latestMessage.id) {
            streamingMessageId = latestMessage.id
            spokenLength = 0
        }
        if (spokenLength > text.length) {
            spokenLength = 0
        }
        val remaining = text.drop(spokenLength)
        val length = speakablePrefixLength(remaining, final = false)
        val segment = remaining.take(length).trim()
        if (segment.isNotBlank()) {
            spokenLength += length
            tts.speak(segment, flushCalled = false, overrideSetting = provider)
        }
    }
}

internal fun shouldUseWideChatLayout(
    windowWidth: Dp,
    windowHeight: Dp,
): Boolean {
    return windowWidth >= 840.dp && windowHeight >= 600.dp
}

internal enum class ChatToolbarPlacement {
    Top,
    Bottom
}

@Composable
fun ChatPage(
    target: ChatRouteTarget,
) {
    val id = target.uuid
    val text = target.text
    val files = target.fileUris
    val searchQuery = target.searchQuery
    val persistenceMode = target.persistenceMode
    val focusLatestMessageKey = target.focusLatestMessageKey
    val vm: ChatVM = koinViewModel(
        key = id.toString(),
        parameters = {
            parametersOf(id.toString())
        }
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val genericErrorMessage = context.getString(R.string.common_error)

    // Handle Error
    LaunchedEffect(vm) {
        vm.errorFlow.collect { error ->
            toaster.show(error.message ?: genericErrorMessage, type = ToastType.Error)
        }
    }

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val conversationInitialized by vm.conversationInitialized.collectAsStateWithLifecycle()
    val conversationAssistant by vm.conversationAssistant.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val conversationPersistenceMode by vm.conversationPersistenceMode.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val currentSearchMode by vm.currentSearchMode.collectAsStateWithLifecycle()
    var manualTemporaryChat by rememberSaveable(id) { mutableStateOf(false) }
    val activePersistenceMode = when {
        manualTemporaryChat || conversationPersistenceMode == ChatPersistenceMode.TEMPORARY -> ChatPersistenceMode.TEMPORARY
        conversationPersistenceMode == ChatPersistenceMode.PERSIST_ON_REPLY -> ChatPersistenceMode.PERSIST_ON_REPLY
        else -> ChatPersistenceMode.NORMAL
    }
    ChatTtsAutoplayEffect(
        settings = setting,
        assistant = conversationAssistant,
        conversation = conversation,
        loadingJob = loadingJob,
    )

    LaunchedEffect(conversation.id) {
        manualTemporaryChat = false
    }

    LaunchedEffect(id, persistenceMode) {
        vm.applyRoutePersistenceMode(ChatPersistenceMode.fromRouteValue(persistenceMode))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            softwareKeyboardController?.hide()
        }
    }

    val windowSize = currentWindowDpSize()
    val useWideLayout = shouldUseWideChatLayout(windowSize.width, windowSize.height)
    val wideDrawerExpandedWidth = 336.dp
    val chatContentMaxWidth = when {
        useWideLayout && windowSize.width >= 1440.dp -> 980.dp
        useWideLayout -> 900.dp
        else -> Dp.Unspecified
    }
    val inputMaxWidth = when {
        useWideLayout && windowSize.width >= 1440.dp -> 920.dp
        useWideLayout -> 840.dp
        else -> Dp.Unspecified
    }
    var isWidePanelCollapsed by rememberSaveable { mutableStateOf(false) }
    var showWideRailAssistantPicker by remember { mutableStateOf(false) }

    val inputState = rememberChatInputState()
    var inputRestored by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, text, files) {
        inputRestored = false
        inputState.clearInput()
        val decodedText = decodeChatRouteText(text)
        val importedParts = if (files.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                buildList {
                    files.forEach { sourceFile ->
                        val mimeType = context.getFileMimeType(sourceFile)
                        val fileName = context.getFileNameFromUri(sourceFile) ?: "file"
                        val localFile = ChatAttachmentManager.importChatFile(
                            uri = sourceFile,
                            fileNameHint = fileName,
                            mimeHint = mimeType,
                        )?.uri ?: return@forEach
                        when {
                            mimeType?.startsWith("image/") == true -> add(UIMessagePart.Image(url = localFile.toString()))
                            mimeType?.startsWith("video/") == true -> add(UIMessagePart.Video(url = localFile.toString()))
                            mimeType?.startsWith("audio/") == true -> add(UIMessagePart.Audio(url = localFile.toString()))
                            else -> add(
                                UIMessagePart.Document(
                                    url = localFile.toString(),
                                    fileName = fileName,
                                    mime = mimeType ?: "application/octet-stream"
                                )
                            )
                        }
                    }
                }
            }
        } else {
            emptyList()
        }
        val routeDraft = ChatInputDraft(
            text = decodedText,
            messageContent = importedParts,
        )
        val draft = routeDraft.takeUnless { it.isEmpty }
            ?: ChatSessionDraftStore.get(id)
            ?: ChatInputDraft()
        inputState.applyDraft(draft)
        inputRestored = true
    }

    LaunchedEffect(id, inputState) {
        snapshotFlow {
            if (inputRestored) {
                inputState.toDraft()
            } else {
                null
            }
        }
            .distinctUntilChanged()
            .collect { draft ->
                if (draft != null) {
                    ChatSessionDraftStore.put(id, draft)
                }
            }
    }

    val initialChatListScrollPosition = vm.chatListScrollPosition
    val chatListState = remember(conversation.id) {
        LazyListState(
            firstVisibleItemIndex = initialChatListScrollPosition?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = initialChatListScrollPosition?.firstVisibleItemScrollOffset ?: 0,
        )
    }
    var chatListReady by remember(conversation.id) { mutableStateOf(false) }
    var chatAnimationsEnabled by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(conversation.id) {
        delay(500)
        chatAnimationsEnabled = true
    }
    var consumedFocusLatestMessageKey by remember(conversation.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        conversation.id,
        conversationInitialized,
        conversation.messageNodes.size,
        chatListState,
    ) {
        if (!conversationInitialized) {
            return@LaunchedEffect
        }
        if (chatListReady) {
            return@LaunchedEffect
        }
        val savedPosition = vm.chatListScrollPosition
        when {
            conversation.messageNodes.isEmpty() -> {
                chatListReady = true
            }

            savedPosition != null -> {
                chatListState.scrollToItem(
                    index = savedPosition.firstVisibleItemIndex,
                    scrollOffset = savedPosition.firstVisibleItemScrollOffset,
                )
                vm.chatListInitialized = true
                chatListReady = true
            }

            !vm.chatListInitialized -> {
                // Scroll to the last item. The LazyColumn shows turn-groups (not raw nodes),
                // so we cannot use messageNodes.lastIndex as the item index — in a tool-heavy
                // chat there are far fewer groups than nodes. Int.MAX_VALUE is clamped by
                // Compose to the true last item index.
                chatListState.scrollToItem(Int.MAX_VALUE)
                vm.chatListInitialized = true
                chatListReady = true
            }

            else -> {
                chatListReady = true
            }
        }
    }

    LaunchedEffect(focusLatestMessageKey, conversation.id, conversation.messageNodes.isNotEmpty()) {
        if (
            focusLatestMessageKey != null &&
            consumedFocusLatestMessageKey != focusLatestMessageKey &&
            conversation.messageNodes.isNotEmpty()
        ) {
            consumedFocusLatestMessageKey = focusLatestMessageKey
            // Same reasoning: use Int.MAX_VALUE instead of messageNodes.lastIndex because
            // the LazyColumn items are turn-groups, not individual nodes.
            chatListState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    LaunchedEffect(conversation.id, conversation.messageNodes.isNotEmpty(), chatListReady, chatListState) {
        if (conversation.messageNodes.isEmpty() || !chatListReady) {
            return@LaunchedEffect
        }
        snapshotFlow {
            ChatListScrollPosition(
                firstVisibleItemIndex = chatListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = chatListState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { position ->
                vm.updateChatListScrollPosition(
                    firstVisibleItemIndex = position.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = position.firstVisibleItemScrollOffset,
                )
            }
    }

    fun navigateToAssistantConversation(selectedAssistant: Assistant) {
        scope.launch {
            val draft = inputState.toDraft()
            val newConversation = vm.createConversationForAssistant(selectedAssistant.id)
            ChatSessionDraftStore.moveDraft(
                fromConversationId = conversation.id,
                toConversationId = newConversation.id,
                draft = draft,
            )
            val draftNavigation = buildAssistantSwitchNavigation(
                persistenceMode = activePersistenceMode,
            )
            navigateToChatPage(
                navController = navController,
                chatId = newConversation.id,
                initText = draftNavigation.initText,
                initFiles = draftNavigation.initFiles.map(String::toUri),
                persistenceMode = draftNavigation.persistenceMode,
            )
        }
    }

    when {
        useWideLayout -> {
            AssistantChatTheme(assistant = conversationAssistant) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val wideHazeState = rememberHazeState()
                    val wideBlur = remember(setting.displaySetting.enableBlurEffect, wideHazeState) {
                        LastChatBlur(
                            enabled = setting.displaySetting.enableBlurEffect,
                            hazeState = wideHazeState,
                        )
                    }
                    val widePanelWidth by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isWidePanelCollapsed) 80.dp else wideDrawerExpandedWidth,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 260,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        label = "chat_wide_panel_width"
                    )
                    val widePanelHaptics = rememberPremiumHaptics(enabled = setting.displaySetting.enableUIHaptics)
                    var widePanelDragX by remember { mutableStateOf(0f) }
                    val wideEffectiveDisplaySetting = setting.getEffectiveDisplaySetting(conversationAssistant)
                    val wideShowsNewChatContent =
                        wideEffectiveDisplaySetting.newChatHeaderStyle != me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE ||
                            wideEffectiveDisplaySetting.newChatContentStyle != me.rerere.rikkahub.data.datastore.NewChatContentStyle.NONE
                    val showWideBottomFade =
                        hasConversationMessages(conversation) ||
                            conversationAssistant.presetMessages.isNotEmpty() ||
                            activePersistenceMode == ChatPersistenceMode.TEMPORARY ||
                            !wideShowsNewChatContent
                    CompositionLocalProvider(LocalLastChatBlur provides wideBlur) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AssistantBackground(
                            assistant = conversationAssistant,
                            modifier = Modifier
                                .fillMaxSize()
                                .lastChatBlurSource()
                        )
                        ChatWidePanelEdgeFadeOverlay(
                            width = widePanelWidth,
                            placement = chatTopBarPlacement(setting),
                            showBottomFade = showWideBottomFade,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(widePanelWidth)
                                    .fillMaxHeight()
                                    .clipToBounds()
                                    .pointerInput(isWidePanelCollapsed) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                widePanelDragX = 0f
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                widePanelDragX += dragAmount
                                                change.consume()
                                            },
                                            onDragEnd = {
                                                val threshold = 36.dp.toPx()
                                                val shouldCollapse = !isWidePanelCollapsed && widePanelDragX < -threshold
                                                val shouldExpand = isWidePanelCollapsed && widePanelDragX > threshold
                                                when {
                                                    shouldCollapse -> {
                                                        widePanelHaptics.perform(HapticPattern.Pop)
                                                        isWidePanelCollapsed = true
                                                    }

                                                    shouldExpand -> {
                                                        widePanelHaptics.perform(HapticPattern.Pop)
                                                        isWidePanelCollapsed = false
                                                    }
                                                }
                                                widePanelDragX = 0f
                                            },
                                            onDragCancel = {
                                                widePanelDragX = 0f
                                            }
                                        )
                                    }
                            ) {
                                AnimatedContent(
                                    targetState = isWidePanelCollapsed,
                                    transitionSpec = {
                                        (fadeIn(
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.7f,
                                                stiffness = 360f
                                            )
                                        ) + scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.7f,
                                                stiffness = 360f
                                            )
                                        )) togetherWith (fadeOut(
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.85f,
                                                stiffness = 420f
                                            )
                                        ) + scaleOut(
                                            targetScale = 0.96f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.85f,
                                                stiffness = 420f
                                            )
                                        )) using SizeTransform(clip = true)
                                    },
                                    label = "chat_side_panel",
                                    modifier = Modifier.fillMaxSize()
                                ) { collapsed ->
                                    if (collapsed) {
                                        CollapsedChatSideRail(
                                            current = conversation,
                                            settings = setting,
                                            onExpand = { isWidePanelCollapsed = false },
                                            onOpenImageGen = { navController.navigate(Screen.ImageGen) },
                                            onOpenStatistics = { navController.navigate(Screen.Menu) },
                                            onOpenSettings = { navController.navigate(Screen.Setting) },
                                            onOpenAssistant = {
                                                showWideRailAssistantPicker = true
                                            },
                                            vm = vm
                                        )
                                    } else {
                                        ChatDrawerContent(
                                            navController = navController,
                                            current = conversation,
                                            vm = vm,
                                            settings = setting,
                                            inputState = inputState,
                                            activePersistenceMode = activePersistenceMode,
                                            drawerState = null,
                                            presentation = ChatDrawerPresentation.PermanentPane,
                                            collapsedWidth = wideDrawerExpandedWidth,
                                            expandedWidth = wideDrawerExpandedWidth,
                                            onCollapseRequest = { isWidePanelCollapsed = true },
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                ChatPageContent(
                                    inputState = inputState,
                                    loadingJob = loadingJob,
                                    setting = setting,
                                    currentAssistant = conversationAssistant,
                                    conversationInitialized = conversationInitialized,
                                    conversation = conversation,
                                    drawerState = drawerState,
                                    navController = navController,
                                    vm = vm,
                                    chatListState = chatListState,
                                    chatListReady = chatListReady,
                                    chatAnimationsEnabled = chatAnimationsEnabled,
                                    enableWebSearch = enableWebSearch,
                                    currentSearchMode = currentSearchMode,
                                    currentChatModel = currentChatModel,
                                    conversationPersistenceMode = conversationPersistenceMode,
                                    manualTemporaryChat = manualTemporaryChat,
                                    onManualTemporaryChatChange = { manualTemporaryChat = it },
                                    bigScreen = true,
                                    contentMaxWidth = chatContentMaxWidth,
                                    inputMaxWidth = inputMaxWidth,
                                    initialSearchQuery = searchQuery,
                                    renderBackground = false,
                                    inheritedBlur = wideBlur,
                                )
                            }
                        }
                        if (showWideRailAssistantPicker) {
                            me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
                                settings = setting,
                                currentAssistant = conversationAssistant,
                                onAssistantSelected = { assistant ->
                                    vm.setSelectedAssistant(assistant.id)
                                },
                                onNavigate = { assistant ->
                                    showWideRailAssistantPicker = false
                                    navigateToAssistantConversation(assistant)
                                },
                                onDismiss = {
                                    showWideRailAssistantPicker = false
                                }
                            )
                        }
                    }
                    }
                }
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        inputState = inputState,
                        activePersistenceMode = activePersistenceMode,
                        drawerState = drawerState,
                        presentation = ChatDrawerPresentation.Modal,
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    setting = setting,
                    currentAssistant = conversationAssistant,
                    conversationInitialized = conversationInitialized,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    chatListReady = chatListReady,
                    chatAnimationsEnabled = chatAnimationsEnabled,
                    enableWebSearch = enableWebSearch,
                    currentSearchMode = currentSearchMode,
                    currentChatModel = currentChatModel,
                    conversationPersistenceMode = conversationPersistenceMode,
                    manualTemporaryChat = manualTemporaryChat,
                    onManualTemporaryChatChange = { manualTemporaryChat = it },
                    bigScreen = false,
                    contentMaxWidth = Dp.Unspecified,
                    inputMaxWidth = Dp.Unspecified,
                    initialSearchQuery = searchQuery
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    setting: Settings,
    currentAssistant: Assistant,
    conversationInitialized: Boolean,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: NavHostController,
    vm: ChatVM,
    chatListState: LazyListState,
    chatListReady: Boolean,
    chatAnimationsEnabled: Boolean,
    enableWebSearch: Boolean,
    currentSearchMode: me.rerere.rikkahub.data.model.AssistantSearchMode,
    currentChatModel: Model?,
    conversationPersistenceMode: ChatPersistenceMode,
    manualTemporaryChat: Boolean,
    onManualTemporaryChatChange: (Boolean) -> Unit,
    contentMaxWidth: Dp = Dp.Unspecified,
    inputMaxWidth: Dp = Dp.Unspecified,
    initialSearchQuery: String? = null,
    renderBackground: Boolean = true,
    inheritedBlur: LastChatBlur? = null,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val modelRequiredMessage = context.getString(R.string.chat_model_required)
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val activePersistenceMode = when {
        manualTemporaryChat || conversationPersistenceMode == ChatPersistenceMode.TEMPORARY -> ChatPersistenceMode.TEMPORARY
        conversationPersistenceMode == ChatPersistenceMode.PERSIST_ON_REPLY -> ChatPersistenceMode.PERSIST_ON_REPLY
        else -> ChatPersistenceMode.NORMAL
    }
    val isTemporaryChat = activePersistenceMode == ChatPersistenceMode.TEMPORARY

    // State for regeneration confirmation dialog
    var showRegenerateConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRegenerateMessage by rememberSaveable { mutableStateOf<me.rerere.ai.ui.UIMessage?>(null) }
    // State for user message regeneration confirmation dialog
    var showUserRegenerateConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUserRegenerateMessage by rememberSaveable { mutableStateOf<me.rerere.ai.ui.UIMessage?>(null) }
    // State for user message delete confirmation dialog
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteMessage by rememberSaveable { mutableStateOf<me.rerere.ai.ui.UIMessage?>(null) }
    var showToolbarOverflowMenu by remember { mutableStateOf(false) }
    var isChatShareSelecting by rememberSaveable { mutableStateOf(false) }
    var selectedChatShareItems by remember(conversation.id) { mutableStateOf<Set<Uuid>>(emptySet()) }
    var showExportSheet by remember { mutableStateOf(false) }
    var chatSearchQuery by rememberSaveable(conversation.id) { mutableStateOf(initialSearchQuery.orEmpty()) }
    var consumedInitialSearchQuery by remember(conversation.id) { mutableStateOf<String?>(null) }
    val toolbarPlacement = chatTopBarPlacement(setting)
    val isGenerating = loadingJob != null
    val density = LocalDensity.current
    val hazeState = rememberHazeState()
    val localBlur = remember(setting.displaySetting.enableBlurEffect, hazeState) {
        LastChatBlur(
            enabled = setting.displaySetting.enableBlurEffect,
            hazeState = hazeState,
        )
    }
    val blur = inheritedBlur ?: localBlur

    LaunchedEffect(conversation.id) {
        previewMode = false
        showToolbarOverflowMenu = false
        showRegenerateConfirmDialog = false
        pendingRegenerateMessage = null
        showUserRegenerateConfirmDialog = false
        pendingUserRegenerateMessage = null
        showDeleteConfirmDialog = false
        pendingDeleteMessage = null
        if (isChatShareSelecting) {
            isChatShareSelecting = false
            selectedChatShareItems = emptySet()
        }
    }
    
    // Auto-scroll to first matching message when opened from search
    LaunchedEffect(initialSearchQuery, conversation.id, conversation.messageNodes.isNotEmpty()) {
        if (
            !initialSearchQuery.isNullOrBlank() &&
            consumedInitialSearchQuery != initialSearchQuery &&
            conversation.messageNodes.isNotEmpty()
        ) {
            consumedInitialSearchQuery = initialSearchQuery
            // Find the first message containing the search query
            val matchIndex = conversation.messageNodes.indexOfFirst { node ->
                node.currentMessage.toText().contains(initialSearchQuery, ignoreCase = true)
            }
            if (matchIndex >= 0) {
                // Small delay to let the UI settle
                delay(100)
                chatListState.animateScrollToItem(matchIndex)
            }
        }
    }
    
    // Track the last selected search provider index so we can restore it when toggling on
    var lastProviderIndex by rememberSaveable { mutableStateOf(0) }
    
    // Update lastProviderIndex whenever currentSearchMode is Provider
    LaunchedEffect(currentSearchMode) {
        if (currentSearchMode is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider) {
            lastProviderIndex = currentSearchMode.index
        }
    }


    fun navigateToAssistantConversation(selectedAssistant: Assistant) {
        scope.launch {
            val draft = inputState.toDraft()
            val newConversation = vm.createConversationForAssistant(selectedAssistant.id)
            ChatSessionDraftStore.moveDraft(
                fromConversationId = conversation.id,
                toConversationId = newConversation.id,
                draft = draft,
            )
            val draftNavigation = buildAssistantSwitchNavigation(
                persistenceMode = activePersistenceMode,
            )
            navigateToChatPage(
                navController = navController,
                chatId = newConversation.id,
                initText = draftNavigation.initText,
                initFiles = draftNavigation.initFiles.map(String::toUri),
                persistenceMode = draftNavigation.persistenceMode,
            )
        }
    }

    LaunchedEffect(conversation.id, initialSearchQuery) {
        chatSearchQuery = initialSearchQuery.orEmpty()
    }

    fun startChatShareSelection() {
        showToolbarOverflowMenu = false
        previewMode = false
        selectedChatShareItems = conversation.messageNodes.map { it.id }.toSet()
        isChatShareSelecting = true
    }

    fun cancelChatShareSelection() {
        isChatShareSelecting = false
        selectedChatShareItems = emptySet()
    }

    fun confirmChatShareSelection() {
        isChatShareSelecting = false
        if (selectedChatShareItems.isNotEmpty()) {
            showExportSheet = true
        }
    }


    LaunchedEffect(loadingJob) {
        inputState.loading = loadingJob != null
    }

    AssistantChatTheme(assistant = currentAssistant) {
        CompositionLocalProvider(LocalLastChatBlur provides blur) {
            Surface(
                color = if (renderBackground) {
                    MaterialTheme.colorScheme.background
                } else {
                    Color.Transparent
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (renderBackground) {
                        AssistantBackground(
                            assistant = currentAssistant,
                            modifier = Modifier.lastChatBlurSource()
                        )
                    }
                    Scaffold(
                topBar = if (toolbarPlacement == ChatToolbarPlacement.Top) {
                    {
                        ChatToolbar(
                            placement = ChatToolbarPlacement.Top,
                            settings = setting,
                            currentAssistant = currentAssistant,
                            conversationInitialized = conversationInitialized,
                            conversation = conversation,
                            bigScreen = bigScreen,
                            drawerState = drawerState,
                            previewMode = previewMode,
                            isTemporaryChat = isTemporaryChat,
                            currentChatModel = currentChatModel,
                            isGenerating = isGenerating,
                            showCloseAction = previewMode || isChatShareSelecting,
                            showTopFade = true,
                            vm = vm,
                            onNewChat = {
                                navigateToChatPage(navController)
                            },
                            onOpenOverflowMenu = {
                                showToolbarOverflowMenu = !showToolbarOverflowMenu
                            },
                            onCloseAction = {
                                showToolbarOverflowMenu = false
                                if (previewMode) {
                                    previewMode = false
                                }
                                if (isChatShareSelecting) {
                                    cancelChatShareSelection()
                                }
                            },
                            onUpdateSettings = { newSettings ->
                                vm.updateSettings(newSettings)
                            },
                            onSwitchAssistant = { assistant ->
                                navigateToAssistantConversation(assistant)
                            },
                            onToggleTemporaryChat = {
                                onManualTemporaryChatChange(!manualTemporaryChat)
                            }
                        )
                    }
                } else {
                    {
                        ChatTopFadeOverlay(
                            fadeHeight = 36.dp,
                        )
                    }
                },
                // Input is rendered manually at the bottom of the screen
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp)
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val recentlyRestoredNodeIds = vm.recentlyRestoredNodeIds.collectAsStateWithLifecycle().value
                    val conversationSnapshots = remember { mutableMapOf<Uuid, Conversation>() }
                    val chatListStateSnapshots = remember { mutableMapOf<Uuid, LazyListState>() }
                    val searchQuerySnapshots = remember { mutableMapOf<Uuid, String?>() }
                    SideEffect {
                        conversationSnapshots[conversation.id] = conversation
                        chatListStateSnapshots[conversation.id] = chatListState
                        searchQuerySnapshots[conversation.id] = initialSearchQuery
                    }
                    AnimatedContent(
                        targetState = conversation.id,
                        transitionSpec = {
                            if (initialState == targetState) {
                                fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                            } else {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(110))
                            }
                        },
                        label = "chat_conversation_content",
                        modifier = Modifier.fillMaxSize(),
                    ) { targetConversationId ->
                        val frameConversation = if (targetConversationId == conversation.id) {
                            conversation
                        } else {
                            conversationSnapshots[targetConversationId] ?: Conversation.ofId(targetConversationId)
                        }
                        val frameListState = if (targetConversationId == conversation.id) {
                            chatListState
                        } else {
                            chatListStateSnapshots.getOrPut(targetConversationId) { LazyListState() }
                        }
                        val frameInitialSearchQuery = if (targetConversationId == conversation.id) {
                            initialSearchQuery
                        } else {
                            searchQuerySnapshots[targetConversationId]
                        }
                        CompositionLocalProvider(
                            LocalChatAnimationsEnabled provides chatAnimationsEnabled
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (targetConversationId != conversation.id || chatListReady) 1f else 0f
                                    }
                            ) {
                                ChatList(
                                    innerPadding = PaddingValues(
                                        top = chatListTopPadding(toolbarPlacement),
                                        bottom = chatListBottomPadding(toolbarPlacement)
                                    ),
                                    conversation = frameConversation,
                                    state = frameListState,
                                    loading = targetConversationId == conversation.id && loadingJob != null,
                                    previewMode = previewMode,
                                    settings = setting,
                                    recentlyRestoredNodeIds = recentlyRestoredNodeIds,
                                    initialSearchQuery = frameInitialSearchQuery,
                                    searchQuery = chatSearchQuery,
                                    onSearchQueryChange = { chatSearchQuery = it },
                                    shareSelecting = isChatShareSelecting,
                                    selectedShareItems = selectedChatShareItems,
                                    onSelectedShareItemsChange = { selectedChatShareItems = it },
                                    contentMaxWidth = contentMaxWidth,
                                    onJumpToMessage = { index ->
                                        previewMode = false
                                        scope.launch {
                                            // Wait for AnimatedContent transition to complete before scrolling
                                            delay(350)
                                            frameListState.animateScrollToItem(index)
                                        }
                                    },
                                    onRegenerate = { message ->
                                        if (message.role == me.rerere.ai.core.MessageRole.USER) {
                                            // User message regeneration always truncates - show confirmation
                                            pendingUserRegenerateMessage = message
                                            showUserRegenerateConfirmDialog = true
                                        } else if (vm.canPreserveVersionHistory(message)) {
                                            // Simple assistant message - regenerate with version history
                                            vm.regenerateAtMessage(message, forceWipe = false)
                                        } else {
                                            // Complex assistant message - show confirmation dialog
                                            pendingRegenerateMessage = message
                                            showRegenerateConfirmDialog = true
                                        }
                                    },
                                    onEdit = {
                                        inputState.editingMessage = it.id
                                        inputState.setContents(it.parts)
                                    },
                                    onDelete = { message ->
                                        if (message.role == me.rerere.ai.core.MessageRole.USER) {
                                            // User message deletion removes all messages after - show confirmation
                                            pendingDeleteMessage = message
                                            showDeleteConfirmDialog = true
                                        } else {
                                            // Assistant message deletion - keep existing behavior with undo toast
                                            scope.launch {
                                                val backup = frameConversation
                                                val removedIds = vm.deleteMessage(message)
                                                toaster.show(
                                                    message = context.getString(R.string.message_deleted),
                                                    action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                                                        label = context.getString(R.string.undo),
                                                        onClick = {
                                                            vm.updateConversation(backup)
                                                            vm.markNodesAsRestored(removedIds)
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onUpdateMessage = { newNode ->
                                        val oldNode = frameConversation.messageNodes.find { it.id == newNode.id }
                                        if (oldNode != null) {
                                            if (oldNode.selectIndex != newNode.selectIndex) {
                                                vm.selectMessageNode(newNode.id, newNode.selectIndex)
                                            } else {
                                                vm.updateConversation(
                                                    frameConversation.copy(
                                                        messageNodes = frameConversation.messageNodes.map { node ->
                                                            if (node.id == newNode.id) {
                                                                newNode
                                                            } else {
                                                                node
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onForkMessage = {
                                        scope.launch {
                                            val forkConversation = vm.forkMessage(it)
                                            navigateToChatPage(navController, forkConversation.id)
                                        }
                                    },
                                )
                            }
                        }
                    }

                ChatExportSheet(
                    visible = showExportSheet,
                    onDismissRequest = {
                        showExportSheet = false
                        selectedChatShareItems = emptySet()
                    },
                    conversation = conversation,
                    selectedMessages = conversation.messageNodes
                        .filter { it.id in selectedChatShareItems }
                        .map { it.currentMessage }
                )

                val hasConversationContent = hasConversationMessages(conversation)
                val hasAnyPresetMessages = hasConversationPresetMessages(conversation, currentAssistant)
                val effectiveDisplaySetting = setting.getEffectiveDisplaySetting(currentAssistant)
                val scrollToBottomRevealThresholdPx = with(density) { 72.dp.roundToPx() }
                val showScrollToBottomButton by remember(
                    chatListState,
                    previewMode,
                    hasConversationContent,
                    scrollToBottomRevealThresholdPx,
                ) {
                    derivedStateOf {
                        if (previewMode || !hasConversationContent) {
                            false
                        } else {
                            val layoutInfo = chatListState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            if (totalItems <= 0) {
                                false
                            } else {
                                val lastContentIndex = (totalItems - 2).coerceAtLeast(0)
                                val lastContentItem = layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == lastContentIndex }
                                val lastContentBottom = lastContentItem?.let { it.offset + it.size }
                                val isNearBottom = lastContentBottom != null &&
                                    lastContentBottom <= layoutInfo.viewportEndOffset + scrollToBottomRevealThresholdPx
                                chatListState.canScrollForward && !isNearBottom
                            }
                        }
                    }
                }
                
                // Temporary chat overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isTemporaryChat && !hasConversationContent && !hasAnyPresetMessages,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HistoryToggleOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.temporary_chat_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                val headerStyle = effectiveDisplaySetting.newChatHeaderStyle
                val contentStyle = effectiveDisplaySetting.newChatContentStyle
                val showNewChatContent = headerStyle != me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE || contentStyle != me.rerere.rikkahub.data.datastore.NewChatContentStyle.NONE
                val showModeComposer = previewMode || isChatShareSelecting
                
                // Detect keyboard visibility
                val isKeyboardOpen = WindowInsets.isImeVisible
                
                // Hide new chat content when keyboard is open or text/media is in input
                val hasTextInput = inputState.textContent.text.isNotEmpty() || inputState.messageContent.isNotEmpty()
                val shouldShowNewChatContent = shouldShowNewChatContent(
                    isTemporaryChat = isTemporaryChat,
                    hasConversationMessages = hasConversationContent,
                    hasAnyPresetMessages = hasAnyPresetMessages,
                    showNewChatContent = showNewChatContent,
                    hasTextInput = hasTextInput,
                    isKeyboardOpen = isKeyboardOpen,
                ) && !showModeComposer
                
                // State for assistant picker triggered from header avatar
                var showHeaderAssistantPicker by remember { mutableStateOf(false) }
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = shouldShowNewChatContent,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 28.dp)
                ) {
                    NewChatContent(
                        assistant = currentAssistant,
                        headerStyle = headerStyle,
                        contentStyle = contentStyle,
                        showAvatarInHeader = effectiveDisplaySetting.newChatShowAvatar,
                        hasBackgroundImage = currentAssistant.background != null,
                        onTemplateClick = { prompt ->
                            // Set text and focus the input field to show keyboard
                            inputState.setMessageTextAndFocus(prompt, scope)
                        },
                        onNavigateToImageGen = {
                            navController.navigate(Screen.ImageGen)
                        },
                        onAvatarClick = {
                            showHeaderAssistantPicker = true
                        }
                    )
                }
                
                // Assistant picker sheet triggered from header avatar
                if (showHeaderAssistantPicker) {
                    me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
                        settings = setting,
                        currentAssistant = currentAssistant,
                        onAssistantSelected = { selectedAssistant ->
                            vm.setSelectedAssistant(selectedAssistant.id)
                        },
                        onNavigate = { selectedAssistant ->
                            showHeaderAssistantPicker = false
                            navigateToAssistantConversation(selectedAssistant)
                        },
                        onDismiss = { showHeaderAssistantPicker = false }
                    )
                }

                // Regeneration confirmation dialog for complex messages
                if (showRegenerateConfirmDialog && pendingRegenerateMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showRegenerateConfirmDialog = false
                            pendingRegenerateMessage = null
                        },
                        title = { Text(stringResource(R.string.chat_regenerate_message_title)) },
                        text = {
                            Text(stringResource(R.string.chat_regenerate_message_warning))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingRegenerateMessage?.let { message ->
                                        vm.regenerateAtMessage(message, forceWipe = true)
                                    }
                                    showRegenerateConfirmDialog = false
                                    pendingRegenerateMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.regenerate))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showRegenerateConfirmDialog = false
                                    pendingRegenerateMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // User message regeneration confirmation dialog
                if (showUserRegenerateConfirmDialog && pendingUserRegenerateMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showUserRegenerateConfirmDialog = false
                            pendingUserRegenerateMessage = null
                        },
                        title = { Text(stringResource(R.string.chat_regenerate_user_message_title)) },
                        text = {
                            Text(stringResource(R.string.chat_regenerate_user_message_warning))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingUserRegenerateMessage?.let { message ->
                                        vm.regenerateAtMessage(message, forceWipe = false)
                                    }
                                    showUserRegenerateConfirmDialog = false
                                    pendingUserRegenerateMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.regenerate))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showUserRegenerateConfirmDialog = false
                                    pendingUserRegenerateMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // User message delete confirmation dialog
                if (showDeleteConfirmDialog && pendingDeleteMessage != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showDeleteConfirmDialog = false
                            pendingDeleteMessage = null
                        },
                        title = { Text(stringResource(R.string.chat_delete_user_message_title)) },
                        text = {
                            Text(stringResource(R.string.chat_delete_user_message_warning))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingDeleteMessage?.let { message ->
                                        scope.launch {
                                            val backup = conversation
                                            val removedIds = vm.deleteMessage(message)
                                            toaster.show(
                                                message = context.getString(R.string.message_deleted),
                                                action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                                                    label = context.getString(R.string.undo),
                                                    onClick = {
                                                        vm.updateConversation(backup)
                                                        vm.markNodesAsRestored(removedIds)
                                                    }
                                                )
                                            )
                                        }
                                    }
                                    showDeleteConfirmDialog = false
                                    pendingDeleteMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDeleteConfirmDialog = false
                                    pendingDeleteMessage = null
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Gradient behind floating toolbar - hidden when showing new chat content
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasConversationContent || hasAnyPresetMessages || isTemporaryChat || !showNewChatContent,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                brush = if (toolbarPlacement == ChatToolbarPlacement.Bottom) {
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.97f)
                                        )
                                    )
                                } else {
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                        )
                                    )
                                }
                            )
                    )
                }

                val bottomToolbarContent: (@Composable () -> Unit)? = if (toolbarPlacement == ChatToolbarPlacement.Bottom) {
                    {
                        ChatToolbar(
                            placement = ChatToolbarPlacement.Bottom,
                            settings = setting,
                            currentAssistant = currentAssistant,
                            conversationInitialized = conversationInitialized,
                            conversation = conversation,
                            bigScreen = bigScreen,
                            drawerState = drawerState,
                            previewMode = previewMode,
                            isTemporaryChat = isTemporaryChat,
                            currentChatModel = currentChatModel,
                            isGenerating = isGenerating,
                            showCloseAction = previewMode || isChatShareSelecting,
                            vm = vm,
                            onNewChat = {
                                navigateToChatPage(navController)
                            },
                            onOpenOverflowMenu = {
                                showToolbarOverflowMenu = !showToolbarOverflowMenu
                            },
                            onCloseAction = {
                                showToolbarOverflowMenu = false
                                if (previewMode) {
                                    previewMode = false
                                }
                                if (isChatShareSelecting) {
                                    cancelChatShareSelection()
                                }
                            },
                            onUpdateSettings = { newSettings ->
                                vm.updateSettings(newSettings)
                            },
                            onSwitchAssistant = { assistant ->
                                navigateToAssistantConversation(assistant)
                            },
                            onToggleTemporaryChat = {
                                onManualTemporaryChatChange(!manualTemporaryChat)
                            }
                        )
                    }
                } else {
                    null
                }

                if (showModeComposer) {
                    ChatModeBottomControls(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(
                                if (inputMaxWidth != Dp.Unspecified) {
                                    Modifier.widthIn(max = inputMaxWidth)
                                } else {
                                    Modifier
                                }
                            ),
                        showSearch = previewMode,
                        searchQuery = chatSearchQuery,
                        onSearchQueryChange = { chatSearchQuery = it },
                        showShareSelection = isChatShareSelecting,
                        selectedCount = selectedChatShareItems.size,
                        allSelected = selectedChatShareItems.isNotEmpty() &&
                            selectedChatShareItems.size == conversation.messageNodes.size,
                        onCancelShareSelection = { cancelChatShareSelection() },
                        onToggleSelectAll = {
                            selectedChatShareItems = if (selectedChatShareItems.isNotEmpty()) {
                                emptySet()
                            } else {
                                conversation.messageNodes.map { it.id }.toSet()
                            }
                        },
                        onConfirmShareSelection = { confirmChatShareSelection() },
                        bottomAccessory = bottomToolbarContent,
                        bottomPadding = if (toolbarPlacement == ChatToolbarPlacement.Bottom) 12.dp else 24.dp,
                    )
                } else {
                    MinimalChatInput(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (inputMaxWidth != Dp.Unspecified) {
                                Modifier.widthIn(max = inputMaxWidth)
                            } else {
                                Modifier
                            }
                        ),
                    state = inputState,
                    settings = setting,
                    conversation = conversation,
                    mcpManager = vm.mcpManager,
                    chatSuggestions = conversation.chatSuggestions,
                    onClickSuggestion = { suggestion ->
                        if (currentChatModel != null) {
                            vm.handleMessageSend(
                                listOf(me.rerere.ai.ui.UIMessagePart.Text(suggestion)),
                                persistenceMode = activePersistenceMode
                            )
                        } else {
                            toaster.show(modelRequiredMessage, type = ToastType.Error)
                        }
                    },
                    onCancelClick = {
                        loadingJob?.cancel()
                    },
                    enableSearch = enableWebSearch,
                    onToggleSearch = {
                        if (enableWebSearch) {
                            vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Off)
                        } else {
                            if (setting.searchServices.isNotEmpty()) {
                                val validIndex = lastProviderIndex.coerceIn(0, setting.searchServices.lastIndex)
                                vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(validIndex))
                            }
                        }
                    },
                    onSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            if (currentChatModel == null) {
                                toaster.show(modelRequiredMessage, type = ToastType.Error)
                                return@MinimalChatInput
                            }
                            vm.handleMessageSend(
                                inputState.getContents(),
                                persistenceMode = activePersistenceMode
                            )
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            if (currentChatModel == null) {
                                toaster.show(modelRequiredMessage, type = ToastType.Error)
                                return@MinimalChatInput
                            }
                            vm.handleMessageSend(
                                content = inputState.getContents(),
                                answer = false,
                                persistenceMode = activePersistenceMode
                            )
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(it)
                    },
                    onUpdateAssistant = {
                        vm.updateConversationAssistant(it)
                    },
                    onUpdateSearchService = { index ->
                        vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(index))
                    },
                    onClearContext = {
                        vm.handleMessageTruncate()
                    },
                    onUpdateConversation = { updatedConversation ->
                        vm.updateConversation(updatedConversation)
                    },
                    onToolApproval = { toolCallId, approved, reason, answer ->
                        vm.handleToolApproval(
                            toolCallId = toolCallId,
                            approved = approved,
                            reason = reason,
                            answer = answer,
                        )
                    },
                    onNavigateToLorebook = { lorebookId ->
                        navController.navigate(Screen.SettingLorebookDetail(lorebookId))
                    },
                    onRefreshContext = { vm.refreshContext() },
                    onDeleteFile = { vm.deleteFile(it) },
                    bottomAccessory = bottomToolbarContent,
                    showScrollToBottomButton = showScrollToBottomButton,
                    onScrollToBottomClick = {
                        scope.launch {
                            val targetIndex = (chatListState.layoutInfo.totalItemsCount - 1)
                                .coerceAtLeast(0)
                            chatListState.animateScrollToItem(targetIndex)
                        }
                    },
                    bottomPadding = if (toolbarPlacement == ChatToolbarPlacement.Bottom) 12.dp else 24.dp,
                )
                }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showToolbarOverflowMenu,
                        enter = fadeIn(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.75f,
                                stiffness = 360f
                            )
                        ) + scaleIn(
                            initialScale = 0.96f,
                            transformOrigin = chatToolbarOverflowMenuTransformOrigin(toolbarPlacement),
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.75f,
                                stiffness = 360f
                            )
                        ),
                        exit = fadeOut(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.85f,
                                stiffness = 420f
                            )
                        ) + scaleOut(
                            targetScale = 0.96f,
                            transformOrigin = chatToolbarOverflowMenuTransformOrigin(toolbarPlacement),
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.85f,
                                stiffness = 420f
                            )
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                            ChatToolbarOverflowMenu(
                                placement = toolbarPlacement,
                                previewMode = previewMode,
                                hasConversationContent = conversation.messageNodes.isNotEmpty(),
                                chatListState = chatListState,
                                onDismissRequest = { showToolbarOverflowMenu = false },
                            onSearchClick = {
                                showToolbarOverflowMenu = false
                                previewMode = !previewMode
                            },
                            onShareClick = {
                                startChatShareSelection()
                            }
                        )
                    }
                }
            }
        }
        }
    }

}

}

@Composable
private fun ChatToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "chat_toolbar_icon_scale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(999.dp))
            .let { modifier ->
                if (enabled) {
                    modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onClick()
                        }
                    )
                } else {
                    modifier
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ChatModeBottomControls(
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showShareSelection: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onCancelShareSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirmShareSelection: () -> Unit,
    bottomAccessory: (@Composable () -> Unit)?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = when {
                    showSearch -> "search"
                    showShareSelection -> "share"
                    else -> "none"
                },
                transitionSpec = {
                    (fadeIn(animationSpec = tween(120)) + scaleIn(
                        initialScale = 0.98f,
                        animationSpec = tween(120)
                    )) togetherWith (fadeOut(animationSpec = tween(90)) + scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(90)
                    ))
                },
                label = "chat_mode_composer"
            ) { mode ->
                when (mode) {
                    "search" -> ChatSearchModeBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                    )
                    "share" -> ChatShareSelectionModeBar(
                        selectedCount = selectedCount,
                        allSelected = allSelected,
                        onCancel = onCancelShareSelection,
                        onToggleSelectAll = onToggleSelectAll,
                        onConfirm = onConfirmShareSelection,
                    )
                    else -> Spacer(Modifier.height(56.dp))
                }
            }

            bottomAccessory?.invoke()
        }
    }
}

@Composable
private fun ChatSearchModeBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val searchFieldShape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .lastChatBlurEffect(containerColor, searchFieldShape),
        shape = searchFieldShape,
        color = blurredContainerColor(containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search messages") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = searchFieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatShareSelectionModeBar(
    selectedCount: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val shape = RoundedCornerShape(999.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        shape = shape,
        color = blurredContainerColor(containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .lastChatBlurEffect(containerColor, shape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            Tooltip(tooltip = { Text(stringResource(R.string.chat_clear_selection)) }) {
                IconButton(
                    modifier = Modifier.size(44.dp),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onCancel()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.weight(1f))

            Tooltip(tooltip = { Text(stringResource(R.string.select_all)) }) {
                IconButton(
                    modifier = Modifier.size(44.dp),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onToggleSelectAll()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (allSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Tooltip(tooltip = { Text(stringResource(R.string.confirm)) }) {
                FilledIconButton(
                    modifier = Modifier.size(44.dp),
                    enabled = selectedCount > 0,
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        onConfirm()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatToolbarOverflowMenu(
    placement: ChatToolbarPlacement,
    previewMode: Boolean,
    hasConversationContent: Boolean,
    chatListState: LazyListState,
    onDismissRequest: () -> Unit,
    onSearchClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    BackHandler(onBack = onDismissRequest)

    var dragDismissInProgress by remember { mutableStateOf(false) }
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val menuShape = RoundedCornerShape(24.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.background)
    val menuTopPadding = if (placement == ChatToolbarPlacement.Top) 64.dp else 0.dp
    val menuBottomPadding = if (placement == ChatToolbarPlacement.Bottom) 72.dp else 0.dp
    val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragDismissInProgress) 0f else 0.16f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.85f,
            stiffness = 420f
        ),
        label = "chat_toolbar_menu_scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismissRequest
            )
            .pointerInput(chatListState) {
                detectDragGestures(
                    onDragStart = {
                        dragDismissInProgress = true
                    },
                    onDragEnd = {
                        dragDismissInProgress = false
                        onDismissRequest()
                    },
                    onDragCancel = {
                        dragDismissInProgress = false
                        onDismissRequest()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        chatListState.dispatchRawDelta(-dragAmount.y)
                    }
                )
            }
    ) {
        if (!dragDismissInProgress) {
            Surface(
                shape = menuShape,
                color = blurredContainerColor(containerColor),
                border = border,
                modifier = Modifier
                    .align(
                        if (placement == ChatToolbarPlacement.Top) {
                            Alignment.TopEnd
                        } else {
                            Alignment.BottomEnd
                        }
                    )
                    .then(
                        if (placement == ChatToolbarPlacement.Top) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(top = menuTopPadding, bottom = menuBottomPadding, end = 16.dp)
                    .widthIn(min = 196.dp, max = 260.dp)
                    .lastChatBlurEffect(containerColor, menuShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                ) {
                    ChatToolbarOverflowMenuItem(
                        label = stringResource(R.string.search_ability_search),
                        icon = if (previewMode) Icons.Rounded.Close else Icons.Rounded.Search,
                        onClick = onSearchClick
                    )
                    ChatToolbarOverflowMenuItem(
                        label = stringResource(R.string.share),
                        icon = Icons.Rounded.Share,
                        enabled = hasConversationContent,
                        onClick = onShareClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatToolbarOverflowMenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "chat_toolbar_menu_item_scale"
    )
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onClick()
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class TopBarActionMode {
    CompactNewChat,
    NewChat,
    TemporaryNewChat,
    InChat,
}

private data class TopBarActionState(
    val mode: TopBarActionMode,
    val assistantId: Uuid?,
    val showCloseAction: Boolean,
)

private val DefaultTopBarActionState = TopBarActionState(
    mode = TopBarActionMode.InChat,
    assistantId = null,
    showCloseAction = false,
)

@Composable
private fun ChatToolbarActionPill(
    actionState: TopBarActionState,
    currentAssistant: Assistant,
    topPillSize: Dp,
    buttonShape: RoundedCornerShape,
    containerColor: Color,
    border: BorderStroke,
    onNewChat: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onCloseAction: () -> Unit,
    onToggleTemporaryChat: () -> Unit,
    onOpenAssistantPicker: () -> Unit,
) {
    val fullPillWidth = topPillSize * 2f
    val targetWidth = if (actionState.mode == TopBarActionMode.CompactNewChat) {
        topPillSize
    } else {
        fullPillWidth
    }
    val pillWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetWidth,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "top_pill_width"
    )
    val compactAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (actionState.mode == TopBarActionMode.CompactNewChat) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "top_pill_compact_alpha"
    )
    val newChatAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (actionState.mode == TopBarActionMode.NewChat) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "top_pill_new_chat_alpha"
    )
    val temporaryAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (actionState.mode == TopBarActionMode.TemporaryNewChat) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "top_pill_temporary_alpha"
    )
    val inChatAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (actionState.mode == TopBarActionMode.InChat) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "top_pill_in_chat_alpha"
    )

    Surface(
        shape = buttonShape,
        color = blurredContainerColor(containerColor),
        border = border,
        modifier = Modifier
            .size(width = pillWidth, height = topPillSize)
            .clip(buttonShape)
            .lastChatBlurEffect(containerColor, buttonShape)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (compactAlpha > 0f) {
                ChatToolbarCompactLayer(
                    alpha = compactAlpha,
                    enabled = actionState.mode == TopBarActionMode.CompactNewChat,
                    topPillSize = topPillSize,
                    onToggleTemporaryChat = onToggleTemporaryChat
                )
            }
            if (newChatAlpha > 0f) {
                ChatToolbarNewChatLayer(
                    alpha = newChatAlpha,
                    enabled = actionState.mode == TopBarActionMode.NewChat,
                    topPillSize = topPillSize,
                    fullPillWidth = fullPillWidth,
                    currentAssistant = currentAssistant,
                    temporary = false,
                    onToggleTemporaryChat = onToggleTemporaryChat,
                    onOpenAssistantPicker = onOpenAssistantPicker
                )
            }
            if (temporaryAlpha > 0f) {
                ChatToolbarNewChatLayer(
                    alpha = temporaryAlpha,
                    enabled = actionState.mode == TopBarActionMode.TemporaryNewChat,
                    topPillSize = topPillSize,
                    fullPillWidth = fullPillWidth,
                    currentAssistant = currentAssistant,
                    temporary = true,
                    onToggleTemporaryChat = onToggleTemporaryChat,
                    onOpenAssistantPicker = onOpenAssistantPicker
                )
            }
            if (inChatAlpha > 0f) {
                ChatToolbarInChatLayer(
                    alpha = inChatAlpha,
                    enabled = actionState.mode == TopBarActionMode.InChat,
                    topPillSize = topPillSize,
                    fullPillWidth = fullPillWidth,
                    showCloseAction = actionState.showCloseAction,
                    onNewChat = onNewChat,
                    onOpenOverflowMenu = onOpenOverflowMenu,
                    onCloseAction = onCloseAction
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatToolbarCompactLayer(
    alpha: Float,
    enabled: Boolean,
    topPillSize: Dp,
    onToggleTemporaryChat: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(topPillSize)
            .graphicsLayer {
                this.alpha = alpha
                val layerScale = 0.92f + alpha * 0.08f
                scaleX = layerScale
                scaleY = layerScale
            },
        contentAlignment = Alignment.Center
    ) {
        ChatToolbarIconButton(
            icon = Icons.Rounded.HistoryToggleOff,
            contentDescription = "Temporary Chat",
            size = topPillSize,
            enabled = enabled,
            onClick = onToggleTemporaryChat
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatToolbarNewChatLayer(
    alpha: Float,
    enabled: Boolean,
    topPillSize: Dp,
    fullPillWidth: Dp,
    currentAssistant: Assistant,
    temporary: Boolean,
    onToggleTemporaryChat: () -> Unit,
    onOpenAssistantPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(width = fullPillWidth, height = topPillSize)
            .graphicsLayer {
                this.alpha = alpha
                val layerScale = 0.92f + alpha * 0.08f
                scaleX = layerScale
                scaleY = layerScale
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatToolbarIconButton(
            icon = if (temporary) Icons.Rounded.History else Icons.Rounded.HistoryToggleOff,
            contentDescription = if (temporary) "Make Normal Chat" else "Temporary Chat",
            size = topPillSize,
            enabled = enabled,
            onClick = onToggleTemporaryChat
        )
        Box(
            modifier = Modifier.size(topPillSize),
            contentAlignment = Alignment.Center
        ) {
            me.rerere.rikkahub.ui.components.ui.UIAvatar(
                name = currentAssistant.name.ifBlank { "Character" },
                value = currentAssistant.avatar,
                modifier = Modifier.size(30.dp),
                onClick = if (enabled) onOpenAssistantPicker else null
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatToolbarInChatLayer(
    alpha: Float,
    enabled: Boolean,
    topPillSize: Dp,
    fullPillWidth: Dp,
    showCloseAction: Boolean,
    onNewChat: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onCloseAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(width = fullPillWidth, height = topPillSize)
            .graphicsLayer {
                this.alpha = alpha
                val layerScale = 0.92f + alpha * 0.08f
                scaleX = layerScale
                scaleY = layerScale
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatToolbarIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "New Message",
            size = topPillSize,
            enabled = enabled,
            onClick = onNewChat
        )
        ChatToolbarIconButton(
            icon = if (showCloseAction) Icons.Rounded.Close else Icons.Rounded.MoreVert,
            contentDescription = if (showCloseAction) {
                stringResource(R.string.banner_dismiss)
            } else {
                stringResource(R.string.more_options)
            },
            size = topPillSize,
            enabled = enabled,
            onClick = if (showCloseAction) onCloseAction else onOpenOverflowMenu
        )
    }
}


@Composable
fun UpdatePill(
    bigScreen: Boolean,
    height: Dp,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val pillShape = RoundedCornerShape(999.dp)

    Surface(
        onClick = {
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
            onClick()
        },
        shape = pillShape,
        color = blurredContainerColor(containerColor),
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = Modifier
            .height(height)
            .lastChatBlurEffect(containerColor, pillShape)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (bigScreen) stringResource(R.string.update_available) else "New Update",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            
            Spacer(Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                    onDismiss()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
private fun ChatToolbar(
    placement: ChatToolbarPlacement,
    settings: Settings,
    currentAssistant: Assistant,
    conversationInitialized: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    isTemporaryChat: Boolean,
    currentChatModel: Model? = null,
    isGenerating: Boolean = false,
    showCloseAction: Boolean,
    showTopFade: Boolean = true,
    vm: ChatVM,
    onNewChat: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onCloseAction: () -> Unit,
    onUpdateSettings: (Settings) -> Unit,
    onSwitchAssistant: (Assistant) -> Unit,
    onToggleTemporaryChat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val topContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val topContainerBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.background)
    val buttonShape = RoundedCornerShape(999.dp)
    val topPillSize = 48.dp
    // State for assistant picker - must be at function level for proper recomposition
    var showAssistantPicker by remember { mutableStateOf(false) }
    val isEmpty = !conversation.messageNodes.any { it.role == me.rerere.ai.core.MessageRole.USER }
    val rawActionMode = run {
        val hasPresetMessages = currentAssistant.presetMessages.isNotEmpty()
        val effectiveDisplay = settings.getEffectiveDisplaySetting(currentAssistant)
        val headerShowsAvatar = effectiveDisplay.newChatShowAvatar && (
            effectiveDisplay.newChatHeaderStyle == me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.BIG_ICON ||
                effectiveDisplay.newChatHeaderStyle == me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.GREETING
            )
        val shouldUseCompactTemporaryToggle = !hasPresetMessages && headerShowsAvatar
        when {
            isEmpty && !isTemporaryChat && shouldUseCompactTemporaryToggle -> TopBarActionMode.CompactNewChat
            isEmpty && !isTemporaryChat -> TopBarActionMode.NewChat
            isEmpty && isTemporaryChat -> TopBarActionMode.TemporaryNewChat
            else -> TopBarActionMode.InChat
        }
    }
    val rawActionState = TopBarActionState(
        mode = rawActionMode,
        assistantId = currentAssistant.id.takeUnless { rawActionMode == TopBarActionMode.InChat },
        showCloseAction = showCloseAction && rawActionMode == TopBarActionMode.InChat,
    )
    var displayedActionState by remember {
        mutableStateOf(DefaultTopBarActionState)
    }

    LaunchedEffect(rawActionState, conversationInitialized) {
        if (conversationInitialized) {
            if (displayedActionState != rawActionState) {
                withFrameNanos { }
            }
            displayedActionState = rawActionState
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (placement == ChatToolbarPlacement.Top && showTopFade) {
            ChatTopFadeOverlay(
                fadeHeight = 96.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Row(
            modifier = Modifier
                .then(
                    if (placement == ChatToolbarPlacement.Top) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    }
                )
                .fillMaxWidth()
                .padding(
                    vertical = if (placement == ChatToolbarPlacement.Top) 8.dp else 0.dp,
                    horizontal = if (placement == ChatToolbarPlacement.Top) 16.dp else 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!bigScreen) {
                Surface(
                    onClick = {
                        scope.launch { drawerState.open() }
                    },
                    shape = buttonShape,
                    color = blurredContainerColor(topContainerColor),
                    border = topContainerBorder,
                    modifier = Modifier
                        .size(topPillSize)
                        .lastChatBlurEffect(topContainerColor, buttonShape)
                ) {
                    Box(
                        modifier = Modifier.size(topPillSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Menu, "Messages")
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            
            val isForcedCheck by vm.isForcedCheck.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var showUpdateDialog by remember { mutableStateOf(false) }
            var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
            val currentVersion = remember { me.rerere.rikkahub.utils.Version(BuildConfig.VERSION_NAME) }
            val isNewChat = isEmpty
            val shouldObserveUpdates = (settings.displaySetting.checkForUpdates || isForcedCheck) &&
                isNewChat

            // Always collect update state so AnimatedVisibility can fade out gracefully
            // even when shouldObserveUpdates becomes false mid-session.
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            @Suppress("UNCHECKED_CAST")
            val updateInfo = ((updateState as? me.rerere.rikkahub.utils.UiState.Success<*>)?.data as? me.rerere.rikkahub.utils.UpdateInfo)
            val latestVersion = remember(updateInfo) {
                updateInfo?.let { me.rerere.rikkahub.utils.Version(it.version) }
            }
            val isNewer = latestVersion != null && latestVersion > currentVersion
            val isIgnored = remember(updateInfo, isForcedCheck) {
                if (updateInfo != null)
                    vm.updateChecker.isUpdateIgnored(context, updateInfo.version, forceCheck = isForcedCheck)
                else true
            }
            val showUpdatePill = shouldObserveUpdates &&
                updateInfo != null &&
                (isNewer || isForcedCheck) &&
                !isIgnored &&
                dismissedUpdateVersion != updateInfo?.version

            androidx.compose.animation.AnimatedVisibility(
                visible = showUpdatePill,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                UpdatePill(
                    bigScreen = bigScreen,
                    height = topPillSize,
                    onDismiss = { dismissedUpdateVersion = updateInfo?.version },
                    onClick = { showUpdateDialog = true }
                )
            }

            if (showUpdateDialog && updateInfo != null) {
                UpdateDialog(
                    info = updateInfo,
                    updateChecker = vm.updateChecker,
                    onDismiss = {
                        showUpdateDialog = false
                    },
                    onLater = {
                        showUpdateDialog = false
                        dismissedUpdateVersion = updateInfo.version
                    },
                    onIgnore = {
                        vm.updateChecker.ignoreUpdate(context, updateInfo.version)
                        vm.updateChecker.clearForcedCheck()
                        showUpdateDialog = false
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            ChatToolbarActionPill(
                actionState = displayedActionState,
                currentAssistant = currentAssistant,
                topPillSize = topPillSize,
                buttonShape = buttonShape,
                containerColor = topContainerColor,
                border = topContainerBorder,
                onNewChat = onNewChat,
                onOpenOverflowMenu = onOpenOverflowMenu,
                onCloseAction = onCloseAction,
                onToggleTemporaryChat = onToggleTemporaryChat,
                onOpenAssistantPicker = { showAssistantPicker = true }
            )
        }
    }
    
    // Assistant picker sheet - outside TopAppBar for proper state handling
    if (showAssistantPicker) {
        me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
            settings = settings,
            currentAssistant = currentAssistant,
            onAssistantSelected = { selectedAssistant ->
                onUpdateSettings(settings.copy(assistantId = selectedAssistant.id))
            },
            onNavigate = { selectedAssistant ->
                showAssistantPicker = false
                onSwitchAssistant(selectedAssistant)
            },
            onDismiss = { showAssistantPicker = false }
        )
    }
}
