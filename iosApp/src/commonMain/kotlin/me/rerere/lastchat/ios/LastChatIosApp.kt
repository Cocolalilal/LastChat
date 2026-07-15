package me.rerere.lastchat.ios

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformAttachmentOpener
import me.rerere.common.platform.PlatformPickedFileKind
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
import me.rerere.common.calendar.CalendarHeatmapDay
import me.rerere.common.calendar.CalendarMonth
import me.rerere.rikkahub.ui.components.chat.BubblePosition
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.ConversationRowSurface
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.TypingIndicator
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAction
import me.rerere.rikkahub.ui.components.chat.LastChatComposerActionButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerDefaultActionContent
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddButton
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAddIcon
import me.rerere.rikkahub.ui.components.chat.LastChatComposerCapsule
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAttachmentRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerAudioIcon
import me.rerere.rikkahub.ui.components.chat.LastChatDocumentAttachmentTile
import me.rerere.rikkahub.ui.components.chat.LastChatComposerImageAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatComposerMediaAttachment
import me.rerere.rikkahub.ui.components.chat.LastChatMessageAttachmentRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerRow
import me.rerere.rikkahub.ui.components.chat.LastChatComposerVideoIcon
import me.rerere.rikkahub.ui.components.ai.LastChatAssistantPickerItem
import me.rerere.rikkahub.ui.components.ai.LastChatAssistantPickerSheet
import me.rerere.rikkahub.ui.components.nav.LastChatBackButton
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerActionIcon
import me.rerere.rikkahub.ui.components.nav.LastChatBarChartIcon
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickAction
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerQuickActionGroup
import me.rerere.rikkahub.ui.components.nav.LastChatMenuButton
import me.rerere.rikkahub.ui.components.nav.LastChatModalDrawerSheet
import me.rerere.rikkahub.ui.components.nav.LastChatDrawerSearch
import me.rerere.rikkahub.ui.components.stats.LastChatStatCard
import me.rerere.rikkahub.ui.components.stats.LastChatActivityHeatmapCard
import me.rerere.rikkahub.ui.components.stats.LastChatStatIcon
import me.rerere.rikkahub.ui.components.stats.LastChatStatIconGlyph
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.buildLastChatTypography
import me.rerere.rikkahub.ui.theme.presetColorScheme
import me.rerere.rikkahub.ui.theme.rememberLastChatFontFamily
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlinx.coroutines.launch

private enum class IosRoute { Chat, Settings, Statistics }

private enum class IosSettingsSection(val title: String) {
    Home("Settings"),
    Appearance("Display"),
    Assistant("Assistant"),
    Provider("Providers"),
    Data("Data"),
}

private data class DisplayMessage(
    val text: String,
    val outgoing: Boolean,
    val position: BubblePosition = BubblePosition.SINGLE,
    val parts: List<UIMessagePart> = emptyList(),
)

@Composable
fun LastChatIosApp(
    controller: IosAppController,
    platformHaptics: PlatformHaptics,
    filePicker: PlatformFilePicker,
    attachmentOpener: PlatformAttachmentOpener,
    darkTheme: Boolean? = null,
) {
    LaunchedEffect(controller) { controller.initialize() }
    val state by controller.state.collectAsState()
    val useDarkTheme = darkTheme ?: when (state.appearance.colorMode) {
        IosColorMode.SYSTEM -> isSystemInDarkTheme()
        IosColorMode.LIGHT -> false
        IosColorMode.DARK -> true
    }
    val colorScheme = presetColorScheme(state.appearance.themeId, useDarkTheme)
    val appFontFamily = rememberLastChatFontFamily()
    MaterialTheme(
        colorScheme = colorScheme.withLastChatAmoledSurface(useDarkTheme),
        typography = buildLastChatTypography(appFontFamily),
        shapes = Shapes,
    ) {
        var route by remember { mutableStateOf(IosRoute.Chat) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "lastchat-route",
        ) { destination ->
            when (destination) {
                IosRoute.Chat -> ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        LastChatModalDrawerSheet(
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) {
                            MenuPage(
                                state = state,
                                onSelectConversation = controller::selectConversation,
                                onRenameConversation = controller::renameConversation,
                                onDeleteConversation = controller::deleteConversation,
                                onSelectAssistant = controller::selectAssistant,
                                darkTheme = useDarkTheme,
                                platformHaptics = platformHaptics,
                                onDismiss = { scope.launch { drawerState.close() } },
                                onSettings = {
                                    scope.launch {
                                        drawerState.close()
                                        route = IosRoute.Settings
                                    }
                                },
                                onStatistics = {
                                    scope.launch {
                                        drawerState.close()
                                        route = IosRoute.Statistics
                                    }
                                },
                            )
                        }
                    },
                ) {
                    ChatPage(
                        state = state,
                        onSend = controller::send,
                        onCancelGeneration = controller::cancelGeneration,
                        onPickFile = { filePicker.pickFile(controller::handlePickedFile) },
                        onRemovePendingAttachment = controller::removePendingAttachment,
                        platformHaptics = platformHaptics,
                        attachmentOpener = attachmentOpener,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onOpenSettings = { route = IosRoute.Settings },
                    )
                }
                IosRoute.Settings -> SettingsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    onSaveProvider = controller::saveProvider,
                    onClearApiKey = controller::clearApiKey,
                    onSaveAppearance = controller::saveAppearance,
                    onSaveAssistant = controller::saveAssistant,
                    onNewAssistant = controller::newAssistant,
                    onSelectAssistant = controller::selectAssistant,
                    onDeleteAssistant = controller::deleteAssistant,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                )
                IosRoute.Statistics -> StatisticsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPage(
    state: IosAppState,
    onSend: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onPickFile: () -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    platformHaptics: PlatformHaptics,
    attachmentOpener: PlatformAttachmentOpener,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val inputState = remember { TextFieldState() }
    val orderedPendingAttachments = remember(state.pendingAttachments) {
        PlatformPickedFileKind.entries.flatMap { kind ->
            state.pendingAttachments.filter { attachment -> attachment.kind == kind }
        }
    }
    val conversationMessages = state.selectedConversation?.messages.orEmpty()
        .filter { message ->
            message.toText().isNotBlank() || message.parts.any {
                it is UIMessagePart.Image || it is UIMessagePart.Video ||
                    it is UIMessagePart.Audio || it is UIMessagePart.Document
            }
        }
    val messages = conversationMessages.mapIndexed { index, message ->
        val outgoing = message.role == MessageRole.USER
        val sameBefore = conversationMessages.getOrNull(index - 1)?.role == message.role
        val sameAfter = conversationMessages.getOrNull(index + 1)?.role == message.role
        val position = when {
            !sameBefore && !sameAfter -> BubblePosition.SINGLE
            !sameBefore -> BubblePosition.FIRST
            !sameAfter -> BubblePosition.LAST
            else -> BubblePosition.MIDDLE
        }
        DisplayMessage(
            text = message.toText(),
            outgoing = outgoing,
            position = position,
            parts = message.parts,
        )
    }
    fun send() {
        val text = inputState.text.toString().trim()
        if (text.isEmpty() && state.pendingAttachments.isEmpty()) return
        onSend(text)
        inputState.setTextAndPlaceCursorAtEnd("")
        platformHaptics.perform(PlatformHapticPattern.Send)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            TopAppBar(
                title = { Text(state.assistant.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    LastChatMenuButton(
                        onClick = onOpenMenu,
                        contentDescription = "Messages",
                    )
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LastChatComposerRow {
                    LastChatComposerAddButton(
                        onClick = {
                            platformHaptics.perform(PlatformHapticPattern.Pop)
                            onPickFile()
                        },
                        onLongClick = {},
                    ) {
                        LastChatComposerAddIcon(contentDescription = "Attach file")
                    }
                    LastChatComposerCapsule {
                        Column(Modifier.fillMaxWidth()) {
                            if (state.pendingAttachments.isNotEmpty()) {
                                LastChatComposerAttachmentRow {
                                    items(
                                        items = orderedPendingAttachments,
                                        key = { attachment -> attachment.storagePath },
                                    ) { attachment ->
                                        val remove = {
                                            platformHaptics.perform(PlatformHapticPattern.Pop)
                                            onRemovePendingAttachment(attachment.storagePath)
                                        }
                                        when (attachment.kind) {
                                            PlatformPickedFileKind.Image -> {
                                                LastChatComposerImageAttachment(
                                                    onClick = {},
                                                    onRemove = remove,
                                                    removeContentDescription = "Remove attachment",
                                                ) {
                                                    AsyncImage(
                                                        model = attachment.localUrl,
                                                        contentDescription = attachment.displayName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                            }
                                            PlatformPickedFileKind.Video -> {
                                                LastChatComposerMediaAttachment(onRemove = remove) {
                                                    LastChatComposerVideoIcon()
                                                }
                                            }
                                            PlatformPickedFileKind.Audio -> {
                                                LastChatComposerMediaAttachment(onRemove = remove) {
                                                    LastChatComposerAudioIcon()
                                                }
                                            }
                                            PlatformPickedFileKind.Document -> {
                                                LastChatDocumentAttachmentTile(
                                                    fileName = attachment.displayName,
                                                    modifier = Modifier.size(60.dp),
                                                    onRemove = remove,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth()) {
                                TextField(
                                    state = inputState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 1.dp),
                                    placeholder = {
                                        Text(
                                            "Message ${state.assistant.name}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        top = 12.dp,
                                        end = 52.dp,
                                        bottom = 12.dp,
                                    ),
                                    colors = TextFieldDefaults.colors().copy(
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                    ),
                                )
                                val action = when {
                                    state.generating -> LastChatComposerAction.Loading
                                    inputState.text.isNotBlank() || state.pendingAttachments.isNotEmpty() ->
                                        LastChatComposerAction.Send
                                    else -> LastChatComposerAction.Picker
                                }
                                LastChatComposerActionButton(
                                    action = action,
                                    onClick = {
                                        when (action) {
                                            LastChatComposerAction.Loading -> {
                                                onCancelGeneration()
                                                platformHaptics.perform(PlatformHapticPattern.Cancel)
                                            }
                                            LastChatComposerAction.Send -> send()
                                            LastChatComposerAction.Picker -> {
                                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                                onOpenSettings()
                                            }
                                            else -> Unit
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .size(36.dp),
                                    content = { currentAction ->
                                        LastChatComposerDefaultActionContent(currentAction) {
                                            Text(
                                                text = state.provider.modelId.firstOrNull()?.uppercase() ?: "M",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (messages.isEmpty()) {
                item {
                    MessageBubble(
                        message = DisplayMessage("How can I help?", outgoing = false),
                        attachmentOpener = attachmentOpener,
                        platformHaptics = platformHaptics,
                    )
                }
            }
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    attachmentOpener = attachmentOpener,
                    platformHaptics = platformHaptics,
                )
            }
            if (state.generating) item {
                GroupedMessageBubble(
                    position = BubblePosition.SINGLE,
                    role = BubbleRole.ACTIVITY,
                ) { TypingIndicator() }
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(
    message: DisplayMessage,
    attachmentOpener: PlatformAttachmentOpener,
    platformHaptics: PlatformHaptics,
) {
    val attachments = message.parts.filter { part ->
        part is UIMessagePart.Image || part is UIMessagePart.Video ||
            part is UIMessagePart.Audio || part is UIMessagePart.Document
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (attachments.isNotEmpty()) {
            LastChatMessageAttachmentRow(alignEnd = message.outgoing) {
                items(
                    items = attachments,
                    key = { part -> part.hashCode() },
                ) { part ->
                    when (part) {
                        is UIMessagePart.Image -> AsyncImage(
                            model = part.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .size(72.dp)
                                .clickable {
                                    platformHaptics.perform(PlatformHapticPattern.Pop)
                                    attachmentOpener.open(part.url)
                                },
                        )
                        is UIMessagePart.Video -> LastChatDocumentAttachmentTile(
                            fileName = "Video",
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
                        )
                        is UIMessagePart.Audio -> LastChatDocumentAttachmentTile(
                            fileName = "Audio",
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
                        )
                        is UIMessagePart.Document -> LastChatDocumentAttachmentTile(
                            fileName = part.fileName,
                            modifier = Modifier.size(72.dp),
                            onClick = {
                                platformHaptics.perform(PlatformHapticPattern.Pop)
                                attachmentOpener.open(part.url)
                            },
                        )
                        else -> Unit
                    }
                }
            }
        }
        if (message.text.isNotBlank()) {
            GroupedMessageBubble(
                position = message.position,
                role = if (message.outgoing) BubbleRole.USER else BubbleRole.ASSISTANT,
                modifier = Modifier.fillMaxWidth(0.86f),
            ) {
                Text(message.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPage(
    state: IosAppState,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectAssistant: (String) -> Unit,
    darkTheme: Boolean,
    platformHaptics: PlatformHaptics,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showAssistantPicker by remember { mutableStateOf(false) }
    val filteredConversations = remember(state.conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { conversation ->
                conversation.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            item {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            item {
                LastChatDrawerSearch(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    placeholder = "Search conversations",
                    hint = "Search titles",
                )
            }
            item {
                AnimatedVisibility(
                    visible = !searchExpanded,
                    enter = fadeIn(animationSpec = spring(stiffness = 300f)) +
                        expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)),
                    exit = fadeOut(animationSpec = spring(stiffness = 500f)) +
                        shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)),
                ) {
                    LastChatDrawerQuickActionGroup {
                        LastChatDrawerQuickAction(
                            label = "Statistics",
                            onClick = onStatistics,
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Tick) },
                            icon = { LastChatBarChartIcon(contentDescription = null) },
                        )
                    }
                }
            }
            if (filteredConversations.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = AppShapes.CardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "No conversations",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            items(filteredConversations, key = { it.id }) { conversation ->
                ConversationListRow(
                    title = conversation.title,
                    selected = conversation.id == state.selectedConversationId,
                    platformHaptics = platformHaptics,
                    onRename = { title -> onRenameConversation(conversation.id, title) },
                    onDelete = { onDeleteConversation(conversation.id) },
                ) {
                    onSelectConversation(conversation.id)
                    onDismiss()
                }
            }
            item {
                val actionButtonSize = 42.dp
                val assistantAvatarSize = 30.dp
                val itemColor = MaterialTheme.colorScheme.surfaceContainerHighest
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = itemColor,
                        shape = AppShapes.ButtonPill,
                        modifier = Modifier.weight(1f).height(actionButtonSize),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        if (state.assistants.size > 1) showAssistantPicker = true
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = state.assistant.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IosAssistantAvatar(
                                name = state.assistant.name,
                                modifier = Modifier
                                    .size(assistantAvatarSize)
                                    .clickable {
                                        platformHaptics.perform(PlatformHapticPattern.Pop)
                                        onSettings()
                                    },
                            )
                        }
                    }
                    LastChatDrawerAction(
                        onClick = onSettings,
                        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                        containerColor = itemColor,
                        size = actionButtonSize,
                    ) { containerSize, iconSize ->
                        LastChatDrawerActionIcon(
                            containerSize = containerSize,
                            iconSize = iconSize,
                            contentDescription = "Settings",
                        )
                    }
                }
            }
    }
    if (showAssistantPicker) {
        val pickerItems = state.assistants.map { assistant ->
            LastChatAssistantPickerItem(
                id = assistant.id,
                name = assistant.name,
                systemPrompt = assistant.systemPrompt,
            )
        }
        LastChatAssistantPickerSheet(
            assistants = pickerItems,
            currentAssistantId = state.assistant.id,
            title = "Assistants",
            noSystemPromptLabel = "No system prompt",
            isDarkMode = darkTheme,
            onAssistantSelected = onSelectAssistant,
            onNavigate = {
                showAssistantPicker = false
                onDismiss()
            },
            onEdit = {
                showAssistantPicker = false
                onSettings()
            },
            onDismiss = { showAssistantPicker = false },
            onPopHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
            onThudHaptic = { platformHaptics.perform(PlatformHapticPattern.Thud) },
            avatar = { item, modifier -> IosAssistantAvatar(item.name, modifier) },
        )
    }
}

@Composable
private fun IosAssistantAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "A",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsPage(
    state: IosAppState,
    darkTheme: Boolean,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    val messages = state.conversations.flatMap { it.messages }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val heatmapData = remember(messages) {
        messages
            .groupingBy { message -> message.createdAt.date }
            .eachCount()
            .map { (date, count) -> CalendarHeatmapDay(date, count) }
            .sortedBy { it.date }
    }
    val promptTokens = messages.sumOf { it.usage?.promptTokens?.toLong() ?: 0L }
    val completionTokens = messages.sumOf { it.usage?.completionTokens?.toLong() ?: 0L }
    val cachedTokens = messages.sumOf { it.usage?.cachedTokens?.toLong() ?: 0L }
    val neutralContainer = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IosBackButton(platformHaptics, onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LastChatActivityHeatmapCard(
                    heatmapData = heatmapData,
                    today = today,
                    activityTitle = "Activity",
                    emptyText = "No activity yet",
                    weekdayLabels = listOf("Mon", "", "Wed", "", "Fri", "", "Sun"),
                    lessLabel = "Less",
                    moreLabel = "More",
                    fallbackMonthLabel = "Activity timeline",
                    monthName = { month, abbreviated -> englishMonthName(month, abbreviated) },
                    messageCountText = { count ->
                        "${formatCompactCount(count)} ${if (count == 1L) "message" else "messages"}"
                    },
                    showEmptyState = messages.isEmpty(),
                    darkTheme = darkTheme,
                    onMonthSelected = {
                        platformHaptics.perform(PlatformHapticPattern.Pop)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LastChatStatCard(
                    title = "Conversations",
                    value = formatCompactCount(state.conversations.size.toLong()),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    icon = { LastChatStatIconGlyph(LastChatStatIcon.Conversations) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LastChatStatCard(
                        title = "Messages",
                        value = formatCompactCount(messages.size.toLong()),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.Messages) },
                    )
                    LastChatStatCard(
                        title = "Input tokens",
                        value = formatCompactCount(promptTokens),
                        containerColor = neutralContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.InputTokens) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LastChatStatCard(
                        title = "Output tokens",
                        value = formatCompactCount(completionTokens),
                        containerColor = neutralContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.OutputTokens) },
                    )
                    LastChatStatCard(
                        title = "Cached tokens",
                        value = formatCompactCount(cachedTokens),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = { LastChatStatIconGlyph(LastChatStatIcon.CachedTokens) },
                    )
                }
            }
        }
    }
}

private fun englishMonthName(month: CalendarMonth, abbreviated: Boolean): String {
    val name = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )[month.monthNumber - 1]
    return if (abbreviated) name.take(3) else name
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "${(value / 100_000_000.0).toLong() / 10.0}B"
    value >= 1_000_000 -> "${(value / 100_000.0).toLong() / 10.0}M"
    value >= 1_000 -> "${(value / 100.0).toLong() / 10.0}K"
    else -> value.toString()
}

@Composable
private fun ConversationListRow(
    title: String,
    selected: Boolean,
    platformHaptics: PlatformHaptics,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditTitle by remember { mutableStateOf(false) }
    var editedTitle by remember(title) { mutableStateOf(title) }
    Box {
        ConversationRowSurface(
            selected = selected,
            onClick = {
                platformHaptics.perform(PlatformHapticPattern.Tick)
                onClick()
            },
            onLongClick = {
                platformHaptics.perform(PlatformHapticPattern.Buildup)
                showMenu = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.ifBlank { "New chat" },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = AppShapes.ButtonRounded,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("Edit title") },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Pop)
                    editedTitle = title
                    showMenu = false
                    showEditTitle = true
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    platformHaptics.perform(PlatformHapticPattern.Error)
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
    if (showEditTitle) {
        AlertDialog(
            onDismissRequest = { showEditTitle = false },
            title = { Text("Edit title") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    singleLine = true,
                    shape = AppShapes.InputField,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(editedTitle)
                    showEditTitle = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitle = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    state: IosAppState,
    darkTheme: Boolean,
    onSaveProvider: (IosProviderType, String, String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveAppearance: (String, IosColorMode) -> Unit,
    onSaveAssistant: (String, String) -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf(IosSettingsSection.Home) }
    var providerType by remember(state.provider.type) { mutableStateOf(state.provider.type) }
    var baseUrl by remember(state.provider.baseUrl) { mutableStateOf(state.provider.baseUrl) }
    var modelId by remember(state.provider.modelId) { mutableStateOf(state.provider.modelId) }
    var apiKey by remember { mutableStateOf("") }
    var themeId by remember(state.appearance.themeId) { mutableStateOf(state.appearance.themeId) }
    var colorMode by remember(state.appearance.colorMode) { mutableStateOf(state.appearance.colorMode) }
    var assistantName by remember(state.assistant.name) { mutableStateOf(state.assistant.name) }
    var systemPrompt by remember(state.assistant.systemPrompt) { mutableStateOf(state.assistant.systemPrompt) }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = { TopAppBar(
            title = { Text(section.title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IosBackButton(platformHaptics) {
                    if (section == IosSettingsSection.Home) onBack()
                    else section = IosSettingsSection.Home
                }
            },
        ) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (section == IosSettingsSection.Home) Modifier
                    else Modifier.padding(16.dp)
                ),
            verticalArrangement = Arrangement.spacedBy(
                if (section == IosSettingsSection.Home) 0.dp else 10.dp
            ),
        ) {
            if (section == IosSettingsSection.Home) {
                item {
                    LastChatSettingsGroup(title = "General settings") {
                        LastChatSettingGroupItem(
                            title = "Display",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { section = IosSettingsSection.Appearance },
                        )
                        LastChatSettingGroupItem(
                            title = "Assistant",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Group, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { section = IosSettingsSection.Assistant },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "Models & services") {
                        LastChatSettingGroupItem(
                            title = "Providers",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Cloud, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { section = IosSettingsSection.Provider },
                        )
                    }
                }
                item {
                    LastChatSettingsGroup(title = "Data") {
                        LastChatSettingGroupItem(
                            title = "Chat storage",
                            subtitle = "Conversations are stored in the iOS app container",
                            darkTheme = darkTheme,
                            icon = { Icon(Icons.Rounded.Storage, null, Modifier.size(20.dp)) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                            onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
                            onClick = { section = IosSettingsSection.Data },
                        )
                    }
                }
            }
            if (section == IosSettingsSection.Assistant) {
            item { Text("Assistant", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.assistants.forEach { assistant ->
                        if (assistant.id == state.selectedAssistantId) {
                            Button(onClick = {}) { Text(assistant.name) }
                        } else {
                            TextButton(onClick = { onSelectAssistant(assistant.id) }) {
                                Text(assistant.name)
                            }
                        }
                    }
                    TextButton(onClick = onNewAssistant) { Text("New assistant") }
                }
            }
            item { OutlinedTextField(
                value = assistantName,
                onValueChange = { assistantName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            ) }
            item { OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("System prompt") },
                minLines = 3,
                maxLines = 8,
            ) }
            item { Button(
                onClick = { onSaveAssistant(assistantName, systemPrompt) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save assistant") } }
            if (state.assistants.size > 1) item {
                TextButton(onClick = { onDeleteAssistant(state.assistant.id) }) {
                    Text("Delete assistant")
                }
            }
            }
            if (section == IosSettingsSection.Provider) {
            item { Text("Provider", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IosProviderType.entries.forEach { type ->
                        if (type == providerType) {
                            Button(onClick = {}) { Text(type.displayName()) }
                        } else {
                            TextButton(onClick = {
                                providerType = type
                                val saved = state.providerConfigurations.firstOrNull { it.type == type }
                                baseUrl = saved?.baseUrl ?: type.defaultBaseUrl()
                                modelId = saved?.modelId ?: type.defaultModelId()
                                apiKey = ""
                            }) { Text(type.displayName()) }
                        }
                    }
                }
            }
            item { OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true) }
            item { OutlinedTextField(modelId, { modelId = it }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, singleLine = true) }
            item { OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.hasApiKey && providerType == state.provider.type) "API key (saved in Keychain)" else "API key") },
                placeholder = {
                    if (state.hasApiKey && providerType == state.provider.type) {
                        Text("Leave blank to keep current key")
                    }
                },
                singleLine = true,
            ) }
            item { Button(
                onClick = { onSaveProvider(providerType, baseUrl, modelId, apiKey); apiKey = "" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save provider") } }
            if (state.hasApiKey && providerType == state.provider.type) item {
                TextButton(onClick = onClearApiKey) { Text("Remove saved API key") }
            }
            }
            if (section == IosSettingsSection.Appearance) {
            item { Text("Appearance", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "seafoam_mint" to "Seafoam",
                        "ocean" to "Ocean",
                        "sakura" to "Sakura",
                        "spring" to "Spring",
                        "autumn" to "Autumn",
                        "black" to "Black",
                    ).forEach { (id, label) ->
                        if (themeId == id) Button(onClick = {}) { Text(label) }
                        else TextButton(onClick = {
                            themeId = id
                            onSaveAppearance(themeId, colorMode)
                        }) { Text(label) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IosColorMode.entries.forEach { mode ->
                        if (colorMode == mode) Button(onClick = {}) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        else TextButton(onClick = {
                            colorMode = mode
                            onSaveAppearance(themeId, colorMode)
                        }) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            }
            if (section == IosSettingsSection.Data) {
                item { MenuCard("Data", "Conversations are stored in the iOS app container") {} }
            }
        }
    }
}

@Composable
private fun IosBackButton(
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
) {
    LastChatBackButton(
        onClick = onBack,
        contentDescription = "Back",
        onHaptic = { platformHaptics.perform(PlatformHapticPattern.Pop) },
    )
}

private fun IosProviderType.displayName(): String = when (this) {
    IosProviderType.OPENAI -> "OpenAI"
    IosProviderType.GOOGLE -> "Google"
    IosProviderType.CLAUDE -> "Claude"
}

private fun IosProviderType.defaultBaseUrl(): String = when (this) {
    IosProviderType.OPENAI -> "https://api.openai.com/v1"
    IosProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
    IosProviderType.CLAUDE -> "https://api.anthropic.com/v1"
}

private fun IosProviderType.defaultModelId(): String = when (this) {
    IosProviderType.OPENAI -> "gpt-4.1-mini"
    IosProviderType.GOOGLE -> "gemini-2.5-flash"
    IosProviderType.CLAUDE -> "claude-sonnet-4-5"
}
