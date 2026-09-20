package me.rerere.common.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Shared on-device LLM contract. Android binds LiteRT-LM; iOS uses an honest
 * unavailable shim until a native runtime exists. Chat orchestration
 * ([me.rerere.ai.provider.OnDeviceLlmProvider], GenerationHandler, and
 * IosAppController) talks to this interface rather than LiteRT directly.
 */
interface OnDeviceLlmRuntime {
    val available: Boolean
    val unavailableReason: String?

    suspend fun listModels(): List<OnDeviceLlmModel>

    suspend fun generateText(modelId: String, prompt: String): String

    suspend fun generateChat(
        modelId: String,
        messages: List<OnDeviceChatMessage>,
    ): OnDeviceChatChunk {
        val prompt = flattenOnDeviceChat(messages)
        val text = generateText(modelId, prompt)
        return OnDeviceChatChunk(text = text, finishReason = "stop", finished = true)
    }

    fun streamChat(
        modelId: String,
        messages: List<OnDeviceChatMessage>,
    ): Flow<OnDeviceChatChunk> = flow {
        emit(generateChat(modelId, messages))
    }

    suspend fun embed(modelId: String, text: String): List<Float>?
}

data class OnDeviceLlmModel(
    val id: String,
    val displayName: String,
    val supportsEmbeddings: Boolean = false,
)

data class OnDeviceChatMessage(
    val role: String,
    val text: String,
)

data class OnDeviceChatChunk(
    val text: String = "",
    val reasoning: String = "",
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val finished: Boolean = false,
)

fun flattenOnDeviceChat(messages: List<OnDeviceChatMessage>): String =
    messages.joinToString("\n\n") { message ->
        "${message.role}: ${message.text}"
    }

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

    suspend fun writeFile(path: String, text: String, overwrite: Boolean = true)

    suspend fun exec(
        command: String,
        timeoutSeconds: Long = 30,
        cwd: String? = null,
        stdin: ByteArray? = null,
    ): OnDeviceWorkspaceCommandResult
}

data class OnDeviceWorkspaceEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val name: String = path.trimEnd('/').substringAfterLast('/').ifBlank { "/" },
    val updatedAtEpochMs: Long = 0L,
)

data class OnDeviceWorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
)

class UnavailableOnDeviceWorkspaceRuntime(
    override val unavailableReason: String = DEFAULT_UNAVAILABLE_REASON,
) : OnDeviceWorkspaceRuntime {
    override val available: Boolean = false

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> = emptyList()

    override suspend fun readFile(path: String): String {
        error(unavailableReason)
    }

    override suspend fun writeFile(path: String, text: String, overwrite: Boolean) {
        error(unavailableReason)
    }

    override suspend fun exec(
        command: String,
        timeoutSeconds: Long,
        cwd: String?,
        stdin: ByteArray?,
    ): OnDeviceWorkspaceCommandResult {
        error(unavailableReason)
    }

    companion object {
        const val DEFAULT_UNAVAILABLE_REASON =
            "The Linux PRoot workspace sandbox is not available on this platform. Attachment storage still uses the shared app-container file store."
    }
}
