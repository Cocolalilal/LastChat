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
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
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
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.BidiDirection
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.resolveBidiDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatBlurSource

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

private fun BidiDirection.toLayoutDirection(): LayoutDirection {
    return if (this == BidiDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

private fun BidiDirection.toComposeTextDirection(): TextDirection {
    return if (this == BidiDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
}

private fun resolveSnippetDirection(text: String, locale: Locale): BidiDirection {
    return resolveBidiDirection(text = text, fallbackLocale = locale)
}

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    previewMode: Boolean,
    settings: Settings,
    contentMaxWidth: Dp = Dp.Unspecified,
    recentlyRestoredNodeIds: Set<Uuid> = emptySet(),
    initialSearchQuery: String? = null,
    shareSelectionRequestKey: Int = 0,
    shareSelectionCancelRequestKey: Int = 0,
    onShareSelectionRequestConsumed: () -> Unit = {},
    onShareSelectionCancelRequestConsumed: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
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
                    contentMaxWidth = contentMaxWidth,
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
                    contentMaxWidth = contentMaxWidth,
                    recentlyRestoredNodeIds = recentlyRestoredNodeIds,
                    shareSelectionRequestKey = shareSelectionRequestKey,
                    shareSelectionCancelRequestKey = shareSelectionCancelRequestKey,
                    onShareSelectionRequestConsumed = onShareSelectionRequestConsumed,
                    onShareSelectionCancelRequestConsumed = onShareSelectionCancelRequestConsumed,
                    onSelectionModeChange = onSelectionModeChange,
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
    contentMaxWidth: Dp,
    recentlyRestoredNodeIds: Set<Uuid> = emptySet(),
    shareSelectionRequestKey: Int,
    shareSelectionCancelRequestKey: Int,
    onShareSelectionRequestConsumed: () -> Unit,
    onShareSelectionCancelRequestConsumed: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
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

    LaunchedEffect(shareSelectionRequestKey) {
        if (shareSelectionRequestKey > 0) {
            selecting = true
            selectedItems.clear()
            selectedItems.addAll(conversation.messageNodes.map { it.id })
            onShareSelectionRequestConsumed()
        }
    }

    LaunchedEffect(shareSelectionCancelRequestKey) {
        if (shareSelectionCancelRequestKey > 0) {
            selecting = false
            selectedItems.clear()
            onShareSelectionCancelRequestConsumed()
        }
    }

    LaunchedEffect(selecting) {
        onSelectionModeChange(selecting)
    }

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
        val assistant = remember(settings.assistants, conversation.assistantId) {
            settings.getAssistantById(conversation.assistantId)
        }
        val modelById = remember(settings.providers) {
            settings.providers
                .flatMap { it.models }
                .associateBy { it.id }
        }
        
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp) + PaddingValues(bottom = 32.dp) + innerPadding + androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                    .lastChatBlurSource()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "conversation_list"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .align(Alignment.TopCenter)
                    .then(
                        if (contentMaxWidth != Dp.Unspecified) {
                            Modifier
                                .widthIn(max = contentMaxWidth)
                                .fillMaxWidth()
                                .fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ),
            ) {
                itemsIndexed(
                    items = displayGroups,
                    key = { index, group ->
                        if (group.role == me.rerere.ai.core.MessageRole.ASSISTANT && index == displayGroups.lastIndex) {
                            "pending_assistant"
                        } else {
                            "turn:${group.firstNode.id}:$index"
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
                                model = group.lastNode.currentMessage.modelId?.let(modelById::get),
                                assistant = assistant,
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
                                onUpdate = {
                                    onUpdateMessage(it)
                                },
                                onEditLorebookEntry = { entry ->
                                    navController.navigate(Screen.SettingLorebookDetail(entry.lorebookId, entry.entryId))
                                },
                                onModeClick = { mode ->
                                    navController.navigate(Screen.SettingSkills(scrollToSkillId = mode.modeId))
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
                                onExpandedStreamingCodeBlockChanged = if (loading && isLastTurn) {
                                    {
                                        if (!userScrolledUp) {
                                            scope.launch {
                                                val targetIndex = state.layoutInfo.totalItemsCount - 1
                                                if (targetIndex >= 0) {
                                                    state.animateScrollToItem(targetIndex)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                enter = fadeIn(
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 340f)
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 340f)
                ) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 340f),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
                ) + slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
                    targetOffsetY = { it }
                ),
            ) {
                val selectionToolbarShape = RoundedCornerShape(999.dp)
                Surface(
                    shape = selectionToolbarShape,
                    color = blurredContainerColor(MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .height(48.dp)
                        .lastChatBlurEffect(MaterialTheme.colorScheme.surfaceContainer, selectionToolbarShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Tooltip(
                            tooltip = {
                                Text(stringResource(R.string.chat_clear_selection))
                            }
                        ) {
                            IconButton(
                                modifier = Modifier.size(40.dp),
                                onClick = {
                                    selecting = false
                                    selectedItems.clear()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Tooltip(
                            tooltip = {
                                Text(stringResource(R.string.select_all))
                            }
                        ) {
                            IconButton(
                                modifier = Modifier.size(40.dp),
                                onClick = {
                                    if (selectedItems.isNotEmpty()) {
                                        selectedItems.clear()
                                    } else {
                                        selectedItems.addAll(conversation.messageNodes.map { it.id })
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Tooltip(
                            tooltip = {
                                Text(stringResource(R.string.confirm))
                            }
                        ) {
                            FilledIconButton(
                                modifier = Modifier.size(40.dp),
                                onClick = {
                                    selecting = false
                                    val messages = conversation.messageNodes.filter { it.id in selectedItems }
                                    if (messages.isNotEmpty()) {
                                        showExportSheet = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
            val effectiveDisplay = settings.getEffectiveDisplaySetting(
                settings.getAssistantById(conversation.assistantId)
            )

            // 消息快速跳转
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MessageJumper(
                    show = isRecentScroll && !state.isScrollInProgress && effectiveDisplay.showMessageJumper && !captureProgress,
                    onLeft = effectiveDisplay.messageJumperOnLeft,
                    scope = scope,
                    state = state
                )
            }
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
    contentMaxWidth: Dp,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onJumpToMessage: (Int) -> Unit,
    initialSearchQuery: String? = null,
) {
    var searchQuery by remember { mutableStateOf(initialSearchQuery ?: "") }
    val previewTopPadding = 20.dp
    val appLocale = LocalContext.current.appLocale()
    val previewLayoutDirection = LocalLayoutDirection.current

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

    Box(
        modifier = Modifier
            .padding(
                start = innerPadding.calculateStartPadding(previewLayoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(previewLayoutDirection),
                bottom = 0.dp
            )
            .padding(top = previewTopPadding)
            .then(
                if (contentMaxWidth != Dp.Unspecified) {
                    Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                }
            ),
    ) {
        // 搜索框
        // 消息预览
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 80.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .lastChatBlurSource()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "conversation_list"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.12f to Color.Black,
                                    0.88f to Color.Black,
                                    1f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
            ) {
                itemsIndexed(
                    items = filteredMessages,
                    key = { index, item -> "preview:${item.id}:$index" },
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
                                val snippetText = remember(searchQuery, message) {
                                    val fullText = message.toText().trim().ifBlank { "[...]" }
                                    extractMatchingSnippet(
                                        text = fullText,
                                        query = searchQuery
                                    )
                                }
                                val highlightedText = remember(snippetText, searchQuery, highlightColor) {
                                    buildHighlightedText(
                                        text = snippetText,
                                        query = searchQuery,
                                        highlightColor = highlightColor
                                    )
                                }
                                val snippetDirection = remember(snippetText, appLocale) {
                                    resolveSnippetDirection(snippetText, appLocale)
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides snippetDirection.toLayoutDirection()) {
                                    Text(
                                        text = highlightedText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDirection = snippetDirection.toComposeTextDirection()
                                        ),
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

        val searchFieldShape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField
        val searchFieldContainerColor = MaterialTheme.colorScheme.surfaceContainer
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .lastChatBlurEffect(searchFieldContainerColor, searchFieldShape),
            shape = searchFieldShape,
            color = blurredContainerColor(searchFieldContainerColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.background)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search messages") },
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
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = searchFieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                ),
                maxLines = 1,
            )
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
                color = blurredContainerColor(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.65f)
                ),
                modifier = Modifier.lastChatBlurEffect(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    CircleShape
                )
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
                color = blurredContainerColor(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.65f)
                ),
                modifier = Modifier.lastChatBlurEffect(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    CircleShape
                )
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
                color = blurredContainerColor(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.65f)
                ),
                modifier = Modifier.lastChatBlurEffect(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    CircleShape
                )
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
                color = blurredContainerColor(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.65f)
                ),
                modifier = Modifier.lastChatBlurEffect(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    CircleShape
                ),
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
