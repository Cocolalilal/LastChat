package me.rerere.common.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface KeyCodec<K : Any> {
    fun toFileName(key: K): String
    fun fromFileName(name: String): K?
}

@OptIn(ExperimentalEncodingApi::class)
class Base64JsonKeyCodec<K : Any>(
    private val keySerializer: KSerializer<K>,
    private val json: Json = Json { allowStructuredMapKeys = true }
) : KeyCodec<K> {
    override fun toFileName(key: K): String {
        val jsonStr = json.encodeToString(keySerializer, key)
        return Base64.UrlSafe.encode(jsonStr.encodeToByteArray()).trimEnd('=')
    }

    override fun fromFileName(name: String): K? = try {
        val paddedName = name.padEnd(name.length + (4 - name.length % 4) % 4, '=')
        val jsonStr = Base64.UrlSafe.decode(paddedName).decodeToString()
        json.decodeFromString(keySerializer, jsonStr)
    } catch (_: Exception) {
        null
    }
}

