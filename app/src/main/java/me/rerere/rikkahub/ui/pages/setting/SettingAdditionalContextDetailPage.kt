package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.components.AddonPromptField
import me.rerere.rikkahub.ui.pages.setting.components.CharacterAccessSection
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingAdditionalContextDetailPage(id: String? = null, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = settings.additionalContexts.find { it.id.toString() == id }
    if (settings.init || (id != null && existing == null)) return
    var draft by remember(id) { mutableStateOf(AdditionalContext()) }
    val snippet = existing ?: draft
    val nav = LocalNavController.current
    val toaster = LocalToaster.current
    var creating by remember(id) { mutableStateOf(false) }
    var active by remember(id) { mutableStateOf(true) }
    DisposableEffect(id) { onDispose { active = false } }
    AdditionalContextEditorPage(
        snippet = snippet, assistants = settings.assistants, saving = creating,
        onCreate = if (id != null) null else ({
            if (!creating) {
                creating = true
                val created = snippet.copy(name = snippet.name.trim())
                vm.updateSettings(afterPersist = { if (active) nav.popBackStack() }, onError = { error ->
                    if (active) { creating = false; toaster.show(error.message ?: "Could not create context") }
                }) { latest -> latest.copy(additionalContexts = latest.additionalContexts.filterNot { it.id == created.id } + created) }
            }
        }),
        onUpdate = { transform ->
            if (existing == null) draft = transform(draft) else vm.updateSettings { latest ->
                latest.copy(additionalContexts = latest.additionalContexts.map { if (it.id == snippet.id) transform(it) else it })
            }
        },
    )
}

@Composable
internal fun AdditionalContextEditorPage(
    snippet: AdditionalContext,
    assistants: List<Assistant>,
    onUpdate: ((AdditionalContext) -> AdditionalContext) -> Unit,
    onCreate: (() -> Unit)? = null,
    saving: Boolean = false,
) {
    var namePending by remember(snippet.id) { mutableStateOf(false) }
    var promptPending by remember(snippet.id) { mutableStateOf(false) }
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    androidx.activity.compose.BackHandler(enabled = saving) { }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(
            title = { Text(if (onCreate != null) "New context" else snippet.name.ifBlank { "Additional context" },
                maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { if (!saving) BackButton() else Box(Modifier.size(48.dp)) },
            actions = {
                AutoSaveIndicator((onCreate == null && (namePending || promptPending)) || saving)
                if (onCreate != null) TextButton(enabled = snippet.name.isNotBlank() && snippet.prompt.isNotBlank() &&
                    !namePending && !promptPending && !saving,
                    onClick = { haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop); onCreate() }) { Text("Create") }
            },
        )
    }) { padding ->
        AdditionalContextEditor(snippet = snippet, assistants = assistants, contentPadding = padding,
            enabled = !saving, onNamePending = { namePending = it }, onPromptPending = { promptPending = it }, onUpdate = onUpdate)
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
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SettingsGroup(title = "") {
            SettingGroupItem(title = "Name", trailing = {
                DebouncedTextField(value = snippet.name,
                    onValueChange = { name -> onUpdate { it.copy(name = name) } },
                    stateKey = "context_name_${snippet.id}", singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.5f), enabled = enabled,
                    onPendingChange = onNamePending)
            })
            SettingGroupItem(title = "Enabled",
                trailing = { HapticSwitch(snippet.enabled, { checked -> onUpdate { it.copy(enabled = checked) } }, enabled = enabled) },
                onClick = if (enabled) ({ onUpdate { it.copy(enabled = !it.enabled) } }) else null)
        }
        AddonPromptField(title = "Prompt", value = snippet.prompt,
            onValueChange = { prompt -> onUpdate { it.copy(prompt = prompt) } },
            stateKey = "context_prompt_${snippet.id}", enabled = enabled, onPendingChange = onPromptPending)
        CharacterAccessSection(assistants = assistants, selectedIds = snippet.assistantIds, enabled = enabled,
            entryName = snippet.name,
            allSelected = snippet.allAssistants,
            onAllSelectedChange = { all -> onUpdate { it.copy(allAssistants = all) } },
            onSelectionChange = { id, checked -> onUpdate {
                it.copy(assistantIds = if (checked) it.assistantIds + id else it.assistantIds - id)
            } })
    }
}
