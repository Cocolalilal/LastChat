package me.rerere.tts.provider.providers

import kotlinx.serialization.json.Json

internal val ttsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Keeps user-facing Float settings compact when encoded as JSON numbers. */
internal fun Float.toTtsJsonNumber(): Double = toString().toDouble()
