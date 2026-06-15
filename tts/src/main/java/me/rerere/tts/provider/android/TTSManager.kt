package me.rerere.tts.provider.android

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.common.platform.android.OkHttpPlatformHttpClient
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.MiniMaxTTSProvider
import me.rerere.tts.provider.providers.OpenAITTSProvider
import me.rerere.tts.provider.providers.QwenTTSProvider
import me.rerere.tts.provider.providers.android.SystemTTSProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TTSManager(private val context: Context) {
    private val openAIProvider = OpenAITTSProvider(
        httpClient = OkHttpPlatformHttpClient(
            OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    )
    private val geminiProvider = GeminiTTSProvider(
        httpClient = OkHttpPlatformHttpClient(
            OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    )
    private val systemProvider = SystemTTSProvider(context)
    private val miniMaxProvider = MiniMaxTTSProvider(
        httpClient = OkHttpPlatformHttpClient(
            OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        )
    )
    private val elevenLabsProvider = ElevenLabsTTSProvider(
        httpClient = OkHttpPlatformHttpClient(
            OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        )
    )
    private val qwenProvider = QwenTTSProvider(
        httpClient = OkHttpPlatformHttpClient(
            OkHttpClient.Builder()
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
        )
    )

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Gemini -> geminiProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.MiniMax -> miniMaxProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Qwen -> qwenProvider.generateSpeech(providerSetting, request)
        }
    }
}
