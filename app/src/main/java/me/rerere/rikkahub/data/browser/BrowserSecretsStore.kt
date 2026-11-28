package me.rerere.rikkahub.data.browser

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.util.UUID

@Serializable
enum class BrowserSecretKind {
    SITE_LOGIN,
    ENV_SECRET,
    COOKIE_BUNDLE,
}

@Serializable
data class BrowserSecretMetadata(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val domain: String = "",
    val kind: BrowserSecretKind = BrowserSecretKind.SITE_LOGIN,
    val note: String = "",
    val savedByAgent: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

interface SecretValueStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

internal class InMemorySecretValueStorage : SecretValueStorage {
    private val map = linkedMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() { map.clear() }
}

private class EncryptedPrefsSecretValueStorage(
    private val context: Context,
) : SecretValueStorage {
    companion object {
        private const val TAG = "BrowserSecretsCrypto"
        private const val PREFS_NAME = "browser_encrypted_secrets"
    }

    private val fallback = mutableMapOf<String, String>()

    private val encryptedPrefs: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createPrefs() ?: run {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                val file = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
                if (file.exists()) file.delete()
            }
            createPrefs()
        }
    }

    private fun createPrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable: ${t.message}")
            null
        }
    }

    override fun get(key: String): String? = encryptedPrefs?.getString(key, null) ?: fallback[key]

    override fun put(key: String, value: String) {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().putString(key, value).commit()
        } else {
            fallback[key] = value
        }
    }

    override fun remove(key: String) {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().remove(key).commit()
        } else {
            fallback.remove(key)
        }
    }

    override fun clear() {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().clear().commit()
        } else {
            fallback.clear()
        }
    }
}

/**
 * Encrypted `.secrets` store with metadata JSON, local `{{SECRET:KEY}}` placeholder
 * substitution, optional workspace `.secrets` `chmod 600` mirroring, and reactive revision tracking.
 */
class BrowserSecretsStore(
    private val metadataDir: File,
    private val valueStorage: SecretValueStorage,
    private val workspaceRootsProvider: () -> List<File> = { emptyList() },
) {
    constructor(
        context: Context,
        workspaceRootsProvider: () -> List<File> = {
            val base = File(context.filesDir, "workspaces")
            base.listFiles()?.filter { it.isDirectory }?.map { File(it, "workspace") } ?: emptyList()
        },
    ) : this(
        metadataDir = File(context.filesDir, ".secrets"),
        valueStorage = EncryptedPrefsSecretValueStorage(context.applicationContext),
        workspaceRootsProvider = workspaceRootsProvider,
    )

    companion object {
        private const val TAG = "BrowserSecretsStore"
        private const val METADATA_FILE = "metadata.json"
        val SECRET_KEY_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        val PLACEHOLDER_REGEX = Regex("\\{\\{SECRET:([A-Za-z][A-Za-z0-9_]*)\\}\\}")
    }

    private val lock = Any()
    private val entries = mutableListOf<BrowserSecretMetadata>()

    private val _entriesFlow = MutableStateFlow<List<BrowserSecretMetadata>>(emptyList())
    val entriesFlow: StateFlow<List<BrowserSecretMetadata>> = _entriesFlow.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var syncToWorkspaceEnabled: Boolean = false

    init {
        loadMetadata()
    }

    fun setSyncToWorkspaceEnabled(enabled: Boolean) {
        val changed: Boolean
        synchronized(lock) {
            changed = syncToWorkspaceEnabled != enabled
            syncToWorkspaceEnabled = enabled
        }
        if (changed) {
            if (enabled) syncWorkspaceFiles() else clearWorkspaceFiles()
        }
    }

    fun listMetadata(): List<BrowserSecretMetadata> = synchronized(lock) {
        entries.sortedByDescending { it.updatedAt }
    }

    fun getMetadata(key: String): BrowserSecretMetadata? = synchronized(lock) {
        entries.firstOrNull { it.key == key }
    }

    fun hasKey(key: String): Boolean = synchronized(lock) {
        entries.any { it.key == key }
    }

    fun getValue(key: String): String? = synchronized(lock) {
        if (entries.none { it.key == key }) return null
        valueStorage.get(key)
    }

    fun allSecretValues(): List<String> = synchronized(lock) {
        entries.mapNotNull { entry ->
            valueStorage.get(entry.key)?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Save or update a secret. When [overwrite] is false and [key] already exists,
     * returns a failure; otherwise writes metadata + encrypted value.
     */
    fun saveSecret(
        key: String,
        value: String,
        domain: String = "",
        kind: BrowserSecretKind = BrowserSecretKind.SITE_LOGIN,
        note: String = "",
        savedByAgent: Boolean = false,
        overwrite: Boolean = false,
    ): Result<BrowserSecretMetadata> {
        val trimmedKey = key.trim()
        if (!SECRET_KEY_REGEX.matches(trimmedKey)) {
            return Result.failure(
                IllegalArgumentException("Secret key must match [A-Za-z][A-Za-z0-9_]* (got '$key')")
            )
        }
        if (value.isEmpty()) {
            return Result.failure(IllegalArgumentException("Secret value cannot be empty"))
        }

        val entry = synchronized(lock) {
            val existingIdx = entries.indexOfFirst { it.key == trimmedKey }
            if (existingIdx >= 0 && !overwrite) {
                return Result.failure(
                    IllegalStateException("Secret '$trimmedKey' already exists; pass overwrite=true to replace it.")
                )
            }
            val next = if (existingIdx >= 0) {
                entries[existingIdx].copy(
                    domain = domain.ifBlank { entries[existingIdx].domain },
                    kind = kind,
                    note = note.ifBlank { entries[existingIdx].note },
                    savedByAgent = savedByAgent,
                    updatedAt = System.currentTimeMillis(),
                ).also { entries[existingIdx] = it }
            } else {
                BrowserSecretMetadata(
                    key = trimmedKey,
                    domain = domain.trim(),
                    kind = kind,
                    note = note.trim(),
                    savedByAgent = savedByAgent,
                    updatedAt = System.currentTimeMillis(),
                ).also { entries.add(it) }
            }
            valueStorage.put(trimmedKey, value)
            persistMetadataLocked()
            _entriesFlow.value = entries.sortedByDescending { it.updatedAt }
            _revision.value++
            next
        }

        if (syncToWorkspaceEnabled) {
            syncWorkspaceFiles()
        }
        return Result.success(entry)
    }

    fun deleteSecret(key: String): Boolean {
        val removed = synchronized(lock) {
            val idx = entries.indexOfFirst { it.key == key }
            if (idx < 0) return false
            entries.removeAt(idx)
            valueStorage.remove(key)
            persistMetadataLocked()
            _entriesFlow.value = entries.sortedByDescending { it.updatedAt }
            _revision.value++
            true
        }
        if (removed && syncToWorkspaceEnabled) {
            syncWorkspaceFiles()
        }
        return removed
    }

    /**
     * Resolves `{{SECRET:KEY}}` placeholders in [input] locally right before WebView injection.
     * Throws [IllegalArgumentException] if any referenced secret key does not exist.
     */
    fun resolvePlaceholders(input: String): String {
        if (!input.contains("{{SECRET:")) return input
        return PLACEHOLDER_REGEX.replace(input) { match ->
            val key = match.groupValues[1]
            getValue(key) ?: throw IllegalArgumentException(
                "Unknown secret key '$key'. Check saved secrets or save it first."
            )
        }
    }

    fun formatPromptManifest(): String {
        val snapshot = listMetadata()
        if (snapshot.isEmpty()) return "Saved secrets: none."
        return buildString {
            appendLine("Saved secrets (keys & metadata only — use `{{SECRET:KEY}}` in `type` or `restore_cookies_from_secret(key)`):")
            for (item in snapshot) {
                append("  - ${item.key} [${item.kind.name.lowercase()}]")
                if (item.domain.isNotBlank()) append(" domain=${item.domain}")
                if (item.note.isNotBlank()) append(" (${item.note})")
                appendLine()
            }
        }.trimEnd()
    }

    private fun persistMetadataLocked() {
        try {
            metadataDir.mkdirs()
            val file = File(metadataDir, METADATA_FILE)
            val json = JsonInstant.encodeToString(ListSerializer(BrowserSecretMetadata.serializer()), entries)
            file.writeText(json)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to save secret metadata: ${t.message}")
        }
    }

    private fun loadMetadata() {
        synchronized(lock) {
            try {
                val file = File(metadataDir, METADATA_FILE)
                if (!file.exists()) return
                val decoded = JsonInstant.decodeFromString(
                    ListSerializer(BrowserSecretMetadata.serializer()),
                    file.readText(),
                )
                entries.clear()
                entries.addAll(decoded)
                _entriesFlow.value = entries.sortedByDescending { it.updatedAt }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to load secret metadata: ${t.message}")
            }
        }
    }

    fun syncWorkspaceFiles() {
        val snapshot = synchronized(lock) {
            entries.mapNotNull { meta ->
                val raw = valueStorage.get(meta.key) ?: return@mapNotNull null
                meta to raw
            }
        }
        val payload = buildString {
            appendLine("# LastChat Browser Secrets (auto-synced; mode 0600)")
            for ((meta, raw) in snapshot) {
                val encoded = if (meta.kind == BrowserSecretKind.COOKIE_BUNDLE || raw.contains('\n')) {
                    "base64:" + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                } else {
                    raw
                }
                val escaped = encoded.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
                appendLine("${meta.key}=\"$escaped\"")
            }
        }
        for (root in workspaceRootsProvider()) {
            runCatching {
                if (!root.exists()) return@runCatching
                val target = File(root, ".secrets")
                target.writeText(payload)
                target.setReadable(false, false)
                target.setWritable(false, false)
                target.setExecutable(false, false)
                target.setReadable(true, true)
                target.setWritable(true, true)
            }.onFailure {
                Log.w(TAG, "Failed to sync .secrets to ${root.absolutePath}: ${it.message}")
            }
        }
    }

    fun clearWorkspaceFiles() {
        for (root in workspaceRootsProvider()) {
            runCatching {
                val target = File(root, ".secrets")
                if (target.exists()) target.delete()
            }
        }
    }
}
