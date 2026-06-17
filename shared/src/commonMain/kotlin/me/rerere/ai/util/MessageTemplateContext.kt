package me.rerere.ai.util

import me.rerere.ai.core.MessageRole

data class MessageTemplateContext(
    val message: String,
    val role: String,
    val time: String,
    val date: String,
) {
    fun asMap(): Map<String, String> = mapOf(
        "message" to message,
        "role" to role,
        "time" to time,
        "date" to date,
    )

    companion object {
        fun build(
            message: String,
            role: MessageRole,
            time: String,
            date: String,
        ): MessageTemplateContext {
            return MessageTemplateContext(
                message = message,
                role = role.name.lowercase(),
                time = time,
                date = date,
            )
        }
    }
}
