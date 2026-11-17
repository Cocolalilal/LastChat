package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import me.rerere.rikkahub.ui.pages.setting.components.AddonTextEditor
import androidx.compose.foundation.layout.imePadding
import me.rerere.rikkahub.ui.pages.setting.components.AddonEditorField
import me.rerere.rikkahub.ui.pages.setting.components.CharacterAccessSection
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
fun SettingAdditionalContextDetailPage(id: String? = null, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = settings.additionalContexts.find { it.id.toString() == id }
    if (settings.init || (id != null && existing == null)) return
    var draft by remember { mutableStateOf(AdditionalContext()) }
    val snippet = existing ?: draft
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var namePending by remember { mutableStateOf(false) }
    var promptPending by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { active = false } }
    androidx.activity.compose.BackHandler(enabled = creating) { }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).imePadding(),
        topBar = {
            OneUITopAppBar(
                title = "Additional context", scrollBehavior = scrollBehavior,
                navigationIcon = { if (!creating) BackButton() else androidx.compose.foundation.layout.Box(Modifier.size(48.dp)) },
                actions = {
                    AutoSaveIndicator((existing != null && (namePending || promptPending)) || creating)
                    if (existing == null) {
                        androidx.compose.material3.TextButton(
                            enabled = snippet.name.isNotBlank() && snippet.prompt.isNotBlank() &&
                                !namePending && !promptPending && !creating,
                            onClick = {
                                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                                creating = true
                                val created = snippet.copy(name = snippet.name.trim())
                                vm.updateSettings(
                                    afterPersist = { if (active) navController.popBackStack() },
                                    onError = { error -> if (active) {
                                        creating = false
                                        toaster.show(error.message ?: "Could not create context")
                                    } },
                                ) { latest ->
                                    latest.copy(additionalContexts = latest.additionalContexts.filterNot { it.id == created.id } + created)
                                }
                            },
                        ) { androidx.compose.material3.Text("Create") }
                    } else IconButton(onClick = {
                        haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
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
            snippet = snippet, assistants = settings.assistants, contentPadding = padding,
            enabled = !creating,
            onNamePending = { namePending = it }, onPromptPending = { promptPending = it },
            onUpdate = { transform ->
                if (existing == null) draft = transform(draft) else vm.updateSettings { latest ->
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
    enabled: Boolean = true,
) {
    var editingPrompt by remember(snippet.id) { mutableStateOf(false) }
    if (editingPrompt) AddonTextEditor("Prompt", snippet.prompt,
        onDismiss = { editingPrompt = false },
        onSave = { prompt -> onUpdate { it.copy(prompt = prompt) }; editingPrompt = false },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SettingsGroup(title = "") {
                AddonEditorField(
                    title = "Name", value = snippet.name,
                    onValueChange = { name -> onUpdate { it.copy(name = name) } },
                    stateKey = snippet.id, singleLine = true, onPendingChange = onNamePending, enabled = enabled,
                )
                AddonEditorField(
                    title = "Prompt", value = snippet.prompt,
                    onValueChange = { prompt -> onUpdate { it.copy(prompt = prompt) } },
                    stateKey = snippet.id, minLines = 5, maxLines = 12, onPendingChange = onPromptPending,
                    onExpand = { editingPrompt = true }, enabled = enabled,
                )
                SettingGroupItem(
                    title = "Enabled",
                    trailing = {
                        HapticSwitch(snippet.enabled, { checked -> onUpdate { it.copy(enabled = checked) } }, enabled = enabled)
                    },
                    onClick = if (enabled) ({ onUpdate { it.copy(enabled = !it.enabled) } }) else null,
                )
            }
        }
        item {
            CharacterAccessSection(
                assistants = assistants, selectedIds = snippet.assistantIds, enabled = enabled,
                allSelected = snippet.allAssistants,
                onAllSelectedChange = { all -> onUpdate { it.copy(allAssistants = all) } },
                onSelectionChange = { id, checked -> onUpdate {
                    it.copy(assistantIds = if (checked) it.assistantIds + id else it.assistantIds - id)
                } },
            )
        }
    }
}
