package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.search.SearchServiceOptions
import coil3.compose.AsyncImage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.deleteChatFiles
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.ViewModule
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.crop.CropImageScreen
import me.rerere.rikkahub.ui.components.ui.icons.ModeIcons
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.AppPickerRow
import me.rerere.rikkahub.ui.components.ui.AppPickerRowStyle
import me.rerere.rikkahub.ui.components.ui.GroupedStack
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.GroupedAxis
import me.rerere.rikkahub.ui.theme.appOutlinedBorderColor
import me.rerere.rikkahub.ui.theme.groupedItemShape
import me.rerere.rikkahub.ui.theme.groupedInsetItemShape
import me.rerere.rikkahub.ui.theme.nestedSurfaceColor
import me.rerere.rikkahub.ui.theme.placedSurfaceColor
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import java.io.File
import kotlin.uuid.Uuid

/**
 * Minimal ChatGPT-style input bar with bottom sheet picker.
 * Shows a simple input bar with + button, text field, and send button.
 * The + button opens a bottom sheet with file upload, model picker, and other options.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onUpdateSearchService: (Int) -> Unit,
    onClearContext: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    askUserMode: AskUserComposerMode? = null,
    onAskUserOptionSelect: (String) -> Unit = {},
    onAskUserDismiss: () -> Unit = {},
    onAskUserBack: () -> Unit = {},
    onAskUserPrimaryAction: () -> Unit = {},
    onNavigateToLorebook: (String) -> Unit = {},
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult = { ChatService.ContextRefreshResult(false, errorMessage = "Not configured") },
    onDeleteFile: (Uri) -> Unit = {},
    bottomAccessory: @Composable (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val keyboardController = LocalSoftwareKeyboardController.current
    val localSettings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val availableSkills = remember(settings.skills) { settings.skills }
    val availableSkillIds = remember(availableSkills) { availableSkills.map { it.id }.toSet() }
    val activeConversationSkillIds = remember(conversation.enabledModeIds, assistant.enabledSkillIds, availableSkillIds) {
        val activeIds = if (conversation.enabledModeIds.isNotEmpty()) {
            conversation.enabledModeIds
        } else {
            assistant.enabledSkillIds
        }
        activeIds.intersect(availableSkillIds)
    }
    val slashInvocableSkills = remember(availableSkills) {
        availableSkills.distinctBy { it.id }
    }
    val inputText = state.textContent.text.toString()
    val isAskUserMode = askUserMode != null
    val slashToken = inputText.substringBefore(" ")
    val isTypingSlashToken = inputText.startsWith("/") &&
        !inputText.drop(1).contains(' ') &&
        !inputText.contains('\n')
    val filteredSlashSkills = remember(slashToken, slashInvocableSkills) {
        if (!slashToken.startsWith("/")) {
            emptyList()
        } else {
            val query = slashToken.lowercase()
            slashInvocableSkills.filter { skill ->
                skill.slashCommand().lowercase().startsWith(query)
            }
        }
    }
    val exactSlashSkill = remember(slashToken, slashInvocableSkills) {
        slashInvocableSkills.firstOrNull { skill ->
            skill.slashCommand().equals(slashToken, ignoreCase = true)
        }
    }

    // Picker sheet styling - optical roundness: outer (40dp) = button corners (24dp) + padding (16dp)
    val pickerSheetShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    
    // Camera permission - must be in parent, not inside ModalBottomSheet
    val cameraPermission = rememberPermissionState(PermissionCamera)
    
    var showPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val outlineColor = appOutlinedBorderColor()
    val activeChatModel = settings.getCurrentChatModel()
    val activeProvider = activeChatModel?.findProvider(providers = settings.providers)

    LaunchedEffect(isAskUserMode) {
        if (isAskUserMode) {
            showPicker = false
        }
    }
    
    // Collapse picker when keyboard opens
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            showPicker = false
        }
    }
    
    fun sendMessage() {
        if (state.loading) {
            keyboardController?.hide()
            haptics.perform(HapticPattern.Send)
            onCancelClick()
            return
        }
        if (isAskUserMode) {
            keyboardController?.hide()
            when (askUserMode?.stage) {
                AskUserComposerStage.Review -> haptics.perform(HapticPattern.Success)
                else -> haptics.perform(HapticPattern.Pop)
            }
            onAskUserPrimaryAction()
            return
        }
        if (!state.loading && exactSlashSkill != null) {
            val updatedIds = activeConversationSkillIds + exactSlashSkill.id
            if (updatedIds != conversation.enabledModeIds) {
                onUpdateConversation(conversation.copy(enabledModeIds = updatedIds))
            }
        }
        keyboardController?.hide()
        haptics.perform(HapticPattern.Send)
        onSendClick()
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = askUserMode != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                askUserMode?.let { mode ->
                    AskUserPicker(
                        mode = mode,
                        onSelectOption = {
                            haptics.perform(HapticPattern.Pop)
                            onAskUserOptionSelect(it)
                        },
                        onDismiss = {
                            haptics.perform(HapticPattern.Pop)
                            onAskUserDismiss()
                        },
                        onBack = {
                            haptics.perform(HapticPattern.Pop)
                            onAskUserBack()
                        },
                    )
                }
            }

            // Media preview row
            if (!isAskUserMode && state.messageContent.isNotEmpty()) {
                MediaFileInputRow(
                    state = state,
                    onDelete = onDeleteFile
                )
            }
            
            // Suggestions row
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAskUserMode && chatSuggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ChatSuggestionsRow(
                    suggestions = chatSuggestions,
                    onClickSuggestion = onClickSuggestion
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isAskUserMode && isTypingSlashToken && filteredSlashSkills.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SlashSkillsPicker(
                    skills = filteredSlashSkills,
                    onSelect = { skill ->
                        val slashCommand = "${skill.slashCommand()} "
                        state.setMessageText(slashCommand)
                        haptics.perform(HapticPattern.Pop)
                    }
                )
            }
            
            // Content receiver for clipboard image paste (must be outside Surface lambda)
            val receiveContentListener = remember(isAskUserMode) {
                ReceiveContentListener { transferableContent ->
                    when {
                        isAskUserMode -> transferableContent
                        transferableContent.hasMediaType(MediaType.Image) -> {
                            transferableContent.consume { item ->
                                item.uri?.let { uri ->
                                    state.addImages(
                                        context.createChatFilesByContents(
                                            listOf(uri)
                                        )
                                    )
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAskUserMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showPicker = true
                            keyboardController?.hide()
                        },
                        shape = CircleShape,
                        color = placedSurfaceColor(),
                        border = BorderStroke(1.dp, outlineColor),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                val askUserQuestionStage = askUserMode?.stage as? AskUserComposerStage.Question
                val askUserCanAdvance = when (askUserMode?.stage) {
                    is AskUserComposerStage.Question -> inputText.trim().isNotBlank()
                    AskUserComposerStage.Review -> true
                    null -> false
                }
                val currentAction = when {
                    state.loading -> "loading"
                    askUserMode?.stage is AskUserComposerStage.Review -> "confirm"
                    askUserQuestionStage != null -> {
                        val isLastQuestion =
                            askUserQuestionStage.index == askUserQuestionStage.total - 1
                        when {
                            askUserCanAdvance && isLastQuestion -> "review"
                            askUserCanAdvance -> "next"
                            isLastQuestion -> "review_disabled"
                            else -> "next_disabled"
                        }
                    }
                    !state.isEmpty() -> "send"
                    else -> "model"
                }
                val actionContainerColor by animateColorAsState(
                    targetValue = when (currentAction) {
                        "loading" -> MaterialTheme.colorScheme.errorContainer
                        "send", "next", "review", "confirm" -> MaterialTheme.colorScheme.primary
                        "next_disabled", "review_disabled" -> nestedSurfaceColor()
                        else -> Color.Transparent
                    },
                    label = "InputActionContainerColor"
                )

                val isExpandedAction = currentAction in setOf(
                    "next",
                    "next_disabled",
                    "review",
                    "review_disabled",
                    "confirm",
                )
                val inputEndPadding = if (isExpandedAction) 76.dp else 48.dp
                val inputVerticalPadding = if (isAskUserMode) 12.dp else 8.dp

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = placedSurfaceColor(),
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isEditing() && !isAskUserMode) {
                            Surface(
                                color = nestedSurfaceColor(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp)
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 1.dp),
                            ) {
                                TextField(
                                    state = state.textContent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 1.dp)
                                        .focusRequester(state.focusRequester)
                                        .contentReceiver(receiveContentListener),
                                    readOnly = askUserMode?.stage is AskUserComposerStage.Review,
                                    placeholder = {
                                        Text(
                                            text = when (askUserMode?.stage) {
                                                AskUserComposerStage.Review -> "Confirm your answers"
                                                is AskUserComposerStage.Question -> "Type your own answer"
                                                null -> "Ask ${assistant.name}"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                                    colors = TextFieldDefaults.colors().copy(
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 16.dp,
                                        end = inputEndPadding,
                                        top = inputVerticalPadding,
                                        bottom = inputVerticalPadding
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = {
                                            when (currentAction) {
                                                "send",
                                                "loading",
                                                "next",
                                                "review",
                                                "confirm" -> sendMessage()
                                                "model" -> {
                                                    haptics.perform(HapticPattern.Pop)
                                                    keyboardController?.hide()
                                                    showModelPicker = true
                                                }
                                            }
                                        },
                                        shape = if (isExpandedAction) RoundedCornerShape(18.dp) else CircleShape,
                                        color = actionContainerColor,
                                        modifier = if (isExpandedAction) {
                                            Modifier
                                                .height(40.dp)
                                                .defaultMinSize(minWidth = 40.dp)
                                        } else {
                                            Modifier.size(40.dp)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 40.dp)
                                                .padding(horizontal = if (isExpandedAction) 12.dp else 0.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AnimatedContent(
                                                targetState = currentAction,
                                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                                label = "ActionContent"
                                            ) { action ->
                                                when (action) {
                                                    "loading" -> {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Stop,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(22.dp),
                                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                    "send" -> {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ArrowUpward,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(22.dp),
                                                            tint = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                    "next", "next_disabled" -> {
                                                        Text(
                                                            text = "Next",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (action == "next_disabled") {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            } else {
                                                                MaterialTheme.colorScheme.onPrimary
                                                            }
                                                        )
                                                    }
                                                    "review", "review_disabled" -> {
                                                        Text(
                                                            text = "Review",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (action == "review_disabled") {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            } else {
                                                                MaterialTheme.colorScheme.onPrimary
                                                            }
                                                        )
                                                    }
                                                    "confirm" -> {
                                                        Text(
                                                            text = "Confirm",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                    "model" -> {
                                                        Box(
                                                            modifier = Modifier.size(30.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (activeChatModel != null) {
                                                                me.rerere.rikkahub.ui.components.ui.ModelIcon(
                                                                    model = activeChatModel,
                                                                    provider = activeProvider,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    color = Color.Transparent,
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.ViewModule,
                                                                    contentDescription = stringResource(R.string.setting_model_page_chat_model),
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }

            bottomAccessory?.invoke()
        }  // Column ends
    }  // Box ends
    
    // Bottom sheet picker with custom MinimalPickerContent
    // Optical roundness: sheet corners (40dp) = button corners (24dp) + padding (16dp)
    if (showPicker && !isAskUserMode) {
        ModalBottomSheet(
containerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
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
                cameraPermission = cameraPermission,
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onUpdateChatModel = onUpdateChatModel,
                onUpdateConversation = onUpdateConversation,
                onUpdateAssistant = onUpdateAssistant,
                onUpdateSearchService = onUpdateSearchService,
                onNavigateToLorebook = onNavigateToLorebook,
                onRefreshContext = onRefreshContext,
                onDismiss = { showPicker = false }
            )
        }
    }

    if (showModelPicker && !isAskUserMode) {
        ChatModelPickerSheet(
            settings = settings,
            assistant = assistant,
            onUpdateChatModel = onUpdateChatModel,
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun SlashSkillsPicker(
    skills: List<Skill>,
    onSelect: (Skill) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppShapes.Grouped,
        color = placedSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(skills, key = { it.id }) { skill ->
                val slashCommand = skill.slashCommand()
                ListItem(
                    modifier = Modifier.clickable { onSelect(skill) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = ModeIcons.getIcon(skill.icon ?: "category"),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    headlineContent = {
                        Text(
                            text = slashCommand,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    supportingContent = {
                        val summary = when {
                            skill.description.isNotBlank() -> skill.description
                            skill.instructions.isNotBlank() -> skill.instructions
                            else -> skill.name
                        }
                        Text(
                            text = summary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MinimalPickerContent(
    state: ChatInputState,
    conversation: Conversation,
    settings: Settings,
    assistant: Assistant,
    cameraPermission: me.rerere.rikkahub.ui.components.ui.permission.PermissionState,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateChatModel: (Model) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onNavigateToLorebook: (String) -> Unit,
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localSettings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    
    // OLED dark mode detection for buttons (not sheet backgrounds)
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = me.rerere.rikkahub.ui.theme.LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    // Sheet background uses the shared neutral placed surface.
    val sheetContainerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor()
    
    // Camera state
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    
    // Crop state
    var showCropScreen by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Uri?>(null) }
    
    // Sub-picker states
    var showModelPicker by remember { mutableStateOf(false) }
    var showReasoningPicker by remember { mutableStateOf(false) }
    var showSkillsPicker by remember { mutableStateOf(false) }
    var showLorebooksPicker by remember { mutableStateOf(false) }
    var showContextRefreshDialog by remember { mutableStateOf(false) }
    var showSearchPicker by remember { mutableStateOf(false) }
    val assistantDefaultSkillIds = assistant.enabledSkillIds
    val effectiveActiveSkillIds = if (conversation.enabledModeIds.isNotEmpty()) {
        conversation.enabledModeIds
    } else {
        assistantDefaultSkillIds
    }
    
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
    
    // Nested grouped buttons sit inside a 4dp padded shell, so their exposed corners
    // need inset-adjusted radii to keep the outer roundness optically correct.
    val uploadButtonInset = 4.dp
    val leftButtonShape = groupedInsetItemShape(
        position = ItemPosition.FIRST,
        inset = uploadButtonInset,
        axis = GroupedAxis.Horizontal,
    )
    val middleButtonShape = groupedInsetItemShape(
        position = ItemPosition.MIDDLE,
        inset = uploadButtonInset,
        axis = GroupedAxis.Horizontal,
    )
    val rightButtonShape = groupedInsetItemShape(
        position = ItemPosition.LAST,
        inset = uploadButtonInset,
        axis = GroupedAxis.Horizontal,
    )

    fun importImages(
        uris: List<Uri>,
        dismissOnSuccess: Boolean = false,
        onFinally: () -> Unit = {}
    ) {
        if (uris.isEmpty()) {
            onFinally()
            return
        }

        scope.launch {
            val importedUris = withContext(Dispatchers.IO) {
                context.createChatFilesByContents(uris)
            }
            if (importedUris.isEmpty()) {
                Log.w("MinimalChatInput", "Failed to import ${uris.size} selected image(s)")
                toaster.show("Couldn't add the selected image. Please try again.")
            } else {
                state.addImages(importedUris)
                if (dismissOnSuccess) {
                    onDismiss()
                }
            }
            onFinally()
        }
    }
    
    // Crop screen dialog
    if (showCropScreen && imageToCrop != null) {
        CropImageScreen(
            sourceUri = imageToCrop!!,
            onCropComplete = { croppedUri ->
                importImages(
                    uris = listOf(croppedUri),
                    onFinally = {
                        showCropScreen = false
                        imageToCrop = null
                        cameraOutputFile?.delete()
                        cameraOutputFile = null
                        cameraOutputUri = null
                    }
                )
            },
            onCancel = {
                showCropScreen = false
                imageToCrop = null
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            }
        )
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        val capturedUri = cameraOutputUri
        val capturedFile = cameraOutputFile
        if (captureSuccessful && capturedUri != null) {
            if (localSettings.displaySetting.skipCropImage) {
                importImages(
                    uris = listOf(capturedUri),
                    onFinally = {
                        capturedFile?.delete()
                        cameraOutputFile = null
                        cameraOutputUri = null
                    }
                )
            } else {
                imageToCrop = capturedUri
                showCropScreen = true
            }
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
            if (localSettings.displaySetting.skipCropImage || selectedUris.size > 1) {
                importImages(
                    uris = selectedUris,
                    dismissOnSuccess = true
                )
            } else {
                imageToCrop = selectedUris.first()
                showCropScreen = true
            }
        }
    }
    
    // File picker launcher - categorizes files by type
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            val isPythonEnabled = assistant.localTools.any { it is LocalToolOption.PythonEngine }
            scope.launch {
                val importedFiles = withContext(Dispatchers.IO) {
                    context.prepareImportedPickerFiles(
                        selectedUris = selectedUris,
                        isPythonEnabled = isPythonEnabled,
                    )
                }

                importedFiles.unsupportedFileNames.forEach { fileName ->
                    toaster.show("Unsupported file type: $fileName (Enable Python tool to use this file)")
                }
                importedFiles.failedFileNames.forEach { fileName ->
                    toaster.show("Couldn't add file: $fileName")
                }

                if (importedFiles.imageUris.isNotEmpty()) {
                    state.addImages(importedFiles.imageUris)
                }
                if (importedFiles.documents.isNotEmpty()) {
                    state.addFiles(importedFiles.documents)
                }
                onDismiss()
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // File upload buttons - only this cluster stays filled; lower rows stay flat on the sheet.
        Surface(
            shape = AppShapes.Grouped,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(4.dp),
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
        }
        
        // Separator
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        
        GroupedStack {
            // Model picker - uses actual model icon, full-width clickable
            val currentModel = settings.getCurrentChatModel()
            val provider = currentModel?.findProvider(providers = settings.providers)
            MinimalPickerItem(
                icon = {
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
                    showModelPicker = true
                }
            )

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

            val searchService = settings.searchServices.getOrNull(effectiveProviderIndex)
            val searchProviderName = if (searchService != null) {
                SearchServiceOptions.TYPES[searchService::class]
            } else null
            MinimalPickerItem(
                icon = {
                    if (enableSearch && searchProviderName != null) {
                        AutoAIIcon(
                            name = searchProviderName,
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

            val availableSkills = settings.skills
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

            val activeLorebooksCount = assistant.enabledLorebookIds.size
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

            if (assistant.enableContextRefresh && conversation.currentMessages.size > 2) {
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
    
    if (showModelPicker) {
        ChatModelPickerSheet(
            settings = settings,
            assistant = assistant,
            onUpdateChatModel = onUpdateChatModel,
            onDismiss = { showModelPicker = false }
        )
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
            onUpdateAssistant = onUpdateAssistant,
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
            onDismiss = { showContextRefreshDialog = false }
        )
    }
    
    // Search picker sheet (same as floating toolbar) - direct content, no intermediate button
    if (showSearchPicker) {
        val chatModel = settings.getCurrentChatModel()
        
        ModalBottomSheet(
containerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
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
private fun ChatModelPickerSheet(
    settings: Settings,
    assistant: Assistant,
    onUpdateChatModel: (Model) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val modelPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filteredProviders = settings.providers.filter {
        it.enabled && it.models.any { model -> model.type == me.rerere.ai.provider.ModelType.CHAT }
    }

    ModalBottomSheet(
        containerColor = placedSurfaceColor(),
        onDismissRequest = onDismiss,
        sheetState = modelPickerSheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        modelPickerSheetState.hide()
                        onDismiss()
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
                        onDismiss()
                    }
                },
                onDismiss = {
                    scope.launch {
                        modelPickerSheetState.hide()
                        onDismiss()
                    }
                }
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
        color = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
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
        color = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
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
    Surface(
        onClick = onClick,
        shape = shape,
        color = nestedSurfaceColor(),
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
    Surface(
        onClick = onClick,
        shape = shape,
        color = nestedSurfaceColor(),
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
    AppPickerRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        style = AppPickerRowStyle.FlatTransparent,
        onClick = onClick,
    )
}

@Composable
private fun MediaFileInputRow(
    state: ChatInputState,
    onDelete: (Uri) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        state.messageContent.filterIsInstance<UIMessagePart.Image>().fastForEach { image ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(20.dp)
                        .clickable {
                            state.messageContent = state.messageContent.filterNot { it == image }
                            onDelete(image.url.toUri())
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Video>().fastForEach { video ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
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
                        .size(20.dp)
                        .clickable {
                            state.messageContent = state.messageContent.filterNot { it == video }
                            onDelete(video.url.toUri())
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Audio>().fastForEach { audio ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
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
                        .size(20.dp)
                        .clickable {
                            state.messageContent = state.messageContent.filterNot { it == audio }
                            onDelete(audio.url.toUri())
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Document>().fastForEach { document ->
            me.rerere.rikkahub.ui.components.ui.DocumentChip(
                fileName = document.fileName,
                mimeType = document.mime,
                onRemove = {
                    state.messageContent = state.messageContent.filterNot { it == document }
                    onDelete(document.url.toUri())
                }
            )
        }
    }
}

private fun Skill.slashCommand(): String {
    val hinted = argumentHint?.trim()
    return when {
        !hinted.isNullOrBlank() && hinted.startsWith("/") -> hinted
        name.isNotBlank() -> "/$name"
        else -> "/skill"
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
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = placedSurfaceColor(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
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


