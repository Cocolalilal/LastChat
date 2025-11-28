package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import me.rerere.ai.core.ReasoningLevel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import com.dokar.sonner.ToastType
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
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

    val keyboardController = LocalSoftwareKeyboardController.current

    fun sendMessage() {
        keyboardController?.hide()
        if (state.loading) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        keyboardController?.hide()
        if (state.loading) onCancelClick() else onLongSendClick()
    }

    var expand by remember { mutableStateOf(ExpandState.Collapsed) }
    fun dismissExpand() {
        expand = ExpandState.Collapsed
    }

    fun expandToggle(type: ExpandState) {
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


        val isPillShape = !state.isEditing() && state.textContent.text.length < 40 && state.textContent.text.lines().size <= 1
        val outerCornerSize by animateDpAsState(
            targetValue = if (isPillShape) 100.dp else 48.dp,
            label = "outer_corner_size"
        )
        val innerCornerSize by animateDpAsState(
            targetValue = if (isPillShape) 100.dp else 40.dp,
            label = "inner_corner_size"
        )

        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp), // Raised toolbar
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Medias
            MediaFileInputRow(state = state, context = context)

            // Floating Input Bar
            Surface(
                shape = RoundedCornerShape(outerCornerSize), // Dynamic Outer Shape
                color = MaterialTheme.colorScheme.surfaceContainerLow, // Material You Surface Color
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp), // Increased padding
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp) // Tighter spacing
                ) {
                    IconButton(
                        onClick = {
                            expandToggle(ExpandState.Files)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .padding(start = 10.dp) // Move button more inside
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (expand == ExpandState.Files) 45f else 0f,
                            label = "rotation"
                        )
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.more_options),
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurface // Icon on Surface
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
                                                targetState = isExpanded || state.loading,
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
                                                    // Send Button
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .combinedClickable(
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
                        shape = RoundedCornerShape(24.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val supportVideo = provider != null && provider is ProviderSetting.Google
        if(supportVideo) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TakePicButton {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ImagePickButton {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        VideoPickButton {
                            state.addVideos(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FilePickButton {
                            state.addFiles(it)
                            onDismiss()
                        }
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TakePicButton {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ImagePickButton {
                            state.addImages(it)
                            onDismiss()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FilePickButton {
                            state.addFiles(it)
                            onDismiss()
                        }
                    }
                }
            }
        }

        if (!WindowInsets.isImeVisible) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth()
            )

            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = if (amoledMode && LocalDarkMode.current) Color.Black else Color.Transparent
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
private fun useCropLauncher(
    onCroppedImageReady: (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null
): Pair<ActivityResultLauncher<Intent>, (Uri) -> Unit> {
    val context = LocalContext.current
    var cropOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cropActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cropOutputUri?.let { croppedUri ->
                onCroppedImageReady(croppedUri)
            }
        }
        // Clean up crop output file
        cropOutputUri?.toFile()?.delete()
        cropOutputUri = null
        onCleanup?.invoke()
    }

    val launchCrop: (Uri) -> Unit = { sourceUri ->
        val outputFile = File(context.appTempFolder, "crop_output_${System.currentTimeMillis()}.jpg")
        cropOutputUri = Uri.fromFile(outputFile)

        val cropIntent = UCrop.of(sourceUri, cropOutputUri!!)
            .withOptions(UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setAllowedGestures(
                    UCropActivity.SCALE,
                    UCropActivity.ROTATE,
                    UCropActivity.NONE
                )
                setCompressionFormat(Bitmap.CompressFormat.PNG)
            })
            .withMaxResultSize(4096, 4096)
            .getIntent(context)

        cropActivityLauncher.launch(cropIntent)
    }

    return Pair(cropActivityLauncher, launchCrop)
}

@Composable
private fun ImagePickButton(onAddImages: (List<Uri>) -> Unit = {}) {
    val context = LocalContext.current
    val settings = LocalSettings.current

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
        }
    )

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
                    launchCrop(selectedUris.first())
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
        icon = {
            Icon(Icons.Rounded.Photo, null)
        }
    ) {
        imagePickerLauncher.launch("image/*")
    }
}

@Composable
fun TakePicButton(onAddImages: (List<Uri>) -> Unit = {}) {
    val cameraPermission = rememberPermissionState(PermissionCamera)

    val context = LocalContext.current
    val settings = LocalSettings.current
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
        },
        onCleanup = {
            // Clean up camera temp file after cropping is done
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )

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
                launchCrop(cameraOutputUri!!)
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
fun VideoPickButton(onAddVideos: (List<Uri>) -> Unit = {}) {
    val context = LocalContext.current
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onAddVideos(context.createChatFilesByContents(selectedUris))
        }
    }

    BigIconTextButton(
        icon = {
            Icon(Icons.Rounded.VideoLibrary, null)
        }
    ) {
        videoPickerLauncher.launch("video/*")
    }
}



@Composable
fun FilePickButton(onAddFiles: (List<UIMessagePart.Document>) -> Unit = {}) {
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
                    toaster.show("不支持的文件类型", type = ToastType.Error)
                }
            }
        }
    BigIconTextButton(
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
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val amoledMode by rememberAmoledDarkMode()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
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
                shape = RoundedCornerShape(24.dp),
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
