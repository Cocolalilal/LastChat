# Engine proposal and adversarial review

This records the design target. See IMPLEMENTATION.md for implemented behavior and remaining limitations; this document is not an implemented guarantee.
It deliberately separates Basic note semantics from Advanced structured memory. Shared
infrastructure covers permissions, sources, revisions, drafts and scheduling; it does
not turn Basic into a hidden graph engine.

## Candidate representation

Basic: character/context-owned notes with title, description, aliases, body and a
monotonic revision. Keep provenance for model-authored note fragments and preserve
explicitly authored text. A note body can remain readable prose without graph entities.
If a fragment cannot be mapped safely after source deletion, mark it for repair/exclude
it from recall rather than claiming precise automatic retraction.

Advanced: entities plus temporal claims/events and custom relations. Each claim records
subject/object or event participants, value/text, status (asserted/believed/planned/
uncertain), source author/provenance, recording time, source time, validity/occurrence
interval and context. A support alternative can require several sources jointly;
independent confirmations are separate support alternatives. Losing one of two jointly
required sources invalidates that alternative. Repeated processing is not corroboration.

Shared source identity: conversation, selected message revision/content hash, committed
turn/tool-chain identity, author and exact supporting span. A visual message index is
not an identity. Current source eligibility is checked on retrieval as well as write,
so an asynchronous repair cannot leave deleted/inactive claims in model context.

Shared durable metadata: owner/context-bound jobs and intents, per-record revisions,
policy generations, staged change sets, explicit manual corrections, privacy receipts,
limited reversible history and migration checkpoints. None of these is a new equal-peer
sync protocol. Current LastChat sync restores snapshots; conflict-free peer operation
merging remains a separate project.

Derived artifacts: user summaries, graph layout, vectors, suggested questions and
compression. Each references the memory revisions and eligible sources it represents.
They must never become additional independent evidence for their own content. Summary
links are validated typed references to eligible scoped records; a generator cannot
make a valid link merely by inventing an ID or underlining text. Dates/ages and active
projects need validity-aware rendering, not indefinite reuse of a cached number.

## Read/write contract

The host binds character, permitted context, selected model/locality and operation
capabilities before any model call. Tools never choose or widen those bounds. Lookup
must verify ownership before exposing even previous content. Temporary/incognito
requests deny both retrieval and tools. The memory agent's internal traces are not
conversation evidence and cannot trigger recursive extraction.

Read tools: list/search/read notes, inspect entities, read supporting source snippets,
inspect relationships and existing policy. Responses are bounded and paginated; a
summary overview is not permission to read all chats. Retrieved text remains data,
not executable instructions.

Draft tools: add/replace/append a note; propose a claim/event; connect/disconnect scoped
entities; set validity; resolve a proposed duplicate; stage a merge; stage forgetting;
propose a how-to-remember instruction. A tool operates only on allowed IDs or
host-assigned new handles. Dynamic relation labels are data, not executable schema or
migration code. Irreversible database/OS/network access is not exposed.

A popup transaction has read-your-writes: later tools see its draft. The host validates
all operations and affected source/policy revisions. An invalid operation does not
partially write canonical memory. Once the user reviews the complete proposal, Apply
commits its batch atomically. Cancellation increments a session generation; late model
output cannot commit. Dismissal never means approval. Unrelated background edits do not
invalidate an entire draft: check touched records and relevant policies, then safely
merge disjoint updates or require an updated preview for genuine conflicts.

A refreshed draft summary belongs to the same draft. If summary generation fails,
proposal: allow review of the exact changes plus a plainly labeled unavailable summary,
then Apply data with an immediately safe plain-text fallback view. Do not silently
block corrections behind another cloud call or display an old paragraph as updated.
This fallback is a user-review decision, not settled behavior.

## Personalized remembering policy

Keep the memory agent's own instructions inside memory settings, editable by the user.
They concern how/what to remember, not a second biography store. Support nuanced authored
instructions; structured categories can assist enforcement but are not the whole format.
Automatically inferred policy changes require review. Explicit restrictive requests such
as never store my address must restrict storage immediately; a popup must not keep
exposing the address while waiting for another approval. Never copy the address into the
policy text itself. Exact enforcement and exception handling belong in behavioral tests.

## Automatic learning contract — needs explicit behavioral approval

The management popup uses Apply/Discard. A direct, specific "remember/correct/forget"
request during ordinary chat can authorize a bounded checked update with notification
and Undo, following Basic's upstream intentional-tool behavior; it should not force the
user to open another approval popup. A broad or ambiguous destructive request needs an
impact preview. If work is queued, the assistant must say pending rather than saved.
This is a proposed distinction to validate with the user's examples.

Normal Dreaming cannot ask for approval after every
mundane fact. Proposed default: automatically add well-supported low-impact durable
information, independently add support, and expire dated relevance. Do not automatically
merge ambiguous people, infer sensitive traits, rewrite user-authored corrections,
change remembering policy, or accept uncertain meaning-changing contradictions.

A proposed current-state update must distinguish a new state from a historical report.
"I graduated" can end current enrollment when the subject/context/time are supported;
"I graduated in 2020" does not necessarily contradict studying somewhere in 2026.
Ambiguity is omitted or kept tentative; it must not force an interruption to every chat.
Review is available when needed without turning the main page into a stream of alerts.

Evidence matching is necessary, not sufficient: an exact quote can still be misread.
Negation, sarcasm, intentions, quotations, hypothetical speech and third-party claims
need real extraction evaluation. A second LLM is not a deterministic truth validator.
Confidence thresholds are provisional until calibrated against fixtures; a model's
self-reported confidence alone does not authorize important edits.

## Identity, time and importance

Name/embedding similarity proposes a match; it does not merge entities. Aliases can
be context-bound and time-bound. Family/romantic roles are relations. An ex can remain
historically a girlfriend without being treated as the current girlfriend. Custom
brother/mentor/landlord relations are supported without inventing universal inverses.
A shared event requires shared-event evidence, not a path through two adjacent facts.

Use original source time and timezone for relative real-world dates when available.
Preserve uncertainty for old messages without timezone. "I'm 19" is reported age at a
date, not an exact birthday. Story time belongs to a story timeline; real inactivity
must not advance a fictional scene. Validity, source time and learning time are distinct.
An expired plan never becomes a completed event. Temporary low-value remarks can stay
in short-term conversation context without creating durable graph nodes.

Importance uses explicit user intent, durability, salience, usefulness and independent
confirmation. Frequency and recency assist ranking; they do not prove truth or authorize
irreversible deletion. A deceased relative may remain important after years of silence.
Graph saturation uses actual eligible mentions, not retrieval-induced reinforcement.
Pins affect visibility, not truth or source eligibility.

## Retrieval and limited model context

Bind permissions first; then filter eligible/current sources, lexical/entity matches,
local semantic candidates and a bounded graph neighborhood. Rank for the actual query,
time intent and context. Past questions can retrieve historical states; current questions
must not use a superseded state without explanation. Graph chains are evidence-backed
associations, not transitive factual inference. Keep the response grounded without
inserting personal details into generic answers just to demonstrate memory.

Pack relevant details with dates/uncertainty into a real token budget. Preserve the
active user turn, required identity and tool-call dependency chain. When retrieval is
insufficient, perform a bounded deeper read or admit the gap; never silently send the
entire graph or truncate an arbitrary serialized object. Oversized sources split at
stable boundaries and checkpoint; no dropped remainder because a context window filled.

EmbeddingGemma 2 Text is fixed, but index invalidation still exists. Index identity must
include artifact/revision, task/input prefix, tokenizer/bundle identity, normalization,
dimension/chunking recipe and text revision. Model UUID alone is insufficient after an
artifact update under that UUID. Reindex derived vectors without losing canonical
memory; lexical recall remains available. No cloud embedding fallback. Extraction and
summarization may use the explicitly selected processing model; the UI must make that
separate locality boundary clear. Nothing implies a bundled generative extractor exists.

## Event-driven background processing without a quota

Queue actual new/changed eligible source revisions; coalesce repeated edits. Serialize
conflicting work per character/context, prioritize deliberate foreground requests,
checkpoint longer tasks and avoid starving background work. Cache successful no-ops.
Bound one run's context/tool steps/concurrency, then schedule a continuation when needed.
These are execution bounds, not a daily request allowance.

Use stable source/revision idempotency keys for extracted candidates and committed
batches. Record intent separately from observed state. User pause stops new work and
fences in-flight writes until resumed/revalidated. Cancellation prevents late commits.
A potentially billed call interrupted by process death requires explicit retry/takeover,
not automatic replay. Provider errors/malformed results receive bounded retries; a
persistent problem produces a resumable needs-attention state rather than an agent loop.
The OS can delay background work. Progress reflects committed units, not invented %.

## Forgetting, editing and recovery

Forget a fact, forget a subject and never learn a category are different actions.
Preview subject-wide impact; graph proximity alone cannot authorize deleting everything
connected to Clara. Preserve independent facts about the laptop or other people.

Remove forgotten content immediately from recall, links, summary text, suggestions,
vectors, draft outputs, tool previews and replay. A short Undo may retain a temporary
payload until its explicitly shown deadline. Afterwards, retained history must not act
as a hidden recovery copy of erased material. Source-edit invalidation is not identical
to user erasure: independently supported claims can survive source removal.

Historical rebuild must not relearn the forgotten claim from the same source. Track
source-fragment exclusions/privacy receipts rather than suppressing the whole message
and losing an unrelated pet fact. Redact excluded fragments before a model sees a
rebuild batch. If redaction mapping is uncertain, quarantine/exclude the affected unit
pending review instead of sending it unredacted. Do not drop unrelated safely separable
fragments. Edited sources, overlapping spans and paraphrased imported
copies are adversarial cases requiring implementation tests; a text hash alone cannot
promise universal semantic suppression. A standing policy such as never store addresses
handles future sources, and must not retain the address in the policy itself.

Basic/Advanced conversion is staged and reviewed. Basic notes and legacy Core notes
are read as notes with honest provenance, not fabricated original-chat evidence. For
Advanced preparation, process every eligible conversation's selected committed history,
with a durable manifest and source revisions. Do not flatten abandoned regenerations.
Decide access to alternate branches explicitly; shared-prefix support must not duplicate
mentions. Legacy episodic records are not imported and are removed only after validated
cutover. Repeated imports/migrations must be idempotent.

Chat remains usable during preparation. Keep the previous valid memory snapshot until
a new one is validated; queue messages arriving after the preparation watermark. Pause,
resume, crash, exclusion and source deletion cannot silently lose progress or widen
permissions. Switching modes must not erase the previous representation or reinterpret
story facts as real. Conversion back to Basic is a note export/summary, not a claim of
lossless graph round-trip. Re-entering Advanced must revalidate retained structured data
against intervening note edits and changed sources. Both modes share erasure and policy
generations: an older retained graph cannot resurrect a Basic-forgotten record. Omission
from a condensed note is not proof that the user wanted a fact erased. Conversions use
original eligible records, not summaries as fresh independent evidence.

Full backups must include canonical memory, revisions, provenance, policies and deletion
metadata. Compare incoming privacy generations/receipts against current local state
outside the incoming snapshot before replacement; simply backing up tombstones is not
enough. When no newer local/device state exists, an old external backup cannot establish
what was forgotten later. Explain that limit and require an explicit restore decision;
do not claim universal resurrection protection. Indexes can be rebuilt. Pending possibly billed jobs restored on another device
must not start silently. Validate imports in staging; malformed rows are quarantined with
a repair report, not silently coerced into trusted facts. Physical erasure of an offline
device or external old backup cannot be guaranteed by this app. Do not describe current
snapshot sync as a distributed lock or an immutable operation log.

## Current-code audit findings, not fixes already made

- `GenerationHandler.kt:700,2164,2198`: temporary tool capability and owner-scoped ID checks
  are incomplete; `MemoryRepository.kt:276,427` exposes unscoped updates/deletes.
- `ChatService.kt:1647,1706`: temporary context suppression does not disable all tools.
- `ConversationRepository.kt:157,209,223`: Core memory has no source-version retraction.
- `MemoryConsolidationWorker.kt:509`: snapshot checks lack exact content/policy revisions.
- `ConversationDeletionLedger.kt:8`: deletion ledger is process-local.
- `WebdavSync.kt:480`: restore is staged snapshot replacement, not peer operation merging.
- `AssistantOverlayVM.kt:49`: existing system overlay creates a persisted conversation.
- `AssistantOverlayScreen.kt:407,785`: tool-only filtering and private compact activity
  cannot simply be reused as an expandable memory work transcript.

These are release-blocking findings for the proposed feature. This review intentionally
does not silently patch current runtime behavior while the feature remains unapproved.

## Release evidence

Repository/permission/transaction tests, native UI interaction tests and real extraction
quality are separate gates. Use the fixture ledger for expected/must-not results, then
run the actual selected processing model on ordinary and adversarial conversations.
Measure unsupported claims, missed durable facts, false merges, stale current-state recall,
privacy leaks, irrelevant recall, latency and resource use. Agree target thresholds before
calling the system daily-chat-ready. Host test counts cannot establish these outcomes.
