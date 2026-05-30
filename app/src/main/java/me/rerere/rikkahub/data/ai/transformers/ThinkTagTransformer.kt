package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.extractLatestReasoningSummaryTitle
import kotlin.time.Clock

private val THINKING_REGEX = Regex(
    "<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|$)",
    RegexOption.DOT_MATCHES_ALL
)
private val CLOSING_TAG_REGEX = Regex("</think(?:ing)?>")

// Some providers stream reasoning inside <think> tags instead of native reasoning parts.
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(messages, finishUnclosed = false)
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(messages, finishUnclosed = true)
    }

    private fun transformMessages(
        messages: List<UIMessage>,
        finishUnclosed: Boolean,
    ): List<UIMessage> {
        val generationFinishedAt = if (finishUnclosed) Clock.System.now() else null
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT || !message.hasPart<UIMessagePart.Text>()) {
                return@map message
            }

            message.copy(
                parts = message.parts.flatMap { part ->
                    if (part !is UIMessagePart.Text || !THINKING_REGEX.containsMatchIn(part.text)) {
                        return@flatMap listOf(part)
                    }

                    val reasoning = THINKING_REGEX.find(part.text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    val strippedText = part.text.replace(THINKING_REGEX, "").trim()
                    if (reasoning.isBlank()) {
                        return@flatMap listOf(part.copy(text = strippedText))
                    }

                    val hasClosingTag = CLOSING_TAG_REGEX.containsMatchIn(part.text)
                    val reasoningPart = UIMessagePart.Reasoning(
                        reasoning = reasoning,
                        createdAt = message.createdAt.toInstant(TimeZone.currentSystemDefault()),
                        finishedAt = when {
                            finishUnclosed -> generationFinishedAt
                            hasClosingTag -> Clock.System.now()
                            else -> null
                        },
                        title = reasoning.extractLatestReasoningSummaryTitle()
                    )

                    listOf(
                        reasoningPart,
                        part.copy(text = strippedText),
                    )
                }
            )
        }
    }
}
