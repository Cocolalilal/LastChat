package me.rerere.tts.provider

/** Prebuilt voices documented at https://ai.google.dev/gemini-api/docs/speech-generation. */
object GeminiTtsCatalog {
    // Offline/unsupported-endpoint fallback only; the Add Voice picker refreshes from /voices.
    val voices: List<String> = listOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
        "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi",
        "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
    )

    // These models support the generateContent speech API used by GeminiTTSProvider.
    val models: List<String> = listOf(
        "gemini-3.8-flash-tts",
        "gemini-3.8-flash-lite-tts",
        "gemini-3.1-flash-tts-preview",
        "gemini-2.5-flash-preview-tts",
        "gemini-2.5-pro-preview-tts",
    )

    fun modelOptions(current: String, discovered: List<String> = emptyList()): List<String> =
        (listOf(current) + models + discovered).filter { it.isNotBlank() }.distinct()
}
