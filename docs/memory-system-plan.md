# LastChat Human-Like Memory System — Complete Plan

Status: **P1 + Room v34 migration + P2 + P3 (sleep pass) implemented** (behind the master toggle
`Settings.memory.enabled`, default OFF). P4 (UI), P5 (profiles/curiosity), and P6 (hardening) are
not yet built. Supersedes all prior memory designs, including the unmerged `MemoryItemEntity`
two-layer store (deleted; its good ideas are absorbed here).

### Implementation status / deviations from this plan
- **Store & migration (P1)**: `memory_node/edge/provenance/fts/activity/budget_ledger/store_meta`
  tables live in Room v34 via `AutoMigration(33→34)` (schema `34.json` committed). The
  encoding watermark lives in a dedicated `memory_conversation_state` table instead of a
  `Conversation.extracted_up_to_index` column — this keeps it off the high-frequency
  conversation-persist path (which rebuilds the whole entity every save and would clobber a
  column) and keeps the memory system self-contained. FTS is a standalone manually-synced FTS4
  table (no Room triggers), as the plan intends.
- **§7.2/§7.3 hardening (applied after the initial P1/P2 build)**: the watermark is anchored on a
  **message id** (`extracted_up_to_message_id`), not a list index — re-resolved against the current
  branch, falling back to the nearest surviving earlier processed message when the anchor is gone
  (`MemoryWatermark`); this was an in-place v34 column change (no v35). Branch/regenerate
  abandonment demotes nodes whose evidence is entirely on the abandoned branch to DORMANT, keeping
  facts still evidenced elsewhere and never demoting on plain deletion (`MemoryBranchLogic`,
  reconciled at encode start); REINFORCE reactivates DORMANT. All memory writes serialize through a
  per-scope mutex (`MemoryScopeLocks`) so a future sleep pass can't interleave with extraction.
- **Applier / dedup gate (P1)**: `MemoryOpApplier` is the sole writer with the lexical dedup gate,
  entity-hub resolution, provenance/activity/FTS sync, and all guardrails. Vector-assisted dedup,
  inline embedding of new nodes, and scope promotion are deferred (FTS-only path works today).
- **Encode + recall (P2)**: `MemoryEncoder` (+ `MemoryExtractionWorker`, triggered post-persist
  from `ChatService`) and `MemoryRecall` (injected via `MemoryRecallTransformer`, replacing the
  legacy per-message injection when the toggle is on). Budget ledger + presets enforced. Character
  profiles/frames, curiosity, and the retrieval access-time bump are deferred to later phases.
- **Sleep pass (P3)**: `MemorySleepPass` (Koin single) + `MemorySleepWorker` implement §5.4's staged
  pipeline. Deterministic stages — decay/promotion (retention `R = importance·w1 + log(1+reinforced)·w2
  + log(1+retrieved)·w3 − ageDecay(last_accessed)`; ACTIVE→DORMANT→FORGOTTEN with 30-day grace →
  hard-delete; PROVISIONAL→ACTIVE auto-promote for importance≥3 after 7 quiet days gated on no
  CONTRADICTS/supersession/adjudication flag), expiry (`valid_until`/horizon → CLOSED), and size
  enforcement (§9: per-scope non-entity budgets ~1,500 char / ~1,000 global, lowest-retention DORMANT
  eviction, provenance first+last-3 cap, activity ~500/scope, FORGOTTEN-past-grace purge) — live in
  the pure, unit-tested `MemorySleepLogic` and run under the per-scope `MemoryScopeLocks` with **no
  network/budget dependency**. Model-assisted stages — identity adjudication + contradiction (one
  batched call: merge/supersede/coexist, deterministic MERGE field-combination, MANUAL/CONFIRMED
  never merged-away or auto-superseded) and episode compression → GIST + habit induction (one batched
  call, toggleable via `habitInduction`) — draw from `MemoryBudget` (category SLEEP), make their model
  call *outside* the scope lock and re-validate under it, and simply skip when the budget is exhausted
  or the call fails (never block, never error the app). Everything writes a `memory_activity` row. The
  worker self-schedules ~12h (battery-not-low) and is triggered opportunistically by the extraction
  worker when the adjudication backlog exceeds a threshold. §6.4 goal generation and §6.5 scope-
  promotion review are left as clean TODO(P5) hooks in the pipeline.
- The legacy `MemoryConsolidationWorker` / `MemoryEntity` / `ChatEpisodeEntity` remain read-only and
  are imported once into the graph store when the toggle is first enabled. The legacy worker's
  periodic scheduling is now **retired for memory-enabled users** (`LastChatApp` schedules exactly one
  of `memory_sleep` or `memory_consolidation` based on `memory.enabled`); the class + its manual-
  trigger UI paths stay until P4 removes the old sub-pages.

---

## 0. Design philosophy

Three principles, from which everything else follows:

1. **Append-only beliefs, computed forgetting.** Nothing is ever edited in place or
   silently deleted by a model. Models can only *propose* operations; deterministic code
   applies them under guardrails. Forgetting is a scheduled, explainable, reversible-until-grace
   process — never a side effect of an update.
2. **The graph is the dedup mechanism, not just the display.** Every fact/event attaches to
   canonical *entity* nodes ("user", "user's school", "Mom"). New information must pass through
   the neighborhood of its entities before it may become a new node. Duplicates fail
   structurally, not because a prompt said "avoid duplicates".
3. **Model calls are a metered resource.** Every background model call draws from an explicit
   budget ledger. The system is designed to be *good when the budget is zero* (deterministic
   retrieval, FTS, decay) and *better when budget exists* (extraction, consolidation, curiosity).

Human-memory analogy used throughout (loosely, where it helps):

| Human | This system |
|---|---|
| Working memory | The live chat context window (no memory machinery involved) |
| Encoding | Extraction pass over new messages → provisional nodes |
| Consolidation during sleep | Periodic "sleep pass": merge, verify, compress, induce habits |
| Semantic memory | FACT nodes attached to ENTITY hubs |
| Episodic memory | EPISODE nodes with time anchors and frames |
| Gist retention | Old episode clusters compressed into summary nodes; details forgotten |
| Retrieval strengthening | `timesRetrieved` boosts salience; unused memories decay |
| Curiosity | GOAL nodes → at most one gentle in-character question, heavily rate-limited |

---

## 1. Failure modes → structural counters

Every §2 failure from the brief, and the mechanism that makes it *impossible by construction*
rather than mitigated:

| Past failure | Structural counter |
|---|---|
| 1. Duplicates | Entity-hub write gate: an ADD must first be matched against the 1-hop neighborhood of its entities. Restatements (no new information) become REINFORCE; anything similar-but-different is inserted and *flagged for sleep-pass adjudication* — similarity is never treated as identity, so corrections can't be silently absorbed. There is no code path that inserts a fact without passing the gate. |
| 2. Incorrect extractions | New nodes are born `PROVISIONAL` with a confidence score. `ACTIVE` nodes are injected plainly; high-importance PROVISIONALs are injected *hedged* ("you recall, though mentioned only once") so a fact stated once is never invisible. Promotion to ACTIVE via reinforcement, or automatically after 7 days without contradiction — never on self-reported model confidence alone. Every node carries provenance (message excerpt + one-line rationale), so wrong extractions are visible and correctable. Extraction classifies literal/joke/hypothetical/fiction using the character profile and window context. |
| 3. Trivia stored forever | Salience decay is universal: every non-pinned node has a computed retention score; low scores → DORMANT → FORGOTTEN (grace) → deleted. Importance is capped at extraction time (max ADDs per pass, importance rubric in prompt, applier clamps). "Permanent" is something a memory *earns* through reinforcement, not something extraction can grant. |
| 4. Destructive overwrites | Updates create a *new* node plus a `SUPERSEDES` edge; the old node stays readable (status SUPERSEDED) with its full chain. Endings are recorded by closing a validity interval (`validUntil`), not by deleting. There is no UPDATE-in-place operation in the op vocabulary. |
| 5. Migration pain | Old rows import as first-class nodes (`source=IMPORTED`) through the same applier; old tables stay read-only one release; imports are then refined by the ordinary sleep machinery — no special one-shot "migration quality" problem. Store carries its own `schema_version` for future evolutions. |
| 6. Embedding dependency | FTS is the primary index; vectors are an *accelerator*. Every retrieval and dedup path has an FTS/lexical branch. Embeddings are stored per-node with `embedding_model_id`; a model switch triggers background re-embed while FTS covers the gap. Zero-config users get a fully working system. |
| 7. Time misunderstanding | First-class bitemporal model (§4): when it happened vs. when we learned it vs. how long it's valid, plus fuzzy verbalization at injection. Events can have start/end; facts have validity intervals. |
| 8. No coherent vision | This document: one pipeline, one op vocabulary, one budget, one UI, one degradation matrix, one migration story. |

Additional brief requirements mapped: §3.3 zero burden → no user-facing maintenance
states exist at all (no red dots; the sleep pass is self-scheduling). §3.8 async
consistency → §7.4. §3.11 bounded growth/cost → §8, §9.

---

## 2. Architecture overview

All in `:app` (memory is app-level orchestration, like `GenerationHandler`).
New package: `me.rerere.rikkahub.data.memory/`.

```
                       ┌──────────────────────────────────────────────┐
 chat messages ──────► │ MemoryEncoder (extraction)                   │──► ops
                       │  trigger: watermark ≥ N msgs / conv idle /   │
                       │  conv switch / app background                │
                       └──────────────────────────────────────────────┘
                                                                        │
 ┌────────────────────┐    ┌───────────────────────────┐    ┌──────────▼──────────┐
 │ MemorySleepWorker  │──► │  MemoryOpApplier          │◄───│ (ops from anywhere) │
 │ merge/compress/    │    │  guardrails + dedup gate  │    └─────────────────────┘
 │ decay/habits/goals │    │  the ONLY writer          │
 └────────────────────┘    └────────────┬──────────────┘
                                        ▼
                        ┌────────────────────────────────┐
                        │ Graph store (Room v34)         │
                        │ nodes / edges / provenance /   │
                        │ FTS / ledger / activity        │
                        └────────────┬───────────────────┘
                                     ▼
 ┌──────────────────────────────────────────────────────────────────┐
 │ MemoryRecall (retrieval, ZERO model calls)                       │
 │  core sheet (cached) + query recall (FTS+vector+1-hop) + recent  │──► system prompt
 │  episode strip + pending-tail digest                             │
 └──────────────────────────────────────────────────────────────────┘
```

Components (all Koin singles unless noted):

- **`MemoryGraphRepository`** — DAO façade; all reads. Owns the cached "core sheet" per scope.
- **`MemoryOpApplier`** — the *only* code that writes nodes/edges. Validates ids, forces
  scope/sensitivity, runs the dedup gate, clamps caps, writes provenance + activity rows.
- **`MemoryEncoder`** — builds the extraction prompt (window + entity-neighborhood digest +
  character profile), parses structured ops. Called from `ChatService` triggers via
  WorkManager (expedited one-shot) — never on the generation hot path.
- **`MemorySleepWorker`** — periodic WorkManager worker (self-throttled): merge confirmation,
  episode→gist compression, habit induction, decay/expiry sweep, goal generation, budget-aware.
  Replaces `MemoryConsolidationWorker`.
- **`MemoryRecall`** — synchronous, deterministic retrieval used by `ChatService` before
  generation; also backs the `memory_search` tool (which may additionally use the existing
  subagent summarizer, since tool calls are user-initiated cost).
- **`CharacterMemoryProfile` generator** — one model call per character, keyed by hash of the
  system prompt; produces frames/modes, persona relation, "what this character cares about".
- **`CuriosityEngine`** — goal lifecycle + ask-delivery gating (§6.4).
- **`MemoryBudget`** — ledger + admission control for every background model call.

---

## 3. Data model (Room v34)

Node ids are `Uuid` strings (stable across export/import; no autoincrement coupling).

### 3.1 `memory_node`

| column | notes |
|---|---|
| `id` TEXT PK | Uuid |
| `type` INT | ENTITY, FACT, EPISODE, HABIT, FRAME, GOAL, GIST |
| `scope` INT | GLOBAL_USER, CHARACTER |
| `owner_assistant_id` TEXT? | null iff GLOBAL_USER |
| `content` TEXT | canonical statement ("User studies CS at TU Wien") |
| `display_label` TEXT? | short label for graph UI (entities: "TU Wien") |
| `importance` INT 1..5 | extraction-proposed, applier-clamped |
| `confidence` REAL 0..1 | |
| `sensitivity` INT | NORMAL, SENSITIVE (never promotable to GLOBAL, never in goal/web-search paths) |
| `status` INT | PROVISIONAL, ACTIVE, DORMANT, SUPERSEDED, CLOSED, FORGOTTEN |
| `pinned` INT | user-set only; exempt from decay/compression |
| `reality` INT | REAL, FICTION (in-roleplay world state / events) |
| `event_start` INT?, `event_end` INT? | EPISODE/GIST: when it happened (end null = point event) |
| `valid_from` INT?, `valid_until` INT? | FACT: validity interval; `valid_until` set = the fact *ended* (CLOSED), not wrong |
| `recorded_at` INT | when the system learned it |
| `last_confirmed_at`, `last_accessed_at` INT | |
| `times_reinforced`, `times_retrieved` INT | |
| `source` INT | EXTRACTED, TOOL, MANUAL, IMPORTED, MERGED, DERIVED, CONFIRMED_BY_USER |
| `embedding_blob` BLOB?, `embedding_model_id` TEXT? | VectorUtils format |
| `extra` TEXT JSON | type-specific payload (GOAL state, HABIT cadence, FRAME descriptor, ENTITY `aliases` list — e.g. "uni", "die Uni" for "TU Wien"; FACT `category` for promotion whitelisting) |

Indices: `(scope, status)`, `(owner_assistant_id, status, type)`, `(type, status)`,
`(embedding_model_id)`.

### 3.2 `memory_edge`

`id`, `from_id`, `to_id`, `type`, `weight REAL`, `created_at`, `extra JSON`.
Edge types:

- `ABOUT` — fact/episode/habit → entity (the dedup backbone; every non-entity node has ≥1)
- `SUPERSEDES` — new belief → old belief
- `CONTRADICTS` — flagged by extraction, resolved by sleep pass (one side gets superseded/closed, or both stay with a scoping note)
- `INSTANCE_OF` — episode → habit (evidence for an induced pattern)
- `DERIVED_FROM` — gist → compressed episodes; inferred fact → its evidence
- `IN_FRAME` — episode/fact → frame node (roleplay mode, chat mode, storyline)
- `RELATES_TO` — generic weighted association (co-mention), feeds 1-hop expansion

Indices on `from_id`, `to_id`, `(type, from_id)`. Deleting a node cascades its edges.

### 3.3 Supporting tables

- **`memory_provenance`** — `node_id`, `conversation_id?`, `message_ids JSON`, `excerpt`
  (≤300 chars of source text), `rationale` (model's one-liner: *why this was saved*),
  `created_at`. One row per originating pass (REINFORCE appends another row). This is the
  entire "why does this exist" story in the UI.
- **`memory_node_fts`** — FTS4 over `content` + `display_label` (project already uses FTS4).
  Kept in sync by the applier (no triggers-on-Room surprises).
- **`memory_activity`** — append-only feed for the UI pill/timeline: `at`, `kind`
  (EXTRACTED, MERGED, PROMOTED, PROMOTION_SUGGESTED, DECAYED, COMPRESSED, GOAL_*, IMPORTED,
  WIPED), `summary`, `node_ids JSON`, `conversation_id?`, `state?` (for suggestion rows:
  pending/accepted/dismissed/expired). Pruned to last ~500 rows per scope.
- **`memory_budget_ledger`** — `day`, `category` (EXTRACTION, SLEEP, PROFILE, CURIOSITY),
  `calls`, `tokens_in`, `tokens_out`. §8.
- **`memory_store_meta`** — key/value: `store_schema_version`, import watermarks, last sleep
  run, per-assistant profile hash.

Per-conversation watermark: reuse the existing `Conversation.extractedUpToIndex` pattern
(add column if the reverted one isn't present) — index into `currentMessages` up to which
extraction has run.

### 3.4 Kotlin model layer

`data/model/MemoryGraph.kt`: sealed `MemoryNode` (Entity/Fact/Episode/Habit/Frame/Goal/Gist
subtypes deserializing `extra`), `MemoryEdge`, `MemoryOp` (§5.2), mappers. All JSON via
`JsonInstant`.

---

## 4. Time model

Bitemporal + fuzzy verbalization; this is what gives characters a real sense of time.

1. **Event time** (`event_start`/`event_end`) — when it happened in the world. For FICTION
   episodes this is still wall-clock ("we roleplayed X last week" — the *session* time; any
   in-fiction chronology lives in the episode content itself).
2. **Belief time** (`recorded_at`, `last_confirmed_at`) — when we learned/reconfirmed it.
3. **Validity** (`valid_from`/`valid_until`) — for facts that are states: "is preparing for
   exams" gets a validity window; extraction may CLOSE it when the user says exams ended, or
   the sleep pass expires it past a proposed horizon. A closed fact is history, not garbage:
   "used to live in Graz" remains retrievable and is verbalized in past tense.
4. **Verbalization at injection** — never raw timestamps. Reuse/extend the existing
   `fuzzyMemoryAgeLabel` (`:ai` util): "earlier today", "a few days ago", "about a week ago",
   "a couple of months ago", "last summer". Closed facts render as "used to …". Frames prefix
   episodes: "*[roleplay, about a week ago]* you two played at learning together".
5. **Extraction prompt receives** the current date/time and per-message timestamps, and must
   output relative anchors it resolved ("next Friday" → absolute epoch). The applier stores
   absolutes only.
6. **Toggle** (`timeAwareness=false`): validity/decay still work internally (bounded growth
   is not optional), but injection drops all age phrasing and tense conversion.

---

## 5. Lifecycle

### 5.1 Encoding (extraction)

**Triggers** (all funnel into one WorkManager one-shot, deduped by conversation; only the
flush-on-switch trigger is expedited, with `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`
so Android's expedited quota can't silently demote the pipeline):
- watermark lag ≥ 10 messages (Balanced preset; §8)
- conversation switched away / app backgrounded with lag ≥ 2 messages
- 15 min conversation idle with lag ≥ 2
- immediately before a spontaneous/scheduled message for that assistant (so proactive
  messages never act on stale memory)

**One model call per pass.** Input:
- the unprocessed window (watermark → end), with roles + timestamps, ±2 messages of processed
  context for antecedent resolution
- the character memory profile (frames, persona relation, care-abouts)
- a *neighborhood digest*: for entities/keywords detected in the window (cheap lexical pass +
  FTS), the current beliefs (id + content + status) — this is what lets the model say
  REINFORCE/UPDATE/CLOSE instead of blindly ADDing
- current date/time; the user's display name

Output: strict JSON list of ops (§5.2). Structured-output/JSON-mode where the provider
supports it; tolerant parsing with `jsonObjectOrNull` helpers otherwise.

**Watermark integrity rule** (hard invariant): the per-call token cap applies only to the
*context* portions of the prompt (neighborhood digest, surrounding processed messages) —
never to the extraction window itself. If the unprocessed window exceeds the cap (e.g. after
budget deferral), it is split into sequential oldest-first chunks; each pass processes one
chunk and advances the watermark exactly to that chunk's end. The watermark never moves past
a message that no extraction pass has read, so deferral delays memory but can never lose it.

Prompt contract highlights (the *judgment* layer):
- classify each candidate: `literal | joke | hypothetical | fiction | instruction-to-character`.
  Jokes/hypotheticals are dropped unless they reveal a durable style trait (then they become a
  low-importance HABIT candidate: "often deadpans about hating Mondays"). Fiction → EPISODE
  with `reality=FICTION`, `IN_FRAME` the roleplay frame; **never** a GLOBAL_USER fact.
- importance rubric (5 = identity-level: name, family, health; 1 = ambient trivia). The model
  is told trivia should usually produce *no* op.
- mark `sensitivity=SENSITIVE` for secrets, health, sexuality, finances, anything confided.
- tag user-facts with a `category` (name, pronouns, language, timezone, occupation_study,
  other) — consumed by the promotion whitelist (§6.5).
- propose `aliases` when creating/mentioning entities ("uni" → "TU Wien") — consumed by
  zero-config recall (§5.5).
- propose validity horizons for state-facts ("on vacation" → ~2 weeks unless dated).
- max 6 ADDs per pass (applier also enforces).

**Applier guardrails** (deterministic, in `MemoryOpApplier`):
- drop ops referencing unknown ids (anti-hallucination)
- force scope: an op may never write GLOBAL_USER unless the fact is about the user *and*
  sensitivity is NORMAL; sleep pass handles promotion (§6.5), extraction never does
- dedup gate (§5.3) may downgrade ADD→REINFORCE
- new non-entity nodes require ≥1 `ABOUT` edge; the applier resolves/creates entity hubs
  (case-insensitive label match within scope, then FTS, then vector)
- alias hygiene: a proposed alias that exactly matches another in-scope entity's canonical
  label is dropped; the same alias *may* legitimately map to multiple entities ("school"),
  because alias expansion only generates ranked candidates (§5.5), never rewrites
- everything lands `PROVISIONAL` — no extraction confidence value can birth a node ACTIVE
  directly (self-reported confidence is poorly calibrated on small models); hedged injection
  (§5.5) plus the 7-day sweep (§5.4) cover visibility, so the fast path costs nothing
- write provenance + one `memory_activity` row summarizing the pass
- advance the watermark only on success

Embeddings for new nodes are computed inline if an embedding model is configured (single
batched call via existing `EmbeddingService`), else skipped — FTS row is always written.

### 5.2 Op vocabulary (complete, closed set)

```
ADD_NODE   {type, scope?, content, entities[], aliases?, category?, importance, confidence,
            sensitivity, reality, frame?, event_start?, event_end?, valid_from?, valid_until?}
ADD_EDGE   {from, to, type}                    // RELATES_TO / CONTRADICTS only, from extraction
REINFORCE  {id, note?}                         // bumps times_reinforced, last_confirmed_at,
                                               // may promote PROVISIONAL→ACTIVE
UPDATE     {old_id, content, ...}              // = ADD_NODE + SUPERSEDES edge; old→SUPERSEDED
CLOSE      {id, ended_at?}                     // sets valid_until / event_end; status CLOSED
OPEN_GOAL  {question, entities[], value_note}  // curiosity; sleep-pass & extraction may propose
RESOLVE_GOAL {id, outcome, learned?}           // when the chat answered a pending goal
```

No DELETE. No in-place edit. Sleep-pass-only ops (never from extraction): `MERGE`,
`COMPRESS`, `PROMOTE_SCOPE`, `DECAY` — generated by code, with a model call only where noted.

### 5.3 Dedup gate

Core rule: **similarity is not identity**. A near-duplicate is disproportionately often an
*update or contradiction* ("studies CS at TU Wien" arriving over "studies math at TU Wien"),
so high similarity must never silently collapse into reinforcement of the old belief — that
would be failure mode #4 reborn inside the mechanism meant to fix #1.

For each ADD candidate:
1. Resolve entity hubs; collect 1-hop non-entity neighbors of the same type + scope.
   Embeddings (when available and same-model) act only as a **candidate generator** to widen
   this set — vector similarity never decides a gate outcome by itself, since cosine
   distributions vary wildly across user-supplied embedding models. Lexical/deterministic
   checks are authoritative.
2. **REINFORCE only when the candidate adds no information**: normalized exact match, or the
   candidate's content-token set is a subset of the existing node's (pure restatement).
   Exception: if the matched node was superseded by a MANUAL edit, the reinforcement routes to
   the *superseding* node's chain — extraction can never resurrect a belief the user
   corrected away.
3. **Similar but different** (shared entities + high lexical overlap, but new/changed content
   tokens) → insert as a new node, add a `RELATES_TO` edge, and flag the pair for sleep-pass
   adjudication (§5.4 stage 3), which decides merge / supersede / coexist with a model call.
   Nothing is absorbed at write time.
4. Semantic contradiction heuristics (same entity + same predicate class, e.g. two different
   home cities) → insert with `CONTRADICTS` edge; sleep pass resolves (usually: newer
   SUPERSEDES older, or older gets CLOSED with `valid_until`).

(Optional later enhancement: per-embedding-model percentile calibration so vectors can carry
more weight as candidate generators; never a correctness dependency.)

### 5.4 Sleep pass (`MemorySleepWorker`)

Periodic (WorkManager, ~every 12h, constraints: battery-not-low; runs opportunistically
sooner if merge/contradiction backlog > threshold). Deterministic stages first, model-assisted
stages only within budget:

1. **Decay + promotion sweep** (no model): retention score
   `R = importance·w1 + log(1+reinforced)·w2 + log(1+retrieved)·w3 − ageDecay(last_accessed)`
   — where `times_retrieved`/`last_accessed_at` count only *genuine* recall (query-relevant
   hits, `memory_search` hits), never core-sheet inclusion (§5.5), so always-injected items
   can't self-perpetuate. Thresholds move ACTIVE→DORMANT (still searchable, not injected) and
   DORMANT→FORGOTTEN (30-day grace, visible under the Browse "Recently forgotten" filter with
   one-tap restore) → hard delete. Pinned and ENTITY nodes exempt (entities die only when
   orphaned). The same sweep **auto-promotes** PROVISIONAL→ACTIVE for importance≥3 nodes that
   survived 7 days without a CONTRADICTS edge, supersession, **or a pending adjudication flag
   (§5.3 step 3)** — so under a starved budget, both halves of an unadjudicated pair wait for
   stage 3 rather than promoting; a fact stated plainly once still becomes a real memory
   without needing re-mention.
2. **Expiry** (no model): `valid_until`/proposed horizons past due → CLOSED.
3. **Identity adjudication** (1 batched model call): pairs flagged by the dedup gate (§5.3
   step 3) → decide **merge** (same belief restated), **supersede** (newer corrects older),
   or **coexist** (genuinely distinct; unflag). MERGE semantics are deterministic: new MERGED
   node with `pinned = either`, `times_reinforced = sum`, `importance/confidence = max`,
   `recorded_at = earliest`, `last_confirmed_at = latest`, provenance union; both sources
   SUPERSEDED into it. Nodes with `source = MANUAL/CONFIRMED_BY_USER` are **never**
   merged-away or superseded by automatic passes — conflicts against them stay as
   CONTRADICTS edges surfaced in the activity feed for the user to settle.
4. **Contradiction resolution** (same batched call): decide supersede/close/coexist-with-scope
   ("likes coffee at work, tea at home" — both survive, contents refined). Same MANUAL
   protection applies.
5. **Episode compression** (1 model call, only under size pressure or age): clusters of old
   low-importance episodes (same frame + entity + era) → one GIST node with `DERIVED_FROM`
   edges; originals FORGOTTEN after grace. Human gist retention.
6. **Habit induction** (same call as 5 when budget-tight): ≥3 similar episodes/REINFORCEs →
   HABIT node ("usually studies late at night", `INSTANCE_OF` edges). Toggleable.
7. **Scope promotion review** (§6.5) and **goal generation** (§6.4) — strictly budget-gated.
8. **Size enforcement** (§9) — runs regardless of budget.

Everything the sleep pass does lands in `memory_activity`. There is *no* user-facing "needs
consolidation" state: the worker self-schedules; a failed run just retries.

### 5.5 Retrieval & injection (`MemoryRecall`) — zero model calls

Assembled synchronously in `ChatService` before generation (replaces the current
`retrieveRelevantMemories` block around `ChatService.kt:1488`):

1. **Core sheet** (cached; invalidated only by applier *content* writes — node/edge/status
   changes — never by batched access-time bumps, or the cache would never live): pinned +
   top-K by core-sheet rank (importance, reinforcement, confirmation-recency — deliberately
   *not* `times_retrieved`) among GLOBAL_USER facts + this character's CHARACTER facts +
   active habits + open relationship context. High-importance (≥4) PROVISIONALs are included
   with hedged phrasing ("you recall, though it was mentioned only once") so single-mention
   facts are never invisible while they await promotion. Rendered compactly (~400–700
   tokens), grouped, with tense/fuzzy-time applied.
2. **Query-relevant recall**: last user message(s) → tokens expanded through the ENTITY
   **alias tables** ("uni" → "TU Wien"; learned per-user, replacing the old hardcoded
   expansion maps). Expansion is additive candidate generation — expanded terms join the
   original tokens with lower rank weight, never replace them, so an ambiguous alias
   ("school" claimed by two entities) degrades to ranking, not misdirection → FTS query +
   vector query (if available) → seed nodes → 1-hop graph
   expansion (weighted `RELATES_TO`/`ABOUT`) → rank by (match score × retention × recency) →
   top-N episodes/facts not already in the core sheet.
3. **Recent episode strip**: last ~3 episodes with this character, fuzzy-aged, frame-labeled —
   the "sense of shared history" ("*[roleplay, about a week ago]* …").
4. **Pending-tail digest** (§7.4) for async consistency.
5. **Curiosity hint** if and only if `CuriosityEngine` clears delivery (§6.4).

Global injection rule: nodes carrying an unresolved adjudication flag or `CONTRADICTS` edge
inject **only the newest of the pair** — the character must never voice both sides of a
pending correction ("you recall they study math… you also recall CS") while the sleep pass
catches up.

Injected as one system-prompt section with clear sourcing ("Things you remember — fuzzy,
don't recite verbatim"). Only nodes surfaced by **query-relevant recall** (and `memory_search`
hits) get `times_retrieved`+1 and `last_accessed_at` (batched write, off the hot path) —
core-sheet inclusion is injection, not retrieval, and counts for nothing; otherwise the early
core sheet fossilizes via a rich-get-richer loop.

The existing `memory_search` **tool** stays (deliberate recall; may use the subagent
summarizer as today since the user initiated the turn) but is rebuilt over the graph store,
replacing the hardcoded expansion tables in `MemorySearchService` with FTS+vector+graph.

---

## 6. Character awareness, curiosity, privacy

### 6.1 Character memory profile

Generated by one model call per character, cached in `memory_store_meta` keyed by
`hash(systemPrompt + name)`; regenerated only when the hash changes (budget category
PROFILE). Produces: detected **frames/modes** (→ FRAME nodes: "roleplay mode", "study-buddy
chat", storylines), the character's relation to the user, topics this character plausibly
cares about (guides extraction importance), and tone notes for the curiosity ask. Characters
without mode structure get a single default frame — the system degrades to frameless
gracefully.

### 6.2 Frames

Episodes (and character-scoped facts where relevant) carry `IN_FRAME` edges. Retrieval labels
them; the graph UI clusters by them. Fiction world-state (`reality=FICTION`) lets a roleplay
remember its own continuity ("in our story, we live in a lighthouse") without ever leaking
into real user facts.

### 6.3 Seriousness / joke / play detection

Handled at extraction (§5.1) — the profile plus surrounding context is what makes "I'm going
to *die* if this exam goes badly" a joke and "my grandmother died" a fact. Low-confidence
classifications land PROVISIONAL and only survive if reinforced. The user never has to change
how they talk.

### 6.4 Curiosity engine (proactive knowledge building) — default OFF

GOAL node lifecycle: `OPEN → PRIMED → ASKED → CONFIRMED | DECLINED | IGNORED → COOLDOWN | ABANDONED`
(state in `extra`).

- **Generation**: sleep pass only, ≤1 new goal per character per run, only from importance≥3
  NORMAL-sensitivity beliefs, with a `value_note` ("knowing the semester schedule would help
  the study-buddy framing"). Extraction may also RESOLVE_GOAL when a chat organically answers one.
- **Optional web fill** (child toggle, off by default): the sleep pass may run one search via
  the existing `:search` providers to draft the answer ("Vienna school holidays start …"),
  stored on the goal as *unconfirmed*.
- **Delivery**: `MemoryRecall` injects at most one short hint — "If it fits naturally, you're
  curious whether …; drop it if the moment is wrong" — only when ALL hold: toggle on; goal
  PRIMED; conversation ≥6 messages and not mid-roleplay-scene (frame check); ≥72h since *any*
  ask by this character; this goal never asked before.
- **Back-off is structural**: DECLINED → ABANDONED forever (node kept as a "do not ask" marker
  so it can't be regenerated — the generator excludes entities of abandoned goals). IGNORED
  twice → ABANDONED. CONFIRMED → learned facts enter through the normal applier with
  `source=CONFIRMED_BY_USER` and high confidence. Global cap: ≤2 asks per character per week,
  enforced in code, not prompt.

### 6.5 Privacy boundaries

- Scope is enforced in **SQL**, not prompts: every read path takes `(scope=GLOBAL_USER) OR
  (scope=CHARACTER AND owner=thisAssistant)`. Character A structurally cannot query B's nodes.
- Extraction writes user-facts as CHARACTER-scoped by default. **Promotion** to GLOBAL_USER
  is conservative by construction, because it ultimately rests on a cheap model's labels and
  one misclassified secret would be exactly the leak this section forbids:
  - **Automatic** only for ACTIVE, `sensitivity=NORMAL` facts whose extraction `category` is
    on a closed identity-level whitelist: name, pronouns, language, timezone,
    occupation/study. The applier validates the category; anything unlisted cannot
    auto-promote regardless of what the model claims.
  - **Everything else** → an optional one-tap suggestion chip in the activity feed ("Share
    'user studies CS' with all characters?"). Tap = promote; dismiss or ignore = stays
    private. Non-action is always the safe state, so this adds zero required burden.
  - SENSITIVE never promotes, never chips, never feeds goals or web searches.
- `Assistant.useSharedUserMemory=false` (existing concept) opts a character out of the global
  layer entirely, both read and write.
- **Character deletion cascades**: deleting an assistant deletes its CHARACTER-scoped nodes,
  edges, provenance, goals, and activity rows — users rightly assume deleting a character
  deletes what it knew. GLOBAL_USER facts survive regardless of which character sourced them.
  The existing delete-assistant confirmation dialog states this.
- Export/wipe (§10.4) honors the same boundaries (per-character export excludes other
  characters; global wipe requires the strong confirmation).

---

## 7. Chat integration

### 7.1 Hot path budget
Pre-generation retrieval is local-DB-only: target <50ms (core sheet cached; FTS + one vector
scan + 1-hop expansion over indexed edges). No model calls, ever, on this path.

### 7.2 Triggers
`ChatService` (post-`saveConversation`) checks watermark lag and enqueues extraction as
described in §5.1. Generation is never awaited on; extraction failures never surface as chat
errors (activity row + `PlatformLog` only).

Operational hardening (normative for implementation):
- **Never trigger off streaming checkpoints.** The 1s streaming checkpoint saves must not
  count toward watermark lag; triggers evaluate only after `generationDone` (or on
  user-message save when no generation is running), so extraction never reads a
  half-generated assistant message. The watermark may never sit inside a message that is
  still streaming.
- **Single writer per scope.** Extraction and sleep passes serialize through unique
  WorkManager work (`memory-extract-<conversationId>` with `ExistingWorkPolicy.KEEP`;
  `memory-sleep` unique periodic) plus an app-level per-scope mutex around
  `MemoryOpApplier`, so a sleep-pass merge can never race an in-flight extraction write.
- **Atomic apply.** Ops, provenance, activity row, and the watermark advance commit in one
  Room transaction — a crash mid-apply leaves the watermark unmoved and the pass re-runs
  idempotently (the dedup gate absorbs the replay).
- **Worker constraints.** Extraction/sleep workers require `NetworkType.CONNECTED` (model
  call) and use exponential backoff; the deterministic sleep stages (decay, expiry, size
  enforcement) run in a separate no-network pass so an offline device still ages its graph.
  All of this is delay-safe by construction: reboot, Doze, or OEM task-killers only postpone
  the watermark, never lose content.

### 7.3 Streaming/regenerate/branches
Extraction reads `currentMessages` (selected branch) only; watermark is per-conversation
against the selected path. On branch switch/regenerate below the watermark, the watermark is
clamped back to the divergence point **and** nodes whose provenance message-ids lie strictly
beyond it on the abandoned branch are immediately demoted to DORMANT (reason=branch) — but
only if **all** of a node's provenance rows lie beyond the divergence point; a fact also
evidenced before the divergence (or on the surviving branch) keeps its status — a user
who regenerated *because* the branch went wrong must not have its facts injected for weeks
while decay grinds. If the branch is re-selected or the same content re-extracted, the dedup
gate's REINFORCE path reactivates them. Edited messages (`buildEditedParts` metadata
preserved) re-extract the affected window the same way.

**Message deletion** (single messages removed from a chat): memories derived from
already-extracted deleted messages are **kept** — same diary-burned principle as conversation
deletion (§11), and provenance excerpts are stored copies so node sheets keep working; a user
who deleted a message *because* it was wrong can forget the derived nodes from the node sheet
(the provenance link makes them findable). Mechanically, the watermark must be **anchored to
a message id, not a list index** — on any deletion, it re-resolves to the nearest surviving
earlier message, so it can never point into a gap after indices shift. Deleting *unprocessed*
messages needs nothing: they simply never get extracted. Regenerate-triggered demotion (above)
does **not** apply to plain deletion — removing a message is ambiguous cleanup, not the clear
"this content was wrong" signal that abandoning a branch is.

### 7.4 Async consistency (the "remembered too late" bug)
Guarantee: *anything said in a conversation is either still in that conversation's context, or
past the watermark and thus in the store.* The residual gap is **cross-conversation**: user
tells character something, immediately opens a *different* chat before extraction ran. Cover:
- flush-on-switch trigger (§5.1) closes the gap in the common case;
- **pending-tail digest**: `MemoryRecall` checks for conversations **of the same assistant
  only** updated in the last 24h with watermark lag > 0; injects their last few raw message
  lines, explicitly labeled ("recent unprocessed chat with you — raw and unclassified, may
  include roleplay or jokes; treat cautiously") — deterministic, no model call, capped at
  ~200 tokens. The same-assistant restriction is what makes raw injection safe: these lines
  bypass joke/fiction classification, so at worst a character sees its own recent lines —
  exactly what it would have seen had that conversation stayed open — and cross-character
  leakage is impossible by construction. So the model can never "forget" something the system
  technically has.

Honest caveat: the same-assistant restriction means the guarantee is per-character, not
global — a shareable fact told to Character A reaches Character B only after extraction (and,
for non-whitelist facts, promotion) has run. Flush-on-switch keeps that window to seconds in
the common case, but it is a window, and the design accepts it as the price of never
injecting another character's unclassified raw text.

### 7.5 Activity surfacing
After extraction/sleep runs tied to the active conversation, `ChatService` emits a
non-blocking event → the existing activity pill/timeline shows "🧠 3 memories updated"; tap
opens a bottom sheet listing the activity rows with node links. Never a dialog, never
interrupts streaming.

---

## 8. Cost, latency & budget

`MemoryBudget` admission control before *every* background model call, per category, per day
(ledger table). Presets (global setting, per-assistant overridable):

| Preset | Extraction cadence | Sleep model stages | Curiosity | ~calls/day heavy use |
|---|---|---|---|---|
| **Off** | never | decay/expiry only (free) | off | 0 |
| **Eco** | every 25 msgs + on-close | merges only, 1 batch/day | off | ~3–6 |
| **Balanced** (default) | every 10 msgs + triggers | full, 1 session/12h | optional | ~10–20 |
| **Rich** | every 6 msgs + triggers | full, 2 sessions/day | optional | ~25–40 |

- Hard daily caps per category; when exhausted, extraction *defers* (the watermark just
  stops advancing — nothing is lost). When budget returns, the backlog is worked off in
  sequential oldest-first chunks per the §5.1 watermark-integrity rule; a big backlog costs
  more calls later rather than ever skipping content. Deterministic stages (decay, expiry,
  size enforcement, retrieval) never depend on budget.
- Token caps per call apply to context/digest portions only — never the extraction window
  (§5.1).
- Model: `settings.memoryModelId` (new) → fallback `summarizerModelId` → `backgroundModelId`
  → chat model, mirroring today's chain; per-assistant `memoryModelId` override supported.
  Recommended default: a cheap fast model; the UI says so.
- Ledger is visible in the memory page's overview ("~14 background calls today") —
  transparency, not a chore.

---

## 9. Bounded growth

Hard, budget-independent guarantees:

- **Node budget per scope**: default ~1,500 non-entity nodes per character, ~1,000 global
  (configurable). At 90%, the sleep pass *must* run compression stages; at 100%, the applier
  admits new ADDs only by evicting the lowest-retention DORMANT nodes (activity-logged).
- **Compression ladder**: episodes → gists (10:1-ish), gists → era-gists ("early 2026:
  mostly studied together, user stressed about uni"). Detail is lost; gist persists — exactly
  the human curve.
- **Provenance/activity pruning**: provenance excerpts capped per node (keep first + last 3);
  activity capped at ~500 rows/scope; FORGOTTEN past grace physically deleted with edges.
- **Retrieval latency** stays flat because injectable set = ACTIVE only, which the budget caps;
  DORMANT is reachable only via explicit `memory_search`.
- Steady state for a years-long user: a bounded, slowly-churning graph, not a landfill.

---

## 10. UI (Material 3 Expressive)

Replaces `AssistantMemorySubPage`, `AssistantRagMemorySubPage`,
`AssistantMemoryConsolidationSubPage`. New `Screen.MemoryCenter(assistantId?)` route
(`composable<Screen.MemoryCenter>` in `AppRoutes`; opened from assistant detail and from
Settings for the global layer). ViewModel `MemoryCenterVM` in `di/ViewModelModule.kt`.
`AppShapes` tokens, `rememberPremiumHaptics()`, `MotionPolicy` transitions throughout.

Three tabs (primary tab row, hierarchical transitions):

1. **Overview** — stat cards (nodes by type, "memories this week"), the activity feed
   (grouped by day, tap → node sheet), health strip (purely informational, never a required
   action: "embedding index: 82% — rebuilding", import progress, and pipeline stalls —
   "memory model failing, extraction paused for 3 days" with a link to model settings; zero
   burden must not mean zero visibility into a broken pipeline), quick toggles, budget glance.
   Promotion **suggestion chips** (§6.5) render here as dismissible rows in the feed: tap =
   promote (`PremiumHaptics.Success` + activity row), dismiss/ignore = stays private; max 3
   pending shown, oldest auto-expire after 30 days.
2. **Graph** — Compose `Canvas` force-directed layout (custom; no heavy dependency):
   - simulate off-UI (coroutine on Default), render positions via `graphicsLayer`; freeze
     layout after settling; pan/zoom via `detectTransformGestures`
   - render *neighborhoods*, not everything: start clustered by ENTITY hubs and FRAME nodes
     (bubble clusters, `MaterialTheme.colorScheme` tertiary/secondary containers by type,
     size ∝ retention, dashed ring = PROVISIONAL, faded = DORMANT/CLOSED)
   - tap node → expand neighborhood + detail sheet; long-press → pin (`PremiumHaptics.Thud`);
     search field flies the camera to matches
   - reduced-motion (`LocalMotionPolicy`) → pre-settled static layout, no idle animation
3. **Browse** — searchable, filterable list (type/status/frame/scope chips, `ListItem`
   grouped shapes), sorted by retention or recency. Includes a **"Recently forgotten"**
   filter chip listing FORGOTTEN-in-grace nodes with one-tap restore — the undo surface for
   automatic forgetting (§5.4). A **"+ Add memory"** FAB lets the user hand-write a fact:
   it goes through the normal applier (dedup gate included) as a MANUAL, ACTIVE node with an
   optional pin. Browse is also the declared **accessibility-equivalent surface** for the
   graph: the Canvas gets a `contentDescription` summary and TalkBack users are routed here.

**Node detail sheet** (`AppShapes.BottomSheet`): content + type/status/scope/frame chips,
fuzzy time line ("learned about 2 weeks ago, last confirmed yesterday"),
**"Why I remember this"** — provenance excerpt(s) + rationale + link to source conversation
(via `navigateToChatPage`; if the conversation was deleted, the stored excerpt remains and
the navigation grays out — memories survive their source), **history** — walkable SUPERSEDES
chain rendered as a timeline, **connections** — tappable edge list. ENTITY nodes additionally
show their **alias list with add/remove** (a wrong alias is a user-visible retrieval bug the
user must be able to fix). Actions: Pin, Edit (creates MANUAL supersede — even the user can't
destructively overwrite), Forget (→ FORGOTTEN with grace + snackbar undo).

**Conversation deletion**: the delete-conversation dialog gains an off-by-default "Also
forget memories from this chat" checkbox (provenance conversation-id lookup → FORGOTTEN with
grace). Default keeps memories: a person still remembers things after the diary is burned.

**Export / wipe**: overflow menu → "Export memory…" (JSON zip via existing
`BackupArchiveFormat` patterns; per-character or global) and "Erase memory…" behind:
menu → full-screen confirmation explaining scope → type-the-character's-name (or "everything")
→ `PremiumHaptics.Error` + final button with 3s enable delay. Wipe writes a single activity
row and clears the rest.

**Zero-burden check**: nothing on these screens is ever *required*. No badges, no red dots,
no "run consolidation" buttons anywhere else in the app.

---

## 11. Settings & degradation matrix

Global `MemorySettings` in `Settings` (normalized via a new `normalizeMemorySettings` stage in
`SettingsStore.update`), per-assistant overrides on `Assistant` (existing `enableMemory` is
reused as the master per-character switch; legacy flags migrate — §12.3).

| Toggle | Default | Off-behavior (everything else keeps working) |
|---|---|---|
| Memory (per character) | existing value | no extraction, no injection; store kept |
| Shared user memory (per character) | on | character reads/writes CHARACTER scope only |
| Preset (Eco/Balanced/Rich) | Balanced | §8 budgets |
| Time awareness | on | no fuzzy ages/tenses in injection; internal time keeps running |
| Proactive curiosity | **off** | no goals generated; existing goals frozen |
| └ Web lookups for curiosity | off (grayed unless curiosity on) | goals ask without pre-research |
| Habit induction | on | no HABIT nodes; episodes still compress |
| Embedding use | auto | auto-degrades: no embedding model configured → pure FTS/lexical everywhere (dedup thresholds switch to lexical-only); configure one later → backfill worker embeds existing nodes in batches |
| Memory model | summarizer chain | per-assistant override picker included this time |

Dependent toggles gray out (never silently force values). Every subsystem above is
independently removable because they only communicate through the store + op vocabulary.

---

## 12. Migration & change resilience

### 12.1 Room v33 → v34
Purely **additive**: new tables (§3), plus `extracted_up_to_index` on conversation if absent.
`AutoMigration(from=33, to=34)`, registered in `dataSourceModule.addMigrations(...)`, schema
JSON committed. The unmerged `MemoryItemEntity`/`MemoryItemFtsEntity` files are deleted
(never in `@Database`, so no migration implication). Old `MemoryEntity` + `ChatEpisodeEntity`
tables and DAOs stay untouched (read-only) for at least one release; dropped in v35 later.

### 12.2 Data import (background, resumable)
`MemoryImportWorker`, enqueued once on first boot ≥ v34 (watermark in `memory_store_meta`):
- `MemoryEntity` CORE → FACT nodes (CHARACTER scope, importance 3, ACTIVE,
  `source=IMPORTED`, provenance "imported from previous memory system"); EPISODIC +
  `ChatEpisodeEntity` → EPISODE nodes (event time from old timestamps; existing embeddings
  carried over when `embedding_model_id` matches current, else left for backfill).
- Imports go **through `MemoryOpApplier`** — same dedup gate, same entity resolution — so the
  import inherits every guardrail instead of needing its own quality logic.
- No model calls at import time. Ordinary sleep passes then refine imports over the following
  days within normal budget. Chunked (200 rows/run), safe to kill and resume.
- **User communication**: while running, the Memory Center health strip shows import
  progress; on completion an activity row plus a one-time dismissible card appears ("Your
  memories were migrated to the new system — N facts, M episodes") so a slow import on a big
  database never reads as "my memories are gone".

**Backup/restore**: the new tables live in the same Room database and therefore ride the
existing whole-DB backup path (`WebdavSync`/`BackupArchiveFormat`) automatically — a restore
restores memory. The §10 memory export is an additional user-facing format, not the backup
mechanism.

### 12.3 Settings migration
In `SettingsStore` normalization: `enableMemoryConsolidation=true` → preset Balanced;
`useRagMemoryRetrieval` retires (retrieval is always hybrid now);
`enableRecentChatsReference` → recent-episode strip on. Old flags kept deserializable
(the `PythonEngine`-style pattern) so downgrades don't crash.

### 12.4 Embedding model changes
Per-node `embedding_model_id`; vector search filters to the current model's vectors; a
backfill worker re-embeds mismatched ACTIVE nodes in batches (budget category SLEEP); FTS
serves fully in the interim. Because vectors are only candidate generators (§5.3) and never
gate correctness, switching models is a non-event with a temporary recall-quality dip, not
data loss — and no similarity threshold anywhere assumes a particular model's cosine
distribution.

### 12.5 Future-proofing
`store_schema_version` in `memory_store_meta` + a tiny in-store migration registry for
semantic migrations (re-scoring, re-classification) that Room DDL migrations can't express.
Export format carries the same version.

---

## 13. Implementation phases

Riskiest-first; each phase ships behind the master toggle and leaves the app releasable.

- **P1 — Store + applier + import** (foundation): schema v34, models, `MemoryGraphRepository`,
  `MemoryOpApplier` with dedup gate (lexical path), FTS, provenance/activity, import worker,
  unit tests (applier guardrails, dedup gate, watermark clamping are the test-critical core).
- **P2 — Encode + recall**: `MemoryEncoder` + triggers in `ChatService`, `MemoryRecall`
  injection (core sheet, query recall, episode strip, pending-tail), time verbalization,
  budget ledger. Old injection path removed. This is the end-to-end MVP.
- **P3 — Sleep pass** ✅ *implemented*: decay/expiry/size enforcement (deterministic), then
  merge/contradiction confirmation, compression, habit induction. Legacy `MemoryConsolidationWorker`
  periodic path retired for memory-enabled users.
- **P4 — UI**: Memory Center (Overview + Browse + node sheet + export/wipe first; Graph tab
  second — it's the most polish-hungry, and Browse makes the system fully usable meanwhile).
  Activity pill integration. Remove old sub-pages.
- **P5 — Profiles + curiosity**: character profile generation, frames in extraction/retrieval,
  `CuriosityEngine` (off by default), web-fill option.
- **P6 — Hardening**: embedding backfill worker, settings migration polish, on-device v33→v34
  migration test, i18n pass (English-only until then, per repo policy), baseline-profile check
  for the graph canvas.

Explicit test matrix per phase: applier property tests (no op sequence can produce a
neighbor-duplicate or an orphan non-entity node), watermark/branch tests, retrieval latency
benchmark on a 5k-node store, migration test against a real v33 database file.

---

## 14. Decided defaults & remaining open questions

Decided (with the user, 2026-07-03):
- **Curiosity ships default OFF** — tone-safest; the feature is fully built and one toggle
  away. (External review noted the brief's flagship scenario requires the flip; accepted.)
- **Shared-memory promotion = category whitelist + optional confirm chip** (§6.5) — never
  fully automatic outside identity-level categories.

Still open (defaults chosen, validate during implementation):
1. Node budget numbers (1,500/1,000) — validate against real DB sizes during P3.
2. Graph physics: custom Canvas simulation (chosen) vs. precomputed static layout — fall back
   to static clustered layout if 60fps pan/zoom isn't comfortably reachable on mid-range ARM.
3. Whether `memory_search` tool should also search raw past conversations (as today) — keep
   initially, since gists may not cover everything until compression matures.
4. Per-embedding-model similarity calibration (§5.3) — enhancement only; not needed for
   correctness.
