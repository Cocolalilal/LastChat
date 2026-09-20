package me.rerere.common.platform

/**
 * Records microphone audio as WAV PCM for cloud speech-to-text.
 * iOS uses AVAudioRecorder; Android can later bind AudioRecord behind the same contract.
 */
interface PlatformSpeechRecorder {
    val isRecording: Boolean

    suspend fun start(): Boolean

    /** Stops recording and returns a WAV byte array, or null if nothing was captured. */
    suspend fun stop(): ByteArray?
}

class UnavailableSpeechRecorder : PlatformSpeechRecorder {
    override val isRecording: Boolean = false
    override suspend fun start(): Boolean = false
    override suspend fun stop(): ByteArray? = null
}
