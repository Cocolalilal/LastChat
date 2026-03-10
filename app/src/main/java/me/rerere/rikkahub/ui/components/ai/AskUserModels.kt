package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

const val ASK_USER_TOOL_NAME = "ask_user"

@Immutable
data class AskUserQuestion(
    val id: String,
    val question: String,
    val options: List<String> = emptyList(),
)

@Immutable
data class AskUserPrompt(
    val toolCallId: String,
    val questions: List<AskUserQuestion>,
)

@Immutable
data class AskUserAnsweredEntry(
    val questionId: String,
    val question: String,
    val answer: String,
)

@Immutable
data class AskUserAnsweredSummary(
    val toolCallId: String,
    val entries: List<AskUserAnsweredEntry>,
)

sealed interface AskUserComposerStage {
    data class Question(
        val index: Int,
        val total: Int,
        val question: AskUserQuestion,
    ) : AskUserComposerStage

    data object Review : AskUserComposerStage
}

@Immutable
data class AskUserComposerMode(
    val prompt: AskUserPrompt,
    val stage: AskUserComposerStage,
    val answers: Map<String, String>,
)

fun UIMessagePart.ToolCall.toAskUserPromptOrNull(): AskUserPrompt? {
    if (toolName != ASK_USER_TOOL_NAME) return null
    val root = runCatching { JsonInstant.parseToJsonElement(arguments) }.getOrNull() as? JsonObject
        ?: return null
    val questions = root["questions"] as? JsonArray ?: return null
    val parsedQuestions = questions.mapNotNull { item ->
        val questionObject = item as? JsonObject ?: return@mapNotNull null
        val id = (questionObject["id"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val question = (questionObject["question"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (id.isBlank() || question.isBlank()) return@mapNotNull null
        AskUserQuestion(
            id = id,
            question = question,
            options = (questionObject["options"] as? JsonArray)
                ?.mapNotNull { option ->
                    (option as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
                }
                .orEmpty(),
        )
    }
    return parsedQuestions.takeIf { it.isNotEmpty() }?.let {
        AskUserPrompt(
            toolCallId = toolCallId,
            questions = it,
        )
    }
}

fun buildAskUserAnswerPayload(answers: Map<String, String>): String {
    return JsonInstant.encodeToString(
        buildJsonObject {
            put(
                "answers",
                buildJsonObject {
                    answers.forEach { (questionId, answer) ->
                        put(questionId, answer)
                    }
                }
            )
        }
    )
}

fun parseAskUserAnswerPayload(raw: String): Map<String, String> {
    val root = runCatching { JsonInstant.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        ?: return emptyMap()
    val answersObject = when (val answers = root["answers"]) {
        is JsonObject -> answers
        else -> root
    }
    return answersObject.mapNotNull { (questionId, value) ->
        val answer = (value as? JsonPrimitive)?.content?.trim().orEmpty()
        if (questionId.isBlank() || answer.isBlank()) {
            null
        } else {
            questionId to answer
        }
    }.toMap()
}

fun UIMessagePart.ToolCall.toAskUserAnsweredSummaryOrNull(): AskUserAnsweredSummary? {
    val prompt = toAskUserPromptOrNull() ?: return null
    val approvalState = approvalState as? ToolApprovalState.Answered ?: return null
    val answers = parseAskUserAnswerPayload(approvalState.answer)
    val entries = prompt.questions.mapNotNull { question ->
        answers[question.id]?.let { answer ->
            AskUserAnsweredEntry(
                questionId = question.id,
                question = question.question,
                answer = answer,
            )
        }
    }
    return entries.takeIf { it.isNotEmpty() }?.let {
        AskUserAnsweredSummary(
            toolCallId = toolCallId,
            entries = it,
        )
    }
}
