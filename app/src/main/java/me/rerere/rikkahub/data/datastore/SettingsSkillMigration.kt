package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.Skill
import kotlin.math.min

/**
 * One-way migration: legacy Modes -> Skills.
 *
 * Keeps IDs stable so existing per-conversation selections remain valid.
 * Legacy modes are cleared after migration to prevent duplicate context injection.
 */
internal fun Settings.migrateLegacyModesToSkills(): Settings {
    if (modes.isEmpty()) return this

    val now = System.currentTimeMillis()
    val migratedSkills = skills.toMutableList()
    val existingSkillIds = migratedSkills.map { it.id }.toMutableSet()
    val usedNames = migratedSkills.map { it.name.lowercase() }.toMutableSet()
    val defaultEnabledModeIds = mutableSetOf<kotlin.uuid.Uuid>()

    modes.forEach { mode ->
        if (mode.defaultEnabled) {
            defaultEnabledModeIds += mode.id
        }
        if (existingSkillIds.contains(mode.id)) {
            return@forEach
        }

        val baseName = mode.name.toSkillNameSeed()
        val uniqueName = uniqueSkillName(baseName, usedNames)
        migratedSkills += mode.toSkill(
            name = uniqueName,
            createdAt = now
        )
        existingSkillIds += mode.id
    }

    val mergedAssistants = assistants.map { assistant ->
        assistant.copy(
            enabledSkillIds = assistant.enabledSkillIds + defaultEnabledModeIds
        )
    }

    return copy(
        modes = emptyList(),
        skills = migratedSkills,
        assistants = mergedAssistants
    )
}

private fun Mode.toSkill(
    name: String,
    createdAt: Long
): Skill {
    val migratedDescription = if (this.name.isNotBlank()) {
        "Migrated from Mode '${this.name}'."
    } else {
        "Migrated from legacy Mode."
    }

    return Skill(
        id = this.id,
        name = name,
        description = migratedDescription,
        icon = this.icon,
        instructions = this.prompt,
        attachments = this.attachments,
        // Legacy mode "defaultEnabled" means "enabled by default in new chats",
        // not "globally disabled". Skills should stay enabled after migration.
        enabled = true,
        injectionPosition = this.injectionPosition,
        depth = this.depth,
        argumentHint = "/$name",
        createdAt = createdAt,
        updatedAt = createdAt
    )
}

private fun String.toSkillNameSeed(): String {
    val sanitized = lowercase()
        .map { ch ->
            when {
                ch.isLetterOrDigit() -> ch
                ch == '-' || ch == '_' -> ch
                ch.isWhitespace() -> '-'
                else -> '-'
            }
        }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')

    if (sanitized.isNotBlank()) {
        return sanitized.take(64)
    }
    return "migrated-skill"
}

private fun uniqueSkillName(baseName: String, usedNames: MutableSet<String>): String {
    var candidate = baseName
    var suffix = 2
    while (usedNames.contains(candidate)) {
        val suffixText = "-$suffix"
        val maxBaseLength = min(64 - suffixText.length, baseName.length)
        val trimmedBase = baseName.take(maxBaseLength).trimEnd('-')
        candidate = "$trimmedBase$suffixText"
        suffix += 1
    }
    usedNames += candidate
    return candidate
}
