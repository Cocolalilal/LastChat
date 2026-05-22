package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltInToolResolutionTest {
    @Test
    fun `resolveActiveBuiltInTools keeps url context enabled without forcing google search`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
        )
        val assistant = Assistant(
            searchMode = AssistantSearchMode.Provider(index = 0),
            preferBuiltInSearch = false
        )

        assertEquals(
            setOf(BuiltInTools.UrlContext),
            resolveActiveBuiltInTools(model, assistant)
        )
    }

    @Test
    fun `resolveActiveBuiltInTools enables google search when assistant prefers built in search`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
        )
        val assistant = Assistant(preferBuiltInSearch = true)

        assertEquals(
            setOf(BuiltInTools.Search, BuiltInTools.UrlContext),
            resolveActiveBuiltInTools(model, assistant)
        )
    }

    @Test
    fun `resolveActiveBuiltInTools respects legacy built in mode`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.Search)
        )
        val assistant = Assistant(searchMode = AssistantSearchMode.BuiltIn)

        assertEquals(
            setOf(BuiltInTools.Search),
            resolveActiveBuiltInTools(model, assistant)
        )
    }

    @Test
    fun `shouldUseBuiltInSearch ignores unrelated built in tools`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.UrlContext)
        )
        val assistant = Assistant(
            searchMode = AssistantSearchMode.Provider(index = 0),
            preferBuiltInSearch = true
        )

        assertEquals(false, shouldUseBuiltInSearch(model, assistant))
    }

    @Test
    fun `shouldUseBuiltInSearch requires assistant opt in`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
        )
        val assistant = Assistant(
            searchMode = AssistantSearchMode.Provider(index = 0),
            preferBuiltInSearch = false
        )

        assertEquals(false, shouldUseBuiltInSearch(model, assistant))
    }

    @Test
    fun `shouldUseBuiltInSearch enables search when preferred and supported`() {
        val model = Model(
            modelId = "gemini-3.1-pro-preview",
            tools = setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
        )
        val assistant = Assistant(
            searchMode = AssistantSearchMode.Provider(index = 0),
            preferBuiltInSearch = true
        )

        assertEquals(true, shouldUseBuiltInSearch(model, assistant))
    }
}
