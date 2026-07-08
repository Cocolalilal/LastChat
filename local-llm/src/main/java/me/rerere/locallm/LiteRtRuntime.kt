package me.rerere.locallm

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** An engine that is loaded and ready, together with the model + backends it was loaded with. */
class LoadedEngine internal constructor(
    val engine: Engine,
    val model: InstalledLocalModel,
    val backends: ResolvedBackends,
    internal val loadKey: String,
)

/**
 * Owns the single live LiteRT-LM [Engine]. On-device models are large, so only one is resident at a
 * time; requesting a different model (or a config change that affects the engine) disposes the previous
 * engine and loads the new one.
 *
 * Crash safety: the GPU backend can hard-crash the process on some devices. Before a GPU load we persist
 * a marker; if the process dies mid-load, on next launch we detect the marker and permanently fall this
 * model back to CPU under [LocalAccelerator.AUTO]. Catchable JNI/GPU exceptions are handled inline with
 * the same fallback.
 */
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
        // Detect a GPU load that crashed the process last session and pin that model to CPU.
        val pending = crashPrefs.getString(KEY_PENDING_GPU, null)
        if (pending != null) {
            crashPrefs.edit().remove(KEY_PENDING_GPU).apply()
            // Flag the model so AUTO avoids the GPU next time.
            markGpuCrashedBlocking(pending)
        }
    }

    fun currentModelId(): String? = loaded?.model?.id

    /**
     * Returns a ready [LoadedEngine] for [model], loading it (and disposing any other) if necessary.
     * Must be called off the main thread — model initialization is slow.
     */
    suspend fun acquire(model: InstalledLocalModel): LoadedEngine = mutex.withLock {
        val backends = AcceleratorProbe.resolve(model)

        // KV cache size (maxNumTokens in EngineConfig) — this is the total number of tokens
        // (input + output) the engine will cache, and it directly determines memory consumption.
        //
        // The model's maxContextLength is the ceiling the model *could* support, NOT what should be
        // allocated by default. Allocating a 32K-token KV cache (as the old code did) consumes
        // gigabytes of RAM and causes OOM crashes on devices that run the same model fine in Google's
        // Edge Gallery app (which passes maxTokens = 1024–4096).
        //
        // Default to maxTokens (the decode/output budget). If the user explicitly sets contextLength,
        // honor it but cap at the model's maxContextLength ceiling.
        val kvCacheTokens = resolveKvCacheSize(model)
        val key = loadKey(model, backends, kvCacheTokens)

        loaded?.let { if (it.loadKey == key) return@withLock it }

        // A different model/config is requested: dispose the old engine first to free RAM.
        disposeLocked()

        when (val mem = MemoryGuard.check(context, model.sizeInBytes, kvCacheTokens)) {
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
            // A catchable GPU/JNI failure: fall back to CPU once.
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
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
        val config = EngineConfig(
            /* modelPath = */ model.filePath,
            /* backend = */ backends.main,
            /* visionBackend = */ backends.vision,
            /* audioBackend = */ backends.audio,
            /* maxNumTokens = */ kvCacheTokens,
            /* maxNumImages = */ if (model.supportsImage) MAX_IMAGES else null,
            /* cacheDir = */ cacheDir.absolutePath,
        )
        if (backends.usingGpu) armGpuCrashMarker(model.id)
        return try {
            Engine(config).also { it.initialize() }
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

    /** Disposes the loaded engine (e.g. when the model is deleted or memory is needed elsewhere). */
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
        // Fire-and-forget flag update; done on a background thread to avoid blocking init.
        Thread {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                runCatching { store.updateRuntimeFlags(modelId) { it.copy(gpuCrashed = true) } }
            }
        }.start()
    }

    class InsufficientMemoryException(val info: MemoryCheck.Insufficient) : Exception("insufficient_memory")

    companion object {
        private const val TAG = "LiteRtRuntime"
        private const val KEY_PENDING_GPU = "pending_gpu_model"
        private const val MAX_IMAGES = 8

        /**
         * Resolves the KV cache size (maxNumTokens) to pass to EngineConfig.
         *
         * LiteRT-LM's `maxNumTokens` controls the KV cache allocation — it is the total number of
         * tokens (prefill + decode) the engine will hold. A larger value means more memory consumed
         * even before generation starts. For reference, Google's Edge Gallery app passes
         * `maxTokens` (1024–4096) as this value, NOT the model's full context window.
         *
         * Priority:
         * 1. User-explicit `contextLength` override (capped at the model's maxContextLength ceiling)
         * 2. The model's curated `maxTokens` (the decode budget — same as Gallery)
         */
        private fun resolveKvCacheSize(model: InstalledLocalModel): Int {
            val maxTokens = model.config.maxTokens ?: model.defaultConfig.maxTokens
            val userContextLength = model.config.contextLength
            val modelMaxContext = model.defaultConfig.maxContextLength

            return when {
                // User explicitly set a context length — honor it but cap at the model's ceiling.
                userContextLength != null -> {
                    val capped = modelMaxContext?.let { minOf(userContextLength, it) } ?: userContextLength
                    maxOf(capped, maxTokens) // never go below maxTokens (decode budget)
                }
                // Default: use maxTokens (matches Google Edge Gallery behavior).
                else -> maxTokens
            }
        }

        private fun loadKey(model: InstalledLocalModel, backends: ResolvedBackends, kvCacheTokens: Int): String =
            listOf(model.filePath, backends.effective.name, kvCacheTokens.toString()).joinToString("|")
    }
}
