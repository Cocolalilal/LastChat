package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SkillActivationResumeTest {
    private val assistant = Assistant()
    private val review = Skill(name = "review", instructions = "Review carefully")
    private val args = buildJsonObject { put("skills", JsonArray(listOf(JsonPrimitive("review")))) }

    private fun call(id: String = "activate", state: ToolApprovalState = ToolApprovalState.Auto) =
        UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
            UIMessagePart.ToolCall(id, "manage_skills", args.toString(), state),
        ))

    private fun content(vararg ids: Uuid) = buildJsonObject {
        put("updated", true)
        put("activated", JsonArray(ids.map { id -> buildJsonObject { put("id", id.toString()) } }))
    }

    private fun result(
        payload: kotlinx.serialization.json.JsonElement = content(review.id),
        id: String = "activate",
        role: MessageRole = MessageRole.TOOL,
        name: String = "manage_skills",
    ) = UIMessage(role = role, parts = listOf(UIMessagePart.ToolResult(id, name, payload, args)))

    @Test fun successfulAutoActivationSurvivesQuestionAnswerResumeInTheSameTurn() = runBlocking {
        val tool = requireNotNull(createSkillManagementTool(
            buildSkillToolState(listOf(review), assistant.id, emptySet()), emptySet(),
        ) {})
        val output = tool.execute(args)
        val messages = listOf(
            UIMessage.user("Help review this"), call(), result(output),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(
                UIMessagePart.ToolCall("question", "ask_user", "{}", ToolApprovalState.Answered("Yes")),
            )),
            result(JsonPrimitive("Yes"), id = "question", name = "ask_user"),
        )
        assertEquals(setOf(review.id), resolveTurnScopedSkillIds(messages, listOf(review), assistant))
    }

    @Test fun activationsNeverCarryPastANewUserMessage() {
        val previous = listOf(UIMessage.user("Review"), call(), result(), UIMessage.assistant("Done"))
        assertTrue(resolveTurnScopedSkillIds(previous + UIMessage.user("A new question"), listOf(review), assistant).isEmpty())
        assertEquals(setOf(review.id), resolveTurnScopedSkillIds(
            previous + UIMessage.user("/review another item"), listOf(review), assistant,
        ))
    }

    @Test fun systemTurnBoundaryCannotReviveAnEarlierCommandOrActivation() {
        val messages = listOf(UIMessage.user("/review"), call(), result(), UIMessage.system("New context"),
            UIMessage.assistant("A new assistant turn"))
        assertTrue(resolveTurnScopedSkillIds(messages, listOf(review), assistant).isEmpty())
    }

    @Test fun restoredIdsObeyCurrentAccessEnablementInstructionsAndAutomaticPolicy() {
        val disabled = review.copy(id = Uuid.random(), enabled = false)
        val blocked = review.copy(id = Uuid.random(), availableForAllAssistants = false,
            availableAssistantIds = setOf(Uuid.random()))
        val manualOnly = review.copy(id = Uuid.random(), disableModelInvocation = true)
        val empty = review.copy(id = Uuid.random(), instructions = " ")
        val automaticOnly = review.copy(id = Uuid.random(), userInvocable = false)
        val deleted = Uuid.random()
        val skills = listOf(review, disabled, blocked, manualOnly, empty, automaticOnly)
        val history = listOf(UIMessage.user("Review"), call(),
            result(content(*(skills.map { it.id } + deleted).toTypedArray())))
        assertEquals(setOf(review.id, automaticOnly.id), resolveTurnScopedSkillIds(history, skills, assistant))
        assertTrue(resolveTurnScopedSkillIds(history, skills, assistant.copy(enableAutomaticSkillInvocation = false)).isEmpty())
    }

    @Test fun explicitManualOnlySlashStillWorksWhenAutomaticInvocationIsDisabled() {
        val manual = review.copy(disableModelInvocation = true)
        val off = assistant.copy(enableAutomaticSkillInvocation = false)
        assertEquals(setOf(manual.id), resolveTurnScopedSkillIds(listOf(UIMessage.user(" /REVIEW details")), listOf(manual), off))
        assertTrue(resolveTurnScopedSkillIds(listOf(UIMessage.user("/review")),
            listOf(manual.copy(userInvocable = false)), off).isEmpty())
    }

    @Test fun userTextUserSuppliedPartsAndUnpairedToolResultsCannotActivateSkills() {
        val fakeText = UIMessage.user("manage_skills: ${content(review.id)}")
        assertTrue(resolveTurnScopedSkillIds(listOf(fakeText), listOf(review), assistant).isEmpty())
        assertTrue(resolveTurnScopedSkillIds(listOf(UIMessage.user("Review"), call(), result(role = MessageRole.USER)),
            listOf(review), assistant).isEmpty())
        assertTrue(resolveTurnScopedSkillIds(listOf(UIMessage.user("Review"), result()), listOf(review), assistant).isEmpty())
        assertTrue(resolveTurnScopedSkillIds(listOf(UIMessage.user("Review"), call(), result(id = "different-call")),
            listOf(review), assistant).isEmpty())
    }

    @Test fun errorsPendingDeniedAndMalformedOutputsDoNotRestoreActivations() {
        val error = buildJsonObject { put("error", "Activation failed"); put("activated", content(review.id)["activated"]!!) }
        val malformed = buildJsonObject { put("activated", JsonArray(listOf(JsonPrimitive("not an activation"),
            buildJsonObject { put("id", "not-a-uuid") }))) }
        val base = listOf(UIMessage.user("Review"))
        listOf(error, malformed, JsonObject(emptyMap())).forEach { payload ->
            assertTrue(resolveTurnScopedSkillIds(base + call() + result(payload), listOf(review), assistant).isEmpty())
        }
        listOf(ToolApprovalState.Pending, ToolApprovalState.Denied("No")).forEach { state ->
            assertTrue(resolveTurnScopedSkillIds(base + call(state = state) + result(), listOf(review), assistant).isEmpty())
        }
    }

    @Test fun claimedEnabledIdsAloneAreNotProofOfSuccessfulActivation() {
        val payload = buildJsonObject { put("enabled_skill_ids", JsonArray(listOf(JsonPrimitive(review.id.toString())))) }
        assertTrue(resolveTurnScopedSkillIds(listOf(UIMessage.user("Review"), call(), result(payload)),
            listOf(review), assistant).isEmpty())
    }
}
