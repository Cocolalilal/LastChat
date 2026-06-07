package me.rerere.tts.provider

import kotlin.uuid.Uuid

fun TTSProviderSetting.findVoice(voiceId: Uuid?): TTSVoice? {
    if (voiceId == null) return null
    return voices.firstOrNull { it.id == voiceId }
}

fun List<TTSProviderSetting>.findTtsVoice(voiceId: Uuid?): Pair<TTSProviderSetting, TTSVoice>? {
    if (voiceId == null) return null
    forEach { provider ->
        provider.voices.firstOrNull { it.id == voiceId }?.let { voice ->
            return provider to voice
        }
    }
    return null
}

fun TTSProviderSetting.withDefaultVoices(defaultVoiceId: Uuid? = null): TTSProviderSetting {
    if (voices.isNotEmpty()) return this
    val voice = legacyDefaultVoice(defaultVoiceId)
    return copyProvider(voices = listOf(voice))
}

fun TTSProviderSetting.legacyDefaultVoice(defaultVoiceId: Uuid? = null): TTSVoice {
    fun id() = defaultVoiceId ?: Uuid.random()
    return when (this) {
        is TTSProviderSetting.OpenAI -> TTSVoice(
            id = id(),
            name = voice.ifBlank { "Alloy" },
            providerVoiceId = voice.ifBlank { "alloy" },
            speed = 1.0f,
            model = model,
        )

        is TTSProviderSetting.Gemini -> TTSVoice(
            id = id(),
            name = voiceName.ifBlank { "Kore" },
            providerVoiceId = voiceName.ifBlank { "Kore" },
            model = model,
        )

        is TTSProviderSetting.SystemTTS -> TTSVoice(
            id = id(),
            name = voiceName?.takeIf { it.isNotBlank() } ?: name.ifBlank { "System Voice" },
            providerVoiceId = voiceName.orEmpty(),
            pitch = pitch,
            speed = speechRate,
        )

        is TTSProviderSetting.MiniMax -> TTSVoice(
            id = id(),
            name = voiceId.ifBlank { "MiniMax Voice" },
            providerVoiceId = voiceId,
            speed = speed,
            model = model,
            emotion = emotion,
        )

        is TTSProviderSetting.ElevenLabs -> TTSVoice(
            id = id(),
            name = voiceId.ifBlank { "ElevenLabs Voice" },
            providerVoiceId = voiceId,
            model = modelId,
        )

        is TTSProviderSetting.Qwen -> TTSVoice(
            id = id(),
            name = voice.ifBlank { "Qwen Voice" },
            providerVoiceId = voice,
            model = model,
            languageType = languageType,
        )
    }
}

fun TTSProviderSetting.withVoiceApplied(voice: TTSVoice): TTSProviderSetting {
    val providerVoiceId = voice.providerVoiceId.takeIf { it.isNotBlank() }
    return when (this) {
        is TTSProviderSetting.OpenAI -> copy(
            voice = providerVoiceId ?: this.voice,
            model = voice.model ?: model,
        )

        is TTSProviderSetting.Gemini -> copy(
            voiceName = providerVoiceId ?: voiceName,
            model = voice.model ?: model,
        )

        is TTSProviderSetting.SystemTTS -> copy(
            voiceName = providerVoiceId,
            pitch = voice.pitch,
            speechRate = voice.speed,
        )

        is TTSProviderSetting.MiniMax -> copy(
            voiceId = providerVoiceId ?: voiceId,
            model = voice.model ?: model,
            emotion = voice.emotion ?: emotion,
            speed = voice.speed,
        )

        is TTSProviderSetting.ElevenLabs -> copy(
            voiceId = providerVoiceId ?: voiceId,
            modelId = voice.model ?: modelId,
        )

        is TTSProviderSetting.Qwen -> copy(
            voice = providerVoiceId ?: this.voice,
            model = voice.model ?: model,
            languageType = voice.languageType ?: languageType,
        )
    }
}
