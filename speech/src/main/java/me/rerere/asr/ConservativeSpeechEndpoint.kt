package me.rerere.asr

/**
 * Conservative end-of-speech detector for auto-stop STT.
 *
 * Prefers staying in a listening session through normal mid-sentence pauses. A stop is
 * only recommended after a substantial amount of actual speech followed by a long stretch
 * of silence.
 */
class ConservativeSpeechEndpointDetector(
    private val speechAmplitude: Float = DEFAULT_SPEECH_AMPLITUDE,
    private val minSpeechMs: Long = DEFAULT_MIN_SPEECH_MS,
    private val stopSilenceMs: Long = DEFAULT_STOP_SILENCE_MS,
) {
    private var speechAccumulatedMs = 0L
    private var silenceStartedAt = 0L
    private var lastTs = 0L

    fun reset() {
        speechAccumulatedMs = 0L
        silenceStartedAt = 0L
        lastTs = 0L
    }

    /**
     * @return true when the detector is confident that speech has ended.
     */
    fun onFrame(amplitude: Float, nowMs: Long): Boolean {
        val deltaMs = if (lastTs == 0L) 0L else (nowMs - lastTs).coerceIn(0L, 250L)
        lastTs = nowMs
        if (amplitude >= speechAmplitude) {
            speechAccumulatedMs += deltaMs
            silenceStartedAt = 0L
            return false
        }
        if (speechAccumulatedMs < minSpeechMs) return false
        if (silenceStartedAt == 0L) silenceStartedAt = nowMs
        return nowMs - silenceStartedAt >= stopSilenceMs
    }

    companion object {
        const val DEFAULT_SPEECH_AMPLITUDE = 0.24f
        const val DEFAULT_MIN_SPEECH_MS = 1_200L
        const val DEFAULT_STOP_SILENCE_MS = 3_200L
    }
}
