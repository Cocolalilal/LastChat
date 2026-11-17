package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.utils.SkillExportImport

private val skillPackageEditMutex = Mutex()

/** One process-wide lane shared by editor autosaves and explicit package-file edits. */
internal suspend fun <T> withSkillPackageEdit(block: suspend () -> T): T {
    skillPackageEditMutex.lock()
    try {
        return block()
    } finally {
        skillPackageEditMutex.unlock()
    }
}

/** Publish drafts immediately; durable writes and package IO survive the owning editor. */
internal class SkillEditWriter(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
) {
    fun save(
        skill: Skill,
        create: Boolean = false,
        synchronizePackage: Boolean = true,
        onSaved: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val draft = if (synchronizePackage) skill.copy(packageRoot = skill.safePackageRoot()) else skill
                // enqueueUpdate applies this pure transform before returning, so a quick Back /
                // reopen observes the latest draft even while earlier disk writes are pending.
                val acknowledged = settingsStore.enqueueUpdate { latest ->
                    if (!create && latest.skills.none { it.id == draft.id }) latest else latest.copy(
                        skills = if (latest.skills.any { it.id == draft.id }) {
                            latest.skills.map { if (it.id == draft.id) draft else it }
                        } else latest.skills + draft,
                    )
                }
                withSkillPackageEdit {
                    acknowledged.await()
                    if (synchronizePackage) {
                        // A newer optimistic edit may already exist. Sync that value, never an
                        // older captured draft, and do not publish an IO result over newer state.
                        settingsStore.settingsFlow.value.skills.firstOrNull { it.id == draft.id }?.let { current ->
                            withContext(Dispatchers.IO) { SkillExportImport.syncManagedSkill(context, current) }
                        }
                    }
                }
                withContext(Dispatchers.Main.immediate) { onSaved() }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.Main.immediate) { onError(error) }
            }
        }
    }
}
