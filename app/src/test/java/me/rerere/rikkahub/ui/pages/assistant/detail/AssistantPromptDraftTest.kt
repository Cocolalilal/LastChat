package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptDraftTest {
    @Test
    fun replacingThePromptKeepsTheFirstMessage() {
        val original = assistant(prompt = "old prompt", intros = listOf("hello"))
        val holder = PromptDraftHolder(original)
        holder.replaceIntro(0, "pasted first message")
        holder.applySystemPrompt("replaced prompt")

        assertEquals("replaced prompt", holder.assistant.systemPrompt)
        assertEquals(listOf("pasted first message"), holder.assistant.introTexts())
    }

    @Test
    fun pastingAFirstMessageKeepsThePrompt() {
        val original = assistant(prompt = "keep me", intros = listOf("old intro", "alt"))
        val holder = PromptDraftHolder(original)
        holder.applySystemPrompt("newer prompt")
        holder.replaceIntro(0, "pasted first message")

        assertEquals("newer prompt", holder.assistant.systemPrompt)
        assertEquals(listOf("pasted first message", "alt"), holder.assistant.introTexts())
    }

    @Test
    fun staleAssistantCopyDropsTheFirstMessage() {
        val original = assistant(prompt = "old", intros = listOf("old intro"))
        val withIntro = original.withIntros(listOf("pasted first message"))
        val stalePromptWrite = original.copy(systemPrompt = "replaced prompt")

        assertEquals(listOf("pasted first message"), withIntro.introTexts())
        assertTrue(stalePromptWrite.introTexts().single() != "pasted first message")
    }

    @Test
    fun flushOnLeaveWritesBothFieldsOnce() {
        val original = assistant(prompt = "old", intros = listOf("old intro"))
        val holder = PromptDraftHolder(original)
        holder.replaceIntro(0, "pasted first message")
        val saved = mutableListOf<Assistant>()
        holder.flush(
            systemPrompt = "replaced prompt",
            persisted = original,
            onUpdate = { saved += it },
        )
        holder.flush(
            systemPrompt = "replaced prompt",
            persisted = original,
            onUpdate = { saved += it },
        )

        assertEquals(1, saved.size)
        assertEquals("replaced prompt", saved.single().systemPrompt)
        assertEquals(listOf("pasted first message"), saved.single().introTexts())
    }

    @Test
    fun flushBeforeEditsDoesNotWrite() {
        val original = assistant(prompt = "old", intros = emptyList())
        val holder = PromptDraftHolder(original)
        var writes = 0
        holder.flush("old", original) { writes++ }
        assertEquals(0, writes)
    }

    private fun assistant(prompt: String, intros: List<String>): Assistant {
        return Assistant(systemPrompt = prompt).withIntros(intros)
    }
}
