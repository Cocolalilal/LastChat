package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingAdditionalContextDetailPage(id: String, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snippet = settings.additionalContexts.find { it.id.toString() == id } ?: return
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var namePending by remember { mutableStateOf(false) }
    var promptPending by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OneUITopAppBar(
                title = "Additional context",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    AutoSaveIndicator(namePending || promptPending)
                    IconButton(onClick = {
                        val index = settings.additionalContexts.indexOfFirst { it.id == snippet.id }
                        navController.popBackStack()
                        vm.updateSettings { latest ->
                            latest.copy(additionalContexts = latest.additionalContexts.filterNot { it.id == snippet.id })
                        }
                        toaster.show("Context deleted", action = ToastAction("Undo") {
                            vm.updateSettings { latest ->
                                if (latest.additionalContexts.any { it.id == snippet.id }) latest else latest.copy(
                                    additionalContexts = latest.additionalContexts.toMutableList().apply {
                                        add(index.coerceIn(0, size), snippet)
                                    },
                                )
                            }
                        })
                    }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { padding ->
        AdditionalContextEditor(
            snippet = snippet,
            assistants = settings.assistants,
            contentPadding = padding,
            onNamePending = { namePending = it },
            onPromptPending = { promptPending = it },
            onUpdate = { transform ->
                vm.updateSettings { latest ->
                    latest.copy(additionalContexts = latest.additionalContexts.map {
                        if (it.id == snippet.id) transform(it) else it
                    })
                }
            },
        )
    }
}

@Composable
internal fun AdditionalContextEditor(
    snippet: AdditionalContext,
    assistants: List<Assistant>,
    onUpdate: ((AdditionalContext) -> AdditionalContext) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onNamePending: (Boolean) -> Unit = {},
    onPromptPending: (Boolean) -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsGroup(title = "") {
                SettingGroupInputItem(title = "Name") {
                    DebouncedTextField(
                        value = snippet.name,
                        onValueChange = { name -> onUpdate { it.copy(name = name) } },
                        stateKey = snippet.id,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        onPendingChange = onNamePending,
                    )
                }
                SettingGroupInputItem(title = "Prompt") {
                    DebouncedTextField(
                        value = snippet.prompt,
                        onValueChange = { prompt -> onUpdate { it.copy(prompt = prompt) } },
                        stateKey = snippet.id,
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        onPendingChange = onPromptPending,
                    )
                }
                SettingGroupItem(
                    title = "Enabled",
                    trailing = {
                        HapticSwitch(snippet.enabled, { enabled -> onUpdate { it.copy(enabled = enabled) } })
                    },
                    onClick = { onUpdate { it.copy(enabled = !it.enabled) } },
                )
            }
        }
        item {
            SettingsGroup(title = "Characters") {
                SettingGroupItem(
                    title = "All characters",
                    trailing = {
                        HapticSwitch(snippet.allAssistants, { all -> onUpdate { it.copy(allAssistants = all) } })
                    },
                    onClick = { onUpdate { it.copy(allAssistants = !it.allAssistants) } },
                )
                if (!snippet.allAssistants) assistants.forEach { assistant ->
                    val selected = assistant.id in snippet.assistantIds
                    fun toggle(checked: Boolean) = onUpdate {
                        it.copy(assistantIds = if (checked) it.assistantIds + assistant.id else it.assistantIds - assistant.id)
                    }
                    SettingGroupItem(
                        title = assistant.name.ifBlank { "Unnamed character" },
                        icon = { UIAvatar(name = assistant.name, value = assistant.avatar, modifier = Modifier.size(24.dp)) },
                        trailing = { HapticSwitch(selected, ::toggle) },
                        onClick = { toggle(!selected) },
                    )
                }
            }
        }
    }
}
