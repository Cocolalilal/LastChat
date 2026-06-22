package me.rerere.tts.provider

import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest

interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(
        providerSetting: T,
        request: TTSRequest
    ): Flow<AudioChunk>

    suspend fun listModels(providerSetting: T): List<TTSModelInfo> = emptyList()
}
