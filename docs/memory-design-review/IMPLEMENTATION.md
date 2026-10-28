# Character memory implementation — 28 October 2026

This describes the actual Android implementation on `lastchat_dev`. It supersedes the
proposal-only status of the earlier review documents. The development APK is a working
implementation, not a certification of production readiness or extraction accuracy.
No subagents were used for this implementation pass.

## Implemented experience

- Exactly Basic and Advanced, with a separate memory enable switch. Dreaming is a Basic
  option. Turning memory off keeps stored data and prevents ordinary-chat reads/writes.
- Basic uses authoritative readable notes under You, Topics, and People, without an
  embedding requirement, graph dependency, file names, or file icons. Notes support
  title, category, description, aliases, body, direct Save/Cancel, pinning and forgetting.
- Basic Dreaming learns from new eligible user messages. Explicit Update also checks
  saved chats when Dreaming is off. It is an intentional deviation from RikkaHub's
  two-minute/six-turn scheduling: durable per-character work is currently queued promptly.
- Advanced stores entities, arbitrary relation labels and source-backed temporal facts.
  The v2 home uses durable overview/topic paragraphs, linked entity references,
  evidence-backed “More about…” entries, a text-free density graph, and browse/search.
- Both modes use LastChat's native composer components in a floating footer with a fade
  and measured scroll clearance. Settings and editors use Material theme colors and
  connected groups. The work popup has an opaque surface, compact real work statuses,
  expandable details, a shared input, Before/After, optional summary preview, Apply and
  Discard. There is no separate agent chat, name, avatar, or persisted chat transcript.
- Dismissing a popup does not apply it. Prepared drafts remain available on the memory
  page. Failed/cancelled requests retain the input. Interrupted requests require sending
  again rather than being automatically replayed. Recent checked edits can be undone
  from memory settings, subject to conflicts. Forgetting clears reversible history.
- Entity details show connected facts; individual facts show source quotes, reported
  time and relevance expiry. Chat sources open their conversation, and saved-note
  sources open their note. Actions use tonal colors; destructive actions disclose impact.

## Embeddings and model boundaries

The only embedding execution path is local **EmbeddingGemma 2 Text 270M** through
LiteRT-LM. The shared install card appears during first setup when Advanced is chosen,
in memory settings on enablement, after restoring enabled Advanced memory with missing
weights, and in the default-model settings page. It supports progress, pause/retry and
unsupported-device feedback. Basic needs no download. Model weights are not in backups.

Legacy provider/global/per-character embedding selections are not executed. The retired
300M entry is removed; only unshared files inside LastChat's managed local-model folder
are deleted. Arbitrary external imported paths are preserved. Missing/truncated bundles
are not shown as ready. Index identity includes the actual bundle SHA-256, the symmetric
input recipe, dimensions/normalization and fact revision. Changed weights cause derived
index rebuilding, without deleting memory. There is no remote embedding fallback.

Extraction, requested edits and summaries use the chosen memory-processing chat model,
then the app's configured summarizer/character/default selection when no explicit memory
model was selected. This is separate from local embeddings and may use a remote provider.
The UI identifies that distinction. Missing providers, context overflow, malformed output
and interrupted possibly billed requests produce actionable errors; no hidden infinite
retry or daily automatic-request quota exists. Each worker run is bounded to eight model
calls, with a persisted continuation for remaining work.

Optional Jev review is off by default and uses a separate secure TypeSafe credential.
It reviews deliberate Advanced edit drafts, sends only the edit request/proposal, and
cannot write, authorize, widen scope, or block the normal memory path. Timeout or invalid
responses leave a plainly unavailable review. Live TypeSafe credentials were not tested.

## Engine and migration

New Room v40 owner-scoped records replace the old memory runtime. The database migration
retires old episodes and derived v3 indexes. Original Core notes and conversations survive.
Per-character initialization imports Core once as honestly labeled saved notes, then
removes redundant old copies. Old episode text is never used as extraction evidence.
The backup sanitizer performs the same retirement for older restored snapshots.

Advanced preparation processes imported/saved note paragraphs and selected user-message
revisions across retained chats, chronologically and in bounded chunks. Candidate records
are separate until atomic activation. A final transaction checks for newly arrived or
edited sources and continues catching up before activation. Pause/resume, cancellation,
source/chunk receipts, configuration/policy fences, and interrupted-request markers
survive normal process restarts. A late result cannot activate a cancelled preparation.
New eligible messages are queued after normal chat; temporary conversations do not gain
memory context/tools or trigger learning. Assistant guesses are not automatic evidence.

Writes validate host-bound owner/context, record versions, policy/configuration, selected
source revision and exact supporting quotes. An invalid multi-record edit writes nothing.
Unrelated edits do not invalidate a draft. Source checks are repeated on reads, and parsed
sources are cached only for one operation. User-authored paragraphs retain authorship;
changing a title or pin does not magically make other paragraphs user-authored.

Plans and temporary facts require an expiry. Expiry removes current relevance and never
asserts completion. Durable summaries exclude plans/temporary states and validate their
fact references before display. New messages preserve instant/timezone; old local dates
remain uncertain. Confirmed changes can supersede earlier facts into history. Exact text
repeats and repeated source revisions do not create duplicate support. Name similarity or
a graph path does not authorize merging people or inventing a joint event.

Basic and Advanced conversion preserves readable notes and retained canonical facts.
Changed projected paragraphs close their previous Advanced facts; deleting a converted
note removes its associated retained facts. Re-entering Advanced revalidates sources.
Typed character exports can include canonical memory and remembering preferences, but
not vectors, pending work, edit history, or credentials. Imported packages receive a fresh
owner and imported provenance, not forged transcript evidence.

Forgetting removes canonical entries, dependent note copies, vectors, pending drafts,
summary text, candidate generations and reversible history. Hash/span receipts redact
relevant original evidence before later model extraction. Newer local erasure receipts
are merged into restored databases before live replacement, including legacy Core-note
fragments. Unrelated facts in a shared source remain. This is snapshot backup restoration,
not a new multi-device operation-based sync protocol. Receipts unavailable on every
remaining device cannot protect an independently restored ancient external backup.

Recall binds the selected context before reading: Basic profile/directory/on-demand
notes, or a lexical fallback for non-tool models; Advanced combines current lexical,
entity/alias and matching local-vector scores. Results fit the existing chat context
manager. Read tools have pagination and bounded responses. A separate explicit historical
search can find prior states without promoting them to current facts. Specific new,
non-destructive English remember requests can save directly; broader or existing-entry
edits and forgetting produce a reviewable draft. No model tool accepts an owner ID.

## Verification and limits

Verification results and artifact hashes are recorded in the release bundle. Native
Compose tests exercise Basic/Advanced, dark/light, entity details, staging/applying and
discarding edits, and a 320 dp / 1.5× text popup. These are Robolectric captures with
controlled memory-model responses, not HTML mockups or an extraction-quality evaluation.
Repository tests cover stale/disjoint/atomic writes, owner/context isolation, source
revision retraction, forgetting, conversion, cancellation and catch-up. Backup tests cover
fragment-level erasure, unrelated evidence, old Core copies and forgotten entity IDs.

There is no attached Android device or configured live processing/Jev credential in this
workspace. Native LiteRT inference, real provider extraction/recall, TalkBack, actual IME
behavior, long-run battery/thermal cost, and large-corpus performance still need device
and live-model verification. A quote match validates provenance, not logical entailment;
sarcasm, negation, sensitive facts, ambiguous people and chronology remain model-evaluation
risks. No perfect-human-memory claim is made.

Earlier proposed extras not implemented here include graph history playback, a complete
pan/zoom graph explorer, user-reviewed entity merging, per-person image management,
automatically learned remembering policies, a full memory-agent tool/planning loop,
relationship-affect/mood engines, and future group/scene knowledge boundaries. Current
contexts are explicitly selected per character; the engine does not guess real/fiction
boundaries from automatically generated topic labels. The graph preview caps rendering
at 180 nodes; this does not cap stored memory or the browse list. Edit history is bounded
to 30 batches and is not graph time travel. Summary previews after edits have a safe
fact-prose fallback; Update generates a polished overview from eligible durable facts.

These limitations prevent describing every historical brainstorming idea as finished,
or calling the development build fully proven for public production rollout.
