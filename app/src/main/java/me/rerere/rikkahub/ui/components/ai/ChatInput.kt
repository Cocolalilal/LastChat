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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
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
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
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
    onUpdateConversation: (Conversation) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onClearContext: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    onNavigateToLorebook: (String) -> Unit = {},
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult = { ChatService.ContextRefreshResult(false, errorMessage = "Not configured") },
    onDeleteFile: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()

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

    // Collapse when ime is hidden - always clear focus to show pickers
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            expand = ExpandState.Collapsed
        } else {
            // Always clear focus when keyboard closes to collapse toolbar
            focusManager.clearFocus()
        }
    }

    // Focus state for the text field
    var isFocused by remember { mutableStateOf(false) }
    
    // Expanded state logic: Expanded ONLY when focused (keyboard open)
    // When collapsed with text, show pickers and single-line text preview
    val isExpanded = isFocused

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
                MediaFileInputRow(
                    state = state,
                    onDelete = onDeleteFile
                )
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
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
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
                    // Delayed appearance - slight overlap with height animation for smoother feel
                    var showPickers by remember { mutableStateOf(false) }
                    LaunchedEffect(isExpanded) {
                        if (!isExpanded) {
                            // Start showing buttons while height is still animating (100ms overlap)
                            kotlinx.coroutines.delay(100)
                            showPickers = true
                        } else {
                            // Hide immediately when expanding
                            showPickers = false
                        }
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPickers,
                        modifier = Modifier,
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(150)
                        ) + androidx.compose.animation.expandHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(100)
                        ) + androidx.compose.animation.shrinkHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(150)
                        )
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
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
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
                                            // Send button shows ONLY when keyboard is open (focused) AND has content, or loading
                                            val showSendButton = state.loading || (isFocused && !state.isEmpty())
                                            androidx.compose.animation.AnimatedContent(
                                                targetState = showSendButton,
                                                transitionSpec = {
                                                    androidx.compose.animation.fadeIn(
                                                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                                                    ) togetherWith androidx.compose.animation.fadeOut(
                                                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
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
                                                        modifier = Modifier.size(36.dp),
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
                    // Optical roundness: outer radius (40dp) = inner button corners (24dp) + padding (16dp)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(40.dp),
                        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 8.dp
                    ) {
                        FilesPicker(
                            conversation = conversation,
                            state = state,
                            assistant = assistant,
                            onClearContext = onClearContext,
                            onUpdateAssistant = onUpdateAssistant,
                            onUpdateConversation = onUpdateConversation,
                            onNavigateToLorebook = onNavigateToLorebook,
                            onRefreshContext = onRefreshContext,
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
            // Always use MultiLine to preserve Enter key for newlines
            // Use animated height constraint for visual collapse
            val hasText = state.textContent.text.isNotEmpty()
            
            // Use imeVisible (keyboard state) for animation target - more stable than focus state
            val imeVisibleLocal = WindowInsets.isImeVisible
            
            // Get container color for fade gradient (matches inner capsule with tonal elevation)
            val amoledModeLocal by rememberAmoledDarkMode()
            val fadeColor = if (amoledModeLocal && LocalDarkMode.current) {
                Color.Black
            } else {
                // Use the elevated surface color to match the actual Surface appearance
                MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            }
            
            // Collapsed state: keyboard hidden
            val isCollapsed = !imeVisibleLocal
            
            // Check if text would need multiple lines (approx > 40 chars per line)
            val hasMultiLineContent = hasText && state.textContent.text.length > 40
            
            // Animated height with spring physics - only animate for multi-line content
            val animatedMaxHeight by animateDpAsState(
                targetValue = if (isCollapsed) 56.dp else 200.dp,
                animationSpec = if (hasMultiLineContent) {
                    spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f
                    )
                } else {
                    spring(
                        dampingRatio = 1.0f,  // Critically damped - no visible animation for single line
                        stiffness = 1000f
                    )
                },
                label = "text_height"
            )
            
            // Animated alpha for fade overlay - appears when collapsed with text
            val fadeAlpha by animateFloatAsState(
                targetValue = if (isCollapsed && hasText) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                ),
                label = "fade_alpha"
            )
            
            // Animated fade width - expands when collapsed with text
            val fadeWidth by animateDpAsState(
                targetValue = if (isCollapsed && hasText) 60.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = 350f
                ),
                label = "fade_width"
            )
            
            // Delayed lineLimits state - waits for height animation before switching to SingleLine
            // This prevents text from reflowing mid-animation
            // outputTransformation handles newlines by replacing them with spaces visually
            var useSingleLine by remember { mutableStateOf(false) }
            LaunchedEffect(isCollapsed) {
                if (isCollapsed) {
                    // Wait for height animation to mostly complete before switching to SingleLine
                    kotlinx.coroutines.delay(180)
                    useSingleLine = true
                } else {
                    // Immediately switch to MultiLine when expanding for smooth typing
                    useSingleLine = false
                }
            }
            
            // Box with animated height constraint and gradient fade mask
            Box(
                modifier = Modifier
                    .heightIn(max = animatedMaxHeight)
                    .clipToBounds()
                    .drawWithContent {
                        // Draw the content first
                        drawContent()
                        
                        // Only draw the fade overlay when collapsed (keyboard hidden) with text
                        if (fadeWidth > 0.dp) {
                            // Draw a gradient overlay on the right side (before trailing icon)
                            val fadeWidthPx = fadeWidth.toPx()
                            // Position gradient to end just before the trailing icon starts
                            val endX = size.width - 48.dp.toPx() // trailing icon area
                            val startX = endX - fadeWidthPx
                            
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        fadeColor.copy(alpha = 0f),
                                        fadeColor
                                    ),
                                    startX = startX,
                                    endX = endX
                                ),
                                topLeft = Offset(startX, 0f),
                                size = Size(fadeWidthPx, size.height)
                            )
                        }
                    }
            ) {
                TextField(
                    state = state.textContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .focusRequester(state.focusRequester)
                        .contentReceiver(receiveContentListener)
                        .onFocusChanged {
                            onFocusChange(it.isFocused)
                        },
                    shape = RoundedCornerShape(20.dp),
                    placeholder = {
                        Text(stringResource(R.string.chat_input_placeholder))
                    },
                    // Use SingleLine when collapsed to prevent word wrapping (text extends horizontally)
                    // Use MultiLine when expanded (keyboard open) for normal editing
                    // Uses delayed useSingleLine state for smooth animation transition
                    lineLimits = if (useSingleLine) {
                        TextFieldLineLimits.SingleLine
                    } else {
                        TextFieldLineLimits.MultiLine(maxHeightInLines = 5)
                    },
                    // When collapsed (SingleLine), visually replace newlines with spaces
                    // This allows SingleLine mode even when text contains newlines
                    outputTransformation = if (useSingleLine) {
                        androidx.compose.foundation.text.input.OutputTransformation {
                            // Replace newlines with spaces for visual display only
                            val text = asCharSequence().toString()
                            if (text.contains('\n')) {
                                replace(0, length, text.replace('\n', ' '))
                            }
                        }
                    } else null,
                    // Keep Enter key as newline (not action/done) - ImeAction.None disables the action key
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.None
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    colors = TextFieldDefaults.colors().copy(
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    trailingIcon = trailingIcon
                )
            }
            if (isFullScreen) {
                FullScreenEditor(state = state) {
                    isFullScreen = false
                }
            }
        }
    }
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == image }
                            // Delete image
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == video }
                            // Delete image
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
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == audio }
                            // Delete image
                            onDelete(audio.url.toUri())
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Document>()
            .fastForEach { document ->
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

@Composable
private fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    suggestions: List<String>,
    onClickSuggestion: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // State to track interaction
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
                        brush = Brush.horizontalGradient(colorStops = colorStops),
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
}

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    onClearContext: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onNavigateToLorebook: (String) -> Unit,
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult,
    onDismiss: () -> Unit
) {
    val settings = LocalSettings.current
    val amoledMode by rememberAmoledDarkMode()
    val provider = settings.getCurrentChatModel()?.findProvider(providers = settings.providers)
    
    val isDarkMode = LocalDarkMode.current
    val isKeyboardVisible = WindowInsets.isImeVisible
    val showContextRefresh = assistant.enableContextRefresh && !isKeyboardVisible && conversation.currentMessages.size > 2
    
    // Shapes for 3-button row - different based on keyboard visibility
    val topLeftShape = if (isKeyboardVisible) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    }
    val topMiddleShape = RoundedCornerShape(10.dp)
    val topRightShape = if (isKeyboardVisible) {
        RoundedCornerShape(topStart = 10.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    }
    // Shapes for modes/lorebooks row - middle if context refresh enabled, bottom if not
    val middleLeftShape = RoundedCornerShape(10.dp)
    val middleRightShape = RoundedCornerShape(10.dp)
    val bottomLeftShape = if (showContextRefresh) middleLeftShape else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 10.dp)
    val bottomRightShape = if (showContextRefresh) middleRightShape else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 24.dp)
    // Full-width bottom row shape for context refresh
    val fullBottomShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    
    // State for context refresh dialog
    var showContextRefreshDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // File upload buttons row: Capture, Photo Library, Files
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TakePicButton(shape = topLeftShape) {
                    state.addImages(it)
                    onDismiss()
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ImagePickButton(shape = topMiddleShape) {
                    state.addImages(it)
                    onDismiss()
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Check if Python is enabled for this assistant
                val isPythonEnabled = assistant.localTools.any { it is LocalToolOption.PythonEngine }
                FilePickButton(
                    shape = topRightShape,
                    allowAllTypes = isPythonEnabled
                ) {
                    state.addFiles(it)
                    onDismiss()
                }
            }
        }
        
        // Modes and Lorebooks row - hidden when keyboard is visible
        var showModesPicker by remember { mutableStateOf(false) }
        var showLorebooksPicker by remember { mutableStateOf(false) }
        
        if (!isKeyboardVisible) {
            // Calculate active modes count from conversation
            val activeModeCount = settings.modes.count { mode ->
                if (conversation.enabledModeIds.isEmpty()) {
                    mode.defaultEnabled
                } else {
                    conversation.enabledModeIds.contains(mode.id)
                }
            }
            
            // Calculate active lorebooks count from assistant
            val activeLorebookCount = assistant.enabledLorebookIds.size
            
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Modes button (left half) - matches BigIconTextButton pattern
                val modesActive = activeModeCount > 0
                CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = bottomLeftShape,
                        color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = if (amoledMode && isDarkMode) 0.dp else 6.dp,
                        onClick = { showModesPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                tint = if (modesActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.modes_picker_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (modesActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.modes.isEmpty()) {
                                        stringResource(R.string.modes_picker_none)
                                    } else {
                                        "$activeModeCount/${settings.modes.size}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Lorebooks button (right half) - matches BigIconTextButton pattern
                val lorebooksActive = activeLorebookCount > 0
                CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = bottomRightShape,
                        color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = if (amoledMode && isDarkMode) 0.dp else 6.dp,
                        onClick = { showLorebooksPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Book,
                                contentDescription = null,
                                tint = if (lorebooksActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.lorebooks_picker_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (lorebooksActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.lorebooks.isEmpty()) {
                                        stringResource(R.string.lorebooks_picker_none)
                                    } else {
                                        "$activeLorebookCount/${settings.lorebooks.size}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Context Refresh button row - shown when enabled
            if (showContextRefresh) {
                val totalMessages = conversation.currentMessages.size
                val lastSummaryIndex = conversation.contextSummaryUpToIndex
                val hasPreviousSummary = !conversation.contextSummary.isNullOrBlank() && lastSummaryIndex >= 0
                val messagesToKeep = 2 // Keep last user+assistant exchange
                val newMessageCount = if (hasPreviousSummary && lastSummaryIndex < totalMessages) {
                    // Messages after last summary, minus the ones we keep
                    (totalMessages - lastSummaryIndex - 1 - messagesToKeep).coerceAtLeast(0)
                } else {
                    // No previous summary - all messages minus kept ones
                    (totalMessages - messagesToKeep).coerceAtLeast(0)
                }
                
                CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = fullBottomShape,
                        color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = if (amoledMode && isDarkMode) 0.dp else 6.dp,
                        onClick = { showContextRefreshDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.context_refresh_button),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (newMessageCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "($newMessageCount)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Modes picker sheet
        if (showModesPicker) {
            ModesPickerSheet(
                settings = settings,
                conversation = conversation,
                onUpdateConversation = onUpdateConversation,
                onDismiss = { showModesPicker = false }
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
        
        // Context Refresh confirmation dialog
        if (showContextRefreshDialog) {
            ContextRefreshDialog(
                conversation = conversation,
                onRefresh = onRefreshContext,
                onDismiss = { showContextRefreshDialog = false }
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
    allowAllTypes: Boolean = false,
    onAddFiles: (List<UIMessagePart.Document>) -> Unit = {}
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = context.getFileNameFromUri(uri) ?: "file"
                    val mime = context.getFileMimeType(uri) ?: "application/octet-stream"

                    // When allowAllTypes is true (Python enabled), accept all files
                    val isAllowed = allowAllTypes ||
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
                onClick = onClick
            )
            .semantics {
                role = Role.Button
            }
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && LocalDarkMode.current) 0.dp else LocalAbsoluteTonalElevation.current) {
            Surface(
                shape = shape,
                color = if (amoledMode && LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (amoledMode && LocalDarkMode.current) 0.dp else 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 16.dp),
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

@Composable
internal fun ModesPickerSheet(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    conversation: Conversation,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    
    // Use local state for immediate UI feedback
    var localEnabledIds by remember(conversation.id) {
        mutableStateOf(
            if (conversation.enabledModeIds.isEmpty()) {
                settings.modes.filter { it.defaultEnabled }.map { it.id }.toSet()
            } else {
                conversation.enabledModeIds
            }
        )
    }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.modes_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (settings.modes.isEmpty()) {
                Text(
                    text = stringResource(R.string.modes_picker_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.modes.forEachIndexed { index, mode ->
                    // Use local state for isEnabled
                    val isEnabled = localEnabledIds.contains(mode.id)
                    
                    // Calculate position for grouped card styling
                    val position = when {
                        settings.modes.size == 1 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY
                        index == 0 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST
                        index == settings.modes.lastIndex -> me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST
                        else -> me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE
                    }
                    
                    // Calculate shape based on position (grouped cards)
                    val shape = when (position) {
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST -> RoundedCornerShape(
                            topStart = cornerRadius, topEnd = cornerRadius,
                            bottomStart = smallCorner, bottomEnd = smallCorner
                        )
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST -> RoundedCornerShape(
                            topStart = smallCorner, topEnd = smallCorner,
                            bottomStart = cornerRadius, bottomEnd = cornerRadius
                        )
                    }
                    
                    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = shape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mode content
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.name.ifEmpty { stringResource(R.string.modes_page_unnamed) },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = mode.prompt.take(50) + if (mode.prompt.length > 50) "..." else "",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Switch
                                HapticSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { newEnabled ->
                                        val newEnabledIds = if (newEnabled) {
                                            localEnabledIds + mode.id
                                        } else {
                                            localEnabledIds - mode.id
                                        }
                                        // Update local state immediately for UI feedback
                                        localEnabledIds = newEnabledIds
                                        // Persist change via callback
                                        onUpdateConversation(conversation.copy(enabledModeIds = newEnabledIds))
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
internal fun LorebooksPickerSheet(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onNavigateToLorebook: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    
    // Use local state for immediate UI feedback
    var localEnabledIds by remember(assistant.id) {
        mutableStateOf(assistant.enabledLorebookIds)
    }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.lorebooks_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (settings.lorebooks.isEmpty()) {
                Text(
                    text = stringResource(R.string.lorebooks_picker_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.lorebooks.forEachIndexed { index, lorebook ->
                    val isEnabled = localEnabledIds.contains(lorebook.id)
                    
                    // Calculate position for connected card styling
                    val position = when {
                        settings.lorebooks.size == 1 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY
                        index == 0 -> me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST
                        index == settings.lorebooks.lastIndex -> me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST
                        else -> me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE
                    }
                    
                    // Calculate shape based on position (grouped cards)
                    val cornerRadius = 28.dp
                    val smallCorner = 8.dp
                    val shape = when (position) {
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY -> RoundedCornerShape(cornerRadius)
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST -> RoundedCornerShape(
                            topStart = cornerRadius, topEnd = cornerRadius,
                            bottomStart = smallCorner, bottomEnd = smallCorner
                        )
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE -> RoundedCornerShape(smallCorner)
                        me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST -> RoundedCornerShape(
                            topStart = smallCorner, topEnd = smallCorner,
                            bottomStart = cornerRadius, bottomEnd = cornerRadius
                        )
                    }
                    
                    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if(amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = shape,
                            onClick = { onNavigateToLorebook(lorebook.id.toString()) }
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Book cover or letter fallback
                            val bookShape = when (position) {
                                me.rerere.rikkahub.ui.components.ui.ItemPosition.ONLY -> RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 6.dp,
                                    bottomStart = 16.dp, bottomEnd = 6.dp
                                )
                                me.rerere.rikkahub.ui.components.ui.ItemPosition.FIRST -> RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 6.dp,
                                    bottomStart = 6.dp, bottomEnd = 6.dp
                                )
                                me.rerere.rikkahub.ui.components.ui.ItemPosition.MIDDLE -> RoundedCornerShape(6.dp)
                                me.rerere.rikkahub.ui.components.ui.ItemPosition.LAST -> RoundedCornerShape(
                                    topStart = 6.dp, topEnd = 6.dp,
                                    bottomStart = 16.dp, bottomEnd = 6.dp
                                )
                            }
                            Surface(
                                shape = bookShape,
                                color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(width = 40.dp, height = 56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (val cover = lorebook.cover) {
                                        is me.rerere.rikkahub.data.model.Avatar.Image -> {
                                            AsyncImage(
                                                model = cover.url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                        is me.rerere.rikkahub.data.model.Avatar.Emoji -> {
                                            Text(
                                                text = cover.content,
                                                fontSize = 20.sp
                                            )
                                        }
                                        else -> {
                                            // Letter fallback
                                            Text(
                                                text = lorebook.name.take(1).uppercase().ifEmpty { "L" },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Lorebook info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = lorebook.name.ifEmpty { stringResource(R.string.lorebooks_page_unnamed) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.lorebooks_page_entries_count, lorebook.entries.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Toggle
                            me.rerere.rikkahub.ui.components.ui.HapticSwitch(
                                checked = isEnabled,
                                onCheckedChange = { newEnabled ->
                                    val newIds = if (newEnabled) {
                                        localEnabledIds + lorebook.id
                                    } else {
                                        localEnabledIds - lorebook.id
                                    }
                                    // Update local state immediately for UI feedback
                                    localEnabledIds = newIds
                                    // Persist change via callback
                                    onUpdateAssistant(assistant.copy(enabledLorebookIds = newIds))
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
