package me.rerere.rikkahub.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.browser.BrowserSecretKind
import me.rerere.rikkahub.data.browser.BrowserSecretMetadata
import me.rerere.rikkahub.data.browser.BrowserSecretsStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject

@Composable
fun BrowserSecretsScreen() {
    val secretsStore = koinInject<BrowserSecretsStore>()
    val settingsStore = koinInject<SettingsStore>()
    val entries by secretsStore.entriesFlow.collectAsStateWithLifecycle()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var searchQuery by remember { mutableStateOf("") }
    val revealedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var editingEntry by remember { mutableStateOf<BrowserSecretMetadata?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }

    val filtered = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else {
            val q = searchQuery.trim().lowercase()
            entries.filter {
                it.key.lowercase().contains(q) ||
                    it.domain.lowercase().contains(q) ||
                    it.note.lowercase().contains(q)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Browser Secrets",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    showEditor = true
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add secret")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (settings.browserSettings.syncSecretsToWorkspace) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Workspace .secrets sync is active (chmod 600)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search keys, domains, or notes...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Key,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (entries.isEmpty()) "No saved secrets yet" else "No matching secrets",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add logins, API tokens, or cookie bundles. Reference them safely in browser actions as {{SECRET:KEY}}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isRevealed = revealedKeys[item.key] == true
                        val value = remember(item.key, item.updatedAt, isRevealed) {
                            if (isRevealed) secretsStore.getValue(item.key).orEmpty() else ""
                        }
                        Card(
                            shape = AppShapes.CardMedium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = item.key,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = when (item.kind) {
                                                BrowserSecretKind.SITE_LOGIN -> "Login"
                                                BrowserSecretKind.ENV_SECRET -> "Env"
                                                BrowserSecretKind.COOKIE_BUNDLE -> "Cookies"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (item.savedByAgent) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text = "Agent",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }

                                if (item.domain.isNotBlank()) {
                                    Text(
                                        text = item.domain,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (item.note.isNotBlank()) {
                                    Text(
                                        text = item.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Text(
                                    text = if (isRevealed) value.take(240) else "••••••••••••",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(onClick = { revealedKeys[item.key] = !isRevealed }) {
                                        Icon(
                                            if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (isRevealed) "Hide" else "Reveal",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val raw = secretsStore.getValue(item.key).orEmpty()
                                            context.writeClipboardText(raw)
                                            toaster.show("Copied ${item.key}", type = ToastType.Success)
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy value", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            editingEntry = item
                                            showEditor = true
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { pendingDeleteKey = item.key }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
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

    if (showEditor) {
        SecretEditorDialog(
            existing = editingEntry,
            initialValue = editingEntry?.let { secretsStore.getValue(it.key) }.orEmpty(),
            onDismiss = { showEditor = false },
            onSave = { key, value, domain, kind, note ->
                val result = secretsStore.saveSecret(
                    key = key,
                    value = value,
                    domain = domain,
                    kind = kind,
                    note = note,
                    savedByAgent = editingEntry?.savedByAgent ?: false,
                    overwrite = editingEntry != null,
                )
                result.fold(
                    onSuccess = {
                        showEditor = false
                        toaster.show("Secret saved", type = ToastType.Success)
                    },
                    onFailure = { err ->
                        toaster.show(err.message ?: "Failed to save secret", type = ToastType.Error)
                    },
                )
            },
        )
    }

    pendingDeleteKey?.let { keyToDelete ->
        LastChatDestructiveConfirmDialog(
            title = "Delete secret '$keyToDelete'?",
            message = "This removes the encrypted value and metadata permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                secretsStore.deleteSecret(keyToDelete)
                pendingDeleteKey = null
                toaster.show("Deleted $keyToDelete", type = ToastType.Success)
            },
            onDismiss = { pendingDeleteKey = null },
        )
    }
}

@Composable
private fun SecretEditorDialog(
    existing: BrowserSecretMetadata?,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, domain: String, kind: BrowserSecretKind, note: String) -> Unit,
) {
    var key by remember(existing) { mutableStateOf(existing?.key.orEmpty()) }
    var value by remember(existing, initialValue) { mutableStateOf(initialValue) }
    var domain by remember(existing) { mutableStateOf(existing?.domain.orEmpty()) }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: BrowserSecretKind.SITE_LOGIN) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var reveal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Browser Secret" else "Edit Secret") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key (e.g. GITHUB_PASSWORD)") },
                    enabled = existing == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Select(
                    options = BrowserSecretKind.entries,
                    selectedOption = kind,
                    onOptionSelected = { kind = it },
                    optionToString = { k ->
                        when (k) {
                            BrowserSecretKind.SITE_LOGIN -> "Site Login"
                            BrowserSecretKind.ENV_SECRET -> "Env Secret"
                            BrowserSecretKind.COOKIE_BUNDLE -> "Cookie Bundle"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Secret Value") },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key, value, domain, kind, note) },
                enabled = key.isNotBlank() && value.isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
