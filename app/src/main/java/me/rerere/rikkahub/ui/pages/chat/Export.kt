package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.ai.ui.toSortedMessageParts
import me.rerere.common.android.appTempFolder
import me.rerere.common.platform.android.AndroidPlatformMediaEncoder
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.chat.ActivityPillRow
import me.rerere.rikkahub.ui.components.chat.ActivityState
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.MemoryOperation
import me.rerere.rikkahub.ui.components.chat.TimelineEntry
import me.rerere.rikkahub.ui.components.chat.buildTimelineEntries
import me.rerere.rikkahub.ui.components.chat.deriveActivityState
import me.rerere.rikkahub.ui.components.chat.getBubblePosition
import me.rerere.rikkahub.ui.components.chat.getTimelineIcon
import me.rerere.rikkahub.ui.components.chat.getTimelineLabel
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.BitmapComposer
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.components.ui.TextAvatar
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.writeClipboardText
import okio.buffer
import okio.sink
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val EXPORT_BRAND = "LastChat"
private const val EXPORT_WIDTH_DP = 540
private const val EXPORT_MAX_TEXT_CHARS = 1400
private const val EXPORT_IMAGE_DIAGNOSTIC_CHARS = 560
private const val DEFAULT_TROUBLESHOOTING_EMPTY_MESSAGE = "No stored API error details for this selection."

data class ChatExportOptions(
    val includeReasoning: Boolean = false,
    val includeToolCalls: Boolean = false,
    val includeTroubleshooting: Boolean = false,
)

internal data class ChatExportTurn(
    val role: MessageRole,
    val messages: List<UIMessage>,
) {
    val parts: List<UIMessagePart> = messages.flatMap { it.parts }
    val annotations = messages.flatMap { it.annotations }
    val modelId = messages.asReversed().firstNotNullOfOrNull { it.modelId }
    val usage = messages.asReversed().firstNotNullOfOrNull { it.usage }
    val generationDurationMs = messages.mapNotNull { it.generationDurationMs }.sum().takeIf { it > 0 }
}

internal data class ExportDiagnostic(
    val label: String,
    val value: String,
    val isError: Boolean = false,
)

@Composable
fun ChatExportSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    conversation: Conversation,
    selectedMessages: List<UIMessage>
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics()
    val isDarkMode = LocalDarkMode.current
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val sheetContentColor = MaterialTheme.colorScheme.onSurface
    val sheetSupportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val optionContainerColor = if (isDarkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    var exportOptions by remember(visible) { mutableStateOf(ChatExportOptions()) }

    if (visible) {
        ModalBottomSheet(
            containerColor = sheetContainerColor,
            contentColor = sheetContentColor,
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.chat_page_export_share_via),
                        style = MaterialTheme.typography.titleLarge,
                        color = sheetContentColor
                    )
                    Text(
                        text = stringResource(
                            id = R.string.chat_page_export_selected_count,
                            selectedMessages.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sheetSupportingColor
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val imageSuccessMessage = stringResource(
                        id = R.string.chat_page_export_success,
                        stringResource(R.string.chat_page_export_image)
                    )
                    ShareFormatCard(
                        icon = Icons.Rounded.Image,
                        title = stringResource(id = R.string.chat_page_export_image),
                        description = stringResource(id = R.string.chat_page_export_image_desc),
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            scope.launch {
                                runCatching {
                                    exportToImage(
                                        context = context,
                                        scope = scope,
                                        density = density,
                                        conversation = conversation,
                                        messages = selectedMessages,
                                        settings = settings,
                                        options = exportOptions
                                    )
                                }.onSuccess {
                                    toaster.show(imageSuccessMessage, type = ToastType.Success)
                                    onDismissRequest()
                                }.onFailure {
                                    it.printStackTrace()
                                    toaster.show(
                                        message = context.getString(
                                            R.string.chat_page_export_image_failed,
                                            it.message ?: ""
                                        ),
                                        type = ToastType.Error
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = optionContainerColor,
                        contentColor = sheetContentColor,
                        supportingColor = sheetSupportingColor
                    )

                    val markdownSuccessMessage = stringResource(
                        id = R.string.chat_page_export_success,
                        stringResource(R.string.chat_page_export_markdown)
                    )
                    ShareFormatCard(
                        icon = Icons.Rounded.Description,
                        title = stringResource(id = R.string.chat_page_export_markdown),
                        description = stringResource(id = R.string.chat_page_export_markdown_desc),
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            scope.launch {
                                runCatching {
                                    exportToMarkdown(context, conversation, selectedMessages, exportOptions)
                                }.onSuccess {
                                    toaster.show(markdownSuccessMessage, type = ToastType.Success)
                                    onDismissRequest()
                                }.onFailure {
                                    it.printStackTrace()
                                    toaster.show(
                                        message = context.getString(
                                            R.string.chat_page_export_markdown_failed,
                                            it.message ?: ""
                                        ),
                                        type = ToastType.Error
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = optionContainerColor,
                        contentColor = sheetContentColor,
                        supportingColor = sheetSupportingColor
                    )

                    val copySuccessMessage = stringResource(R.string.chat_page_export_copied)
                    ShareFormatCard(
                        icon = Icons.Rounded.ContentCopy,
                        title = stringResource(id = R.string.chat_page_export_copy),
                        description = stringResource(id = R.string.chat_page_export_copy_desc),
                        onClick = {
                            haptics.perform(HapticPattern.Success)
                            context.writeClipboardText(
                                buildMarkdownExportText(
                                    conversation = conversation,
                                    messages = selectedMessages,
                                    options = exportOptions
                                )
                            )
                            toaster.show(copySuccessMessage, type = ToastType.Success)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = optionContainerColor,
                        contentColor = sheetContentColor,
                        supportingColor = sheetSupportingColor
                    )
                }

                Card(
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = optionContainerColor,
                        contentColor = sheetContentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = sheetContentColor,
                                supportingColor = sheetSupportingColor,
                                leadingIconColor = sheetContentColor
                            ),
                            leadingContent = {
                                Icon(Icons.Rounded.Tune, contentDescription = null)
                            },
                            headlineContent = {
                                Text(stringResource(R.string.chat_page_export_tweaks))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.chat_page_export_tweaks_desc))
                            }
                        )
                        ExportToggleRow(
                            title = stringResource(R.string.chat_page_export_include_reasoning),
                            description = stringResource(R.string.chat_page_export_include_reasoning_desc),
                            checked = exportOptions.includeReasoning,
                            onCheckedChange = {
                                exportOptions = exportOptions.copy(includeReasoning = it)
                            }
                        )
                        ExportToggleRow(
                            title = stringResource(R.string.chat_page_export_include_tool_calls),
                            description = stringResource(R.string.chat_page_export_include_tool_calls_desc),
                            checked = exportOptions.includeToolCalls,
                            onCheckedChange = {
                                exportOptions = exportOptions.copy(includeToolCalls = it)
                            }
                        )
                        ExportToggleRow(
                            title = stringResource(R.string.chat_page_export_include_troubleshooting),
                            description = stringResource(R.string.chat_page_export_include_troubleshooting_desc),
                            checked = exportOptions.includeTroubleshooting,
                            onCheckedChange = {
                                exportOptions = exportOptions.copy(includeTroubleshooting = it)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareFormatCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "share_format_card_scale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .widthIn(min = 108.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = supportingColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExportToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            HapticSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

internal fun buildExportTurns(messages: List<UIMessage>): List<ChatExportTurn> {
    val turns = mutableListOf<ChatExportTurn>()
    var currentRole: MessageRole? = null
    var currentMessages = mutableListOf<UIMessage>()

    fun flush() {
        val role = currentRole ?: return
        if (currentMessages.isNotEmpty()) {
            turns.add(ChatExportTurn(role = role, messages = currentMessages.toList()))
        }
        currentRole = null
        currentMessages = mutableListOf()
    }

    messages.forEach { message ->
        when (message.role) {
            MessageRole.SYSTEM -> Unit
            MessageRole.USER -> {
                flush()
                currentRole = MessageRole.USER
                currentMessages.add(message)
                flush()
            }

            MessageRole.ASSISTANT -> {
                if (currentRole != MessageRole.ASSISTANT) {
                    flush()
                    currentRole = MessageRole.ASSISTANT
                }
                currentMessages.add(message)
            }

            MessageRole.TOOL -> {
                if (currentRole != MessageRole.ASSISTANT) {
                    flush()
                    currentRole = MessageRole.ASSISTANT
                }
                currentMessages.add(message)
            }
        }
    }
    flush()
    return turns.filterNot { it.parts.isEmptyUIMessage() && it.parts.none { part -> part is UIMessagePart.ToolResult } }
}

internal fun buildMarkdownExportText(
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ChatExportOptions = ChatExportOptions(),
): String = buildString {
    val mediaEncoder = AndroidPlatformMediaEncoder()
    append("# ${conversation.title}\n\n")
    append("*Exported on ${LocalDateTime.now().toLocalString()}*\n\n")

    buildExportTurns(messages).forEach { turn ->
        append(if (turn.role == MessageRole.USER) "**User**" else "**Assistant**")
        appendLine(":")
        appendLine()

        turn.parts.toSortedMessageParts().forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    if (part.text.isNotBlank()) {
                        append(part.text.trim())
                        appendLine()
                        appendLine()
                    }
                }

                is UIMessagePart.Image -> {
                    append("![Image](${mediaEncoder.encodeImage(part.url).getOrNull()})")
                    appendLine()
                    appendLine()
                }

                else -> Unit
            }
        }

        if (options.includeReasoning) {
            appendReasoningMarkdown(turn)
        }
        if (options.includeToolCalls) {
            appendToolMarkdown(turn)
        }
        if (options.includeTroubleshooting) {
            appendTroubleshootingMarkdown(turn)
        }

        appendLine("---")
        appendLine()
    }
}

private fun StringBuilder.appendReasoningMarkdown(turn: ChatExportTurn) {
    val reasoningParts = turn.parts.filterIsInstance<UIMessagePart.Reasoning>()
        .filter { it.reasoning.isNotBlank() }
    if (reasoningParts.isEmpty()) return

    appendLine("### Reasoning")
    reasoningParts.forEach { reasoning ->
        reasoning.title?.takeIf { it.isNotBlank() }?.let {
            appendLine("**$it**")
            appendLine()
        }
        reasoning.reasoning.lines()
            .filter { it.isNotBlank() }
            .forEach { appendLine("> ${redactTroubleshootingText(it)}") }
        appendLine()
    }
}

private fun StringBuilder.appendToolMarkdown(turn: ChatExportTurn) {
    val entries = buildTimelineEntries(parts = turn.parts, annotations = turn.annotations)
        .filter { it is TimelineEntry.ToolCall || it is TimelineEntry.MemoryAction || it is TimelineEntry.Ocr }
    if (entries.isEmpty()) return

    appendLine("### Activity")
    entries.forEach { entry ->
        appendLine("- **${entry.exportLabel()}**")
        when (entry) {
            is TimelineEntry.ToolCall -> {
                entry.argumentsText.takeIf { it.isNotBlank() }?.let {
                    appendLine("  - Arguments: ${redactTroubleshootingText(it).singleLinePreview(EXPORT_MAX_TEXT_CHARS)}")
                }
                entry.resultText?.takeIf { it.isNotBlank() }?.let {
                    appendLine("  - Result: ${redactTroubleshootingText(it).singleLinePreview(EXPORT_MAX_TEXT_CHARS)}")
                }
            }

            is TimelineEntry.MemoryAction -> {
                entry.previousContent?.takeIf { it.isNotBlank() }?.let {
                    appendLine("  - Before: ${redactTroubleshootingText(it).singleLinePreview(EXPORT_MAX_TEXT_CHARS)}")
                }
                entry.content?.takeIf { it.isNotBlank() }?.let {
                    appendLine("  - Content: ${redactTroubleshootingText(it).singleLinePreview(EXPORT_MAX_TEXT_CHARS)}")
                }
            }

            is TimelineEntry.Ocr -> {
                entry.fileName?.takeIf { it.isNotBlank() }?.let { appendLine("  - File: $it") }
                if (entry.pageNumbers.isNotEmpty()) {
                    appendLine("  - Pages: ${entry.pageNumbers.joinToString()}")
                }
            }

            else -> Unit
        }
    }
    appendLine()
}

private fun StringBuilder.appendTroubleshootingMarkdown(turn: ChatExportTurn) {
    val diagnostics = collectTroubleshootingDiagnostics(turn)
    appendLine("### Troubleshooting")
    if (diagnostics.isEmpty()) {
        appendLine("- ${troubleshootingEmptyMessage()}")
    } else {
        diagnostics.forEach { diagnostic ->
            appendLine(
                "- **${diagnostic.label}**: ${
                    diagnostic.value.singleLinePreview(EXPORT_MAX_TEXT_CHARS)
                }"
            )
        }
    }
    appendLine()
}

private suspend fun exportToMarkdown(
    context: Context,
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ChatExportOptions,
) {
    val filename = "chat-export-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.md"
    val markdown = buildMarkdownExportText(conversation, messages, options)

    val file = withContext(Dispatchers.IO) {
        val dir = context.appTempFolder
        val file = dir.resolve(filename)
        if (!file.exists()) {
            file.createNewFile()
        } else {
            file.delete()
            file.createNewFile()
        }
        file.sink().buffer().use {
            it.write(markdown.toByteArray())
        }
        file
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    shareFile(context, uri, "text/markdown")
}

private suspend fun exportToImage(
    context: Context,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ChatExportOptions,
) {
    val filename = "chat-export-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.png"
    val composer = BitmapComposer(scope)
    val activity = context.getActivity()
    if (activity == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_page_export_activity_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
        return
    }

    val bitmap = composer.composableToBitmap(
        activity = activity,
        width = EXPORT_WIDTH_DP.dp,
        screenDensity = density,
        content = {
            CompositionLocalProvider(LocalSettings provides settings) {
                ExportedChatImage(
                    conversation = conversation,
                    messages = messages,
                    options = options
                )
            }
        }
    )

    val file = withContext(Dispatchers.IO) {
        val dir = context.appTempFolder
        val file = dir.resolve(filename)
        if (!file.exists()) {
            file.createNewFile()
        } else {
            file.delete()
            file.createNewFile()
        }

        file.sink().buffer().outputStream().use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
        }
        file
    }

    try {
        context.exportImage(activity, bitmap, filename)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareFile(context, uri, "image/png")
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_page_export_image_failed, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    } finally {
        bitmap.recycle()
    }
}

@Composable
private fun ExportedChatImage(
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ChatExportOptions,
) {
    val navBackStack = rememberNavController()
    val highlighter = koinInject<Highlighter>()
    RikkahubTheme {
        CompositionLocalProvider(
            LocalNavController provides navBackStack,
            LocalHighlighter provides highlighter
        ) {
            Surface(modifier = Modifier.width(EXPORT_WIDTH_DP.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ExportHeader(conversation = conversation)
                    buildExportTurns(messages).forEach { turn ->
                        ExportedChatTurn(turn = turn, options = options)
                    }
                    Surface(
                        shape = AppShapes.CardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = "$EXPORT_BRAND - ${stringResource(R.string.export_image_warning)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportHeader(conversation: Conversation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_lastchat_foreground),
                contentDescription = stringResource(R.string.a11y_logo),
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${LocalDateTime.now().toLocalString()} - $EXPORT_BRAND",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExportedChatTurn(
    turn: ChatExportTurn,
    options: ChatExportOptions,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val model = turn.modelId?.let { settings.findModelById(it) }
    val provider = model?.findProvider(settings.providers)
    val iconLabel = when {
        model?.modelId?.isNotBlank() == true -> model.modelId
        model?.displayName?.isNotBlank() == true -> model.displayName
        else -> "AI"
    }
    val isUser = turn.role == MessageRole.USER
    val textParts = turn.parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }
    val imageParts = turn.parts.filterIsInstance<UIMessagePart.Image>().filter { it.url.isNotBlank() }
    val activityState = deriveActivityState(
        parts = turn.parts,
        annotations = turn.annotations,
        loading = false
    )
    val timelineEntries = buildTimelineEntries(
        parts = turn.parts,
        annotations = turn.annotations,
        loading = false
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (model != null) {
                    ModelIcon(model = model, provider = provider, modifier = Modifier.size(32.dp))
                } else {
                    TextAvatar(text = iconLabel, modifier = Modifier.size(32.dp))
                }
                Text(
                    text = iconLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activityState !is ActivityState.Hidden) {
                ActivityPillRow(
                    state = activityState,
                    onClick = {},
                    connectsToBubbleBelow = false,
                    modifier = Modifier.widthIn(max = (EXPORT_WIDTH_DP * 0.82f).dp),
                    reasoningPreviewEnabled = false,
                    maxBubbleWidth = (EXPORT_WIDTH_DP * 0.82f).dp
                )
            }
        }

        imageParts.forEach { part ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(part.url)
                    .allowHardware(false)
                    .crossfade(false)
                    .build(),
                contentDescription = stringResource(R.string.a11y_image),
                modifier = Modifier
                    .sizeIn(maxHeight = 300.dp)
                    .clip(AppShapes.MessageBubbleInner),
            )
        }

        textParts.forEachIndexed { index, part ->
            GroupedMessageBubble(
                position = getBubblePosition(index, textParts.size),
                role = if (isUser) BubbleRole.USER else BubbleRole.ASSISTANT,
                modifier = Modifier.widthIn(max = (EXPORT_WIDTH_DP * 0.82f).dp)
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    MarkdownBlock(
                        content = part.text.trimStart(),
                        paragraphSpacing = 12.dp
                    )
                }
            }
        }

        if (options.includeReasoning) {
            timelineEntries.filterIsInstance<TimelineEntry.Reasoning>().forEach {
                ExportTimelineEntryCard(entry = it, expanded = true)
            }
        }
        if (options.includeToolCalls) {
            timelineEntries
                .filter { it is TimelineEntry.ToolCall || it is TimelineEntry.MemoryAction || it is TimelineEntry.Ocr }
                .forEach { ExportTimelineEntryCard(entry = it, expanded = true) }
        }
        if (options.includeTroubleshooting) {
            ExportTroubleshootingCard(diagnostics = collectTroubleshootingDiagnostics(turn))
        }
    }
}

@Composable
private fun ExportTimelineEntryCard(
    entry: TimelineEntry,
    expanded: Boolean,
) {
    Surface(
        shape = if (expanded) AppShapes.CardMedium else AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = (EXPORT_WIDTH_DP * 0.82f).dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = getTimelineIcon(entry),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = getTimelineLabel(entry),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                (entry as? TimelineEntry.Reasoning)?.durationMs?.takeIf { it > 0 }?.let { duration ->
                    Text(
                        text = "(${(duration / 1000.0).formatSeconds()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }
            if (expanded) {
                ExportTimelineEntryDetails(entry = entry)
            }
        }
    }
}

@Composable
private fun ExportTimelineEntryDetails(entry: TimelineEntry) {
    when (entry) {
        is TimelineEntry.Reasoning -> {
            if (entry.content.isNotBlank()) {
                ExportDetailBlock(entry.content)
            }
        }

        is TimelineEntry.ToolCall -> {
            entry.argumentsText.takeIf { it.isNotBlank() }?.let {
                ExportField(
                    label = stringResource(R.string.activity_timeline_arguments),
                    value = redactTroubleshootingText(it).singleLinePreview(EXPORT_IMAGE_DIAGNOSTIC_CHARS),
                    monospace = true
                )
            }
            entry.resultText?.takeIf { it.isNotBlank() }?.let {
                ExportField(
                    label = stringResource(R.string.chat_message_tool_call_result),
                    value = redactTroubleshootingText(it).singleLinePreview(EXPORT_IMAGE_DIAGNOSTIC_CHARS),
                    monospace = true
                )
            }
        }

        is TimelineEntry.MemoryAction -> {
            val operation = when (entry.operation) {
                MemoryOperation.CREATE -> stringResource(R.string.chat_message_tool_create_memory)
                MemoryOperation.EDIT -> stringResource(R.string.chat_message_tool_edit_memory)
                MemoryOperation.DELETE -> stringResource(R.string.chat_message_tool_delete_memory)
            }
            ExportField(label = stringResource(R.string.activity_timeline_status), value = operation)
            entry.previousContent?.takeIf { it.isNotBlank() }?.let {
                ExportField(label = "Before", value = it.singleLinePreview(EXPORT_IMAGE_DIAGNOSTIC_CHARS))
            }
            entry.content?.takeIf { it.isNotBlank() }?.let {
                ExportField(label = "Content", value = it.singleLinePreview(EXPORT_IMAGE_DIAGNOSTIC_CHARS))
            }
        }

        is TimelineEntry.Ocr -> {
            entry.fileName?.takeIf { it.isNotBlank() }?.let {
                ExportField(label = stringResource(R.string.activity_timeline_file_name), value = it)
            }
            if (entry.pageNumbers.isNotEmpty()) {
                ExportField(
                    label = stringResource(R.string.activity_timeline_ocr_pages),
                    value = entry.pageNumbers.joinToString()
                )
            }
        }

        is TimelineEntry.Reply -> Unit
    }
}

@Composable
private fun ExportTroubleshootingCard(diagnostics: List<ExportDiagnostic>) {
    Surface(
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.widthIn(max = (EXPORT_WIDTH_DP * 0.82f).dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.chat_page_export_troubleshooting_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (diagnostics.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_page_export_no_stored_api_error_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                )
            } else {
                diagnostics.forEach { diagnostic ->
                    ExportField(
                        label = diagnostic.label,
                        value = diagnostic.value.singleLinePreview(EXPORT_IMAGE_DIAGNOSTIC_CHARS),
                        valueColor = if (diagnostic.isError) MaterialTheme.colorScheme.error else null,
                        monospace = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportDetailBlock(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMediumInner12,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        MarkdownBlock(
            content = content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ExportField(
    label: String,
    value: String,
    valueColor: Color? = null,
    monospace: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

internal fun collectTroubleshootingDiagnostics(turn: ChatExportTurn): List<ExportDiagnostic> {
    val diagnostics = mutableListOf<ExportDiagnostic>()

    turn.messages.forEachIndexed { index, message ->
        message.modelId?.let {
            diagnostics.add(ExportDiagnostic("message[$index].modelId", it.toString()))
        }
        message.usage?.let { usage ->
            diagnostics.add(ExportDiagnostic("message[$index].promptTokens", usage.promptTokens.toString()))
            diagnostics.add(ExportDiagnostic("message[$index].completionTokens", usage.completionTokens.toString()))
            if (usage.cachedTokens > 0) {
                diagnostics.add(ExportDiagnostic("message[$index].cachedTokens", usage.cachedTokens.toString()))
            }
            diagnostics.add(ExportDiagnostic("message[$index].totalTokens", usage.totalTokens.toString()))
        }
        message.generationDurationMs?.let {
            diagnostics.add(ExportDiagnostic("message[$index].generationMs", it.toString()))
        }
    }

    turn.parts.forEachIndexed { index, part ->
        part.metadata?.let { metadata ->
            diagnostics.add(
                ExportDiagnostic(
                    label = "part[$index].metadata",
                    value = metadata.redactedPretty(),
                    isError = metadata.containsTroubleshootingSignal()
                )
            )
        }
        when (part) {
            is UIMessagePart.ToolCall -> {
                diagnostics.add(
                    ExportDiagnostic(
                        label = "tool.${part.toolName}.approval",
                        value = part.approvalState::class.simpleName ?: part.approvalState.toString()
                    )
                )
                if (part.arguments.isNotBlank()) {
                    diagnostics.add(
                        ExportDiagnostic(
                            label = "tool.${part.toolName}.arguments",
                            value = redactTroubleshootingText(part.arguments),
                            isError = part.arguments.containsTroubleshootingSignal()
                        )
                    )
                }
            }

            is UIMessagePart.ToolResult -> {
                diagnostics.add(
                    ExportDiagnostic(
                        label = "tool.${part.toolName}.result",
                        value = part.content.redactedPretty(),
                        isError = part.content.containsTroubleshootingSignal()
                    )
                )
                val extracted = part.content.extractTroubleshootingFields("tool.${part.toolName}.result")
                diagnostics.addAll(extracted)
                diagnostics.addAll(part.arguments.extractTroubleshootingFields("tool.${part.toolName}.arguments"))
            }

            else -> Unit
        }
    }

    return diagnostics.distinctBy { it.label to it.value }
}

internal fun JsonElement.extractTroubleshootingFields(prefix: String): List<ExportDiagnostic> {
    val diagnostics = mutableListOf<ExportDiagnostic>()

    fun walk(path: String, element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) ->
                    val childPath = "$path.$key"
                    if (key.isTroubleshootingKey()) {
                        diagnostics.add(
                            ExportDiagnostic(
                                label = childPath,
                                value = value.redactedPretty(),
                                isError = key.isErrorKey() || value.containsTroubleshootingSignal()
                            )
                        )
                    }
                    walk(childPath, value)
                }
            }

            is JsonArray -> element.forEachIndexed { index, value -> walk("$path[$index]", value) }
            is JsonPrimitive -> Unit
        }
    }

    walk(prefix, this)
    return diagnostics
}

internal fun redactTroubleshootingText(value: String): String {
    var redacted = value
    secretInlinePatterns.forEach { pattern ->
        redacted = redacted.replace(pattern, "$1[redacted]")
    }
    return redacted
        .let { text ->
            Regex("\\S+").replace(text) { match ->
                if (match.value.isSecretLikeToken()) "[redacted]" else match.value
            }
        }
}

private fun JsonElement.redactedPretty(): String {
    return runCatching {
        JsonInstantPretty.encodeToString(JsonElement.serializer(), redactTroubleshootingJson(this))
    }.getOrElse {
        redactTroubleshootingText(toString())
    }
}

private fun redactTroubleshootingJson(element: JsonElement, key: String? = null): JsonElement {
    return when (element) {
        is JsonObject -> buildJsonObject {
            element.forEach { (childKey, value) ->
                put(childKey, redactTroubleshootingJson(value, childKey))
            }
        }

        is JsonArray -> buildJsonArray {
            element.forEach { add(redactTroubleshootingJson(it, key)) }
        }

        is JsonPrimitive -> {
            if (element.isString) {
                JsonPrimitive(redactTroubleshootingValue(key.orEmpty(), element.content))
            } else {
                element
            }
        }
    }
}

internal fun redactTroubleshootingValue(label: String, value: String): String {
    return if (label.isSecretKey() || value.isSecretLikeToken()) {
        "[redacted]"
    } else {
        redactTroubleshootingText(value)
    }
}

private fun JsonElement.containsTroubleshootingSignal(): Boolean {
    return when (this) {
        is JsonObject -> entries.any { (key, value) ->
            key.isTroubleshootingKey() || value.containsTroubleshootingSignal()
        }

        is JsonArray -> any { it.containsTroubleshootingSignal() }
        is JsonPrimitive -> {
            contentOrNull?.containsTroubleshootingSignal() == true
        }
    }
}

private fun String.containsTroubleshootingSignal(): Boolean {
    return contains("error", ignoreCase = true) ||
        contains("exception", ignoreCase = true) ||
        contains("timed out", ignoreCase = true) ||
        contains("unauthorized", ignoreCase = true) ||
        contains("forbidden", ignoreCase = true) ||
        contains("statusCode", ignoreCase = true) ||
        contains("status_code", ignoreCase = true) ||
        contains("exitCode", ignoreCase = true)
}

private fun String.isTroubleshootingKey(): Boolean {
    val normalized = replace("_", "").replace("-", "").lowercase()
    return normalized in troubleshootingKeys
}

private fun String.isErrorKey(): Boolean {
    val normalized = replace("_", "").replace("-", "").lowercase()
    return normalized in errorKeys
}

private fun String.isSecretKey(): Boolean {
    val normalized = lowercase()
    return secretKeyPattern.containsMatchIn(normalized)
}

private fun String.isSecretLikeToken(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return false
    if (trimmed.length >= 48 && trimmed.any { it.isDigit() } && trimmed.any { it.isLetter() }) return true
    return secretTokenPatterns.any { it.containsMatchIn(trimmed) }
}

private fun String.singleLinePreview(maxChars: Int): String {
    val oneLine = replace('\n', ' ').replace('\r', ' ').trim()
    return if (oneLine.length > maxChars) {
        oneLine.take(maxChars).trimEnd() + "..."
    } else {
        oneLine
    }
}

private fun TimelineEntry.exportLabel(): String {
    return when (this) {
        is TimelineEntry.Reasoning -> title ?: "Reasoning"
        is TimelineEntry.ToolCall -> displayName
        is TimelineEntry.MemoryAction -> when (operation) {
            MemoryOperation.CREATE -> "Created memory"
            MemoryOperation.EDIT -> "Updated memory"
            MemoryOperation.DELETE -> "Deleted memory"
        }

        is TimelineEntry.Ocr -> "OCR"
        is TimelineEntry.Reply -> "Reply"
    }
}

private fun Double.formatSeconds(): String {
    return if (this < 10) {
        String.format("%.1fs", this)
    } else {
        String.format("%.0fs", this)
    }
}

private fun troubleshootingEmptyMessage(): String {
    return DEFAULT_TROUBLESHOOTING_EMPTY_MESSAGE
}

private fun shareFile(context: Context, uri: Uri, mimeType: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent,
            context.getString(R.string.chat_page_export_share_via)
        )
    )
}

private val troubleshootingKeys = setOf(
    "error",
    "code",
    "status",
    "statuscode",
    "message",
    "type",
    "param",
    "exitcode",
    "stderr",
    "timedout",
)

private val errorKeys = setOf(
    "error",
    "stderr",
    "timedout",
)

private val secretKeyPattern = Regex(
    pattern = "(api[_-]?key|authorization|bearer|token|secret|password|credential)",
    option = RegexOption.IGNORE_CASE
)

private val secretInlinePatterns = listOf(
    Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+"),
    Regex("(?i)(authorization[\"'\\s:=]+)[A-Za-z0-9._\\-]+"),
    Regex("(?i)((?:api[_-]?key|token|secret|password)[\"'\\s:=]+)[^\\s,}\"']+"),
    Regex("(?i)(sk-(?:proj-)?)[A-Za-z0-9_\\-]+"),
)

private val secretTokenPatterns = listOf(
    Regex("(?i)^sk-(?:proj-)?[A-Za-z0-9_\\-]{12,}$"),
    Regex("(?i)^Bearer\\s+[A-Za-z0-9._\\-]{12,}$"),
)
