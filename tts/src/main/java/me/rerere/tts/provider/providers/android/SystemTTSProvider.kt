package me.rerere.tts.provider.providers.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.android.appTempFolder
import me.rerere.common.platform.PlatformLog
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.Uuid

private const val TAG = "SystemTTSProvider"

class SystemTTSProvider(
    private val context: Context
) : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val audioData = suspendCancellableCoroutine<ByteArray> { continuation ->
            var tts: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ttsInstance = tts
                    if (ttsInstance == null) {
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("TextToSpeech instance is null")
                        )
                        return@OnInitListener
                    }

                // Set language
                val locale = java.util.Locale.getDefault()
                val langResult = ttsInstance.setLanguage(locale)

                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    PlatformLog.w(TAG, "generateSpeech: Language $locale not supported")
                }

                val voiceName = providerSetting.voiceName?.takeIf { it.isNotBlank() }
                if (voiceName != null) {
                    val voice = ttsInstance.voices?.firstOrNull { it.name == voiceName }
                    if (voice == null) {
                        PlatformLog.w(TAG, "generateSpeech: Voice $voiceName not found")
                    } else if (ttsInstance.setVoice(voice) != TextToSpeech.SUCCESS) {
                        PlatformLog.w(TAG, "generateSpeech: Failed to select voice $voiceName")
                    }
                }

                // Set speech parameters
                ttsInstance.setSpeechRate(providerSetting.speechRate)
                ttsInstance.setPitch(providerSetting.pitch)

                // Create temporary file for audio output using temp directory like LastChatApp
                val tempDir = context.appTempFolder
                val audioFile = tempDir.resolve("tts_${System.currentTimeMillis()}.wav")

                val utteranceId = Uuid.random().toString()

                ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        PlatformLog.i(TAG, "onStart: TTS engine started!")
                    }

                    override fun onDone(utteranceId: String?) {
                        try {
                            if (audioFile.exists()) {
                                val audioData = audioFile.readBytes()
                                audioFile.delete()

                                if (continuation.isActive) continuation.resume(audioData)
                            } else {
                                if (continuation.isActive) continuation.resumeWithException(
                                    Exception("Failed to generate audio file")
                                )
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            ttsInstance.shutdown()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        PlatformLog.e(TAG, "onError: TTS synthesis failed!")
                        audioFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("TTS synthesis failed")
                        )
                        ttsInstance.shutdown()
                    }
                })

                val result = ttsInstance.synthesizeToFile(
                    request.text,
                    null,
                    audioFile,
                    utteranceId
                )

                if (result != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) continuation.resumeWithException(
                        Exception("Failed to start TTS synthesis")
                    )
                    ttsInstance.shutdown()
                }

            } else {
                if (continuation.isActive) continuation.resumeWithException(
                    Exception("Failed to initialize TextToSpeech engine")
                )
            }
        }
        val enginePackageName = providerSetting.enginePackageName?.takeIf { it.isNotBlank() }
        tts = if (enginePackageName == null) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, enginePackageName)
        }

        continuation.invokeOnCancellation {
            tts?.shutdown()
        }
    }

        emit(
            AudioChunk(
                data = audioData,
                format = me.rerere.tts.model.AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString(),
                    "enginePackageName" to (providerSetting.enginePackageName ?: ""),
                    "voiceName" to (providerSetting.voiceName ?: "")
                )
            )
        )
    }
}
