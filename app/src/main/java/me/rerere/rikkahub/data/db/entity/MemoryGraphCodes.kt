package me.rerere.rikkahub.data.db.entity

/**
 * Integer code tables for the graph-based human-like memory store (Room v34).
 *
 * These are deliberately plain `Int` constants (not enums) so they can be stored directly in
 * Room columns without a type converter and remain stable across schema evolutions — the same
 * approach the legacy memory entities used. Never renumber an existing value: the numbers are
 * persisted on disk and in exported backups.
 */
object MemNodeType {
    const val ENTITY = 0
    const val FACT = 1
    const val EPISODE = 2
    const val HABIT = 3
    const val FRAME = 4
    const val GOAL = 5
    const val GIST = 6
}

object MemScope {
    const val GLOBAL_USER = 0
    const val CHARACTER = 1
}

object MemSensitivity {
    const val NORMAL = 0
    const val SENSITIVE = 1
}

object MemStatus {
    const val PROVISIONAL = 0
    const val ACTIVE = 1
    const val DORMANT = 2
    const val SUPERSEDED = 3
    const val CLOSED = 4
    const val FORGOTTEN = 5
}

object MemReality {
    const val REAL = 0
    const val FICTION = 1
}

object MemSource {
    const val EXTRACTED = 0
    const val TOOL = 1
    const val MANUAL = 2
    const val IMPORTED = 3
    const val MERGED = 4
    const val DERIVED = 5
    const val CONFIRMED_BY_USER = 6
}

object MemEdgeType {
    const val ABOUT = 0
    const val SUPERSEDES = 1
    const val CONTRADICTS = 2
    const val INSTANCE_OF = 3
    const val DERIVED_FROM = 4
    const val IN_FRAME = 5
    const val RELATES_TO = 6
}

/**
 * Kinds for the append-only [MemoryActivityEntity] feed. Stored as short strings so the UI can
 * render them without a lookup table and new kinds can be added without a migration.
 */
object MemActivityKind {
    const val EXTRACTED = "EXTRACTED"
    const val REINFORCED = "REINFORCED"
    const val UPDATED = "UPDATED"
    const val MERGED = "MERGED"
    const val PROMOTED = "PROMOTED"
    const val PROMOTION_SUGGESTED = "PROMOTION_SUGGESTED"
    const val DECAYED = "DECAYED"
    const val DEMOTED_BRANCH = "DEMOTED_BRANCH"
    const val COMPRESSED = "COMPRESSED"
    const val CLOSED = "CLOSED"
    const val GOAL_OPENED = "GOAL_OPENED"
    const val GOAL_ASKED = "GOAL_ASKED"
    const val GOAL_RESOLVED = "GOAL_RESOLVED"
    const val IMPORTED = "IMPORTED"
    const val WIPED = "WIPED"
    const val MANUAL_ADDED = "MANUAL_ADDED"
}

/** State for suggestion-style activity rows (e.g. scope-promotion chips). */
object MemActivityState {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val DISMISSED = "dismissed"
    const val EXPIRED = "expired"
}

/** Budget ledger categories — every background model call is metered against one of these. */
object MemBudgetCategory {
    const val EXTRACTION = "EXTRACTION"
    const val SLEEP = "SLEEP"
    const val PROFILE = "PROFILE"
    const val CURIOSITY = "CURIOSITY"
}
