package me.rerere.ai.mnn

import android.util.Log
import java.io.File
import java.util.stream.Collectors
import kotlin.concurrent.Volatile
import android.util.Pair
import android.content.Context
import android.app.ActivityManager
import me.rerere.ai.mnn.model.ChatDataItem

private const val TAG = "LlmSession"

class LlmSession(
    private val modelId: String,
    override var sessionId: String,
    private val configPath: String,
    var savedHistory: List<ChatDataItem>?,
) : ChatSession {
    private var extraAssistantPrompt: String? = null
    override var supportOmni: Boolean = false
    private var nativePtr: Long = 0

    @Volatile
    private var modelLoading = false

    @Volatile
    private var generating = false

    @Volatile
    private var releaseRequested = false

    private var keepHistory = false

    override fun getHistory(): List<ChatDataItem>? {
        return savedHistory
    }

    override fun setHistory(history: List<ChatDataItem>?) {
    }

    override fun load() {
//        Log.d(TAG, "MNN_DEBUG load begin")
//        modelLoading = true
//        var historyStringList: List<String>? = null
//        val currentHistory = this.savedHistory
//        if (!currentHistory.isNullOrEmpty()) {
//            historyStringList =
//                currentHistory.stream()
//                    .map { obj: ChatDataItem -> obj.text }
//                    .filter { obj: String? -> obj != null }
//                    .map { obj: String? -> obj!! }
//                    .collect(Collectors.toList())
//        }
//        val config = ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))!!
//        var rootCacheDir: String? = ""
//        if (config.useMmap == true) {
//            rootCacheDir = MmapUtils.getMmapDir(modelId)
//            File(rootCacheDir).mkdirs()
//        }
//        val backend = config.backendType
//        val configMap = HashMap<String, Any>().apply {
//            put("is_r1", ModelUtils.isR1Model(modelId))
//            put("mmap_dir", rootCacheDir ?: "")
//            put("keep_history", keepHistory)
//        }
//        val extraConfig = ModelConfig.loadMergedConfig(configPath, getExtraConfigFile(modelId))?.apply {
//            this.assistantPromptTemplate = extraAssistantPrompt
//            this.backendType = backend
//        }
//        Log.d(TAG, "MNN_DEBUG load initNative")
//        nativePtr = initNative(
//            configPath,
//            historyStringList,
//            if (extraConfig != null) {
//                Gson().toJson(extraConfig)
//            } else {
//                "{}"
//            },
//            Gson().toJson(configMap)
//        )
//        Log.d(TAG, "MNN_DEBUG load initNative end")
//        modelLoading = false
//        if (releaseRequested) {
//            release()
//        }
    }

    private fun generateNewSessionId(): String {
        this.sessionId = System.currentTimeMillis().toString()
        return this.sessionId
    }

    override fun generate(
        prompt: String,
        params: Map<String, Any>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any> {
        Log.d(TAG, "start generate prompt: $prompt")
        synchronized(this) {
            Log.d(TAG, "MNN_DEBUG submit$prompt")
            generating = true
            val result = submitNative(nativePtr, prompt, keepHistory, progressListener)
            generating = false
            if (releaseRequested) {
                release()
            }
            return result
        }
    }

    override fun reset(): String {
        synchronized(this) {
            resetNative(nativePtr)
        }
        return generateNewSessionId()
    }

    override fun release() {
        synchronized(this) {
            Log.d(
                TAG,
                "MNN_DEBUG release nativePtr: $nativePtr mGenerating: $generating"
            )
            if (!generating && !modelLoading) {
                releaseInner()
            } else {
                releaseRequested = true
                while (generating || modelLoading) {
                    try {
                        (this as Object).wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e(TAG, "Thread interrupted while waiting for release", e)
                    }
                }
                releaseInner()
            }
        }
    }

    private fun releaseInner() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr)
            nativePtr = 0
            // provide().removeSession(sessionId)
            (this as Object).notifyAll()
        }
    }

    private external fun initNative(
        configPath: String?,
        history: List<String>?,
        mergedConfigStr: String?,
        configJsonStr: String?
    ): Long

    private external fun submitNative(
        instanceId: Long,
        input: String,
        keepHistory: Boolean,
        listener: GenerateProgressListener
    ): HashMap<String, Any>

    private external fun resetNative(instanceId: Long)

    private external fun getDebugInfoNative(instanceId: Long): String

    private external fun releaseNative(instanceId: Long)

    override fun setKeepHistory(keepHistory: Boolean) {
        this.keepHistory = keepHistory
    }

    override fun setEnableAudioOutput(enable: Boolean) {
        updateEnableAudioOutputNative(nativePtr, enable)
    }

    override val debugInfo
        get() = getDebugInfoNative(nativePtr) + "\n"

    fun updateMaxNewTokens(maxNewTokens: Int) {
        updateMaxNewTokensNative(nativePtr, maxNewTokens)
    }

    fun updateSystemPrompt(systemPrompt: String) {
        updateSystemPromptNative(nativePtr, systemPrompt)
    }

    fun updateAssistantPrompt(assistantPrompt: String) {
        extraAssistantPrompt = assistantPrompt
        updateAssistantPromptNative(nativePtr, assistantPrompt)
    }

    private external fun updateEnableAudioOutputNative(llmPtr: Long, enable: Boolean)


    private external fun updateMaxNewTokensNative(llmPtr: Long, maxNewTokens: Int)

    private external fun updateSystemPromptNative(llmPtr: Long, systemPrompt: String)

    private external fun updateAssistantPromptNative(llmPtr: Long, assistantPrompt: String)


    companion object {
        const val TAG: String = "LlmSession"

        init {
            System.loadLibrary("mnnllmapp")
        }
    }


    // 新增：支持完整历史消息的公开方法
    fun submitFullHistory(
        history: List<Pair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any> {
        synchronized(this) {
            // 转换类型：kotlin.Pair -> android.util.Pair
            val androidHistory = history.map { android.util.Pair(it.first, it.second) }
            // 调用JNI方法，移除不必要的类型转换
            val result = submitFullHistoryNative(nativePtr, androidHistory, progressListener)
            generating = false
            return result
        }
    }

    private external fun submitFullHistoryNative(
        nativePtr: Long,
        history: List<android.util.Pair<String, String>>,
        progressListener: GenerateProgressListener
    ): HashMap<String, Any>

    fun modelId(): String {
        //创建一个临时变量，避免修改原始的modelId
        return modelId

    }

    fun getSystemPrompt(): String? {
        return getSystemPromptNative(nativePtr)
    }

    private external fun getSystemPromptNative(llmPtr: Long): String?

    // Helper function to get current memory usage in MB
    private fun getCurrentMemoryUsageMB(context: Context): Long {
        val runtime = Runtime.getRuntime()
        val usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory()
        return usedMemoryBytes / (1024 * 1024) // Convert to MB
    }

    // Helper function to get total memory info
    private fun getMemoryInfo(context: Context): Pair<Long, Long> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val availMemoryMB = memoryInfo.availMem / (1024 * 1024)

        return Pair(usedMemoryMB, availMemoryMB)
    }
}
