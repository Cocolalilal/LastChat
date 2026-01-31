package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.highlight.HighlightText
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.saveToDownloads
import org.koin.compose.koinInject

@Composable
fun ToolCallItem(
    toolName: String,
    arguments: JsonElement,
    content: JsonElement?,
    loading: Boolean = false,
) {
    var showResult by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.animateContentSize(),
        onClick = {
            showResult = true
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 12.dp)
                .height(IntrinsicSize.Min)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 4.dp,
                )
            } else {
                Icon(
                    imageVector = when (toolName) {
                        "create_memory", "edit_memory" -> Icons.Rounded.Bookmark
                        "delete_memory" -> Icons.Rounded.BookmarkRemove
                        "search_web" -> Icons.Rounded.Public
                        "scrape_web" -> Icons.Rounded.Public
                        "eval_python", "pip_install", "write_sandbox_file", "read_sandbox_file",
                        "list_sandbox_files", "delete_sandbox_file", "import_attachment" -> Icons.Rounded.Terminal
                        else -> Icons.Rounded.Build
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when (toolName) {
                        "create_memory" -> stringResource(R.string.chat_message_tool_create_memory)
                        "edit_memory" -> stringResource(R.string.chat_message_tool_edit_memory)
                        "delete_memory" -> stringResource(R.string.chat_message_tool_delete_memory)
                        "search_web" -> stringResource(
                            R.string.chat_message_tool_search_web,
                            (arguments as? JsonObject)?.get("query")?.jsonPrimitiveOrNull?.contentOrNull
                                ?: ""
                        )
                        "scrape_web" -> stringResource(R.string.chat_message_tool_scrape_web)
                        "eval_python" -> stringResource(R.string.chat_message_tool_python_eval)
                        "pip_install" -> stringResource(R.string.chat_message_tool_python_pip)
                        "write_sandbox_file" -> stringResource(
                            R.string.chat_message_tool_python_write_file,
                            (arguments as? JsonObject)?.get("path")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                        )
                        "read_sandbox_file" -> stringResource(
                            R.string.chat_message_tool_python_read_file,
                            (arguments as? JsonObject)?.get("path")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                        )
                        "list_sandbox_files" -> stringResource(R.string.chat_message_tool_python_list_files)
                        "delete_sandbox_file" -> stringResource(
                            R.string.chat_message_tool_python_delete_file,
                            (arguments as? JsonObject)?.get("path")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                        )
                        "import_attachment" -> stringResource(R.string.chat_message_tool_python_import)
                        else -> stringResource(
                            R.string.chat_message_tool_call_generic,
                            toolName
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.shimmer(isLoading = loading),
                )
                if (toolName == "create_memory" || toolName == "edit_memory") {
                    val memoryContent = (content as? JsonObject)?.get("content")?.jsonPrimitiveOrNull?.contentOrNull
                    if (memoryContent != null) {
                        Text(
                            text = memoryContent,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.shimmer(isLoading = loading),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (toolName == "search_web") {
                    val answer = (content as? JsonObject)?.get("answer")?.jsonPrimitiveOrNull?.contentOrNull
                    if (answer != null) {
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.shimmer(isLoading = loading),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val items = (content as? JsonObject)?.get("items")?.jsonArray ?: emptyList()
                    if (items.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FaviconRow(
                                urls = items.mapNotNull {
                                    (it as? JsonObject)?.get("url")?.jsonPrimitiveOrNull?.contentOrNull
                                },
                                size = 18.dp,
                            )
                            Text(
                                text = stringResource(R.string.chat_message_tool_search_results_count, items.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
                if(toolName == "scrape_web") {
                    val url = (arguments as? JsonObject)?.get("url")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                // Python tool output preview
                if (toolName == "eval_python" && content != null && !loading) {
                    val contentObj = content as? JsonObject
                    val result = contentObj?.get("result")?.jsonPrimitiveOrNull?.contentOrNull
                    val stdout = contentObj?.get("stdout")?.jsonPrimitiveOrNull?.contentOrNull
                    val error = contentObj?.get("error")?.jsonPrimitiveOrNull?.contentOrNull
                    val previewText = error ?: result ?: stdout
                    if (!previewText.isNullOrBlank()) {
                        Text(
                            text = previewText.take(100) + if (previewText.length > 100) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (error != null) MaterialTheme.colorScheme.error 
                                   else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // File operation result preview
                if (toolName in listOf("write_sandbox_file", "read_sandbox_file", "list_sandbox_files") && content != null && !loading) {
                    val contentObj = content as? JsonObject
                    val uri = contentObj?.get("uri")?.jsonPrimitiveOrNull?.contentOrNull
                    val files = contentObj?.get("files")?.jsonArray
                    val success = contentObj?.get("success")?.jsonPrimitiveOrNull?.contentOrNull
                    val previewText = when {
                        uri != null -> uri.substringAfterLast("/")
                        files != null -> "${files.size} files"
                        success != null -> if (success == "true") "✓" else "✗"
                        else -> null
                    }
                    if (previewText != null) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    if (showResult && content != null) {
        ToolCallPreviewSheet(
            toolName = toolName,
            arguments = arguments,
            content = content,
            onDismissRequest = {
                showResult = false
            }
        )
    }
}

@Composable
private fun ToolCallPreviewSheet(
    toolName: String,
    arguments: JsonElement,
    content: JsonElement,
    onDismissRequest: () -> Unit = {}
) {
    val navController = LocalNavController.current
    val memoryRepo: MemoryRepository = koinInject()
    val scope = rememberCoroutineScope()

    // Check if this is a memory creation/update operation
    val isMemoryOperation = toolName in listOf("create_memory", "edit_memory")
    val memoryId = (content as? JsonObject)?.get("id")?.jsonPrimitiveOrNull?.intOrNull

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = {
            onDismissRequest()
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .padding(16.dp)
                    .padding(16.dp),
                    //.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (toolName) {
                    "search_web" -> {
                        Text(
                            stringResource(
                                R.string.chat_message_tool_search_prefix,
                                (arguments as? JsonObject)?.get("query")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                            )
                        )
                        val contentObj = content as? JsonObject
                        val items = contentObj?.get("items")?.jsonArray ?: emptyList()
                        val answer = contentObj?.get("answer")?.jsonPrimitive?.contentOrNull
                        if (items.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (answer != null) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        ) {
                                            MarkdownBlock(
                                                content = answer,
                                                modifier = Modifier
                                                    .padding(16.dp)
                                                    .fillMaxWidth(),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                items(items.size, key = { index -> "search_item_$index" }) { index ->
                                    val it = items[index]
                                    val itemObj = it as? JsonObject ?: return@items
                                    val url = itemObj["url"]?.jsonPrimitive?.content ?: return@items
                                    val title =
                                        itemObj["title"]?.jsonPrimitive?.content
                                            ?: return@items
                                    val text = itemObj["text"]?.jsonPrimitive?.content ?: return@items
                                    Card(
                                        onClick = {
                                            navController.navigate(Screen.WebView(url = url))
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp, horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Favicon(
                                                url = url,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = title,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = text,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    text = url,
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.6f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            HighlightText(
                                code = JsonInstantPretty.encodeToString(content),
                                language = "json",
                                fontSize = 12.sp
                            )
                        }
                    }

                    "scrape_web" -> {
                        val urls = (content as? JsonObject)?.get("urls")?.jsonArray ?: emptyList()
                        Text(
                            text = stringResource(
                                R.string.chat_message_tool_scrape_prefix,
                                urls.joinToString(", ") { (it as? JsonObject)?.get("url")?.jsonPrimitiveOrNull?.contentOrNull ?: "" }),
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(urls.size, key = { index -> "scrape_url_$index" }) { index ->
                                val url = urls[index]
                                val urlObject = url as? JsonObject
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = urlObject?.get("url")?.jsonPrimitive?.content ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Card {
                                        MarkdownBlock(
                                            content = urlObject?.get("content")?.jsonPrimitive?.content ?: "",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    8.dp
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Python tools - show code with syntax highlighting and formatted output
                    "eval_python" -> {
                        val code = (arguments as? JsonObject)?.get("code")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                        val contentObj = content as? JsonObject
                        val result = contentObj?.get("result")?.jsonPrimitiveOrNull?.contentOrNull
                        val stdout = contentObj?.get("stdout")?.jsonPrimitiveOrNull?.contentOrNull
                        val stderr = contentObj?.get("stderr")?.jsonPrimitiveOrNull?.contentOrNull
                        val error = contentObj?.get("error")?.jsonPrimitiveOrNull?.contentOrNull
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Code section
                            Text(
                                text = stringResource(R.string.chat_message_tool_python_code),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HighlightText(
                                    code = code,
                                    language = "python",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            
                            // Output section
                            Text(
                                text = stringResource(R.string.chat_message_tool_python_output),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (error != null) 
                                        MaterialTheme.colorScheme.errorContainer 
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (error != null) {
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    } else {
                                        if (!stdout.isNullOrBlank()) {
                                            Text(
                                                text = stdout,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        if (!result.isNullOrBlank() && result != "null") {
                                            Text(
                                                text = "→ $result",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        if (!stderr.isNullOrBlank()) {
                                            Text(
                                                text = stderr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        if (stdout.isNullOrBlank() && (result.isNullOrBlank() || result == "null") && stderr.isNullOrBlank()) {
                                            Text(
                                                text = "(no output)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // File write tool - show file info with download button
                    "write_sandbox_file" -> {
                        val path = (arguments as? JsonObject)?.get("path")?.jsonPrimitiveOrNull?.contentOrNull ?: ""
                        val contentObj = content as? JsonObject
                        val success = contentObj?.get("success")?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
                        val uri = contentObj?.get("uri")?.jsonPrimitiveOrNull?.contentOrNull
                        val error = contentObj?.get("error")?.jsonPrimitiveOrNull?.contentOrNull
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val scope = rememberCoroutineScope()
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chat_message_tool_file_created),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (success) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // File path
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (success) Icons.Rounded.Build
                                                else Icons.Rounded.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (success) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    if (error != null) {
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    
                                    // Download button
                                    if (success && uri != null) {
                                        androidx.compose.material3.FilledTonalButton(
                                            onClick = {
                                                val uriParsed = android.net.Uri.parse(uri)
                                                scope.launch {
                                                    context.saveToDownloads(uriParsed, path.substringAfterLast("/"))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            androidx.compose.foundation.layout.Spacer(
                                                modifier = Modifier.size(8.dp)
                                            )
                                            Text(stringResource(R.string.chat_message_tool_download_file))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.chat_message_tool_call_title),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )

                            // 如果是memory操作，允许用户快速删除
                            if (isMemoryOperation && memoryId != null) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                memoryRepo.deleteMemory(memoryId)
                                                onDismissRequest()
                                            } catch (e: Exception) {
                                                // Handle error if needed
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete memory"
                                    )
                                }
                            }
                        }
                        
                        // Arguments section
                        val clipboardManager = LocalClipboardManager.current
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val argumentsJson = remember(arguments) {
                                JsonInstantPretty.encodeToString(arguments)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.chat_message_tool_call_label,
                                        toolName
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    modifier = Modifier.size(24.dp),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(argumentsJson))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy arguments",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HighlightText(
                                    code = argumentsJson,
                                    language = "json",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Result section
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val contentJson = remember(content) {
                                JsonInstantPretty.encodeToString(content)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_message_tool_call_result),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    modifier = Modifier.size(24.dp),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(contentJson))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy result",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HighlightText(
                                    code = contentJson,
                                    language = "json",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
