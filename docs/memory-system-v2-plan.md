# LastChat Memory v2 — Graph Memory System: Complete Plan

Status: **PLAN** (nothing implemented). Supersedes every prior memory design, including:

- the live legacy system (`MemoryEntity` + `ChatEpisodeEntity` + `MemoryConsolidationWorker` RAG),
- the orphaned `MemoryItemEntity`/`MemoryItemFtsEntity` two-layer WIP (dead code; deleted by this plan),
- **attempt 1** — the full graph build on branch `origin/LC_memory_system_attempt_1` (Room v34/v35,
  `docs/memory-system-plan.md` on that branch). Attempt 1 was implemented end-to-end (P1–P6) and
  reverted. This plan deliberately keeps its proven operational machinery (message-id watermark,
  single-writer applier, budget ledger, scope locks, branch demotion) and **replaces its core data
  model**, which is where it failed the brief.

## 0. What was wrong with attempt 1, and what v2 changes

Attempt 1's `memory_node` stored a whole sentence per node ("User studies CS at TU Wien") and used
edges only as plumbing (`ABOUT`, `RELATES_TO`). Consequences, mapped to the brief's failure list:

| Brief failure | How attempt 1 exhibited it | v2 structural change |
|---|---|---|
| #9 not a proper graph; long-text nodes; can't digest the graph efficiently | Sentence-nodes meant the "graph" was a bag of paragraphs hub-linked to entities. The extraction digest had to quote whole sentences (token-expensive); the graph UI showed text blobs, not structure. | **Facts live on edges, not nodes.** Nodes are entities (short labels, hard caps); a fact is a typed `subject —predicate→ object` relationship with a ≤160-char rendering. The neighborhood digest serializes as compact triplet lines — the whole relevant subgraph fits in a few hundred tokens. |
| #10 didn't merge nodes naturally ("user" vs the user's name as two nodes) | Entity resolution was label-match + FTS; nothing prevented the extractor from minting "Julian" as a person separate from "user". | **Pinned canonical nodes + structural rules.** Every store is born with a pinned `user` node and a pinned `character` node. The user's name is **never an entity** — it is an alias on the `user` node plus a fact `user —is_named→ "…"`. The applier resolves any PERSON whose name matches a known alias of `user`/`character` to that canonical node before anything is created. Duplicate-person creation for the two protagonists is impossible by construction. |
| #11 episode spam (an episode per message; endless variations) | Episodes were extracted per pass with a soft rubric only. | **Scene-level episodes with an upsert path.** ≤1 episode per detected scene; a continuing scene **extends** the existing episode (`EXTEND_EPISODE`) instead of creating a sibling; a salience rubric means most passes create zero episodes. Caps are enforced in the applier, not the prompt. |
| #1 duplicates | Lexical dedup gate over sentence-nodes — "similar text" is a weak identity signal. | **Structural dedup.** After entity resolution, a fact is a `(subject_id, predicate, object)` triple. Exact triple match → REINFORCE, mechanically. Same `(subject, predicate)` with a different object → contradiction/update candidate, mechanically. Text similarity is demoted to a tie-breaker, never the decision. |

Everything else attempt 1 got right is retained and cited section-by-section, so this document is
self-contained: you never need the old branch to implement v2 (but it remains a useful reference
for concrete code shapes — see §17).

---

## 1. Design philosophy

Four principles; every mechanism below follows from them.

1. **The graph is entities and relations, or it isn't a graph.** Nodes are things (people, places,
   activities, fictional props). Facts are labeled edges between them. Episodes are the only
   prose-carrying nodes, and they are scene-sized, capped, and compressible. Anything that would
   be a paragraph is provenance, not memory.
2. **Append-only beliefs, computed forgetting.** Models *propose* operations from a closed
   vocabulary; deterministic code applies them under guardrails. There is no DELETE op and no
   in-place edit. Updates supersede; endings close validity windows; forgetting is a scheduled,
   explainable, grace-period process driven by a retention formula — never a side effect.
3. **Autonomous thinking is metered; user-driven work is not — and the system is good with no
   model at all.** Retrieval, decay, expiry, dedup's structural path, and size enforcement are
   deterministic. Extraction is user-activity-driven (cost ∝ chatting, cheap model, each message
   read once) and runs unmetered on a cadence; the *autonomous* stages — sleep adjudication,
   compression, profiles, curiosity — draw from an explicit daily ledger and simply defer when
   exhausted. Deferral delays memory, never loses it.
4. **Embeddings are an accelerator, never a dependency.** FTS + alias tables + graph traversal is
   the primary index. Every retrieval and dedup path has a lexical branch. A user who never
   configures an embedding model gets a fully working system.

Human-memory analogy (used where it genuinely maps):

| Human | v2 |
|---|---|
| Working memory | The live chat context window (no memory machinery) |
| Encoding | Extraction pass over new messages → provisional facts/episodes |
| Semantic memory | Fact-edges between entity nodes |
| Episodic memory | Scene-level EPISODE nodes with time anchors and frames |
| Consolidation in sleep | Sleep pass: adjudicate, merge, compress, induce habits |
| Gist retention | Old episode clusters → GIST summaries; details forgotten |
| Forgetting curve | Per-item stability `S`; retrievability `R = e^(−Δt/S)`; reinforcement raises `S` |
| Retrieval strengthening | Genuine recall bumps stability; unused memories decay |
| Source monitoring | Provenance rows: excerpt + rationale + literal/joke/fiction classification |
| Curiosity | GOAL rows → at most one gentle in-character question, hard-capped |

---

## 2. Past failures → structural counters (complete map of brief §2)

| # | Past failure | Structural counter (impossible-by-construction, not mitigated) |
|---|---|---|
| 1 | Duplicates | Dedup is structural triple equality after entity resolution (§6.4). The applier is the only writer; there is no code path that inserts a fact without passing the gate. Restatements become REINFORCE mechanically. |
| 2 | Incorrect extractions | Everything is born PROVISIONAL with provenance (excerpt + rationale + literal/joke/fiction label). ACTIVE status is earned by reinforcement or 7 quiet days — never by model-reported confidence. High-importance provisionals inject *hedged*. Every item is user-correctable from its node sheet, and a user correction (MANUAL) can never be overridden by an automatic pass. |
| 3 | Trivia stored forever | Universal decay: every non-pinned item has stability/retrievability; low retention → DORMANT → FORGOTTEN (30-day grace) → deleted. Extraction has hard per-pass ADD caps and an importance rubric whose default answer is "no op". Permanence is earned through reinforcement, not granted at write time. |
| 4 | Destructive overwrites | No UPDATE-in-place, no DELETE in the op vocabulary. Updates create a new fact plus a SUPERSEDES link; the old fact remains readable (SUPERSEDED) with its full chain. Endings close `valid_until` (CLOSED = history, not garbage: "used to live in Graz"). |
| 5 | Migration pain | Legacy rows import through the same applier as first-class items (`source=IMPORTED`), no model calls, chunked and resumable; old tables stay read-only for one release; sleep passes refine imports gradually within normal budget (§16.2). Store carries its own `store_schema_version` for future semantic migrations. |
| 6 | Embedding dependency | FTS4 + alias expansion + graph traversal is the primary path everywhere; vectors only widen candidate sets. Per-item `embedding_model_id` + background backfill worker makes model switches a non-event (§16.4). Zero-config users lose nothing but a recall-quality edge. |
| 7 | Time misunderstanding | First-class bi-temporal model on every fact (§5): event/validity time vs belief time. Point vs durative fact kinds. Fuzzy verbalization at injection ("about a week ago"), past tense for closed facts. Extraction resolves relative dates against per-message timestamps before storage. |
| 8 | No coherent vision | This document: one graph, one op vocabulary, one budget, one degradation matrix, one migration story, one UI. |
| 9 | Not proper graphs; long-text nodes; poor digestion | Facts-on-edges model with hard length caps (§4); token-efficient triplet digest (§6.2). |
| 10 | No natural node merging (user ≠ user's name) | Pinned canonicals + alias-first resolution + name-is-an-alias rule (§6.4); merge adjudication in sleep (§6.5). |
| 11 | Useless memories; an episode per message | Scene segmentation + salience rubric + EXTEND_EPISODE upsert + applier caps (§6.2, §6.3). |

Brief requirements mapped elsewhere: zero burden §3.3 → no user-facing maintenance state exists at
all (§14 "zero-burden check"); async consistency §3.8 → §11.4; mode awareness §3.4 → §8; proactive
§3.5 → §9; privacy §3.6 → §10; transparency §3.7 → §14; modularity §3.9 → §15; migration §3.10 →
§16; bounded growth/cost §3.11 → §12–§13.

---

## 3. Architecture overview

All in `:app` (memory is app-level orchestration, like `GenerationHandler`). New package:
`me.rerere.rikkahub.data.memory/`.

```
                        ┌─────────────────────────────────────────────┐
 chat messages ───────► │ MemoryEncoder (extraction)                  │──► ops (closed vocab)
 (post-generationDone)  │ triggers: watermark lag / conv switch /     │
                        │ idle / app background / pre-spontaneous     │
                        └─────────────────────────────────────────────┘
                                                                        │
 ┌────────────────────┐    ┌───────────────────────────┐    ┌──────────▼──────────┐
 │ MemorySleepWorker  │──► │  MemoryOpApplier          │◄───│ save_memory tool,   │
 │ decay/adjudicate/  │    │  entity resolution +      │    │ manual UI adds,     │
 │ compress/habits/   │    │  structural dedup gate;   │    │ import worker       │
 │ goals/size         │    │  the ONLY writer          │    └─────────────────────┘
 └────────────────────┘    └────────────┬──────────────┘
                                        ▼
                        ┌────────────────────────────────┐
                        │ Graph store (Room v34)         │
                        │ entities / facts / episodes /  │
                        │ aliases / frames / provenance /│
                        │ FTS / goals / activity / ledger│
                        └────────────┬───────────────────┘
                                     ▼
 ┌──────────────────────────────────────────────────────────────────┐
 │ MemoryRecall (retrieval — ZERO model calls, <50 ms target)       │
 │ core sheet (cached) + query recall (alias→FTS→vectors→spreading  │──► system prompt
 │ activation) + recent episode strip + pending-tail digest         │    (via transformer)
 └──────────────────────────────────────────────────────────────────┘
```

Components (Koin singles unless noted):

- **`MemoryGraphRepository`** — DAO façade, all reads, owns the cached core sheet per scope.
- **`MemoryOpApplier`** — the *only* writer. Entity resolution, structural dedup gate, guardrails,
  length clamps, provenance/FTS/activity sync, watermark advance — all in one Room transaction.
- **`MemoryEncoder`** + **`MemoryExtractionWorker`** — builds the extraction prompt (window +
  triplet neighborhood digest + character profile), parses ops. Triggered from `ChatService`
  post-persist via WorkManager one-shots — never on the generation hot path.
- **`MemorySleepPass`** + **`MemorySleepWorker`** — periodic (~12 h): deterministic stages always,
  model-assisted stages within budget. Replaces `MemoryConsolidationWorker`.
- **`MemoryRecall`** — synchronous deterministic retrieval; injected via a new
  **`MemoryRecallTransformer`** (input transformer in `ChatService`'s pipeline, replacing the
  legacy `retrieveRelevantMemories` block at `ChatService.kt:1487–1525`); also backs the rebuilt
  `search_memory` tool.
- **`CharacterProfileGenerator`** — one cached model call per character (hash of systemPrompt +
  name + enabled skills) → frames/modes, persona relation, care-abouts (§8.1).
- **`CuriosityEngine`** — goal lifecycle + delivery gating + structural back-off (§9).
- **`MemoryBudget`** — ledger for every background model call (transparency) + hard admission
  control for the autonomous categories (sleep/profile/curiosity) and the extraction safety
  ceiling (§12).
- **`MemoryImportWorker`**, **`MemoryEmbeddingBackfillWorker`** — migration/backfill (§16).

Pure-logic companions (unit-testable, no Android deps): `MemoryOpParser`, `MemoryDedupLogic`,
`MemoryRetentionLogic`, `MemorySleepLogic`, `MemoryWatermark`, `MemoryBranchLogic`,
`CuriosityLogic`, `PromotionLogic`, `SceneSegmenter`.

---

## 4. Data model (Room v34, all tables additive)

Ids are `Uuid` strings. All JSON via `JsonInstant`. Hard length caps are applier-enforced
(clamped at sentence boundary, full text preserved in provenance) — the schema never stores
unbounded prose outside episode summaries and provenance excerpts.

### 4.1 `memory_node` — the nodes

> **Implemented as `memory_node`, not `memory_entity`** (P1a, commit `fecb53bd`). Room's
> generated `AppDatabase_Impl` collapses `memory_entity` and the legacy `MemoryEntity` table to
> the same identifier → compile error while legacy survives to v35. All P1b+ SQL targets
> `memory_node`; the Kotlin type is `MemoryNodeEntity`/`MemoryNodeDao`. Domain concept
> ("entity node") unchanged; every "entity table" reference below means this table.

| column | notes |
|---|---|
| `id` TEXT PK | Uuid |
| `scope` INT | GLOBAL_USER=0, CHARACTER=1 |
| `owner_assistant_id` TEXT? | null iff GLOBAL_USER |
| `name` TEXT | canonical label, **≤64 chars** ("TU Wien", "Mom", "our lighthouse") |
| `kind` INT | USER_SELF, CHARACTER_SELF, PERSON, PLACE, ORG, ACTIVITY, OBJECT, CONCEPT, EVENT |
| `reality` INT | REAL=0, FICTION=1 (fiction props/places live in the graph but never leak — §8.2) |
| `summary` TEXT? | **≤240 chars**, refreshed by sleep pass from top facts; null OK |
| `pinned` INT | user-set; USER_SELF/CHARACTER_SELF born pinned |
| `status` INT | ACTIVE, DORMANT (entities have no PROVISIONAL; they exist iff referenced) |
| `created_at`, `last_accessed_at` INT; `times_retrieved` INT | |
| `embedding_blob` BLOB?, `embedding_model_id` TEXT? | `VectorUtils` format (existing codec) |
| `extra` TEXT JSON | kind-specific payload |

Indices: `(scope, status)`, `(owner_assistant_id, status, kind)`, `(embedding_model_id)`.

**Canonical rows:** on first enable per character, the applier ensures a pinned GLOBAL `user`
node (kind USER_SELF, one per store) and a pinned CHARACTER `character-self` node per assistant.
These ids are handed to every extraction prompt; first/second-person references resolve to them
*before* any resolution logic runs.

### 4.2 `memory_alias`

`id`, `entity_id`, `alias` TEXT (≤64), `normalized` TEXT (lowercased/trimmed, indexed), `scope`,
`owner_assistant_id`, `created_at`, `source`. Unique index `(normalized, entity_id)`; index
`(normalized, scope, owner_assistant_id)`. A dedicated table (not JSON-in-extra as attempt 1)
because alias lookup is the first step of both entity resolution and query expansion — it must be
an indexed query, not a JSON scan. The user's display name and nicknames land here on the `user`
node ("Julian", "Jules"); "uni" lands on "TU Wien".

### 4.3 `memory_fact` — the relationship edges (the semantic layer)

| column | notes |
|---|---|
| `id` TEXT PK | |
| `subject_id` TEXT | → `memory_entity` |
| `predicate` TEXT | **≤32 chars**, normalized snake_case verb phrase ("studies", "lives_in", "dislikes", "is_named", "works_at", "owns", "is_dating") — seeded vocabulary + open extension (§6.4) |
| `object_id` TEXT? | → `memory_entity`; exactly one of `object_id`/`object_value` is set |
| `object_value` TEXT? | literal **≤80 chars** ("Julian", "1999-04-12", "vegetarian") |
| `statement` TEXT | **≤160 chars** human rendering for FTS/UI ("Takes math classes at TU Wien") |
| `kind` INT | DURATIVE (state), POINT (one-time event-fact), HABIT (recurring pattern) |
| `scope` INT, `owner_assistant_id` TEXT? | as entities |
| `reality` INT | REAL / FICTION |
| `frame_id` TEXT? | → `memory_frame` (§8.2) |
| `importance` INT 1..5, `confidence` REAL, `sensitivity` INT (NORMAL/SENSITIVE) | |
| `category` TEXT? | closed set for promotion whitelist: name, pronouns, language, timezone, occupation_study, other (§10) |
| `status` INT | PROVISIONAL, ACTIVE, DORMANT, SUPERSEDED, CLOSED, FORGOTTEN |
| `pinned` INT | |
| `valid_from` INT?, `valid_until` INT? | real-world validity window (durative facts) |
| `recorded_at` INT, `expired_at` INT? | belief time: learned / belief retracted (bi-temporal pair to validity) |
| `last_confirmed_at`, `last_accessed_at` INT | |
| `times_reinforced`, `times_retrieved` INT | |
| `stability` REAL | forgetting-curve stability in days (§6.6) |
| `source` INT | EXTRACTED, TOOL, MANUAL, IMPORTED, MERGED, DERIVED, CONFIRMED_BY_USER |
| `embedding_blob` BLOB?, `embedding_model_id` TEXT? | embedding of `statement` |
| `extra` TEXT JSON | HABIT cadence, import payload, etc. |

Indices: `(subject_id, predicate, status)`, `(object_id)`, `(scope, status)`,
`(owner_assistant_id, status)`, `(frame_id)`, `(embedding_model_id)`.

Notes:
- A fact is *addressable* like an edge (subject→object) **and** carries all belief metadata. The
  graph UI draws it as a labeled edge; the digest serializes it as a triplet line.
- "Legacy note" facts (from import, §16.2) use `predicate='notes'`, `object_value=null`,
  statement = the old free text (clamped) — they are valid but second-class, and the sleep pass
  gradually restructures the important ones into real triplets.

### 4.4 `memory_fact_link` — belief-chain metadata between facts

`id`, `fact_id`, `other_fact_id`, `type` INT (SUPERSEDES, CONTRADICTS, DERIVED_FROM), `created_at`.
Indices on both fact columns. This keeps the entity graph clean (facts link entities; fact-links
link beliefs) — the UI's "history" timeline walks SUPERSEDES chains here.

### 4.5 `memory_episode` — scene-level events

| column | notes |
|---|---|
| `id` TEXT PK | |
| `owner_assistant_id` TEXT | episodes are always CHARACTER-scoped |
| `title` TEXT | **≤60 chars** ("Roleplayed learning together") |
| `summary` TEXT | **≤400 chars** |
| `frame_id` TEXT?, `reality` INT | |
| `event_start` INT, `event_end` INT? | wall-clock session time (fiction chronology lives in the summary text) |
| `importance` INT, `status` INT, `pinned` INT, `stability` REAL | as facts |
| `is_gist` INT | 1 = compression product (§6.5 stage 5) |
| `conversation_id` TEXT? | source conversation (survives its deletion) |
| `recorded_at`, `last_accessed_at` INT; `times_retrieved` INT | |
| `embedding_blob` BLOB?, `embedding_model_id` TEXT?; `extra` JSON | |

Indices: `(owner_assistant_id, status, event_start)`, `(conversation_id)`, `(frame_id)`.

### 4.6 `memory_mention` — episode ↔ entity connectivity

`id`, `episode_id`, `entity_id`, `weight` REAL, `created_at`. Indices both ways. This is what
makes episodes part of the graph (spreading activation traverses it) without giving them prose
edges.

### 4.7 `memory_frame` — modes/storylines per character

`id`, `owner_assistant_id`, `label` (≤40: "roleplay", "study chat", "lighthouse storyline"),
`descriptor` (≤200), `source` INT (PROFILE, DETECTED), `created_at`, `status`. Produced by the
character profile generator (§8.1) or detected by extraction; a character without mode structure
gets a single default frame and the system degrades to frameless gracefully.

### 4.8 `memory_provenance`

`id`, `row_kind` INT (FACT, EPISODE, ENTITY, GOAL), `row_id`, `conversation_id?`,
`message_ids` JSON, `excerpt` (≤300 chars, stored copy — survives message/conversation deletion),
`rationale` (model one-liner: *why saved*), `classification` INT (LITERAL, JOKE, HYPOTHETICAL,
FICTION, INSTRUCTION), `created_at`. One row per originating pass; REINFORCE appends. This is the
entire "why does this memory exist" story in the UI (§14 node sheet) and the source of the
"Literal" chip in the reference sketches.

### 4.9 `memory_fts` — the primary index

Standalone FTS4 table (the first FTS4 table in the project — the §4.9 "already uses FTS4" claim
was inaccurate; AutoMigration creates the virtual table fine), manually synced by the applier
(no Room triggers): `row_kind`, `row_id`, `text` = fact statement / episode title+summary /
entity name+aliases. One unified table so a single MATCH query covers all recall paths.

### 4.10 `memory_goal` — curiosity state (deliberately *not* in the graph)

`id`, `owner_assistant_id`, `question` (≤200), `value_note` (≤200), `entity_ids` JSON,
`state` INT (OPEN, PRIMED, ASKED, CONFIRMED, DECLINED, IGNORED, ABANDONED), `web_draft` TEXT?
(unconfirmed search result), `asked_at?`, `created_at`, `resolved_at?`, `extra`. Goals are process
state, not memory; keeping them out of the node/edge tables keeps the graph a graph (lesson from
attempt 1, which had GOAL nodes cluttering the store).

### 4.11 Supporting tables (adopted from attempt 1 unchanged in spirit)

- **`memory_activity`** — append-only UI feed: `at`, `kind` (EXTRACTED, REINFORCED, UPDATED,
  MERGED, PROMOTED, SUGGESTED, DECAYED, COMPRESSED, CLOSED, FORGOTTEN, EVICTED, GOAL_*, IMPORTED,
  WIPED, MANUAL_ADDED, EMBEDDED), `summary`, `row_refs` JSON, `conversation_id?`, `state?`
  (suggestion rows: pending/accepted/dismissed/expired), `calls_used` INT? (budget transparency —
  feeds the "4 API calls today" label). Pruned to ~500 rows/scope.
- **`memory_budget_ledger`** — `day`, `category` (EXTRACTION, SLEEP, PROFILE, CURIOSITY), `calls`,
  `tokens_in`, `tokens_out`.
- **`memory_conversation_state`** — `conversation_id` PK, `extracted_up_to_message_id` TEXT?
  (message-id anchored watermark — attempt 1's §7.2 hardening, kept verbatim), `last_extracted_at`.
  Dedicated table keeps it off the high-frequency conversation-persist path.
- **`memory_store_meta`** — key/value: `store_schema_version`, import watermarks, last sleep run,
  per-assistant profile hash, embedding backfill watermark.

### 4.12 Kotlin model layer

`data/model/MemoryGraph.kt`: `MemoryEntityNode`, `MemoryFact`, `MemoryEpisode`, `MemoryFrame`,
`MemoryGoal`, `MemoryOp` (sealed, §6.3), int-code objects (attempt 1's `MemoryGraphCodes` pattern),
entity/DTO mappers. No `!!` on JSON anywhere (`jsonObjectOrNull` helpers per repo rules).

---

## 5. Time model

Bi-temporal + fuzzy verbalization — this is what gives characters a genuine sense of time.

1. **Event time** — `memory_episode.event_start/event_end`: when it happened (wall clock; for
   FICTION episodes this is the *session* time — "we roleplayed X last week"; in-fiction
   chronology stays inside the summary text).
2. **Validity time** — `memory_fact.valid_from/valid_until`: the real-world window a durative
   fact held. "Is preparing for exams" gets a window; extraction CLOSEs it when the user says
   exams ended; the sleep pass expires it past a proposed horizon. A CLOSED fact is history, not
   garbage — retrievable, verbalized in past tense ("used to live in Graz").
3. **Belief time** — `recorded_at` / `expired_at` (+ `last_confirmed_at`): when the system
   learned/retracted/reconfirmed the belief. Together with validity this is the full Graphiti
   bi-temporal quartet: "what is true now" and "what did we believe then" are both answerable,
   and corrections never destroy the record.
4. **Point vs durative** (`kind`) — "mentioned a headache" (POINT) vs "is vegetarian" (DURATIVE).
   Durative facts get validity windows and higher default stability; point facts decay faster
   unless reinforced. (This split is the core insight of arXiv 2601.07468.)
5. **Verbalization at injection** — never raw timestamps. Fuzzy labels ("earlier today", "a few
   days ago", "about a week ago", "last summer" — reuse/extend the existing `:ai`
   fuzzy-age util), past tense for CLOSED/SUPERSEDED, frame prefixes for episodes:
   "*[roleplay, about a week ago]* you two played at learning together".
6. **Extraction receives** current date/time and per-message timestamps, and must resolve relative
   anchors ("next Friday" → absolute epoch) before output. The applier stores absolutes only.
7. **Toggle** (`timeAwareness=false`, §15): injection drops age phrasing and tense conversion;
   validity/decay keep running internally (bounded growth is not optional).

---

## 6. Lifecycle

### 6.1 Triggers (when the system is allowed to think)

All funnel into one WorkManager one-shot per conversation (`memory-extract-<conversationId>`,
`ExistingWorkPolicy.KEEP`); only flush-on-switch is expedited
(`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`):

- watermark lag ≥ N messages (preset-dependent, §12; Balanced N=10)
- conversation switched away or app backgrounded, lag ≥ 2
- 15 min conversation idle, lag ≥ 2
- immediately before a spontaneous/scheduled message for that assistant (proactive messages never
  act on stale memory)

Hard rules (attempt 1's hardening, kept): triggers evaluate only after `generationDoneFlow` (or on
user-message save when no generation is running) — **never** off the 1 s streaming checkpoints;
the watermark can never sit inside a streaming message. Only `ChatPersistenceMode.NORMAL`
conversations extract (TEMPORARY never; PERSIST_ON_REPLY once persisted).

### 6.2 The extraction pass (one model call)

**Input** (token-capped on context portions only — never the extraction window; §12):
- the unprocessed window (watermark → end) with roles + timestamps, ±2 processed messages for
  antecedent resolution; text parts only (attachments render as `[image]`/`[document: name]`
  placeholders; OCR text already present as parts is included)
- the character memory profile (§8.1): frames, persona relation, care-abouts
- pinned canonical ids: "the user is entity e-user; you (the character) are e-char"
- the **neighborhood digest**: for entities detected in the window (alias-table lexical pass +
  FTS), their current facts as compact triplet lines. This is the token-efficient graph
  serialization attempt 1 lacked:

```
[e12] user (aka Julian, Jules)
  [f31] user —studies→ [e4] TU Wien :: "Takes math classes at TU Wien" (durative, ACTIVE, since 2026-02, imp4, ×3)
  [f18] user —dislikes→ [e9] school (durative, ACTIVE, imp3)
  [f44] user —is_named→ "Julian" (imp5, pinned)
[e4] TU Wien (PLACE, aka uni, TU)
[ep7] "Roleplayed learning together" (2026-06-28, frame: roleplay)
```

- open goals for this character (so a chat that answers one triggers RESOLVE_GOAL)
- current date/time, user display name

**Prompt contract (the judgment layer):**
- classify each candidate `literal | joke | hypothetical | fiction | instruction`; jokes and
  hypotheticals produce no fact (at most a low-importance HABIT candidate if a durable style
  trait: "often deadpans about hating Mondays"); fiction → FICTION-flagged episode/facts inside
  the active frame, **never** GLOBAL_USER or REAL
- importance rubric (5 = identity: name, family, health; 1 = ambient trivia) with the explicit
  default: **trivia produces no op at all**
- reuse existing entity ids from the digest; propose a new entity only when nothing matches, with
  kind + aliases; the user's/character's names and nicknames are ALIAS ops on the canonical
  nodes, never new entities
- prefer REINFORCE/UPDATE_FACT/CLOSE_FACT over ADD_FACT when the digest shows a related belief
- mark `sensitivity=SENSITIVE` for secrets, health, sexuality, finances, anything confided
- tag user-facts with `category` (promotion whitelist input, §10)
- propose validity horizons for states ("on vacation" → ~2 weeks unless dated)
- **scene handling**: the pass receives the tail episode (if the window continues a scene) and
  must EXTEND_EPISODE rather than add; a new episode requires a scene boundary (>2 h gap, frame
  switch, or clear topic close) *and* scene salience (emotional weight, novelty, narrative
  significance) — a routine exchange yields no episode
- output caps stated in-prompt and enforced in-applier: ≤6 ADD_FACT, ≤1 ADD_EPISODE per scene,
  ≤8 ops total per pass

Output: strict JSON op list (structured-output mode where the provider supports it; tolerant
parsing via `jsonObjectOrNull` otherwise). Parse failure → pass aborts, watermark unmoved, retry
with backoff; repeated failure surfaces only in the Memory screen health strip (never a chat
error).

**Watermark integrity invariant** (kept from attempt 1): if the unprocessed window exceeds the
per-call token cap (e.g. after an offline period or failure-induced deferral), split into
oldest-first chunks; each pass advances the watermark exactly to its chunk's end. The watermark never moves past a message no pass has read —
deferral delays memory, never loses it.

### 6.3 Op vocabulary (complete, closed)

```
ENSURE_ENTITY {ref, name, kind, aliases[], reality, scope?}        // resolve-or-create; applier decides
ADD_ALIAS     {entity, alias}
ADD_FACT      {subject, predicate, object | value, statement, kind, importance, confidence,
               sensitivity, category?, reality, frame?, valid_from?, valid_until?}
REINFORCE     {fact_id, note?}                                     // bumps counters + stability
UPDATE_FACT   {old_fact_id, ...new fields}                         // = ADD_FACT + SUPERSEDES link
CLOSE_FACT    {fact_id, ended_at?}                                 // closes validity; status CLOSED
ADD_EPISODE   {title, summary, entities[], frame?, reality, event_start, event_end?, importance}
EXTEND_EPISODE{episode_id, summary, event_end}                     // scene continuation (replaces summary, applier merges)
OPEN_GOAL     {question, entities[], value_note}
RESOLVE_GOAL  {goal_id, outcome, learned?}
```

No DELETE. No in-place edit. Sleep-pass-only ops (generated by code, never accepted from
extraction): `MERGE_ENTITIES`, `MERGE_FACTS`, `RESOLVE_CONTRADICTION`, `COMPRESS_EPISODES`,
`INDUCE_HABIT`, `PROMOTE_SCOPE`, `REFRESH_SUMMARY`, `DECAY`. Manual/UI ops: `MANUAL_ADD`,
`MANUAL_EDIT` (supersede), `PIN`, `FORGET`, `RESTORE`.

### 6.4 Entity resolution & the structural dedup gate

**Entity resolution pipeline** (applier, deterministic-first, per ENSURE_ENTITY / fact endpoint):
1. **Canonical short-circuit**: first/second-person refs and any PERSON whose normalized name or
   alias matches a `user`/`character-self` alias → the pinned canonical node. (Failure #10 dies
   here.)
2. **Alias table exact match** (normalized) within scope.
3. **FTS match** over entity names + aliases; **vector shortlist** if embeddings available (never
   decisive alone — cosine distributions vary across user-supplied models).
4. Ambiguity (multiple candidates above lexical threshold) → create nothing yet: attach to best
   candidate, flag pair for sleep-pass merge adjudication.
5. No match → create (status ACTIVE — entities exist by reference), with proposed aliases after
   alias hygiene (an alias exactly matching another in-scope entity's canonical label is dropped;
   the same alias *may* map to multiple entities — expansion is candidate generation, never
   rewriting).

**Predicate normalization**: lowercase snake_case; a small static synonym map folds variants
("attends"→"studies", "hates"→"dislikes", "lives_at"→"lives_in"); unknown predicates are allowed
(open world) but counted, and the sleep pass canonicalizes frequent synonyms. The seeded
vocabulary ships ~40 predicates covering people/places/preferences/possessions/relationships/
activities/health/schedule.

**Fact dedup gate** (structural; core rule unchanged from attempt 1: *similarity is not
identity*):
1. Resolve subject/object → candidate set = existing facts with same `(subject_id, predicate)`
   (indexed lookup), widened by FTS/vector over statements.
2. **Exact triple match** (same subject, predicate, object entity or normalized literal) and no
   new information → **REINFORCE** mechanically. If the matched fact was superseded by a MANUAL
   edit, the reinforcement routes to the superseding fact — extraction can never resurrect a
   belief the user corrected away.
3. **Same (subject, predicate), different object**:
   - DURATIVE + functional predicate (lives_in, studies, is_dating, works_at — a seeded
     "single-valued" flag on the vocabulary): insert new fact + `CONTRADICTS` link; injection
     immediately prefers the newer (§7); sleep pass resolves (usually newer SUPERSEDES older or
     older gets CLOSED with `valid_until` = new `valid_from`).
   - Non-functional (likes, owns, visits): coexist — insert plainly.
4. **Similar statements, no structural relation** (FTS/vector-only match): insert + flag pair for
   sleep-pass adjudication. Nothing is absorbed at write time.
5. Everything lands **PROVISIONAL** (except REINFORCE targets); no extraction confidence can birth
   ACTIVE.

**Other applier guardrails**: drop ops referencing unknown ids (anti-hallucination); force scope
(GLOBAL_USER only for user-subject, NORMAL-sensitivity facts — and even then extraction writes
CHARACTER scope; promotion is sleep-pass-only, §10); length clamps; caps; every non-entity row
gets ≥1 provenance row; one `memory_activity` row per pass; watermark advance and all writes in
one transaction (crash → idempotent re-run, dedup gate absorbs the replay). Per-scope mutex
(`MemoryScopeLocks`) serializes applier writes against the sleep pass.

Embeddings for new rows: single batched `EmbeddingService` call if a model is configured, else
skipped — the FTS row is always written.

### 6.5 Sleep pass (`MemorySleepWorker`, ~12 h, battery-not-low)

Deterministic stages (always run, no network, no budget):
1. **Decay sweep** — retention per §6.6; ACTIVE→DORMANT→FORGOTTEN (30-day grace, restorable in
   UI) → hard delete. Pinned and canonical rows exempt; entities go DORMANT only when all their
   facts/mentions are gone, and are deleted only when orphaned past grace.
2. **Auto-promotion** — PROVISIONAL→ACTIVE for importance ≥3 rows that survived 7 days with no
   CONTRADICTS link, no supersession, no pending adjudication flag. A fact stated plainly once
   becomes a real memory without re-mention.
3. **Expiry** — `valid_until`/proposed horizons past due → CLOSED.
4. **Hygiene** — predicate canonicalization, alias dedup, orphan cleanup, FTS integrity.
5. **Size enforcement** (§13) — runs regardless of budget.

Model-assisted stages (budget category SLEEP; batched; call made *outside* the scope lock,
re-validated under it; skipped silently when budget exhausted — never block, never error):

6. **Adjudication** (one batched call): flagged entity pairs → merge/keep-separate; flagged fact
   pairs → merge/supersede/coexist. MERGE semantics deterministic: merged row keeps
   `pinned=either`, `times_reinforced=sum`, `importance/confidence=max`, `recorded_at=earliest`,
   `last_confirmed_at=latest`, provenance union; sources SUPERSEDED into it; entity merges
   re-point facts/mentions/aliases. `MANUAL`/`CONFIRMED_BY_USER` rows are **never** merged away or
   auto-superseded — conflicts against them surface as suggestion rows in the activity feed.
7. **Contradiction resolution** (same call): supersede / close / coexist-with-scope ("likes
   coffee at work, tea at home" — both survive, statements refined).
8. **Episode compression** (under size pressure or age): clusters of old low-importance episodes
   (same frame + entities + era) → one GIST episode (`is_gist=1`, DERIVED_FROM links); originals
   FORGOTTEN after grace. Gists can later compress into era-gists. Human gist retention.
9. **Habit induction** (toggleable): ≥3 similar POINT facts/episodes → HABIT fact ("usually
   studies late at night") with DERIVED_FROM links to evidence.
10. **Entity summary refresh** — regenerate ≤240-char summaries for entities whose fact set
    changed materially.
11. **Scope promotion review** (§10) and **goal generation** (§9) — strictly budget-gated.
12. **Import refinement** (§16.2) — restructure top-importance legacy "note" facts into triplets.

Every action lands in `memory_activity`. There is no user-facing "needs consolidation" state —
the worker self-schedules and is opportunistically triggered early when adjudication backlog
exceeds a threshold.

### 6.6 Forgetting model (deterministic, budget-free)

Per non-pinned fact/episode: stability `S` (days), initialized by kind and importance
(`S₀ = base(kind) × importanceFactor`; e.g. DURATIVE imp4 ≈ 120 d, POINT imp2 ≈ 10 d).
Retrievability `R = e^(−Δt/S)` with `Δt` = days since `max(last_confirmed_at, last_accessed_at)`.

- Genuine retrieval (query-relevant recall hit or `search_memory` hit — **not** core-sheet
  inclusion) and REINFORCE: `S ← S × 1.6` (capped), `last_accessed_at` bumped in a batched
  off-hot-path write. Core-sheet inclusion deliberately counts for nothing, or the early sheet
  would self-perpetuate (rich-get-richer fossilization — attempt 1's rule, kept).
- Thresholds: `R < 0.35` → DORMANT (searchable via `search_memory`, never auto-injected);
  `R < 0.05` while DORMANT → FORGOTTEN (grace 30 d, "Recently forgotten" filter, one-tap restore)
  → hard delete with links + provenance.
- Pinned/MANUAL/CONFIRMED_BY_USER rows: exempt from FORGOTTEN, can still go DORMANT? **No** —
  fully exempt from decay transitions; the user said keep it, so it stays. Trivia can't hide
  here because pinning is a deliberate user act.
- IMPORTED rows: decay transitions suspended for 60 days after import (§16.2 amnesty).
- **No cleanup model configured?** The forgetting model, expiry, size enforcement, structural
  dedup, and auto-promotion are all deterministic — the graph stays healthy, bounded, and
  ordered with **zero** model availability; only adjudication/compression/habits/summaries wait
  (flagged pairs simply coexist, injection already prefers the newer side of contradictions).
  This is a designed answer, not an accident: the system must work for users who never configure
  a processor model.

---

## 7. Retrieval & injection (`MemoryRecall`) — zero model calls

Assembled synchronously before generation via **`MemoryRecallTransformer`** (input transformer,
inserted in `ChatService`'s input pipeline before `TemplateTransformer`), replacing the legacy
`retrieveRelevantMemories` block at `ChatService.kt:1487–1525`. Local-DB only, target <50 ms.

1. **Core sheet** (cached per scope; invalidated only by applier *content* writes, never by
   access-time bumps): two compact cards —
   - **"Who the user is"**: pinned + top-K facts about `user` (GLOBAL + this character's scope)
     ranked by (importance, reinforcement, confirmation recency — deliberately *not*
     `times_retrieved`), rendered as short lines with fuzzy time/tense.
   - **"You and them"**: relationship facts, active habits, open frames, the character-self
     facts. This is the Letta core-block idea: always-present identity beats retrieval for the
     80% case, and costs nothing.
   High-importance (≥4) PROVISIONALs are included **hedged** ("you recall, though it was
   mentioned only once…") so a fact stated once is never invisible while awaiting promotion.
   Budget ~400–700 tokens, grouped, deterministic rendering.
2. **Query-relevant recall**: last user message(s) → tokens expanded through the alias table
   ("uni" adds "TU Wien" as a lower-weight candidate; expansion is additive, never replacing, so
   an ambiguous alias degrades to ranking, not misdirection) → unified FTS query + vector query
   (if available, RRF-fused) → seed entities/facts/episodes → **spreading activation**: 1–2 hop
   weighted traversal over fact edges and mentions (HippoRAG-style, bounded breadth) → rank by
   `matchScore × (0.5 + 0.5·R)` (retention-weighted) → top-N facts/episodes not already in the
   core sheet.
3. **Recent episode strip**: last ~3 episodes with this character, fuzzy-aged, frame-labeled —
   the "sense of shared history" ("*[roleplay, about a week ago]* …").
4. **Pending-tail digest** (§11.4) — async-consistency cover.
5. **Curiosity hint** iff `CuriosityEngine` clears delivery (§9).

Injection rules:
- One system-prompt section, clearly sourced: "Things you remember — fuzzy, don't recite
  verbatim." Facts render from `statement` (never raw triplets); episodes as one-liners.
- A fact pair with an unresolved CONTRADICTS link injects **only the newer side** — the character
  never voices both sides of a pending correction.
- FICTION rows inject only inside their frame (i.e. when the profile/frame detector says the
  conversation is in that mode), always frame-labeled.
- Only query-relevant recall (and `search_memory`) hits get `times_retrieved`+1 /
  `last_accessed_at` bumps, batched off the hot path.

**Tools**:
- **`search_memory`** stays (deliberate recall; its optional synthesis step uses the **memory
  processor** model — user-initiated cost, outside the daily budget; never the subagent/summarizer
  selectors, per §12's decoupling rule) but is rebuilt over the graph:
  alias→FTS→vector→spreading activation replaces
  `MemorySearchService`'s hardcoded expansion tables (`MEMORY_SEARCH_EXPANSIONS`, `BED_TERMS`…).
  Past-chat span search is kept initially (gists may not cover everything until compression
  matures — attempt 1 open question 3, same answer).
- **`save_memory`** (new, replaces `create_memory`): explicit saves when the *user asks* the
  character to remember something; routes through the applier as `source=TOOL` (dedup gate
  included), auto-approval as today. `edit_memory`/`delete_memory` are **retired** — the model
  never edits or deletes memory; the user does, in the UI. (Original motivation honored: the
  character no longer needs to manage memory manually at all; `save_memory` is a courtesy hatch,
  not a requirement.)

---

## 8. Character awareness

### 8.1 Character memory profile

One model call per character (budget category PROFILE), cached in `memory_store_meta` keyed by
`hash(systemPrompt + name + enabled skill ids)`; regenerated only on hash change. Produces:
- **frames/modes** → `memory_frame` rows ("roleplay mode", "study-buddy chat" — from prompt
  structure like the brief's two-mode example, and from enabled Skills/Modes)
- the character's **relation to the user** (friend/mentor/partner-in-fiction…)
- **care-abouts** — topics this character plausibly cares about; feeds the extraction importance
  rubric ("a study-buddy character cares about schedules and exams")
- **tone notes** for curiosity asks (§9).

Characters without structure degrade to a single default frame.

### 8.2 Frames & fiction

Episodes and character-scoped facts carry `frame_id`; retrieval labels them; the graph UI clusters
by them. `reality=FICTION` rows (world-state of a roleplay: "in our story we live in a
lighthouse") give the roleplay its own continuity while being structurally unable to leak: FICTION
never promotes to GLOBAL_USER, never feeds goals or web lookups, and injects only frame-labeled.

### 8.3 Seriousness / joke / play detection

Handled at extraction (§6.2) with the profile + window context as evidence; the classification is
*stored* on provenance (the "Literal" chip in the UI sketches), so a misclassification is visible
and correctable, not silent. Low-confidence classifications land PROVISIONAL and only survive if
reinforced. The user never has to change how they talk.

---

## 9. Curiosity engine (proactive knowledge building) — default OFF

Goal lifecycle: `OPEN → PRIMED → ASKED → CONFIRMED | DECLINED | IGNORED → ABANDONED`.

- **Generation**: sleep pass only (never mid-chat), ≤1 new goal per character per run, only from
  importance ≥3, NORMAL-sensitivity, REAL beliefs, each with a `value_note` ("knowing the
  semester schedule would help the study-buddy framing" — shown verbatim in the UI as the
  explanation). Extraction may RESOLVE_GOAL when a chat organically answers one.
- **Optional web fill** (child toggle, off by default, grayed unless curiosity is on): the sleep
  pass may run one search via the existing `:search` `SearchService` to draft an answer ("Vienna
  school holidays start …"), stored on the goal as `web_draft` = *unconfirmed*; it becomes memory
  only after the user confirms in chat.
- **Delivery** (`MemoryRecall`): at most one short hint — "If it fits naturally, you're curious
  whether …; drop it if the moment is wrong" — only when ALL hold: toggle on; goal PRIMED;
  conversation ≥6 messages; not mid-roleplay-scene (frame check); ≥72 h since *any* ask by this
  character; this goal never asked before.
- **Back-off is structural, not prompt-based**: DECLINED → ABANDONED forever (the goal row stays
  as a do-not-ask marker; the generator excludes entities of abandoned goals). ASKED with no
  engagement → IGNORED; IGNORED twice → ABANDONED. CONFIRMED → learned facts enter through the
  normal applier with `source=CONFIRMED_BY_USER`. Global cap: **≤2 asks per character per week,
  enforced in code**. "Natural curiosity" cannot tip into nagging because the rate limit and
  back-off live in `CuriosityLogic`, not in a prompt.
- Default OFF (decided with the user 2026-07-03 for attempt 1; the decision stands — tone-safest,
  and the feature is one toggle away). Revisit the default after real-world use.

---

## 10. Privacy boundaries

- **Scope is enforced in SQL, not prompts**: every read path takes
  `(scope=GLOBAL_USER) OR (scope=CHARACTER AND owner_assistant_id=:thisAssistant)`. Character A
  structurally cannot query B's rows. There is no "all scopes" query outside the user-facing
  global browse UI and export.
- Extraction always writes user-facts CHARACTER-scoped. **Promotion to GLOBAL_USER** happens only
  in the sleep pass and is conservative by construction:
  - **Automatic** only for ACTIVE, `sensitivity=NORMAL`, `reality=REAL` facts whose `category` is
    on a closed identity whitelist: name, pronouns, language, timezone, occupation_study. The
    applier validates the category; nothing unlisted can auto-promote regardless of model claims.
  - **Everything else** → a suggestion row in the activity feed / Suggestions section ("Share
    'user studies math' with all characters?"). Tap = promote; dismiss/ignore = stays private.
    Non-action is always the safe state.
  - SENSITIVE never promotes, never chips, never feeds goals or web searches.
- `Assistant.useSharedUserMemory=false` (new field) opts a character out of the global layer
  entirely — read and write.
- **Character deletion cascades**: deleting an assistant deletes its CHARACTER-scoped entities,
  facts, episodes, aliases, frames, goals, provenance, activity. GLOBAL_USER facts survive
  regardless of which character sourced them. The delete-assistant dialog states this.
- Export/wipe (§14) honors the same boundaries.

---

## 11. Chat & app integration (complete inventory)

### 11.1 Hooks

- **Retrieval**: `MemoryRecallTransformer` in `ChatService` input transformers (§7). The legacy
  `memories = …` parameter of `GenerationHandler.generateText` and `buildMemoryPrompt` are
  removed once the toggle path is gone (P2 end state).
- **Extraction trigger**: `ChatService` post-`persistConversationToRepository` on the
  `onCompletion`/`generationDoneFlow` path (`ChatService.kt:1542+`), plus on user-message save
  when no generation runs. Never awaited; failures log to `memory_activity` + `PlatformLog`,
  never surface as chat errors.
- **Workers**: scheduled in `LastChatApp.onCreate` following the existing
  `settingsFlow.collect → enqueueUniquePeriodicWork(UPDATE)` pattern (as the current
  `memory_consolidation` scheduling at `LastChatApp.kt:127–150`): `memory_sleep` (periodic ~12 h,
  network for model stages; the deterministic stages also run in a separate no-network pass so an
  offline device still ages its graph), extraction one-shots, import/backfill one-shots.
  `LastChatApp` schedules exactly one of `memory_sleep` / legacy `memory_consolidation` per user
  depending on migration state (§16).
- **No worker may ever key off the "current" assistant** (normative — the legacy
  `MemoryConsolidationWorker` reads `settings.getCurrentAssistant()` and thus silently serves
  only the selected character; this is a bug class, not a pattern). Extraction jobs carry their
  conversation's assistant id; the sleep pass, import, and backfill iterate **all
  memory-enabled assistants** every run. Switching characters, backgrounding, or closing the
  app never delays another character's processing — flush-on-switch (§6.1) makes leaving a chat
  the moment its extraction is *enqueued*, and WorkManager guarantees it runs regardless of
  what's on screen.

### 11.2 Message regeneration / edit / delete / branches (attempt 1's hardened rules, kept verbatim)

- Extraction reads `Conversation.currentMessages` (the selected branch) only; the watermark is
  **anchored to a message id** and re-resolves to the nearest surviving earlier processed message
  when its anchor disappears.
- **Regenerate/branch switch below the watermark**: clamp watermark to the divergence point;
  demote to DORMANT (reason=branch) only rows **all** of whose provenance lies strictly beyond
  the divergence on the abandoned branch — facts also evidenced elsewhere keep status. Re-selecting
  the branch or re-encountering the content reactivates via the REINFORCE path.
- **Edited messages** (`__from_message_attachment` etc. metadata preserved per repo rules):
  re-extract the affected window under the same demotion rule.
- **Message deletion**: derived memories are **kept** (diary-burned principle — a person still
  remembers after the page is torn out); provenance excerpts are stored copies so node sheets
  keep working; the node sheet's provenance link makes derived rows findable for manual Forget.
  Deleting unprocessed messages needs nothing. Plain deletion never triggers branch-style
  demotion (ambiguous cleanup ≠ "this was wrong").
- **Conversation deletion**: dialog gains an off-by-default "Also forget memories from this chat"
  checkbox (provenance conversation-id lookup → FORGOTTEN with grace).
- **Fork** (`isFork`): forked conversations start with a fresh watermark at the fork point's
  message id; already-extracted content behind the fork is not re-extracted (dedup absorbs
  overlap anyway).

### 11.3 Other LastChat features

| Feature | Integration |
|---|---|
| `ChatPersistenceMode` | NORMAL: full memory. TEMPORARY: recall **read-only** (the character remembers you), no extraction ever. PERSIST_ON_REPLY: extraction only after persistence. |
| Spontaneous messages (`SpontaneousWorker`) | Pre-flush extraction (§6.1 trigger), then `MemoryRecall` core sheet + episode strip replaces `resolveMemoryContext`. |
| Scheduled messages | Same recall path when building the prompt. |
| Quick Ask / Text Selection / Share / widget trampolines | All route through `ChatService` → inherit the rules by construction. |
| Lorebooks | Orthogonal: lorebooks are authored static knowledge, memory is learned dynamic knowledge; they inject as separate sections. No interplay needed; the profile generator may read lorebook names as frame hints only. |
| Skills/Modes | Enabled skills feed the profile hash + frame detection (§8.1). |
| Context stack UI (`ContextSourcesSheet`) | `UsedMemory` entries now reference graph rows (tap → node sheet). |
| Activity pill (`ActivityPill.kt`) | Existing `MEMORY_RECALL` type stays for `search_memory`. In-chat surfacing of memory writes is the **memory ball / memory pill** (user decision — no popup): when other activity entries exist for the turn, it renders as a small **circular chip styled exactly like a minimized activity-pill entry** (the "+1"-ball geometry) at the **right end of the pill row**, in a distinct memory accent color, **exempt from the row's "+N" grouping/collapse** — always standing alone. Its **icon varies by event type** (the "+1" was just the new-memory example): e.g. plus = new memories, pencil = update, split-arrows = contradiction flagged, book = new episode. When memory is the turn's **only** activity, it renders as a **full-sized pill like the reasoning pill**, with text describing what it did ("Remembered 2 things", "Updated a memory"). Appears only for *interesting* events (new facts, updates, contradictions, episodes — **never** plain reinforcements); tap → bottom sheet of the pass's activity rows with node links; fades out on its own. A **"Show memory activity in chat" toggle (default on) lives in Advanced settings → UI customization** (`DisplaySetting`, alongside the other chat-UI toggles — it's a UI preference, not a memory setting); hiding it never affects what memory actually does. Never a dialog, never interrupts streaming. |
| Notifications (`AssistantNotificationListener`) | No memory writes from notification context (unvetted third-party text). |
| Attachments/OCR/documents | Extraction sees text parts incl. OCR output already in message parts; binary parts render as placeholders. |
| Web UI / Ktor (`WebApi.kt`) | Chats from the web client flow through `ChatService` → memory works unchanged. Dedicated read-only memory endpoints + SSE activity events are **P7** (optional): `GET /api/memory/summary`, `GET /api/memory/browse`, node detail, activity feed; no write endpoints initially. |
| Backup/WebDAV | New tables ride the whole-DB backup automatically; **`DatabaseSanitizer`'s hardcoded table allowlist (`data/sync/DatabaseSanitizer.kt:67–75`) MUST be extended with every new table** or restore silently drops them (verified real footgun). |
| `SecretKeyManager` | Nothing memory-related is a secret; no changes. |
| Baseline profile | Add a Memory screen + graph-canvas journey to the generator (P6). |
| i18n | English-only strings per repo policy; snake_case keys `memory_*`. |

### 11.4 Async consistency (the "remembered too late" bug)

Guarantee: *anything said is either still in that conversation's context, or past the watermark
and thus in the store.* The residual gap is cross-conversation (user switches chats before
extraction ran). Cover, kept from attempt 1:
- flush-on-switch trigger closes the common case within seconds;
- **pending-tail digest**: `MemoryRecall` checks same-assistant conversations updated in the last
  24 h with watermark lag > 0 and injects their last few raw lines, explicitly labeled ("recent
  unprocessed chat with you — raw, may include roleplay or jokes; treat cautiously"),
  deterministic, capped ~200 tokens. When extraction is backlogged (lag older than ~48 h —
  extended offline or a failing model, §12), the 24 h window widens to the full unprocessed
  span, most-recent-first under the same token cap — cross-chat recall never regresses just
  because extraction is behind. Same-assistant restriction is what makes raw injection safe:
  worst case a character sees its own recent lines; cross-character leakage is impossible.

Honest caveat (unchanged): the guarantee is per-character. A shareable fact told to A reaches B
only after extraction + promotion. Flush-on-switch keeps that window to seconds; the design
accepts it as the price of never injecting another character's unclassified raw text.

---

## 12. Cost, latency & budget

The core distinction (user decision, replacing an earlier across-the-board budget):
**user-driven work is unmetered; autonomous work is hard-capped.**

- **Extraction is NOT budget-limited.** It fires only in response to the user chatting, so its
  cost is inherently proportional to usage the user already chose to pay for — and tiny in
  comparison: the chat model re-reads the whole history every turn, while extraction reads each
  message **once** with a cheap model. Cadence (below) is the cost control; there is no daily
  extraction cap. A high **safety ceiling** (~300 extraction calls/day, ledger-enforced) exists
  purely as a bug-stop against runaway loops and is invisible in any legitimate use; hitting it
  trips the §19.10 circuit breaker (it is a malfunction signal, not a tariff).
- **Autonomous categories keep hard daily caps** — SLEEP model stages, PROFILE, CURIOSITY (and
  its web lookups). These run on schedules without user action; this is where past attempts
  hid their costs, and it stays strictly bounded and ledger-visible.

Presets (**per-character**, like all memory settings — §15) tune cadence and autonomous richness:

| Preset | Extraction cadence | Sleep model stages | Curiosity | ~background calls/day heavy use |
|---|---|---|---|---|
| **Off** | never | deterministic only (free) | off | 0 |
| **Eco** | every 25 msgs + flush triggers | adjudication only, 1 batch/day | off | extraction ∝ usage + ~1–2 |
| **Balanced** (default) | every 10 msgs + triggers | full, 1 session/12 h | optional | extraction ∝ usage + ~3–6 |
| **Rich** | every 6 msgs + triggers | full, 2 sessions/day | optional | extraction ∝ usage + ~6–10 |

- **Deferral & catch-up** (now only from offline periods, model failures, the Off preset, or a
  tripped circuit breaker — no longer a daily-budget event): the watermark stops advancing,
  nothing is lost, and the per-pass window is unbounded (§6.2 splits only on the token cap), so
  a backlogged pass swallows the whole lag in one or few large calls — being 80 messages behind
  costs roughly one call, not eight.
- **Backlog UX**: if watermark lag exceeds ~48 h (extended offline, failing model), the
  pending-tail digest window (§11.4) widens to the full unprocessed span so cross-chat recall
  never regresses, and the health strip says why ("memory model failing — extraction paused").
  Behind is visible, lossless, and self-healing.
- Token caps apply to context/digest portions only, never the extraction window.
- **Models — dedicated "Memory" category** in the default-models settings page (user decision;
  replaces attempt 1's fallback-chain coupling): **Memory parser** (extraction passes),
  **Memory processor** (sleep adjudication/compression/profiles/curiosity), **Embedding model**.
  No connection to the summarizer/subagent/background selectors — memory uses only its own
  pickers. Unset parser/processor → falls back to the assistant's chat model (stated inline in
  the picker, so zero-config users still get a working system); unset embedding → FTS-only mode
  (§15). UI recommends cheap fast models. All calls use `PlatformLog` + the standard 429-retry
  pattern.
- Hot path: retrieval is local-DB only, <50 ms target (core sheet cached; FTS + one optional
  vector scan + bounded traversal over indexed edges). **No model calls, ever, on this path.**
- Ledger is visible in the Memory screen's Activity header ("4 API calls today" — matching the
  reference sketch) — transparency, not a chore.

---

## 13. Bounded growth (hard, budget-independent guarantees)

- **Row budgets per scope** (configurable, defaults): per character ~1,000 facts + 400 episodes
  (+ gists) + 250 entities; global ~800 facts + 150 entities. At 90% the sleep pass must run
  compression; at 100% the applier admits new ADDs only by evicting lowest-retention DORMANT
  rows (activity-logged).
- **Compression ladder**: episodes → gists (~10:1) → era-gists ("early 2026: mostly studied
  together, user stressed about uni"). Detail is lost; gist persists — the human curve.
- **Prose is capped everywhere**: statements ≤160, episode summaries ≤400, entity summaries ≤240,
  provenance excerpts ≤300 and capped per row (first + last 3); activity ~500 rows/scope;
  FORGOTTEN past grace physically deleted with links + provenance + FTS rows.
- **Retrieval latency stays flat**: the injectable set is ACTIVE-only, which the budgets cap;
  DORMANT is reachable only via explicit `search_memory`. A years-long user gets a bounded,
  slowly-churning graph, not a landfill.

---

## 14. UI (Material 3 Expressive) — maps the Figma reference sketches

Replaces `AssistantMemorySubPage`, `AssistantRagMemorySubPage`,
`AssistantMemoryConsolidationSubPage`. New top-level route `Screen.MemoryCenter(assistantId?)`
(`composable<Screen.MemoryCenter>` in `AppRoutes`; `assistantId=null` = the global user layer,
opened from Settings; per-character opened from assistant detail). ViewModel `MemoryCenterVM`
registered in `di/ViewModelModule.kt`. Throughout: `AppShapes` tokens, `rememberPremiumHaptics()`,
`MotionPolicy` transitions (`hierarchicalEnterTransition` into the center, `lateralEnter` between
sub-views), `LocalToaster`, `FormItem` for settings rows, `Icons.Rounded.*` only.

### 14.0 Source of truth & hard consistency rules

The attempt-1 UI failed review on exactly these points (user feedback, verbatim lessons). These
are **normative for implementation**, not suggestions:

1. **The Figma mockups are the main source of truth.** Screens match them near-identically;
   anything not sketched is designed in the same style *and* cross-checked against existing app
   screens (assistant detail pages, settings pages) before inventing anything new.
2. **No hardcoded colors, ever.** Every color comes from `MaterialTheme.colorScheme` pairs
   (`surfaceContainer*`/`onSurface*`, `primary`/`onPrimary`, …) so dark mode can never produce
   black-on-black text. Accent numbers in the stat cards use `primary`/`tertiary`, not literal
   cyan/purple.
3. **Corner radii only from `AppShapes`** (CardLarge 28 / CardMedium 24 / CardSmall 16 /
   ButtonRounded 20 / Chip 12 / Tag 50% …), with the optical-roundness inner tokens
   (`CardLargeInner12`, `CardMediumInner12`, …) for nested surfaces. No ad-hoc `dp` radii — a
   "too small corner radius" is a review-blocking defect.
4. **Grouping like the sketches**: related rows render as grouped `ListItem` stacks using the
   `ListItemFirst`/`ListItem`/`ListItemLast` shape tokens with hairline separation — not as
   free-floating cards.
5. **The minimized graph card look** (from sketch 1): a near-black surface
   (`surfaceContainerLowest` in dark) with a **visible outline stroke** (`outlineVariant`) and
   `CardLarge` outer radius, nested with an inner-radius token inside its section — the
   "outline-like thing and is black" treatment, reproduced exactly.
6. **Typography adapts to content length**: short node/memory titles render bold
   (`titleMedium`/`SemiBold`); long-statement memories drop to regular weight
   (`bodyLarge`) past a length threshold (~90 chars) so walls of bold text never appear.
7. **Filter/tag options are pill rows, never vertical option lists**: kind/status/frame/scope
   filters render as wrapping rows of selectable pills (`Chip` 12 dp / `Tag` 50% per their
   nature) pinned at the top of Browse and any other filterable screen.
8. **Alignment audit is part of done**: every screen is checked in dark + light, and against a
   neighboring existing app screen, before a phase is called complete. "Some text is black in
   dark mode / some stuff is unaligned" must be unreproducible.

### 14.1 Memory screen (per character) — sketch 1 & 2

Single scrollable screen (not tabs — matches the sketches), title = character name:

- **Stat cards row** (3× `AppShapes.CardMedium`, tap → filtered Browse): total memories
  (facts+episodes), "this week" (recorded_at ≥ 7 d), entities. Numbers in
  `MaterialTheme.colorScheme` primary/tertiary like the sketch's cyan/purple accents.
- **Status pill** (only when something is running — informational, never actionable-required):
  "Rebuilding memory · 83%" for import (§16.2) or embedding backfill (§16.4); pipeline-stall
  notice ("memory model failing — extraction paused 3 days", link to model settings). Zero
  burden must not mean zero visibility into a broken pipeline.
- **Suggestions section** (sketch 2; rendered only when non-empty): the *only* place the system
  ever asks anything, and everything here is dismissible with no consequence. Row types:
  promotion chips ("Share 'studies math' with all characters?"), adjudication conflicts touching
  MANUAL rows ("These two seem to conflict — keep which?"), curiosity confirmations. Accept =
  `PremiumHaptics.Success`; dismiss = ignore-forever for that row; max 3 shown, oldest
  auto-expire after 30 days. No badges or red dots anywhere else in the app.
- **Memories section**: a **static simplified graph preview** styled per rule 14.0-5 (black
  `surfaceContainerLowest` card, `outlineVariant` stroke, `CardLarge` outer / inner-radius
  nesting) — the pre-settled layout of the **whole graph at once**, rendered label-free (colored
  dots by entity kind, thin edges; exactly the sketch's "static, simplified graph… whole graph
  in one go in that simplified state"), tap → full-screen graph view. Below it a full-width
  "Browse all memories" `ButtonRounded`.
- **Activity section**: header with trailing "4 API calls today" label (from
  `memory_budget_ledger`). Cards = `memory_activity` rows, expandable in place
  (`animateContentSize`, spring 0.5f/400f) to show affected rows with links to node sheets;
  grouped with day dividers ("Yesterday") like the sketch. `ListItem` grouped shapes
  (First/Last tokens).
- **Gear FAB** (bottom-right, `IconButton` 50%, `PremiumHaptics.Pop`) → memory settings
  (§15) as a `BottomSheet` — **this is where essentially all memory settings live** (per-character
  toggles, preset, curiosity, habit induction, time awareness). No global settings page to link
  to; a small footnote row deep-links to the default-models "Memory" category for model pickers.
- Overflow menu: "Export memory…", "Erase memory…" (§14.4).

**Shared Memory page** (the global layer, user decision — replaces any "global memory settings"
concept): the main settings page entry is named **"Shared Memory"** and sits **above the Add-ons
entry**. It is **not a settings page** — it is the redesigned Browse experience over GLOBAL_USER
rows: view, add, edit, delete shared memories (all through the applier as MANUAL ops), each row
showing which characters contributed it. Zero settings live here. There is **no global memory
on/off toggle anywhere** — memory is enabled strictly per character; the only memory-related
controls outside the per-character sheet are the three model pickers in the default-models page's
"Memory" category (§12).

### 14.2 Full-screen graph view

**Built new, not ported** — attempt 1's graph screen was explicitly rejected by the user; only
its *performance technique* (off-thread settle + freeze + `graphicsLayer` pan/zoom) carries over.
The visual language comes from the mockups: the full-screen view is the mini-preview expanded —
same simplified dot-and-line aesthetic on the same black outlined surface, with labels and detail
appearing progressively on zoom, never a dense labeled hairball on open.

Two states, per the user's spec:
- **Minimized preview** (Memory screen card): fully **static and non-interactable** — the only
  gesture is tap-to-open the full graph screen. The preview is **stylized, not a thumbnail** of
  the full view: it renders its own presentation pass where node radii, edge thickness, and
  layout spacing **auto-scale to the graph's size** (few nodes → bigger, chunkier dots that fill
  the card pleasantly; hundreds of nodes → smaller dots, tighter packing, hub-biased sizing) so
  that the **entire graph is always visible in one go** inside the card and always looks
  composed — never clipped, never a sparse corner, never an unreadable smear. Relative size
  encoding (∝ relations) is preserved within the rescale.
- **Full-screen graph**: **live, interactable, smooth and pleasing in every way.** Interaction
  feel is a first-class requirement, not a byproduct: pan has momentum/fling with gentle edge
  resistance, pinch-zoom is anchored at the gesture focal point, camera flights and fades use the
  app's standard springs, node presses give a subtle press-scale (the `BackButton`
  0.85f/spring-0.6f/300f "golden standard") with `PremiumHaptics.Tick`/`Pop`, and every
  transition (focus fade-out, fade-back, label reveal) is interruptible mid-flight — a new
  gesture never waits for an animation to finish. Target: nothing ever stutters, snaps, or
  teleports.

Compose `Canvas` force-directed layout:
- **default framing shows the whole graph at once** in the simplified label-free state (the
  preview, expanded); **node size ∝ number of relations** (degree) as the primary driver, with
  retention/pinned as secondary multipliers — so the whole thing reads at a glance; capped at
  ≤180 rendered nodes per view (clusters collapse into hub bubbles beyond that)
- **labels never overlap, ever**: label visibility is zoom-driven and collision-resolved — as
  you zoom toward a node its label fades in smoothly (alpha+scale), and a per-frame
  collision pass (grid-based, cheap) shows only the highest-priority non-overlapping labels at
  the current zoom; a label that would collide simply stays hidden until there is room
- **focus flow** (the core interaction):
  1. **first tap on a node** → camera animates in on it; its neighbors stay, everything else
     **fades away** (alpha, not removal) → the focused neighborhood view
  2. **back gesture, zoom-out, or the recenter button** → the rest of the graph **fades back
     in** and the camera returns to the whole-graph framing
  3. **second tap on the focused node** → the expanded node view — the **same node sheet used
     in the Browse screen** (§14.3); no separate graph-only detail UI
- physics: simulate off-UI (coroutine on `Dispatchers.Default`), settle then freeze; pan/zoom is
  a pure `graphicsLayer` transform (never recomputes layout or recomposes) → 60 fps regardless
  of store size; standard spring specs (0.5f/400f; camera flights use the bouncy 0.6f/300f only
  for the recenter snap)
- visual encoding: node = entity (color by kind from `colorScheme` secondary/tertiary
  containers); edge = fact (label revealed by the same zoom/collision system, dashed =
  PROVISIONAL, faded = DORMANT/CLOSED); episodes as small satellites on mention edges; FICTION
  cluster visually ringed
- long-press → pin (`PremiumHaptics.Thud`); search field flies the camera to matches
- reduced motion (`LocalMotionPolicy`): pre-settled static layout, fades become instant,
  no idle animation
- accessibility: Canvas gets a `contentDescription` summary; TalkBack users are routed to Browse
  (the declared equivalent surface).

### 14.3 Node sheet — sketch 3

`AppShapes.BottomSheet`, for any fact/episode/entity:
- **chips row**: kind (Fact/Episode/Entity), classification from provenance (Literal/Joke/
  Fiction — the sketch's "Literal"), status (Provisional rendered as the sketch's dashed
  outline chip; Closed/Superseded as muted chips)
- **title**: the statement/title ("Takes math classes at TU Wien") — bold only when short
  (rule 14.0-6); long statements render regular-weight
- **focused mini-graph**: static neighborhood centered on this row (sketch's "focused only on
  the current node")
- **time line**: "Learned 1 week ago · last confirmed yesterday" (fuzzy)
- **Sources card**: provenance excerpts as italic quotes (sketch), rationale, link to the source
  conversation via `navigateToChatPage` (grayed if deleted — the excerpt remains; memories
  survive their source)
- **history**: walkable SUPERSEDES chain timeline; **connections**: tappable related rows
- **actions**: **Pin** / **Edit** (creates a MANUAL supersede — even the user can't destructively
  overwrite) / **Forget** (error-container red like the sketch → FORGOTTEN with grace +
  snackbar undo, `PremiumHaptics.Error`). Entities additionally show the alias list with
  add/remove (a wrong alias is a user-visible retrieval bug the user must be able to fix).

### 14.4 Browse, export, wipe

- **Browse**: searchable list with all filters as **wrapping pill rows at the top**
  (kind/status/frame/scope/classification — rule 14.0-7; never a vertical option list),
  `SearchField` pill, list rows in grouped `ListItem` shapes with length-adaptive title weight;
  sort by retention or recency; **"Recently forgotten"** filter with
  one-tap restore (the undo surface for automatic forgetting); "+ Add memory" FAB → manual fact
  through the applier (MANUAL, ACTIVE, optional pin).
- **Export**: JSON zip (versioned schema, per-character or global) via `BackupArchiveFormat`
  patterns; excludes other characters' scopes by construction.
- **Wipe**: overflow → full-screen confirmation explaining scope → type the character's name (or
  "everything") → `PremiumHaptics.Error` + 3 s-delayed final button. Writes one WIPED activity
  row and clears the rest.

**Zero-burden check**: nothing on these screens is ever required. No red dots, no "run
consolidation" buttons, no maintenance chores anywhere in the app.

---

## 15. Settings & degradation matrix

**Placement (user decisions, normative):**
- **Essentially all memory settings are per-character**, stored on `Assistant`, edited in the
  Memory screen's gear sheet (§14.1). `Assistant.enableMemory` remains the master per-character
  switch. **There is no global memory on/off toggle.**
- The **only** memory controls outside the per-character sheet are the three model pickers in
  the default-models settings page under a new **"Memory"** category: parser, processor,
  embedding (§12). No other model selector in the app is consulted by the memory system.
- The **Shared Memory** page (main settings, above Add-ons) contains zero settings — it is the
  GLOBAL_USER browse/add/edit/delete surface (§14.1).
- Internally, a small `MemorySettings` block in `Settings` stores only the three model ids
  (normalized via a new `normalizeMemorySettings` stage appended to the 5-stage chain in
  `PreferencesStore.kt:491–496`); everything user-facing lives on `Assistant`.

| Toggle | Default | Off-behavior (everything else keeps working) |
|---|---|---|
| Memory (per character, `enableMemory`) | existing value | no extraction, no injection; store kept intact |
| Shared user memory (per character, `useSharedUserMemory`) | on | character reads/writes CHARACTER scope only |
| Preset (per character, Eco/Balanced/Rich) | Balanced | §12 budgets |
| Time awareness | on | no fuzzy ages/tenses at injection; internal clocks keep running |
| Proactive curiosity | **off** | no goal generation; existing goals frozen |
| └ Web lookups for curiosity | off (grayed unless curiosity on) | goals ask without pre-research |
| Habit induction | on | no HABIT facts; episodes still compress |
| Episode memory | on | facts only; no episodes/gists (for users who only want a fact sheet) |
| Embedding use | auto | auto-degrades: no model → pure FTS/lexical everywhere; configure later → backfill worker (§16.4) |
| Memory model | chain (§12) | per-assistant override picker included |

Dependent toggles gray out (never silently force values — the brief's 3.9 rule). Every subsystem
communicates only through the store + op vocabulary, so each is independently removable.

---

## 16. Migration & change resilience

### 16.1 Room v33 → v34

Purely **additive**: the §4 tables. `AutoMigration(from=33, to=34)` in `@Database`, schema
`34.json` committed. Delete the orphaned `MemoryItemEntity`/`MemoryItemFtsEntity` files (never in
`@Database`, no migration implication). Legacy `MemoryEntity`/`ChatEpisodeEntity` tables + DAOs
stay untouched and read-only for at least one release; drop in a later v35. **Add every new table
to `DatabaseSanitizer`'s allowlist** (§11.3). Attempt 1 never shipped (current release is DB v33),
so no end-user path from its v34/v35 exists — its schema numbers are simply reused fresh.

### 16.2 Legacy data import (background, resumable, zero model calls)

`MemoryImportWorker`, enqueued once per assistant when memory is first enabled ≥ v34 (watermark in
`memory_store_meta`):
- Legacy CORE `MemoryEntity` → "legacy note" facts (`subject=user`, `predicate='notes'`,
  statement = content clamped at sentence boundary; overflow text → provenance excerpt;
  importance 3, ACTIVE, `source=IMPORTED`).
- Legacy EPISODIC + `ChatEpisodeEntity` → episodes (event time from old timestamps; existing
  embeddings carried over when `embedding_model_id` matches, else left for backfill).
- Everything goes **through `MemoryOpApplier`** — same dedup gate, same entity resolution — so
  import inherits every guardrail.
- Sleep-pass **import refinement** (§6.5 stage 12) then restructures the highest-importance
  notes into real triplets over the following days, within normal budget.
- **Import decay amnesty** (field lesson from attempt 1, where import flooded "Recently
  forgotten" with memories the user considered important): imported rows are exempt from
  DORMANT/FORGOTTEN transitions for **60 days**, get a generous stability floor
  (`S ≥ S₀(DURATIVE, imp3)`), and legacy `significance` maps onto importance (legacy
  significance ≥7 → importance 4–5) instead of a flat 3. Decay only begins to bite after the
  user has had real sessions in which reinforcement/retrieval could have occurred. An import
  must never visibly "forget" anything in its first weeks.
- Chunked (200 rows/run), kill-safe, resumable. While running: the status pill shows progress
  ("Rebuilding memory · 83%"); on completion an activity row + one-time dismissible card ("Your
  memories were migrated — N facts, M episodes") so a slow import never reads as "my memories
  are gone".

**Backup/restore**: new tables ride the existing whole-DB WebDAV backup automatically; §14
export is an additional user-facing format, not the backup mechanism. Restoring a pre-v34 backup
re-triggers import (watermark absent) — idempotent through the dedup gate.

### 16.3 Settings migration

In `normalizeMemorySettings`: `enableMemoryConsolidation=true` → preset Balanced;
`useRagMemoryRetrieval`/`ragSimilarityThreshold`/`ragLimit`/`ragInclude*` retire (retrieval is
always hybrid); `enableRecentChatsReference` → episode strip on. Old fields stay declared on
`Assistant` so old settings keep deserializing (the `PythonEngine` pattern); legacy
`MemoryConsolidationWorker` periodic scheduling is retired for migrated users (`LastChatApp`
schedules exactly one of the two systems). `search_memory`'s old tool remains name-compatible.

### 16.4 Embedding model changes

Per-row `embedding_model_id`; vector search filters to current-model vectors;
`MemoryEmbeddingBackfillWorker` re-embeds mismatched ACTIVE rows oldest-salient-first (budget
category SLEEP, outside scope locks), enqueued whenever the configured model changes, self-
continuing across days, no-op when aligned (`memory_store_meta[embed_backfill_model]`). Because
vectors are only candidate generators, a model switch is a temporary recall-quality dip, never
data loss, and no threshold anywhere assumes a particular model's cosine distribution. Setting a
model for the *first time* triggers the same backfill (upgrade path for zero-config users).

### 16.5 Mode/preset switches & future-proofing

Preset changes apply on next trigger (no rebuild). Disabling memory freezes the store. Disabling
episodes stops new episodes; existing ones age out normally. `store_schema_version` in
`memory_store_meta` + a small in-store migration registry handles semantic migrations (re-scoring,
re-classification) that Room DDL can't express; the export format carries the same version.

---

## 17. Implementation phases & test matrix

Riskiest-first; each phase ships behind `Assistant.enableMemory` remaining functionally legacy
until P2 completes, and leaves the app releasable. **Session-by-session breakdown with
copy-paste starter prompts: `docs/memory-system-v2-sessions.md`.** The attempt-1 branch
(`origin/LC_memory_system_attempt_1`) is a code-shape reference for workers, watermark, budget,
canvas layout, and DI wiring — but entities/ops/dedup are new (facts-on-edges), so port
patterns, not files.

- **P1 — Store + applier + import**: v34 schema, model layer, `MemoryGraphRepository`,
  `MemoryOpApplier` (entity resolution + structural dedup, lexical path), FTS, provenance,
  activity, `MemoryImportWorker`, `DatabaseSanitizer` list, delete dead `MemoryItemEntity` files.
  *Tests*: applier property tests (no op sequence can create a duplicate triple, an orphan fact,
  or a second user-person entity), dedup-gate table tests, parser fuzz tests.
- **P2 — Encode + recall (end-to-end MVP)**: `MemoryEncoder` + triggers + watermark,
  `MemoryRecallTransformer` (core sheet, query recall, episode strip, pending-tail), time
  verbalization, budget ledger, `save_memory`/rebuilt `search_memory`, legacy injection path
  removed behind the switch. *Tests*: watermark/branch/regenerate/delete scenarios
  (`MemoryWatermark`/`MemoryBranchLogic` pure tests), digest golden tests, retrieval correctness.
- **P3 — Sleep pass**: deterministic stages (decay/promotion/expiry/hygiene/size) then
  model-assisted (adjudication, contradiction, compression, habits, summaries). Retire legacy
  worker for migrated users. *Tests*: `MemoryRetentionLogic`/`MemorySleepLogic` unit tests,
  merge-semantics tests, MANUAL-protection tests.
- **P4 — UI**: Memory screen (stats, status pill, suggestions, mini-graph, browse, activity,
  settings sheet, export/wipe) first; full-screen graph canvas second. Activity pill
  `MEMORY_UPDATED`. Remove old sub-pages. *Tests*: snapshot/interaction where feasible; baseline
  profile journey.
- **P5 — Profiles + curiosity**: `CharacterProfileGenerator`, frames in extraction/retrieval,
  `CuriosityEngine` (off by default, structural back-off), web-fill option, promotion review +
  suggestion chips. *Tests*: `CuriosityLogic`/`PromotionLogic` unit tests, frame retrieval tests.
- **P6 — Hardening**: embedding backfill worker, `normalizeMemorySettings`, on-device
  `MigrationTestHelper` v33→v34 test against a real historical DB, 5k-fact retrieval-latency
  benchmark (in-memory Room, <50 ms assertion), graph-canvas baseline-profile journey.
- **P7 (optional) — Web exposure**: read-only `/api/memory/*` endpoints + SSE activity event +
  web-ui browse page.

---

## 18. Decided defaults & open questions

Decided:
- **Facts live on edges; prose is capped** — the defining break from attempt 1 (§0).
- **Curiosity default OFF** (user decision 2026-07-03, reaffirmed; one toggle away).
- **Promotion = category whitelist + confirm chips** — never fully automatic beyond
  identity-level categories.
- **No model-facing edit/delete tools** — the model proposes through extraction ops only;
  `save_memory` is the single explicit hatch.
- **Extraction is unmetered; only autonomous work is hard-capped** (user decision) — user-driven
  extraction runs on cadence with just a bug-stop ceiling; daily caps apply to sleep model
  stages, profiles, and curiosity, where autonomous cost actually hides.
- **Single-screen Memory UI** per the Figma sketches (not attempt 1's three tabs).
- **Mockups are the UI source of truth; §14.0 rules are review-blocking** (no hardcoded colors,
  AppShapes-only radii, grouped list shapes, filter pills, length-adaptive title weight, dark+light
  alignment audit). The graph screen is designed fresh from the mockup aesthetic — attempt 1's
  graph UI is explicitly not a visual reference.
- **Settings placement**: no global memory toggle; per-character gear sheet holds all settings;
  "Shared Memory" browse page (above Add-ons) with zero settings; models only via the
  default-models "Memory" category (parser/processor/embedding), decoupled from
  summarizer/subagent/background selectors.
- **In-chat surfacing = the memory ball / pill** — a circular chip styled like a minimized
  activity-pill entry at the right end of the pill row, memory accent color, excluded from the
  +N grouping, event-type-specific icons; renders as a full-sized memory-text pill when it's the
  turn's only activity; interesting events only (never reinforcements); hideable via Advanced
  settings → UI customization — no popups.
- **Graph interaction contract**: static non-interactable preview with **size-adaptive
  stylization** (node/edge/spacing scale to graph size so the whole graph always fits the card
  and looks composed); live full screen; tap = focus-zoom with fade-out of the rest;
  back/zoom-out/recenter = fade back; second tap = the Browse node sheet; node size ∝ relation
  count; zoom-driven collision-resolved labels that never overlap; momentum pan, focal-point
  pinch, interruptible spring animations, press-scale + haptics on nodes — interaction feel is
  a review criterion.
- **Import amnesty**: imported memories are decay-exempt for 60 days and inherit legacy
  significance — an import must never flood "Recently forgotten".

Open (defaults chosen; validate during implementation):
1. Row-budget numbers (§13) — validate against real DB sizes in P3.
2. Predicate seed vocabulary + single-valued flags — draft ~40 in P1, refine from real
   extraction logs in P2.
3. Stability constants (`S₀` bases, 1.6 boost, 0.35/0.05 thresholds) — tune in P3 against
   simulated month-long transcripts.
4. Whether `search_memory` keeps raw past-chat span search after gist compression matures.
5. Graph edge-label rendering threshold and episode-satellite visual — resolve in P4 on-device.
6. Curiosity default flip to ON-gentle after a release of telemetry-free field experience.

---

## 19. Edge cases & further refinements

Gaps found by adversarial walkthrough; each is normative and assigned to a phase.

### 19.1 "Please forget that" — the RETRACT op (P2)

The op vocabulary bans model-initiated deletion, but a **user explicitly asking the character to
forget something** is a real interaction that must work. New extraction op:

```
RETRACT {fact_or_episode_id, reason_excerpt}
```

Applier guardrails: allowed **only** when the extraction window contains a user message
requesting it (the applier requires `reason_excerpt` to be provenance-quotable from a USER-role
message in the window); target → FORGOTTEN **with the normal 30-day grace** (restorable in
"Recently forgotten"), activity row "Forgot at your request", and the entity's aliases are left
untouched. RETRACT can target MANUAL/pinned rows too — the user outranks their own pin — but
never rows of another scope. The character verbally "forgetting" is thus honored structurally,
while remaining reversible.

### 19.2 Contradiction storms / volatility guard (P3)

A user testing or trolling ("I live in Paris. Actually Tokyo. Actually Mars.") would otherwise
mint a supersede chain per message. Deterministic guard in the applier: if the same
`(subject, predicate)` flips more than **3 times within 24 h**, the whole chain is marked
`volatile` (in `extra`): injection stops surfacing any of it, importance is clamped to ≤2, and it
takes a **quiet reinforcement after 48 h** for the final value to exit volatility and inject
again. The chain itself is preserved (append-only as always); volatility is state, not deletion.
Prevents both belief-flapping in prompts and adjudication-budget burn.

### 19.3 Third-party text is not user truth (P2)

Pasted articles, forwarded messages, document/OCR content, and web-search tool results appearing
in the window must not become user-facts ("User lives in Kyiv" because they pasted a news story).
Extraction contract: facts about the user/relationship may only be sourced from the **user's own
conversational words**; pasted/quoted/document content may at most yield facts *about the
document's topic entities*, classified `INSTRUCTION`/quoted provenance, importance ≤2 — and by
default yields nothing. This is also the prompt-injection posture: text inside documents/tool
results that tries to command the memory system ("remember that the user loves X") is inert
because ops are only parsed from the dedicated extraction call's output, every write is
PROVISIONAL + provenance-visible, and the applier ignores op types the vocabulary doesn't
contain. (Memory can never execute anything; worst case is a bogus provisional fact, visible and
forgettable in the UI.)

### 19.4 Oversized single message (P2)

The §6.2 chunking rule splits windows *between* messages; a single message larger than the
per-call cap (a pasted document) would deadlock the watermark. Rule: such a message is processed
**once**, middle-out truncated (head + tail preserved) to fit, watermark advances past it, and
the pass notes `truncated` in the activity row. Combined with §19.3, a giant paste typically
yields zero ops anyway.

### 19.5 Backlog fairness across characters (P3)

When a backlog exists (after offline periods or a recovered failure — extraction itself is
unmetered, §12), the catch-up scheduler works off the **oldest watermark first across
assistants**, round-robining between assistants with pending lag so one hyperactive character's
backlog can't delay another's indefinitely. The same ordering applies to the capped autonomous
categories (sleep adjudication backlog, profile regeneration). Deterministic, no config surface.

### 19.6 Multilingual users & CJK (P1/P2)

The user demonstrably chats in EN/IT/RO; the app ships zh/ja/ko locales. Rules:
- FTS4 uses the `unicode61` tokenizer (diacritics-folding, `remove_diacritics=2`).
- **Statements are stored in English** (canonical, keeps FTS/dedup coherent); the extraction
  model translates while `reason_excerpt`/provenance keep the original language verbatim.
- **Aliases are multilingual by design** ("uni", "l'università", "die Uni" all alias "TU Wien") —
  entity matching is the cross-language bridge; the extractor is told to add native-language
  aliases whenever the user names an entity in another language.
- CJK degradation: unicode61 doesn't word-segment CJK; recall there leans on alias exact-match +
  entity resolution + vectors when configured. Acceptable; documented, not silent.

### 19.7 Cold start & settings sync (P2)

- On first enable, the store is seeded **without model calls** from what the app already knows:
  user display name → alias + `is_named` fact (source=CONFIRMED_BY_USER), app language,
  device timezone (category-whitelisted, GLOBAL). The graph is never empty on first open, and
  the character knows the user's name from message one.
- The user display-name setting is **watched**: renames update the alias/fact automatically
  (supersede, not overwrite).
- Character renames regenerate the profile (name is in the hash) and update the
  character-self node's canonical name; frames orphaned by a profile regeneration go DORMANT,
  never deleted (episodes keep their labels).
- Empty-state UI: the Memory screen shows a friendly explainer card instead of zeroed stat cards
  until the first extraction lands.

### 19.8 Identity-fact floor & emotional salience (P3)

- **Importance-5 facts are never auto-FORGOTTEN** — DORMANT is their floor; only the user (or
  RETRACT) can forget identity-level facts. Generalizes the import-amnesty lesson: the system
  must never be seen discarding something it itself rated identity-level.
- **Emotional salience multiplier**: extraction already rates importance; episodes/facts flagged
  emotionally significant (grief, milestones, confessions) get an `S₀` multiplier (×2) — the
  human pattern that emotional memories resist decay. Deterministic once flagged.

### 19.9 Character duplication, card export, and world sharing (P4/P5)

- **Duplicate assistant**: dialog gains "Also copy memories" (default off) → clones
  CHARACTER-scope rows to the new owner id through the applier.
- **Assistant export/import** (`utils/AssistantExportImport.kt` — found via graphify; it already
  has an opt-in `includeMemories` flag that bundles core+episodic memories and restores them via
  `addMemory`): the opt-in survives, upgraded — when enabled it embeds the §14.4 graph-export
  format (this character's scope only) and import feeds it through the applier
  (`source=IMPORTED`, dedup gate absorbing collisions). Default stays **off**: sharing a card
  shares the character, not your history. Legacy assistant exports containing old-format
  memories import as legacy-note facts with the §16.2 amnesty.
- Multi-character shared fiction worlds (two characters in one storyline) are **out of scope**
  for v2; the scope model (§10) makes cross-character reads impossible by design. Noted as the
  price of the privacy guarantee; a future opt-in "shared frame" concept would need its own
  privacy review.

### 19.10 Pipeline resilience (P2/P6)

- **Circuit breaker**: 5 consecutive extraction failures (parse/model/HTTP) for an assistant →
  extraction pauses 24 h, health-strip notice with the error class ("memory model failing"),
  auto-resume with one probe pass; watermark holds everything meanwhile (nothing lost, per the
  §6.2 invariant).
- **Restore/idempotency**: workers re-validate conversation/assistant ids on run (a WebDAV
  restore can remove them mid-queue) and no-op gracefully; replayed passes are absorbed by the
  dedup gate; `memory_conversation_state` rows for missing conversations are swept by sleep
  hygiene.
- **Clock sanity**: event timestamps are clamped to `[2000-01-01, now + 48 h]`; fuzzy
  verbalization treats out-of-range as "at some point".

### 19.11 "On this day" enrichment (P5, free)

`SpontaneousWorker`'s prompt (already memory-fed, §11.3) additionally receives episodes whose
`event_start` anniversary (month-day) is today — "it's been a year since you two first talked
about the lighthouse story". Zero extra model calls, uses existing spontaneous rate limits and
its existing toggle; disabled automatically when time awareness is off.

### 19.12 Developer observability (P6)

A `memoryDebugLogging` flag (developer page, like `enableRagLogging` today) dumps extraction
prompts/ops/gate decisions to the in-memory log ring buffer — for diagnosing "why did it
save/not save X" without shipping telemetry. Off by default; never persisted.

---

## 20. Implementation sessions & kickoff prompts

The §17 phases cut into **12 agent-session-sized slices**. Rules that apply to every session:
work on branch `LC_memory_v2`; one conventional commit per session (`feat(memory): …`); the app
must compile and remain releasable at the end of every session (everything stays inert until
P2b flips the switch); run `graphify update .` after code changes; never touch the legacy
memory tables except where a session says so. Each prompt below is self-contained —
paste it as the session's first message.

### S1 — P1a: schema & model layer

> Read docs/memory-system-v2-plan.md §4, §16.1, and §11.3's DatabaseSanitizer row before writing
> any code. Implement the Room v34 graph-memory schema exactly as §4 specifies: all new tables,
> entities, DAOs, int-code objects, and the Kotlin model layer (data/model/MemoryGraph.kt),
> with AutoMigration(33→34) registered and schema 34.json committed. Add every new table to
> DatabaseSanitizer's allowlist. Delete the orphaned MemoryItemEntity/MemoryItemFtsEntity files.
> Do NOT build any logic (no applier, no workers) — store only. Done when: project compiles,
> migration test scaffold passes (fresh install + 33→34), sanitizer round-trips the new tables,
> one commit.

### S2 — P1b: the applier

> Read docs/memory-system-v2-plan.md §6.3, §6.4, §19.2, and §0's dedup table. Implement
> MemoryOpApplier as the sole writer: entity resolution pipeline (pinned canonicals first,
> alias table, FTS; §6.4 order), predicate normalization + seed vocabulary (~40 predicates with
> single-valued flags), the structural dedup gate, length clamps, the volatility guard (§19.2),
> provenance/FTS/activity sync, per-scope mutex, single-transaction apply with watermark
> advance. Also MemoryOpParser (tolerant JSON, no `!!`). Pure logic goes in unit-testable
> classes (MemoryDedupLogic etc.). Done when: property tests prove no op sequence can create a
> duplicate triple, an orphan fact, or a second user-person entity; parser fuzz tests pass; one
> commit.

### S3 — P1c: reads + legacy import

> Read docs/memory-system-v2-plan.md §3 (MemoryGraphRepository), §16.2, §19.7. Implement
> MemoryGraphRepository (all read paths, cached core-sheet queries, scope-enforced SQL per
> §10), MemoryImportWorker (legacy MemoryEntity/ChatEpisodeEntity → applier ops, chunked 200
> rows, resumable via memory_store_meta, 60-day decay amnesty, significance→importance mapping),
> and cold-start seeding (§19.7: user name/language/timezone on first enable). Import runs
> through the applier only. Done when: import unit tests cover amnesty + idempotent re-run;
> repository queries have scope tests; one commit.

### S4 — P2a: extraction pipeline

> Read docs/memory-system-v2-plan.md §6.1–§6.4, §11.1–§11.2, §12, §19.3, §19.4, §19.10.
> Implement MemoryEncoder (extraction prompt with triplet neighborhood digest per §6.2,
> classification contract, op output), MemoryExtractionWorker, ChatService triggers (post-
> generationDone only, flush-on-switch expedited, never streaming checkpoints, NORMAL
> persistence mode only), message-id watermark + MemoryWatermark/MemoryBranchLogic
> (edit/regenerate/delete/fork rules §11.2), oversized-message rule, third-party-text guardrail
> in the prompt contract, circuit breaker, ledger recording with the extraction safety ceiling
> (§12 — extraction is otherwise unmetered). Workers must never read
> settings.getCurrentAssistant() (§11.1). Done when: watermark/branch scenario tests pass;
> digest golden tests pass; a real device chat produces provisional facts; one commit.

### S5 — P2b: recall & injection (end-to-end MVP)

> Read docs/memory-system-v2-plan.md §5, §7, §11.3, §11.4. Implement MemoryRecall (core sheet
> with hedged provisionals, alias-expanded FTS+optional-vector query recall with spreading
> activation, recent episode strip, pending-tail digest incl. the §11.4 widening rule, fuzzy
> time verbalization + tense rules) and MemoryRecallTransformer; wire it into ChatService
> replacing the legacy retrieveRelevantMemories block (ChatService.kt ~1487) when
> assistant.enableMemory + store migrated. Rebuild search_memory over the graph; add
> save_memory; retire create/edit/delete_memory tools. RETRACT op (§19.1) lands here too.
> Contradiction pairs inject newest-only. Done when: retrieval correctness tests pass; recall
> <50 ms on a seeded 1k-fact store; end-to-end on device: chat → extraction → next chat recalls
> it; one commit.

### S6 — P3: sleep pass

> Read docs/memory-system-v2-plan.md §6.5, §6.6, §13, §19.5, §19.8. Implement MemorySleepPass +
> MemorySleepWorker: deterministic stages first (decay via stability/retrievability, §19.8
> identity floor + emotional multiplier, auto-promotion, expiry, hygiene, §13 size enforcement
> with compression-pressure flags) as pure MemoryRetentionLogic/MemorySleepLogic; then
> model-assisted stages (batched adjudication with deterministic MERGE semantics and
> MANUAL/CONFIRMED protection, contradiction resolution, episode→gist compression, habit
> induction, entity summary refresh, import refinement) under the SLEEP budget, calls outside
> the scope lock, re-validated under it. Iterate ALL memory-enabled assistants. Retire the
> legacy MemoryConsolidationWorker for migrated users (§16.3). Done when: retention/merge unit
> tests pass incl. MANUAL protection; a simulated 3-month store stays within §13 budgets; one
> commit.

### S7 — P4-i: settings restructure + Shared Memory page

> Read docs/memory-system-v2-plan.md §14.0 (hard rules — review-blocking), §14.1's Shared
> Memory paragraph, §15, §12's model pickers. Restructure settings: no global memory toggle
> anywhere; per-character settings into the Memory screen's gear sheet (build the sheet);
> rename the main-settings memory entry to "Shared Memory", move it above Add-ons, and build it
> as the GLOBAL_USER browse/add/edit/delete page (no settings on it); add the "Memory" category
> (parser/processor/embedding) to the default-models page and remove every memory fallback to
> summarizer/subagent/background selectors; normalizeMemorySettings stage per §16.3. Register
> Screen.MemoryCenter + MemoryCenterVM skeleton. Compare every screen against an existing
> settings page and the Figma sketches described in §14. Done when: navigation works, old
> memory sub-pages are unreachable (files removed in S8), dark+light audit clean; one commit.

### S8 — P4-ii: Memory screen, browse, node sheet

> Read docs/memory-system-v2-plan.md §14.0–§14.1, §14.3–§14.4 fully; the Figma sketches are the
> source of truth and §14.0's eight rules are review-blocking. Build the per-character Memory
> screen (stat cards, status pill, Suggestions section with accept/dismiss, static graph
> preview card placeholder — real renderer lands in S9 — "Browse all memories" button, Activity
> section with "N API calls today" and day dividers, gear FAB), the Browse page (filter pill
> rows, grouped ListItem shapes, length-adaptive title weight, Recently-forgotten with restore,
> + Add memory), the node sheet (kind/classification/status chips incl. dashed Provisional,
> focused mini-graph placeholder, fuzzy time line, Sources card with italic quotes +
> conversation link, SUPERSEDES history timeline, alias editing on entities, Pin/Edit/Forget
> with grace + undo snackbar), and export/wipe flows (§14.4). Delete the three legacy memory
> sub-pages. Done when: every §14.0 rule holds in dark+light; one commit.

### S9 — P4-iii: the graph

> Read docs/memory-system-v2-plan.md §14.2 fully — the interaction contract is normative, and
> attempt 1's graph is explicitly NOT a visual reference (only its off-thread-settle/freeze/
> graphicsLayer technique may be ported). Build: the stylized static preview renderer
> (size-adaptive so the whole graph always fits the card, label-free, black outlined surface
> per §14.0-5), and the full-screen live graph (force layout settled off-thread then frozen;
> whole-graph default framing; node size ∝ relation count; zoom-driven collision-resolved
> labels that can never overlap; tap→focus-zoom with fade-out, back/zoom-out/recenter→fade
> back, second tap→the S8 node sheet; momentum pan, focal-point pinch, interruptible springs,
> press-scale + PremiumHaptics; reduced-motion static fallback; TalkBack routes to Browse).
> Wire the S8 placeholders to the real renderers. Done when: 60 fps pan/zoom on a 150-node
> store on device, no label overlap at any zoom, all §14.2 interactions verified by hand; one
> commit.

### S10 — P4-iv: in-chat surfacing

> Read docs/memory-system-v2-plan.md §11.3's activity-pill row and §14.0. In ActivityPill.kt's
> system, add the memory ball/pill: circular chip with minimized-pill geometry at the right end
> of the row, memory accent color, exempt from +N grouping, event-type icons (new/update/
> contradiction/episode); renders as a full-sized text pill ("Remembered 2 things") when memory
> is the turn's only activity; interesting events only, never reinforcements; tap → bottom
> sheet of the pass's activity rows with node-sheet links; self-fades. Add the "Show memory
> activity in chat" toggle (default on) to Advanced settings → UI customization
> (DisplaySetting). Update ContextSourcesSheet's UsedMemory entries to open node sheets. Done
> when: verified in a real chat with multiple pills and with memory-only turns; one commit.

### S11 — P5: profiles, frames, curiosity

> Read docs/memory-system-v2-plan.md §8, §9, §19.9, §19.11. Implement
> CharacterProfileGenerator (hash-cached, PROFILE budget, frames→memory_frame, care-abouts into
> the extraction prompt, stale frames→DORMANT on regen), frame labels through extraction and
> recall (fiction rules §8.2), CuriosityEngine + CuriosityLogic (goal lifecycle, ≤1/run
> generation in sleep stage, optional :search web-fill to web_draft, delivery gating, structural
> back-off, ≤2 asks/character/week in code, default OFF with grayed child toggle), promotion
> review + suggestion chips wiring (§10), duplicate-assistant "copy memories" option, and the
> spontaneous-message integrations (§11.3: pre-flush + recall context + on-this-day §19.11).
> Done when: CuriosityLogic/PromotionLogic unit tests pass incl. back-off permanence; frames
> visibly label episodes end-to-end; one commit.

### S12 — P6: hardening

> Read docs/memory-system-v2-plan.md §16.4, §17 P6, §19.10, §19.12. Implement
> MemoryEmbeddingBackfillWorker (model-change triggered, oldest-salient-first, resumable,
> store_meta watermark, status-pill progress), restore/idempotency sweeps (§19.10), clock
> clamps, memoryDebugLogging (§19.12), the on-device MigrationTestHelper 33→34 test against a
> real v33 DB file, a 5k-fact retrieval-latency benchmark asserting <50 ms, and a Memory
> screen + graph journey in the baseline-profile generator. Then do a full §14.0 audit pass
> over every memory screen in dark+light. Done when: all instrumented tests compile and pass on
> device; benchmark green; one commit.

(P7 web exposure remains optional/unscheduled; prompt it ad hoc if wanted.)

