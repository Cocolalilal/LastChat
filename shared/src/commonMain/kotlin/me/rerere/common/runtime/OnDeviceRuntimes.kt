package me.rerere.common.runtime

/**
 * Shared on-device LLM contract. Android binds LiteRT-LM; iOS uses an honest
 * unavailable shim until a native runtime exists. Chat orchestration stays in
 * shared/iOS Kotlin rather than a second engine.
 */
interface OnDeviceLlmRuntime {
    val available: Boolean
    val unavailableReason: String?

    suspend fun listModels(): List<OnDeviceLlmModel>

    suspend fun generateText(modelId: String, prompt: String): String

    suspend fun embed(modelId: String, text: String): List<Float>?
}

data class OnDeviceLlmModel(
    val id: String,
    val displayName: String,
    val supportsEmbeddings: Boolean = false,
)

class UnavailableOnDeviceLlmRuntime(
    override val unavailableReason: String = DEFAULT_UNAVAILABLE_REASON,
) : OnDeviceLlmRuntime {
    override val available: Boolean = false

    override suspend fun listModels(): List<OnDeviceLlmModel> = emptyList()

    override suspend fun generateText(modelId: String, prompt: String): String {
        error(unavailableReason)
    }

    override suspend fun embed(modelId: String, text: String): List<Float>? = null

    companion object {
        const val DEFAULT_UNAVAILABLE_REASON =
            "On-device LiteRT-LM inference is not available on this platform. Chat still uses the shared generation loop with cloud providers."
    }
}

/**
 * Shared workspace/sandbox contract. Android binds PRoot; iOS keeps the same
 * tool names in the generation loop but reports a disabled sandbox.
 */
interface OnDeviceWorkspaceRuntime {
    val available: Boolean
    val unavailableReason: String?

    suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry>

    suspend fun readFile(path: String): String

    suspend fun writeFile(path: String, text: String)

    suspend fun exec(command: String, timeoutSeconds: Long = 30): OnDeviceWorkspaceCommandResult
}

data class OnDeviceWorkspaceEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
)

data class OnDeviceWorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
)

class UnavailableOnDeviceWorkspaceRuntime(
    override val unavailableReason: String = DEFAULT_UNAVAILABLE_REASON,
) : OnDeviceWorkspaceRuntime {
    override val available: Boolean = false

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> = emptyList()

    override suspend fun readFile(path: String): String {
        error(unavailableReason)
    }

    override suspend fun writeFile(path: String, text: String) {
        error(unavailableReason)
    }

    override suspend fun exec(command: String, timeoutSeconds: Long): OnDeviceWorkspaceCommandResult {
        error(unavailableReason)
    }

    companion object {
        const val DEFAULT_UNAVAILABLE_REASON =
            "The Linux PRoot workspace sandbox is not available on this platform. Attachment storage still uses the shared app-container file store."
    }
}
