package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.core.net.toUri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatPersistenceMode
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.components.ui.UIAvatar

import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberIsPlayStoreVersion
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toDp
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.utils.Version
import me.rerere.rikkahub.utils.onSuccess
import coil3.compose.AsyncImage

@Composable
fun ChatDrawerContent(
    navController: NavHostController,
    vm: ChatVM,
    settings: Settings,
    current: Conversation,
    inputState: ChatInputState,
    activePersistenceMode: ChatPersistenceMode,
    drawerState: androidx.compose.material3.DrawerState? = null,  // Optional for animated close
    presentation: ChatDrawerPresentation = ChatDrawerPresentation.Modal,
    collapsedWidth: Dp = 320.dp,
    expandedWidth: Dp = 600.dp,
    onCollapseRequest: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = me.rerere.rikkahub.ui.context.LocalToaster.current
    val isPlayStore = rememberIsPlayStoreVersion()
    val currentAssistant = settings.getAssistantById(current.assistantId) ?: settings.getCurrentAssistant()

    // Search expansion state - hoisted here so drawer width can animate
    var isSearchExpanded by remember { mutableStateOf(false) }
    val drawerWidth by animateDpAsState(
        targetValue = if (isSearchExpanded) expandedWidth else collapsedWidth,
        animationSpec = if (isSearchExpanded) {
            spring(dampingRatio = 0.8f, stiffness = 400f)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        },
        label = "drawer_width"
    )

    val conversations = vm.conversations.collectAsLazyPagingItems()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    val recentlyRestoredIds by vm.recentlyRestoredIds.collectAsStateWithLifecycle()

    fun dismissDrawerAfterSelection() {
        when (presentation) {
            ChatDrawerPresentation.Modal -> drawerState?.let { state ->
                scope.launch { state.close() }
            }

            ChatDrawerPresentation.PermanentPane -> onCollapseRequest?.invoke()
        }
    }

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        vm.updateSettings(
            settings.copy(
                displaySetting = settings.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        )
    }

    val drawerContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 用户头像和昵称自定义区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val headerActionColor = MaterialTheme.colorScheme.surfaceContainerHighest
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    onUpdate = { newAvatar ->
                        vm.updateSettings(
                            settings.copy(
                                displaySetting = settings.displaySetting.copy(
                                    userAvatar = newAvatar
                                )
                            )
                        )
                    },
                    modifier = Modifier.size(50.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                nicknameEditState.open(settings.displaySetting.userNickname)
                            }
                    )
                    }
                    Greeting(
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        assistant = currentAssistant
                    )
                }

                if (onCollapseRequest != null) {
                    DrawerAction(
                        icon = {
                            Icon(Icons.Rounded.ChevronLeft, null)
                        },
                        label = { Text(stringResource(R.string.activity_timeline_collapse)) },
                        onClick = onCollapseRequest,
                        containerColor = headerActionColor,
                        size = 42.dp
                    )
                }
            }

            ConversationList(
                current = current,
                conversations = conversations,
                conversationJobs = conversationJobs.keys,
                recentlyRestoredIds = recentlyRestoredIds,
                searchQuery = searchQuery,
                onSearchQueryChange = { vm.updateSearchQuery(it) },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = {
                    // Only pass search query if the match was from message content (not title)
                    // This scrolls to the matching message; for title matches, just open normally
                    val titleMatches = searchQuery.isNotBlank() && it.title.contains(searchQuery, ignoreCase = true)
                    navigateToChatPage(navController, it.id, searchQuery = if (titleMatches) null else searchQuery.ifBlank { null })
                    dismissDrawerAfterSelection()
                },
                onRegenerateTitle = {
                    vm.generateTitle(it, true)
                },
                onEditTitle = { conversation, title ->
                    vm.updateConversationTitle(conversation, title)
                },
                onConsolidate = {
                    vm.consolidateConversation(it)
                },
                onDelete = {
                    vm.deleteConversation(it)
                    toaster.show(
                        message = context.getString(R.string.conversation_deleted),
                        action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                            label = context.getString(R.string.undo),
                            onClick = {
                                vm.undoDeleteConversation(it.id)
                            }
                        )
                    )
                    if (it.id == current.id) {
                        navigateToChatPage(navController)
                        dismissDrawerAfterSelection()
                    }
                },
                onPin = {
                    vm.updatePinnedStatus(it)
                },
                showUnconsolidatedDot = currentAssistant.enableMemory && currentAssistant.enableMemoryConsolidation,
                showConsolidateOption = currentAssistant.enableMemory && currentAssistant.enableMemoryConsolidation,
                // Imagine + Stats buttons (visibility handled by ConversationList)
                quickActions = {
                    // Quick Action Buttons (settings-style grouping)
                    val itemColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val haptics = rememberPremiumHaptics()
                        // Imagine button
                        Surface(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                navController.navigate(Screen.ImageGen)
                                dismissDrawerAfterSelection()
                            },
                            color = itemColor,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.chat_drawer_imagine),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Stats button
                        Surface(
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                navController.navigate(Screen.Menu)
                                dismissDrawerAfterSelection()
                            },
                            color = itemColor,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.menu_statistics_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                    }
                }
            }
                }
            )

            fun navigateToAssistantConversation(assistant: me.rerere.rikkahub.data.model.Assistant) {
                scope.launch {
                    val draft = inputState.toDraft()
                    val newConversation = vm.createConversationForAssistant(assistant.id)
                    ChatSessionDraftStore.moveDraft(
                        fromConversationId = current.id,
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
                    dismissDrawerAfterSelection()
                }
            }
            val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
            var showCharacterPicker by remember { mutableStateOf(false) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                val actionButtonSize = 42.dp
                val assistantAvatarSize = 30.dp
                val itemColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val haptics = rememberPremiumHaptics()
                val assistantName = currentAssistant.name.ifEmpty { defaultAssistantName }

                Surface(
                    color = itemColor,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
                    modifier = Modifier
                        .weight(1f)
                        .height(actionButtonSize)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.perform(HapticPattern.Pop)
                                    if (settings.assistants.size > 1) {
                                        showCharacterPicker = true
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = assistantName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(assistantAvatarSize)
                                .clip(rememberAvatarShape(false))
                                .clickable {
                                    haptics.perform(HapticPattern.Pop)
                                    navController.navigate(Screen.AssistantDetail(id = currentAssistant.id.toString()))
                                    dismissDrawerAfterSelection()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            DrawerAvatarVisual(
                                name = assistantName,
                                avatar = currentAssistant.avatar,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Settings icon
                DrawerAction(
                    icon = {
                        Icon(Icons.Rounded.Settings, null)
                    },
                    label = { Text(stringResource(R.string.settings)) },
                    onClick = {
                        navController.navigate(Screen.Setting)
                        dismissDrawerAfterSelection()
                    },
                    containerColor = itemColor,
                    size = actionButtonSize
                )
            }

            // Character picker sheet
            if (showCharacterPicker) {
                me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
                    settings = settings,
                    currentAssistant = currentAssistant,
                    onAssistantSelected = { assistant ->
                        vm.setSelectedAssistant(assistant.id)
                    },
                    onNavigate = { assistant ->
                        showCharacterPicker = false
                        navigateToAssistantConversation(assistant)
                    },
                    onDismiss = {
                        showCharacterPicker = false
                    }
                )
            }
        }
    }

    // 昵称编辑对话框
    when (presentation) {
        ChatDrawerPresentation.Modal -> {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = drawerWidth),
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                drawerContent()
            }
        }

        ChatDrawerPresentation.PermanentPane -> {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                drawerContent()
            }
        }
    }

    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

enum class ChatDrawerPresentation {
    Modal,
    PermanentPane
}

@Composable
fun CollapsedChatSideRail(
    current: Conversation,
    settings: Settings,
    onExpand: () -> Unit,
    onOpenImageGen: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    vm: ChatVM,
    modifier: Modifier = Modifier,
) {
    val currentAssistant = settings.getAssistantById(current.assistantId) ?: settings.getCurrentAssistant()
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    val assistantName = currentAssistant.name.ifEmpty { defaultAssistantName }
    val isPlayStore = rememberIsPlayStoreVersion()

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerAction(
                icon = { Icon(Icons.Rounded.ChevronRight, null) },
                label = { Text(stringResource(R.string.activity_timeline_expand)) },
                onClick = onExpand,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                size = 48.dp
            )
            Column(
                modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DrawerAction(
                    icon = { Icon(Icons.Rounded.Image, null) },
                    label = { Text(stringResource(R.string.chat_drawer_imagine)) },
                    onClick = onOpenImageGen,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp),
                    size = 48.dp
                )
                DrawerAction(
                    icon = { Icon(Icons.Rounded.BarChart, null) },
                    label = { Text(stringResource(R.string.menu_statistics_title)) },
                    onClick = onOpenStatistics,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp),
                    size = 48.dp
                )
            }

            Spacer(Modifier.weight(1f))

            Surface(
                onClick = onOpenAssistant,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DrawerAvatarVisual(
                        name = assistantName,
                        avatar = currentAssistant.avatar,
                        modifier = Modifier.fillMaxSize(),
                        forceCircle = true
                    )
                }
            }

            DrawerAction(
                icon = { Icon(Icons.Rounded.Settings, null) },
                label = { Text(stringResource(R.string.settings)) },
                onClick = onOpenSettings,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                size = 48.dp
            )
        }
    }
}

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = CircleShape,
    size: Dp = 42.dp,
) {
    val containerSize = size
    val iconSize = 22.dp
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "drawer_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "drawer_alpha"
    )
    val haptics = rememberPremiumHaptics()
    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
        interactionSource = interactionSource,
        color = containerColor,
        shape = shape,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Tooltip(
            tooltip = {
               label()
            }
        ) {
            Box(
                modifier = Modifier
                    .size(containerSize),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(iconSize)) {
                    icon()
                }
            }
        }
    }
}

@Composable
private fun DrawerAvatarVisual(
    name: String,
    avatar: Avatar,
    modifier: Modifier = Modifier,
    forceCircle: Boolean = false
) {
    Box(
        modifier = modifier.clip(if (forceCircle) CircleShape else rememberAvatarShape(false)),
        contentAlignment = Alignment.Center
    ) {
        when (avatar) {
            is Avatar.Image -> {
                AsyncImage(
                    model = avatar.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            is Avatar.Resource -> {
                AsyncImage(
                    model = avatar.id,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            is Avatar.Emoji -> {
                Text(
                    text = avatar.content,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            is Avatar.Dummy -> {
                Text(
                    text = name
                        .ifBlank { "A" }
                        .take(1)
                        .uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}
