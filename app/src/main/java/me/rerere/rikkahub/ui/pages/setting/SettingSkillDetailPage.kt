package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val context = LocalContext.current
    val navController = LocalNavController.current

    fun persist(saved: Skill) {
        vm.updateSettings { latest ->
            latest.copy(skills = if (latest.skills.any { it.id == saved.id }) {
                latest.skills.map { if (it.id == saved.id) saved else it }
            } else latest.skills + saved)
        }
    }

    SkillEditorPage(
        skill = skill,
        assistants = settings.assistants,
        onSave = {
            persist(SkillExportImport.syncManagedSkill(context, it))
            navController.popBackStack()
        },
        onSavePreservingPackage = { persist(it); navController.popBackStack() },
        onPackageMetadataChanged = ::persist,
        onAutoSave = { persist(SkillExportImport.syncManagedSkill(context, it)) },
    )
}
