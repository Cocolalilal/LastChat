package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

/** A default applies to current and future characters; individual toggles record exceptions. */
fun isCharacterAssigned(
    defaultEnabled: Boolean,
    includedIds: Set<Uuid>,
    excludedIds: Set<Uuid>,
    assistantId: Uuid,
): Boolean = if (defaultEnabled) assistantId !in excludedIds else assistantId in includedIds
