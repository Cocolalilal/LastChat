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

    internal fun transformMessages(
        messages: List<UIMessage>,
        finishUnclosed: Boolean,
    ): List<UIMessage> {
        val generationFinishedAt = if (finishUnclosed) Clock.System.now() else null
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) {
                return@map message
            }

            var transformedParts = message.parts.flatMap { part ->
                if (part !is UIMessagePart.Text || !part.text.contains("<think", ignoreCase = true)) {
                    return@flatMap listOf(part)
                }

                val matches = THINKING_REGEX.findAll(part.text).toList()
                if (matches.isEmpty()) {
                    return@flatMap listOf(part)
                }

                val resultParts = mutableListOf<UIMessagePart>()
                val sb = StringBuilder()
                var lastIndex = 0

                for (match in matches) {
                    sb.append(part.text, lastIndex, match.range.first)
                    lastIndex = match.range.last + 1

                    val reasoning = match.groupValues.getOrNull(1)?.trim().orEmpty()
                    if (reasoning.isNotBlank()) {
                        val hasClosingTag = CLOSING_TAG_REGEX.containsMatchIn(match.value)
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
                        resultParts.add(reasoningPart)
                    }
                }
                if (lastIndex < part.text.length) {
                    sb.append(part.text, lastIndex, part.text.length)
                }

                val strippedText = sb.toString().trim()
                if (strippedText.isNotEmpty() || resultParts.isEmpty()) {
                    resultParts.add(part.copy(text = strippedText))
                }
                resultParts
            }

            if (finishUnclosed) {
                transformedParts = transformedParts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = generationFinishedAt)
                    } else {
                        part
                    }
                }
            }

            message.copy(parts = transformedParts)
        }
    }
}
