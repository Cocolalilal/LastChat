package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.rikkahub.service.assist.AssistScreenHolder

/**
 * `take_screenshot` tool for the assistant overlay.
 *
 * Reports whether a screen capture from the moment the assistant was summoned is
 * available. The overlay's primary mechanism for feeding the screen to a vision model
 * is auto-attaching that capture as an image part on the user's message (see
 * AssistantOverlayVM + [AssistScreenHolder]); this tool exposes the same capability as
 * an explicit model-initiated "pull".
 *
 * NOTE: returning the raw pixels *through the tool result* requires GenerationHandler to
 * support image parts in tool results. Until then, enable "Attach current screen" so the
 * capture rides along with the user's message.
 */
fun assistScreenshotTool(): Tool = Tool(
    name = "take_screenshot",
    description = "Get a screenshot of the screen the user was looking at when they " +
        "summoned the assistant. Use this when you need visual context about the user's " +
        "current screen to answer their question.",
    parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
    approvalMode = ToolApprovalMode.Auto,
    execute = {
        buildJsonObject {
            if (AssistScreenHolder.hasFreshScreenshot()) {
                put("available", JsonPrimitive(true))
                put(
                    "note",
                    JsonPrimitive(
                        "A screenshot of the user's screen at summon time is attached to " +
                            "this conversation as an image. Refer to it directly."
                    )
                )
            } else {
                put("available", JsonPrimitive(false))
                put(
                    "note",
                    JsonPrimitive(
                        "No screen capture is available (the system did not provide one, " +
                            "or it has expired)."
                    )
                )
            }
        }
    },
)
