package me.rerere.rikkahub.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.service.PythonServerService
import me.rerere.rikkahub.service.workspace.WorkspaceEnvironmentManager
import me.rerere.rikkahub.service.workspace.WorkspaceEnvironmentTask
import me.rerere.rikkahub.service.workspace.defaultRootfsUrl
import me.rerere.workspace.WorkspaceShellStatus
import java.io.File

data class PythonPlugin(
    val id: String,
    val fileName: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "User",
    val enabled: Boolean = true,
    val hooks: List<String> = emptyList(),
    val filePath: String = "",
)

data class PythonServerProcess(
    val id: String,
    val name: String,
    val command: String,
    val port: Int?,
    val startedAt: Long = System.currentTimeMillis(),
    val isRunning: Boolean = true,
    val logFilePath: String = "",
)

data class PythonEnvStatus(
    val hasWorkspace: Boolean = false,
    val workspace: WorkspaceEntity? = null,
    val isRootfsReady: Boolean = false,
    val isPythonInstalled: Boolean = false,
    val isBusy: Boolean = false,
    val currentTask: WorkspaceEnvironmentTask? = null,
)

data class PythonPluginUiState(
    val customFont: String? = null,
    val typingAnimation: String? = null, // "particles", "neon_glow", "matrix", null
    val customEmojis: Map<String, String> = emptyMap(),
)

class PythonPluginManager(
    private val context: Context,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceDao: WorkspaceDAO,
    private val environmentManager: WorkspaceEnvironmentManager,
    private val settingsStore: SettingsStore,
) {
    private val _uiState = MutableStateFlow(PythonPluginUiState())
    val uiState: StateFlow<PythonPluginUiState> = _uiState.asStateFlow()

    private val _runningServers = MutableStateFlow<Map<String, PythonServerProcess>>(emptyMap())
    val runningServers: StateFlow<Map<String, PythonServerProcess>> = _runningServers.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val disabledPluginsFile by lazy {
        File(context.filesDir, "disabled_python_plugins.txt")
    }

    fun isAnyServerRunning(): Boolean = _runningServers.value.isNotEmpty()

    suspend fun startServer(
        workspace: WorkspaceEntity,
        name: String,
        command: String,
        port: Int? = null,
    ): PythonServerProcess = withContext(Dispatchers.IO) {
        val id = "server_" + System.currentTimeMillis().toString().takeLast(6)
        val logFile = File(context.filesDir, "workspaces/${workspace.root}/files/server_${id}.log")
        val proc = PythonServerProcess(
            id = id,
            name = name,
            command = command,
            port = port,
            startedAt = System.currentTimeMillis(),
            isRunning = true,
            logFilePath = logFile.absolutePath,
        )

        _runningServers.update { it + (id to proc) }
        PythonServerService.start(context, name, port)

        val job = serverScope.launch {
            try {
                workspaceRepository.executeCommand(
                    id = workspace.id,
                    command = "$command > \"/workspace/server_${id}.log\" 2>&1",
                    timeoutMillis = Long.MAX_VALUE,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Server $id terminated with error", e)
            } finally {
                _runningServers.update { it - id }
                if (_runningServers.value.isEmpty()) {
                    PythonServerService.stop(context)
                }
            }
        }
        activeJobs[id] = job
        proc
    }

    fun stopServer(id: String): Boolean {
        val job = activeJobs.remove(id)
        job?.cancel()
        _runningServers.update { it - id }
        if (_runningServers.value.isEmpty()) {
            PythonServerService.stop(context)
        }
        return true
    }

    fun stopAllServers() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _runningServers.value = emptyMap()
        PythonServerService.stop(context)
    }

    suspend fun addLocalMcpServer(name: String, url: String, transport: String = "sse") = withContext(Dispatchers.IO) {
        val config = if (transport.lowercase() == "streamable_http") {
            McpServerConfig.StreamableHTTPServer(
                commonOptions = McpCommonOptions(name = name),
                url = url
            )
        } else {
            McpServerConfig.SseTransportServer(
                commonOptions = McpCommonOptions(name = name),
                url = url
            )
        }
        val currentSettings = settingsStore.settingsFlowRaw.first()
        val exists = currentSettings.mcpServers.any { it.commonOptions.name.equals(name, ignoreCase = true) }
        if (!exists) {
            settingsStore.update { it.copy(mcpServers = it.mcpServers + config) }
        }
    }

    private fun getDisabledPluginIds(): Set<String> {
        if (!disabledPluginsFile.exists()) return emptySet()
        return runCatching {
            disabledPluginsFile.readLines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun setPluginDisabledState(pluginId: String, disabled: Boolean) {
        val current = getDisabledPluginIds().toMutableSet()
        if (disabled) current.add(pluginId) else current.remove(pluginId)
        runCatching {
            disabledPluginsFile.writeText(current.joinToString("\n"))
        }
    }

    fun observeWorkspaces(): Flow<List<WorkspaceEntity>> = workspaceDao.listFlow()

    suspend fun getActiveWorkspace(): WorkspaceEntity? = withContext(Dispatchers.IO) {
        workspaceDao.getAll().firstOrNull()
    }

    suspend fun checkEnvironmentStatus(): PythonEnvStatus = withContext(Dispatchers.IO) {
        val workspace = getActiveWorkspace() ?: return@withContext PythonEnvStatus()
        val isRootfsReady = workspace.shellStatus == WorkspaceShellStatus.READY.name
        val isPythonInstalled = if (isRootfsReady) {
            runCatching { workspaceRepository.isPythonInstalled(workspace.id) }.getOrDefault(false)
        } else false

        val currentTask = environmentManager.tasks.value[workspace.id]
        val isBusy = environmentManager.isBusy(workspace.id)

        PythonEnvStatus(
            hasWorkspace = true,
            workspace = workspace,
            isRootfsReady = isRootfsReady,
            isPythonInstalled = isPythonInstalled,
            isBusy = isBusy,
            currentTask = currentTask,
        )
    }

    fun installFullEnvironment(workspace: WorkspaceEntity) {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val url = defaultRootfsUrl(nativeLibDir)
        environmentManager.installRootfs(
            workspaceId = workspace.id,
            workspaceName = workspace.name,
            url = url,
            installPythonAfter = true,
        )
    }

    fun installPythonOnly(workspace: WorkspaceEntity) {
        environmentManager.installPython(
            workspaceId = workspace.id,
            workspaceName = workspace.name,
        )
    }

    fun getPluginsDirectory(workspace: WorkspaceEntity): File {
        val dir = File(context.filesDir, "workspaces/${workspace.root}/files/plugins")
        if (!dir.exists()) {
            dir.mkdirs()
            createSamplePlugin(dir)
        }
        return dir
    }

    suspend fun listPlugins(workspace: WorkspaceEntity): List<PythonPlugin> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getPluginsDirectory(workspace)
            val files = dir.listFiles { f -> f.isFile && f.extension == "py" } ?: emptyArray()
            val disabledSet = getDisabledPluginIds()

            files.map { file ->
                parsePluginFile(file, disabledSet)
            }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        setPluginDisabledState(pluginId, !enabled)
    }

    suspend fun deletePlugin(workspace: WorkspaceEntity, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val dir = getPluginsDirectory(workspace)
        val file = File(dir, fileName)
        if (file.exists()) file.delete() else false
    }

    suspend fun readPluginCode(workspace: WorkspaceEntity, fileName: String): String = withContext(Dispatchers.IO) {
        val dir = getPluginsDirectory(workspace)
        val file = File(dir, fileName)
        if (file.exists()) file.readText() else ""
    }

    suspend fun savePluginCode(workspace: WorkspaceEntity, fileName: String, code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getPluginsDirectory(workspace)
            val file = File(dir, fileName)
            file.writeText(code)
            true
        }.getOrDefault(false)
    }

    private fun parsePluginFile(file: File, disabledSet: Set<String>): PythonPlugin {
        val id = file.nameWithoutExtension
        val lines = runCatching { file.readLines().take(25) }.getOrDefault(emptyList())
        var name = id.replace('_', ' ').replaceFirstChar { it.uppercase() }
        var description = "Custom Python extension"
        var version = "1.0.0"
        var author = "User"
        val hooks = mutableListOf<String>()

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("# Name:", ignoreCase = true)) {
                name = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("# Description:", ignoreCase = true)) {
                description = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("# Version:", ignoreCase = true)) {
                version = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("# Author:", ignoreCase = true)) {
                author = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("# Hooks:", ignoreCase = true)) {
                hooks.addAll(trimmed.substringAfter(":").split(",").map { it.trim().lowercase() })
            } else if (trimmed.contains("@hook.")) {
                val hookMatch = Regex("@hook\\.(\\w+)").find(trimmed)
                if (hookMatch != null) {
                    hooks.add(hookMatch.groupValues[1].lowercase())
                }
            }
        }

        return PythonPlugin(
            id = id,
            fileName = file.name,
            name = name,
            description = description,
            version = version,
            author = author,
            enabled = id !in disabledSet,
            hooks = hooks.distinct(),
            filePath = file.absolutePath,
        )
    }

    private fun createSamplePlugin(dir: File) {
        val sample = File(dir, "starter_extension.py")
        if (sample.exists()) return
        sample.writeText(
            """
# Name: Starter Extension
# Description: Example ExteraGram-style extension demonstrating typing fx, custom fonts, emojis, and prompt hooks.
# Version: 1.0.0
# Author: Velvet & Ammar
# Hooks: typing_animation, custom_emojis, before_prompt, after_reply

class Hook:
    @staticmethod
    def before_prompt(prompt: str) -> str:
        # Intercept and modify outgoing prompt
        return prompt

    @staticmethod
    def after_reply(reply: str) -> str:
        # Intercept and transform AI response
        return reply

    @staticmethod
    def typing_animation() -> dict:
        # Options: "particles", "neon_glow", "matrix", None
        return {
            "effect": "neon_glow",
            "color": "#c1122c",
            "intensity": "high"
        }

    @staticmethod
    def custom_emojis() -> dict:
        # Maps :shortcodes: to inline graphics
        return {
            ":fire:": "🔥",
            ":sparkles:": "✨"
        }

if __name__ == "__main__":
    import sys, json
    # CLI entry for LastChat hook runner
    print(json.dumps({"status": "ready", "plugin": "Starter Extension"}))
""".trimIndent()
        )
    }

    companion object {
        private const val TAG = "PythonPluginManager"
    }
}
