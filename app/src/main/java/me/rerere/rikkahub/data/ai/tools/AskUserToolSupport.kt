package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.MessageRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlin.uuid.Uuid

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
    val parsed = parseJsonElementWithRecovery(arguments, json) ?: return null
    return parseAskUserQuestionnaireInternal(parsed, json, depth = 0)
}

fun parseAskUserQuestionnaire(arguments: JsonElement): AskUserQuestionnaire? {
    return parseAskUserQuestionnaireInternal(arguments, JsonInstant, depth = 0)
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
        parseJsonElementWithRecovery(raw, json)
    }
    return normalizeAskUserAnswerPayload(
        questionnaire = questionnaire,
        rawAnswer = parsed,
        dismissedFallback = dismissedFallback,
    )
}

fun normalizeAskUserAnswerPayload(
    questionnaire: AskUserQuestionnaire,
    rawAnswer: JsonElement?,
    dismissedFallback: Boolean = false,
): AskUserAnswerPayload {
    val root = rawAnswer as? JsonObject
    val arrayAnswers = root?.get("answers") as? JsonArray
    val mapAnswers = root?.get("answers") as? JsonObject
    val dismissed = root?.get("dismissed")?.jsonPrimitiveOrNull?.booleanOrNull ?: dismissedFallback

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

internal fun List<UIMessage>.recoverInlineAskUserToolCall(json: Json = JsonInstant): List<UIMessage> {
    val lastMessage = lastOrNull() ?: return this
    val recoveredMessage = lastMessage.recoverInlineAskUserToolCall(json) ?: return this
    return dropLast(1) + recoveredMessage
}

internal fun UIMessage.recoverInlineAskUserToolCall(json: Json = JsonInstant): UIMessage? {
    if (role != MessageRole.ASSISTANT || getToolCalls().isNotEmpty()) {
        return null
    }
    if (parts.none { part -> part is UIMessagePart.Text }) {
        return null
    }

    val contentText = toContentText().trim()
    if (!looksLikeStandaloneAskUserPayload(contentText)) {
        return null
    }

    val questionnaire = parseAskUserQuestionnaire(contentText, json) ?: return null
    return copy(
        parts = parts.filterNot { part -> part is UIMessagePart.Text } + UIMessagePart.ToolCall(
            toolCallId = "ask_user_recovered_${Uuid.random()}",
            toolName = ASK_USER_TOOL_NAME,
            arguments = questionnaire.toJsonElement().toString(),
        )
    )
}

internal fun parseJsonElementWithRecovery(
    arguments: String,
    json: Json = JsonInstant,
): JsonElement? {
    return parseJsonElementWithRecovery(
        arguments = arguments,
        json = json,
        unwrapNestedStrings = true,
    )
}

private fun parseAskUserQuestion(question: JsonElement): AskUserQuestion? {
    val record = question as? JsonObject ?: return null
    val id = record["id"]?.jsonPrimitiveOrNull?.contentOrNull?.trim().orEmpty()
    val prompt = record["question"]?.jsonPrimitiveOrNull?.contentOrNull?.trim().orEmpty()
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
            val label = option["label"]?.jsonPrimitiveOrNull?.contentOrNull?.trim().orEmpty()
            if (label.isBlank()) {
                null
            } else {
                AskUserOption(
                    label = label,
                    description = option["description"]?.jsonPrimitiveOrNull?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
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
                ?.jsonPrimitiveOrNull
                ?.contentOrNull == questionId
        } as? JsonObject ?: return null

    val status = answer["status"]?.jsonPrimitiveOrNull?.contentOrNull?.trim()?.lowercase().orEmpty()
    if (status == "skipped") {
        return AskUserAnswer(
            id = questionId,
            status = "skipped",
        )
    }

    val value = answer["value"]?.jsonPrimitiveOrNull?.contentOrNull?.trim().orEmpty()
    if (status != "answered" || value.isBlank()) {
        return null
    }

    val source = answer["source"]?.jsonPrimitiveOrNull?.contentOrNull?.trim()?.lowercase()
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
        ?.jsonPrimitiveOrNull
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

private fun parseAskUserQuestionnaireInternal(
    arguments: JsonElement?,
    json: Json,
    depth: Int,
): AskUserQuestionnaire? {
    if (arguments == null || depth > 8) {
        return null
    }

    return when (arguments) {
        is JsonObject -> {
            parseAskUserQuestions(arguments["questions"], json, depth + 1)
                ?.let { questions -> AskUserQuestionnaire(questions = questions) }
                ?: ASK_USER_WRAPPER_KEYS.firstNotNullOfOrNull { key ->
                    parseAskUserQuestionnaireInternal(arguments[key], json, depth + 1)
                }
                ?: if (resolveAskUserToolName(arguments) == ASK_USER_TOOL_NAME) {
                    arguments.entries.firstNotNullOfOrNull { (key, value) ->
                        if (key in ASK_USER_TOOL_NAME_KEYS) {
                            null
                        } else {
                            parseAskUserQuestionnaireInternal(value, json, depth + 1)
                        }
                    }
                } else {
                    null
                }
        }

        is JsonArray -> {
            parseAskUserQuestions(arguments, json, depth + 1)
                ?.let { questions -> AskUserQuestionnaire(questions = questions) }
                ?: arguments.firstNotNullOfOrNull { element ->
                    parseAskUserQuestionnaireInternal(element, json, depth + 1)
                }
        }

        is JsonPrimitive -> {
            arguments.contentOrNull
                ?.takeIf { content -> content.isNotBlank() }
                ?.let { content -> parseJsonElementWithRecovery(content, json) }
                ?.takeIf { recovered -> recovered != arguments }
                ?.let { recovered -> parseAskUserQuestionnaireInternal(recovered, json, depth + 1) }
        }
    }
}

private fun parseAskUserQuestions(
    element: JsonElement?,
    json: Json,
    depth: Int,
): List<AskUserQuestion>? {
    return when (element) {
        is JsonArray -> element
            .mapNotNull(::parseAskUserQuestion)
            .take(ASK_USER_MAX_QUESTIONS)
            .takeIf { questions -> questions.isNotEmpty() }

        is JsonObject -> parseAskUserQuestionnaireInternal(element, json, depth + 1)?.questions
        is JsonPrimitive -> element.contentOrNull
            ?.takeIf { content -> content.isNotBlank() }
            ?.let { content -> parseJsonElementWithRecovery(content, json) }
            ?.takeIf { recovered -> recovered != element }
            ?.let { recovered -> parseAskUserQuestions(recovered, json, depth + 1) }

        else -> null
    }
}

private fun resolveAskUserToolName(root: JsonObject): String? {
    return ASK_USER_TOOL_NAME_KEYS.firstNotNullOfOrNull { key ->
        root[key]
            ?.jsonPrimitiveOrNull
            ?.contentOrNull
            ?.trim()
            ?.lowercase()
            ?.takeIf { value -> value.isNotBlank() }
    }
}

private fun parseJsonElementWithRecovery(
    arguments: String,
    json: Json,
    unwrapNestedStrings: Boolean,
): JsonElement? {
    if (arguments.isBlank()) {
        return runCatching { json.parseToJsonElement("{}") }.getOrNull()
    }
    val trimmed = arguments.trim()

    val fenced = extractJsonCodeFence(trimmed)
    val candidates = listOfNotNull(
        trimmed,
        fenced,
        extractBalancedJsonSlice(trimmed),
        fenced?.let(::extractBalancedJsonSlice),
    ).distinct()

    val parsed = candidates.firstNotNullOfOrNull { candidate ->
        runCatching { json.parseToJsonElement(candidate) }.getOrNull()
    } ?: return null

    return if (unwrapNestedStrings) {
        unwrapNestedJsonString(parsed, json)
    } else {
        parsed
    }
}

private fun unwrapNestedJsonString(
    element: JsonElement,
    json: Json,
    maxDepth: Int = 4,
): JsonElement {
    var current = element
    repeat(maxDepth) {
        val content = (current as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return current
        current = parseJsonElementWithRecovery(
            arguments = content,
            json = json,
            unwrapNestedStrings = false,
        ) ?: return current
    }
    return current
}

private fun extractJsonCodeFence(input: String): String? {
    return ASK_USER_JSON_CODE_FENCE_REGEX.matchEntire(input)?.groupValues?.getOrNull(1)
}

private fun extractBalancedJsonSlice(input: String): String? {
    if (input.isBlank()) {
        return null
    }

    val objectStart = input.indexOf('{')
    val arrayStart = input.indexOf('[')
    val startIndex = when {
        objectStart < 0 -> arrayStart
        arrayStart < 0 -> objectStart
        else -> minOf(objectStart, arrayStart)
    }
    if (startIndex < 0) {
        return null
    }

    val stack = ArrayDeque<Char>()
    var inString = false
    var escaping = false

    for (index in startIndex until input.length) {
        val char = input[index]
        if (escaping) {
            escaping = false
            continue
        }

        when (char) {
            '\\' -> {
                if (inString) {
                    escaping = true
                }
            }

            '"' -> inString = !inString

            else -> {
                if (inString) {
                    continue
                }

                when (char) {
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> {
                        if (stack.isEmpty() || stack.removeLast() != char) {
                            return null
                        }
                        if (stack.isEmpty()) {
                            return input.substring(startIndex, index + 1)
                        }
                    }
                }
            }
        }
    }

    return null
}

private fun looksLikeStandaloneAskUserPayload(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) {
        return false
    }
    if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```")) {
        return true
    }

    val candidate = extractBalancedJsonSlice(trimmed) ?: return false
    val startIndex = trimmed.indexOf(candidate)
    if (startIndex < 0) {
        return false
    }

    val wrapperText = buildString {
        append(trimmed.substring(0, startIndex))
        append(trimmed.substring(startIndex + candidate.length))
    }.trim()
    return isAskUserWrapperText(wrapperText)
}

private fun isAskUserWrapperText(text: String): Boolean {
    if (text.isBlank()) {
        return true
    }

    val lowered = text.lowercase()
    if (
        !lowered.contains(ASK_USER_TOOL_NAME) &&
        !lowered.contains("tool_call") &&
        !lowered.contains("function_call")
    ) {
        return false
    }

    val normalized = lowered
        .replace(ASK_USER_TOOL_NAME, "")
        .replace("tool_call", "")
        .replace("function_call", "")
        .replace("assistant", "")
        .replace("json", "")
        .replace("function", "")
        .replace("tool", "")
        .replace("call", "")
        .replace("name", "")
        .replace("arguments", "")
        .replace("arg", "")
        .replace("to", "")
        .replace(Regex("""[\s:={}\[\]()"'.,;`_-]+"""), "")

    return normalized.isBlank()
}

private val ASK_USER_WRAPPER_KEYS = listOf(
    "arguments",
    "input",
    "payload",
    "questionnaire",
    "params",
    "data",
    "value",
    "function",
    "function_call",
    "tool_calls",
    "toolCall",
    "call",
)

private val ASK_USER_TOOL_NAME_KEYS = listOf(
    "name",
    "toolName",
    "tool",
    "type",
)

private val ASK_USER_JSON_CODE_FENCE_REGEX = Regex(
    pattern = """^```(?:json)?\s*([\s\S]*?)\s*```$""",
    option = RegexOption.IGNORE_CASE,
)
