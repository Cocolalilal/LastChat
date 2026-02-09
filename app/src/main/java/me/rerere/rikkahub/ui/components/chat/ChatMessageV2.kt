package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.message.ChatMessageActionButtons
import me.rerere.rikkahub.ui.components.message.ChatMessageActionsSheet
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.ui.components.message.ChatMessageCopySheet
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.ai.core.MessageRole as AIMessageRole

/**
 * Represents a group of consecutive messages from the same role.
 * For assistant messages, this groups all consecutive assistant nodes together.
 */
data class MessageTurnGroup(
    val nodes: List<MessageNode>,
    val role: MessageRole
) {
    val firstNode get() = nodes.first()
    val lastNode get() = nodes.last()
    
    /** Node with the most message versions - used for version switching controls */
    val nodeWithMostVersions get() = nodes.maxByOrNull { it.messages.size } ?: lastNode
    
    /** The active versionTag from the first node's current message */
    val activeVersionTag: String? get() = firstNode.currentMessage.versionTag
    
    /** 
     * Nodes filtered to only include those with a message matching the active versionTag.
     * For backwards compatibility, if no versionTag exists on the active version, 
     * we show nodes that have at least one null-tagged message (pre-versioning nodes).
     * 
     * IMPORTANT: This returns nodes with their selectIndex adjusted to point to the 
     * message matching the active versionTag, not the original currentMessage.
     */
    val filteredNodes: List<MessageNode> get() {
        val tag = activeVersionTag
        return nodes.mapNotNull { node ->
            if (tag == null) {
                // Active version is null-tagged (old version before versioning)
                // Find the first message with null versionTag
                val index = node.messages.indexOfFirst { it.versionTag == null }
                if (index != -1) {
                    node.copy(selectIndex = index)
                } else null
            } else {
                // Active version has a tag, find message with matching tag
                val index = node.messages.indexOfFirst { it.versionTag == tag }
                if (index != -1) {
                    node.copy(selectIndex = index)
                } else null
            }
        }
    }
    
    /** All message parts from filtered nodes in the group */
    val allParts: List<UIMessagePart> get() = filteredNodes.flatMap { it.currentMessage.parts }
    
    /** Combined token usage for filtered messages in the group */
    val combinedUsage: TokenUsage? get() {
        val usages = filteredNodes.mapNotNull { it.currentMessage.usage }
        if (usages.isEmpty()) return null
        return TokenUsage(
            promptTokens = usages.sumOf { it.promptTokens },
            completionTokens = usages.sumOf { it.completionTokens },
            totalTokens = usages.sumOf { it.totalTokens },
            cachedTokens = usages.sumOf { it.cachedTokens }
        )
    }
    
    /** Combined generation duration for filtered messages */
    val combinedGenerationDurationMs: Long? get() {
        val durations = filteredNodes.mapNotNull { it.currentMessage.generationDurationMs }
        return if (durations.isNotEmpty()) durations.sum() else null
    }
}

/**
 * Group consecutive messages by role into MessageTurnGroups.
 * TOOL messages are treated as part of the ASSISTANT turn (they're tool results).
 */
fun List<MessageNode>.groupIntoTurns(): List<MessageTurnGroup> {
    if (isEmpty()) return emptyList()
    
    val groups = mutableListOf<MessageTurnGroup>()
    var currentGroup = mutableListOf<MessageNode>()
    var currentGroupRole: MessageRole? = null  // The "logical" role for grouping
    
    // Helper to determine the logical role for grouping:
    // TOOL messages belong to ASSISTANT turn, others are their own role
    fun getGroupingRole(role: MessageRole): MessageRole = when (role) {
        MessageRole.TOOL -> MessageRole.ASSISTANT
        else -> role
    }
    
    forEach { node ->
        val nodeRole = node.currentMessage.role
        val logicalRole = getGroupingRole(nodeRole)
        
        // Start a new group if logical role changes
        if (logicalRole != currentGroupRole && currentGroup.isNotEmpty()) {
            groups.add(MessageTurnGroup(currentGroup.toList(), currentGroupRole!!))
            currentGroup = mutableListOf()
        }
        currentGroup.add(node)
        currentGroupRole = logicalRole
    }
    
    if (currentGroup.isNotEmpty() && currentGroupRole != null) {
        groups.add(MessageTurnGroup(currentGroup.toList(), currentGroupRole!!))
    }
    
    return groups
}

/**
 * Build timeline entries from message parts.
 */
private fun buildTimelineEntries(parts: List<UIMessagePart>): List<TimelineEntry> {
    val entries = mutableListOf<TimelineEntry>()
    val memoryTools = setOf("create_memory", "edit_memory", "delete_memory")
    
    // Find tool results to match with tool calls
    val toolResults = parts.filterIsInstance<UIMessagePart.ToolResult>()
        .associateBy { it.toolCallId }
    
    parts.forEach { part ->
        when (part) {
            is UIMessagePart.Reasoning -> {
                val durationMs = if (part.finishedAt != null) {
                    (part.finishedAt!! - part.createdAt).inWholeMilliseconds
                } else 0L
                
                entries.add(TimelineEntry.Reasoning(
                    id = "reasoning_${entries.size}",
                    content = part.reasoning,
                    durationMs = durationMs,
                    title = null
                ))
            }
            is UIMessagePart.ToolCall -> {
                val result = toolResults[part.toolCallId]
                if (part.toolName in memoryTools) {
                    entries.add(buildMemoryTimelineEntry(part, result))
                } else {
                    val argumentsJson = result?.arguments ?: parseJsonObjectOrNull(part.arguments)
                    val resultJson = result?.content
                    entries.add(TimelineEntry.ToolCall(
                        id = "tool_${part.toolCallId}",
                        toolName = part.toolName,
                        displayName = getToolDisplayName(part.toolName),
                        argumentsText = part.arguments.take(200),
                        resultText = result?.content?.toString()?.take(500),
                        argumentsJson = argumentsJson,
                        resultJson = resultJson,
                        isLoading = result == null
                    ))
                }
            }
            // Don't add Reply entries to timeline - they're shown as bubbles
            else -> {}
        }
    }
    
    return entries
}

private fun buildMemoryTimelineEntry(
    call: UIMessagePart.ToolCall,
    result: UIMessagePart.ToolResult?
): TimelineEntry.MemoryAction {
    val operation = when (call.toolName) {
        "create_memory" -> MemoryOperation.CREATE
        "edit_memory" -> MemoryOperation.EDIT
        "delete_memory" -> MemoryOperation.DELETE
        else -> MemoryOperation.CREATE
    }
    val resultObj = result?.content as? JsonObject
    val argsObj = (result?.arguments as? JsonObject) ?: parseJsonObjectOrNull(call.arguments)

    val memoryId = resultObj?.get("id")?.jsonPrimitiveOrNull?.intOrNull
        ?: argsObj?.get("id")?.jsonPrimitiveOrNull?.intOrNull
    val content = resultObj?.get("content")?.jsonPrimitiveOrNull?.contentOrNull
        ?: argsObj?.get("content")?.jsonPrimitiveOrNull?.contentOrNull
    val previousContent = resultObj?.get("before_content")?.jsonPrimitiveOrNull?.contentOrNull
    val memoryType = resultObj?.get("type")?.jsonPrimitiveOrNull?.intOrNull
    val timestamp = resultObj?.get("timestamp")?.jsonPrimitiveOrNull?.longOrNull

    return TimelineEntry.MemoryAction(
        id = "memory_${call.toolCallId}",
        toolName = call.toolName,
        operation = operation,
        memoryId = memoryId,
        content = content,
        previousContent = previousContent,
        memoryType = memoryType,
        timestamp = timestamp,
        isLoading = result == null
    )
}

private fun parseJsonObjectOrNull(raw: String): JsonObject? {
    return runCatching { JsonInstant.parseToJsonElement(raw).jsonObject }.getOrNull()
}

/**
 * Get display name for a tool.
 */
private fun getToolDisplayName(toolName: String): String {
    return when (toolName) {
        "search_web" -> "Searching web"
        "scrape_web" -> "Reading page"
        "eval_python" -> "Running Python"
        "pip_install" -> "Installing packages"
        "write_sandbox_file" -> "Writing file"
        "read_sandbox_file" -> "Reading file"
        "list_sandbox_files" -> "Listing files"
        "delete_sandbox_file" -> "Deleting file"
        "create_memory" -> "Creating memory"
        "edit_memory" -> "Editing memory"
        "delete_memory" -> "Deleting memory"
        else -> toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

/**
 * Determine the current activity state from message parts.
 */
@Composable
private fun deriveActivityState(
    parts: List<UIMessagePart>,
    loading: Boolean
): ActivityState {
    val toolResults = parts.filterIsInstance<UIMessagePart.ToolResult>()
        .associateBy { it.toolCallId }
    
    val reasoningParts = parts.filterIsInstance<UIMessagePart.Reasoning>()
    val toolCalls = parts.filterIsInstance<UIMessagePart.ToolCall>()
    
    // Only count text AFTER the last tool-related part as "currently replying"
    // This prevents text from before tool calls (e.g. "Let me run that for you") 
    // from causing a "Replying" state during/between tool calls
    val lastToolIndex = parts.indexOfLast { it is UIMessagePart.ToolCall || it is UIMessagePart.ToolResult }
    val hasRecentText = parts.drop(lastToolIndex + 1)
        .filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }
        
    if (!loading) {
        // Generation complete - determine what to show based on activities
        val totalReasoningMs = reasoningParts.sumOf { r ->
            if (r.finishedAt != null) {
                (r.finishedAt!! - r.createdAt).inWholeMilliseconds
            } else 0L
        }
        
        // Group tools by CATEGORY (Python, Search, etc.) not individual tool names
        val toolCategories = toolCalls.map { categorizeToolName(it.toolName) }.distinct()
        
        val hasReasoning = totalReasoningMs > 0
        val hasTools = toolCategories.isNotEmpty()
        
        // Count distinct activity categories (not individual tools)
        val activityCount = (if (hasReasoning) 1 else 0) + toolCategories.size
        
        return when {
            activityCount == 0 -> ActivityState.Hidden  // No activities, hide pill
            activityCount == 1 && hasReasoning -> ActivityState.CompletedSingle(
                type = ActivityType.REASONING,
                durationMs = totalReasoningMs
            )
            activityCount == 1 && hasTools -> ActivityState.CompletedSingle(
                type = toolCategories.first(),
                toolName = toolCalls.first().toolName,
                displayName = getToolDisplayName(toolCalls.first().toolName),
                count = toolCalls.size  // Pass total count of tool calls
            )
            else -> ActivityState.CompletedMultiple(
                reasoningDurationMs = if (hasReasoning) totalReasoningMs else null,
                toolsUsed = toolCalls.map { it.toolName }.distinct()
            )
        }
    }
    
    // Check for active reasoning
    val activeReasoning = reasoningParts.lastOrNull { it.finishedAt == null }
    if (activeReasoning != null) {
        return ActivityState.Reasoning(startTimeMs = activeReasoning.createdAt.toEpochMilliseconds())
    }
    
    // Check for active tool calls (tool call without matching result)
    val activeTool = toolCalls.lastOrNull { toolResults[it.toolCallId] == null }
    if (activeTool != null) {
        return ActivityState.ToolUse(
            toolName = activeTool.toolName,
            displayName = getToolDisplayName(activeTool.toolName),
            startTimeMs = System.currentTimeMillis()
        )
    }
    
    // Check if we have any text AFTER the last tool activity
    if (hasRecentText) {
        // Text is being generated after all tools completed - show "Replying" state
        return ActivityState.Replying
    }
    
    return ActivityState.Waiting
}

/**
 * Redesigned ChatMessage component for a GROUP of consecutive messages.
 * 
 * For user message turns: Right-aligned bubbles, long-press for menu
 * For assistant message turns: 
 *   - Name + Avatar row at the top
 *   - Activity Pill below name
 *   - Stacked message bubbles with grouped corners
 *   - Token stats and action buttons at bottom
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageTurn(
    group: MessageTurnGroup,
    isLastTurn: Boolean,
    onCitationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    onFork: (MessageNode) -> Unit = {},
    onRegenerate: (MessageNode) -> Unit = {},
    onEdit: (MessageNode) -> Unit = {},
    onShare: (MessageNode) -> Unit = {},
    onDelete: (MessageNode) -> Unit = {},
    onUpdate: (MessageNode) -> Unit = {},
    showRegenerate: Boolean,
    onEditLorebookEntry: ((me.rerere.ai.ui.UsedLorebookEntry) -> Unit)? = null,
    onModeClick: ((me.rerere.ai.ui.UsedMode) -> Unit)? = null,
    onMemoryClick: ((me.rerere.ai.ui.UsedMemory) -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * effectiveDisplay.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * effectiveDisplay.fontSizeRatio
    )
    val configuration = LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.85f).dp
    
    // State for sheets
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    var showTimelineSheet by remember { mutableStateOf(false) }
    var initialTimelineExpandedType by remember { mutableStateOf<ActivityType?>(null) }
    var showUserDropdown by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var showUserToolbar by remember { mutableStateOf(false) }  // User message toolbar visibility
    
    // Activity state from ALL nodes in the group
    // For multi-node turns (with tools), the current generation is on the last node
    val activityState = deriveActivityState(group.allParts, loading && isLastTurn)
    
    // Timeline entries from all parts - computed fresh to avoid stale data
    val timelineEntries = buildTimelineEntries(group.allParts)

    // Actions should target the visible assistant content node instead of blindly using lastNode,
    // because the last node in a turn can be a tool node.
    val actionTargetNode = remember(group) {
        group.filteredNodes
            .asReversed()
            .firstOrNull { node ->
                node.currentMessage.parts.any { part ->
                    part is UIMessagePart.Text || part is UIMessagePart.Reasoning || part is UIMessagePart.Thinking
                }
            }
            ?: group.filteredNodes.lastOrNull()
            ?: group.lastNode
    }
    
    ProvideTextStyle(textStyle) {
        when (group.role) {
            MessageRole.USER -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                UserMessageTurn(
                    group = group,
                    assistant = assistant,
                    maxWidth = maxBubbleWidth,
                    showToolbar = showUserToolbar,
                    onToggleToolbar = { showUserToolbar = !showUserToolbar },
                    onCopy = { context.copyMessageToClipboard(group.lastNode.currentMessage) },
                    onRegenerate = { onRegenerate(group.lastNode) },
                    onOpenMenu = { showActionsSheet = true },
                    showRegenerate = showRegenerate,
                    modifier = modifier
                )
            }
            
            MessageRole.ASSISTANT -> {
                AssistantMessageTurn(
                    group = group,
                    assistant = assistant,
                    model = model,
                    activityState = activityState,
                    loading = loading && isLastTurn,
                    isLastTurn = isLastTurn,
                    actionsExpanded = actionsExpanded,
                    maxWidth = maxBubbleWidth,
                    showTokenUsage = effectiveDisplay.showTokenUsage,
                    showAssistantBubbles = effectiveDisplay.showAssistantBubbles,
                    onCitationClick = onCitationClick,
                    onActivityPillClick = { type ->
                        initialTimelineExpandedType = type
                        showTimelineSheet = true
                    },
                    onBubbleClick = {
                        if (isLastTurn) {
                            showActionsSheet = true
                        } else {
                            actionsExpanded = !actionsExpanded
                        }
                    },
                    onRegenerate = { onRegenerate(group.lastNode) },
                    onUpdate = onUpdate,
                    onOpenActionSheet = { showActionsSheet = true },
                    showRegenerate = showRegenerate,
                    onEditLorebookEntry = onEditLorebookEntry,
                    onModeClick = onModeClick,
                    onMemoryClick = onMemoryClick,
                    modifier = modifier
                )
            }
            
            else -> { /* System messages not rendered */ }
        }
    }
    
    // Sheets
    if (showTimelineSheet) {
        ActivityTimelineSheet(
            entries = timelineEntries,
            onDismissRequest = { showTimelineSheet = false },
            initialExpandedType = initialTimelineExpandedType,
            assistantId = assistant?.id?.toString()
        )
    }
    
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = actionTargetNode.currentMessage,
            onEdit = { onEdit(actionTargetNode) },
            onDelete = { onDelete(actionTargetNode) },
            onShare = { onShare(actionTargetNode) },
            onFork = { onFork(actionTargetNode) },
            model = model,
            onSelectAndCopy = { showSelectCopySheet = true },
            onWebViewPreview = { },
            onDismissRequest = { showActionsSheet = false }
        )
    }
    
    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = actionTargetNode.currentMessage,
            onDismissRequest = { showSelectCopySheet = false }
        )
    }
}

/**
 * User message turn - right-aligned stacked bubbles.
 * Tap to show/hide action toolbar.
 */
@Composable
private fun UserMessageTurn(
    group: MessageTurnGroup,
    assistant: Assistant?,
    maxWidth: androidx.compose.ui.unit.Dp,
    showToolbar: Boolean,
    onToggleToolbar: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenMenu: () -> Unit,
    showRegenerate: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPremiumHaptics()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Collect all images from all nodes
        val allImages = group.nodes.flatMap { node ->
            node.currentMessage.parts.filterIsInstance<UIMessagePart.Image>()
        }
        
        // Display images above the text bubbles
        if (allImages.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                allImages.fastForEach { image ->
                    ZoomableAsyncImage(
                        model = image.url,
                        contentDescription = null,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .height(72.dp)
                    )
                }
            }
        }
        
        // Message bubbles
        group.nodes.forEachIndexed { nodeIndex, node ->
            val textParts = node.currentMessage.parts.filterIsInstance<UIMessagePart.Text>()
            textParts.forEachIndexed { partIndex, part ->
                // Calculate bubble position based on overall position in group
                val isFirst = nodeIndex == 0 && partIndex == 0
                val isLast = nodeIndex == group.nodes.lastIndex && partIndex == textParts.lastIndex
                val totalBubbles = group.nodes.sumOf { n -> 
                    n.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().size 
                }
                val position = when {
                    totalBubbles == 1 -> BubblePosition.SINGLE
                    isFirst -> BubblePosition.FIRST
                    isLast -> BubblePosition.LAST
                    else -> BubblePosition.MIDDLE
                }
                
                GroupedMessageBubble(
                    position = position,
                    role = BubbleRole.USER,
                    modifier = Modifier.widthIn(max = maxWidth),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onToggleToolbar()
                    }
                ) {
                    MarkdownBlock(
                        content = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = true,
                        ),
                        onClickCitation = {}
                    )
                }
            }
        }
        
        // Toolbar - appears on tap
        AnimatedVisibility(
            visible = showToolbar,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeOut()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // Copy button
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            haptics.perform(HapticPattern.Pop)
                            onCopy()
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Regenerate button
                if (showRegenerate) {
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable {
                                haptics.perform(HapticPattern.Pop)
                                onRegenerate()
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Refresh,
                            contentDescription = "Regenerate",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // More options button
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            haptics.perform(HapticPattern.Pop)
                            onOpenMenu()
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.MoreHoriz,
                        contentDescription = "More Options",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Assistant message turn - name + avatar at top, activity pill, stacked bubbles.
 */
@Composable
private fun AssistantMessageTurn(
    group: MessageTurnGroup,
    assistant: Assistant?,
    model: Model?,
    activityState: ActivityState,
    loading: Boolean,
    isLastTurn: Boolean,
    actionsExpanded: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    showTokenUsage: Boolean,
    showAssistantBubbles: Boolean,
    onCitationClick: (String) -> Unit,
    onActivityPillClick: (ActivityType?) -> Unit,
    onBubbleClick: () -> Unit,
    onRegenerate: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    onOpenActionSheet: () -> Unit,
    showRegenerate: Boolean,
    onEditLorebookEntry: ((me.rerere.ai.ui.UsedLorebookEntry) -> Unit)?,
    onModeClick: ((me.rerere.ai.ui.UsedMode) -> Unit)?,
    onMemoryClick: ((me.rerere.ai.ui.UsedMemory) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)
    val showIcon = effectiveDisplay.showModelIcon
    val showModelName = effectiveDisplay.showModelName
    val haptics = rememberPremiumHaptics()
    val showName = showModelName && (!isLastTurn || !loading)
    val nameAlpha by animateFloatAsState(
        targetValue = if (showName) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "assistant_name_alpha"
    )
    val handleBubbleClick = {
        haptics.perform(HapticPattern.Pop)
        onBubbleClick()
    }
    
    // Get avatar info
    val avatarName = assistant?.name?.ifEmpty { null } ?: model?.displayName ?: "Assistant"
    val avatarValue = assistant?.avatar ?: Avatar.Dummy
    
    // Check if there's interesting activity (reasoning or tools)
    val hasInterestingActivity = activityState !is ActivityState.Hidden
    
    // Collect all text parts from filtered nodes (only nodes matching active versionTag)
    val allTextBubbles = mutableListOf<Pair<MessageNode, UIMessagePart.Text>>()
    group.filteredNodes.forEach { node ->
        node.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().forEach { part ->
            if (part.text.isNotBlank()) {
                allTextBubbles.add(node to part)
            }
        }
    }
    
    // Consistent spacing between all elements
    val elementSpacing = 4.dp
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f)
            ),
        verticalArrangement = Arrangement.spacedBy(
            if (showAssistantBubbles) elementSpacing else 3.dp
        )
    ) {
        if (showAssistantBubbles) {
            // Layout varies based on whether there's an activity bar
            if (hasInterestingActivity) {
                // WITH ACTIVITIES:
                // [Name] (if enabled)
                // [Avatar] [Pills row]
                // [Full-width bubble]
                
                // Name above pills (only if enabled)
                if (showModelName) {
                    Text(
                        text = avatarName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { alpha = nameAlpha }
                    )
                }
                
                // Avatar + Pills row
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(elementSpacing)
                ) {
                    if (showIcon) {
                        UIAvatar(
                            name = avatarName,
                            modifier = Modifier.size(36.dp),
                            value = avatarValue,
                            loading = loading,
                        )
                    }

                    ActivityPillRow(
                        state = activityState,
                        onClick = { type ->
                            haptics.perform(HapticPattern.Pop)
                            onActivityPillClick(type)
                        },
                        connectsToBubbleBelow = false,  // Bubbles are separate - fully rounded
                        modifier = Modifier.height(36.dp)
                    )
                }
            } else {
                // WITHOUT ACTIVITIES:
                // [Avatar] [Name] (side by side)
                // [Full-width bubble]

                if (showIcon || showModelName) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showIcon) {
                            UIAvatar(
                                name = avatarName,
                                modifier = Modifier.size(36.dp),
                                value = avatarValue,
                                loading = loading,
                            )
                        }
                        if (showModelName) {
                            Text(
                                text = avatarName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { alpha = nameAlpha }
                            )
                        }
                    }
                }
            }
            
            // Message bubbles - full width, standard bubble positions (no connection to pills)
            allTextBubbles.forEachIndexed { index, (node, part) ->
                val position = when {
                    allTextBubbles.size == 1 -> BubblePosition.SINGLE
                    index == 0 -> BubblePosition.FIRST
                    index == allTextBubbles.lastIndex -> BubblePosition.LAST
                    else -> BubblePosition.MIDDLE
                }
                
                GroupedMessageBubble(
                    position = position,
                    role = BubbleRole.ASSISTANT,
                    modifier = Modifier.widthIn(max = maxWidth),
                    onClick = handleBubbleClick
                ) {
                    MarkdownBlock(
                        content = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.ASSISTANT,
                            visual = true,
                        ),
                        onClickCitation = { id -> onCitationClick(id) }
                    )
                }
            }
        } else {
            // No bubbles for characters:
            // [Avatar] [Name] (side by side)
            // [Activity pills] below avatar row
            // [Full-width text]

            if (showIcon || showModelName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showIcon) {
                        UIAvatar(
                            name = avatarName,
                            modifier = Modifier.size(36.dp),
                            value = avatarValue,
                            loading = loading,
                        )
                    }
                    if (showModelName) {
                        Text(
                            text = avatarName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { alpha = nameAlpha }
                        )
                    }
                }
            }

            if (activityState !is ActivityState.Hidden) {
                ActivityPillRow(
                    state = activityState,
                    onClick = { type ->
                        haptics.perform(HapticPattern.Pop)
                        onActivityPillClick(type)
                    },
                    connectsToBubbleBelow = false,
                    modifier = Modifier.height(36.dp)
                )
            }

            allTextBubbles.forEach { (_, part) ->
                MarkdownBlock(
                    content = part.text.replaceRegexes(
                        assistant = assistant,
                        scope = AssistantAffectScope.ASSISTANT,
                        visual = true,
                    ),
                    onClickCitation = { id -> onCitationClick(id) },
                    modifier = Modifier.clickable { handleBubbleClick() }
                )
            }
        }
        
        // Token statistics - combined for all messages in group
        if (showTokenUsage && group.combinedUsage != null && !loading) {
            TokenStatisticsInline(
                usage = group.combinedUsage!!,
                generationDurationMs = group.combinedGenerationDurationMs,
                modifier = Modifier
            )
        }
        
        // Action buttons
        val showActions = !loading && (isLastTurn || actionsExpanded)
        
        AnimatedVisibility(
            visible = showActions,
            enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) +
                    slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } +
                    fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
            exit = shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) +
                   slideOutVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it } +
                   fadeOut()
        ) {
            ChatMessageActionButtons(
                message = group.lastNode.currentMessage,
                onRegenerate = onRegenerate,
                node = group.nodeWithMostVersions,
                onUpdate = onUpdate,
                showRegenerate = showRegenerate,
                onOpenActionSheet = onOpenActionSheet,
                onEditLorebookEntry = onEditLorebookEntry,
                onModeClick = onModeClick,
                onMemoryClick = onMemoryClick,
            )
        }
    }
}

/**
 * Inline token statistics display.
 */
@Composable
private fun TokenStatisticsInline(
    usage: TokenUsage,
    generationDurationMs: Long?,
    modifier: Modifier = Modifier
) {
    val grayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    
    // Calculate tokens per second
    val tokensPerSecond: Float? = generationDurationMs?.let { durationMs ->
        if (durationMs > 0) {
            (usage.completionTokens / (durationMs / 1000.0)).toFloat()
        } else null
    }
    
    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sent tokens
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "Sent",
                modifier = Modifier.size(14.dp),
                tint = grayColor
            )
            Text(
                text = buildString {
                    append("${usage.promptTokens.formatNumber()} tokens")
                    if (usage.cachedTokens > 0) {
                        append(" (${usage.cachedTokens.formatNumber()} cached)")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = grayColor
            )
        }
        
        // Received tokens
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = "Received",
                modifier = Modifier.size(14.dp),
                tint = grayColor
            )
            Text(
                text = "${usage.completionTokens.formatNumber()} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = grayColor
            )
        }
        
        // Tokens per second
        if (tokensPerSecond != null && tokensPerSecond > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Speed",
                    modifier = Modifier.size(14.dp),
                    tint = grayColor
                )
                Text(
                    text = "%.1f tok/s".format(tokensPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = grayColor
                )
            }
        }
    }
}
