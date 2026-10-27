# Memory design review — 27 October 2026

**Read REVISION-2.md first.** The user rejected the first browser prototype; its visual
checks missed important defects. Revision 2 replaces that artifact and strengthens the
daily-use audit. Production implementation is still awaiting review.

Status: design proposal for review, not authorization to replace the app's memory feature.
The implementation baseline is `lastchat_dev` at `db906721`, based on upstream dev
`e7b0d276`. Upstream was fetched again during this review and has not advanced.
The rejected v4 branch is not the implementation baseline.

## What is already decided

- Two modes: Basic and Advanced. Basic has a Dreaming toggle, not a third mode.
- Basic closely follows the **current RikkaHub memory behavior**, with LastChat styling
  and the user's You / Topics / People grouped-list design. No `.md` framing.
- Advanced follows the user's **v2** screen directly: Overview, relevant summary
  sections, Dive deeper, Memory Graph, floating status/update row and Manage memory input.
- The floating footer deliberately has a fade and enough bottom scroll clearance.
- Highlighted references open the corresponding node detail sheet. Links carry scoped
  stable IDs, not just matching display text.
- Manage memory opens a temporary in-page work popup resembling LastChat's Android
  system assistant: no agent name/avatar, reasoning/status and expandable tool calls.
  The user can clarify within that session. It is not a normal saved conversation.
- The popup prepares a draft and refreshed summary; Apply commits and Discard drops it.
- Advanced uses local EmbeddingGemma 2 Text 270M. No remote embedding fallback.
- No daily automatic-request quota. No revival of the rejected memory map/screens.
- The user has explicitly waived the prior test-build rollback/recovery detour. This
  review does not make it a prerequisite for design or add a new downgrade APK.

## Basic: observed upstream behavior

Pinned reference: RikkaHub `a7d1e6efb50bb781e49bc26c5145640ad6a7ea97`.

Dreaming OFF does not turn memory into read-only storage. Existing notes remain usable.
The conversation model is told not to casually write memories; explicit requests to
remember, correct or forget can use memory tools. Dreaming ON queues finished turns
for a background organizer. Upstream normally waits two idle minutes or six pending
turns; leaving a conversation/backgrounding can trigger earlier work. Manual Dream now
can run independently of the toggle.

Retrieval does not use embeddings: profile and response preferences are included in
full; other notes expose a directory entry with description, aliases and date. The
model reads relevant notes when needed. Response preferences concern reply style;
hobbies belong in topics. Generic questions should not trigger unnecessary personal
recall. A no-op is a legitimate result.

Upstream has You, Topics, Areas and People. The user's design has You, Topics and
People. Proposal: put ongoing areas/projects inside Topics using the same grouped rows,
without introducing an extra primary section. Response preferences remain reachable
under You. This mapping needs visual review, not an automatic new navigation scheme.

The readable note editor retains title, description, aliases and body. Use LastChat's
fields, Save/Cancel, grouped shapes and fonts. A Basic note is authoritative text: Basic
must not secretly require a knowledge graph or embedding index to work.

### Intentional changes from upstream, proposed for review

1. Owner/context checks, exact source revisions, checked manual edits and atomic draft
   writes. These protect LastChat's existing history/versioning and the requested popup.
2. Durable pause/cancel/checkpoints for long organization and preparation work.
3. A selected processing model/locality boundary, rather than silent model fallback.
4. Cursor progress by source identity/revision, not timestamps alone. Do not lose turns
   that share timestamps or were edited while work was running.
5. All-history Advanced migration. Routine Basic Dreaming remains recent/incremental,
   but whether to keep RikkaHub's exact seven-day lookback is a review decision. The
   reference also starts with only the most recent 12 user turns. Recommend tracking
   all unprocessed eligible revisions in bounded batches, rather than silently losing
   history after inactivity. This is a disclosed deviation, not a hidden quota.
6. Character/story interpretation must be adapted explicitly; upstream's personal-user
   extraction rules cannot represent all accepted fictional character/narrator events.

## Advanced is a different memory experience

Advanced stores source-backed claims, events and relations, and derives readable topic
summaries, an entity graph, vectors and suggested recall questions. The summary is for
browsing. It is not a giant replacement prompt and is not evidence for its own claims.

Ordinary chatting follows: bind permitted context → inspect the current turn and
selected history → retrieve current relevant claims (lexical + local semantic search,
then bounded graph associations) → fit a bounded recollection into model context →
respond. New eligible source revisions are queued asynchronously. Extraction does not
block the conversation. Missing embeddings leave clearly described lexical recall
available during download/reindexing; no cloud fallback or silent mode promotion.

Stable identity and durable changes may enter summaries. Routine plans and temporary
situations stay out of the permanent overview. An exam tomorrow can be useful in a
near-term recollection without becoming a lasting identity fact. Expiration never
asserts that the user took the exam. A confirmed meaningful event can remain historical.

Entity references have stable identity, context-bound aliases and supporting sources.
"My girlfriend" is a dated relationship role, not Clara's global synonym. Two Claras
remain separate unless evidence or the user resolves them. A shared laptop/gaming event
can link participants and objects, but graph paths alone never prove joint activity.
Custom relation labels are allowed; safe inverse/symmetry rules require explicit
semantics and evidence. Organizations can be Things. No fixed tiny edge vocabulary
should constrain ordinary relationships such as brother, mentor or landlord.

Repeated evidence consolidates a fact; retrieval frequency does not become evidence.
Duplicate extraction from one source does not create additional support or importance.
Durable facts do not expire because a user was inactive. Current relevance, historical
validity and optional visual fading are separate from erasure. Mixed/negative affect
stays attributed to a person, time and experience, not an objective judgment of School
or Clara. Mood-based retrieval remains outside the core release until independently
reviewed, and may never override relevant evidence or amplify a negativity loop.

## UI interaction contract

### Basic home and note

Use the user's You / Topics / People list. The Dreaming toggle lives in settings.
Opening a row shows its readable note, with direct editing and clear Save/Cancel.
Changes use a checked revision even for a manual editor; no silent lost updates.
Manage memory uses the same temporary work popup as Advanced, working on note drafts.
This does not add graph metadata, confidence badges or technical file names to Basic.

### Advanced v2 and node sheet

Keep the user's layout and typography hierarchy. Add sections when there is supported
content, not empty filler cards. Preserve highlighted links as designed; exact link
density can be reviewed in the prototype. A linked entity opens its node sheet. A
linked claim opens its memory entry; both use the user's detail-sheet visual family.

The sheet includes focused nearby connections, readable facts, learned/updated time,
validity where useful, source quotes, and grouped Pin/Edit/Forget actions with semantic
theme colors. Sources describe evidence honestly; an imported note is not fabricated
transcript evidence. Pin preserves availability but never overrides invalid sources,
privacy deletion or an expired plan. Entity edits and merges disclose their affected
memories and can be reviewed/undone when privacy permits.

The home density graph is text-free and decorative/informative. The whole card opens
expanded browsing. Expanded graph offers search/list alternatives, stable layout,
avatars, bounded degree sizing and mention-based saturation. Long chains load on demand;
rendering a viewport never limits what exists in memory. Time replay cannot reconstruct
forgotten information and must disclose the actual retained history range.

Dive deeper suggestions must have answerable evidence. Selecting a question expands
an answer with references in the current page/work surface; it does not create a new
normal chat or invent an answer just to populate the suggestion card.

Proposed Advanced behavior: "Last updated" refers to the displayed summary and Update
refreshes that presentation from current eligible memory. This is not yet approved;
review whether Update should also finish pending learning. Basic's manual Dream now
operation must be mapped explicitly rather than mislabeled as a summary refresh.
Neither action should silently initiate a full historical rebuild. Preparation
has its own explicit progress and pause/resume controls. If the underlying memory
changes, links must still resolve safely. Deletion invalidates affected summary text
immediately; regenerating a polished paragraph may happen afterward.

### Temporary work popup

The reusable reference is the Android assistant's presentation shell, not its VM.
That VM currently creates a persisted chat and the overlay hides some tool-only turns.
Use a dedicated memory session with bound owner/context, request, model, draft snapshot,
read/write versions, tool/status events and cancellation state.

Flow: request → live status/tool activity → optional clarification in the same input →
draft summary and before/after edits → Apply or Discard → refreshed page + Undo.
Tool results say proposed, not saved. Reads after draft edits see the draft. Read-only
requests need no approval. Reasoning UI uses only actual provider-returned visible
reasoning or a concise execution status; it must not invent a private thought stream.
No normal assistant name/avatar, ordinary reply bubbles, chat-list record or memory
extraction from the memory agent's own trace.

Unrelated background changes can merge. A conflict on an affected record/source/policy
keeps the draft visible and offers a refreshed preview. A global version fence on
unrelated changes would make daily editing frustrating.

Proposal for dismissal: collapsing the popup keeps one recoverable pending draft on
this character's memory page; it never Applies. Explicit Discard clears it. Returning
restores real state. Across process death, retain the request/draft but require explicit
retry for a possibly billed interrupted generation. This needs interaction approval.

Keyboard, gesture Back and tool details must not create a stack of nested sheets. Expand
tool details inside the popup. Keep a bounded popup height, reserve measured footer/IME
insets, retain scroll position and autoscroll only if the user is already following the
latest activity. Use existing motion/haptic preferences and accessible hit areas.

## Review gates before implementation

1. Confirm the Basic upstream behavior and the few deliberate deviations above.
2. Resolve context continuity: ordinary cross-chat character memory should work by default, without
   silently isolating every chat. Real/story boundaries need a user-defined context,
   not an LLM guessing from topic labels. The current app is not the full future
   persona/group-scene runtime; implementation must not claim those unsupported powers.
3. Review the temporary popup, including dismissal, conflicts, error/no-op, Apply/Discard.
4. Review both full screen layouts, node details, graph and floating input at small
   widths, large text, keyboard open, light/dark and reduced motion.
5. Review the scenario ledger and the engine requirements in ENGINE.md. Only then
   approve the implementation scope and sequence.

The interactive HTML artifact is a browser review simulation using actual app fonts.
It is not a native Compose screenshot, a working memory engine or proof of extraction
quality. A later native prototype must confirm component fidelity before full rollout.
