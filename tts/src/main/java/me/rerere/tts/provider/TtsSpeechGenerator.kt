package me.rerere.tts.provider

import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest

/** Provider-dispatch boundary shared by Android and iOS queue orchestration. */
interface TtsSpeechGenerator {
    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest,
    ): Flow<AudioChunk>
}
