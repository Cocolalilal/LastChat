package me.rerere.lastchat.ios

import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition

internal fun List<PortableSkill>.upsertSkill(skill: PortableSkill): List<PortableSkill> {
    return if (any { it.id == skill.id }) map { if (it.id == skill.id) skill else it } else this + skill
}

internal fun List<PortableLorebook>.upsertLorebook(book: PortableLorebook): List<PortableLorebook> {
    return if (any { it.id == book.id }) map { if (it.id == book.id) book else it } else this + book
}

internal fun PortableLorebook.replaceEntry(entry: PortableLorebookEntry): PortableLorebook {
    val next = if (entries.any { it.id == entry.id }) {
        entries.map { if (it.id == entry.id) entry else it }
    } else {
        entries + entry
    }
    return copy(entries = next)
}

internal fun PortableLorebook.removeEntry(entryId: String): PortableLorebook =
    copy(entries = entries.filterNot { it.id == entryId })

internal fun parseLorebookKeywords(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

internal fun lorebookActivationForKeywords(keywords: List<String>, current: LorebookActivationKind): LorebookActivationKind {
    return when {
        current == LorebookActivationKind.RAG -> LorebookActivationKind.RAG
        keywords.isEmpty() -> LorebookActivationKind.ALWAYS
        else -> LorebookActivationKind.KEYWORDS
    }
}

internal fun promptInjectionLabel(position: PromptInjectionPosition): String = when (position) {
    PromptInjectionPosition.BEFORE_SYSTEM -> "Before system"
    PromptInjectionPosition.AFTER_SYSTEM -> "After system"
    PromptInjectionPosition.TOP_OF_CHAT -> "Top of chat"
    PromptInjectionPosition.BEFORE_LATEST -> "Before latest"
    PromptInjectionPosition.AT_DEPTH -> "At depth"
}
