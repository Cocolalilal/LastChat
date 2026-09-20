package me.rerere.tts.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest

/** Platform speech synthesis used for [TTSProviderSetting.SystemTTS]. */
interface PlatformSystemTts {
    val available: Boolean

    fun speak(text: String, speechRate: Float = 1.0f, pitch: Float = 1.0f, onDone: () -> Unit = {})

    fun stop()

    fun generateSpeech(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        speak(request.text, providerSetting.speechRate, providerSetting.pitch)
    }
}

class UnavailableSystemTts : PlatformSystemTts {
    override val available: Boolean = false

    override fun speak(text: String, speechRate: Float, pitch: Float, onDone: () -> Unit) {
        onDone()
    }

    override fun stop() = Unit
}
