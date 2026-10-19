package me.rerere.locallm

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.common.platform.PlatformLog

/** An engine that is loaded and ready, together with the model + backends it was loaded with. */
class LoadedEngine internal constructor(
    val engine: Engine,
    val model: InstalledLocalModel,
    val backends: ResolvedBackends,
    internal val loadKey: String,
)

class LiteRtRuntime(
    private val context: Context,
    private val store: LocalModelStore,
) {
    private val _state = MutableStateFlow<LocalRuntimeState>(LocalRuntimeState.Idle)
    val state: StateFlow<LocalRuntimeState> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var loaded: LoadedEngine? = null

    private val crashPrefs = context.getSharedPreferences("local_llm_runtime", Context.MODE_PRIVATE)

    init {
        val pending = crashPrefs.getString(KEY_PENDING_GPU, null)
        if (pending != null) {
            crashPrefs.edit().remove(KEY_PENDING_GPU).commit()
            runCatching { markGpuCrashedBlocking(pending) }
        }
    }

    fun currentModelId(): String? = loaded?.model?.id

    suspend fun acquire(model: InstalledLocalModel): LoadedEngine = mutex.withLock {
        val backends = AcceleratorProbe.resolve(model)
        val kvCacheTokens = resolveKvCacheSize(model)
        val key = loadKey(model, backends, kvCacheTokens)

        loaded?.let { if (it.loadKey == key) return@withLock it }

        disposeLocked()

        when (val mem = MemoryGuard.check(context, model.sizeInBytes, model.minDeviceMemoryGb)) {
            is MemoryCheck.Insufficient -> {
                _state.value = LocalRuntimeState.Error(model.id, "insufficient_memory")
                throw InsufficientMemoryException(mem)
            }
            is MemoryCheck.Advisory -> PlatformLog.w(
                TAG,
                "Attempting ${model.id} on ${mem.deviceMb} MB total RAM; allowlist recommends ${mem.recommendedMb} MB",
            )
            MemoryCheck.Ok -> Unit
        }

        _state.value = LocalRuntimeState.LoadingModel(model.id, model.displayName)

        val engine = try {
            loadEngine(model, backends, kvCacheTokens)
        } catch (t: Throwable) {
            if (t is InsufficientMemoryException) throw t
            if (backends.usingGpu) {
                store.updateRuntimeFlags(model.id) { it.copy(gpuCrashed = true) }
                val cpuModel = model.copy(runtimeFlags = model.runtimeFlags.copy(gpuCrashed = true))
                val cpuBackends = AcceleratorProbe.resolve(cpuModel)
                _state.value = LocalRuntimeState.SwitchedToCpu(model.id, model.displayName)
                val cpuEngine = try {
                    loadEngine(cpuModel, cpuBackends, kvCacheTokens)
                } catch (cpuError: Throwable) {
                    val wrapped = IncompatibleModelException.from(cpuError)
                    _state.value = LocalRuntimeState.Error(model.id, wrapped.message ?: "load_failed")
                    throw wrapped
                }
                val result = LoadedEngine(cpuEngine, cpuModel, cpuBackends, loadKey(cpuModel, cpuBackends, kvCacheTokens))
                loaded = result
                _state.value = LocalRuntimeState.Ready(model.id, model.displayName, cpuBackends.effective)
                return@withLock result
            }
            val wrapped = IncompatibleModelException.from(t)
            _state.value = LocalRuntimeState.Error(model.id, wrapped.message ?: "load_failed")
            throw wrapped
        }

        val result = LoadedEngine(engine, model, backends, key)
        loaded = result
        _state.value = LocalRuntimeState.Ready(model.id, model.displayName, backends.effective)
        result
    }

    @OptIn(ExperimentalApi::class)
    private fun loadEngine(
        model: InstalledLocalModel,
        backends: ResolvedBackends,
        kvCacheTokens: Int,
    ): Engine {
        val modelPath = model.filePath
        if (modelPath.endsWith(".litertlm", ignoreCase = true)) {
            ModelInstall.installedFileProblem(java.io.File(modelPath))?.let { problem ->
                throw IncompatibleModelException(
                    IncompatibleModelException.Reason.INVALID_FILE,
                    "file header check failed: $problem",
                    IllegalStateException(problem),
                )
            }
        }
        val cacheDir: String? = if (modelPath.startsWith("/data/local/tmp")) {
            context.getExternalFilesDir(null)?.absolutePath
        } else {
            null
        }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backends.main,
            visionBackend = backends.vision,
            audioBackend = backends.audio,
            maxNumTokens = kvCacheTokens,
            maxNumImages = null,
            cacheDir = cacheDir,
        )
        if (backends.usingGpu) armGpuCrashMarker(model.id)
        return try {
            // LiteRT-LM snapshots this flag when Engine is created; token usage needs it enabled.
            ExperimentalFlags.enableBenchmark = true
            ExperimentalFlags.enableSpeculativeDecoding = false

            Engine(config).also { engine ->
                engine.initialize()
                @OptIn(ExperimentalApi::class)
                ExperimentalFlags.enableSpeculativeDecoding = false
            }
        } finally {
            if (backends.usingGpu) disarmGpuCrashMarker()
        }
    }

    internal fun setGenerating(model: InstalledLocalModel) {
        _state.value = LocalRuntimeState.Generating(model.id, model.displayName)
    }

    internal fun setReady(model: InstalledLocalModel, accelerator: LocalAccelerator) {
        _state.value = LocalRuntimeState.Ready(model.id, model.displayName, accelerator)
    }

    internal fun setError(model: InstalledLocalModel?, message: String) {
        _state.value = LocalRuntimeState.Error(model?.id, message)
    }

    suspend fun unload() = mutex.withLock { disposeLocked() }

    private fun disposeLocked() {
        loaded?.let { runCatching { it.engine.close() } }
        loaded = null
        if (_state.value !is LocalRuntimeState.Error) _state.value = LocalRuntimeState.Idle
    }

    private fun armGpuCrashMarker(modelId: String) {
        crashPrefs.edit().putString(KEY_PENDING_GPU, modelId).commit()
    }

    private fun disarmGpuCrashMarker() {
        crashPrefs.edit().remove(KEY_PENDING_GPU).commit()
    }

    private fun markGpuCrashedBlocking(modelId: String) {
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            store.updateRuntimeFlags(modelId) { it.copy(gpuCrashed = true) }
        }
    }

    /**
     * The engine refused the model file (bad header, newer/older format, or a tensor/op the
     * backend can't run). [detail] is the native message without C++ source locations.
     */
    class IncompatibleModelException(
        val reason: Reason,
        val detail: String,
        cause: Throwable,
    ) : Exception(detail, cause) {
        enum class Reason { INVALID_FILE, UNSUPPORTED }

        companion object {
            fun from(t: Throwable): IncompatibleModelException {
                val raw = t.message.orEmpty()
                val reason = if (raw.contains("magic number", ignoreCase = true) ||
                    raw.contains("failed to read", ignoreCase = true)
                ) Reason.INVALID_FILE else Reason.UNSUPPORTED
                return IncompatibleModelException(reason, cleanNativeMessage(raw), t)
            }

            /** Drops `[third_party/...cc:123]` locations and tree glyphs from LiteRT errors. */
            fun cleanNativeMessage(raw: String): String =
                raw.replace(Regex("""\[[^\]]*\.(cc|h|cpp):\d+\]"""), "")
                    .replace("└", "")
                    .replace(Regex("""(ERROR:\s*)+"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .trimEnd(':', ' ')
                    .ifBlank { "load_failed" }
                    .take(300)
        }
    }

    class InsufficientMemoryException(val info: MemoryCheck.Insufficient) : Exception("insufficient_memory")

    companion object {
        private const val TAG = "LiteRtRuntime"
        private const val KEY_PENDING_GPU = "pending_gpu_model"

        private fun loadKey(model: InstalledLocalModel, backends: ResolvedBackends, kvCacheTokens: Int): String =
            listOf(
                model.filePath,
                java.io.File(model.filePath).let { "${it.length()}:${it.lastModified()}" },
                backends.effective.name,
                (backends.vision != null).toString(),
                (backends.audio != null).toString(),
                kvCacheTokens.toString(),
            ).joinToString("|")
    }

    private fun resolveKvCacheSize(model: InstalledLocalModel): Int {
        return model.effectiveRuntimeContextLength(MemoryGuard.deviceTotalRamGb(context))
    }
}
