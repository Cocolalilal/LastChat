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

        when (val mem = MemoryGuard.check(context, model.sizeInBytes, kvCacheTokens, model.minDeviceMemoryGb)) {
            is MemoryCheck.Insufficient -> {
                _state.value = LocalRuntimeState.Error(model.id, "insufficient_memory")
                throw InsufficientMemoryException(mem)
            }
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
                val cpuEngine = loadEngine(cpuModel, cpuBackends, kvCacheTokens)
                val result = LoadedEngine(cpuEngine, cpuModel, cpuBackends, loadKey(cpuModel, cpuBackends, kvCacheTokens))
                loaded = result
                _state.value = LocalRuntimeState.Ready(model.id, model.displayName, cpuBackends.effective)
                return@withLock result
            }
            _state.value = LocalRuntimeState.Error(model.id, t.message ?: "load_failed")
            throw t
        }

        val result = LoadedEngine(engine, model, backends, key)
        loaded = result
        _state.value = LocalRuntimeState.Ready(model.id, model.displayName, backends.effective)
        result
    }

    private fun loadEngine(
        model: InstalledLocalModel,
        backends: ResolvedBackends,
        kvCacheTokens: Int,
    ): Engine {
        val modelPath = model.filePath
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
            @OptIn(ExperimentalApi::class)
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

    class InsufficientMemoryException(val info: MemoryCheck.Insufficient) : Exception("insufficient_memory")

    companion object {
        private const val KEY_PENDING_GPU = "pending_gpu_model"

        private fun requestedKvCacheSize(model: InstalledLocalModel): Int {
            val maxTokens = model.config.maxTokens ?: model.defaultConfig.maxTokens
            val userContextLength = model.config.contextLength
            val modelMaxContext = model.defaultConfig.maxContextLength

            return when {
                userContextLength != null -> {
                    val capped = modelMaxContext?.let { minOf(userContextLength, it) } ?: userContextLength
                    maxOf(capped, maxTokens)
                }
                else -> maxTokens
            }
        }

        private fun loadKey(model: InstalledLocalModel, backends: ResolvedBackends, kvCacheTokens: Int): String =
            listOf(model.filePath, backends.effective.name, kvCacheTokens.toString()).joinToString("|")
    }

    private fun resolveKvCacheSize(model: InstalledLocalModel): Int {
        val requested = requestedKvCacheSize(model)
        val safeCap = MemoryGuard.safeContextTokenCap(MemoryGuard.deviceTotalRamGb(context))
        return minOf(requested, safeCap)
    }
}
