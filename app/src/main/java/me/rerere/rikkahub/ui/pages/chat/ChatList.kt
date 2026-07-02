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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.onSizeChanged
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
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
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
private const val ScrollBottomKey = "ScrollBottomKey"
private const val AssistantInitialTurnKey = "assistant_initial"
private const val AssistantResponseTurnKey = "assistant_response"

/**
 * Computes a cheap structural fingerprint of a [MessageNode] list that deliberately avoids
 * calling equals() on any [kotlinx.serialization.json.JsonElement] fields (which performs a
 * full deep structural comparison and is the source of scroll-stutter in tool-heavy chats).
 *
 * Only primitive/identity fields are mixed in: node IDs, selectIndex, message IDs, part count,
 * and the *length* of string payloads (never their content, which may embed large JSON).
 */
private fun List<MessageNode>.messageNodesSignature(): Long {
    var hash = -3750763034362895579L
    for (node in this) {
        hash = (hash xor node.id.hashCode().toLong()) * 1099511628211L
        hash = (hash xor node.selectIndex.toLong()) * 1099511628211L
        hash = (hash xor node.messages.size.toLong()) * 1099511628211L
        for (msg in node.messages) {
            hash = (hash xor msg.id.hashCode().toLong()) * 1099511628211L
            hash = (hash xor msg.parts.size.toLong()) * 1099511628211L
            hash = (hash xor msg.annotations.size.toLong()) * 1099511628211L
            for (part in msg.parts) {
                hash = when (part) {
                    is me.rerere.ai.ui.UIMessagePart.Text ->
                        (hash xor 1L xor part.text.length.toLong()) * 1099511628211L
                    is me.rerere.ai.ui.UIMessagePart.ToolCall ->
                        (hash xor 2L xor part.toolCallId.hashCode().toLong()
                            xor part.arguments.length.toLong()) * 1099511628211L
                    is me.rerere.ai.ui.UIMessagePart.ToolResult ->
                        // Use toolCallId + toolName only; never compare content/arguments JsonElement.
                        (hash xor 3L xor part.toolCallId.hashCode().toLong()
                            xor part.toolName.hashCode().toLong()) * 1099511628211L
                    is me.rerere.ai.ui.UIMessagePart.Reasoning ->
                        (hash xor 4L xor part.reasoning.length.toLong()) * 1099511628211L
                    else ->
                        (hash xor part::class.hashCode().toLong()) * 1099511628211L
                }
            }
        }
    }
    return hash
}

internal fun chatListTurnKey(
    group: MessageTurnGroup,
    index: Int,
    previousGroup: MessageTurnGroup? = null,
    isPendingAssistantTurn: Boolean,
): String {
    val previousUserId = previousGroup
        ?.takeIf { it.role == me.rerere.ai.core.MessageRole.USER }
        ?.lastNode
        ?.id

    return when {
        isPendingAssistantTurn && previousUserId != null -> "$AssistantResponseTurnKey:$previousUserId"
        isPendingAssistantTurn -> "$AssistantInitialTurnKey:$index"
        group.role == me.rerere.ai.core.MessageRole.ASSISTANT && previousUserId != null -> {
            "$AssistantResponseTurnKey:$previousUserId"
        }
        group.role == me.rerere.ai.core.MessageRole.ASSISTANT && previousGroup == null -> {
            "$AssistantInitialTurnKey:$index"
        }
        else -> "turn:${group.firstNode.id}:$index"
    }
}

private fun buildChatStreamingFollowSignature(
    conversation: Conversation,
    loading: Boolean
): String {
    if (!loading) return "idle:${conversation.messageNodes.size}"
    val latestAssistant = conversation.currentMessages
        .asReversed()
        .firstOrNull { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
    val textLength = latestAssistant
        ?.parts
        ?.filterIsInstance<UIMessagePart.Text>()
        ?.sumOf { it.text.length }
        ?: 0
    val activityLength = latestAssistant
        ?.parts
        ?.sumOf { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.length
                is UIMessagePart.Reasoning -> part.reasoning.length
                is UIMessagePart.ToolCall -> part.arguments.length
                else -> 0
            }
        }
        ?: 0
    return "${conversation.messageNodes.size}:$textLength:$activityLength"
}

internal fun isChatListAtStreamingBottom(
    visibleItems: List<LazyListItemInfo>,
    canScrollForward: Boolean,
): Boolean {
    return visibleItems.isNotEmpty() && !canScrollForward
}

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
    searchQuery: String = initialSearchQuery.orEmpty(),
    onSearchQueryChange: (String) -> Unit = {},
    shareSelecting: Boolean = false,
    selectedShareItems: Set<Uuid> = emptySet(),
    onSelectedShareItemsChange: (Set<Uuid>) -> Unit = {},
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
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
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
                    selecting = shareSelecting,
                    selectedItems = selectedShareItems,
                    onSelectedItemsChange = onSelectedShareItemsChange,
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
    selecting: Boolean,
    selectedItems: Set<Uuid>,
    onSelectedItemsChange: (Set<Uuid>) -> Unit,
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
    var followStreamingBottom by remember { mutableStateOf(true) }
    var forceBottomAttachPending by remember(conversation.id) { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    val context = LocalContext.current
    val navController = LocalNavController.current

    suspend fun snapToStreamingBottom() {
        val targetIndex = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        if (targetIndex <= 0) return
        try {
            state.scrollToItem(targetIndex)
        } catch (_: IllegalStateException) {
            // The lazy list can be between measure passes while a streaming turn morphs.
        }
    }

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
    val generationHaptics = rememberPremiumHaptics(
        enabled = settings.displaySetting.enableMessageGenerationHapticEffect
    )

    LaunchedEffect(conversation.id, settings.displaySetting.enableMessageGenerationHapticEffect) {
        var previousLength = 0
        snapshotFlow {
            if (!loadingState) {
                0
            } else {
                conversationUpdated.currentMessages
                    .asReversed()
                    .firstOrNull { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
                    ?.parts
                    ?.filterIsInstance<UIMessagePart.Text>()
                    ?.sumOf { it.text.length }
                    ?: 0
            }
        }.collect { length ->
            if (length == 0) {
                previousLength = 0
            } else if (length > previousLength + 24) {
                generationHaptics.perform(HapticPattern.ScrollEdge)
                previousLength = length
                delay(120)
            } else if (length < previousLength) {
                previousLength = length
            }
        }
    }

    // 聊天选择
    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // Empty chat state removed - assistant icon now shown in TopBar

        // User scrolls detach live following; reaching the bottom reattaches it.
        LaunchedEffect(state) {
            var previousFirstIndex = state.firstVisibleItemIndex
            var previousFirstOffset = state.firstVisibleItemScrollOffset
            snapshotFlow {
                Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }.collect { (isScrolling, firstIndex, firstOffset) ->
                if (isScrolling && loadingState) {
                    val scrolledUp = firstIndex < previousFirstIndex ||
                        (firstIndex == previousFirstIndex && firstOffset < previousFirstOffset)
                    if (scrolledUp) {
                        followStreamingBottom = false
                    }
                    if (
                        isChatListAtStreamingBottom(
                            visibleItems = state.layoutInfo.visibleItemsInfo,
                            canScrollForward = state.canScrollForward,
                        )
                    ) {
                        followStreamingBottom = true
                    }
                }
                previousFirstIndex = firstIndex
                previousFirstOffset = firstOffset
            }
        }

        // New generations start attached unless the user scrolls away.
        LaunchedEffect(loading) {
            if (loading) {
                followStreamingBottom = true
                forceBottomAttachPending = true
            } else {
                forceBottomAttachPending = false
            }
        }

        LaunchedEffect(state) {
            snapshotFlow {
                isChatListAtStreamingBottom(
                    visibleItems = state.layoutInfo.visibleItemsInfo,
                    canScrollForward = state.canScrollForward,
                )
            }.collect { isAtBottom ->
                if (loadingState && isAtBottom) {
                    followStreamingBottom = true
                }
            }
        }

        LaunchedEffect(state) {
            snapshotFlow {
                buildChatStreamingFollowSignature(
                    conversation = conversationUpdated,
                    loading = loadingState
                )
            }.collect {
                if (loadingState && followStreamingBottom) {
                    snapToStreamingBottom()
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

        // Group consecutive messages by role into turns.
        // Memoized with a cheap structural signature to prevent O(N) grouping on every
        // recomposition. We deliberately avoid using `conversation.messageNodes` directly as the
        // key because Kotlin List.equals() deep-compares JsonElement fields inside ToolResult
        // (content/arguments), which can be huge JSON trees and causes a visible stutter on
        // first scroll in tool-heavy chats.
        val turnGroupsKey = conversation.messageNodes.messageNodesSignature()
        val turnGroups = remember(turnGroupsKey) {
            conversation.messageNodes.groupIntoTurns()
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

        LaunchedEffect(loading, displayGroups.size, state) {
            if (!loading || !forceBottomAttachPending) return@LaunchedEffect

            withFrameNanos { }
            snapToStreamingBottom()
            withFrameNanos { }
            snapToStreamingBottom()
            followStreamingBottom = true
            forceBottomAttachPending = false
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
                        chatListTurnKey(
                            group = group,
                            index = index,
                            previousGroup = displayGroups.getOrNull(index - 1),
                            isPendingAssistantTurn = needsPhantomLoadingTurn && index == displayGroups.lastIndex,
                        )
                    },
                    contentType = { _, group ->
                        // Distinguish tool-bearing assistant turns (heavy composition subtrees)
                        // from text-only turns so Compose reuses the most appropriate slot.
                        if (group.role == me.rerere.ai.core.MessageRole.USER) {
                            "user"
                        } else if (group.nodes.any { it.role == me.rerere.ai.core.MessageRole.TOOL }) {
                            "assistant_tools"
                        } else {
                            "assistant_text"
                        }
                    },
                ) { index, group ->
                    Column {
                        // Check if any node in group is selected
                        val isSelected by remember(group.nodes.map { it.id }, selectedItems) {
                            derivedStateOf { group.nodes.any { selectedItems.contains(it.id) } }
                        }
                        ListSelectableItem(
                            isSelected = isSelected,
                            onSelectChange = { checked ->
                                val groupIds = group.nodes.map { it.id }.toSet()
                                if (checked) {
                                    onSelectedItemsChange(selectedItems + groupIds)
                                } else {
                                    onSelectedItemsChange(selectedItems - groupIds)
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
                                        if (followStreamingBottom && !state.isScrollInProgress) {
                                            scope.launch {
                                                snapToStreamingBottom()
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                                modifier = if (loading && isLastTurn) {
                                    Modifier.onSizeChanged {
                                        if (followStreamingBottom && !state.isScrollInProgress) {
                                            scope.launch {
                                                snapToStreamingBottom()
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
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
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
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
