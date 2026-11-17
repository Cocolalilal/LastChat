package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import me.rerere.rikkahub.ui.context.LocalToaster
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.SkillExportImport
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingSkillDetailPage(id: String?, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skill = settings.skills.find { it.id.toString() == id }
    if (id != null && skill == null) return
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    var pendingSaves by remember(id) { mutableIntStateOf(0) }

    fun persist(saved: Skill, synchronizePackage: Boolean = true, close: Boolean = false) {
        pendingSaves++
        vm.saveSkillEdit(
            skill = saved, create = id == null, synchronizePackage = synchronizePackage,
            onSaved = { pendingSaves--; if (close) navController.popBackStack() },
            onError = { pendingSaves--; toaster.show(it.message ?: "Could not save skill") },
        )
    }

    SkillEditorPage(
        skill = skill, assistants = settings.assistants, isSaving = pendingSaves > 0,
        onSave = { persist(it, close = true) },
        onPackageMetadataChanged = { persist(it, synchronizePackage = false) },
        onAutoSave = { persist(it) },
    )
}
