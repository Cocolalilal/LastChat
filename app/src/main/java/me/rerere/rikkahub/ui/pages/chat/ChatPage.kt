package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff

import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.ui.components.chat.NewChatContent

import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.datastore.ChatInputStyle
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.MinimalChatInput
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.AssistantChatTheme
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, searchQuery: String? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handle Error
    LaunchedEffect(Unit) {
        vm.errorFlow.collect { error ->
            toaster.show(error.message ?: "Error", type = ToastType.Error)
        }
    }

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val currentSearchMode by vm.currentSearchMode.collectAsStateWithLifecycle()
    val newChatStats by vm.newChatStats.collectAsStateWithLifecycle()

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

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    val inputState = rememberChatInputState(
        message = remember(files) {
            buildList {
                val localFiles = context.createChatFilesByContents(files)
                val contentTypes = files.mapNotNull { file ->
                    context.getFileMimeType(file)
                }
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
        },
        textContent = remember(text) {
            text?.base64Decode() ?: ""
        }
    )

    val chatListState = rememberLazyListState()
    LaunchedEffect(vm) {
        if(!vm.chatListInitialized) {
            chatListState.scrollToItem(chatListState.layoutInfo.totalItemsCount)
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentSearchMode = currentSearchMode,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    initialSearchQuery = searchQuery,
                    newChatStats = newChatStats
                )
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
                        drawerState = drawerState
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentSearchMode = currentSearchMode,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    initialSearchQuery = searchQuery,
                    newChatStats = newChatStats
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
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: NavHostController,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentSearchMode: me.rerere.rikkahub.data.model.AssistantSearchMode,
    currentChatModel: Model?,
    initialSearchQuery: String? = null,
    newChatStats: me.rerere.rikkahub.ui.components.chat.NewChatStats,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var isTemporaryChat by rememberSaveable { mutableStateOf(false) }

    // State for regeneration confirmation dialog
    var showRegenerateConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRegenerateMessage by rememberSaveable { mutableStateOf<me.rerere.ai.ui.UIMessage?>(null) }
    val currentAssistant = setting.getCurrentAssistant()
    val topMessagePadding = 72.dp
    
    // Auto-scroll to first matching message when opened from search
    LaunchedEffect(initialSearchQuery, conversation.messageNodes) {
        if (!initialSearchQuery.isNullOrBlank() && conversation.messageNodes.isNotEmpty()) {
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



    LaunchedEffect(loadingJob) {
        inputState.loading = loadingJob != null
    }

    AssistantChatTheme(assistant = currentAssistant) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            AssistantBackground(setting = setting)
            Scaffold(
                topBar = {
                    TopBar(
                        settings = setting,
                        conversation = conversation,
                        bigScreen = bigScreen,
                        drawerState = drawerState,
                        previewMode = previewMode,
                        isTemporaryChat = isTemporaryChat,
                        onNewChat = {
                            // Temporary chats are not persisted, so just navigate to new chat
                            navigateToChatPage(navController)
                        },
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onUpdateSettings = { newSettings ->
                            vm.updateSettings(newSettings)
                        },
                        onToggleTemporaryChat = {
                            isTemporaryChat = !isTemporaryChat
                        }
                    )
                },
                // Removed bottomBar to allow floating input
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp)
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    ChatList(
                        innerPadding = PaddingValues(top = topMessagePadding, bottom = 140.dp),
                        conversation = conversation,
                        state = chatListState,
                        loading = loadingJob != null,
                        previewMode = previewMode,
                        settings = setting,
                        recentlyRestoredNodeIds = vm.recentlyRestoredNodeIds.collectAsStateWithLifecycle().value,
                        initialSearchQuery = initialSearchQuery,
                        onJumpToMessage = { index ->
                            previewMode = false
                            scope.launch {
                                // Wait for AnimatedContent transition to complete before scrolling
                                delay(350)
                                chatListState.animateScrollToItem(index)
                            }
                        },
                        onRegenerate = { message ->
                            // Check if this is a simple message (can preserve version history)
                            // or complex message (will wipe old version)
                            if (vm.canPreserveVersionHistory(message)) {
                                // Simple message - regenerate with version history
                                vm.regenerateAtMessage(message, forceWipe = false)
                            } else {
                                // Complex message - show confirmation dialog
                                pendingRegenerateMessage = message
                                showRegenerateConfirmDialog = true
                            }
                        },
                        onEdit = {
                            inputState.editingMessage = it.id
                            inputState.setContents(it.parts)
                        },

                        onDelete = {
                            val backup = conversation
                            val deletedNodeIds = conversation.messageNodes.map { it.id }.toSet()
                            vm.deleteMessage(it)
                            val newNodeIds = vm.conversation.value.messageNodes.map { it.id }.toSet()
                            val removedIds = deletedNodeIds - newNodeIds
                            toaster.show(
                                message = context.getString(R.string.message_deleted),
                                action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                                    label = context.getString(R.string.undo),
                                    onClick = {
                                        vm.updateConversation(backup)
                                        // Track restored node IDs for fade animation
                                        vm.markNodesAsRestored(removedIds)
                                    }
                                )
                            )
                        },
                        onUpdateMessage = { newNode ->
                            // Turn-based version switching using versionTag
                            // When switching versions, find the versionTag of the target message
                            // and synchronize all nodes in the turn to show messages with that tag
                            val oldNode = conversation.messageNodes.find { it.id == newNode.id }
                            val isVersionSwitch = oldNode != null &&
                                oldNode.selectIndex != newNode.selectIndex &&
                                oldNode.role != me.rerere.ai.core.MessageRole.USER

                            if (isVersionSwitch && oldNode != null) {
                                val nodeIndex = conversation.messageNodes.indexOf(oldNode)

                                // Get the versionTag of the newly selected message
                                val targetVersionTag = newNode.messages.getOrNull(newNode.selectIndex)?.versionTag

                                // Find the turn boundaries
                                val turnStartIndex = conversation.messageNodes
                                    .subList(0, nodeIndex + 1)
                                    .indexOfLast { it.role == me.rerere.ai.core.MessageRole.USER } + 1

                                val turnEndIndex = conversation.messageNodes
                                    .subList(nodeIndex, conversation.messageNodes.size)
                                    .indexOfFirst { it.role == me.rerere.ai.core.MessageRole.USER }
                                    .let { if (it == -1) conversation.messageNodes.size else nodeIndex + it }

                                // Update all nodes in the turn
                                val updatedNodes = conversation.messageNodes.mapIndexed { index, node ->
                                    when {
                                        // This is the node we're switching, use the new selectIndex directly
                                        node.id == newNode.id -> newNode

                                        // This node is in the same turn, try to find matching versionTag
                                        index in turnStartIndex until turnEndIndex &&
                                            node.role != me.rerere.ai.core.MessageRole.USER &&
                                            node.messages.size > 1 -> {
                                            if (targetVersionTag != null) {
                                                // Find message with matching versionTag
                                                val matchingIndex = node.messages.indexOfFirst {
                                                    it.versionTag == targetVersionTag
                                                }
                                                if (matchingIndex >= 0) {
                                                    node.copy(selectIndex = matchingIndex)
                                                } else {
                                                    // Fallback: use index-based switching
                                                    val versionDelta = newNode.selectIndex - oldNode.selectIndex
                                                    val newSelectIndex = (node.selectIndex + versionDelta)
                                                        .coerceIn(0, node.messages.lastIndex)
                                                    node.copy(selectIndex = newSelectIndex)
                                                }
                                            } else {
                                                // No versionTag (old conversation), use index-based switching
                                                val versionDelta = newNode.selectIndex - oldNode.selectIndex
                                                val newSelectIndex = (node.selectIndex + versionDelta)
                                                    .coerceIn(0, node.messages.lastIndex)
                                                node.copy(selectIndex = newSelectIndex)
                                            }
                                        }

                                        // Not in this turn, keep unchanged
                                        else -> node
                                    }
                                }

                                vm.updateConversation(conversation.copy(messageNodes = updatedNodes))
                            } else {
                                // Normal update (not version switching)
                                vm.updateConversation(
                                    conversation.copy(
                                        messageNodes = conversation.messageNodes.map { node ->
                                            if (node.id == newNode.id) {
                                                newNode
                                            } else {
                                                node
                                            }
                                        }
                                    )
                                )
                            }
                        },
                        onForkMessage = {
                            scope.launch {
                                vm.forkMessage(it)
                            }
                        },
                    )

                // Temporary chat overlay - shown when no user messages and temporary
                // (ignores preset messages from assistant)
                val hasUserSentMessages = conversation.messageNodes.any { it.role == me.rerere.ai.core.MessageRole.USER }
                val hasAnyPresetMessages = currentAssistant.presetMessages.isNotEmpty()
                val effectiveDisplaySetting = setting.getEffectiveDisplaySetting(currentAssistant)
                
                // Temporary chat overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isTemporaryChat && !hasUserSentMessages && !hasAnyPresetMessages,
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
                
                // New chat customization - shown when NOT temporary, no user messages, and no preset messages
                // Only show if header or content is not NONE
                val headerStyle = effectiveDisplaySetting.newChatHeaderStyle
                val contentStyle = effectiveDisplaySetting.newChatContentStyle
                val showNewChatContent = headerStyle != me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.NONE || contentStyle != me.rerere.rikkahub.data.datastore.NewChatContentStyle.NONE
                
                // Detect keyboard visibility
                val isKeyboardOpen = WindowInsets.isImeVisible
                
                // Hide new chat content when keyboard is open or text/media is in input
                val hasTextInput = inputState.textContent.text.isNotEmpty() || inputState.messageContent.isNotEmpty()
                val shouldShowNewChatContent = !isTemporaryChat && !hasUserSentMessages && !hasAnyPresetMessages && showNewChatContent && !hasTextInput && !isKeyboardOpen
                
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
                        stats = newChatStats,
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
                    val assistantState = me.rerere.rikkahub.ui.hooks.rememberAssistantState(setting) { newSettings ->
                        vm.updateSettings(newSettings)
                    }
                    me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
                        settings = setting,
                        currentAssistant = currentAssistant,
                        onAssistantSelected = { selectedAssistant ->
                            assistantState.setSelectAssistant(selectedAssistant)
                            showHeaderAssistantPicker = false
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
                        title = { Text("Regenerate Message") },
                        text = {
                            Text(
                                "This message contains tool calls or multiple steps. " +
                                "Regenerating will replace the entire response and you won't be able to go back to the previous version. " +
                                "Are you sure you want to continue?"
                            )
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
                                Text("Regenerate")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showRegenerateConfirmDialog = false
                                    pendingRegenerateMessage = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Gradient behind floating toolbar - hidden when showing new chat content
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasUserSentMessages || hasAnyPresetMessages || isTemporaryChat || !showNewChatContent,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                }

                // Conditionally render input based on style setting (using effective setting for per-assistant override)
                when (effectiveDisplaySetting.chatInputStyle) {
                    ChatInputStyle.MINIMAL -> {
                        MinimalChatInput(
                            modifier = Modifier
                                .align(Alignment.BottomCenter),
                            state = inputState,
                            settings = setting,
                            conversation = conversation,
                            mcpManager = vm.mcpManager,
                            chatSuggestions = conversation.chatSuggestions,
                            onClickSuggestion = { suggestion ->
                                if (currentChatModel != null) {
                                    vm.handleMessageSend(
                                        listOf(me.rerere.ai.ui.UIMessagePart.Text(suggestion)),
                                        isTemporaryChat = isTemporaryChat
                                    )
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
                                } else {
                                    toaster.show("Please select a model first", type = ToastType.Error)
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
                                        toaster.show("Please select a model first", type = ToastType.Error)
                                        return@MinimalChatInput
                                    }
                                    vm.handleMessageSend(inputState.getContents(), isTemporaryChat = isTemporaryChat)
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
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
                                        toaster.show("Please select a model first", type = ToastType.Error)
                                        return@MinimalChatInput
                                    }
                                    vm.handleMessageSend(content = inputState.getContents(), answer = false, isTemporaryChat = isTemporaryChat)
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
                                }
                                inputState.clearInput()
                            },
                            onUpdateChatModel = {
                                vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                            },
                            onUpdateAssistant = {
                                vm.updateSettings(
                                    setting.copy(
                                        assistants = setting.assistants.map { assistant ->
                                            if (assistant.id == it.id) {
                                                it
                                            } else {
                                                assistant
                                            }
                                        }
                                    )
                                )
                            },
                            onUpdateSearchService = { index ->
                                vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(index))
                            },
                            onClearContext = {
                                vm.handleMessageTruncate()
                            },
                            onUpdateConversation = { updatedConversation ->
                                vm.updateConversation(updatedConversation)
                                vm.saveConversationAsync()
                            },
                            onNavigateToLorebook = { lorebookId ->
                                navController.navigate(Screen.SettingLorebookDetail(lorebookId))
                            },
                            onRefreshContext = { vm.refreshContext() },
                            onDeleteFile = { vm.deleteFile(it) },
                        )
                    }
                    ChatInputStyle.FLOATING -> {
                        ChatInput(
                            modifier = Modifier
                                .align(Alignment.BottomCenter),
                            state = inputState,
                            settings = setting,
                            conversation = conversation,
                            mcpManager = vm.mcpManager,
                            chatSuggestions = conversation.chatSuggestions,
                            onClickSuggestion = { suggestion ->
                                if (currentChatModel != null) {
                                    vm.handleMessageSend(
                                        listOf(me.rerere.ai.ui.UIMessagePart.Text(suggestion)),
                                        isTemporaryChat = isTemporaryChat
                                    )
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
                                } else {
                                    toaster.show("Please select a model first", type = ToastType.Error)
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
                                        toaster.show("Please select a model first", type = ToastType.Error)
                                        return@ChatInput
                                    }
                                    vm.handleMessageSend(inputState.getContents(), isTemporaryChat = isTemporaryChat)
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
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
                                        toaster.show("Please select a model first", type = ToastType.Error)
                                        return@ChatInput
                                    }
                                    vm.handleMessageSend(content = inputState.getContents(), answer = false, isTemporaryChat = isTemporaryChat)
                                    scope.launch {
                                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                    }
                                }
                                inputState.clearInput()
                            },
                            onUpdateChatModel = {
                                vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                            },
                            onUpdateAssistant = {
                                vm.updateSettings(
                                    setting.copy(
                                        assistants = setting.assistants.map { assistant ->
                                            if (assistant.id == it.id) {
                                                it
                                            } else {
                                                assistant
                                            }
                                        }
                                    )
                                )
                            },
                            onUpdateSearchService = { index ->
                                vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(index))
                            },
                            onClearContext = {
                                vm.handleMessageTruncate()
                            },
                            onUpdateConversation = { updatedConversation ->
                                vm.updateConversation(updatedConversation)
                                vm.saveConversationAsync()
                            },
                            onNavigateToLorebook = { lorebookId ->
                                navController.navigate(Screen.SettingLorebookDetail(lorebookId))
                            },
                            onRefreshContext = { vm.refreshContext() },
                            onDeleteFile = { vm.deleteFile(it) },
                        )
                    }
                }
                }
            }
        }
    }
}

private data class TopBarActionState(
    val isEmpty: Boolean,
    val isTemporaryChat: Boolean,
    val shouldUseCompactTemporaryToggle: Boolean,
    val assistantId: kotlin.uuid.Uuid,
    val conversationId: kotlin.uuid.Uuid
)

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    isTemporaryChat: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateSettings: (Settings) -> Unit,
    onToggleTemporaryChat: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val topContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val topContainerBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.background)
    val buttonShape = RoundedCornerShape(999.dp)
    val topPillSize = 48.dp
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // State for assistant picker - must be at function level for proper recomposition
    var showAssistantPicker by remember { mutableStateOf(false) }
    val currentAssistant = settings.getCurrentAssistant()
    val isEmpty = !conversation.messageNodes.any { it.role == me.rerere.ai.core.MessageRole.USER }
    var animateTopPillIn by remember { mutableStateOf(false) }

    LaunchedEffect(conversation.id) {
        animateTopPillIn = false
        delay(16)
        animateTopPillIn = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!bigScreen) {
                Surface(
                    onClick = {
                        scope.launch { drawerState.open() }
                    },
                    shape = buttonShape,
                    color = topContainerColor,
                    border = topContainerBorder
                ) {
                    Box(
                        modifier = Modifier.size(topPillSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Menu, "Messages")
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

            val topPillScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (animateTopPillIn) 1f else 0.88f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = 300f
                ),
                label = "top_pill_scale"
            )

            Surface(
                shape = buttonShape,
                color = topContainerColor,
                border = topContainerBorder,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = topPillScale
                        scaleY = topPillScale
                    }
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = TopBarActionState(
                        isEmpty = isEmpty,
                        isTemporaryChat = isTemporaryChat,
                        shouldUseCompactTemporaryToggle = run {
                            val hasPresetMessages = currentAssistant.presetMessages.isNotEmpty()
                            val effectiveDisplay = settings.getEffectiveDisplaySetting(currentAssistant)
                            val headerShowsAvatar = effectiveDisplay.newChatShowAvatar && (
                                effectiveDisplay.newChatHeaderStyle == me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.BIG_ICON ||
                                    effectiveDisplay.newChatHeaderStyle == me.rerere.rikkahub.data.datastore.NewChatHeaderStyle.GREETING
                                )
                            !hasPresetMessages && headerShowsAvatar
                        },
                        assistantId = currentAssistant.id,
                        conversationId = conversation.id
                    ),
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.6f,
                                stiffness = 300f
                            )
                        ) + androidx.compose.animation.scaleIn(
                            initialScale = 0.92f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.6f,
                                stiffness = 300f
                            )
                        )) togetherWith (androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.75f,
                                stiffness = 400f
                            )
                        ) + androidx.compose.animation.scaleOut(
                            targetScale = 0.92f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.75f,
                                stiffness = 400f
                            )
                        )) using androidx.compose.animation.SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                androidx.compose.animation.core.spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 300f
                                )
                            }
                        )
                    },
                    label = "topbar_actions"
                ) { actionState ->
                    val isEmptyState = actionState.isEmpty
                    val isTempChat = actionState.isTemporaryChat
                    val hideTopRightAvatar = actionState.shouldUseCompactTemporaryToggle
                    when {
                        isEmptyState && !isTempChat && hideTopRightAvatar -> {
                            IconButton(
                                onClick = { onToggleTemporaryChat() },
                                modifier = Modifier.size(topPillSize)
                            ) {
                                Icon(Icons.Rounded.HistoryToggleOff, "Temporary Chat")
                            }
                        }

                        else -> Row(
                            modifier = Modifier.height(topPillSize),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isEmptyState && !isTempChat -> {
                                    IconButton(
                                        onClick = { onToggleTemporaryChat() },
                                        modifier = Modifier.size(topPillSize)
                                    ) {
                                        Icon(Icons.Rounded.HistoryToggleOff, "Temporary Chat")
                                    }
                                    Box(
                                        modifier = Modifier.size(topPillSize),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                            name = currentAssistant.name.ifBlank { "Character" },
                                            value = currentAssistant.avatar,
                                            modifier = Modifier.size(30.dp),
                                            onClick = { showAssistantPicker = true }
                                        )
                                    }
                                }

                                isEmptyState && isTempChat -> {
                                    IconButton(
                                        onClick = { onToggleTemporaryChat() },
                                        modifier = Modifier.size(topPillSize)
                                    ) {
                                        Icon(Icons.Rounded.History, "Make Normal Chat")
                                    }
                                    Box(
                                        modifier = Modifier.size(topPillSize),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                            name = currentAssistant.name.ifBlank { "Character" },
                                            value = currentAssistant.avatar,
                                            modifier = Modifier.size(30.dp),
                                            onClick = { showAssistantPicker = true }
                                        )
                                    }
                                }

                                else -> {
                                    IconButton(
                                        onClick = { onClickMenu() },
                                        modifier = Modifier.size(topPillSize)
                                    ) {
                                        Icon(if (previewMode) Icons.Rounded.Close else Icons.Rounded.Search, "Chat Options")
                                    }
                                    IconButton(
                                        onClick = { onNewChat() },
                                        modifier = Modifier.size(topPillSize)
                                    ) {
                                        Icon(Icons.Rounded.Add, "New Message")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Assistant picker sheet - outside TopAppBar for proper state handling
    if (showAssistantPicker) {
        val assistantState = me.rerere.rikkahub.ui.hooks.rememberAssistantState(settings, onUpdateSettings)
        me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
            settings = settings,
            currentAssistant = currentAssistant,
            onAssistantSelected = { selectedAssistant ->
                assistantState.setSelectAssistant(selectedAssistant)
                showAssistantPicker = false
            },
            onDismiss = { showAssistantPicker = false }
        )
    }
}
