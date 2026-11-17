package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.AddonEditorField
import me.rerere.rikkahub.ui.pages.setting.components.CharacterAccessSection
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.OwnedFileDirectory
import me.rerere.rikkahub.utils.importOwnedFile
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun SettingLorebookSettingsPage(id: String? = null, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = settings.lorebooks.find { it.id.toString() == id }
    if (settings.init || (id != null && existing == null)) return
    var draft by remember { mutableStateOf(Lorebook()) }
    var draftAssistantIds by remember { mutableStateOf(emptySet<Uuid>()) }
    val lorebook = existing ?: draft
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val appScope: AppScope = koinInject()
    val storage: AppStorageRepository = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val haptics = rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val cleanupCoverRefs = remember { mutableStateListOf<String>() }
    var active by remember { mutableStateOf(true) }
    var namePending by remember { mutableStateOf(false) }
    var descriptionPending by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var importingCover by remember { mutableStateOf(false) }
    val selectedAssistantIds = if (existing == null) draftAssistantIds else settings.assistants
        .filter { lorebook.id in it.enabledLorebookIds }.map { it.id }.toSet()

    DisposableEffect(Unit) {
        onDispose {
            active = false
            val refs = cleanupCoverRefs.toList()
            if (refs.isNotEmpty()) appScope.launch {
                settingsStore.awaitQueuedWrites()
                storage.deleteFilesIfUnreferenced(refs)
            }
        }
    }

    fun update(transform: (Lorebook) -> Lorebook) {
        if (existing == null) {
            draft = transform(draft)
        } else {
            vm.updateSettings { latest ->
                latest.copy(lorebooks = latest.lorebooks.map {
                    if (it.id == existing.id) transform(it) else it
                })
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importingCover = true
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        context.importOwnedFile(uri, OwnedFileDirectory.LOREBOOK_COVER)?.let { localUri ->
                            val newUrl = localUri.toString()
                            if (!active) {
                                appScope.launch { storage.deleteFilesIfUnreferenced(listOf(newUrl)) }
                                return@withContext
                            }
                            cleanupCoverRefs += newUrl
                            val previousUrl = (lorebook.cover as? Avatar.Image)?.url
                            if (existing == null) {
                                draft = draft.copy(cover = Avatar.Image(newUrl))
                            } else {
                                if (previousUrl != null && previousUrl != newUrl) cleanupCoverRefs += previousUrl
                                vm.updateSettings { latest ->
                                    latest.copy(lorebooks = latest.lorebooks.map {
                                        if (it.id == existing.id) it.copy(cover = Avatar.Image(newUrl)) else it
                                    })
                                }
                            }
                        }
                    } catch (error: Exception) {
                        if (active) toaster.show(error.message ?: "Could not import cover",
                            me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                    } finally {
                        if (active) importingCover = false
                    }
                }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = creating) { }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).imePadding(),
        topBar = {
            OneUITopAppBar(
                title = stringResource(if (existing == null) R.string.lorebooks_page_create_lorebook else R.string.lorebook_edit),
                scrollBehavior = scrollBehavior,
                navigationIcon = { if (!creating) BackButton() else Box(Modifier.size(48.dp)) },
                actions = {
                    AutoSaveIndicator((existing != null && (namePending || descriptionPending)) || creating)
                    if (existing == null) TextButton(
                        enabled = draft.name.isNotBlank() && !namePending && !descriptionPending && !creating && !importingCover,
                        onClick = {
                            if (creating) return@TextButton
                            haptics.perform(HapticPattern.Pop)
                            creating = true
                            val created = draft.copy(name = draft.name.trim())
                            val selectedIds = draftAssistantIds
                            vm.updateSettings(afterPersist = {
                                if (active) {
                                    navController.popBackStack()
                                    navController.navigate(Screen.SettingLorebookDetail(created.id.toString()))
                                }
                            }, onError = { error ->
                                if (active) {
                                    creating = false
                                    toaster.show(error.message ?: "Could not create lorebook",
                                        me.rerere.rikkahub.ui.components.ui.ToastType.Error)
                                }
                            }) { latest ->
                                latest.copy(
                                    lorebooks = if (latest.lorebooks.any { it.id == created.id }) {
                                        latest.lorebooks.map { if (it.id == created.id) created else it }
                                    } else latest.lorebooks + created,
                                    assistants = latest.assistants.map {
                                        it.copy(enabledLorebookIds = if (it.id in selectedIds) {
                                            it.enabledLorebookIds + created.id
                                        } else it.enabledLorebookIds - created.id)
                                    },
                                )
                            }
                        },
                    ) { Text(stringResource(R.string.create)) }
                },
            )
        },
    ) { padding ->
        LorebookSettingsContent(
            lorebook = lorebook,
            enabled = !creating,
            assistants = settings.assistants,
            selectedAssistantIds = selectedAssistantIds,
            onUpdate = ::update,
            onPickCover = { if (!importingCover) coverPicker.launch("image/*") },
            onAssistantSelectionChange = { assistantId, checked ->
                if (creating) return@LorebookSettingsContent
                if (existing == null) {
                    draftAssistantIds = if (checked) draftAssistantIds + assistantId else draftAssistantIds - assistantId
                } else vm.updateSettings { latest ->
                    latest.copy(assistants = latest.assistants.map {
                        if (it.id == assistantId) it.copy(enabledLorebookIds =
                            if (checked) it.enabledLorebookIds + existing.id else it.enabledLorebookIds - existing.id)
                        else it
                    })
                }
            },
            contentPadding = padding,
            onNamePending = { namePending = it },
            onDescriptionPending = { descriptionPending = it },
        )
    }
}

@Composable
internal fun LorebookSettingsContent(
    lorebook: Lorebook,
    assistants: List<Assistant>,
    selectedAssistantIds: Set<Uuid>,
    onUpdate: ((Lorebook) -> Lorebook) -> Unit,
    onPickCover: () -> Unit,
    onAssistantSelectionChange: (Uuid, Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onNamePending: (Boolean) -> Unit = {},
    onDescriptionPending: (Boolean) -> Unit = {},
    enabled: Boolean = true,
) {
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
                SettingGroupItem(
                    title = "Cover",
                    trailing = {
                        Surface(shape = AppShapes.CardSmall, color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp, 48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                when (val cover = lorebook.cover) {
                                    is Avatar.Image -> AsyncImage(cover.url, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    is Avatar.Emoji -> Text(cover.content, style = MaterialTheme.typography.titleLarge)
                                    else -> Icon(Icons.Rounded.Book, contentDescription = null)
                                }
                            }
                        }
                    },
                    onClick = onPickCover.takeIf { enabled },
                )
                AddonEditorField(
                    title = stringResource(R.string.lorebooks_page_name),
                    value = lorebook.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value) } },
                    stateKey = "lorebook-name-${lorebook.id}", singleLine = true, enabled = enabled,
                    onPendingChange = onNamePending,
                )
                AddonEditorField(
                    title = stringResource(R.string.lorebooks_page_description),
                    value = lorebook.description,
                    onValueChange = { value -> onUpdate { it.copy(description = value) } },
                    stateKey = "lorebook-description-${lorebook.id}", minLines = 2, maxLines = 5, enabled = enabled,
                    onPendingChange = onDescriptionPending,
                )
            }
        }
        item {
            CharacterAccessSection(
                assistants = assistants,
                selectedIds = selectedAssistantIds, enabled = enabled,
                onSelectionChange = onAssistantSelectionChange,
            )
        }
    }
}
