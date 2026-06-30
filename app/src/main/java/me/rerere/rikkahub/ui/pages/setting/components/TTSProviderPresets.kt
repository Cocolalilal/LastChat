package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.rikkahub.data.ai.models.CatalogTTSProvider
import me.rerere.rikkahub.data.ai.models.CatalogTTSProviderType
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.toCatalogIconUrl
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

data class TTSProviderPreset(
    val id: String? = null,
    val name: String,
    val description: String,
    val type: KClass<out TTSProviderSetting>,
    val baseUrl: String = "",
    val defaultModel: String = "",
    val defaultVoice: String = "",
    val customIconUri: String? = null,
    val isLocal: Boolean = false,
    val signupUrl: String? = null,
    val apiKeyUrl: String? = null,
)

val FALLBACK_TTS_PROVIDER_PRESETS = listOf(
    TTSProviderPreset(
        name = "System TTS",
        description = "Local Android system text-to-speech engine",
        type = TTSProviderSetting.SystemTTS::class,
        isLocal = true,
    ),
    TTSProviderPreset(
        name = "OpenAI",
        description = "OpenAI text-to-speech (gpt-4o-mini-tts)",
        type = TTSProviderSetting.OpenAI::class,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini-tts",
        defaultVoice = "alloy",
        customIconUri = "icons/openai.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "Gemini",
        description = "Google Gemini text-to-speech",
        type = TTSProviderSetting.Gemini::class,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash-preview-tts",
        defaultVoice = "Kore",
        customIconUri = "icons/gemini.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "ElevenLabs",
        description = "ElevenLabs text-to-speech",
        type = TTSProviderSetting.ElevenLabs::class,
        customIconUri = "icons/elevenlabs.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "MiniMax",
        description = "MiniMax text-to-speech",
        type = TTSProviderSetting.MiniMax::class,
        baseUrl = "https://api.minimaxi.com/v1",
        defaultModel = "speech-2.5-hd-preview",
        defaultVoice = "female-shaonv",
        customIconUri = "icons/minimax.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "Qwen",
        description = "Qwen / DashScope text-to-speech",
        type = TTSProviderSetting.Qwen::class,
        baseUrl = "https://dashscope.aliyuncs.com/api/v1",
        defaultModel = "qwen3-tts-flash",
        defaultVoice = "Cherry",
        customIconUri = "icons/qwen.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "Fish Audio",
        description = "Fish Audio expressive TTS with emotion control",
        type = TTSProviderSetting.FishAudio::class,
        baseUrl = "https://api.fish.audio",
        defaultModel = "s2-pro",
        customIconUri = "icons/fishaudio.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "Cartesia",
        description = "Cartesia Sonic ultra-fast expressive TTS",
        type = TTSProviderSetting.Cartesia::class,
        baseUrl = "https://api.cartesia.ai",
        defaultModel = "sonic-3.5",
        customIconUri = "icons/cartesia.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "PlayHT",
        description = "PlayHT text-to-speech with voice cloning",
        type = TTSProviderSetting.PlayHT::class,
        baseUrl = "https://api.play.ht/api/v2",
        customIconUri = "icons/playht.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "Groq",
        description = "Groq TTS (Orpheus models, OpenAI-compatible)",
        type = TTSProviderSetting.OpenAI::class,
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "canopylabs/orpheus-v1-english",
        defaultVoice = "troy",
        customIconUri = "icons/groq.svg".toCatalogIconUrl(),
    ),
    TTSProviderPreset(
        name = "OpenRouter",
        description = "OpenRouter TTS (OpenAI-compatible audio/speech)",
        type = TTSProviderSetting.OpenAI::class,
        baseUrl = "https://openrouter.ai/api/v1",
        customIconUri = "icons/openrouter.svg".toCatalogIconUrl(),
    ),
)

fun ModelCatalogSnapshot.toTTSProviderPresets(): List<TTSProviderPreset> {
    return ttsProviders
        .filter { it.preset }
        .map { it.toTTSProviderPreset() }
}

fun CatalogTTSProvider.toTTSProviderPreset(): TTSProviderPreset {
    val presetType = when (type) {
        CatalogTTSProviderType.OPENAI -> TTSProviderSetting.OpenAI::class
        CatalogTTSProviderType.GEMINI -> TTSProviderSetting.Gemini::class
        CatalogTTSProviderType.SYSTEM -> TTSProviderSetting.SystemTTS::class
        CatalogTTSProviderType.MINIMAX -> TTSProviderSetting.MiniMax::class
        CatalogTTSProviderType.ELEVENLABS -> TTSProviderSetting.ElevenLabs::class
        CatalogTTSProviderType.QWEN -> TTSProviderSetting.Qwen::class
        CatalogTTSProviderType.FISHAUDIO -> TTSProviderSetting.FishAudio::class
        CatalogTTSProviderType.CARTESIA -> TTSProviderSetting.Cartesia::class
        CatalogTTSProviderType.PLAYHT -> TTSProviderSetting.PlayHT::class
    }
    return TTSProviderPreset(
        id = id,
        name = name,
        description = description,
        type = presetType,
        baseUrl = baseUrl,
        defaultModel = defaultModel,
        defaultVoice = defaultVoice,
        customIconUri = icon?.toCatalogIconUrl(),
        isLocal = type == CatalogTTSProviderType.SYSTEM,
        signupUrl = signupUrl,
        apiKeyUrl = apiKeyUrl,
    )
}

fun TTSProviderPreset.toTTSProviderSetting(): TTSProviderSetting {
    return when (type) {
        TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://api.openai.com/v1" },
            model = defaultModel.ifBlank { "gpt-4o-mini-tts" },
            voice = defaultVoice.ifBlank { "alloy" },
            customIconUri = customIconUri,
        )

        TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" },
            model = defaultModel.ifBlank { "gemini-2.5-flash-preview-tts" },
            voiceName = defaultVoice.ifBlank { "Kore" },
            customIconUri = customIconUri,
        )

        TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
            name = name,
            customIconUri = customIconUri,
        )

        TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://api.minimaxi.com/v1" },
            model = defaultModel.ifBlank { "speech-2.5-hd-preview" },
            voiceId = defaultVoice.ifBlank { "female-shaonv" },
            customIconUri = customIconUri,
        )

        TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
            name = name,
            voiceId = defaultVoice.ifBlank { "21m00Tcm4TlvDq8ikWAM" },
            modelId = defaultModel.ifBlank { "eleven_multilingual_v2" },
            customIconUri = customIconUri,
        )

        TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://dashscope.aliyuncs.com/api/v1" },
            model = defaultModel.ifBlank { "qwen3-tts-flash" },
            voice = defaultVoice.ifBlank { "Cherry" },
            customIconUri = customIconUri,
        )

        TTSProviderSetting.FishAudio::class -> TTSProviderSetting.FishAudio(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://api.fish.audio" },
            model = defaultModel.ifBlank { "s2-pro" },
            referenceId = defaultVoice,
            customIconUri = customIconUri,
        )

        TTSProviderSetting.Cartesia::class -> TTSProviderSetting.Cartesia(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://api.cartesia.ai" },
            modelId = defaultModel.ifBlank { "sonic-3.5" },
            voiceId = defaultVoice,
            customIconUri = customIconUri,
        )

        TTSProviderSetting.PlayHT::class -> TTSProviderSetting.PlayHT(
            name = name,
            baseUrl = baseUrl.ifBlank { "https://api.play.ht/api/v2" },
            voice = defaultVoice,
            customIconUri = customIconUri,
        )

        else -> TTSProviderSetting.OpenAI(
            name = name,
            baseUrl = baseUrl,
            model = defaultModel,
            voice = defaultVoice,
            customIconUri = customIconUri,
        )
    }
}
