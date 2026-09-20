package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

data class PortableCharacterImport(
    val name: String,
    val systemPrompt: String,
    val firstMes: String,
    val alternateGreetings: List<String>,
    val lorebook: PortableLorebook?,
    val avatarBytes: ByteArray?,
    val avatarUrl: String?,
)

/**
 * SillyTavern / Chub character-card parser shared by Android and iOS.
 * Accepts JSON V1/V2 wrappers plus PNG `tEXt`/`iTXt` `chara` payloads.
 */
object PortableCharacterCardParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val pngHeader = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private val pngKeys = listOf(
        "chara", "ccv3", "character", "card", "ccv2", "card_data", "tavern", "sillytavern", "character_card",
    )

    fun parse(bytes: ByteArray, fileName: String = "", avatarBytes: ByteArray? = null): PortableCharacterImport? {
        val name = fileName.lowercase()
        return when {
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(pngHeader) -> {
                val payload = extractPngCharacterJson(bytes) ?: return null
                parseJson(payload, avatarBytes ?: bytes)
            }
            name.endsWith(".png") -> {
                val payload = extractPngCharacterJson(bytes) ?: return null
                parseJson(payload, avatarBytes ?: bytes)
            }
            else -> parseJson(bytes.decodeToString(), avatarBytes)
        }
    }

    fun parseJson(raw: String, avatarBytes: ByteArray? = null): PortableCharacterImport? {
        return runCatching {
            val current = unwrapCardObject(raw) ?: return null
            val dataObj = (current["data"] as? JsonObject) ?: current
            val name = firstString(
                dataObj,
                current,
                "name", "char_name", "character_name", "title", "bot_name",
            ) ?: "Imported Character"
            val description = firstString(
                dataObj, current,
                "description", "char_persona", "persona", "character_persona", "char_description", "about",
            ).orEmpty()
            val personality = firstString(dataObj, current, "personality", "char_personality").orEmpty()
            val scenario = firstString(
                dataObj, current, "scenario", "world_scenario", "char_scenario",
            ).orEmpty()
            val systemPrompt = firstString(
                dataObj, current, "system_prompt", "main_prompt", "custom_system_prompt", "prompt",
            ).orEmpty()
            val mesExample = firstString(
                dataObj, current, "mes_example", "example_dialogs", "example_dialogue", "examples",
            ).orEmpty()
            val firstMes = firstString(
                dataObj, current,
                "first_mes", "first_message", "char_greeting", "greeting", "initial_message",
            ).orEmpty()
            val postHistory = firstString(
                dataObj, current, "post_history_instructions", "post_history", "jailbreak",
            ).orEmpty()
            val alternateGreetings = parseAlternateGreetings(
                dataObj["alternate_greetings"]
                    ?: dataObj["alt_greetings"]
                    ?: dataObj["group_only_greetings"]
                    ?: current["alternate_greetings"],
            )
            val composed = buildString {
                appendSection(systemPrompt)
                appendLabeled("Description", description)
                appendLabeled("Personality", personality)
                appendLabeled("Scenario", scenario)
                appendLabeled("Examples", mesExample)
                appendLabeled("Instructions", postHistory)
            }.trim()
            val lorebook = parseLorebook(
                (dataObj["character_book"] as? JsonObject) ?: (current["character_book"] as? JsonObject),
            )
            val avatarUrl = firstString(dataObj, current, "avatar")
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            PortableCharacterImport(
                name = name.ifBlank { "Imported Character" },
                systemPrompt = composed,
                firstMes = firstMes,
                alternateGreetings = alternateGreetings,
                lorebook = lorebook,
                avatarBytes = avatarBytes,
                avatarUrl = avatarUrl,
            )
        }.getOrNull()
    }

    fun extractPngCharacterJson(bytes: ByteArray): String? {
        val chunks = extractPngTextChunks(bytes)
        val preferred = pngKeys.firstNotNullOfOrNull { key ->
            chunks.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        } ?: chunks.values.firstOrNull { value ->
            value.contains("\"name\"") || value.contains("\"spec\"") || value.startsWith("ey")
        } ?: return null
        val decoded = decodeBase64OrUtf8(preferred)
        return decoded.takeIf { it.contains('{') } ?: preferred.takeIf { it.contains('{') }
    }

    internal fun extractPngTextChunks(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(pngHeader)) return emptyMap()
        val result = mutableMapOf<String, String>()
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = readBeInt(bytes, offset)
            offset += 4
            val type = latin1(bytes, offset, 4)
            offset += 4
            if (offset + length > bytes.size) break
            val data = bytes.copyOfRange(offset, offset + length)
            offset += length + 4
            when (type) {
                "tEXt" -> {
                    val separator = data.indexOf(0)
                    if (separator > 0) {
                        result[latin1(data, 0, separator)] =
                            latin1(data, separator + 1, data.size - separator - 1)
                    }
                }
                "iTXt" -> {
                    val separator = data.indexOf(0)
                    if (separator > 0 && separator + 3 < data.size) {
                        val keyword = latin1(data, 0, separator)
                        val compressionFlag = data[separator + 1].toInt() and 0xFF
                        if (compressionFlag == 0) {
                            var cursor = separator + 3
                            val langEnd = data.indexOf(0, cursor).takeIf { it >= 0 } ?: continue
                            cursor = langEnd + 1
                            val translatedEnd = data.indexOf(0, cursor).takeIf { it >= 0 } ?: continue
                            result[keyword] = data.decodeToString(translatedEnd + 1, data.size)
                        }
                    }
                }
            }
            if (type == "IEND") break
        }
        return result
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun unwrapCardObject(raw: String): JsonObject? {
        var trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("ey") && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            trimmed = decodeBase64OrUtf8(trimmed)
        }
        var element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
            ?: decodeBase64OrUtf8(trimmed).let { decoded ->
                runCatching { json.parseToJsonElement(decoded) }.getOrNull()
            }
            ?: return null
        var current: JsonObject = when (element) {
            is JsonArray -> element.firstOrNull { it is JsonObject } as? JsonObject ?: return null
            is JsonObject -> element
            else -> return null
        }
            val charaB64 = current["chara"].asStringOrNull()
            if (charaB64 != null && charaB64.startsWith("ey")) {
                val nested = runCatching {
                    json.parseToJsonElement(decodeBase64OrUtf8(charaB64)) as? JsonObject
                }.getOrNull()
                if (nested != null) current = nested
            }
        val wrapperKeys = listOf("character", "definition", "card", "bot", "chara", "character_data", "char")
        var unwrapped = true
        while (unwrapped) {
            unwrapped = false
            for (key in wrapperKeys) {
                val child = current[key] as? JsonObject ?: continue
                if (
                    child.containsKey("data") || child.containsKey("name") || child.containsKey("char_name") ||
                    child.containsKey("first_mes") || child.containsKey("spec") || child.containsKey("description")
                ) {
                    current = child
                    unwrapped = true
                    break
                }
            }
        }
        val dataChild = current["data"]
        if (dataChild is JsonPrimitive && dataChild.isString) {
            runCatching { json.parseToJsonElement(dataChild.content) }.getOrNull()?.let { parsed ->
                if (parsed is JsonObject) {
                    current = JsonObject(current.toMutableMap().apply { put("data", parsed) })
                }
            }
        }
        return current
    }

    private fun parseLorebook(obj: JsonObject?): PortableLorebook? {
        if (obj == null) return null
        val name = obj["name"].asStringOrNull() ?: "Imported Lorebook"
        val entriesArray = obj["entries"] as? JsonArray ?: return null
        val entries = entriesArray.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val content = entry["content"].asStringOrNull()
                ?: entry["prompt"].asStringOrNull()
                ?: return@mapNotNull null
            if (content.isBlank()) return@mapNotNull null
            val keys = when (val raw = entry["keys"]) {
                is JsonArray -> raw.mapNotNull { it.asStringOrNull() }
                is JsonPrimitive -> raw.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()
                else -> emptyList()
            }
            PortableLorebookEntry(
                id = Uuid.random().toString(),
                name = entry["name"].asStringOrNull() ?: name,
                prompt = content,
                enabled = entry["enabled"].asStringOrNull()?.toBooleanStrictOrNull() ?: true,
                activationType = if (keys.isEmpty()) {
                    LorebookActivationKind.ALWAYS
                } else {
                    LorebookActivationKind.KEYWORDS
                },
                keywords = keys,
                caseSensitive = entry["case_sensitive"].asStringOrNull()?.toBooleanStrictOrNull() ?: false,
            )
        }
        if (entries.isEmpty()) return null
        return PortableLorebook(
            id = Uuid.random().toString(),
            name = name,
            description = obj["description"].asStringOrNull().orEmpty(),
            entries = entries,
        )
    }

    private fun parseAlternateGreetings(raw: JsonElement?): List<String> = when (raw) {
        is JsonArray -> raw.mapNotNull { elem ->
            elem.asStringOrNull() ?: (elem as? JsonObject)?.let { obj ->
                obj["content"].asStringOrNull()
                    ?: obj["message"].asStringOrNull()
                    ?: obj["text"].asStringOrNull()
                    ?: obj["greeting"].asStringOrNull()
            }
        }.filter { it.isNotBlank() }
        is JsonPrimitive -> raw.contentOrNull?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
        else -> emptyList()
    }

    private fun firstString(primary: JsonObject, fallback: JsonObject, vararg keys: String): String? {
        keys.forEach { key ->
            primary[key].asStringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        keys.forEach { key ->
            fallback[key].asStringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun StringBuilder.appendSection(value: String) {
        if (value.isNotBlank()) {
            append(value.trim())
            append("\n\n")
        }
    }

    private fun StringBuilder.appendLabeled(label: String, value: String) {
        if (value.isNotBlank()) {
            append(label).append(":\n").append(value.trim()).append("\n\n")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64OrUtf8(value: String): String {
        val cleaned = value.filterNot { it.isWhitespace() }
        return runCatching {
            Base64.Default.decode(cleaned).decodeToString()
        }.getOrElse {
            runCatching { Base64.UrlSafe.decode(cleaned).decodeToString() }.getOrDefault(value)
        }
    }

    private fun JsonElement?.asStringOrNull(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        else -> null
    }

    private fun readBeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun latin1(bytes: ByteArray, offset: Int, length: Int): String {
        return buildString(length) {
            repeat(length) { index ->
                append((bytes[offset + index].toInt() and 0xFF).toChar())
            }
        }
    }

    private fun ByteArray.indexOf(value: Byte, start: Int = 0): Int {
        for (index in start until size) if (this[index] == value) return index
        return -1
    }
}
