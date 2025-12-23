package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.content.Intent

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import me.rerere.ai.core.ReasoningLevel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import me.rerere.ai.provider.BuiltInTools
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.ui.draw.rotate
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.components.crop.CropImageScreen
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer

enum class ExpandState {
    Collapsed,
    Files,
}

@Composable
fun ChatInput(
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
    onUpdateSearchService: (Int) -> Unit,
    onClearContext: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)

    val keyboardController = LocalSoftwareKeyboardController.current

    fun sendMessage() {
        keyboardController?.hide()
        haptics.perform(HapticPattern.Send)
        if (state.loading) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        keyboardController?.hide()
        haptics.perform(HapticPattern.Thud)
        if (state.loading) onCancelClick() else onLongSendClick()
    }

    var expand by remember { mutableStateOf(ExpandState.Collapsed) }
    fun dismissExpand() {
        expand = ExpandState.Collapsed
    }

    fun expandToggle(type: ExpandState) {
        haptics.perform(HapticPattern.Pop)
        if (expand == type) {
            dismissExpand()
        } else {
            expand = type
        }
    }

    // Collapse when ime is visible
    val imeVisile = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(imeVisile) {
        if (imeVisile) {
            expand = ExpandState.Collapsed
        } else if (state.textContent.text.isEmpty()) {
            focusManager.clearFocus()
        }
    }

    // Focus state for the text field
    var isFocused by remember { mutableStateOf(false) }
    
    // Expanded state logic: Expanded if focused OR text is not empty
    val isExpanded = isFocused || state.textContent.text.isNotEmpty()

    Box(
        modifier = modifier.fillMaxWidth(), // Apply passed modifier (alignment) here
        contentAlignment = Alignment.BottomCenter
    ) {


        // Corner radius for pill-shaped input bar (35dp outer, 28dp inner)
        val cornerRadius = 35.dp
        val innerCornerSize = 28.dp

        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp), // Raised toolbar
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Medias (shown above suggestions when both exist)
            if (state.messageContent.isNotEmpty()) {
                MediaFileInputRow(state = state, context = context)
            }

            // Suggestions row (shown above toolbar, below images)
            if (chatSuggestions.isNotEmpty()) {
                ChatSuggestionsRow(
                    suggestions = chatSuggestions,
                    onClickSuggestion = onClickSuggestion
                )
            }

            // Medias (only show if no suggestions - handled above)
            if (state.messageContent.isEmpty()) {
                // No-op: medias already shown above when present
            } else if (chatSuggestions.isEmpty()) {
                // Only show media row here if there are no suggestions and has content
                // (already handled at top)
            }

            // Floating Input Bar
            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLow, // Material You Surface Color
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp), // Increased padding
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp) // Tighter spacing
                ) {
                    // Plus button with physics-based animations
                    val plusInteractionSource = remember { MutableInteractionSource() }
                    val isPlusPressed by plusInteractionSource.collectIsPressedAsState()
                    val plusScale by animateFloatAsState(
                        targetValue = if (isPlusPressed) 0.85f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.4f,
                            stiffness = 400f
                        ),
                        label = "plus_scale"
                    )
                    val plusAlpha by animateFloatAsState(
                        targetValue = if (isPlusPressed) 0.7f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.6f,
                            stiffness = 300f
                        ),
                        label = "plus_alpha"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = plusScale
                                scaleY = plusScale
                                alpha = plusAlpha
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = plusInteractionSource,
                                indication = LocalIndication.current
                            ) {
                                expandToggle(ExpandState.Files)
                            }
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (expand == ExpandState.Files) 45f else 0f,
                            animationSpec = spring(
                                dampingRatio = 0.5f,
                                stiffness = 300f
                            ),
                            label = "rotation"
                        )
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.more_options),
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Search & Reasoning (Visible when NOT expanded)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isExpanded,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search
                            val enableSearchMsg = stringResource(R.string.web_search_enabled)
                            val disableSearchMsg = stringResource(R.string.web_search_disabled)
                            val chatModel = settings.getCurrentChatModel()
                            
                            SearchPickerButton(
                                enableSearch = enableSearch,
                                settings = settings,
                                shape = CircleShape,
                                onToggleSearch = { enabled ->
                                    onToggleSearch(enabled)
                                    toaster.show(
                                        message = if (enabled) enableSearchMsg else disableSearchMsg,
                                        duration = 1.seconds,
                                        type = if (enabled) {
                                            ToastType.Success
                                        } else {
                                            ToastType.Normal
                                        }
                                    )
                                },
                                onUpdateSearchService = onUpdateSearchService,
                                model = chatModel,
                                selectedProviderIndex = when (val mode = assistant.searchMode) {
                                    is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> mode.index
                                    else -> -1
                                },
                                isBuiltInMode = assistant.searchMode is me.rerere.rikkahub.data.model.AssistantSearchMode.BuiltIn,
                                preferBuiltInSearch = assistant.preferBuiltInSearch,
                                onTogglePreferBuiltInSearch = { enabled ->
                                    onUpdateAssistant(assistant.copy(preferBuiltInSearch = enabled))
                                },
                                contentColor = if (enableSearch || chatModel?.tools?.contains(BuiltInTools.Search) == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                onlyIcon = true
                            )

                            // Reasoning
                            val hasReasoning = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                            if (hasReasoning) {
                                ReasoningButton(
                                    reasoningTokens = assistant.thinkingBudget ?: 0,
                                    shape = CircleShape,
                                    onUpdateReasoningTokens = {
                                        onUpdateAssistant(assistant.copy(thinkingBudget = it))
                                    },
                                    onlyIcon = true,
                                    contentColor = if (ReasoningLevel.fromBudgetTokens(assistant.thinkingBudget ?: 0).isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Inner Capsule (Text Input Field + Model Picker + Send Button)
                    val amoledMode by rememberAmoledDarkMode()
                    val containerColor = if (amoledMode && LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                    val elevation = if (amoledMode && LocalDarkMode.current) 0.dp else 6.dp
                    
                    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && LocalDarkMode.current) 0.dp else LocalAbsoluteTonalElevation.current) {
                        Surface(
                            shape = RoundedCornerShape(innerCornerSize), // Dynamic Inner Shape
                            color = containerColor,
                            tonalElevation = elevation,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp) // Increased start padding
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TextInputRow(
                                        state = state,
                                        context = context,
                                        isFocused = isFocused,
                                        onFocusChange = { isFocused = it },
                                        trailingIcon = {
                                            // Crossfade between Model Picker and Send Button
                                            androidx.compose.animation.AnimatedContent(
                                                targetState = !state.isEmpty() || state.loading,
                                                transitionSpec = {
                                                    androidx.compose.animation.fadeIn(
                                                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                                                    ) togetherWith androidx.compose.animation.fadeOut(
                                                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                                                    )
                                                },
                                                label = "button_crossfade"
                                            ) { expanded ->
                                                if (expanded) {
                                                    // Send Button with physics-based press feedback
                                                    val sendInteractionSource = remember { MutableInteractionSource() }
                                                    val isSendPressed by sendInteractionSource.collectIsPressedAsState()
                                                    val sendScale by animateFloatAsState(
                                                        targetValue = if (isSendPressed) 0.85f else 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.4f,
                                                            stiffness = 400f
                                                        ),
                                                        label = "send_scale"
                                                    )
                                                    val sendAlpha by animateFloatAsState(
                                                        targetValue = if (isSendPressed) 0.8f else 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.6f,
                                                            stiffness = 300f
                                                        ),
                                                        label = "send_alpha"
                                                    )
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .graphicsLayer {
                                                                scaleX = sendScale
                                                                scaleY = sendScale
                                                                alpha = sendAlpha
                                                            }
                                                            .clip(CircleShape)
                                                            .combinedClickable(
                                                                interactionSource = sendInteractionSource,
                                                                indication = null,
                                                                enabled = state.loading || !state.isEmpty(),
                                                                onClick = {
                                                                    expand = ExpandState.Collapsed
                                                                    sendMessage()
                                                                },
                                                                onLongClick = {
                                                                    expand = ExpandState.Collapsed
                                                                    sendMessageWithoutAnswer()
                                                                }
                                                            )
                                                            .background(
                                                                color = when {
                                                                    state.loading -> MaterialTheme.colorScheme.errorContainer
                                                                    state.isEmpty() -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                                    else -> MaterialTheme.colorScheme.primary
                                                                }
                                                            )
                                                    ) {
                                                        val contentColor = when {
                                                            state.loading -> MaterialTheme.colorScheme.onErrorContainer
                                                            state.isEmpty() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                            else -> MaterialTheme.colorScheme.onPrimary
                                                        }
                                                        if (state.loading) {
                                                            KeepScreenOn()
                                                            Icon(Icons.Rounded.Stop, stringResource(R.string.stop), tint = contentColor, modifier = Modifier.size(20.dp))
                                                        } else {
                                                            Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.send), tint = contentColor, modifier = Modifier.size(20.dp))
                                                        }
                                                    }
                                                } else {
                                                    // Model Selector
                                                    ModelSelector(
                                                        modelId = assistant.chatModelId ?: settings.chatModelId,
                                                        providers = settings.providers,
                                                        onSelect = {
                                                            onUpdateChatModel(it)
                                                            dismissExpand()
                                                        },
                                                        type = ModelType.CHAT,
                                                        onlyIcon = true,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // Expanded content (Files Picker)
            Box(
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                BackHandler(
                    enabled = expand != ExpandState.Collapsed,
                ) {
                    dismissExpand()
                }
                if (expand == ExpandState.Files) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 8.dp
                    ) {
                        FilesPicker(
                            conversation = conversation,
                            state = state,
                            assistant = assistant,
                            onClearContext = onClearContext,
                            onUpdateAssistant = onUpdateAssistant,
                            onDismiss = { dismissExpand() }
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun TextInputRow(
    state: ChatInputState,
    context: Context,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val assistant = LocalSettings.current.getCurrentAssistant()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // TextField
        // Removed Surface wrapper to blend with FloatingInputBar
        Column {
            if (state.isEditing()) {
                Surface(
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 8.dp, start = 12.dp, top = 8.dp) // Added start and top padding
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
            var isFullScreen by remember { mutableStateOf(false) }
            val receiveContentListener = remember {
                ReceiveContentListener { transferableContent ->
                    when {
                        transferableContent.hasMediaType(MediaType.Image) -> {
                            transferableContent.consume { item ->
                                val uri = item.uri
                                if (uri != null) {
                                    state.addImages(
                                        context.createChatFilesByContents(
                                            listOf(
                                                uri
                                            )
                                        )
                                    )
                                }
                                uri != null
                            }
                        }

                        else -> transferableContent
                    }
                }
            }
            TextField(
                state = state.textContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .contentReceiver(receiveContentListener)
                    .onFocusChanged {
                        onFocusChange(it.isFocused)
                    },
                shape = RoundedCornerShape(20.dp),
                placeholder = {
                    Text(stringResource(R.string.chat_input_placeholder))
                },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp), // Increased padding for centering
                colors = TextFieldDefaults.colors().copy(
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                leadingIcon = if (assistant.quickMessages.isNotEmpty()) {
                    {
                        QuickMessageButton(assistant = assistant, state = state)
                    }
                } else null,
                trailingIcon = trailingIcon
            )
            if (isFullScreen) {
                FullScreenEditor(state = state) {
                    isFullScreen = false
                }
            }
        }
    }
}

@Composable
private fun QuickMessageButton(
    assistant: Assistant,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }
    ) {
        Icon(Icons.Rounded.FlashOn, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp)
                .width(IntrinsicSize.Min)
        ) {
            assistant.quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFileInputRow(
    state: ChatInputState,
    context: Context
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == image }
                            // Delete image
                            context.deleteChatFiles(listOf(image.url.toUri()))
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == video }
                            // Delete image
                            context.deleteChatFiles(listOf(video.url.toUri()))
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == audio }
                            // Delete image
                            context.deleteChatFiles(listOf(audio.url.toUri()))
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Document>()
            .fastForEach { document ->
                Box {
                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(max = 128.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(
                                0.8f
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    text = document.fileName,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier
                            .clip(CircleShape)
                            .padding(end = 4.dp)
                            .size(24.dp)
                            .clickable {
                                // Remove image
                                state.messageContent =
                                    state.messageContent.filterNot { it == document }
                                // Delete image
                                context.deleteChatFiles(listOf(document.url.toUri()))
                            }
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.secondary),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
    }
}

@Composable
private fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    suggestions: List<String>,
    onClickSuggestion: (String) -> Unit
) {
    val haptics = rememberPremiumHaptics()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // State to track interaction
    var pressedSuggestionIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSuggestionIndex by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            // Each chip animates in with a staggered delay
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(suggestion) {
                kotlinx.coroutines.delay(index * 50L) // Staggered delay
                visible = true
            }

            // Determine if this item is selected or pressed
            val isSelected = selectedSuggestionIndex == index
            val isPressed = pressedSuggestionIndex == index
            val isAnythingSelected = selectedSuggestionIndex != null
            val isAnythingPressed = pressedSuggestionIndex != null
            
            // Animation States
            val targetScale = when {
                isSelected -> 1.05f // quick spring up before disappearing
                isPressed -> 0.9f // shrink when pressed
                else -> 1f
            }
            
            val targetAlpha = when {
                isSelected -> 0f // fade out after selection
                isAnythingSelected -> 0f // others disappear immediately
                isAnythingPressed && !isPressed -> 0.5f // others fade when one is pressed
                visible -> 1f
                else -> 0f
            }

            // Animate scale
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = if (isSelected) 0.4f else 0.7f,
                    stiffness = if (isSelected) 500f else 300f
                ),
                label = "suggestion_scale"
            )

            // Animate alpha
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 300f
                ),
                label = "suggestion_alpha"
            )
            
            // Handle disappearance after selection animation
            // When selected, wait for animation then trigger callback
            LaunchedEffect(isSelected) {
                if(isSelected) {
                    kotlinx.coroutines.delay(200) // Wait for spring up
                    onClickSuggestion(suggestion)
                    //Reset state is handled by parent recomposition usually, 
                    // or we can reset here but the list might change.
                    selectedSuggestionIndex = null 
                    pressedSuggestionIndex = null
                }
            }

            if (alpha > 0.01f) {
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(50))
                        .pointerInput(suggestion) {
                            detectTapGestures(
                                onPress = {
                                    pressedSuggestionIndex = index
                                    tryAwaitRelease()
                                    pressedSuggestionIndex = null
                                },
                                onTap = {
                                    haptics.perform(HapticPattern.Pop)
                                    selectedSuggestionIndex = index
                                }
                            )
                        },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    onClearContext: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit
) {
    val settings = LocalSettings.current
    val amoledMode by rememberAmoledDarkMode()
    val provider = settings.getCurrentChatModel()?.findProvider(providers = settings.providers)
    
    // Position-based corner shapes for 2x2 grid
    val topLeftShape = RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    val topRightShape = RoundedCornerShape(topStart = 10.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    val bottomLeftShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 10.dp)
    val bottomRightShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 24.dp)
    // For single item in row
    val fullBottomShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val supportVideo = provider != null && provider is ProviderSetting.Google
        if(supportVideo) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TakePicButton(shape = topLeftShape) {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ImagePickButton(shape = topRightShape) {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        VideoPickButton(shape = bottomLeftShape) {
                            state.addVideos(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FilePickButton(shape = bottomRightShape) {
                            state.addFiles(it)
                            onDismiss()
                        }
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TakePicButton(shape = topLeftShape) {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ImagePickButton(shape = topRightShape) {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FilePickButton(shape = fullBottomShape) {
                            state.addFiles(it)
                            onDismiss()
                        }
                    }
                }
            }
        }

        if (!WindowInsets.isImeVisible) {
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = stringResource(R.string.chat_page_learning_mode),
                    )
                },
                headlineContent = {
                    Text(stringResource(R.string.chat_page_learning_mode))
                },
                supportingContent = {
                    Text(stringResource(R.string.chat_page_learning_mode_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.learningMode,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(learningMode = it))
                        }
                    )
                },
            )
        }

    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState,
    onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }
                        ) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = state.textContent,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(stringResource(R.string.chat_input_placeholder))
                        },
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePickButton(
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    onAddImages: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    
    // State for crop dialog
    var showCropScreen by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Uri?>(null) }

    // Show crop screen dialog
    if (showCropScreen && imageToCrop != null) {
        CropImageScreen(
            sourceUri = imageToCrop!!,
            onCropComplete = { croppedUri ->
                onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
                showCropScreen = false
                imageToCrop = null
            },
            onCancel = {
                showCropScreen = false
                imageToCrop = null
            }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            Log.d("ImagePickButton", "Selected URIs: $selectedUris")
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add images
                onAddImages(context.createChatFilesByContents(selectedUris))
            } else {
                // Show crop interface
                if (selectedUris.size == 1) {
                    // Single image - offer crop
                    imageToCrop = selectedUris.first()
                    showCropScreen = true
                } else {
                    // Multiple images - no crop
                    onAddImages(context.createChatFilesByContents(selectedUris))
                }
            }
        } else {
            Log.d("ImagePickButton", "No images selected")
        }
    }

    BigIconTextButton(
        shape = shape,
        icon = {
            Icon(Icons.Rounded.Photo, null)
        }
    ) {
        imagePickerLauncher.launch("image/*")
    }
}


@Composable
fun TakePicButton(
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    onAddImages: (List<Uri>) -> Unit = {}
) {
    val cameraPermission = rememberPermissionState(PermissionCamera)

    val context = LocalContext.current
    val settings = LocalSettings.current
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    
    // State for crop dialog
    var showCropScreen by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Uri?>(null) }

    // Show crop screen dialog
    if (showCropScreen && imageToCrop != null) {
        CropImageScreen(
            sourceUri = imageToCrop!!,
            onCropComplete = { croppedUri ->
                onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
                showCropScreen = false
                imageToCrop = null
                // Clean up camera temp file after cropping is done
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            },
            onCancel = {
                showCropScreen = false
                imageToCrop = null
                // Clean up camera temp file if crop cancelled
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            }
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add image
                onAddImages(context.createChatFilesByContents(listOf(cameraOutputUri!!)))
                // Clean up camera temp file
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            } else {
                // Show crop interface
                imageToCrop = cameraOutputUri
                showCropScreen = true
            }
        } else {
            // Clean up camera temp file if capture failed
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }

    // 使用权限管理器包装
    PermissionManager(
        permissionState = cameraPermission
    ) {
        BigIconTextButton(
            shape = shape,
            icon = {
                Icon(Icons.Rounded.CameraAlt, null)
            }
        ) {
            if (cameraPermission.allRequiredPermissionsGranted) {
                // 权限已授权，直接启动相机
                cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
                cameraOutputUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cameraOutputFile!!
                )
                cameraLauncher.launch(cameraOutputUri!!)
            } else {
                // 请求权限
                cameraPermission.requestPermissions()
            }
        }
    }
}


@Composable
fun VideoPickButton(
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    onAddVideos: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onAddVideos(context.createChatFilesByContents(selectedUris))
        }
    }

    BigIconTextButton(
        shape = shape,
        icon = {
            Icon(Icons.Rounded.VideoLibrary, null)
        }
    ) {
        videoPickerLauncher.launch("video/*")
    }
}



@Composable
fun FilePickButton(
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    onAddFiles: (List<UIMessagePart.Document>) -> Unit = {}
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                // Allow all file types
                val allowedMimeTypes = setOf("*/*")

                val documents = uris.mapNotNull { uri ->
                    val fileName = context.getFileNameFromUri(uri) ?: "file"
                    val mime = context.getFileMimeType(uri) ?: "text/plain"

                    // Filter by MIME type or file extension
                    val isAllowed = allowedMimeTypes.contains(mime) ||
                        mime.startsWith("text/") ||
                        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        mime == "application/pdf" ||
                        fileName.endsWith(".txt", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".csv", ignoreCase = true) ||
                        fileName.endsWith(".json", ignoreCase = true) ||
                        fileName.endsWith(".js", ignoreCase = true) ||
                        fileName.endsWith(".html", ignoreCase = true) ||
                        fileName.endsWith(".css", ignoreCase = true) ||
                        fileName.endsWith(".xml", ignoreCase = true) ||
                        fileName.endsWith(".py", ignoreCase = true) ||
                        fileName.endsWith(".java", ignoreCase = true) ||
                        fileName.endsWith(".kt", ignoreCase = true) ||
                        fileName.endsWith(".ts", ignoreCase = true) ||
                        fileName.endsWith(".tsx", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".markdown", ignoreCase = true) ||
                        fileName.endsWith(".mdx", ignoreCase = true) ||
                        fileName.endsWith(".yml", ignoreCase = true) ||
                        fileName.endsWith(".yaml", ignoreCase = true)

                    if (isAllowed) {
                        val localUri = context.createChatFilesByContents(listOf(uri))[0]
                        UIMessagePart.Document(
                            url = localUri.toString(),
                            fileName = fileName,
                            mime = mime
                        )
                    } else {
                        null
                    }
                }

                if (documents.isNotEmpty()) {
                    onAddFiles(documents)
                } else {
                    // Show toast for unsupported file types
                    toaster.show("Unsupported file type", type = ToastType.Error)
                }
            }
        }
    BigIconTextButton(
        shape = shape,
        icon = {
            Icon(Icons.Rounded.FolderOpen, null)
        }
    ) {
        pickMedia.launch(arrayOf("*/*"))
    }
}


@Composable
private fun BigIconTextButton(
    modifier: Modifier = Modifier,
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val amoledMode by rememberAmoledDarkMode()
    
    // Physics-based press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 400f
        ),
        label = "button_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "button_alpha"
    )
    
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onClick()
                }
            )
            .semantics {
                role = Role.Button
            }
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && LocalDarkMode.current) 0.dp else LocalAbsoluteTonalElevation.current) {
            Surface(
                shape = shape,
                color = if (amoledMode && LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (amoledMode && LocalDarkMode.current) 0.dp else 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BigIconTextButtonPreview() {
    Row(
        modifier = Modifier.padding(16.dp)
    ) {
        BigIconTextButton(
            icon = {
            Icon(Icons.Rounded.Photo, null)
            }
        ) {}
    }
}
