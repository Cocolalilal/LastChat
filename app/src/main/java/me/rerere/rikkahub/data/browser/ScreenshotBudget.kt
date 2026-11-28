package me.rerere.rikkahub.data.browser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Caps the number of retained `browser_use` screenshots sent to the LLM in each request.
 * Older browser screenshots are elided from context and replaced with a concise text
 * placeholder referencing their saved local path and URL.
 */
object ScreenshotBudget {
    private const val BROWSER_TOOL_NAME = "browser_use"
    private const val MIN_CAP = 2
    private const val MAX_CAP = 6

    fun apply(
        messages: List<UIMessage>,
        maxScreenshots: Int,
    ): List<UIMessage> {
        val cap = maxScreenshots.coerceIn(MIN_CAP, MAX_CAP)
        var keptCount = 0
        var anyModified = false

        val reversedResult = messages.asReversed().map { message ->
            var msgChanged = false
            val updatedParts = message.parts.asReversed().map { part ->
                if (part is UIMessagePart.ToolResult && part.toolName == BROWSER_TOOL_NAME) {
                    val activeImages = part.inspectedImages.filter { it.url.isNotBlank() }
                    if (activeImages.isEmpty()) {
                        part
                    } else {
                        var partChanged = false
                        val keptImages = part.inspectedImages.asReversed().map { img ->
                            if (img.url.isNotBlank()) {
                                if (keptCount < cap) {
                                    keptCount++
                                    img
                                } else {
                                    partChanged = true
                                    img.copy(url = "")
                                }
                            } else {
                                img
                            }
                        }.asReversed()

                        if (partChanged) {
                            msgChanged = true
                            anyModified = true
                            val savedPath = (part.content as? JsonObject)
                                ?.get("image_file_path")?.jsonPrimitive?.contentOrNull
                                ?: activeImages.firstOrNull()?.sourceUrl
                                ?: "saved screenshot"
                            val pageUrl = (part.content as? JsonObject)
                                ?.get("page_url")?.jsonPrimitive?.contentOrNull
                                ?: activeImages.firstOrNull()?.title
                                ?: ""
                            val note = buildString {
                                append("[Earlier browser screenshot omitted (kept newest $cap): path=")
                                append(savedPath)
                                if (pageUrl.isNotBlank()) {
                                    append(", url=")
                                    append(pageUrl)
                                }
                                append("]")
                            }
                            val nextContent = if (part.content is JsonObject) {
                                val map = (part.content as JsonObject).toMutableMap()
                                map["screenshot_elided"] = JsonPrimitive(note)
                                JsonObject(map)
                            } else {
                                buildJsonObject {
                                    put("screenshot_elided", JsonPrimitive(note))
                                }
                            }
                            part.copy(
                                content = nextContent,
                                inspectedImages = keptImages,
                            )
                        } else {
                            part
                        }
                    }
                } else {
                    part
                }
            }.asReversed()

            if (msgChanged) message.copy(parts = updatedParts) else message
        }.asReversed()

        return if (anyModified) reversedResult else messages
    }
}
