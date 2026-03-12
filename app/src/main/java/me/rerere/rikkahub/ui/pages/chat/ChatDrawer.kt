package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.ui.theme.LocalDarkMode

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.ai.AssistantPicker
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.AppPickerRow
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.UpdateCard
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.rememberIsPlayStoreVersion
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.groupedItemShape
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import kotlin.uuid.Uuid
import coil3.compose.AsyncImage

@Composable
fun ChatDrawerContent(
    navController: NavHostController,
    vm: ChatVM,
    settings: Settings,
    current: Conversation,
    drawerState: androidx.compose.material3.DrawerState? = null,  // Optional for animated close
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = me.rerere.rikkahub.ui.context.LocalToaster.current
    val isPlayStore = rememberIsPlayStoreVersion()
    val repo = koinInject<ConversationRepository>()

    // Search expansion state - hoisted here so drawer width can animate
    var isSearchExpanded by remember { mutableStateOf(false) }
    val drawerWidth by animateDpAsState(
        targetValue = if (isSearchExpanded) 600.dp else 320.dp,
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

    // æ˜µç§°ç¼–è¾‘çŠ¶æ€
    val nicknameEditState = useEditState<String> { newNickname ->
        vm.updateSettings(
            settings.copy(
                displaySetting = settings.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        )
    }

    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = drawerWidth),
        drawerShape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        drawerContainerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.displaySetting.showUpdates && !isPlayStore) {
                UpdateCard(vm)
            }

            // ç”¨æˆ·å¤´åƒå’Œæ˜µç§°è‡ªå®šä¹‰åŒºåŸŸ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                        assistant = settings.getCurrentAssistant()
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
                    }
                },
                onPin = {
                    vm.updatePinnedStatus(it)
                },
                showUnconsolidatedDot = settings.getCurrentAssistant().enableMemory && settings.getCurrentAssistant().enableMemoryConsolidation,
                showConsolidateOption = settings.getCurrentAssistant().enableMemory && settings.getCurrentAssistant().enableMemoryConsolidation,
                // Imagine + Stats buttons (visibility handled by ConversationList)
                quickActions = {
                    // Quick Action Buttons (settings-style grouping)
                    val itemColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val haptics = rememberPremiumHaptics()
                        AppPickerRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = "Imagine",
                            subtitle = "Generate images and explore ideas visually",
                            position = ItemPosition.FIRST,
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                navController.navigate(Screen.ImageGen)
                            }
                        )
                        AppPickerRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = "Stats",
                            subtitle = "Open usage and app activity details",
                            position = ItemPosition.LAST,
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                navController.navigate(Screen.Menu)
                            }
                        )
                    }
                }
            )

            // Character picker state (manages the bottom sheet)
            val assistantState = me.rerere.rikkahub.ui.hooks.rememberAssistantState(settings) { newSettings ->
                vm.updateSettings(newSettings)
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
                val itemColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor()
                val haptics = rememberPremiumHaptics()
                val assistantName = assistantState.currentAssistant.name.ifEmpty { defaultAssistantName }

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
                                    val currentAssistantId = settings.assistantId
                                    navController.navigate(Screen.AssistantDetail(id = currentAssistantId.toString()))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            DrawerAvatarVisual(
                                name = assistantName,
                                avatar = assistantState.currentAssistant.avatar,
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
                    },
                    containerColor = itemColor,
                    size = actionButtonSize
                )
            }

            // Character picker sheet
            if (showCharacterPicker) {
                me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
                    settings = settings,
                    currentAssistant = assistantState.currentAssistant,
                    onAssistantSelected = { assistant ->
                        assistantState.setSelectAssistant(assistant)
                    },
                    onNavigate = {
                        showCharacterPicker = false
                        scope.launch {
                            drawerState?.close()

                            val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                                Uuid.random()
                            } else {
                                repo.getConversationsOfAssistant(settings.assistantId)
                                    .first()
                                    .firstOrNull()
                                    ?.id ?: Uuid.random()
                            }
                            navigateToChatPage(navController = navController, chatId = id)
                        }
                    },
                    onDismiss = {
                        showCharacterPicker = false
                    }
                )
            }
        }
    }

    // æ˜µç§°ç¼–è¾‘å¯¹è¯æ¡†
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

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
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
        shape = CircleShape,
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(rememberAvatarShape(false)),
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

