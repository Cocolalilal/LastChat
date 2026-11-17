package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel

/** Compatibility for a saved back stack from the short-lived standalone editor route. */
@Composable
fun SettingLorebookSettingsPage(id: String? = null, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (settings.init) return
    val nav = LocalNavController.current
    val toaster = LocalToaster.current
    val book = settings.lorebooks.firstOrNull { it.id.toString() == id }
    var saving by remember(id) { mutableStateOf(false) }
    if (id != null && book == null) {
        LaunchedEffect(id) { nav.popBackStack() }
        return
    }
    if (book != null) LorebookEditorSheet(
        lorebook = book, saving = saving,
        onDismiss = { refs -> vm.cleanupFilesIfUnreferenced(refs); nav.popBackStack() },
        onDiscardFiles = { vm.cleanupFilesIfUnreferenced(it) },
        onSave = { edited ->
            saving = true
            vm.updateSettings(afterPersist = { nav.popBackStack() }, onError = {
                saving = false; toaster.show(it.message ?: "Could not save lorebook")
            }) { latest -> latest.copy(lorebooks = latest.lorebooks.map {
                if (it.id == book.id) it.copy(name = edited.name, description = edited.description, cover = edited.cover) else it
            }) }
        },
    ) else LorebookCreatorSheet(
        saving = saving, onDismiss = { nav.popBackStack() },
        onDiscardFiles = { vm.cleanupFilesIfUnreferenced(it) },
        onSave = { created ->
            saving = true
            vm.updateSettings(afterPersist = {
                nav.popBackStack()
                nav.navigate(Screen.SettingLorebookDetail(created.id.toString()))
            }, onError = { saving = false; toaster.show(it.message ?: "Could not create lorebook") }) { latest ->
                latest.copy(lorebooks = latest.lorebooks.filterNot { it.id == created.id } + created)
            }
        },
    )
}
