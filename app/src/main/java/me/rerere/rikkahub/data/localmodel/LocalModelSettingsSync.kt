package me.rerere.rikkahub.data.localmodel

import me.rerere.ai.provider.LocalModelDownloadState
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_LOCAL_PROVIDER
import me.rerere.rikkahub.data.datastore.DEFAULT_LOCAL_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity

object LocalModelSettingsSync {
    fun apply(settings: Settings, installs: List<LocalModelInstallEntity>): Settings {
        val localModels = installs
            .filter { it.status == LocalModelDownloadState.DOWNLOADED.name && it.localPath.isNotBlank() }
            .map { it.toModel() }
        val existingLocal = settings.providers
            .filterIsInstance<ProviderSetting.Local>()
            .firstOrNull { it.id == DEFAULT_LOCAL_PROVIDER_ID }
            ?: DEFAULT_LOCAL_PROVIDER
        val localProvider = existingLocal.copyProvider(
            id = DEFAULT_LOCAL_PROVIDER_ID,
            enabled = true,
            name = "Local",
            models = localModels,
            customIconUri = null,
            builtIn = true,
        )
        return settings.copy(
            providers = buildList {
                add(localProvider)
                addAll(settings.providers.filterNot { it.id == DEFAULT_LOCAL_PROVIDER_ID || it is ProviderSetting.Local })
            }
        )
    }
}
