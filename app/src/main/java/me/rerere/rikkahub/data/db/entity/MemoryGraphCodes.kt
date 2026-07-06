package me.rerere.rikkahub.data.db.entity

/**
 * Integer code tables for the Memory v2 graph store (Room v34, plan §4).
 *
 * These are deliberately plain `Int`/`String` constants (not enums) so they can be stored directly
 * in Room columns without a type converter and remain stable across schema evolutions — the same
 * approach the legacy [MemoryEntity] used. **Never renumber an existing value**: the numbers are
 * persisted on disk and travel in exported backups.
 *
 * The v2 model is facts-on-edges: nodes ([MemoryNodeEntity]) are entities (short labels); a fact
 * ([MemoryFactEntity]) is a typed `subject —predicate→ object` relationship carrying all belief
 * metadata. See [docs/memory-system-v2-plan.md] §0 for why this replaces attempt 1's sentence-nodes.
 */

/** Which layer a row belongs to. Read-path scope filtering is enforced in SQL (§10). */
object MemScope {
    const val GLOBAL_USER = 0
    const val CHARACTER = 1
}

/** [MemoryNodeEntity.kind] — the entity taxonomy (§4.1). */
object MemEntityKind {
    const val USER_SELF = 0
    const val CHARACTER_SELF = 1
    const val PERSON = 2
    const val PLACE = 3
    const val ORG = 4
    const val ACTIVITY = 5
    const val OBJECT = 6
    const val CONCEPT = 7
    const val EVENT = 8
}

/** [MemoryFactEntity.kind] — the temporal shape of a fact (§4.3, §5.4). */
object MemFactKind {
    const val DURATIVE = 0 // a state that holds over a window ("is vegetarian")
    const val POINT = 1    // a one-time event-fact ("mentioned a headache")
    const val HABIT = 2    // a recurring pattern ("usually studies late at night")
}

/** REAL beliefs vs FICTION (roleplay world-state). FICTION never leaks to GLOBAL_USER (§8.2). */
object MemReality {
    const val REAL = 0
    const val FICTION = 1
}

/**
 * Status lifecycle shared by facts/episodes (§4.3, §6). Entities ([MemoryNodeEntity]) only ever
 * use [ACTIVE]/[DORMANT] — they exist iff referenced and have no PROVISIONAL stage.
 */
object MemStatus {
    const val PROVISIONAL = 0
    const val ACTIVE = 1
    const val DORMANT = 2
    const val SUPERSEDED = 3
    const val CLOSED = 4
    const val FORGOTTEN = 5
}

/** [MemoryFactEntity.sensitivity] — SENSITIVE rows never promote/chip/feed goals (§10). */
object MemSensitivity {
    const val NORMAL = 0
    const val SENSITIVE = 1
}

/** [MemoryFactEntity.source] — provenance of a belief (§4.3). */
object MemSource {
    const val EXTRACTED = 0
    const val TOOL = 1
    const val MANUAL = 2
    const val IMPORTED = 3
    const val MERGED = 4
    const val DERIVED = 5
    const val CONFIRMED_BY_USER = 6
}

/** [MemoryFactLinkEntity.type] — belief-chain relationships between facts (§4.4). */
object MemFactLinkType {
    const val SUPERSEDES = 0
    const val CONTRADICTS = 1
    const val DERIVED_FROM = 2
}

/** [MemoryFrameEntity.source] — how a mode/storyline frame came to exist (§4.7). */
object MemFrameSource {
    const val PROFILE = 0  // produced by the character profile generator (§8.1)
    const val DETECTED = 1 // detected by extraction
}

/** [MemoryProvenanceEntity.rowKind] — which table a provenance row points at (§4.8). */
object MemProvenanceRowKind {
    const val FACT = 0
    const val EPISODE = 1
    const val ENTITY = 2
    const val GOAL = 3
}

/**
 * [MemoryProvenanceEntity.classification] — the extractor's literal/joke/fiction judgment, stored
 * so a misclassification is visible and correctable rather than silent (§8.3). Surfaces as the
 * "Literal" chip in the node sheet.
 */
object MemClassification {
    const val LITERAL = 0
    const val JOKE = 1
    const val HYPOTHETICAL = 2
    const val FICTION = 3
    const val INSTRUCTION = 4
}

/** [MemoryFtsEntity.rowKind] — which table a unified FTS row indexes (§4.9). */
object MemFtsRowKind {
    const val FACT = 0
    const val EPISODE = 1
    const val ENTITY = 2
}

/** [MemoryGoalEntity.state] — curiosity lifecycle (§9). Back-off is structural, not prompt-based. */
object MemGoalState {
    const val OPEN = 0
    const val PRIMED = 1
    const val ASKED = 2
    const val CONFIRMED = 3
    const val DECLINED = 4
    const val IGNORED = 5
    const val ABANDONED = 6
}

/**
 * The promotion whitelist for [MemoryFactEntity.category] (§10). Only these categories can
 * auto-promote to GLOBAL_USER; the applier validates the value, so nothing unlisted can promote
 * regardless of model claims. `null`/[OTHER] never auto-promotes.
 */
object MemCategory {
    const val NAME = "name"
    const val PRONOUNS = "pronouns"
    const val LANGUAGE = "language"
    const val TIMEZONE = "timezone"
    const val OCCUPATION_STUDY = "occupation_study"
    const val OTHER = "other"

    /** Categories eligible for automatic GLOBAL_USER promotion (identity whitelist, §10). */
    val AUTO_PROMOTABLE = setOf(NAME, PRONOUNS, LANGUAGE, TIMEZONE, OCCUPATION_STUDY)
}

/**
 * Kinds for the append-only [MemoryActivityEntity] feed (§4.11). Stored as short strings so the UI
 * can render them without a lookup table and new kinds can be added without a migration.
 */
object MemActivityKind {
    const val EXTRACTED = "EXTRACTED"
    const val REINFORCED = "REINFORCED"
    const val UPDATED = "UPDATED"
    const val MERGED = "MERGED"
    const val PROMOTED = "PROMOTED"
    const val SUGGESTED = "SUGGESTED"
    const val DECAYED = "DECAYED"
    const val COMPRESSED = "COMPRESSED"
    const val CLOSED = "CLOSED"
    const val FORGOTTEN = "FORGOTTEN"
    const val EVICTED = "EVICTED"
    const val GOAL_OPENED = "GOAL_OPENED"
    const val GOAL_ASKED = "GOAL_ASKED"
    const val GOAL_RESOLVED = "GOAL_RESOLVED"
    const val IMPORTED = "IMPORTED"
    const val WIPED = "WIPED"
    const val MANUAL_ADDED = "MANUAL_ADDED"
    const val EMBEDDED = "EMBEDDED"
}

/** State for suggestion-style [MemoryActivityEntity] rows (e.g. scope-promotion chips, §10). */
object MemActivityState {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val DISMISSED = "dismissed"
    const val EXPIRED = "expired"
}

/** [MemoryBudgetLedgerEntity.category] — every background model call is metered against one (§12). */
object MemBudgetCategory {
    const val EXTRACTION = "EXTRACTION"
    const val SLEEP = "SLEEP"
    const val PROFILE = "PROFILE"
    const val CURIOSITY = "CURIOSITY"
}

/** Well-known [MemoryStoreMetaEntity] keys (§4.11). Free-form key/value otherwise. */
object MemStoreMetaKeys {
    const val STORE_SCHEMA_VERSION = "store_schema_version"
    const val LAST_SLEEP_RUN = "last_sleep_run"
    const val EMBEDDING_BACKFILL_WATERMARK = "embedding_backfill_watermark"

    /** Per-assistant import watermark; append the assistant id. */
    const val IMPORT_WATERMARK_PREFIX = "import_watermark:"

    /** Per-assistant character-profile hash; append the assistant id. */
    const val PROFILE_HASH_PREFIX = "profile_hash:"
}

/**
 * The current semantic version of the memory store, persisted under
 * [MemStoreMetaKeys.STORE_SCHEMA_VERSION] for future semantic migrations independent of the Room
 * schema version (§4.11, brief failure #5).
 */
const val MEMORY_STORE_SCHEMA_VERSION = 1
