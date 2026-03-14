package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant

const val ASK_USER_TOOL_NAME = "ask_user"
const val ASK_USER_MAX_QUESTIONS = 5
const val ASK_USER_MAX_OPTIONS = 3

data class AskUserOption(
    val label: String,
    val description: String? = null,
)

data class AskUserQuestion(
    val id: String,
    val question: String,
    val options: List<AskUserOption> = emptyList(),
)

data class AskUserQuestionnaire(
    val questions: List<AskUserQuestion>,
)

data class AskUserAnswer(
    val id: String,
    val status: String,
    val source: String? = null,
    val value: String? = null,
)

data class AskUserAnswerPayload(
    val answers: List<AskUserAnswer>,
    val dismissed: Boolean,
)

data class PendingAskUserToolCall(
    val toolCallId: String,
    val questionnaire: AskUserQuestionnaire,
)

fun parseAskUserQuestionnaire(arguments: String, json: Json = JsonInstant): AskUserQuestionnaire? {
    val parsed = runCatching {
        json.parseToJsonElement(arguments.ifBlank { "{}" })
    }.getOrElse {
        runCatching {
            json.parseToJsonElement(sanitizeAskUserArguments(arguments))
        }.getOrNull()
    } ?: return null
    return parseAskUserQuestionnaire(parsed)
}

fun parseAskUserQuestionnaire(arguments: JsonElement): AskUserQuestionnaire? {
    val root = arguments as? JsonObject ?: return null
    val questions = (root["questions"] as? JsonArray)
        ?.mapNotNull(::parseAskUserQuestion)
        ?.take(ASK_USER_MAX_QUESTIONS)
        .orEmpty()
    if (questions.isEmpty()) {
        return null
    }
    return AskUserQuestionnaire(questions = questions)
}

fun AskUserQuestionnaire.toJsonElement(): JsonObject {
    return buildJsonObject {
        putJsonArray("questions") {
            questions.forEach { question ->
                add(
                    buildJsonObject {
                        put("id", question.id)
                        put("question", question.question)
                        if (question.options.isNotEmpty()) {
                            putJsonArray("options") {
                                question.options.forEach { option ->
                                    add(
                                        buildJsonObject {
                                            put("label", option.label)
                                            option.description?.let { put("description", it) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

fun normalizeAskUserAnswerPayload(
    questionnaire: AskUserQuestionnaire,
    rawAnswer: String?,
    dismissedFallback: Boolean = false,
    json: Json = JsonInstant,
): AskUserAnswerPayload {
    val parsed = rawAnswer?.let { raw ->
        runCatching { json.parseToJsonElement(raw) }.getOrNull()
    }

    val arrayAnswers = parsed?.jsonObject?.get("answers") as? JsonArray
    val mapAnswers = parsed?.jsonObject?.get("answers") as? JsonObject
    val dismissed = parsed?.jsonObject?.get("dismissed")?.jsonPrimitive?.booleanOrNull ?: dismissedFallback

    val normalizedAnswers = questionnaire.questions.map { question ->
        parseAskUserAnswerFromArray(arrayAnswers, question.id)
            ?: parseAskUserAnswerFromMap(mapAnswers, question.id)
            ?: AskUserAnswer(
                id = question.id,
                status = "skipped",
            )
    }

    return AskUserAnswerPayload(
        answers = normalizedAnswers,
        dismissed = dismissed,
    )
}

fun AskUserAnswerPayload.toJsonElement(): JsonObject {
    return buildJsonObject {
        putJsonArray("answers") {
            answers.forEach { answer ->
                add(
                    buildJsonObject {
                        put("id", answer.id)
                        put("status", answer.status)
                        answer.source?.let { put("source", it) }
                        answer.value?.let { put("value", it) }
                    }
                )
            }
        }
        put("dismissed", dismissed)
    }
}

fun List<UIMessage>.findPendingAskUserToolCall(json: Json = JsonInstant): PendingAskUserToolCall? {
    return asSequence()
        .flatMap { message -> message.getToolCalls().asSequence() }
        .firstNotNullOfOrNull { toolCall ->
            val isPending = toolCall.toolName == ASK_USER_TOOL_NAME &&
                toolCall.approvalState is ToolApprovalState.Pending
            if (!isPending) {
                return@firstNotNullOfOrNull null
            }

            val questionnaire = parseAskUserQuestionnaire(toolCall.arguments, json) ?: return@firstNotNullOfOrNull null
            PendingAskUserToolCall(
                toolCallId = toolCall.toolCallId,
                questionnaire = questionnaire,
            )
        }
}

private fun parseAskUserQuestion(question: JsonElement): AskUserQuestion? {
    val record = question as? JsonObject ?: return null
    val id = record["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val prompt = record["question"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (id.isBlank() || prompt.isBlank()) {
        return null
    }

    val options = (record["options"] as? JsonArray)
        ?.mapNotNull(::parseAskUserOption)
        ?.take(ASK_USER_MAX_OPTIONS)
        .orEmpty()

    return AskUserQuestion(
        id = id,
        question = prompt,
        options = options,
    )
}

private fun parseAskUserOption(option: JsonElement): AskUserOption? {
    return when (option) {
        is JsonPrimitive -> {
            val label = option.contentOrNull?.trim().orEmpty()
            if (label.isBlank()) {
                null
            } else {
                AskUserOption(label = label)
            }
        }

        is JsonObject -> {
            val label = option["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (label.isBlank()) {
                null
            } else {
                AskUserOption(
                    label = label,
                    description = option["description"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                )
            }
        }

        else -> null
    }
}

private fun parseAskUserAnswerFromArray(
    answers: JsonArray?,
    questionId: String,
): AskUserAnswer? {
    val answer = answers
        ?.firstOrNull { element ->
            (element as? JsonObject)
                ?.get("id")
                ?.jsonPrimitive
                ?.contentOrNull == questionId
        } as? JsonObject ?: return null

    val status = answer["status"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
    if (status == "skipped") {
        return AskUserAnswer(
            id = questionId,
            status = "skipped",
        )
    }

    val value = answer["value"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (status != "answered" || value.isBlank()) {
        return null
    }

    val source = answer["source"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        ?.takeIf { it == "option" || it == "custom" }
        ?: "custom"

    return AskUserAnswer(
        id = questionId,
        status = "answered",
        source = source,
        value = value,
    )
}

private fun parseAskUserAnswerFromMap(
    answers: JsonObject?,
    questionId: String,
): AskUserAnswer? {
    val value = answers
        ?.get(questionId)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        .orEmpty()
    if (value.isBlank()) {
        return null
    }
    return AskUserAnswer(
        id = questionId,
        status = "answered",
        source = "custom",
        value = value,
    )
}

private fun sanitizeAskUserArguments(arguments: String): String {
    if (arguments.isBlank()) return "{}"
    val trimmed = arguments.trim()
    var braceCount = 0
    var inString = false
    var escape = false

    for ((index, char) in trimmed.withIndex()) {
        if (escape) {
            escape = false
            continue
        }
        when (char) {
            '\\' -> if (inString) escape = true
            '"' -> inString = !inString
            '{' -> if (!inString) braceCount++
            '}' -> if (!inString) {
                braceCount--
                if (braceCount == 0) {
                    return trimmed.substring(0, index + 1)
                }
            }
        }
    }

    return "{}"
}
