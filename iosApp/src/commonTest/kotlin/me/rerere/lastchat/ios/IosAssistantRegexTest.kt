package me.rerere.lastchat.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.PortableAffectScope
import me.rerere.rikkahub.data.model.PortableAssistantRegex
import me.rerere.rikkahub.data.skill.PortableSkillPackage

class IosAssistantRegexTest {
    @Test
    fun visualAndGenerationScopesMatchAndroid() {
        val rules = listOf(
            PortableAssistantRegex(
                id = "g",
                findRegex = "foo",
                replaceString = "bar",
                affectingScope = setOf(PortableAffectScope.ASSISTANT),
            ),
            PortableAssistantRegex(
                id = "v",
                findRegex = "hide",
                replaceString = "",
                affectingScope = setOf(PortableAffectScope.ASSISTANT),
                visualOnly = true,
            ),
        )
        val messages = listOf(
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("foo hide"))),
        )
        val stored = messages.applyIosRegexes(rules, visual = false)
        assertEquals("bar hide", (stored.single().parts.single() as UIMessagePart.Text).text)
        assertEquals("bar ", "foo hide".applyIosRegexes(rules, outgoing = false, visual = true))
    }

    @Test
    fun generationListTransformSkipsUserMessages() {
        val rules = listOf(
            PortableAssistantRegex(
                id = "both",
                findRegex = "foo",
                replaceString = "bar",
                affectingScope = setOf(PortableAffectScope.USER, PortableAffectScope.ASSISTANT),
            ),
        )
        val messages = listOf(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("foo"))),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("foo"))),
        )
        val stored = messages.applyIosRegexes(rules, visual = false)
        assertEquals("foo", (stored[0].parts.single() as UIMessagePart.Text).text)
        assertEquals("bar", (stored[1].parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun skillPackageExportIsImportable() {
        val zip = PortableSkillPackage.exportZip(
            "guide",
            mapOf(
                "SKILL.md" to PortableSkillPackage.exportToSkillMd(
                    name = "guide",
                    description = "Help",
                    instructions = "Follow the notes.",
                ).encodeToByteArray(),
                "notes.md" to "detail".encodeToByteArray(),
            ),
        )
        val imported = PortableSkillPackage.importFromBytes(zip)
        val success = imported as PortableSkillPackage.ImportResult.Success
        assertEquals("guide", success.parsed.name)
        assertEquals("detail", success.packageFiles.getValue("notes.md").decodeToString())
    }
}
