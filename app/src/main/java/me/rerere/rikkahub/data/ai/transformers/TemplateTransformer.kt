package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

interface MessageTemplateRenderer {
    fun render(templateId: String, context: Map<String, Any?>): String
}

interface MessageTemplateContextFactory {
    fun build(message: String, role: MessageRole): Map<String, Any?>
}

class TemplateTransformer(
    private val renderer: MessageTemplateRenderer,
    private val contextFactory: MessageTemplateContextFactory,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val templateId = ctx.assistant.id.toString()
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            part.copy(
                                text = renderer.render(
                                    templateId = templateId,
                                    context = contextFactory.build(
                                        message = part.text,
                                        role = message.role,
                                    )
                                )
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }
}
