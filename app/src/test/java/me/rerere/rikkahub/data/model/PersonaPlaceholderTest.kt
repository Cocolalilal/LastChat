package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaPlaceholderTest {
    @Test
    fun replacesUserAndCharPlaceholdersForDisplay() {
        val assistant = Assistant(name = "Rikka")

        val rendered = "{{char}} smiles at {{user}}. {CHAR}: ready for {USER}."
            .replacePersonaPlaceholders(
                assistant = assistant,
                userNickname = "Julian",
            )

        assertEquals("Rikka smiles at Julian. Rikka: ready for Julian.", rendered)
    }

    @Test
    fun usesBackendFallbackNamesWhenBlank() {
        val rendered = "{{char}} greets {{user}}."
            .replacePersonaPlaceholders(
                assistant = Assistant(name = ""),
                userNickname = "",
            )

        assertEquals("assistant greets user.", rendered)
    }
}
