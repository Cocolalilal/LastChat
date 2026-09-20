package me.rerere.lastchat.ios

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionEngine
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition

internal fun applyInContextPromptInjections(
    messages: List<UIMessage>,
    skills: List<PortableSkill>,
    lorebookEntries: List<PortableLorebookEntry>,
): List<UIMessage> {
    val topSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
    val topEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.TOP_OF_CHAT }
    val beforeLatestSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST }
    val beforeLatestEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.BEFORE_LATEST }
    val depthSkills = skills.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
    val depthEntries = lorebookEntries.filter { it.injectionPosition == PromptInjectionPosition.AT_DEPTH }
    if (
        topSkills.isEmpty() && topEntries.isEmpty() &&
        beforeLatestSkills.isEmpty() && beforeLatestEntries.isEmpty() &&
        depthSkills.isEmpty() && depthEntries.isEmpty()
    ) {
        return messages
    }

    val latestUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val historical = if (latestUserIndex >= 0) messages.take(latestUserIndex) else messages
    val latest = if (latestUserIndex >= 0) messages.drop(latestUserIndex) else emptyList()

    return buildList {
        topSkills.forEach { add(UIMessage.user(PromptInjectionEngine.inContextSkillText(it))) }
        topEntries.forEach { add(UIMessage.user(PromptInjectionEngine.inContextLorebookText(it))) }

        val depthCount = historical.size
        val groupedSkills = depthSkills.groupBy { skill ->
            (depthCount - skill.depth.coerceAtLeast(0)).coerceIn(0, depthCount)
        }
        val groupedEntries = depthEntries.groupBy { entry ->
            (depthCount - entry.depth.coerceAtLeast(0)).coerceIn(0, depthCount)
        }
        for (index in 0..historical.size) {
            groupedSkills[index].orEmpty().forEach { add(UIMessage.user(PromptInjectionEngine.inContextSkillText(it))) }
            groupedEntries[index].orEmpty().forEach { add(UIMessage.user(PromptInjectionEngine.inContextLorebookText(it))) }
            if (index < historical.size) add(historical[index])
        }

        beforeLatestSkills.forEach { add(UIMessage.user(PromptInjectionEngine.inContextSkillText(it))) }
        beforeLatestEntries.forEach { add(UIMessage.user(PromptInjectionEngine.inContextLorebookText(it))) }
        addAll(latest)
    }
}

internal fun documentPromptText(fileName: String, extracted: String): String = """
    ## user sent a file: $fileName
    <content>
    ${extracted.ifBlank { "[No readable content found]" }}
    </content>
""".trimIndent()
