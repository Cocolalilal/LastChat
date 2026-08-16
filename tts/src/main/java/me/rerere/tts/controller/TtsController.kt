package me.rerere.tts.controller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TtsSpeechGenerator
import me.rerere.tts.provider.ttsIoDispatcher

private const val TAG = "TtsController"

/**
 * TTS 控制器
 * - 负责文本分片、预取合成、无缝排队播放与状态上报
 * - 对外 API 保持完全兼容
 */
class TtsController(
    private val ttsManager: TtsSpeechGenerator,
    private val audio: TtsAudioPlayer,
) {
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 组件
    private val chunker = TextChunker(maxChunkLength = 360)
    private val synthesizer = TtsSynthesizer(ttsManager)

    // Provider & 作业
    private var currentProvider: TTSProviderSetting? = null
    private var workerJob: Job? = null
    private var isPaused = false

    // 队列与缓存（基于稳定 ID）
    private val queue = ArrayDeque<TtsChunk>()
    private val allChunks: MutableList<TtsChunk> = mutableListOf()
    private val cache = mutableMapOf<TtsCacheKey, Deferred<TTSResponse>>()
    private var lastPrefetchedIndex: Int = -1

    // 行为参数
    private val prefetchCount = 4

    // 状态流（保留与旧版兼容的 StateFlow）
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChunk = MutableStateFlow(0)
    val currentChunk: StateFlow<Int> = _currentChunk.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    // 统一播放状态（融合音频播放 + 分片进度）
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        // 同步底层播放器状态到统一状态，并补充分片信息
        scope.launch {
            audio.playbackState.collectLatest { audioState ->
                _playbackState.update {
                    audioState.copy(
                        status = if (!_isAvailable.value) PlaybackStatus.Idle else audioState.status
                    )
                }
                _currentChunk.update { audioState.currentChunkIndex }
                _totalChunks.update { audioState.totalChunks }
                _isSpeaking.update {
                    audioState.status == PlaybackStatus.Playing ||
                            audioState.status == PlaybackStatus.Buffering ||
                            (workerJob?.isActive == true && queue.isNotEmpty())
                }
            }
        }
    }

    /** 选择/取消选择 Provider */
    fun setProvider(provider: TTSProviderSetting?) {
        if (currentProvider != provider) {
            stop()
        }
        currentProvider = provider
        _isAvailable.update { provider != null }
    }

    /**
     * 朗读文本
     * - flush=true: 清空当前进度并重新开始
     * - flush=false: 继续队列，追加朗读
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }

        val newChunks = chunker.split(text)
        if (newChunks.isEmpty()) return

        if (flush) {
            internalReset()
            allChunks.addAll(newChunks)
            queue.addAll(newChunks)
            _currentChunk.update { 0 }
        } else {
            val startIndex = (allChunks.lastOrNull()?.index ?: -1) + 1
            val remapped = newChunks.mapIndexed { i, c -> c.copy(index = startIndex + i) }
            allChunks.addAll(remapped)
            queue.addAll(remapped)
        }
        _totalChunks.update { allChunks.size }
        audio.setTotalChunks(allChunks.size)
        _error.update { null }

        _playbackState.update {
            it.copy(
                currentChunkIndex = _currentChunk.value,
                totalChunks = _totalChunks.value,
                status = PlaybackStatus.Buffering
            )
        }

        if (workerJob?.isActive != true) startWorker()
        prefetchFrom((_currentChunk.value).coerceAtLeast(0))
    }

    /**
     * Speak text with a specific provider (temporary override)
     * Used for testing TTS settings before saving
     */
    fun speakWithProvider(text: String, provider: TTSProviderSetting, flush: Boolean = true) {
        if (text.isBlank()) return

        val newChunks = chunker.split(text)
        if (newChunks.isEmpty()) return

        if (flush) {
            internalReset()
            allChunks.addAll(newChunks)
            queue.addAll(newChunks)
            _currentChunk.update { 0 }
        } else {
            val startIndex = (allChunks.lastOrNull()?.index ?: -1) + 1
            val remapped = newChunks.mapIndexed { i, c -> c.copy(index = startIndex + i) }
            allChunks.addAll(remapped)
            queue.addAll(remapped)
        }
        _totalChunks.update { allChunks.size }
        audio.setTotalChunks(allChunks.size)
        _error.update { null }

        _playbackState.update {
            it.copy(
                currentChunkIndex = _currentChunk.value,
                totalChunks = _totalChunks.value,
                status = PlaybackStatus.Buffering
            )
        }

        if (workerJob?.isActive != true) startWorkerWithProvider(provider)
        prefetchFromWithProvider((_currentChunk.value).coerceAtLeast(0), provider)
    }

    private fun internalReset() {
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Reset")) }
        cache.clear()
        lastPrefetchedIndex = -1
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _error.update { null }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** 暂停播放（保留进度） */
    fun pause() {
        isPaused = true
        audio.pause()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    /** 恢复播放 */
    fun resume() {
        isPaused = false
        audio.resume()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    /** 快进当前音频 */
    fun fastForward(ms: Long = 5_000) {
        audio.seekBy(ms)
    }

    /** 设置播放速度 */
    fun setSpeed(speed: Float) {
        audio.setSpeed(speed)
    }

    /** 跳过下一段（不打断当前正在播放） */
    fun skipNext() {
        if (queue.isNotEmpty()) {
            queue.removeFirstOrNull()
            _totalChunks.update { allChunks.size }
            audio.setTotalChunks(allChunks.size)
        }
        audio.skipNext()
    }

    /** 停止并清空状态 */
    fun stop() {
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Stopped")) }
        cache.clear()
        lastPrefetchedIndex = -1
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** 释放资源 */
    fun dispose() {
        stop()
        scope.cancel()
        audio.release()
    }

    // region 内部：播放调度
    private fun startWorker() {
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }
        startWorkerWithProvider(provider)
    }

    private fun startWorkerWithProvider(provider: TTSProviderSetting) {
        workerJob = scope.launch {
            _isSpeaking.update { true }
            try {
                while (isActive) {
                    if (isPaused) {
                        delay(80)
                        continue
                    }

                    val chunk = queue.removeFirstOrNull() ?: break

                    // 预取下一窗口
                    prefetchFromWithProvider(chunk.index + 1, provider)

                    // Synthesis with retry logic
                    var response: TTSResponse? = null
                    var lastError: Exception? = null
                    val maxRetries = 5
                    var totalAttempts = 0

                    while (response == null && isActive) {
                        for (attempt in 1..maxRetries) {
                            totalAttempts++
                            try {
                                response = awaitOrCreate(chunk, provider)
                                break
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                lastError = e
                                PlatformLog.w(TAG, "Synthesis attempt $attempt/$maxRetries failed for chunk ${chunk.index}: ${e.logSummary()}")

                                if (attempt < maxRetries) {
                                    delay((500L * (1 shl (attempt - 1))).coerceAtMost(8000L))
                                }
                            }
                        }

                        if (response == null) {
                            val errorMsg = lastError?.message ?: "Unknown error"
                            PlatformLog.w(TAG, "Retrying chunk ${chunk.index} after extended delay (total attempts: $totalAttempts): $errorMsg")
                            _error.update { "TTS Error: $errorMsg" }
                            delay(5000L)

                            if (totalAttempts >= 15) {
                                val finalErrorMsg = lastError?.message ?: "Unknown error"
                                PlatformLog.e(TAG, "Giving up on chunk ${chunk.index} after $totalAttempts attempts: ${lastError?.logSummary() ?: finalErrorMsg}")
                                _error.update { "TTS failed after $totalAttempts attempts: $finalErrorMsg" }
                                break
                            }
                        }
                    }

                    if (response == null) {
                        continue
                    }

                    // Enqueue into gapless playlist
                    audio.enqueue(chunk.index, allChunks.size, response)
                }
            } finally {
                if (queue.isEmpty() && audio.playbackState.value.status == PlaybackStatus.Ended) {
                    _isSpeaking.update { false }
                }
            }
        }
    }

    private fun prefetchFrom(startIndex: Int) {
        val provider = currentProvider ?: return
        prefetchFromWithProvider(startIndex, provider)
    }

    private fun prefetchFromWithProvider(startIndex: Int, provider: TTSProviderSetting) {
        val begin = startIndex.coerceAtLeast(lastPrefetchedIndex + 1)
        val endExclusive = (begin + prefetchCount).coerceAtMost(allChunks.size)
        if (begin >= endExclusive) return

        for (i in begin until endExclusive) {
            val chunk = allChunks.getOrNull(i) ?: continue
            getOrCreateCachedSynthesis(chunk, provider)
        }
        lastPrefetchedIndex = endExclusive - 1
    }

    private suspend fun awaitOrCreate(chunk: TtsChunk, provider: TTSProviderSetting): TTSResponse {
        val deferred = getOrCreateCachedSynthesis(chunk, provider)
        return deferred.await()
    }

    private fun getOrCreateCachedSynthesis(
        chunk: TtsChunk,
        provider: TTSProviderSetting,
    ): Deferred<TTSResponse> {
        return cache.getOrPut(TtsCacheKey(chunk.text, provider)) {
            scope.async(ttsIoDispatcher) { synthesizer.synthesize(provider, chunk) }
        }
    }
    // endregion
}

private data class TtsCacheKey(
    val text: String,
    val provider: TTSProviderSetting,
)

private fun Throwable.logSummary(): String {
    val type = this::class.simpleName ?: "Throwable"
    val message = this.message
    return if (message.isNullOrBlank()) type else "$type: $message"
}

