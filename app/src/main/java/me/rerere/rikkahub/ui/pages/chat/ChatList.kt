package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.chat.ChatMessageTurn
import me.rerere.rikkahub.ui.components.chat.MessageTurnGroup
import me.rerere.rikkahub.ui.components.chat.groupIntoTurns
import me.rerere.rikkahub.ui.components.ui.ListSelectableItem
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.utils.plus
import kotlin.uuid.Uuid
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.openUrl

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    previewMode: Boolean,
    settings: Settings,
    recentlyRestoredNodeIds: Set<Uuid> = emptySet(),
    initialSearchQuery: String? = null,
    onRegenerate: (UIMessage) -> Unit = {},
    onEdit: (UIMessage) -> Unit = {},
    onForkMessage: (UIMessage) -> Unit = {},
    onDelete: (UIMessage) -> Unit = {},
    onUpdateMessage: (MessageNode) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
) {
    SharedTransitionLayout {
        AnimatedContent(
            targetState = previewMode,
            label = "ChatListMode",
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.8f) togetherWith fadeOut() + scaleOut(targetScale = 0.8f))
            }
        ) { target ->
            if (target) {
                ChatListPreview(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    settings = settings,
                    onJumpToMessage = onJumpToMessage,
                    animatedVisibilityScope = this@AnimatedContent,
                    initialSearchQuery = initialSearchQuery,
                )
            } else {
                ChatListNormal(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    state = state,
                    loading = loading,
                    settings = settings,
                    recentlyRestoredNodeIds = recentlyRestoredNodeIds,
                    onRegenerate = onRegenerate,
                    onEdit = onEdit,
                    onForkMessage = onForkMessage,
                    onDelete = onDelete,
                    onUpdateMessage = onUpdateMessage,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    settings: Settings,
    recentlyRestoredNodeIds: Set<Uuid> = emptySet(),
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    var userScrolledUp by remember { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    val context = LocalContext.current
    val navController = LocalNavController.current

    val currentConversationState = rememberUpdatedState(conversation)
    val onCitationClick = remember {
        { citationId: String ->
            run findCitation@{
                currentConversationState.value.currentMessages.forEach { message ->
                    message.parts.forEach { part ->
                        if (part is UIMessagePart.ToolResult && part.toolName == "search_web") {
                            val items = part.content.jsonObject["items"]?.jsonArray ?: return@forEach
                            items.forEach { item ->
                                val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                                val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                                if (citationId == id) {
                                    context.openUrl(url)
                                    return@findCitation
                                }
                            }
                        }
                    }
                }
            }
            Unit
        }
    }

    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastItem = lastOrNull() ?: return false
        if (lastItem.key == LoadingIndicatorKey || lastItem.key == ScrollBottomKey) {
            return true
        }
        // Check if we can see the bottom spacer or the last real item
        val hasScrollBottom = any { it.key == ScrollBottomKey }
        if (hasScrollBottom) return true
        // Fallback: check if the last visible item is near the end
        return !state.canScrollForward || (lastItem.offset + lastItem.size <= state.layoutInfo.viewportEndOffset + lastItem.size * 0.15 + 32)
    }

    // 聊天选择
    val selectedItems = remember { mutableStateListOf<Uuid>() }
    var selecting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // Empty chat state removed - assistant icon now shown in TopBar

        // Detect user scrolling up to suppress auto-scroll
        LaunchedEffect(state) {
            var previousFirstIndex = state.firstVisibleItemIndex
            var previousFirstOffset = state.firstVisibleItemScrollOffset
            snapshotFlow {
                Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }.collect { (isScrolling, firstIndex, firstOffset) ->
                if (isScrolling && loadingState) {
                    // User is actively scrolling during generation
                    val scrolledUp = firstIndex < previousFirstIndex ||
                        (firstIndex == previousFirstIndex && firstOffset < previousFirstOffset)
                    if (scrolledUp) {
                        userScrolledUp = true
                    }
                    // If user scrolls back to bottom, resume auto-scroll
                    if (state.layoutInfo.visibleItemsInfo.isAtBottom()) {
                        userScrolledUp = false
                    }
                }
                previousFirstIndex = firstIndex
                previousFirstOffset = firstOffset
            }
        }

        // Reset userScrolledUp when loading stops
        LaunchedEffect(loading) {
            if (!loading) {
                userScrolledUp = false
            }
        }

        // Auto-scroll to bottom during generation
        LaunchedEffect(state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
                if (!state.isScrollInProgress && loadingState && !userScrolledUp) {
                    // Scroll to the very last item in the list (ScrollBottomKey spacer)
                    val targetIndex = state.layoutInfo.totalItemsCount - 1
                    if (targetIndex >= 0) {
                        state.animateScrollToItem(targetIndex)
                    }
                }
            }
        }

        // 判断最近是否滚动
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        // Group consecutive messages by role into turns
        // Computed fresh on each recomposition to ensure up-to-date data
        val turnGroups = conversation.messageNodes.groupIntoTurns()

        // Index helpers for regen visibility
        val lastUserIndex = remember(conversation.messageNodes) {
            conversation.messageNodes.indexOfLast { it.currentMessage.role == me.rerere.ai.core.MessageRole.USER }
        }
        val nodeIndexById = remember(conversation.messageNodes) {
            conversation.messageNodes.mapIndexed { index, node -> node.id to index }.toMap()
        }
        
        // Check if we need a phantom loading turn (loading but no assistant response yet)
        val needsPhantomLoadingTurn = loading && (
            turnGroups.isEmpty() || 
            turnGroups.lastOrNull()?.role == me.rerere.ai.core.MessageRole.USER
        )

        val pendingAssistantGroup = remember(conversation.id) {
            MessageTurnGroup(
                nodes = listOf(MessageNode.of(UIMessage.assistant(""))),
                role = me.rerere.ai.core.MessageRole.ASSISTANT
            )
        }
        val displayGroups = if (needsPhantomLoadingTurn) {
            turnGroups + pendingAssistantGroup
        } else {
            turnGroups
        }
        
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp) + PaddingValues(bottom = 32.dp) + innerPadding + androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "conversation_list"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .fillMaxSize(),
        ) {
            itemsIndexed(
                items = displayGroups,
                key = { index, group ->
                    if (group.role == me.rerere.ai.core.MessageRole.ASSISTANT && index == displayGroups.lastIndex) {
                        "pending_assistant"
                    } else {
                        group.firstNode.id
                    }
                },
            ) { index, group ->
                Column {
                    // Check if any node in group is selected
                    val isSelected by remember(group.nodes.map { it.id }) {
                        derivedStateOf { group.nodes.any { selectedItems.contains(it.id) } }
                    }
                    ListSelectableItem(
                        isSelected = isSelected,
                        onSelectChange = { checked ->
                            if (checked) {
                                group.nodes.forEach { selectedItems.add(it.id) }
                            } else {
                                group.nodes.forEach { selectedItems.remove(it.id) }
                            }
                        },
                        enabled = selecting,
                    ) {
                        val isLastTurn = index == displayGroups.lastIndex
                        val showRegenerate by remember(group.role, isLastTurn) {
                            derivedStateOf {
                                when (group.role) {
                                    me.rerere.ai.core.MessageRole.USER -> true
                                    else -> isLastTurn
                                }
                            }
                        }
                        ChatMessageTurn(
                            group = group,
                            isLastTurn = isLastTurn,
                            onCitationClick = onCitationClick,
                            model = group.lastNode.currentMessage.modelId?.let { settings.findModelById(it) },
                            assistant = settings.getAssistantById(conversation.assistantId),
                            loading = loading && isLastTurn,
                            onRegenerate = { node ->
                                onRegenerate(node.currentMessage)
                            },
                            onEdit = { node ->
                                onEdit(node.currentMessage)
                            },
                            onFork = { node ->
                                onForkMessage(node.currentMessage)
                            },
                            onDelete = { node ->
                                onDelete(node.currentMessage)
                            },
                            onShare = { node ->
                                selecting = true
                                selectedItems.clear()
                                val nodeIndex = conversation.messageNodes.indexOf(node)
                                if (nodeIndex >= 0) {
                                    selectedItems.addAll(conversation.messageNodes
                                        .subList(0, nodeIndex + 1)
                                        .map { it.id })
                                }
                            },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            onEditLorebookEntry = { entry ->
                                navController.navigate(Screen.SettingLorebookDetail(entry.lorebookId, entry.entryId))
                            },
                            onModeClick = { mode ->
                                navController.navigate(Screen.SettingModes(scrollToModeId = mode.modeId))
                            },
                            onMemoryClick = { memory ->
                                navController.navigate(
                                    Screen.AssistantDetail(
                                        id = conversation.assistantId.toString(),
                                        startRoute = "memory",
                                        initialMemoryTab = memory.memoryType,
                                        scrollToMemoryId = memory.memoryId
                                    )
                                )
                            },
                            showRegenerate = showRegenerate,
                        )
                    }
                    // Show truncate indicator if any node in this group is at the truncate point
                    val truncateNode = group.nodes.find { node ->
                        conversation.messageNodes.indexOf(node) == conversation.truncateIndex - 1
                    }
                    if (truncateNode != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.chat_page_clear_context),
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Phantom loading turn now handled as a synthetic assistant group for morphing.

            // 为了能正确滚动到这
            item(ScrollBottomKey) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 完成选择
            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it * 2 },
                ),
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                ) {
                    Tooltip(
                        tooltip = {
                            Text("Clear selection")
                        }
                    ) {
                        IconButton(
                            onClick = {
                                selecting = false
                                selectedItems.clear()
                            }
                        ) {
                            Icon(Icons.Rounded.Close, null)
                        }
                    }
                    Tooltip(
                        tooltip = {
                            Text("Select all")
                        }
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.addAll(conversation.messageNodes.map { it.id })
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.TouchApp, null)
                        }
                    }
                    Tooltip(
                        tooltip = {
                            Text("Confirm")
                        }
                    ) {
                        FilledIconButton(
                            onClick = {
                                selecting = false
                                val messages = conversation.messageNodes.filter { it.id in selectedItems }
                                if (messages.isNotEmpty()) {
                                    showExportSheet = true
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Check, null)
                        }
                    }
                }
            }

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current
            val effectiveDisplay = settings.getEffectiveDisplaySetting()

            // 消息快速跳转
            MessageJumper(
                show = isRecentScroll && !state.isScrollInProgress && effectiveDisplay.showMessageJumper && !captureProgress,
                onLeft = effectiveDisplay.messageJumperOnLeft,
                scope = scope,
                state = state
            )
        }
    }
}

/**
 * 提取包含搜索词的文本片段，确保匹配词在开头可见
 */
private fun extractMatchingSnippet(
    text: String,
    query: String
): String {
    if (query.isBlank()) {
        return text
    }

    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex == -1) {
        return text
    }

    // 直接从匹配词开始显示，确保匹配词在最前面
    val snippet = text.substring(matchIndex)

    // 只在前面有内容时添加省略号
    return if (matchIndex > 0) {
        "...$snippet"
    } else {
        snippet
    }
}

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var startIndex = 0
        var index = text.indexOf(query, startIndex, ignoreCase = true)

        while (index >= 0) {
            // 添加高亮前的文本
            append(text.substring(startIndex, index))

            // 添加高亮文本
            withStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = Color.Black
                )
            ) {
                append(text.substring(index, index + query.length))
            }

            startIndex = index + query.length
            index = text.indexOf(query, startIndex, ignoreCase = true)
        }

        // 添加剩余文本
        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }
}

@Composable
private fun SharedTransitionScope.ChatListPreview(
    innerPadding: PaddingValues,
    conversation: Conversation,
    settings: Settings,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onJumpToMessage: (Int) -> Unit,
    initialSearchQuery: String? = null,
) {
    var searchQuery by remember { mutableStateOf(initialSearchQuery ?: "") }
    val previewTopPadding = 20.dp

    // Filter messages
    val filteredMessages = remember(conversation.messageNodes, searchQuery) {
        if (searchQuery.isBlank()) {
            conversation.messageNodes
        } else {
            conversation.messageNodes.filterIndexed { index, node ->
                node.currentMessage.toText().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(top = previewTopPadding)
            .fillMaxSize(),
    ) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.chat_page_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            maxLines = 1,
        )

        // 消息预览
        LazyColumn(
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "conversation_list"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = filteredMessages,
                key = { index, item -> item.id },
            ) { _, node ->
                val message = node.currentMessage
                val isUser = message.role == me.rerere.ai.core.MessageRole.USER
                val originalIndex = conversation.messageNodes.indexOf(node)
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            if (!isUser) Modifier.padding(end = 24.dp) else Modifier
                        ),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onJumpToMessage(originalIndex)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
                            val highlightedText = remember(searchQuery, message) {
                                val fullText = message.toText().trim().ifBlank { "[...]" }
                                val messageText = extractMatchingSnippet(
                                    text = fullText,
                                    query = searchQuery
                                )
                                buildHighlightedText(
                                    text = messageText,
                                    query = searchQuery,
                                    highlightColor = highlightColor
                                )
                            }
                            Text(
                                text = highlightedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BoxScope.MessageJumper(
    show: Boolean,
    onLeft: Boolean,
    scope: CoroutineScope,
    state: LazyListState
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(
            initialOffsetX = { if (onLeft) -it * 2 else it * 2 },
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it * 2 else it * 2 },
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardDoubleArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(
                            (state.firstVisibleItemIndex - 1).fastCoerceAtLeast(
                                0
                            )
                        )
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(state.firstVisibleItemIndex + 1)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(state.layoutInfo.totalItemsCount - 1)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardDoubleArrowDown,
                    contentDescription = stringResource(R.string.chat_page_scroll_to_bottom),
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Phantom loading turn shown immediately when user sends a message,
 * before any tokens arrive from the assistant.
 */
@Composable
private fun PhantomLoadingTurn(
    assistant: Assistant?,
    settings: Settings,
    modifier: Modifier = Modifier
) {
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)
    val showIcon = effectiveDisplay.showModelIcon
    val showModelName = effectiveDisplay.showModelName
    val showAssistantBubbles = effectiveDisplay.showAssistantBubbles
    val avatarName = assistant?.name?.ifEmpty { null } ?: "Assistant"
    val avatarValue = assistant?.avatar ?: me.rerere.rikkahub.data.model.Avatar.Dummy
    val elementSpacing = if (showAssistantBubbles) 4.dp else 3.dp
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(elementSpacing)
    ) {
        if (showAssistantBubbles) {
            // Name above pills (only if enabled)
            if (showModelName) {
                Text(
                    text = avatarName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0f)
                )
            }

            // Avatar + Waiting pill row
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(elementSpacing)
            ) {
                if (showIcon) {
                    me.rerere.rikkahub.ui.components.ui.UIAvatar(
                        name = avatarName,
                        modifier = Modifier.size(36.dp),
                        value = avatarValue,
                        loading = true,
                    )
                }

                me.rerere.rikkahub.ui.components.chat.ActivityPillRow(
                    state = me.rerere.rikkahub.ui.components.chat.ActivityState.Waiting,
                    onClick = { _ -> },
                    connectsToBubbleBelow = false,
                    modifier = Modifier.height(36.dp)
                )
            }
        } else {
            // No assistant bubbles layout
            if (showIcon || showModelName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showIcon) {
                        me.rerere.rikkahub.ui.components.ui.UIAvatar(
                            name = avatarName,
                            modifier = Modifier.size(36.dp),
                            value = avatarValue,
                            loading = true,
                        )
                    }
                    if (showModelName) {
                        Text(
                            text = avatarName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(0f)
                        )
                    }
                }
            }

            me.rerere.rikkahub.ui.components.chat.ActivityPillRow(
                state = me.rerere.rikkahub.ui.components.chat.ActivityState.Waiting,
                onClick = { _ -> },
                connectsToBubbleBelow = false,
                modifier = Modifier.height(36.dp)
            )
        }
    }
}
