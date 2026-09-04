package me.rerere.lastchat.ios.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.lastchat.ios.IosAppearancePreferences
import me.rerere.lastchat.ios.IosAssistantPreferences
import me.rerere.lastchat.ios.IosConversation
import me.rerere.lastchat.ios.IosMemoryRecord
import me.rerere.lastchat.ios.IosProviderPreferences
import me.rerere.lastchat.ios.IosSearchPreferences
import me.rerere.lastchat.ios.IosTtsPreferences

@Serializable
enum class IosBackupItem {
    @SerialName("database")
    DATABASE,

    @SerialName("files")
    FILES;
}

@Serializable
data class IosWebDavConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val path: String = "LastChat",
    val items: Set<IosBackupItem> = setOf(IosBackupItem.DATABASE),
)

@Serializable
data class IosWebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

@Serializable
data class IosBackupManifest(
    @SerialName("format_version")
    val formatVersion: Int = 2,
    @SerialName("includes_database")
    val includesDatabase: Boolean = true,
    @SerialName("includes_files")
    val includesFiles: Boolean = false,
    @SerialName("managed_file_dirs")
    val managedFileDirs: List<String> = emptyList(),
    @SerialName("shared_prefs_stores")
    val sharedPrefsStores: List<String> = emptyList(),
)

/**
 * Cross-platform portable backup package used for complete export and restore.
 */
@Serializable
data class IosCrossPlatformBackup(
    val version: Int = 2,
    val createdAtEpochMs: Long = 0L,
    val app: String = "LastChat",
    val platform: String = "iOS",
    val appearance: IosAppearancePreferences = IosAppearancePreferences(),
    val provider: IosProviderPreferences = IosProviderPreferences(),
    val providerConfigurations: List<IosProviderPreferences> = emptyList(),
    val assistants: List<IosAssistantPreferences> = emptyList(),
    val skills: List<IosSkill> = emptyList(),
    val lorebooks: List<IosLorebook> = emptyList(),
    val mcpServers: List<IosMcpServerConfig> = emptyList(),
    val search: IosSearchPreferences = IosSearchPreferences(),
    val tts: IosTtsPreferences = IosTtsPreferences(),
    val webDavConfig: IosWebDavConfig = IosWebDavConfig(),
    val conversations: List<IosConversation> = emptyList(),
    val memories: List<IosMemoryRecord> = emptyList(),
)

/**
 * Summary of items restored from an Android or iOS backup.
 */
data class IosRestoreResult(
    val assistantsCount: Int = 0,
    val providersCount: Int = 0,
    val skillsCount: Int = 0,
    val lorebooksCount: Int = 0,
    val mcpServersCount: Int = 0,
    val conversationsCount: Int = 0,
    val memoriesCount: Int = 0,
    val notes: List<String> = emptyList(),
)
