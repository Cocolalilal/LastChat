# Memory v2 — Implementation sessions & starter prompts

Companion to `docs/memory-system-v2-plan.md` (the source of truth). Eleven sessions + one
optional; each leaves the app releasable and ends with tests green, `graphify update .`, and a
conventional commit. Run them in order — later sessions assume earlier invariants.

**Every prompt below assumes this shared preamble (paste it first, verbatim):**

> Read `docs/memory-system-v2-plan.md` and `AGENTS.md` before touching anything — the plan is
> the source of truth for the memory system; where code reality contradicts it, stop and flag
> instead of improvising. The reverted branch `origin/LC_memory_system_attempt_1` may be
> consulted for code *shapes* (workers, watermark, DI wiring, canvas layout) but its data model
> is obsolete — port patterns, never files. Use `graphify explain "<Symbol>"` to locate
> integration points before grepping. The legacy memory system must keep working untouched
> until the session that explicitly retires it. After changes: run the tests you added,
> `graphify update .`, and commit (`feat(memory): …`).

---

## S1 — Store foundation (plan §4, §16.1)

> Implement Memory v2 phase P1a: the graph store. Delete the dead orphaned
> `MemoryItemEntity.kt`/`MemoryItemFtsEntity.kt`. Create all §4 Room tables (entities, DAOs,
> indices, int-code objects, FTS4 with unicode61 per §19.6), the Kotlin model layer
> (`data/model/MemoryGraph.kt`, sealed `MemoryOp` per §6.3), Room v34 via
> `AutoMigration(33→34)` with `34.json` committed, and add every new table to
> `DatabaseSanitizer`'s allowlist (§11.3 — critical). No behavior changes anywhere; the legacy
> memory system is untouched. Exit: build green, `MigrationTestHelper` 33→34 test passes,
> schema JSON committed.

## S2 — Applier + structural dedup (plan §6.3–§6.4)

> Implement Memory v2 phase P1b: `MemoryOpParser` and `MemoryOpApplier` — the sole writer.
> Entity-resolution pipeline with pinned canonical user/character nodes and the
> name-is-an-alias rule (§6.4 step 1), alias table + hygiene, predicate normalization with the
> seeded ~40-predicate vocabulary and single-valued flags (§18 open q.2), the structural dedup
> gate, fact links (SUPERSEDES/CONTRADICTS), provenance/activity/FTS sync, per-scope mutex,
> length clamps, volatility guard (§19.2), all in one transaction per pass. Pure logic in
> unit-testable companions. Exit: property tests prove no op sequence can create a duplicate
> triple, an orphan non-entity row, or a second user-person entity; dedup-gate table tests and
> parser fuzz tests pass.

## S3 — Legacy import (plan §16.2, §19.10)

> Implement Memory v2 phase P1c: `MemoryImportWorker`. Legacy `MemoryEntity` core rows →
> legacy-note facts, episodic + `ChatEpisodeEntity` → episodes, everything through the applier,
> zero model calls, chunked/resumable via `memory_store_meta` watermarks. Enforce the import
> decay amnesty (60-day exemption, stability floor, significance→importance mapping — §16.2;
> an import must never flood "Recently forgotten"). Expose progress for the future status pill.
> Worker id-revalidation per §19.10. Exit: round-trip test on a seeded legacy DB; re-running
> the import is a no-op (dedup absorbs it).

## S4 — Encoder + triggers + watermark (plan §6.1–§6.3, §11.1–§11.2, §12, §19.3–§19.4)

> Implement Memory v2 phase P2a: the write path. `MemoryEncoder` (extraction prompt with
> triplet neighborhood digest, classification contract, scene handling/EXTEND_EPISODE, op caps,
> third-party-text rule §19.3) + `MemoryExtractionWorker`; ChatService triggers (§6.1 — only
> after generationDone, never streaming checkpoints, flush-on-switch expedited,
> pre-spontaneous flush; NORMAL persistence mode only); message-id watermark + branch/edit/
> delete/fork rules (`MemoryWatermark`, `MemoryBranchLogic` — §11.2); oversized-message rule
> (§19.4); budget ledger with the extraction safety ceiling + autonomous caps + circuit breaker
> (§12, §19.10). No worker keys off the current assistant (§11.1). Recall/injection is NOT in
> scope. Exit: watermark/branch scenario tests, digest golden tests; a real conversation
> produces sane ops behind `Assistant.enableMemory` without changing what the character sees.

## S5 — Recall + the swap (plan §5, §7, §11.3–§11.4, §19.7)

> Implement Memory v2 phase P2b: the read path, then retire legacy injection.
> `MemoryRecall` (cached core sheet with hedged provisionals, alias-expanded FTS+optional-vector
> query recall with spreading activation, recent episode strip, pending-tail digest incl. the
> widened-when-backlogged rule §11.4), `MemoryRecallTransformer` replacing the
> `ChatService.kt:1487–1525` block for memory-enabled assistants, fuzzy time verbalization
> (§5), contradiction-pair newest-only rule, retrieval-only access bumps. Rebuild
> `search_memory` over the graph (processor model for synthesis) and add `save_memory`; retire
> `edit_memory`/`delete_memory`; add RETRACT support end-to-end (§19.1). Cold-start seeding +
> display-name watch (§19.7). Exit: <50 ms retrieval on a seeded store; for a memory-enabled
> assistant the legacy RAG path is fully bypassed; TEMPORARY chats recall but never extract.

## S6 — Sleep pass (plan §6.5–§6.6, §19.5, §19.8)

> Implement Memory v2 phase P3: `MemorySleepPass` + `MemorySleepWorker`. Deterministic stages
> first (decay per §6.6 stability model, auto-promotion, expiry, hygiene, size enforcement §13)
> in pure `MemorySleepLogic`/`MemoryRetentionLogic`; then budgeted model stages (adjudication
> with deterministic MERGE semantics and MANUAL protection, contradiction resolution, episode→
> gist compression, habit induction, entity summary refresh, import refinement). Identity-fact
> floor + emotional salience multiplier (§19.8). Scheduling per §11.1: iterate ALL
> memory-enabled assistants, oldest-first fairness (§19.5), no-network pass for deterministic
> stages, retire legacy `MemoryConsolidationWorker` scheduling for migrated users (§16.3).
> Exit: retention/sleep/merge unit tests incl. MANUAL-protection and pinned-exemption cases.

## S7 — Memory screen, browse, node sheet (plan §14.0–§14.1, §14.3–§14.4)

> Implement Memory v2 phase P4a-1: `Screen.MemoryCenter` + `MemoryCenterVM`. The per-character
> Memory screen exactly per the Figma mockups and §14.0's review-blocking rules (stat cards,
> status pill, Suggestions section, static stylized graph preview card placeholder-fed for now,
> "Browse all memories", Activity feed with day dividers + "N API calls today", gear FAB
> settings sheet). Browse with wrapping pill filters, grouped ListItem shapes, length-adaptive
> title weight, "Recently forgotten" restore, "+ Add memory". Node sheet per sketch 3 (chips,
> focused mini-graph placeholder, fuzzy time line, Sources card, SUPERSEDES history, Pin/Edit/
> Forget). Do NOT build the interactive graph yet. Exit: dark+light alignment audit against a
> neighboring app screen (§14.0-8); old memory sub-pages still present (removed in S8).

## S8 — Settings placement, Shared Memory, memory ball (plan §11.3, §14.1, §15)

> Implement Memory v2 phase P4a-2: relocate everything per §15. Remove the three legacy memory
> sub-pages (`AssistantMemorySubPage`, `AssistantRagMemorySubPage`,
> `AssistantMemoryConsolidationSubPage`); add the "Shared Memory" entry above Add-ons (browse-
> only, zero settings); add the "Memory" model-picker category (parser/processor/embedding) to
> the default-models page and remove memory's connections to summarizer/subagent selectors;
> per-character gear sheet holds all remaining settings with dependent-toggle graying. Memory
> ball/pill in chat per §11.3 (minimized-entry geometry, event-type icons, full pill when
> alone, exempt from +N grouping, hide toggle in Advanced settings → UI customization). Export/
> wipe flows (§14.4) and the assistant-export opt-in upgrade (§19.9). Exit: settings migration
> (`normalizeMemorySettings`) keeps old configs deserializing; no orphaned navigation routes.

## S9 — Graph views (plan §14.2)

> Implement Memory v2 phase P4b: the graph. Static stylized preview (size-adaptive node/edge/
> spacing scaling, whole graph always visible, black outlined card per §14.0-5) wired into the
> S7 placeholder; full-screen live graph with the §14.2 interaction contract: focus flow
> (tap → zoom+fade, back/zoom-out/recenter → fade back, second tap → Browse node sheet),
> zoom-driven collision-resolved labels that never overlap, node size ∝ relations, momentum
> pan, focal-point pinch, interruptible springs, press-scale + haptics, off-thread settle then
> freeze, `graphicsLayer`-only pan/zoom, reduced-motion static fallback, TalkBack routing to
> Browse. Exit: 60 fps pan/zoom on a 180-node neighborhood on a mid-range device; interaction
> feel is a review criterion, not a nice-to-have.

## S10 — Profiles, frames, curiosity (plan §8, §9, §19.11)

> Implement Memory v2 phase P5: `CharacterProfileGenerator` (hash-cached, PROFILE budget,
> frames/relation/care-abouts feeding extraction and recall labels), frame lifecycle incl.
> DORMANT orphaned frames (§19.7), `CuriosityEngine` + `CuriosityLogic` (default OFF, goal
> lifecycle, structural back-off, ≤2 asks/character/week in code, optional web fill via
> `:search` stored as unconfirmed draft), sleep-stage promotion review (`PromotionLogic`:
> category whitelist auto-promote + suggestion chips wired to S7's Suggestions section),
> "on this day" spontaneous enrichment (§19.11). Exit: CuriosityLogic/PromotionLogic unit
> tests; a DECLINED goal can never be regenerated or re-asked.

## S11 — Hardening (plan §16.4, §19.10, §19.12, §17-P6)

> Implement Memory v2 phase P6: `MemoryEmbeddingBackfillWorker` (model-change triggered,
> oldest-salient-first, store-meta aligned, FTS covers the interim), first-time-embedding
> upgrade path, `memoryDebugLogging` developer flag (§19.12), on-device v33→v34 migration test
> against a real historical DB file, 5k-fact retrieval-latency benchmark asserting <50 ms,
> Memory screen + graph journey in the baseline-profile generator, clock-sanity clamps
> (§19.10). Sweep the §2 failure-mode table and §18 decided-defaults list and verify each
> claim against the implementation — anything unmet gets fixed or flagged now.

## S12 (optional) — Web exposure (plan §17-P7)

> Implement Memory v2 phase P7: read-only `/api/memory/summary`, `/api/memory/browse`, node
> detail, and activity endpoints in `WebApi.kt` + an SSE activity event + a minimal web-ui
> browse page. No write endpoints. Honor scopes exactly as SQL does on-device.
