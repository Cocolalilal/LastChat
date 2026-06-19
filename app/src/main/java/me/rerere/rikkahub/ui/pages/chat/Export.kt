package me.rerere.rikkahub.ui.pages.chat
import me.rerere.ai.ui.*





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
import androidx.compose.material3.Button
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
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Public
import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart


import me.rerere.common.android.appTempFolder
import me.rerere.common.platform.android.AndroidPlatformMediaEncoder
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.chat.BubbleRole
import me.rerere.rikkahub.ui.components.chat.GroupedMessageBubble
import me.rerere.rikkahub.ui.components.chat.getBubblePosition
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.BitmapComposer
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.components.ui.TextAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.writeClipboardText
import okio.buffer
import okio.sink
import org.koin.compose.koinInject
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private const val EXPORT_BRAND = "LastChat"

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
    var imageExportOptions by remember { mutableStateOf(ImageExportOptions()) }

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.chat_page_export_share_via),
                    style = MaterialTheme.typography.titleLarge,
                    color = sheetContentColor
                )
                Text(
                    text = stringResource(id = R.string.chat_page_export_format),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetSupportingColor
                )

                val markdownSuccessMessage =
                    stringResource(
                        id = R.string.chat_page_export_success,
                        stringResource(R.string.chat_page_export_markdown)
                    )
                ShareOptionCard(
                    icon = Icons.Rounded.Description,
                    title = stringResource(id = R.string.chat_page_export_markdown),
                    description = stringResource(id = R.string.chat_page_export_markdown_desc),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        exportToMarkdown(context, conversation, selectedMessages)
                        toaster.show(
                            markdownSuccessMessage,
                            type = ToastType.Success
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = optionContainerColor,
                    contentColor = sheetContentColor,
                    supportingColor = sheetSupportingColor
                )

                val copySuccessMessage = stringResource(R.string.chat_page_export_copied)
                ShareOptionCard(
                    icon = Icons.Rounded.ContentCopy,
                    title = stringResource(id = R.string.chat_page_export_copy),
                    description = stringResource(id = R.string.chat_page_export_copy_desc),
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        context.writeClipboardText(buildMarkdownExportText(conversation, selectedMessages))
                        toaster.show(
                            copySuccessMessage,
                            type = ToastType.Success
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = optionContainerColor,
                    contentColor = sheetContentColor,
                    supportingColor = sheetSupportingColor
                )

                val imageSuccessMessage =
                    stringResource(
                        id = R.string.chat_page_export_success,
                        stringResource(R.string.chat_page_export_image)
                    )
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
                            headlineContent = {
                                Text(stringResource(id = R.string.chat_page_export_image))
                            },
                            supportingContent = {
                                Text(stringResource(id = R.string.chat_page_export_image_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.Image, contentDescription = null)
                            }
                        )

                        HorizontalDivider()

                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = sheetContentColor
                            ),
                            headlineContent = { Text(stringResource(R.string.chat_page_export_image_expand_reasoning)) },
                            trailingContent = {
                                HapticSwitch(
                                    checked = imageExportOptions.expandReasoning,
                                    onCheckedChange = {
                                        imageExportOptions = imageExportOptions.copy(expandReasoning = it)
                                    }
                                )
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
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
                                                options = imageExportOptions
                                            )
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
                                    toaster.show(
                                        imageSuccessMessage,
                                        type = ToastType.Success
                                    )
                                    onDismissRequest()
                                },
                                shape = AppShapes.ButtonPill
                            ) {
                                Text(stringResource(R.string.mermaid_export))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareOptionCard(
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
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "share_option_card_scale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = contentColor,
                supportingColor = supportingColor,
                leadingIconColor = contentColor
            ),
            headlineContent = {
                Text(title)
            },
            supportingContent = {
                Text(description)
            },
            leadingContent = {
                Icon(icon, contentDescription = null)
            }
        )
    }
}

private fun buildMarkdownExportText(
    conversation: Conversation,
    messages: List<UIMessage>
): String = buildString {
    val mediaEncoder = AndroidPlatformMediaEncoder()
    append("# ${conversation.title}\n\n")
    append("*Exported on ${LocalDateTime.now().toLocalString()}*\n\n")

    messages.forEach { message ->
        val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
        append("$role:\n\n")
        message.parts.toSortedMessageParts().forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    append(part.text)
                    appendLine()
                }

                is UIMessagePart.Image -> {
                    append("![Image](${mediaEncoder.encodeImage(part.url).getOrNull()})")
                    appendLine()
                }

                is UIMessagePart.Reasoning -> {
                    part.reasoning.lines()
                        .filter { it.isNotBlank() }
                        .map { "> $it" }
                        .forEach {
                            append(it)
                        }
                    appendLine()
                    appendLine()
                }

                else -> {}
            }
        }
        appendLine()
        append("---")
        appendLine()
    }
}

private fun exportToMarkdown(
    context: Context,
    conversation: Conversation,
    messages: List<UIMessage>
) {
    val filename = "chat-export-${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.md"
    val markdown = buildMarkdownExportText(conversation, messages)

    try {
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

        // Share the file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareFile(context, uri, "text/markdown")

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun exportToImage(
    context: Context,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions = ImageExportOptions()
) {
    val filename = "chat-export-${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.png"
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
        width = 540.dp,
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

    try {
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

        // Save to gallery
        context.exportImage(activity, bitmap, filename)

        // Share the file
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

data class ImageExportOptions(val expandReasoning: Boolean = false)

@Composable
private fun ExportedChatImage(
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ImageExportOptions = ImageExportOptions()
) {
    val navBackStack = rememberNavController()
    val highlighter = koinInject<Highlighter>()
    RikkahubTheme {
        CompositionLocalProvider(
            LocalNavController provides navBackStack,
            LocalHighlighter provides highlighter
        ) {
            Surface(
                modifier = Modifier.width(540.dp) // like 1080p but with density independence
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${LocalDateTime.now().toLocalString()}  -  $EXPORT_BRAND",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Messages
                    messages.forEachIndexed { index, message ->
                        ExportedChatMessage(
                            message = message,
                            options = options,
                            prevMessage = messages.getOrNull(index - 1)
                        )
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
private fun ExportedChatMessage(
    message: UIMessage,
    prevMessage: UIMessage? = null,
    options: ImageExportOptions = ImageExportOptions()
) {
    if (message.parts.isEmptyUIMessage()) return
    val context = LocalContext.current
    val settings = LocalSettings.current
    val model = message.modelId?.let { settings.findModelById(it) }
    val provider = model?.findProvider(settings.providers)
    // Always show model icon for assistant messages in exported images
    val showModelIcon = message.role == MessageRole.ASSISTANT && prevMessage?.role == MessageRole.USER
    val iconLabel = when {
        model?.modelId?.isNotBlank() == true -> model.modelId
        model?.displayName?.isNotBlank() == true -> model.displayName
        else -> "AI"
    }
    val messageContent: @Composable () -> Unit = {
        val sortedParts = message.parts.toSortedMessageParts()
        val textPartCount = sortedParts.count { part ->
            part is UIMessagePart.Text && part.text.isNotBlank()
        }
        var textPartIndex = 0
        Column(
            modifier = Modifier
                .widthIn(max = (540 * 0.9).dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start
        ) {
            sortedParts.forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        if (part.text.isNotBlank()) {
                            GroupedMessageBubble(
                                position = getBubblePosition(textPartIndex, textPartCount),
                                role = if (message.role == MessageRole.USER) {
                                    BubbleRole.USER
                                } else {
                                    BubbleRole.ASSISTANT
                                },
                                modifier = Modifier.widthIn(max = (540 * 0.82).dp)
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    MarkdownBlock(
                                        content = part.text,
                                        paragraphSpacing = 12.dp
                                    )
                                }
                            }
                            textPartIndex++
                        }
                    }

                    is UIMessagePart.Image -> {
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

                    is UIMessagePart.Reasoning -> {
                        ExportedReasoningCard(reasoning = part, expanded = options.expandReasoning)
                    }

                    is UIMessagePart.ToolCall -> {
                        ExportedToolCall(toolCall = part)
                    }

                    is UIMessagePart.ToolResult -> {
                        ExportedToolResult(toolResult = part)
                    }

                    else -> {
                        // Other parts are not rendered in image export for now
                    }
                }
            }
        }
    }

    if (showModelIcon) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            if (model != null) {
                ModelIcon(
                    model = model,
                    provider = provider,
                    modifier = Modifier
                        .size(32.dp)
                )
            } else {
                TextAvatar(
                    text = iconLabel,
                    modifier = Modifier
                        .size(32.dp)
                )
            }

            Text(
                text = iconLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    messageContent()
}

@Composable
private fun ExportedReasoningCard(reasoning: UIMessagePart.Reasoning, expanded: Boolean) {
    val duration = reasoning.finishedAt?.let { endTime ->
        endTime - reasoning.createdAt
    } ?: (kotlin.time.Clock.System.now() - reasoning.createdAt)

    Surface(
        shape = if (expanded) AppShapes.CardMedium else AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.deep_thinking),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (duration > 0.seconds) {
                    Text(
                        text = "(${duration.toString(DurationUnit.SECONDS, 1)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }
            if (expanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardMediumInner12,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    MarkdownBlock(
                        content = reasoning.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportedToolCall(
    toolCall: UIMessagePart.ToolCall
) {
    Surface(
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 14.dp)
        ) {
            Icon(
                imageVector = when (toolCall.toolName) {
                    "create_memory", "edit_memory" -> Icons.Rounded.Favorite
                    "delete_memory" -> Icons.Rounded.Delete
                    "search_web" -> Icons.Rounded.Public
                    "scrape_web" -> Icons.Rounded.Public
                    else -> Icons.Rounded.Build
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = when (toolCall.toolName) {
                        "create_memory" -> stringResource(R.string.chat_message_tool_create_memory)
                        "edit_memory" -> stringResource(R.string.chat_message_tool_edit_memory)
                        "delete_memory" -> stringResource(R.string.chat_message_tool_delete_memory)
                        "search_web" -> {
                            val query = runCatching {
                                JsonInstant.parseToJsonElement(toolCall.arguments).jsonObject["query"]?.jsonPrimitiveOrNull?.contentOrNull
                                    ?: ""
                            }.getOrDefault("")
                            stringResource(R.string.chat_message_tool_search_web, query)
                        }
                        "scrape_web" -> stringResource(R.string.chat_message_tool_scrape_web)
                        else -> stringResource(R.string.chat_message_tool_call_generic, toolCall.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExportedToolResult(toolResult: UIMessagePart.ToolResult) {
    Surface(
        shape = AppShapes.ButtonPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 14.dp)
        ) {
            Icon(
                imageVector = when (toolResult.toolName) {
                    "create_memory", "edit_memory" -> Icons.Rounded.Favorite
                    "delete_memory" -> Icons.Rounded.Delete
                    "search_web" -> Icons.Rounded.Public
                    "scrape_web" -> Icons.Rounded.Public
                    else -> Icons.Rounded.Build
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = when (toolResult.toolName) {
                        "create_memory" -> stringResource(R.string.chat_message_tool_create_memory)
                        "edit_memory" -> stringResource(R.string.chat_message_tool_edit_memory)
                        "delete_memory" -> stringResource(R.string.chat_message_tool_delete_memory)
                        "search_web" -> {
                            val query =
                                toolResult.arguments.jsonObject["query"]?.jsonPrimitiveOrNull?.contentOrNull
                                    ?: ""
                            stringResource(R.string.chat_message_tool_search_web, query)
                        }
                        "scrape_web" -> stringResource(R.string.chat_message_tool_scrape_web)
                        else -> stringResource(R.string.chat_message_tool_call_generic, toolResult.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
