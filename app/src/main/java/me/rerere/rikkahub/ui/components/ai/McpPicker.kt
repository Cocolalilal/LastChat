package me.rerere.rikkahub.ui.components.ai

import me.rerere.rikkahub.ui.theme.LocalDarkMode
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.rerere.common.http.urlHostOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import org.koin.compose.koinInject

@Composable
fun McpPickerButton(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }
    ToggleSurface(
        modifier = modifier,
        checked = assistant.mcpServers.isNotEmpty(),
        onClick = {
            showMcpPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    BadgedBox(
                        badge = {
                            if (enabledServers.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(text = enabledServers.size.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = stringResource(R.string.mcp_picker_title),
                        )
                    }

                }
            }
        }
    }
    if (showMcpPicker) {
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = { showMcpPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.mcp_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                AnimatedVisibility(loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        LinearWavyProgressIndicator()
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                McpPicker(
                    assistant = assistant,
                    servers = servers,
                    onUpdateAssistant = {
                        onUpdateAssistant(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun McpPicker(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    val mcpManager = koinInject<McpManager>()
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val availableServers = servers.fastFilter { it.commonOptions.enable }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = availableServers,
            key = { _, server -> server.id },
        ) { index, server ->
            val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
            val shape = when {
                availableServers.size == 1 -> RoundedCornerShape(28.dp)
                index == 0 -> RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd = 8.dp,
                )
                index == availableServers.lastIndex -> RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 8.dp,
                    bottomStart = 28.dp,
                    bottomEnd = 28.dp,
                )
                else -> RoundedCornerShape(8.dp)
            }
            CompositionLocalProvider(
                androidx.compose.material3.LocalAbsoluteTonalElevation provides
                    if (amoledMode && isDarkMode) 0.dp
                    else androidx.compose.material3.LocalAbsoluteTonalElevation.current
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        McpServerFavicon(
                            url = server.endpointUrl,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = server.commonOptions.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (status == McpStatus.Connected) {
                                    val tools = server.commonOptions.tools
                                    val enabledTools = tools.fastFilter { it.enable }
                                    Tag(type = TagType.INFO) {
                                        Text(
                                            stringResource(
                                                R.string.mcp_tools_enabled_count,
                                                enabledTools.size,
                                                tools.size
                                            )
                                        )
                                    }
                                }
                                when (status) {
                                    is McpStatus.Idle -> Tag(type = TagType.DEFAULT) {
                                        Text(stringResource(R.string.mcp_status_disconnected))
                                    }
                                    is McpStatus.Connecting -> Tag(type = TagType.INFO) {
                                        Text(stringResource(R.string.mcp_status_connecting))
                                    }
                                    is McpStatus.Connected -> Tag(type = TagType.SUCCESS) {
                                        Text(stringResource(R.string.mcp_status_connected))
                                    }
                                    is McpStatus.Error -> Tag(type = TagType.ERROR) {
                                        Text(stringResource(R.string.mcp_status_error_short))
                                    }
                                }
                            }
                            val currentStatus = status
                            if (currentStatus is McpStatus.Error) {
                                Text(
                                    text = currentStatus.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HapticSwitch(
                            checked = server.id in assistant.mcpServers,
                            onCheckedChange = { enabled ->
                                val validServerIds = servers.map { it.id }.toSet()
                                val newServers = if (enabled) {
                                    assistant.mcpServers + server.id
                                } else {
                                    assistant.mcpServers - server.id
                                }
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.intersect(validServerIds)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private val McpServerConfig.endpointUrl: String
    get() = when (this) {
        is McpServerConfig.SseTransportServer -> url
        is McpServerConfig.StreamableHTTPServer -> url
    }

@Composable
private fun McpServerFavicon(
    url: String,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val faviconUrl = remember(url) {
        url.urlHostOrNull()?.let { host ->
            "https://www.google.com/s2/favicons?domain=$host&sz=64"
        }
    }
    if (!failed && faviconUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(faviconUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
    }
}
