package me.rerere.rikkahub.ui.pages.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import me.rerere.rikkahub.ui.components.nav.AppCompactTopBar
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppModalSheet
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.utils.base64Decode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPage(url: String, content: String) {
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(
            url = url,
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            })
    } else {
        rememberWebViewState(
            data = content.base64Decode(),
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            }
        )
    }

    var showDropdown by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }

    BackHandler(state.canGoBack) {
        state.goBack()
    }

    Scaffold(
        topBar = {
            AppCompactTopBar(
                title = {
                    Text(
                        text = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl
                        ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { state.reload() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }

                    IconButton(
                        onClick = { state.goForward() },
                        enabled = state.canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options")

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in Browser") },
                                leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    state.currentUrl?.let { url ->
                                        if (url.isNotBlank()) {
                                            urlHandler.openUri(url)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Console Logs") },
                                leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    showConsoleSheet = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (showConsoleSheet) {
        AppModalSheet(
            onDismissRequest = { showConsoleSheet = false },
            title = "Console Logs",
            showCloseButton = true,
            maxHeightFraction = 0.85f,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    LazyColumn {
                        items(state.consoleMessages) { message ->
                            Text(
                                text = "${message.messageLevel().name}: ${message.message()}\n" +
                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = when (message.messageLevel().name) {
                                    "ERROR" -> MaterialTheme.colorScheme.error
                                    "WARNING" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                if (state.consoleMessages.isEmpty()) {
                    Text(
                        text = "No console messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
