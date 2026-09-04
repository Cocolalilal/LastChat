package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.ui.components.richtext.LastChatCodeBlock

private val jsonFormatter = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * Visual and interactive Tool Call Card used in chat turns.
 * Identical behavior and layout across Android and iOS in Compose Multiplatform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastChatToolCallCard(
    toolName: String,
    arguments: String,
    result: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onApprove: (() -> Unit)? = null,
    onDeny: (() -> Unit)? = null,
) {
    var showInspector by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val icon: ImageVector = when {
        toolName.contains("search", ignoreCase = true) -> Icons.Rounded.Public
        toolName.contains("image", ignoreCase = true) -> Icons.Rounded.Image
        toolName.contains("memory", ignoreCase = true) -> Icons.Rounded.Bookmark
        toolName.contains("shell", ignoreCase = true) || toolName.contains("python", ignoreCase = true) -> Icons.Rounded.Terminal
        toolName.contains("file", ignoreCase = true) -> Icons.Rounded.Description
        else -> Icons.Rounded.Build
    }

    val summary = remember(toolName, arguments) {
        buildToolSummary(toolName, arguments)
    }

    val cardColor = when {
        errorMessage != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        isLoading -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val borderColor = when {
        errorMessage != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        isLoading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f))
            .clickable { showInspector = true },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Icon or Progress spinner
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (errorMessage != null) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                } else if (result != null) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Summary text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    ),
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isLoading) {
                    Text(
                        text = "Executing tool...",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Approval buttons if manual approval requested
            if (onApprove != null && onDeny != null && isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onApprove,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Approve",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        onClick = onDeny,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Deny",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    // Detail Inspector Bottom Sheet
    if (showInspector) {
        ModalBottomSheet(
            onDismissRequest = { showInspector = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showInspector = false
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                // Arguments Section
                Text(
                    text = "Input Arguments",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                LastChatCodeBlock(
                    code = formatJson(arguments),
                    language = "json",
                    initiallyExpanded = true,
                )

                // Results Section
                if (result != null || errorMessage != null) {
                    Text(
                        text = if (errorMessage != null) "Execution Error" else "Execution Result",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    LastChatCodeBlock(
                        code = formatJson(errorMessage ?: result.orEmpty()),
                        language = "json",
                        initiallyExpanded = true,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun buildToolSummary(name: String, rawArgs: String): String {
    try {
        val element = Json.parseToJsonElement(rawArgs)
        if (element is JsonObject) {
            when {
                name.contains("search", ignoreCase = true) -> {
                    val q = element["query"]?.jsonPrimitive?.content ?: element["q"]?.jsonPrimitive?.content
                    if (!q.isNullOrBlank()) return "Search: $q"
                }
                name.contains("image", ignoreCase = true) -> {
                    val p = element["prompt"]?.jsonPrimitive?.content
                    if (!p.isNullOrBlank()) return "Generate Image: \"$p\""
                }
                name.contains("memory", ignoreCase = true) -> {
                    val c = element["content"]?.jsonPrimitive?.content ?: element["text"]?.jsonPrimitive?.content
                    if (!c.isNullOrBlank()) return "Memory: $c"
                }
                name.contains("shell", ignoreCase = true) -> {
                    val cmd = element["command"]?.jsonPrimitive?.content
                    if (!cmd.isNullOrBlank()) return "Run: $cmd"
                }
            }
        }
    } catch (_: Throwable) {}

    return "Tool: $name"
}

private fun formatJson(raw: String): String {
    return try {
        val element = Json.parseToJsonElement(raw)
        jsonFormatter.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) {
        raw
    }
}
