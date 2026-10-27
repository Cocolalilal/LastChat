# Memory transition and model choices

27 October 2026. Proposal grounded in the current checkout and primary model documentation.
No additional subagents used. No replacement engine, migration, model API integration or
native UI is implemented by this document. The user permits implementation when sufficiently
confident; the remaining evidence gap is engineering judgment, not an approval requirement.

## Recommendation

- Preserve LastChat's floating UI, gradient fades and measured scroll clearance.
- Ship two experiences: Basic with optional Dreaming; Advanced with local embeddings.
- Support EmbeddingGemma 2 Text 270M as the one tested Advanced embedder initially.
  Keep the index behind a replaceable internal contract, independent of canonical memory.
- Make decision-model assistance optional, off by default, and non-authoritative.
- Convert each character in staging while chatting remains available. Never require
  a user to erase their working memory first or silently process their entire history on upgrade.

These are recommendations, except the floating UI and two-mode composition, which the user
has explicitly specified. The latest request reopens whether embeddings should be fixed.

## Floating UI: preserve the intentional design

The screen scrolls behind a floating composer and status/action row. Use LastChat's actual
composer shell, semantic theme surfaces and motion policy. Reference `ChatPage.kt`'s
ChatTopFadeOverlay, ChatWidePanelEdgeFadeOverlay and bottom-toolbar fade, not a screenshot's
hard-coded black rectangle. Use a measured footer height plus safe drawing/IME insets to
reserve scroll clearance; do not double-count navigation insets or assume a fixed text height.
At the end of the list the final item must be completely readable above the fade/controls.

Fades blend scrolling content into the screen background. They do not require making the
popup's text-bearing surface translucent. Keep that surface opaque during movement; animate
its position/shape and the separate scrim. This preserves the requested floating look while
avoiding the overlapping text shown in the rejected screenshots. Popup and composer form
one interaction scope; the screen behind them is inactive while that scope is modal.

When the keyboard opens, preserve the request and caret, raise the composer using the same
inset ownership as chat and shrink the popup's scrollable body. Apply/Discard remain reachable.
Status changes, tool completion and notice dismissal cannot reset the editor. Reduced motion,
large fonts, landscape, light/dynamic themes and TalkBack require native checks.

Migration progress replaces the normal freshness row while preparation is active. Show a
short phase and honest units, such as `Preparing memory · 86/240 chats`, with pause/resume
available from that row. Keep errors and phase detail expandable. Do not add a dashboard of
simultaneous counters. Paused/preparing are operational states, not extra memory modes.

## Seamless migration: user-visible sequence

1. Installing the update preserves memory-off and existing memories. It does not download
   a model or start all-history paid inference without the relevant preparation action.
2. Existing Core notes become available through a scoped compatibility reader. Where a
   complete new Basic note can be preserved without model interpretation, preserve it;
   otherwise label the original note honestly and organize it later. Do not invent categories
   or sources through a database migration. New Basic starts without any embedding download.
3. Enabling Advanced checks the device and model installation. Show actual download size,
   storage needs and the selected memory-processing provider. Reuse one verified installation
   across characters. Local embeddings do not imply local extraction/summarization.
4. Prepare Advanced alongside the current usable memory. Process retained Core/Basic notes
   and every eligible conversation for that character, incrementally and chronologically
   where source time permits. Keep uncertain historical timing uncertain.
5. New chats and manual corrections remain usable during preparation. Record their changes
   durably so the candidate can catch up. The user can pause, leave, restart or cancel.
6. Validate the candidate, reconcile affected changes and switch the character's active
   memory generation atomically. Existing opt-in to preparation is enough for automatic
   cutover when valid; do not add another mandatory approval dialog.
7. Retire old representations according to their retention/privacy rules. Legacy episodes
   are never imported. A failed conversion never destroys the valid Core/Basic representation.

### Which data enters which path

| Starting point | Target and proposed treatment |
| --- | --- |
| Memory off, chats exist | Keep off through app upgrade. When enabled, Basic may retain explicit notes; Advanced preparation covers eligible retained history. Turning memory off is not deleting chats. |
| Old Basic/Core | Preserve original text and manual edits. Basic maps to readable notes without embeddings. Advanced interprets original notes with note provenance, then processes retained eligible chats. |
| Old Basic plus RAG | Preserve eligible original notes; discard/rebuild vectors as derived data. Do not turn an old vector score into confidence or a new source. |
| Old Advanced | Keep eligible Core material. Exclude all legacy episodic records from the new reader, extractor and historical importer; rebuild from eligible original chats. |
| New Basic, Dreaming off | Existing notes remain usable; no casual automatic learning. Explicit requested saves/corrections still work. Advanced opt-in can prepare retained history. |
| New Basic, Dreaming on | Same notes and retrieval. Queue eligible new source revisions for background organization; do not activate the old consolidation/RAG bundle. |
| Advanced to Basic | Materialize useful readable notes with retained provenance/policy metadata. Do not pretend a prose projection is a lossless copy of the graph. |
| Returning to Advanced | Reconcile retained structured records with intervening note edits, source changes and erasures before reuse. Never replay the old graph as if nothing changed. |
| Character/package import | New local owner identity, preserved import provenance and section permissions. Do not claim access to the original app's chats or merge two identities by name alone. |

Removing legacy episodes must mean all old representations: `MemoryEntity(type=EPISODIC)`,
`memory_episode_v3` and their FTS/vector/cache/projection material. Trace dependent summaries
and imported claims before reuse. An old Core claim with no verifiable original source is
an imported note, not a verified transcript claim. Do not blindly trust the old `reality`,
`confidence`, timestamps or `selected_branch` flag as proof of eligibility.

On entry to the new migration path, the compatibility reader uses eligible Core only;
legacy episodes are immediately fenced from recall and never become a fallback. Physical
cleanup can be checkpointed, but logical exclusion must survive cancellation/restart and
apply to newly produced exports/backups. After cutover, delete the excluded old records and
dependent caches. Cancellation preserves Core/Basic; it does not reverse the requested
episode retirement. This is not a promise to erase copies in external old backups.

## Migration mechanics and failure handling

Use separate durable concepts, not a long coroutine with a percentage in preferences:

- A character-scoped migration job: source/target schema, source manifest, state, processing
  model identity, permissions/policy generation, checkpoints and a cancellation generation.
- An active memory generation pointer. Requests bind one valid generation and cannot combine
  half-converted Basic data with half-built Advanced data. Privacy restrictions are checked
  again before returning data, even if the request bound an older generation.
- A manifest of original source IDs and exact content/selected-version revisions, including
  shared-prefix identity for chat forks. A timestamp alone is neither a cursor nor identity.
- A durable change journal for new/edited/deleted sources and deliberate memory edits while
  preparation runs. The old automatic learner stops once the new preparation path owns this
  character; user-visible edits are journaled without keeping two independent writers.
- Candidate batches with idempotency identity covering job, source revision and extraction
  contract. Retry one batch without duplicating facts or multiplying support/importance.
- Per-unit outcomes: pending, processed/no-op, excluded with reason, retryable error, or
  quarantined. Never report a skipped model failure as a successfully processed conversation.

Suggested job states: awaiting download/configuration -> inventory -> importing notes ->
processing history -> catching up -> validating -> active. Pause is durable and resumable
at each applicable phase. Errors need a concrete repair/retry path. Cancellation fences
every in-flight response before a write; stopping the network request alone is insufficient.
An uncertain possibly billed call is not blindly replayed after a crash.

At activation, recheck ownership, permission/erasure generations and affected source revisions
inside the commit boundary, then change the active-generation pointer. Do not hold a global
lock for an entire historical rebuild. Persist the catch-up boundary and hand its remaining
journal entries to ordinary ingestion atomically, so continuous chat cannot prevent cutover
forever. Unrelated later turns remain pending ordinary work; every pending unit stays visible
to the scheduler. Deleted/restricted sources must be ineligible immediately, even when a
polished replacement summary has not yet been generated.

| Daily-use failure | Required outcome |
| --- | --- |
| App killed during download/batch | Resume verified bytes/committed units, not the entire character. Never trust a partial artifact as installed. |
| User pauses while model is responding | No late write is permitted. Retain/discard an uncommitted result under the job's policy and revalidate on resume. |
| User cancels | Current usable Core/Basic remains. Discard candidate data; don't switch modes or retain candidate private text as hidden history. |
| Chat edited/regenerated during scan | Retract old revision's support and process selected eligible revision. Never flatten all variants. |
| User corrects a fact during scan | Preserve the deliberate correction; do not let an older historical statement overwrite it by processing order. |
| Forget while paused | Erasure immediately affects live, staged, draft, summary, graph and vector views. Rebuild cannot relearn excluded fragments. |
| Model fails on one chat | Continue independent units where safe; expose the failure. Do not claim full-history completion with unresolved required units. |
| Device out of storage or overheats | Pause with a useful recovery action. Keep the old usable generation; foreground chat wins local model resources. |
| Two characters use the same name | No shared memory/caches without explicit scope. Only the downloaded artifact is shared. |
| Old backup restored later | Compare current privacy receipts outside the incoming snapshot. If no newer state exists, explain that later erasures cannot be inferred. |
| Other device restores a pending job | Do not silently replay possibly billed calls or assume snapshot sync provides a distributed lock. |

Coverage is every retained eligible conversation, not every byte ever written. Deleted,
temporary, disallowed, abandoned/unselected variants and unavailable source data do not
become eligible because a migration wants a complete graph. Missing per-message historical
offsets cannot be reconstructed from today's device timezone. Full historical import may
cost many processing calls; show that boundary before starting it, without a daily quota.

## Embeddings: one supported model initially, replaceable internals

Recommendation: Advanced initially supports **EmbeddingGemma 2 Text 270M only**. Basic
requires none. Keep arbitrary remote/custom embedding choices out of the initial Advanced
settings, including any existing global embedding selection. A release supporting many
providers multiplies testing, privacy paths and migration complexity before the core works.

Google documents a modular 270M text backbone and task-directed embeddings [1]. This is an
embedding model, not the model that understands a conversation, writes summaries, runs
memory tools or decides truth. Those responsibilities need a selected generative model.
The current local runtime supports arm64/x86_64 bundles; unsupported devices retain Basic
without a surprise download or cloud fallback. Hardware tests must establish actual support,
memory/thermal behavior and local generation coexistence, not just a compatible ABI string.

A single embedding family reduces configuration failures; **it does not eliminate index
invalidation**. The current cache's `(memoryId, memoryType, modelId)` key is insufficient
for the proposed engine. Track:

- Model bundle digest and revision, tokenizer/input-template identity and relevant runtime
  recipe version. A catalog UUID or filename can stay constant while bytes change.
- Query/document task roles and exact prefix recipe, output dimension, normalization and
  vector encoding. Current LastChat uses one sentence-similarity prefix for both. Evaluate
  that against role-specific retrieval before changing it; don't silently mix recipes.
- Chunking/rendered text recipe and exact memory/source revision plus owner/context scope.

Validate vector count, dimension, finite values and expected normalization. Never compare
vectors from different index identities, even if their dimensions match. Rebuild only
derived indexes when any relevant identity changes. Preserve canonical facts, sources,
edits, policies and history. Use one tested dimension/recipe initially; adding a dimension
slider recreates avoidable compatibility choices.

Use one replaceable embedding adapter internally. That is room for future supported models,
not a promise that arbitrary OpenAI-compatible endpoints are interchangeable. When a new
model is eventually supported, build its index alongside the old one and switch only after
validation. Existing vectors can remain for the still-valid old index until cleanup.

If the bundle is missing after activation, keep Advanced data intact and show one compact
semantic-search-unavailable state. Lexical/entity recall can continue with an explicit
limitation. Offer redownload; don't silently switch the user to Basic or send data remotely.
Avoid blocking the entire UI on unrelated derived graph/summary refreshes. Shared model
deletion must disclose which characters will lose semantic recall without deleting memories.

## Optional decision helpers: useful, not required

The user confirmed small helper/decision models. Jev is real: TypeSafe documents typed
choices, scores and yes/no probability outputs [2]. OpenCode's 27 October changelog and
merged Decision API PR confirm decision workflows, including System One/Jev adapters [3].
API support does not establish that a particular OpenCode account or plan serves Jev.
Use the actual provider's current model discovery and decision endpoint when implementing;
do not guess a chat-completions-compatible route or use unrelated Jev-branded websites.

Proposed optional setting inside Advanced memory settings: **Decision assistance** (off),
with provider/model selection and a short explanation of which excerpts are sent. The
embedder, memory-processing model and decision helper are three separate responsibilities.
Existing permission to use one remote chat provider does not configure a second one.
Local-only/private contexts must never be sent to a hosted helper.

Useful narrow decisions, after normal ownership and source filtering:

| Task | Helper's possible role | Authority that remains elsewhere |
| --- | --- | --- |
| A new candidate | Suggest durable fact, short-term state, plan or unclear | Extractor and temporal validator still need supporting source/time; plan expiry never proves occurrence. |
| Candidate versus source | Estimate supported, contradicted, insufficient | Host first verifies source identity/quote; a semantic verdict can be wrong. |
| Candidate versus current fact | Suggest same, compatible change, conflict, unrelated | Engine preserves history and requires adequate evidence for replacement. |
| Retrieved shortlist | Score question relevance or need for deeper read | Host enforces scope/time and preserves important conflicting evidence. |
| Maintenance work | Suggest which non-destructive review to prioritize | Durable queue retains all eligible work; helpers cannot permanently skip history. |

Start with asynchronous candidate support/conflict checks, where a bad result need not
delay the chat or discard evidence. Add synchronous retrieval judgments only if measured
relevance benefits justify their extra call and p95 latency. Keep ordinary direct lookup
free of an obligatory helper round trip. Batch independent questions over the same scoped
state, not every stored memory or every keystroke.

TypeSafe itself supplies memory, citation-check and RAG-passage cookbooks [4-6]. They are
useful patterns, not LastChat-ready engines or evidence of performance on our conversations.
In particular, don't copy a toy rule that saves the entire request and answer above one
threshold: assistant guesses must not become biography, and short-term detail must not
enter permanent summaries just because the turn contains one important fact.

Jev's typed answer reduces output-shape problems, not semantic errors. TypeSafe's
`confidence` summarizes its output distribution; it is not an observed accuracy rate [7].
Do not choose one arbitrary 0.8 threshold and call it safe. Calibrate each question on our
own real/fictional, multilingual, negation, time and correction cases. Include uncertainty
or insufficient evidence as an allowed outcome. Confident extractor/helper agreement is
not independent corroborating evidence; both may have made the same mistake.

The helper cannot authorize reads/writes, bypass forgetting, choose another character's
context, create/merge an identity, erase information, rewrite policy or commit a draft.
Explicit user corrections/privacy requests must not depend on helper agreement. Uncertain
or conflicting helper output defers to ordinary checked processing or review, without
silently dropping the user's update. On timeout/outage use the ordinary pipeline within
its existing correctness policy, not a weaker unvalidated auto-commit path.

Cache only by exact model version, rubric, permitted state/source revisions and policy
identity. Invalidate on erasure; retain diagnostics without unnecessarily copying private
excerpts into logs. Validate missing answers, unknown labels, NaN/out-of-range probabilities,
refusals and changed model capability just like any external API. Pin versions for evaluation.
No arbitrary automatic-request daily limit is introduced; coalescing, bounded per-job
work, backoff and explicit provider spending preferences address wasted calls.

## What implementation confidence still requires

This pass is sufficient to recommend a direction, not to claim a ready-to-ship replacement.
The current audit already found temporary-chat tool access, unscoped memory mutation,
source-retraction and short-turn scheduling hazards. They remain unpatched. Adding Jev
would not repair those host boundaries.

Concrete release evidence must include:

1. Native UI using LastChat components: floating fades/insets, real IME, large fonts,
   TalkBack, motion, light/dynamic themes and long-content work sessions.
2. Transaction and source tests: every migration source combination, process death at
   checkpoints/cutover, pause/cancel, concurrent corrections, deletion and old-backup restore.
3. Actual selected-model extraction/recall evaluation, with required and forbidden facts
   scored separately. No false biography from assistant suggestions or story dialogue;
   no plan-to-completed-event inference; no cross-character/private-context leakage.
4. On-device embedding/index tests: download failure/digest mismatch, updated recipe,
   missing bundle, resource contention and realistic corpus sizes.
5. Optional-helper A/B evaluation against the same base pipeline. Measure missed durable
   facts, unsupported claims, false merges, stale current-state recall, privacy failures,
   irrelevant recall, p50/p95 latency, energy and total processing cost. Preserve the
   no-helper path unless evidence shows a specific improvement worth the tradeoff.

The immediate next implementation checkpoint is the native floating memory shell plus
scoped checked memory operations, followed by Basic notes/Dreaming and resumable conversion.
Advanced follows on the same verified ownership/transaction boundary. These are engineering
checkpoints, not proposed extra public modes or an excuse to label partial work finished.
No production replacement is claimed by this document, and no APK was built in this pass.

## Primary references

Checked 27 October 2026. Provider capabilities and APIs may change; validate at integration.

1. Google, [EmbeddingGemma 2 model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2).
2. TypeSafe, [Introduction](https://docs.typesafe.ai/introduction).
3. OpenCode, [27 October changelog](https://opencode.ai/changelog) and
   [merged Decision API PR #58758](https://github.com/anomalyco/opencode/pull/58758).
4. TypeSafe, [Simple memory system](https://docs.typesafe.ai/cookbooks/basic_memory).
5. TypeSafe, [Double-checking citations](https://docs.typesafe.ai/cookbooks/citation_check).
6. TypeSafe, [Classifying RAG passages](https://docs.typesafe.ai/cookbooks/classifying_rag_passages).
7. TypeSafe, [Confidence](https://docs.typesafe.ai/confidence).

Current-code references: `data/db/entity/MemoryEntity.kt`, `TemporalMemoryEntities.kt`,
`data/db/entity/EmbeddingCacheEntity.kt`, `data/ai/rag/EmbeddingService.kt`,
`local-llm/.../LocalEmbeddingConfig.kt`, `data/model/Conversation.kt`,
`data/sync/WebdavSync.kt`. Paths without a module are under app's rikkahub package.
See SOURCES.md and DAILY-ENGINE-REVIEW.md for the original pinned RikkaHub and host-boundary audit.
