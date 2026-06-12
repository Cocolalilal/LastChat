package me.rerere.tts.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TTSVoice(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val providerVoiceId: String = "",
    val locale: String? = null,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val model: String? = null,
    val emotion: String? = null,
    val languageType: String? = null,
    val enginePackageName: String? = null,
    val requiresNetwork: Boolean = false,
)

@Serializable
sealed class TTSProviderSetting {
    abstract val id: Uuid
    abstract val name: String
    abstract val voices: List<TTSVoice>

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
        voices: List<TTSVoice> = this.voices,
    ): TTSProviderSetting

    fun addVoice(voice: TTSVoice): TTSProviderSetting {
        return copyProvider(voices = voices + voice)
    }

    fun editVoice(voice: TTSVoice): TTSProviderSetting {
        return copyProvider(voices = voices.map { if (it.id == voice.id) voice else it })
    }

    fun delVoice(voice: TTSVoice): TTSProviderSetting {
        return copyProvider(voices = voices.filterNot { it.id == voice.id })
    }

    fun moveVoice(from: Int, to: Int): TTSProviderSetting {
        if (from !in voices.indices || to !in voices.indices || from == to) return this
        val reordered = voices.toMutableList().apply {
            add(to, removeAt(from))
        }
        return copyProvider(voices = reordered)
    }

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "OpenAI TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "gpt-4o-mini-tts",
        val voice: String = "alloy"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    @Serializable
    @SerialName("gemini")
    data class Gemini(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Gemini TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val model: String = "gemini-2.5-flash-preview-tts",
        val voiceName: String = "Kore"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    @Serializable
    @SerialName("system")
    data class SystemTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "System TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val enginePackageName: String? = null,
        val voiceName: String? = null,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    @Serializable
    @SerialName("minimax")
    data class MiniMax(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiniMax TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val apiKey: String = "",
        val baseUrl: String = "https://api.minimaxi.com/v1",
        val model: String = "speech-2.5-hd-preview",
        val voiceId: String = "female-shaonv",
        val emotion: String = "calm",
        val speed: Float = 1.0f
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    @Serializable
    @SerialName("elevenlabs")
    data class ElevenLabs(
        override var id: Uuid = Uuid.random(),
        override var name: String = "ElevenLabs TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val apiKey: String = "",
        val voiceId: String = "21m00Tcm4TlvDq8ikWAM", // Default "Rachel" voice
        val modelId: String = "eleven_multilingual_v2"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    @Serializable
    @SerialName("qwen")
    data class Qwen(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Qwen TTS",
        override val voices: List<TTSVoice> = emptyList(),
        val apiKey: String = "",
        val baseUrl: String = "https://dashscope.aliyuncs.com/api/v1",
        val model: String = "qwen3-tts-flash",
        val voice: String = "Cherry",
        val languageType: String = "Auto"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
            voices: List<TTSVoice>,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
                voices = voices,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Gemini::class,
                SystemTTS::class,
                MiniMax::class,
                ElevenLabs::class,
                Qwen::class,
            )
        }
    }
}
