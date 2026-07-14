package me.rerere.lastchat.ios

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
import me.rerere.rikkahub.ui.components.chat.BubblePosition
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.ConversationRowSurface
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.TypingIndicator
import me.rerere.rikkahub.ui.components.stats.LastChatStatCard
import me.rerere.rikkahub.ui.components.stats.LastChatStatIcon
import me.rerere.rikkahub.ui.components.stats.LastChatStatIconGlyph
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.buildLastChatTypography
import me.rerere.rikkahub.ui.theme.presetColorScheme
import me.rerere.rikkahub.ui.theme.rememberLastChatFontFamily
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import coil3.compose.AsyncImage

private enum class IosRoute { Chat, Menu, Settings, Statistics }

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
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "lastchat-route",
        ) { destination ->
            when (destination) {
                IosRoute.Chat -> ChatPage(
                    state = state,
                    onSend = controller::send,
                    onCancelGeneration = controller::cancelGeneration,
                    onPickFile = { filePicker.pickFile(controller::handlePickedFile) },
                    onRemovePendingAttachment = controller::removePendingAttachment,
                    platformHaptics = platformHaptics,
                    onOpenMenu = { route = IosRoute.Menu },
                )
                IosRoute.Menu -> MenuPage(
                    state = state,
                    onNewChat = controller::newConversation,
                    onSelectConversation = controller::selectConversation,
                    onRenameConversation = controller::renameConversation,
                    onDeleteConversation = controller::deleteConversation,
                    onNewAssistant = controller::newAssistant,
                    onSelectAssistant = controller::selectAssistant,
                    platformHaptics = platformHaptics,
                    onBack = { route = IosRoute.Chat },
                    onSettings = { route = IosRoute.Settings },
                    onStatistics = { route = IosRoute.Statistics },
                )
                IosRoute.Settings -> SettingsPage(
                    state = state,
                    onSaveProvider = controller::saveProvider,
                    onClearApiKey = controller::clearApiKey,
                    onSaveAppearance = controller::saveAppearance,
                    onSaveAssistant = controller::saveAssistant,
                    onNewAssistant = controller::newAssistant,
                    onSelectAssistant = controller::selectAssistant,
                    onDeleteAssistant = controller::deleteAssistant,
                    onBack = { route = IosRoute.Menu },
                )
                IosRoute.Statistics -> StatisticsPage(
                    state = state,
                    darkTheme = useDarkTheme,
                    onBack = { route = IosRoute.Menu },
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
    onOpenMenu: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
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
        val text = input.trim()
        if (text.isEmpty()) return
        onSend(text)
        input = ""
        platformHaptics.perform(PlatformHapticPattern.Pop)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = {
            TopAppBar(
                title = { Text(state.assistant.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onOpenMenu) { Text("Menu") } },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.pendingAttachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.pendingAttachments.forEach { attachment ->
                            TextButton(onClick = { onRemovePendingAttachment(attachment.storagePath) }) {
                                Text("${attachment.displayName}  ×", maxLines = 1)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onPickFile, enabled = !state.generating) { Text("+") }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        shape = AppShapes.InputField,
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        maxLines = 5,
                    )
                    Button(
                        onClick = {
                            if (state.generating) {
                                onCancelGeneration()
                                platformHaptics.perform(PlatformHapticPattern.Cancel)
                            } else {
                                send()
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        shape = AppShapes.IconButton,
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(if (state.generating) "■" else "↑") }
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
                item { MessageBubble(DisplayMessage("How can I help?", outgoing = false)) }
            }
            items(messages) { MessageBubble(it) }
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
private fun MessageBubble(message: DisplayMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        GroupedMessageBubble(
            position = message.position,
            role = if (message.outgoing) BubbleRole.USER else BubbleRole.ASSISTANT,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.text.isNotBlank()) Text(message.text)
                message.parts.forEach { part ->
                    when (part) {
                        is UIMessagePart.Image -> AsyncImage(
                            model = part.url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .clip(AppShapes.CardSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        )
                        is UIMessagePart.Video -> AttachmentLabel("Video")
                        is UIMessagePart.Audio -> AttachmentLabel("Audio")
                        is UIMessagePart.Document -> AttachmentLabel(part.fileName)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(AppShapes.Chip)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPage(
    state: IosAppState,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    platformHaptics: PlatformHaptics,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeContent),
        topBar = { TopAppBar(
            title = { Text("Chats", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        ) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.assistants.forEach { assistant ->
                        if (assistant.id == state.selectedAssistantId) {
                            Button(onClick = {}) { Text(assistant.name) }
                        } else {
                            TextButton(onClick = {
                                onSelectAssistant(assistant.id)
                                onBack()
                            }) { Text(assistant.name) }
                        }
                    }
                    TextButton(onClick = {
                        onNewAssistant()
                        onBack()
                    }) { Text("New assistant") }
                }
            }
            item { Button(onClick = { onNewChat(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("New chat") } }
            items(state.conversations, key = { it.id }) { conversation ->
                ConversationListRow(
                    title = conversation.title,
                    selected = conversation.id == state.selectedConversationId,
                    platformHaptics = platformHaptics,
                    onRename = { title -> onRenameConversation(conversation.id, title) },
                    onDelete = { onDeleteConversation(conversation.id) },
                ) {
                    onSelectConversation(conversation.id)
                    onBack()
                }
            }
            item { MenuCard("Settings", "Providers, appearance, tools, and storage", onSettings) }
            item { MenuCard("Statistics", "Conversations, messages, and token usage", onStatistics) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsPage(
    state: IosAppState,
    darkTheme: Boolean,
    onBack: () -> Unit,
) {
    val messages = state.conversations.flatMap { it.messages }
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
    onSaveProvider: (IosProviderType, String, String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveAppearance: (String, IosColorMode) -> Unit,
    onSaveAssistant: (String, String) -> Unit,
    onNewAssistant: () -> Unit,
    onSelectAssistant: (String) -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onBack: () -> Unit,
) {
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
            title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
        ) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            item { MenuCard("Data", "Conversations are stored in the iOS app container") {} }
        }
    }
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
