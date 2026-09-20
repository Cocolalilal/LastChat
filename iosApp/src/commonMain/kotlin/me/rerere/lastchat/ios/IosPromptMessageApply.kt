package me.rerere.lastchat.ios

import me.rerere.ai.generation.PortableGenerationPrepare
import me.rerere.ai.generation.documentPromptText as portableDocumentPromptText
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill

internal fun applyInContextPromptInjections(
    messages: List<UIMessage>,
    skills: List<PortableSkill>,
    lorebookEntries: List<PortableLorebookEntry>,
): List<UIMessage> = PortableGenerationPrepare.applyInContextInjections(
    messages = messages,
    skills = skills,
    lorebookEntries = lorebookEntries,
)

internal fun documentPromptText(fileName: String, extracted: String): String =
    portableDocumentPromptText(fileName, extracted)
