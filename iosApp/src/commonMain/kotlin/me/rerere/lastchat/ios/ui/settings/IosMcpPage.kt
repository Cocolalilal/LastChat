package me.rerere.lastchat.ios.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.lastchat.ios.models.IosMcpCommonOptions
import me.rerere.lastchat.ios.models.IosMcpServerConfig
import me.rerere.lastchat.ios.models.IosMcpTool
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.theme.AppShapes

data class McpPreset(
    val name: String,
    val description: String,
    val defaultUrl: String,
    val isSse: Boolean = true,
)

val MCP_PRESETS = listOf(
    McpPreset("Brave Search", "Web search and content retrieval", "https://mcp.brave.com/sse"),
    McpPreset("Fetch", "Web page content scraper & converter", "http://127.0.0.1:3000/sse"),
    McpPreset("Filesystem", "Local file reader and workspace manager", "http://127.0.0.1:8000/sse"),
    McpPreset("Memory", "Knowledge graph and persistent memory", "http://127.0.0.1:8080/sse"),
    McpPreset("GitHub", "Repository search and commit viewer", "https://api.github.com/mcp"),
    McpPreset("SQLite", "Query local relational databases", "http://127.0.0.1:8010/sse"),
)

@Composable
fun IosMcpPage(
    servers: List<IosMcpServerConfig>,
    onSaveMcpServer: (IosMcpServerConfig) -> Unit,
    onDeleteMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpTools: suspend (IosMcpServerConfig) -> Result<List<IosMcpTool>>,
    onToggleMcpTool: (String, String, Boolean) -> Unit,
    onHapticPop: () -> Unit = {},
    onHapticTick: () -> Unit = {},
    onHapticSuccess: () -> Unit = {},
    onHapticError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var editingServer by remember { mutableStateOf<IosMcpServerConfig?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshingServerId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header with un-crushable button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MCP Servers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Model Context Protocol over SSE & HTTP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        onHapticPop()
                        editingServer = null
                        showDialog = true
                    },
                    shape = AppShapes.ButtonPill,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Server")
                }
            }
        }

        // Popular Presets Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Popular presets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MCP_PRESETS.forEach { preset ->
                        Surface(
                            onClick = {
                                onHapticPop()
                                editingServer = IosMcpServerConfig.SseTransportServer(
                                    url = preset.defaultUrl,
                                    commonOptions = IosMcpCommonOptions(name = preset.name),
                                )
                                showDialog = true
                            },
                            shape = AppShapes.ButtonPill,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status banner if active
        if (statusMessage != null) {
            item {
                Surface(
                    shape = AppShapes.CardSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Server list or empty placeholder
        if (servers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "No MCP servers configured",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add servers or select a preset above to equip assistants with external tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(servers, key = { it.id }) { server ->
                val isRefreshing = refreshingServerId == server.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (server.commonOptions.enable) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.commonOptions.name.ifBlank { "Untitled Server" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = server.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(
                                checked = server.commonOptions.enable,
                                onCheckedChange = { checked ->
                                    onHapticPop()
                                    onToggleMcpServer(server.id, checked)
                                },
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    onHapticTick()
                                    refreshingServerId = server.id
                                    statusMessage = "Discovering tools from ${server.commonOptions.name}..."
                                    scope.launch {
                                        onRefreshMcpTools(server).onSuccess { tools ->
                                            onHapticSuccess()
                                            statusMessage = "Discovered ${tools.size} tools from ${server.commonOptions.name}"
                                        }.onFailure { err ->
                                            onHapticError()
                                            statusMessage = "Discovery failed: ${err.message}"
                                        }
                                        refreshingServerId = null
                                    }
                                },
                                enabled = !isRefreshing,
                                shape = AppShapes.ButtonPill,
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Discover Tools")
                            }

                            Row {
                                IconButton(onClick = {
                                    onHapticPop()
                                    editingServer = server
                                    showDialog = true
                                }) {
                                    Icon(Icons.Rounded.Edit, "Edit", Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    onHapticPop()
                                    onDeleteMcpServer(server.id)
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        "Delete",
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        if (server.commonOptions.tools.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Discovered Tools (${server.commonOptions.tools.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                server.commonOptions.tools.forEach { tool ->
                                    Surface(
                                        shape = AppShapes.CardSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tool.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                if (!tool.description.isNullOrBlank()) {
                                                    Text(
                                                        text = tool.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = tool.enable,
                                                onCheckedChange = { checked ->
                                                    onHapticPop()
                                                    onToggleMcpTool(server.id, tool.name, checked)
                                                },
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

        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showDialog) {
        var serverName by remember { mutableStateOf(editingServer?.commonOptions?.name.orEmpty()) }
        var serverUrl by remember { mutableStateOf(editingServer?.url.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingServer != null) "Edit MCP Server" else "Add MCP Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LastChatFormItem(label = { Text("Server Name") }) {
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            placeholder = { Text("e.g. Brave Search") },
                            singleLine = true,
                            shape = AppShapes.InputField,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LastChatFormItem(label = { Text("Server URL") }) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            placeholder = { Text("https://example.com/sse") },
                            singleLine = true,
                            shape = AppShapes.InputField,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (serverName.isNotBlank() && serverUrl.isNotBlank()) {
                            val config = editingServer?.clone(
                                commonOptions = editingServer!!.commonOptions.copy(name = serverName),
                            ) ?: IosMcpServerConfig.SseTransportServer(
                                url = serverUrl,
                                commonOptions = IosMcpCommonOptions(name = serverName),
                            )
                            onSaveMcpServer(config)
                        }
                        showDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
        )
    }
}
