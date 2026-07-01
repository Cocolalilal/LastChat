package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.search.SearchServiceOptions
import coil3.compose.AsyncImage
import me.rerere.ai.ui.UIMessagePart
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.ui.zIndex
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.verticalScroll
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.searchProviderIconUri
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.resolveConversationContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.canManuallySummarizeConversation
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.hasManualSkillSelectionOverride
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.withoutSkillSelectionOverride
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.crop.CropImageScreen
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionMicrophone
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalSTTState
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.AskUserAnswer
import me.rerere.rikkahub.data.ai.tools.AskUserAnswerPayload
import me.rerere.rikkahub.data.ai.tools.AskUserOption
import me.rerere.rikkahub.data.ai.tools.AskUserQuestionnaire
import me.rerere.rikkahub.data.ai.tools.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.ai.tools.findPendingAskUserToolCall
import me.rerere.rikkahub.data.ai.tools.toJsonElement
import me.rerere.rikkahub.data.repository.ChatAttachmentManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant
import kotlin.uuid.Uuid
import org.koin.compose.koinInject

/**
 * Minimal ChatGPT-style input bar with bottom sheet picker.
 * Shows a simple input bar with + button, text field, and send button.
 * The + button opens a bottom sheet with file upload, model picker, and other options.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun MinimalChatInput(
    state: ChatInputState,
    conversation: Conversation,
    settings: Settings,
    mcpManager: McpManager,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    chatSuggestions: List<String> = emptyList(),
    onClickSuggestion: (String) -> Unit = {},
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onToolApproval: (toolCallId: String, approved: Boolean, reason: String, answer: String?) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onClearContext: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    onNavigateToLorebook: (String) -> Unit = {},
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult = {
        ChatService.ContextRefreshResult(
            success = false,
            errorResId = R.string.context_refresh_no_summarizer,
        )
    },
    onDeleteFile: (Uri) -> Unit = {},
    bottomAccessory: @Composable (() -> Unit)? = null,
    showScrollToBottomButton: Boolean = false,
    onScrollToBottomClick: () -> Unit = {},
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val conversationContext = remember(settings, conversation) {
        settings.resolveConversationContext(conversation)
    }
    val assistant = conversationContext.assistant
    val currentChatModel = conversationContext.chatModel
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val workspaceRepository = koinInject<WorkspaceRepository>()
    val modelCatalog = koinInject<ModelCatalogService>()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val availableSkills = remember(settings.skills) {
        settings.skills
    }
    val availableSkillIds = remember(availableSkills) { availableSkills.map { it.id }.toSet() }
    val assistantAvailableSkillIds = remember(settings.skills, assistant.id) {
        settings.skills.filter { it.isAvailableForAssistant(assistant.id) }.map { it.id }.toSet()
    }
    val pendingQuestionnaire = remember(conversation.messageNodes) {
        conversation.currentMessages.findPendingAskUserToolCall()
    }
    val isQuestionnaireActive = pendingQuestionnaire != null
    val pendingToolApproval = remember(conversation.messageNodes, pendingQuestionnaire?.toolCallId) {
        if (pendingQuestionnaire != null) {
            null
        } else {
            conversation.currentMessages.findPendingToolApproval()
        }
    }
    val isToolApprovalActive = pendingToolApproval != null
    val questionnaire = pendingQuestionnaire?.questionnaire
    val questionnaireToolCallId = pendingQuestionnaire?.toolCallId
    var questionnaireIndex by rememberSaveable(questionnaireToolCallId) { mutableStateOf(0) }
    var questionnaireSelectedOptions by rememberSaveable(questionnaireToolCallId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var questionnaireCustomAnswers by rememberSaveable(questionnaireToolCallId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val questionnaireTextState = remember(questionnaireToolCallId) { TextFieldState() }
    val toolApprovalTextState = remember(pendingToolApproval?.toolCallId) { TextFieldState() }
    val currentQuestion = questionnaire?.questions?.getOrNull(questionnaireIndex)
    val isFinalQuestion = questionnaire != null && questionnaireIndex == questionnaire.questions.lastIndex

    // OLED dark mode handling for picker sheet
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = me.rerere.rikkahub.ui.theme.LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    // Picker sheet styling - optical roundness: outer (40dp) = button corners (24dp) + padding (16dp)
    // Sheet uses surfaceContainerLow always, buttons inside handle OLED colors
    val pickerSheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    val pickerSheetShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    
    // Camera permission - must be in parent, not inside ModalBottomSheet
    val cameraPermission = rememberPermissionState(PermissionCamera)
    val microphonePermission = rememberPermissionState(PermissionMicrophone)
    val stt = LocalSTTState.current
    val sttState by stt.state.collectAsStateWithLifecycle()
    val hasSelectedSttProvider = settings.sttModelId != null
    var sttDraft by remember { mutableStateOf("") }
    var acceptSttWhenIdle by remember { mutableStateOf(false) }
    var discardSttWhenIdle by remember { mutableStateOf(false) }
    val sttRecording = sttState.isRecording
    val sttFinalizing = sttState.status == me.rerere.asr.ASRStatus.Stopping

    LaunchedEffect(sttState.errorMessage) {
        sttState.errorMessage?.let { error ->
            toaster.show(error, type = me.rerere.rikkahub.ui.components.ui.ToastType.Error)
            stt.clearError()
        }
    }

    fun startSttRecording() {
        if (!microphonePermission.allRequiredPermissionsGranted) {
            microphonePermission.requestPermissions()
            return
        }
        sttDraft = ""
        acceptSttWhenIdle = false
        discardSttWhenIdle = false
        keyboardController?.hide()
        stt.start { transcript ->
            sttDraft = transcript
        }
    }

    fun stopSttRecording(accept: Boolean) {
        acceptSttWhenIdle = accept
        discardSttWhenIdle = !accept
        stt.stop()
    }

    LaunchedEffect(sttState.transcript) {
        if (sttState.transcript.isNotBlank()) {
            sttDraft = sttState.transcript
        }
    }

    LaunchedEffect(sttRecording, sttFinalizing, acceptSttWhenIdle, discardSttWhenIdle) {
        if (!sttRecording && !sttFinalizing && (acceptSttWhenIdle || discardSttWhenIdle)) {
            if (acceptSttWhenIdle) {
                delay(220)
                val transcript = sttDraft.trim()
                
                if (transcript.isNotBlank()) {
                    val prefix = state.textContent.text.toString()
                    state.setMessageText(
                        if (prefix.isBlank()) transcript else "$prefix $transcript"
                    )
                    
                    runCatching { state.focusRequester.requestFocus() }
                }
            }
            sttDraft = ""
            acceptSttWhenIdle = false
            discardSttWhenIdle = false
        }
    }
    
    var showPicker by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var isExpandedFullScreen by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<PendingImageCrop?>(null) }

    LaunchedEffect(questionnaireToolCallId, questionnaire?.questions?.size) {
        if (questionnaire == null) {
            questionnaireIndex = 0
            questionnaireTextState.setTextAndPlaceCursorAtEnd("")
        } else {
            questionnaireIndex = questionnaireIndex.coerceIn(0, questionnaire.questions.lastIndex)
            val initialText = questionnaire.questions
                .getOrNull(questionnaireIndex)
                ?.let { question -> questionnaireCustomAnswers[question.id].orEmpty() }
                .orEmpty()
            questionnaireTextState.setTextAndPlaceCursorAtEnd(initialText)
        }
    }

    LaunchedEffect(currentQuestion?.id) {
        val nextText = currentQuestion?.let { questionnaireCustomAnswers[it.id].orEmpty() }.orEmpty()
        if (questionnaireTextState.text.toString() != nextText) {
            questionnaireTextState.setTextAndPlaceCursorAtEnd(nextText)
        }
    }
    LaunchedEffect(currentQuestion?.id, questionnaireTextState.text.toString()) {
        val question = currentQuestion ?: return@LaunchedEffect
        questionnaireCustomAnswers = questionnaireCustomAnswers + (question.id to questionnaireTextState.text.toString())
    }
    LaunchedEffect(pendingToolApproval?.toolCallId) {
        toolApprovalTextState.setTextAndPlaceCursorAtEnd("")
    }

    // Collapse picker when keyboard opens
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            showPicker = false
        } else {
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(isQuestionnaireActive, isToolApprovalActive) {
        if (isQuestionnaireActive || isToolApprovalActive) {
            showPicker = false
        }
    }

    fun buildQuestionnairePayload(dismissed: Boolean): String? {
        val activeQuestionnaire = questionnaire ?: return null
        val payload = AskUserAnswerPayload(
            answers = activeQuestionnaire.questions.map { question ->
                val custom = questionnaireCustomAnswers[question.id]?.trim().orEmpty()
                val selectedOption = questionnaireSelectedOptions[question.id]?.trim().orEmpty()
                when {
                    custom.isNotBlank() -> AskUserAnswer(
                        id = question.id,
                        status = "answered",
                        source = "custom",
                        value = custom,
                    )

                    selectedOption.isNotBlank() -> AskUserAnswer(
                        id = question.id,
                        status = "answered",
                        source = "option",
                        value = selectedOption,
                    )

                    else -> AskUserAnswer(
                        id = question.id,
                        status = "skipped",
                    )
                }
            },
            dismissed = dismissed,
        )
        return JsonInstantPretty.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            payload.toJsonElement()
        )
    }

    fun submitQuestionnaire(dismissed: Boolean) {
        val toolCallId = questionnaireToolCallId ?: return
        val payload = buildQuestionnairePayload(dismissed) ?: return
        keyboardController?.hide()
        haptics.perform(if (dismissed) HapticPattern.Pop else HapticPattern.Send)
        onToolApproval(toolCallId, true, "", payload)
    }

    fun approvePendingTool(alwaysApproveWorkspace: Boolean) {
        val pending = pendingToolApproval ?: return
        keyboardController?.hide()
        haptics.perform(if (alwaysApproveWorkspace) HapticPattern.Success else HapticPattern.Send)
        if (alwaysApproveWorkspace && pending.isWorkspaceTool) {
            val workspaceId = assistant.workspaceId?.toString()
            if (workspaceId != null) {
                scope.launch {
                    workspaceRepository.setToolApproval(workspaceId, pending.toolName, needsApproval = false)
                    onToolApproval(pending.toolCallId, true, "", null)
                }
                return
            }
        }
        onToolApproval(pending.toolCallId, true, "", null)
    }

    fun denyPendingToolWithInstruction() {
        val pending = pendingToolApproval ?: return
        val reason = toolApprovalTextState.text.toString().trim()
        if (reason.isBlank()) {
            haptics.perform(HapticPattern.Pop)
            state.focusRequester.requestFocus()
            keyboardController?.show()
            return
        }
        keyboardController?.hide()
        haptics.perform(HapticPattern.Send)
        onToolApproval(pending.toolCallId, false, reason, null)
    }

    fun advanceQuestionnaire() {
        val activeQuestionnaire = questionnaire ?: return
        if (isFinalQuestion) {
            submitQuestionnaire(dismissed = false)
            return
        }
        questionnaireIndex = (questionnaireIndex + 1).coerceAtMost(activeQuestionnaire.questions.lastIndex)
        haptics.perform(HapticPattern.Pop)
    }

    fun sendMessage() {
        if (isQuestionnaireActive) {
            val question = currentQuestion
            if (question != null) {
                val trimmed = questionnaireTextState.text.toString().trim()
                questionnaireCustomAnswers = questionnaireCustomAnswers + (question.id to trimmed)
            }
            advanceQuestionnaire()
            return
        }
        if (isToolApprovalActive) {
            denyPendingToolWithInstruction()
            return
        }
        keyboardController?.hide()
        haptics.perform(HapticPattern.Send)
        if (state.loading) onCancelClick() else onSendClick()
    }

    fun replaceCroppedImage(original: PendingImageCrop, croppedUri: Uri) {
        scope.launch {
            val importedUri = withContext(Dispatchers.IO) {
                ChatAttachmentManager.importChatFiles(listOf(croppedUri)).firstOrNull()
            }
            if (importedUri == null) {
                Log.w("MinimalChatInput", "Failed to import cropped image: $croppedUri")
                toaster.show(context.getString(R.string.chat_input_selected_image_failed))
                withContext(Dispatchers.IO) {
                    if (croppedUri.scheme == "file") {
                        runCatching { File(croppedUri.path.orEmpty()).delete() }
                    }
                }
                return@launch
            }

            state.replaceAttachment(
                instanceId = original.instanceId,
                part = UIMessagePart.Image(importedUri.toString()),
            )
            withContext(Dispatchers.IO) {
                if (croppedUri.scheme == "file") {
                    runCatching { File(croppedUri.path.orEmpty()).delete() }
                }
            }
            haptics.perform(HapticPattern.Success)
        }
    }

    imageToCrop?.let { image ->
        CropImageScreen(
            sourceUri = image.image.url.toUri(),
            onCropComplete = { croppedUri ->
                imageToCrop = null
                replaceCroppedImage(image, croppedUri)
            },
            onCancel = {
                imageToCrop = null
                haptics.perform(HapticPattern.Pop)
            }
        )
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val showSuggestions = !isQuestionnaireActive && !isToolApprovalActive && chatSuggestions.isNotEmpty()
            val showScrollToBottom = !isQuestionnaireActive && !isToolApprovalActive && showScrollToBottomButton

            // Suggestions and scroll-to-bottom affordance share a row so they never overlap.
            androidx.compose.animation.AnimatedVisibility(
                visible = showSuggestions || showScrollToBottom,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSuggestions) {
                        ChatSuggestionsRow(
                            modifier = Modifier.weight(1f),
                            suggestions = chatSuggestions,
                            onClickSuggestion = onClickSuggestion
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        ChatScrollToBottomButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                onScrollToBottomClick()
                            }
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isQuestionnaireActive && questionnaire != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                questionnaire?.let { activeQuestionnaire ->
                    CharacterQuestionsCard(
                        questionnaire = activeQuestionnaire,
                        currentIndex = questionnaireIndex,
                        selectedOptionLabel = currentQuestion?.let { question ->
                            questionnaireSelectedOptions[question.id]
                        },
                        onPrevious = {
                            questionnaireIndex = (questionnaireIndex - 1).coerceAtLeast(0)
                            haptics.perform(HapticPattern.Pop)
                        },
                        onNext = {
                            questionnaireIndex = (questionnaireIndex + 1)
                                .coerceAtMost(activeQuestionnaire.questions.lastIndex)
                            haptics.perform(HapticPattern.Pop)
                        },
                        onDismiss = {
                            currentQuestion?.let { question ->
                                questionnaireCustomAnswers =
                                    questionnaireCustomAnswers + (question.id to questionnaireTextState.text.toString())
                            }
                            submitQuestionnaire(dismissed = true)
                        },
                        onSelectOption = { option ->
                            val question = currentQuestion ?: return@CharacterQuestionsCard
                            questionnaireSelectedOptions = questionnaireSelectedOptions + (question.id to option.label)
                            questionnaireCustomAnswers = questionnaireCustomAnswers + (question.id to "")
                            questionnaireTextState.setTextAndPlaceCursorAtEnd("")
                            haptics.perform(HapticPattern.Pop)
                            if (!isFinalQuestion) {
                                questionnaireIndex = (questionnaireIndex + 1)
                                    .coerceAtMost(activeQuestionnaire.questions.lastIndex)
                            }
                        }
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isToolApprovalActive && pendingToolApproval != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                pendingToolApproval?.let { pending ->
                    ToolApprovalCard(
                        approval = pending,
                        onApprove = { approvePendingTool(alwaysApproveWorkspace = false) },
                        onAlwaysApproveWorkspace = { approvePendingTool(alwaysApproveWorkspace = true) },
                        onDenyWithInstruction = {
                            state.focusRequester.requestFocus()
                            keyboardController?.show()
                            haptics.perform(HapticPattern.Pop)
                        }
                    )
                }
            }
            
            // Content receiver for clipboard image paste (must be outside Surface lambda)
            val receiveContentListener = remember(isQuestionnaireActive) {
                ReceiveContentListener { transferableContent ->
                    when {
                        isQuestionnaireActive -> transferableContent
                        transferableContent.hasMediaType(MediaType.Image) -> {
                            transferableContent.consume { item ->
                                item.uri?.let { uri ->
                                    scope.launch {
                                        val importedUris = withContext(Dispatchers.IO) {
                                            ChatAttachmentManager.importChatFiles(listOf(uri))
                                        }
                                        if (importedUris.isNotEmpty()) {
                                            state.addImages(importedUris)
                                        }
                                    }
                                }
                                item.uri != null
                            }
                        }
                        else -> transferableContent
                    }
                }
            }
            
            // Minimal input bar - plus button + text field with embedded action button
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Plus button - 48dp pill button
                if (!isQuestionnaireActive && !isToolApprovalActive) {
                    Surface(
                        shape = CircleShape,
                        color = blurredContainerColor(MaterialTheme.colorScheme.surfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(48.dp)
                            .lastChatBlurEffect(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center, 
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onLongClick = {
                                        if (!sttRecording && !sttFinalizing && hasSelectedSttProvider) {
                                            haptics.perform(HapticPattern.Pop)
                                            startSttRecording()
                                        }
                                    },
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        if (sttRecording) {
                                            stopSttRecording(accept = true)
                                        } else {
                                            showPicker = true
                                            keyboardController?.hide()
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = if (sttRecording) Icons.Rounded.Stop else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Text field capsule with embedded action button
                // Corner radius = 24dp (user confirmed this was correct)
                val inputShape = RoundedCornerShape(24.dp)
                Surface(
                    shape = inputShape,  // Fixed radius - correct per user
                    color = blurredContainerColor(MaterialTheme.colorScheme.surfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)  // Matches plus button, allows 4dp padding all around
                        .lastChatBlurEffect(MaterialTheme.colorScheme.surfaceContainer, inputShape)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isQuestionnaireActive && !isToolApprovalActive && state.messageContent.isNotEmpty(),
                            enter = fadeIn(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                            ) + expandVertically(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                expandFrom = Alignment.Top
                            ),
                            exit = fadeOut(
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 360f)
                            ) + shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 360f),
                                shrinkTowards = Alignment.Top
                            )
                        ) {
                            MediaFileInputRow(
                                state = state,
                                onDelete = { uri ->
                                    haptics.perform(HapticPattern.Pop)
                                    onDeleteFile(uri)
                                },
                                onCropImage = { instanceId, image ->
                                    haptics.perform(HapticPattern.Pop)
                                    imageToCrop = PendingImageCrop(instanceId, image)
                                }
                            )
                        }

                        // Editing indicator - shown when editing a message
                        if (!isQuestionnaireActive && !isToolApprovalActive && state.isEditing()) {
                            Surface(
                                color = if (LocalDarkMode.current) 
                                    MaterialTheme.colorScheme.surfaceContainerLowest  // Darker in dark mode
                                else 
                                    MaterialTheme.colorScheme.surfaceContainerHighest,  // Darker in light mode
                                shape = RoundedCornerShape(16.dp),  // Optical roundness: 24dp outer - 8dp padding = 16dp
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 4.dp)  // Aligned with text
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.editing),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            state.editingMessage = null
                                            state.clearInput()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cancel),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Text input with content receiver for paste + overlaid action button
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = (sttRecording || sttFinalizing) && hasSelectedSttProvider,
                                enter = fadeIn(spring(dampingRatio = 0.6f, stiffness = 300f)),
                                exit = fadeOut(tween(140)),
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(10f)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxSize().clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (sttRecording || sttFinalizing) {
                                            haptics.perform(HapticPattern.Pop)
                                            stopSttRecording(accept = true)
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(
                                            top = 12.dp,
                                            bottom = 12.dp,
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = true,
                                            enter = slideInHorizontally(
                                                initialOffsetX = { it / 2 },
                                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                                            ),
                                            exit = slideOutHorizontally(
                                                targetOffsetX = { it / 2 },
                                                animationSpec = tween(140)
                                            )
                                        ) {
                                            if (sttFinalizing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                STTWaveformLine(
                                                    amplitudes = sttState.amplitudes,
                                                    active = sttRecording,
                                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val activeTextState = when {
                                isQuestionnaireActive -> questionnaireTextState
                                isToolApprovalActive -> toolApprovalTextState
                                else -> state.textContent
                            }
                            val lineCount = androidx.compose.runtime.derivedStateOf {
                                activeTextState.text.toString().lines().size
                            }
                            TextField(
                                state = activeTextState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 1.dp)  // Override internal min height (56dp)
                                    .focusRequester(state.focusRequester)
                                    .then(
                                        if (isQuestionnaireActive || isToolApprovalActive) {
                                            Modifier
                                        } else {
                                            Modifier.contentReceiver(receiveContentListener)
                                        }
                                    )
                                    .onFocusChanged { isFocused = it.isFocused },
                                placeholder = {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !((sttRecording || sttFinalizing) && hasSelectedSttProvider),
                                        enter = fadeIn(tween(220)),
                                        exit = fadeOut(tween(140))
                                    ) {
                                        Text(
                                            text = if (isQuestionnaireActive) {
                                                stringResource(R.string.character_questions_custom_answer_placeholder)
                                            } else if (isToolApprovalActive) {
                                                stringResource(R.string.tool_approval_input_placeholder)
                                            } else {
                                                stringResource(R.string.minimal_chat_input_placeholder, assistant.name)
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 12.dp,
                                    end = androidx.compose.animation.core.animateDpAsState(
                                        targetValue = if ((sttRecording || sttFinalizing) && hasSelectedSttProvider) 150.dp else 42.dp,
                                        animationSpec = tween(220),
                                        label = "input_padding"
                                    ).value,
                                    bottom = 12.dp,
                                ),
                                colors = TextFieldDefaults.colors().copy(
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                )
                            )
                            
                            ExpandButtonOverlay(
                                isVisible = !isExpandedFullScreen && lineCount.value >= 5 && !isQuestionnaireActive && !isToolApprovalActive,
                                onExpand = {
                                    haptics.perform(HapticPattern.Pop)
                                    isExpandedFullScreen = true
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 4.dp, top = 4.dp)
                            )
                            
                            // Action button - bottom-right for multiline, optically centered when collapsed
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                val currentAction = when {
                                    isQuestionnaireActive && isFinalQuestion -> "questionnaire_submit"
                                    isQuestionnaireActive -> "questionnaire_next"
                                    isToolApprovalActive -> "tool_approval_deny"
                                    state.loading -> "loading"
                                    !state.isEmpty() -> "send"
                                    hasSelectedSttProvider && sttRecording -> "stt_recording"
                                    hasSelectedSttProvider && sttFinalizing -> "stt_finalizing"
                                    hasSelectedSttProvider && settings.displaySetting.sttReplaceModelIcon -> "stt"
                                    else -> "picker"
                                }
                                
                                val containerColor by animateColorAsState(
                                    targetValue = when (currentAction) {
                                        "loading" -> MaterialTheme.colorScheme.errorContainer
                                        "questionnaire_submit", "questionnaire_next" -> MaterialTheme.colorScheme.primary
                                        "tool_approval_deny" -> MaterialTheme.colorScheme.errorContainer
                                        "send" -> MaterialTheme.colorScheme.primary
                                        "stt_recording" -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                    animationSpec = tween(250),
                                    label = "ActionContainerColor"
                                )


                                
                                Surface(
                                    onClick = { 
                                        if (
                                            currentAction == "send" ||
                                            currentAction == "loading" ||
                                            currentAction == "tool_approval_deny" ||
                                            currentAction.startsWith("questionnaire_")
                                        ) {
                                            sendMessage()
                                        } else if (currentAction == "stt") {
                                            haptics.perform(HapticPattern.Pop)
                                            startSttRecording()
                                        } else if (currentAction == "stt_recording") {
                                            haptics.perform(HapticPattern.Pop)
                                            stopSttRecording(accept = true)
                                        } else {
                                            showPicker = true
                                        }
                                    },
                                    shape = CircleShape,
                                    color = containerColor,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        AnimatedContent(
                                            targetState = currentAction,
                                            transitionSpec = {
                                                val outFadeSpec = tween<Float>(150)
                                                val inFadeSpec = tween<Float>(150, delayMillis = 100)
                                                val depthScale = 0.6f
                                                
                                                if (targetState == "questionnaire_next") {
                                                    (slideInHorizontally(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                    (slideOutHorizontally(tween(250)) { it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                } else if (initialState == "questionnaire_next") {
                                                    (slideInHorizontally(tween(250)) { it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                    (slideOutHorizontally(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                } else {
                                                    fun getRank(state: String): Int = when (state) {
                                                        "picker", "stt", "stt_recording", "stt_finalizing" -> 0
                                                        "send", "questionnaire_submit" -> 1
                                                        "loading", "tool_approval_deny" -> 2
                                                        else -> 1
                                                    }
                                                    val initialRank = getRank(initialState)
                                                    val targetRank = getRank(targetState)
                                                    
                                                    if (targetRank > initialRank) {
                                                        (slideInVertically(tween(250)) { it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                        (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    } else if (targetRank < initialRank) {
                                                        (slideInVertically(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                        (slideOutVertically(tween(250)) { it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    } else {
                                                        (fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith 
                                                        (fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    }
                                                }
                                            },
                                            contentAlignment = Alignment.Center,
                                            label = "ActionContent"
                                        ) { action ->
                                            when (action) {
                                                "loading" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Stop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                "send" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "questionnaire_next" -> {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "questionnaire_submit" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "tool_approval_deny" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                "stt", "stt_recording" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Mic,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = if (action == "stt_recording") {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                                "stt_finalizing" -> {
                                                    // Handled in the waveform box
                                                }
                                                "picker" -> {
                                                    ModelSelector(
                                                        modelId = assistant.chatModelId ?: settings.chatModelId,
                                                        providers = settings.providers,
                                                        onSelect = { onUpdateChatModel(it) },
                                                        type = me.rerere.ai.provider.ModelType.CHAT,
                                                        onlyIcon = true,
                                                        modifier = Modifier.size(34.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }  // Box for TextField + Action button ends
                    }  // Column ends
                }  // Surface ends
            }  // Row ends

            bottomAccessory?.invoke()
        }  // Column ends
    }  // Box ends
    
    if (isExpandedFullScreen) {
        Dialog(
            onDismissRequest = { isExpandedFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val transitionState = remember { MutableTransitionState(false) }
            LaunchedEffect(Unit) {
                transitionState.targetState = true
            }
            
            // Share the same activeTextState so the text is preserved
            val activeTextState = when {
                isQuestionnaireActive -> questionnaireTextState
                isToolApprovalActive -> toolApprovalTextState
                else -> state.textContent
            }
            
            // Request focus when dialog opens
            val expandedFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(transitionState.currentState) {
                if (transitionState.currentState) {
                    expandedFocusRequester.requestFocus()
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeIn(tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                ) + fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (amoledMode) Color.Black else MaterialTheme.colorScheme.background
                        )
                        .statusBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top bar with minimize button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { 
                                    haptics.perform(HapticPattern.Pop)
                                    isExpandedFullScreen = false 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FullscreenExit,
                                    contentDescription = "Minimize",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        
                        // Text input field taking up remaining space
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            TextField(
                                state = activeTextState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(expandedFocusRequester),
                                placeholder = {
                                    Text(
                                        text = if (isQuestionnaireActive) {
                                            stringResource(R.string.character_questions_custom_answer_placeholder)
                                        } else if (isToolApprovalActive) {
                                            stringResource(R.string.tool_approval_input_placeholder)
                                        } else {
                                            stringResource(R.string.minimal_chat_input_placeholder, assistant.name)
                                        }
                                    )
                                },
                                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = 80.dp, // Leave space for send button
                                ),
                                colors = TextFieldDefaults.colors().copy(
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                )
                            )
                            
                            // Floating send button at the bottom right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                val currentAction = when {
                                    isQuestionnaireActive && isFinalQuestion -> "questionnaire_submit"
                                    isQuestionnaireActive -> "questionnaire_next"
                                    isToolApprovalActive -> "tool_approval_deny"
                                    state.loading -> "loading"
                                    !state.isEmpty() -> "send"
                                    else -> "picker" // Fallback but usually hidden
                                }
                                
                                val containerColor by animateColorAsState(
                                    targetValue = when (currentAction) {
                                        "loading" -> MaterialTheme.colorScheme.errorContainer
                                        "questionnaire_submit", "questionnaire_next" -> MaterialTheme.colorScheme.primary
                                        "tool_approval_deny" -> MaterialTheme.colorScheme.errorContainer
                                        "send" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    animationSpec = tween(250),
                                    label = "ActionContainerColorExpanded"
                                )
                                
                                Surface(
                                    onClick = { 
                                        if (
                                            currentAction == "send" ||
                                            currentAction == "loading" ||
                                            currentAction == "tool_approval_deny" ||
                                            currentAction.startsWith("questionnaire_")
                                        ) {
                                            sendMessage()
                                            isExpandedFullScreen = false
                                        }
                                    },
                                    shape = CircleShape,
                                    color = containerColor,
                                    modifier = Modifier.size(56.dp) // Larger button
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        AnimatedContent(
                                            targetState = currentAction,
                                            transitionSpec = {
                                                val outFadeSpec = tween<Float>(150)
                                                val inFadeSpec = tween<Float>(150, delayMillis = 100)
                                                val depthScale = 0.6f
                                                
                                                if (targetState == "questionnaire_next") {
                                                    (slideInHorizontally(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                    (slideOutHorizontally(tween(250)) { it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                } else if (initialState == "questionnaire_next") {
                                                    (slideInHorizontally(tween(250)) { it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                    (slideOutHorizontally(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                } else {
                                                    fun getRank(state: String): Int = when (state) {
                                                        "picker", "stt", "stt_recording", "stt_finalizing" -> 0
                                                        "send", "questionnaire_submit" -> 1
                                                        "loading", "tool_approval_deny" -> 2
                                                        else -> 1
                                                    }
                                                    val initialRank = getRank(initialState)
                                                    val targetRank = getRank(targetState)
                                                    
                                                    if (targetRank > initialRank) {
                                                        (slideInVertically(tween(250)) { it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                        (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    } else if (targetRank < initialRank) {
                                                        (slideInVertically(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                                                        (slideOutVertically(tween(250)) { it / 2 } + fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    } else {
                                                        (fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith 
                                                        (fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                                                    }
                                                }
                                            },
                                            contentAlignment = Alignment.Center,
                                            label = "ActionContentExpanded"
                                        ) { action ->
                                            when (action) {
                                                "loading" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Stop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                "send" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "questionnaire_next" -> {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "questionnaire_submit" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                                "tool_approval_deny" -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                else -> {
                                                    // Fallback
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    }

    // Bottom sheet picker with custom MinimalPickerContent
    // Optical roundness: sheet corners (40dp) = button corners (24dp) + padding (16dp)
    if (showPicker) {
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = pickerSheetShape,
            dragHandle = null
        ) {
            MinimalPickerContent(
                state = state,
                conversation = conversation,
                settings = settings,
                assistant = assistant,
                currentChatModel = currentChatModel,
                cameraPermission = cameraPermission,
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onUpdateChatModel = onUpdateChatModel,
                onUpdateConversation = onUpdateConversation,
                onUpdateAssistant = onUpdateAssistant,
                onUpdateSearchService = onUpdateSearchService,
                onNavigateToLorebook = onNavigateToLorebook,
                onRefreshContext = onRefreshContext,
                importScope = scope,
                onDismiss = { showPicker = false }
            )
        }
    }
}

@Composable
private fun CharacterQuestionsCard(
    questionnaire: AskUserQuestionnaire,
    currentIndex: Int,
    selectedOptionLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onSelectOption: (AskUserOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = questionnaire.questions.getOrNull(currentIndex) ?: return
    val canGoPrevious = currentIndex > 0
    val canGoNext = currentIndex < questionnaire.questions.lastIndex

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.previous),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.character_questions_progress,
                            currentIndex + 1,
                            questionnaire.questions.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.next),
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.banner_dismiss),
                    )
                }
            }

            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium
            )

            question.options.forEach { option ->
                CharacterQuestionOptionRow(
                    option = option,
                    selected = selectedOptionLabel == option.label,
                    onClick = { onSelectOption(option) }
                )
            }
        }
    }
}

@Composable
private fun CharacterQuestionOptionRow(
    option: AskUserOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "question_option_scale"
    )

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.background
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            option.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class PendingToolApproval(
    val toolCallId: String,
    val toolName: String,
    val arguments: String,
) {
    val isWorkspaceTool: Boolean
        get() = toolName.startsWith("workspace_")
}

private fun List<me.rerere.ai.ui.UIMessage>.findPendingToolApproval(): PendingToolApproval? {
    return firstNotNullOfOrNull { message ->
        message.getToolCalls().firstOrNull { toolCall ->
            toolCall.toolName != ASK_USER_TOOL_NAME &&
                toolCall.approvalState is ToolApprovalState.Pending
        }?.let { toolCall ->
            PendingToolApproval(
                toolCallId = toolCall.toolCallId,
                toolName = toolCall.toolName,
                arguments = toolCall.arguments,
            )
        }
    }
}

@Composable
private fun ToolApprovalCard(
    approval: PendingToolApproval,
    onApprove: () -> Unit,
    onAlwaysApproveWorkspace: () -> Unit,
    onDenyWithInstruction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tool_approval_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = approval.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val summary = approval.summary()
            if (summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ToolApprovalActionRow(
                label = stringResource(R.string.tool_approval_approve),
                description = stringResource(R.string.tool_approval_approve_desc),
                icon = Icons.Rounded.Check,
                selected = true,
                onClick = onApprove,
            )
            if (approval.isWorkspaceTool) {
                ToolApprovalActionRow(
                    label = stringResource(R.string.tool_approval_always_workspace),
                    description = stringResource(R.string.tool_approval_always_workspace_desc),
                    icon = Icons.Rounded.Save,
                    selected = false,
                    onClick = onAlwaysApproveWorkspace,
                )
            }
            ToolApprovalActionRow(
                label = stringResource(R.string.tool_approval_deny_with_instruction),
                description = stringResource(R.string.tool_approval_deny_with_instruction_desc),
                icon = Icons.Rounded.Edit,
                selected = false,
                onClick = onDenyWithInstruction,
            )
        }
    }
}

@Composable
private fun ToolApprovalActionRow(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tool_approval_action_scale"
    )

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.background
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PendingToolApproval.displayName(): String = when (toolName) {
    "workspace_shell" -> stringResource(R.string.activity_timeline_tool_workspace_shell)
    "workspace_read_file" -> stringResource(R.string.activity_timeline_tool_workspace_read_file)
    "workspace_write_file" -> stringResource(R.string.activity_timeline_tool_workspace_write_file)
    "workspace_edit_file" -> stringResource(R.string.activity_timeline_tool_workspace_edit_file)
    else -> toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun PendingToolApproval.summary(): String {
    val args = runCatching { JsonInstantPretty.parseToJsonElement(arguments).jsonObject }.getOrNull()
    val key = when (toolName) {
        "workspace_shell" -> "command"
        "workspace_read_file", "workspace_write_file", "workspace_edit_file" -> "path"
        else -> null
    }
    return key?.let { args?.get(it)?.jsonPrimitiveOrNull?.contentOrNull }
        ?: arguments.take(240)
}

@Composable
private fun MinimalPickerContent(
    state: ChatInputState,
    conversation: Conversation,
    settings: Settings,
    assistant: Assistant,
    currentChatModel: Model?,
    cameraPermission: me.rerere.rikkahub.ui.components.ui.permission.PermissionState,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateChatModel: (Model) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onNavigateToLorebook: (String) -> Unit,
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult,
    importScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val availableSkills = remember(settings.skills) {
        settings.skills
    }
    val availableSkillIds = remember(availableSkills) { availableSkills.map { it.id }.toSet() }
    val assistantAvailableSkillIds = remember(settings.skills, assistant.id) {
        settings.skills.filter { it.isAvailableForAssistant(assistant.id) }.map { it.id }.toSet()
    }
    
    // OLED dark mode detection for buttons (not sheet backgrounds)
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = me.rerere.rikkahub.ui.theme.LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    // Sheet background uses surfaceContainerLow always
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    
    // Camera state
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    
    // Sub-picker states
    var showModelPicker by remember { mutableStateOf(false) }
    var showReasoningPicker by remember { mutableStateOf(false) }
    var showSkillsPicker by remember { mutableStateOf(false) }
    var showLorebooksPicker by remember { mutableStateOf(false) }
    var showContextRefreshDialog by remember { mutableStateOf(false) }
    var showContextSummaryEditDialog by remember { mutableStateOf(false) }
    var editableContextSummary by remember(conversation.contextSummary) {
        mutableStateOf(conversation.contextSummary.orEmpty())
    }
    var showSearchPicker by remember { mutableStateOf(false) }
    val modelCatalogService: ModelCatalogService = koinInject()
    val catalogSnapshot by modelCatalogService.snapshotFlow.collectAsStateWithLifecycle()
    val assistantDefaultSkillIds = assistant.enabledSkillIds.intersect(assistantAvailableSkillIds)
    val alwaysEnabledSkillIds = availableSkills
        .filter { it.alwaysEnabled && assistantAvailableSkillIds.contains(it.id) }
        .map { it.id }
        .toSet()
    val effectiveActiveSkillIds = if (conversation.enabledModeIds.hasManualSkillSelectionOverride() || conversation.enabledModeIds.isNotEmpty()) {
        conversation.enabledModeIds.withoutSkillSelectionOverride()
    } else {
        assistantDefaultSkillIds + alwaysEnabledSkillIds
    }.intersect(availableSkillIds)
    
    // Track the last valid search provider index so selection persists when search is disabled
    // Initialize from assistant's searchMode if available, otherwise use global setting
    val initialProviderIndex = when (val mode = assistant.searchMode) {
        is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> mode.index
        else -> settings.searchServiceSelected.coerceAtLeast(0)
    }
    var lastValidProviderIndex by rememberSaveable(initialProviderIndex) { mutableStateOf(initialProviderIndex) }
    
    // Update lastValidProviderIndex when a valid external index is set
    val currentProviderIndex = when (val mode = assistant.searchMode) {
        is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> mode.index
        else -> -1
    }
    // Sync immediately when currentProviderIndex changes (no LaunchedEffect delay to prevent flickering)
    if (currentProviderIndex >= 0 && currentProviderIndex < settings.searchServices.size && currentProviderIndex != lastValidProviderIndex) {
        lastValidProviderIndex = currentProviderIndex
    }
    
    // Calculate effective provider index (use tracked value when current is invalid)
    val effectiveProviderIndex = if (currentProviderIndex >= 0 && currentProviderIndex < settings.searchServices.size) {
        currentProviderIndex
    } else {
        lastValidProviderIndex.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))
    }
    
    // Button shapes for grouped appearance (24dp outer corners, 10dp inner, matches floating toolbar)
    val leftButtonShape = RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 10.dp)
    val middleButtonShape = RoundedCornerShape(10.dp)
    val rightButtonShape = RoundedCornerShape(topStart = 10.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 24.dp)

    fun importImages(
        uris: List<Uri>,
        onFinally: () -> Unit = {}
    ) {
        if (uris.isEmpty()) {
            onFinally()
            return
        }

        importScope.launch {
            val importedUris = withContext(Dispatchers.IO) {
                ChatAttachmentManager.importChatFiles(uris)
            }
            if (importedUris.isEmpty()) {
                Log.w("MinimalChatInput", "Failed to import ${uris.size} selected image(s)")
                toaster.show(context.getString(R.string.chat_input_selected_image_failed))
            } else {
                state.addImages(importedUris)
            }
            onFinally()
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        val capturedUri = cameraOutputUri
        val capturedFile = cameraOutputFile
        if (captureSuccessful && capturedUri != null) {
            onDismiss()
            importImages(
                uris = listOf(capturedUri),
                onFinally = {
                    capturedFile?.delete()
                    cameraOutputFile = null
                    cameraOutputUri = null
                }
            )
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    
    // Photo picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onDismiss()
            importImages(uris = selectedUris)
        }
    }
    
    // File picker launcher - categorizes files by type
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onDismiss()
            val isWorkspaceEnabled = assistant.workspaceId != null
            importScope.launch {
                val importedFiles = withContext(Dispatchers.IO) {
                    context.prepareImportedPickerFiles(
                        selectedUris = selectedUris,
                        isWorkspaceEnabled = isWorkspaceEnabled,
                    )
                }

                importedFiles.unsupportedFileNames.forEach { fileName ->
                    toaster.show(
                        context.getString(
                            R.string.chat_input_unsupported_file_type,
                            fileName
                        )
                    )
                }
                importedFiles.failedFileNames.forEach { fileName ->
                    toaster.show(context.getString(R.string.chat_input_add_file_failed, fileName))
                }

                if (importedFiles.imageUris.isNotEmpty()) {
                    state.addImages(importedFiles.imageUris)
                }
                if (importedFiles.documents.isNotEmpty()) {
                    state.addFiles(importedFiles.documents)
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // File upload buttons - grouped with corner shapes (no outer container)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Camera button - icon only, no label
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PermissionManager(permissionState = cameraPermission) {
                    MinimalFileButtonGroupedIconOnly(
                        icon = Icons.Rounded.CameraAlt,
                        shape = leftButtonShape,
                        modifier = Modifier.fillMaxSize(),
                        onClick = {
                            if (cameraPermission.allRequiredPermissionsGranted) {
                                cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
                                cameraOutputUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cameraOutputFile!!
                                )
                                cameraLauncher.launch(cameraOutputUri!!)
                            } else {
                                cameraPermission.requestPermissions()
                            }
                        }
                    )
                }
            }
            
            // Photos button - icon only, no label
            MinimalFileButtonGroupedIconOnly(
                icon = Icons.Rounded.Image,
                shape = middleButtonShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )
            
            // Files button - icon only, no label
            MinimalFileButtonGroupedIconOnly(
                icon = Icons.Rounded.FolderOpen,
                shape = rightButtonShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    filePickerLauncher.launch("*/*")
                }
            )
        }
        
        // Separator
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        
        // Model picker - uses actual model icon, full-width clickable
        val currentModel = currentChatModel
        val provider = currentModel?.findProvider(providers = settings.providers)
        MinimalPickerItem(
            icon = {
                // Show model icon (not ModelSelector which handles its own clicks)
                if (currentModel != null) {
                    me.rerere.rikkahub.ui.components.ui.ModelIcon(
                        model = currentModel,
                        provider = provider,
                        modifier = Modifier.size(28.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ViewModule,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = currentModel?.displayName ?: "Select Model",
            subtitle = currentModel?.modelId ?: "Choose a model to use",
            onClick = { 
                // Open model selector sheet
                showModelPicker = true
            }
        )
        
        // Reasoning picker - only show if model has reasoning ability (same as floating toolbar)
        if (currentModel?.abilities?.contains(me.rerere.ai.provider.ModelAbility.REASONING) == true) {
            MinimalPickerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.minimal_input_thinking),
                subtitle = stringResource(R.string.minimal_input_thinking_desc),
                onClick = { 
                    showReasoningPicker = true
                }
            )
        }
        
        // Search picker - show selected provider if enabled (use effectiveProviderIndex to track current selection)
        val searchService = settings.searchServices.getOrNull(effectiveProviderIndex)
        val searchProviderName = if (searchService != null) {
            SearchServiceOptions.TYPES[searchService::class]
        } else null
        
        // Show provider icon when search is enabled and a provider is configured
        MinimalPickerItem(
            icon = {
                // Only show provider icon when search is actually enabled
                if (enableSearch && searchProviderName != null) {
                    AutoAIIconWithUrl(
                        name = searchProviderName,
                        customIconUri = catalogSnapshot?.searchProviderIconUri(searchProviderName),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (enableSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            title = if (enableSearch && searchProviderName != null) searchProviderName else stringResource(R.string.minimal_input_search),
            subtitle = if (enableSearch) stringResource(R.string.web_search_enabled) else stringResource(R.string.minimal_input_search_desc),
            onClick = { 
                showSearchPicker = true
            }
        )
        
        if (settings.skills.isNotEmpty()) {
            // Skills - use enabledModeIds (legacy field) for per-chat overrides.
            val activeSkills = availableSkills.filter { skill ->
                effectiveActiveSkillIds.contains(skill.id)
            }
            val activeSkillsCount = activeSkills.size
            val skillsActive = activeSkillsCount > 0
            val singleActiveSkillIcon = activeSkills.singleOrNull()?.icon
            MinimalPickerItem(
                icon = {
                    Icon(
                        imageVector = if (singleActiveSkillIcon != null) {
                            ModeIcons.getIcon(singleActiveSkillIcon)
                        } else {
                            Icons.Rounded.Category
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (skillsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                title = stringResource(R.string.minimal_input_skills),
                subtitle = if (activeSkillsCount > 0) {
                    stringResource(R.string.skills_picker_active_count, activeSkillsCount)
                } else {
                    stringResource(R.string.minimal_input_skills_desc)
                },
                onClick = {
                    showSkillsPicker = true
                }
            )
        }
        
        if (settings.lorebooks.isNotEmpty()) {
            // Lorebooks - show active count and blue icon when enabled
            val lorebookIds = settings.lorebooks.map { it.id }.toSet()
            val activeLorebookIds = (conversation.enabledLorebookIds ?: assistant.enabledLorebookIds).intersect(lorebookIds)
            val activeLorebooksCount = activeLorebookIds.size
            val lorebooksActive = activeLorebooksCount > 0
            MinimalPickerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Book,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (lorebooksActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                title = stringResource(R.string.minimal_input_lorebooks),
                subtitle = if (activeLorebooksCount > 0) "$activeLorebooksCount active" else stringResource(R.string.minimal_input_lorebooks_desc),
                onClick = {
                    showLorebooksPicker = true
                }
            )
        }
        
        // Summarize button - show whenever there is enough history to summarize
        if (assistant.canManuallySummarizeConversation(conversation.currentMessages.size)) {
            MinimalPickerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Summarize,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = stringResource(R.string.minimal_input_summarize),
                subtitle = stringResource(R.string.minimal_input_summarize_desc),
                onClick = {
                    showContextRefreshDialog = true
                }
            )
        }
    }
    
    // Reasoning picker sheet
    if (showReasoningPicker) {
        ReasoningPicker(
            reasoningTokens = assistant.thinkingBudget ?: 0,
            onDismissRequest = { showReasoningPicker = false },
            onUpdateReasoningTokens = { tokens ->
                onUpdateAssistant(assistant.copy(thinkingBudget = tokens))
                showReasoningPicker = false
            }
        )
    }
    
    // Model picker sheet - direct ModalBottomSheet (not ModelSelector which shows its own button)
    if (showModelPicker) {
        val modelPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredProviders = settings.providers.filter { 
            it.enabled && it.models.any { model -> model.type == me.rerere.ai.provider.ModelType.CHAT }
        }
        
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showModelPicker = false },
            sheetState = modelPickerSheetState,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            modelPickerSheetState.hide()
                            showModelPicker = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModelList(
                    currentModel = assistant.chatModelId ?: settings.chatModelId,
                    providers = filteredProviders,
                    modelType = me.rerere.ai.provider.ModelType.CHAT,
                    onSelect = { selectedModel: Model ->
                        onUpdateChatModel(selectedModel)
                        scope.launch {
                            modelPickerSheetState.hide()
                            showModelPicker = false
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            modelPickerSheetState.hide()
                            showModelPicker = false
                        }
                    }
                )
            }
        }
    }
    
    // Skills picker sheet
    if (showSkillsPicker) {
        SkillsPickerSheet(
            settings = settings,
            assistant = assistant,
            conversation = conversation,
            onUpdateConversation = onUpdateConversation,
            onDismiss = { showSkillsPicker = false }
        )
    }
    
    // Lorebooks picker sheet
    if (showLorebooksPicker) {
        LorebooksPickerSheet(
            settings = settings,
            assistant = assistant,
            conversation = conversation,
            onUpdateConversation = onUpdateConversation,
            onNavigateToLorebook = { lorebookId ->
                showLorebooksPicker = false
                onNavigateToLorebook(lorebookId)
            },
            onDismiss = { showLorebooksPicker = false }
        )
    }
    
    // Context Refresh dialog (same as floating toolbar)
    if (showContextRefreshDialog) {
        ContextRefreshDialog(
            conversation = conversation,
            onRefresh = onRefreshContext,
            onEditSummary = {
                editableContextSummary = conversation.contextSummary.orEmpty()
                showContextSummaryEditDialog = true
            },
            onRevertSummary = {
                onUpdateConversation(
                    conversation.copy(
                        contextSummary = null,
                        contextSummaryUpToIndex = -1,
                        lastRefreshTime = 0L,
                        updateAt = Instant.now()
                    )
                )
            },
            onDismiss = { showContextRefreshDialog = false }
        )
    }

    if (showContextSummaryEditDialog) {
        AlertDialog(
            onDismissRequest = { showContextSummaryEditDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            title = {
                Text(
                    text = stringResource(R.string.context_refresh_edit_summary),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                TextField(
                    value = editableContextSummary,
                    onValueChange = { editableContextSummary = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    placeholder = {
                        Text(stringResource(R.string.context_refresh_edit_summary_placeholder))
                    }
                )
            },
            confirmButton = {
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        val trimmedSummary = editableContextSummary.trim()
                        onUpdateConversation(
                            conversation.copy(
                                contextSummary = trimmedSummary.takeIf { it.isNotBlank() },
                                contextSummaryUpToIndex = if (trimmedSummary.isBlank()) {
                                    -1
                                } else {
                                    conversation.contextSummaryUpToIndex
                                },
                                lastRefreshTime = if (trimmedSummary.isBlank()) 0L else System.currentTimeMillis(),
                                updateAt = Instant.now()
                            )
                        )
                        showContextSummaryEditDialog = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = stringResource(R.string.context_refresh_save_summary)
                    )
                }
            },
            dismissButton = {
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showContextSummaryEditDialog = false
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(android.R.string.cancel)
                    )
                }
            }
        )
    }
    
    // Search picker sheet (same as floating toolbar) - direct content, no intermediate button
    if (showSearchPicker) {
        val chatModel = currentChatModel
        
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showSearchPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
                
                // Direct SearchPicker content
                SearchPicker(
                    enableSearch = enableSearch,
                    settings = settings,
                    model = chatModel,
                    onToggleSearch = { enabled ->
                        if (enabled) {
                            // When turning on, restore the last known valid provider index
                            onUpdateSearchService(effectiveProviderIndex)
                        }
                        onToggleSearch(enabled)
                    },
                    onUpdateSearchService = { index ->
                        // Track this selection
                        lastValidProviderIndex = index
                        onUpdateSearchService(index)
                    },
                    selectedProviderIndex = effectiveProviderIndex,  // Use effective index so selection persists when off
                    preferBuiltInSearch = assistant.preferBuiltInSearch,
                    onTogglePreferBuiltInSearch = { enabled ->
                        onUpdateAssistant(assistant.copy(preferBuiltInSearch = enabled))
                    },
                    onDismiss = { showSearchPicker = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun STTWaveformLine(
    amplitudes: List<Float>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val count = 21
    val center = count / 2
    val historyNeeded = center + 1

    val bars = remember(amplitudes, active) {
        val source = amplitudes.takeLast(historyNeeded).reversed()
        List(count) { index ->
            val distFromCenter = kotlin.math.abs(index - center)
            if (source.isEmpty()) {
                if (active) 0.15f + ((distFromCenter % 3) * 0.05f) else 0.08f
            } else {
                val rawValue = source.getOrNull(distFromCenter) ?: 0.08f
                rawValue
            }
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEachIndexed { index, value ->
            val animatedHeight by animateFloatAsState(
                targetValue = value.coerceIn(0.08f, 1f),
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                label = "stt_waveform_$index",
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = (4.dp + 20.dp * animatedHeight))
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.85f else 0.45f)),
            )
        }
    }
}

@Composable
private fun MinimalFileButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(80.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Compact file button for use inside grouped container (24dp inner radius for optical roundness)
@Composable
private fun MinimalFileButtonCompact(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),  // Optically round with 40dp outer container
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Grouped file button with custom shape for grouped appearance (same as floating toolbar)
@Composable
private fun MinimalFileButtonGrouped(
    icon: ImageVector,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // OLED-aware button color (same as floating toolbar's BigIconTextButton)
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = me.rerere.rikkahub.ui.theme.LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    val buttonColor = if (isAmoled) androidx.compose.ui.graphics.Color.Black 
                      else MaterialTheme.colorScheme.surfaceContainerHigh
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = buttonColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Grouped file button icon-only variant (no text label) for compact picker display
@Composable
private fun MinimalFileButtonGroupedIconOnly(
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // OLED-aware button color (same as floating toolbar's BigIconTextButton)
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = me.rerere.rikkahub.ui.theme.LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    val buttonColor = if (isAmoled) androidx.compose.ui.graphics.Color.Black 
                      else MaterialTheme.colorScheme.surfaceContainerHigh
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = buttonColor,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MinimalPickerItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Custom Row layout with less padding than ListItem (12dp vertical, 8dp horizontal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    }
}

private data class PendingImageCrop(
    val instanceId: String,
    val image: UIMessagePart.Image,
)

private data class IndexedAttachment<T : UIMessagePart>(
    val id: String,
    val part: T,
)

private fun UIMessagePart.attachmentUrl(): String? = when (this) {
    is UIMessagePart.Image -> url
    is UIMessagePart.Video -> url
    is UIMessagePart.Audio -> url
    is UIMessagePart.Document -> url
    else -> null
}

@Composable
private fun MediaFileInputRow(
    state: ChatInputState,
    onDelete: (Uri) -> Unit,
    onCropImage: (String, UIMessagePart.Image) -> Unit,
) {
    val images = remember(state.pendingAttachments) {
        state.pendingAttachments.mapNotNull { attachment ->
            (attachment.part as? UIMessagePart.Image)?.let { image ->
                IndexedAttachment(attachment.id, image)
            }
        }
    }
    val videos = remember(state.pendingAttachments) {
        state.pendingAttachments.mapNotNull { attachment ->
            (attachment.part as? UIMessagePart.Video)?.let { video ->
                IndexedAttachment(attachment.id, video)
            }
        }
    }
    val audios = remember(state.pendingAttachments) {
        state.pendingAttachments.mapNotNull { attachment ->
            (attachment.part as? UIMessagePart.Audio)?.let { audio ->
                IndexedAttachment(attachment.id, audio)
            }
        }
    }
    val documents = remember(state.pendingAttachments) {
        state.pendingAttachments.mapNotNull { attachment ->
            (attachment.part as? UIMessagePart.Document)?.let { document ->
                IndexedAttachment(attachment.id, document)
            }
        }
    }

    fun removePart(instanceId: String): Uri? {
        val removedPart = state.removeAttachment(instanceId) ?: return null
        val removedUrl = removedPart.attachmentUrl() ?: return null
        return if (state.messageContent.none { it.attachmentUrl() == removedUrl }) {
            removedUrl.toUri()
        } else {
            null
        }
    }

    val listState = rememberLazyListState()
    val canScrollLeft by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val canScrollRight by remember {
        androidx.compose.runtime.derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 ||
                lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
        }
    }
    val leftFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollLeft) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "attachment_left_fade"
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollRight) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "attachment_right_fade"
    )

    LazyRow(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if ((leftFadeAlpha > 0f || rightFadeAlpha > 0f) && size.width > 0f) {
                    val fadeWidthPx = 22.dp.toPx()
                    val leftEnd = (fadeWidthPx / size.width).coerceAtMost(0.35f)
                    val rightStart = (1f - fadeWidthPx / size.width).coerceAtLeast(0.65f)
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 1f - leftFadeAlpha),
                                leftEnd to Color.Black,
                                rightStart to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - rightFadeAlpha),
                            )
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
            }
    ) {
        items(
            items = images,
            key = { attachment -> "image:${attachment.id}" }
        ) { attachment ->
            val image = attachment.part
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.94f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                label = "image_attachment_scale"
            )

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onCropImage(attachment.id, image) }
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp
                ) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(38.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                removePart(attachment.id)?.let(onDelete)
                            }
                        ),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        items(
            items = videos,
            key = { attachment -> "video:${attachment.id}" }
        ) { attachment ->
            val video = attachment.part
            Box(
                modifier = Modifier
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, null)
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(24.dp)
                        .clickable {
                            removePart(attachment.id)?.let(onDelete)
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        items(
            items = audios,
            key = { attachment -> "audio:${attachment.id}" }
        ) { attachment ->
            val audio = attachment.part
            Box(
                modifier = Modifier
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.AudioFile, null)
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(24.dp)
                        .clickable {
                            removePart(attachment.id)?.let(onDelete)
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        items(
            items = documents,
            key = { attachment -> "document:${attachment.id}" }
        ) { attachment ->
            val document = attachment.part
            me.rerere.rikkahub.ui.components.ui.DocumentChip(
                fileName = document.fileName,
                mimeType = document.mime,
                modifier = Modifier.size(60.dp),
                onRemove = {
                    removePart(attachment.id)?.let(onDelete)
                }
            )
        }
    }
}

@Composable
private fun ChatScrollToBottomButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scroll_to_bottom_scale"
    )

    Surface(
        shape = CircleShape,
        color = blurredContainerColor(MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .lastChatBlurEffect(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onClick()
                },
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.chat_page_scroll_to_bottom),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    suggestions: List<String>,
    onClickSuggestion: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var pressedSuggestionIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSuggestionIndex by remember { mutableStateOf<Int?>(null) }

    val canScrollLeft by remember { androidx.compose.runtime.derivedStateOf { scrollState.value > 0 } }
    val canScrollRight by remember { androidx.compose.runtime.derivedStateOf { scrollState.value < scrollState.maxValue } }
    val leftFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollLeft) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "left_fade"
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollRight) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "right_fade"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (leftFadeAlpha > 0f || rightFadeAlpha > 0f) {
                    val fadeWidthPx = 24.dp.toPx()
                    val leftEnd = (fadeWidthPx / size.width).coerceAtMost(0.4f)
                    val rightStart = (1f - fadeWidthPx / size.width).coerceAtLeast(0.6f)
                    val colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 1f - leftFadeAlpha),
                        leftEnd to Color.Black,
                        rightStart to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - rightFadeAlpha)
                    )
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colorStops = colorStops),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        suggestions.forEachIndexed { index, suggestion ->
            var visible by remember { mutableStateOf(false) }
            val interactionSource = remember { MutableInteractionSource() }
            val isInteractionPressed by interactionSource.collectIsPressedAsState()

            LaunchedEffect(isInteractionPressed) {
                if (isInteractionPressed) {
                    pressedSuggestionIndex = index
                } else if (pressedSuggestionIndex == index) {
                    pressedSuggestionIndex = null
                }
            }

            LaunchedEffect(suggestion) {
                kotlinx.coroutines.delay(index * 50L)
                visible = true
            }

            val isSelected = selectedSuggestionIndex == index
            val isPressed = pressedSuggestionIndex == index
            val isAnythingSelected = selectedSuggestionIndex != null
            val isAnythingPressed = pressedSuggestionIndex != null
            
            val targetScale = when {
                isSelected -> 1.05f
                isPressed -> 0.9f
                else -> 1f
            }
            
            val targetAlpha = when {
                isSelected -> 0f
                isAnythingSelected -> 0f
                isAnythingPressed && !isPressed -> 0.5f 
                visible -> 1f
                else -> 0f
            }

            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "suggestion_scale"
            )

            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "suggestion_alpha"
            )
            
            LaunchedEffect(isSelected) {
                if (isSelected) {
                    kotlinx.coroutines.delay(200)
                    onClickSuggestion(suggestion)
                }
            }

            if (visible || targetAlpha > 0f) {
                val suggestionShape = RoundedCornerShape(16.dp)
                Surface(
                    shape = suggestionShape,
                    color = blurredContainerColor(MaterialTheme.colorScheme.surfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .lastChatBlurEffect(MaterialTheme.colorScheme.surfaceContainer, suggestionShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                                selectedSuggestionIndex = index
                        }
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ExpandButtonOverlay(
    isVisible: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
        modifier = modifier
    ) {
        androidx.compose.material3.IconButton(
            onClick = onExpand,
            modifier = Modifier.size(36.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.Fullscreen,
                contentDescription = "Expand",
                modifier = Modifier.size(20.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
