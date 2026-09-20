package me.rerere.tts.provider

import kotlinx.coroutines.flow.Flow
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSModelInfo
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.CartesiaTTSProvider
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider
import me.rerere.tts.provider.providers.FishAudioTTSProvider
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.MiniMaxTTSProvider
import me.rerere.tts.provider.providers.OpenAITTSProvider
import me.rerere.tts.provider.providers.PlayHTTTSProvider
import me.rerere.tts.provider.providers.QwenTTSProvider

/** Portable dispatcher for the eight network-backed TTS providers. */
class CloudTtsManager(
    httpClient: PlatformHttpClient,
    private val systemTts: PlatformSystemTts = UnavailableSystemTts(),
) : TtsSpeechGenerator {
    private val openAI = OpenAITTSProvider(httpClient)
    private val gemini = GeminiTTSProvider(httpClient)
    private val miniMax = MiniMaxTTSProvider(httpClient)
    private val elevenLabs = ElevenLabsTTSProvider(httpClient)
    private val qwen = QwenTTSProvider(httpClient)
    private val fishAudio = FishAudioTTSProvider(httpClient)
    private val cartesia = CartesiaTTSProvider(httpClient)
    private val playHT = PlayHTTTSProvider(httpClient)

    override fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest,
    ): Flow<AudioChunk> = when (providerSetting) {
        is TTSProviderSetting.OpenAI -> openAI.generateSpeech(providerSetting, request)
        is TTSProviderSetting.Gemini -> gemini.generateSpeech(providerSetting, request)
        is TTSProviderSetting.MiniMax -> miniMax.generateSpeech(providerSetting, request)
        is TTSProviderSetting.ElevenLabs -> elevenLabs.generateSpeech(providerSetting, request)
        is TTSProviderSetting.Qwen -> qwen.generateSpeech(providerSetting, request)
        is TTSProviderSetting.FishAudio -> fishAudio.generateSpeech(providerSetting, request)
        is TTSProviderSetting.Cartesia -> cartesia.generateSpeech(providerSetting, request)
        is TTSProviderSetting.PlayHT -> playHT.generateSpeech(providerSetting, request)
        is TTSProviderSetting.SystemTTS -> {
            require(systemTts.available) { "System TTS requires a platform provider" }
            systemTts.generateSpeech(providerSetting, request)
        }
    }

    suspend fun listModels(providerSetting: TTSProviderSetting): List<TTSModelInfo> =
        when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAI.listModels(providerSetting)
            is TTSProviderSetting.Gemini -> gemini.listModels(providerSetting)
            is TTSProviderSetting.ElevenLabs -> elevenLabs.listModels(providerSetting)
            is TTSProviderSetting.FishAudio -> fishAudio.listModels(providerSetting)
            is TTSProviderSetting.Cartesia -> cartesia.listModels(providerSetting)
            is TTSProviderSetting.PlayHT -> playHT.listModels(providerSetting)
            is TTSProviderSetting.MiniMax,
            is TTSProviderSetting.Qwen,
            is TTSProviderSetting.SystemTTS -> emptyList()
        }
}
