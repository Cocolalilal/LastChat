# Memory System v3 — Lean Graph Memory for LastChat

> **Supersedes** `memory-system-v2-plan.md`. The v2 plan was sound at its core but overengineered — 12+ tables, 12-stage sleep passes, curiosity engine, habit induction, bi-temporal model, character profiles, force-directed graph canvas. This plan keeps the good core, drops ~60% of the complexity, and integrates with the existing legacy memory system instead of replacing everything.

## Design Philosophy

**mem0 with a graph.** That's the target. The industry has moved away from complex consolidation/graph-traversal toward append-only + entity-linking + multi-signal retrieval. mem0's April 2026 v3 dropped UPDATE/DELETE/consolidation entirely and scores jumped 20+ points on LoCoMo (71.4 → 91.6). We take mem0's simplicity, add Graphiti's temporal-invalidation concept (simplified to one column), and keep Letta's two-tier model (core always-in-context + archival searchable).

### What stays from v2
- Entities + facts-on-edges (the core graph model)
- Extraction pipeline (one LLM call per chat window)
- Structural dedup (don't add what already exists)
- FTS + graph retrieval (1-hop expansion)
- Validity windows (valid_from / valid_until for "used to be true")
- Fiction/real flag (essential for roleplay apps)
- Scope model (GLOBAL_USER vs CHARACTER)
- Provenance (simplified: excerpt + conversation link only)

### What's cut from v2 (with rationale)
| Cut | Why |
|---|---|
| Curiosity engine (goals, web lookups, delivery gating) | Entire subsystem, already off by default. The chat model can be curious via personality. No `memory_goal` table. |
| Habit induction (≥3 facts → HABIT) | The extraction model already surfaces patterns as DURATIVE facts. No `HABIT` kind, no induction stage. |
| Character profiles / frames | Overbuilt. The fiction/real flag replaces it. No `memory_frame` table, no `CharacterProfileGenerator`. |
| Bi-temporal "belief time" (recorded_at + expired_at) | Academic overkill. `recorded_at` is enough; supersession chains show belief changes. |
| Forgetting curve exponential math (R = e^(−Δt/S)) | Replace with simple integer stability comparison. Same behavior, no floating-point math. |
| Budget ledger table | Use `memory_activity.calls_used` instead. One less table. |
| Full-screen force-directed graph canvas | Multi-week feature by itself. Browse list + mini-graph preview suffice. |
| Suggestion chips / activity feed suggestions | Gone with curiosity and promotion chips. User promotes manually from node sheet. |
| RETRACT op | UI "Forget" action is sufficient. |
| Volatility guard | Supersession handles it naturally. |
| Emotional salience multiplier | Importance already captures salience. |
| 3 budget presets (Eco/Balanced/Rich) | One default. Maybe Lite/Full later. |
| Spreading activation (HippoRAG-style) | 1-hop graph expansion from FTS-matched entities is sufficient. |
| Cascading memory evolution (A-Mem style) | Expensive and unpredictable on mobile. Append-only + retrieval-time scoring. |
| Episode compression ladder (episodes → gists → era-gists) | One-level summarization only. Old episodes → single summary episode. |
| Provenance rationale + 5-way classification | Excerpt speaks for itself. REAL/FICTION flag is enough. |
| REINFORCE appends provenance | One provenance row per fact, at extraction. |

## Architecture

### Data Model (8 tables — down from 12+)

```
memory_entity          — named entities (people, places, things, concepts)
memory_alias           — alternate names for entities
memory_fact            — facts (on edges between entities, with temporal windows)
memory_fact_link       — SUPERSEDES links only
memory_episode         — conversation summaries (provenance + retrieval)
memory_provenance      — "where did this come from?" (excerpt + conversation + messages)
memory_fts             — FTS4 mirror for retrieval
memory_activity        — extraction/sleep activity log + call counter
```

That's it. No `memory_goal`, no `memory_frame`, no `memory_budget_ledger`, no `memory_conversation_state` (use a metadata key-value table or settings), no `memory_store_meta`.

### Schema

#### memory_entity
```sql
id              INTEGER PK
name            TEXT NOT NULL
type            TEXT NOT NULL DEFAULT 'thing'  -- person, place, thing, concept, event
scope           TEXT NOT NULL DEFAULT 'CHARACTER'  -- CHARACTER or GLOBAL_USER
assistant_id    TEXT NOT NULL  -- which assistant owns this (empty for GLOBAL_USER)
summary         TEXT DEFAULT ''  -- lazy, computed at retrieval from top facts
embedding_blob   BLOB  -- for semantic search
embedding_model_id TEXT
created_at      INTEGER NOT NULL
```

#### memory_alias
```sql
id          INTEGER PK
entity_id   INTEGER NOT NULL FK → memory_entity
name        TEXT NOT NULL
```

#### memory_fact
```sql
id              INTEGER PK
subject_id      INTEGER NOT NULL FK → memory_entity
object_id       INTEGER NOT NULL FK → memory_entity
predicate       TEXT NOT NULL  -- "lives_in", "works_as", "likes", "studies", etc.
value           TEXT DEFAULT ''  -- natural-language fact: "Sarah lives in Graz"
scope           TEXT NOT NULL DEFAULT 'CHARACTER'
assistant_id    TEXT NOT NULL
status          TEXT NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, DORMANT, FORGOTTEN, CLOSED
kind            TEXT NOT NULL DEFAULT 'POINT'  -- POINT (one-time) or DURATIVE (ongoing)
reality         TEXT NOT NULL DEFAULT 'REAL'  -- REAL or FICTION
importance      INTEGER NOT NULL DEFAULT 3  -- 1-5
stability       INTEGER NOT NULL DEFAULT 30  -- days until DORMANT (integer, not exponential)
valid_from      INTEGER  -- millis, when the fact became true
valid_until     INTEGER  -- millis, when it stopped being true (null = still true)
last_confirmed_at INTEGER NOT NULL  -- last retrieval/reinforcement
recorded_at     INTEGER NOT NULL  -- when learned
category        TEXT DEFAULT ''  -- for scope-promotion whitelist
```

#### memory_fact_link
```sql
id              INTEGER PK
from_fact_id    INTEGER NOT NULL FK → memory_fact
to_fact_id      INTEGER NOT NULL FK → memory_fact
link_type       TEXT NOT NULL  -- "SUPERSEDES" only
```

#### memory_episode
```sql
id              INTEGER PK
assistant_id    TEXT NOT NULL
content         TEXT NOT NULL  -- summary
significance    INTEGER NOT NULL DEFAULT 5  -- 1-10
conversation_id TEXT
embedding_blob   BLOB
embedding_model_id TEXT
start_time      INTEGER NOT NULL
end_time        INTEGER NOT NULL
last_accessed_at INTEGER NOT NULL
source          TEXT NOT NULL DEFAULT 'CHAT'  -- CHAT, SUMMARY, MANUAL
```

#### memory_provenance
```sql
id              INTEGER PK
row_kind        TEXT NOT NULL  -- "fact" or "episode"
row_id          INTEGER NOT NULL
conversation_id TEXT
message_ids     TEXT  -- JSON array
excerpt         TEXT  -- ≤300 chars, the message snippet that produced this
created_at      INTEGER NOT NULL
```

#### memory_fts (FTS4 virtual table)
```sql
-- Mirrors fact.value + entity.name for full-text search
content     TEXT
fact_id     INTEGER
```

#### memory_activity
```sql
id              INTEGER PK
assistant_id    TEXT NOT NULL
scope           TEXT NOT NULL  -- CHARACTER or GLOBAL_USER
action          TEXT NOT NULL  -- EXTRACTION, SLEEP, MANUAL
calls_used      INTEGER NOT NULL DEFAULT 0
created_at      INTEGER NOT NULL
```

### Two-Tier Retrieval (Letta-inspired)

1. **Core memory** (always in context): Top N highest-importance ACTIVE facts for this assistant, rendered as a compact "what I know" sheet. No model call — deterministic selection + rendering.

2. **Archival memory** (searched on demand): FTS + 1-hop graph expansion from matched entities. Returns ranked facts, capped at a token budget. No model call at retrieval time.

### Extraction Pipeline

One LLM call per chat window (watermarked, idempotent):

1. **Window**: Collect messages since last watermark. Cap at ~30 messages or token budget.
2. **Extract**: Single LLM call with a structured prompt: "Extract facts as (subject, predicate, object, value) triples. Tag each as POINT/DURATIVE and REAL/FICTION. Rate importance 1-5."
3. **Dedup**: For each extracted fact, check if a similar fact already exists for this (subject, predicate). If yes → REINFORCE (bump `last_confirmed_at` + `stability`). If contradictory → SUPERSEDE old fact (set old.status = CLOSED, create link). If new → INSERT.
4. **Entity resolution**: Match extracted entities to existing entities by name/alias, or create new ones.
5. **Provenance**: Store excerpt + conversation_id + message_ids for each new fact.
6. **FTS mirror**: Insert fact text into FTS table.
7. **Update watermark**: Record processed message ID.

### Sleep Pass (5 stages — down from 12)

Triggered opportunistically after extraction, or on a periodic WorkManager schedule:

1. **Decay sweep** (deterministic): For each ACTIVE fact, if `now - last_confirmed_at > stability * 86400000`, set status → DORMANT. For DORMANT facts older than 30 days grace, set → FORGOTTEN. Importance-5 facts never auto-FORGOTTEN (identity floor).

2. **Auto-promotion + expiry** (deterministic): Auto-promote CHARACTER-scoped facts in whitelist categories (name, pronouns, language, timezone, occupation_study) to GLOBAL_USER. Set `valid_until` on CLOSED facts that are now superseded.

3. **Hygiene + size enforcement** (deterministic): Dedup aliases, clean orphaned entities, enforce per-character fact cap (e.g., 500). If over cap, FORGET lowest-importance DORMANT facts first.

4. **Adjudication + contradiction resolution** (one model call, batched): Send a batch of recent facts to the model: "Which of these contradict each other? Which should supersede?" Apply results as SUPERSEDES links.

5. **Episode summarization** (one model call, only when over episode budget): If episodes exceed cap, summarize oldest N low-significance episodes into one summary episode. One level, no ladder.

### Retrieval & Injection

**No model call at retrieval time.** Pure deterministic:

1. **Core sheet**: Select top N (e.g., 15) ACTIVE facts for this assistant, ranked by importance DESC, then by last_confirmed_at DESC. Render as:
   ```
   <memory>
   Core facts:
   - Sarah lives in Graz (since last spring)
   - Sarah studies mathematics at TU Graz
   - ...
   </memory>
   ```

2. **Query recall** (when chat context has >2 messages): FTS search on recent messages → matched facts → 1-hop graph expansion (entities connected to matched entities). Rank by importance × recency. Cap at token budget. Append to core sheet.

3. **Episode strip**: Last 3 episodes for this assistant, rendered as "Recent conversations: ..."

4. **Pending-tail digest**: If there are unprocessed messages (extracted after last watermark), inject last ~5 as raw text, capped at 200 tokens.

5. **Time awareness**: Fuzzy verbalization ("last spring", "about 2 weeks ago") based on `recorded_at` and `valid_from`.

### Scope Model

- **CHARACTER scope**: Facts specific to this assistant's conversations with the user. Default.
- **GLOBAL_USER scope**: Facts about the user that all assistants should know. Auto-promoted for whitelist categories. Toggled by `useSharedUserMemory` (reuse existing setting concept, but it's new to this codebase).
- **Fiction isolation**: FICTION facts never promote to GLOBAL_USER.

### Fiction/Real Flag

Single column on `memory_fact`. Set during extraction ("is this real or roleplay?"). FICTION facts:
- Never promote to GLOBAL_USER
- Never feed episode summarization
- Are tagged in the core sheet as "(roleplay)"

### Settings (in Assistant.kt)

**Keep existing (simpler modes):**
- `enableMemory` — master toggle. Now means "graph memory on"
- `enableMemorySearchTool` — registers `search_memory` tool (simpler mode)
- `enableRecentChatsReference` — inject recent conversation titles (lightweight mode)

**Add (graph-specific):**
- `useSharedUserMemory` — participate in GLOBAL_USER layer (default: true)
- `memoryTimeAwareness` — fuzzy ages/tenses in injection (default: true)

**Cut (dead/unnecessary):**
- `useRagMemoryRetrieval` — graph handles retrieval
- `ragSimilarityThreshold` / `ragLimit` — graph uses FTS + ranking
- `ragIncludeEpisodes` / `ragIncludeCore` — graph manages both
- `enableRagLogging` — replaced by `memory_activity`
- `enableMemoryConsolidation` — replaced by graph extraction + sleep
- `consolidationDelayMinutes` / `lastConsolidationTime` / `lastConsolidationResult` — dead

### Migration from Legacy

1. **Keep `MemoryEntity` and `ChatEpisodeEntity` as-is** — don't delete them.
2. **One-shot importer** (`MemoryImportWorker`):
   - `MemoryEntity` (type=CORE) → `memory_fact` (subject="User", predicate="notes", object="User", value=content, importance=3, kind=POINT)
   - `MemoryEntity` (type=EPISODIC) → `memory_episode` (content, significance=5, source=IMPORT)
   - `ChatEpisodeEntity` → `memory_episode` (content, significance, conversation_id, source=CHAT)
3. **Repoint `MemorySearchService`** at graph FTS (`memory_fts` table) instead of `MemoryRepository.retrieveRelevantMemories`.
4. **Repoint `MemoryRepository.addMemory`** (manual add) to create a `memory_fact` via the graph applier with `source=MANUAL`.
5. **Cut `MemoryConsolidationWorker`** — replaced by graph extraction + sleep pass.
6. **Cut `MemoryRepository` RAG methods** — `retrieveRelevantMemories*`, `regenerateEmbeddings`, `getCombinedMemoriesFlow`. Keep only what `MemorySearchService` and the importer still need.
7. **Cut `EmbeddingCacheEntity`** — graph stores embeddings inline on `memory_entity`.

### Reused Legacy Code

| File | Reuse |
|---|---|
| `EmbeddingService.kt` | As-is — clean embedding generation |
| `MemoryChunker.kt` | As-is — pure text chunking |
| `VectorEngine.kt` / `VectorUtils.kt` | As-is — cosine similarity + serialization |
| `MemorySearchService.kt` | Simplify — repoint at graph FTS, cut subagent LLM synthesis |
| `MemoryDAO.kt` | Keep for import source, eventually deprecate |
| `ChatEpisodeDAO.kt` | Keep for import source, eventually deprecate |

### UI (Lean)

- **Memory screen**: stat cards ("N memories, M entities"), recent activity, Browse button. No graph canvas.
- **Browse**: filterable list of facts/entities, search bar, scope filter. This is the main navigation.
- **Node sheet** (bottom sheet): fact text, kind/status chips, time line, source (provenance excerpt + "view in conversation" link), actions (Pin/Edit/Forget/Share with all characters).
- **Mini-graph preview**: static dot-and-line rendering of top ~20 entities. No physics, no zoom, no pan.
- **Shared Memory page**: browse page for GLOBAL_USER facts.
- **Export/Wipe**: as-is.

No full-screen graph canvas. No force-directed layout. No suggestion feed.

## Implementation Plan (7 sessions — down from 12)

### S1: Schema + model layer
- Add 8 new tables to `AppDatabase` (migration v36)
- Create entities, DAOs, data classes
- Add new settings to `Assistant.kt`, mark old ones `@Deprecated`

### S2: Extraction pipeline
- `MemoryEncoder`: watermark-anchored windowing, one LLM call, structured output parsing
- `MemoryOpApplier`: single writer, atomic batches, dedup gate, entity resolution, ≥1 ABOUT edge, provenance, FTS mirror
- `MemoryExtractionWorker`: WorkManager wrapper

### S3: Retrieval + injection
- `MemoryRecall`: core sheet, FTS+graph recall (1-hop), episode strip, pending-tail digest
- `MemoryRecallTransformer`: wire into chat generation as `InputMessageTransformer`
- Replace legacy `MemoryRepository` injection path

### S4: Sleep pass
- `MemorySleepPass`: 5 stages (decay, promotion, hygiene, adjudication, episode summarization)
- `MemorySleepWorker`: WorkManager wrapper
- Simple integer decay (no exponential math)

### S5: Migration + integration
- `MemoryImportWorker`: one-shot legacy → graph importer
- Repoint `MemorySearchService` at graph FTS
- Repoint manual memory add through graph applier
- Cut `MemoryConsolidationWorker`
- Cut dead RAG settings

### S6: UI
- Memory screen (stat cards, activity, browse button)
- Browse (filterable list, search, scope filter)
- Node sheet (chips, timeline, sources, actions)
- Mini-graph preview (static)
- Shared Memory page

### S7: Polish + testing
- Edge cases (branch/regenerate, character deletion, model changes)
- Fuzzy time verbalization
- Fiction isolation enforcement
- Integration tests
- Cleanup deprecated code

## Comparison: v2 vs v3

| Metric | v2 Plan | v3 Plan |
|---|---|---|
| Tables | 12+ | 8 |
| Sleep pass stages | 12 | 5 |
| Implementation sessions | 12 | 7 |
| Model calls per extraction | 1 | 1 |
| Model calls per retrieval | 0 | 0 |
| Model calls per sleep pass | 2-3 | 1-2 |
| UI complexity | Full graph canvas + suggestions + activity feed | Browse list + mini-graph + node sheet |
| Inspiration | Graphiti (full bi-temporal KG) | mem0 (append-only + entity linking) + Graphiti (temporal invalidation, simplified) |

## What This Gives You

- **mem0-style simplicity**: append-only facts, entity linking, multi-signal retrieval
- **Graph structure**: entities connected by facts, 1-hop traversal at retrieval
- **Temporal awareness**: validity windows for "used to be true", supersession chains
- **Scope model**: per-character + shared user memory
- **Fiction isolation**: roleplay facts don't leak into real-world memory
- **Two-tier**: small always-present core sheet + large searchable archive
- **Reuses existing code**: `EmbeddingService`, `VectorEngine`, `MemoryChunker`, `MemorySearchService`
- **Leaves simpler modes intact**: `enableMemorySearchTool`, `enableRecentChatsReference`
