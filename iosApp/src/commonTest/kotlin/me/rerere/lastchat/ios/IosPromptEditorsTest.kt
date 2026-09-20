package me.rerere.lastchat.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition

class IosPromptEditorsTest {
    @Test
    fun upsertReplacesExistingSkillAndLorebook() {
        val original = PortableSkill(id = "s1", name = "Old", instructions = "a")
        val updated = original.copy(name = "New", injectionPosition = PromptInjectionPosition.TOP_OF_CHAT)
        assertEquals(listOf(updated), listOf(original).upsertSkill(updated))
        val book = PortableLorebook(id = "b1", name = "World")
        assertEquals("Atlas", listOf(book).upsertLorebook(book.copy(name = "Atlas")).single().name)
    }

    @Test
    fun lorebookEntriesReplaceAndRemoveInPlace() {
        val entry = PortableLorebookEntry(id = "e1", name = "Town", prompt = "quiet")
        val book = PortableLorebook(id = "b1", name = "World", entries = listOf(entry))
        val edited = book.replaceEntry(entry.copy(prompt = "busy"))
        assertEquals("busy", edited.entries.single().prompt)
        val added = edited.replaceEntry(PortableLorebookEntry(id = "e2", name = "River", prompt = "flows"))
        assertEquals(2, added.entries.size)
        assertEquals(listOf("e1"), added.removeEntry("e2").entries.map { it.id })
    }

    @Test
    fun keywordParserAndActivationMatchAndroidSemantics() {
        assertEquals(listOf("town", "village"), parseLorebookKeywords(" town, village , "))
        assertEquals(LorebookActivationKind.ALWAYS, lorebookActivationForKeywords(emptyList(), LorebookActivationKind.KEYWORDS))
        assertEquals(
            LorebookActivationKind.KEYWORDS,
            lorebookActivationForKeywords(listOf("town"), LorebookActivationKind.ALWAYS),
        )
        assertEquals(
            LorebookActivationKind.RAG,
            lorebookActivationForKeywords(emptyList(), LorebookActivationKind.RAG),
        )
        assertEquals("After system", promptInjectionLabel(PromptInjectionPosition.AFTER_SYSTEM))
    }
}
