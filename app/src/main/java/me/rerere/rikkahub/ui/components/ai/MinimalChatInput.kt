package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.crop.CropImageScreen
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.deleteChatFiles
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
    onNavigateToLorebook: (String) -> Unit = {},
    onRefreshContext: suspend () -> ChatService.ContextRefreshResult = { ChatService.ContextRefreshResult(false, errorMessage = "Not configured") },
    onDeleteFile: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val keyboardController = LocalSoftwareKeyboardController.current
    val localSettings = LocalSettings.current
    val scope = rememberCoroutineScope()

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
    
    var showPicker by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
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
    
    fun sendMessage() {
        keyboardController?.hide()
        haptics.perform(HapticPattern.Send)
        if (state.loading) onCancelClick() else onSendClick()
    }
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Media preview row
            if (state.messageContent.isNotEmpty()) {
                MediaFileInputRow(
                    state = state,
                    onDelete = onDeleteFile
                )
            }
            
            // Suggestions row
            androidx.compose.animation.AnimatedVisibility(
                visible = chatSuggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ChatSuggestionsRow(
                    suggestions = chatSuggestions,
                    onClickSuggestion = onClickSuggestion
                )
            }
            
            // Content receiver for clipboard image paste (must be outside Surface lambda)
            val receiveContentListener = remember {
                ReceiveContentListener { transferableContent ->
                    when {
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
                // Plus button - 48dp pill button
                Surface(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showPicker = true
                        keyboardController?.hide()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
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
                // Text field capsule with embedded action button
                // Corner radius = 24dp (user confirmed this was correct)
                Surface(
                    shape = RoundedCornerShape(24.dp),  // Fixed radius - correct per user
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)  // Matches plus button, allows 4dp padding all around
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Editing indicator - shown when editing a message
                        if (state.isEditing()) {
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
                            TextField(
                                state = state.textContent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 1.dp)  // Override internal min height (56dp)
                                    .focusRequester(state.focusRequester)
                                    .contentReceiver(receiveContentListener)
                                    .onFocusChanged { isFocused = it.isFocused },
                                placeholder = {
                                    Text(
                                        text = "Ask ${assistant.name}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),  // MultiLine for proper Enter key
                                colors = TextFieldDefaults.colors().copy(
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp,
                                    end = 48.dp,  // Space for 40dp button + 4dp padding
                                    top = 12.dp,  // (48dp height - 24dp text) / 2 = 12dp
                                    bottom = 12.dp
                                )
                            )
                            
                            // Action button - bottom-right, extra bottom padding for visual alignment
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 6.dp)
                            ) {
                                val currentAction = when {
                                    state.loading -> "loading"
                                    !state.isEmpty() -> "send"
                                    else -> "picker"
                                }
                                
                                val containerColor by animateColorAsState(
                                    targetValue = when (currentAction) {
                                        "loading" -> MaterialTheme.colorScheme.errorContainer
                                        "send" -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                    label = "ActionContainerColor"
                                )
                                
                                Surface(
                                    onClick = { 
                                        if (currentAction == "send" || currentAction == "loading") sendMessage()
                                        else showPicker = true
                                    },
                                    shape = CircleShape,
                                    color = containerColor,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
        }  // Column ends
    }  // Box ends
    
    // Bottom sheet picker with custom MinimalPickerContent
    // Optical roundness: sheet corners (40dp) = button corners (24dp) + padding (16dp)
    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = pickerSheetColor,
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
    // Sheet background uses surfaceContainerLow always
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    
    // Camera state
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    
    // Crop state
    var showCropScreen by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Uri?>(null) }
    
    // Sub-picker states
    var showModelPicker by remember { mutableStateOf(false) }
    var showReasoningPicker by remember { mutableStateOf(false) }
    var showModesPicker by remember { mutableStateOf(false) }
    var showLorebooksPicker by remember { mutableStateOf(false) }
    var showContextRefreshDialog by remember { mutableStateOf(false) }
    var showSearchPicker by remember { mutableStateOf(false) }
    
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
    
    // Crop screen dialog
    if (showCropScreen && imageToCrop != null) {
        CropImageScreen(
            sourceUri = imageToCrop!!,
            onCropComplete = { croppedUri ->
                state.addImages(context.createChatFilesByContents(listOf(croppedUri)))
                showCropScreen = false
                imageToCrop = null
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
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
        if (captureSuccessful && cameraOutputUri != null) {
            if (localSettings.displaySetting.skipCropImage) {
                state.addImages(context.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            } else {
                imageToCrop = cameraOutputUri
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
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            if (localSettings.displaySetting.skipCropImage) {
                state.addImages(context.createChatFilesByContents(selectedUris))
                onDismiss()
            } else {
                if (selectedUris.size == 1) {
                    imageToCrop = selectedUris.first()
                    showCropScreen = true
                } else {
                    state.addImages(context.createChatFilesByContents(selectedUris))
                    onDismiss()
                }
            }
        }
    }
    
    // File picker launcher - categorizes files by type
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            val isPythonEnabled = assistant.localTools.any { it is LocalToolOption.PythonEngine }
            val images = mutableListOf<android.net.Uri>()
            val documents = mutableListOf<me.rerere.ai.ui.UIMessagePart.Document>()
            
            selectedUris.forEach { uri ->
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val fileName = context.getFileNameFromUri(uri) ?: "file"
                
                // Allow if Python is enabled OR it's a generally supported type (Images/Text/PDF)
                // Strict check for "Native" support usually implies Images, but app allows Text/PDF too.
                val isSupported = mimeType.startsWith("image/") || 
                                  mimeType.startsWith("text/") || 
                                  mimeType == "application/pdf"
                                  
                if (!isPythonEnabled && !isSupported) {
                     toaster.show("Unsupported file type: $fileName (Enable Python tool to use this file)")
                     return@forEach
                }

                when {
                    mimeType.startsWith("image/") -> {
                        images.add(uri)
                    }
                    else -> {
                        // Non-image files become Documents
                        val localUri = context.createChatFilesByContents(listOf(uri))[0]
                        documents.add(
                            me.rerere.ai.ui.UIMessagePart.Document(
                                url = localUri.toString(),
                                fileName = fileName,
                                mime = mimeType.ifEmpty { "application/octet-stream" }
                            )
                        )
                    }
                }
            }
            
            if (images.isNotEmpty()) {
                state.addImages(context.createChatFilesByContents(images))
            }
            if (documents.isNotEmpty()) {
                state.addFiles(documents)
            }
            onDismiss()
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
                    imagePickerLauncher.launch("image/*")
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
        val currentModel = settings.getCurrentChatModel()
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
        
        // Modes - use enabledModeIds from conversation or default modes
        val activeModesCount = if (conversation.enabledModeIds.isNotEmpty()) {
            conversation.enabledModeIds.size
        } else {
            settings.modes.count { it.defaultEnabled }
        }
        val modesActive = activeModesCount > 0
        MinimalPickerItem(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FlashOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (modesActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            title = stringResource(R.string.minimal_input_modes),
            subtitle = if (activeModesCount > 0) "$activeModesCount active" else stringResource(R.string.minimal_input_modes_desc),
            onClick = { 
                showModesPicker = true
            }
        )
        
        // Lorebooks - show active count and blue icon when enabled
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
        
        // Summarize button - only show when context refresh is enabled and more than 2 messages
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
            onDismissRequest = { showModelPicker = false },
            sheetState = modelPickerSheetState,
            sheetGesturesEnabled = false,
            containerColor = sheetContainerColor,
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
            onDismissRequest = { showSearchPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = sheetContainerColor
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
