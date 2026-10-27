# Revision 2 — critical review after the user's screenshots

Status: review proposal and browser fixture only. No replacement engine or production
UI has been implemented. The earlier visual artifact is superseded for this review.

## Why the first prototype was not acceptable

The user's screenshots exposed real faults, not harmless screenshot artifacts:

- Opacity animated on the entire popup/sheet, so text behind the panel became readable
  through it during transitions. A surface must remain opaque while moving; scrim
  opacity is independent. Mid-transition frames are part of visual correctness.
- Status, request heading, explanation, tools-completed card, proposed-change heading,
  before/after boxes, breakup explanation, summary preview and three actions competed
  in one small popup. Repeated explanatory copy was filler.
- Separate rounded cards did not establish Material You's connected grouping. Logical
  tool rows, suggested questions and actions need shared grouping, small internal seams
  and appropriate outer/inner corners. Unrelated Overview and Routine sections remain
  separate, as in the user's design.
- The graph was a repeated radial fixture. It could not demonstrate natural clusters,
  bridges, long chains or a different focused neighborhood.
- Bounds checks alone missed transparency, focus escaping behind a modal, typography
  problems and meaninglessly repeated feedback. Passing them did not make the UI good.

## Revised visual contract

Preserve the user's Advanced v2 and Basic compositions. Reduce the popup to one compact
work state, a short request context and a connected group of expandable tool rows.
Remove demonstration copy from the app surface; label the simulation in reviewer chrome.

The review state shows a compact grouped change. Keep exact before/after information,
optional detail disclosure and Apply/Discard. The existing composer handles refinement;
a third permanent Refine button is unnecessary. Explanations belong in expandable
details when they help a decision, not as permanent paragraphs describing the engine.
Use transient feedback for completed/discarded actions rather than persistent banners.

Opaque surfaces, one active presentation layer and a separate scrim must hold through
animation, dark/light, narrow widths, large text and viewport changes. The popup and
composer need a coherent focus scope; background content is not keyboard/assistive-tech
interactive while work is modal. Long headings cannot use a fixed-height clipped layout.
Reserve measured footer/inset clearance; scrolling content must remain fully reachable.

The graph prototype now uses seeded force/collision layout, irregular clusters and
bridges. Overview and focused node use distinct data. Position stability, finite bounds,
separation and paths are checked in the fixture. This is still illustrative: it is not
a tested production layout for tens of thousands of nodes or evidence of graph retrieval.

## Engine confidence must be earned separately

The UI failures do not prove that every engine idea is wrong. They do show the review
bar was insufficient. The engine is not proven by this prototype, its transaction
requirements, or a list of expected test cases. Actual extraction/recall evaluation on
the selected model remains required.

Further current-code findings from the daily-use audit:

| Finding | Consequence | Proposed requirement |
| --- | --- | --- |
| PreferencesModels.normalizeMemorySettings forces RAG/recent/episode flags when consolidation is on | Mapping Basic Dreaming onto that boolean silently enables Advanced-like behavior | Independent Basic/Advanced policy; normalization and mode-switch tests |
| MemoryConsolidationPolicy and worker skip chats below four nonblank messages | A short but important move/medical/preference update may never be learned | Turn count may schedule a batch, never decide that content is unimportant |
| MemorySearchService discards tokens shorter than three characters | Short names, AI and short CJK queries can miss recall | Exact/alias/Unicode-aware retrieval; no English token-length floor as authority |
| Historical LocalDateTime is interpreted with the current timezone | Travel changes time-filtered recall | Persist offset/instant for new sources; retain uncertainty for old sources |

These are observed current-code risks, not fixes made by the proposal. Keep owner/scope,
temporary-tool permissions, selected message versions and source retraction as release
blockers alongside these findings.

## Strict engine mode boundary

- Basic, Dreaming off: readable notes, profile/preferences plus on-demand reads, explicit
  requested updates. No embeddings and no automatic casual fact-writing.
- Basic, Dreaming on: the same note representation and retrieval, plus background note
  organization. Turning it on must not enable embeddings, graph retrieval or old episodes.
- Advanced: structured temporal/source-aware connected memory and fixed local embeddings.
  Its background-learning permission is explicit; a pause stops new work without hiding
  existing memory or changing the mode.

Basic must remain close to actual RikkaHub. Start with a narrow note/version/draft
contract. Any source-support additions must be justified by concrete deletion/edit cases,
not silently import the entire Advanced graph architecture. If note-level support cannot
safely identify a removed fragment, withhold the affected model-authored material from
recall and repair from eligible sources and explicitly authored corrections. Do not send
stale private prose back into a model merely to ask it to remove that prose.

## Additional compatibility and resource decisions

The old proactive-memory prompt also conflicts with Basic Dreaming-off behavior; changing
only the toggle is insufficient. Mode-aware prompts, tools and host permissions must
agree. Direct scoped lookup should precede generative search, because the current search
path can add query expansion, two span summaries and a synthesis call for a simple recall.

Basic's on-demand reads depend on a tool-capable conversation model. Decide and test a
bounded host-side relevant-note fallback for models without tools, or explicitly disclose
the limitation before selection. Do not silently lose topic recall or switch providers.
An overlong profile or multiple read notes must respect the actual request token budget;
retain the complete note in storage and provide bounded reads/continuation, not arbitrary
string truncation or a claim that every model sees the full store. These are compatibility
requirements to resolve before implementing, not capabilities established by this fixture.

## Status and Update semantics need explicit agreement

The earlier proposal chose summary refresh for Advanced's Update button without an
explicit user decision. Keep that as a proposal. Review whether Update should finish
pending eligible learning as well as refresh the summary, and how Basic's manual Dream
now maps to the same control. The UI must show the relevant freshness, not conflate
last note update, last learning run and last summary generation. This browser fixture
does not demonstrate a real Update or Dreaming job.

## Remaining product decisions

The next engine review must settle these with ordinary conversation examples, not
assume the answer from a schema:

1. What ordinary Dreaming may commit automatically, versus ambiguous changes needing
   review. Do not ask for approval for every mundane memory, or let newest text always win.
2. How real/story context is selected while keeping normal character-wide continuity.
   Topic clustering cannot grant permission or infer a complete persona/group runtime.
3. How pending drafts are reopened and what survives dismissal/restart. Never Apply by
   dismissal, replay possibly billed calls automatically, or make unrelated background
   edits invalidate a draft.
4. Source deletion and cross-mode forgetting semantics, including what manually edited
   note text counts as independently authored. Approving one changed word must not turn
   every pre-existing claim in that note into permanent independent evidence.

The fixture ledger is a review instrument. Model evaluation must measure unsupported
claims, missed important facts, false merges, stale recall, irrelevant recall and privacy
leaks. Source ownership and permission tests are deterministic host responsibilities;
semantic extraction correctness is not established by a valid quote or JSON shape.

## Revised fixture verification and limits

Independent browser checks sample both modal surfaces and their ancestor opacity at
multiple animation fractions; surfaces remain fully opaque. Geometry checks cover dark,
light/narrow, short and CSS large-text views. Additional checks cover inert background,
keyboard scope/restore, complete After/Summary scroll reachability, Apply hit targets and
final-row clearance. A reviewer-style paragraph selector also leaked dark-theme ink
into light panels; it is now scoped outside the app surface. Sampled After/tool/source
foreground/background roles exceed 4.5:1 contrast in both themes. This does not establish
contrast of every theme role or native Android accessibility.

The illustrative graph has independently checked deterministic finite geometry, node
separation, connectivity and longer paths. A different local neighborhood appears in
details. The fixture does not establish production graph scaling/retrieval/privacy.
Browser state checks verify modeled editing/forgetting/Undo/cancellation behavior. They
do not evaluate an actual memory model or the unimplemented engine proposal. No app
implementation has changed in this revision.

Live progress must preserve user-owned UI state: an expiring notice or completed tool
run cannot clear unsent composer text/caret, close inspected tool details or steal focus
from typing. If a focused activity row disappears on completion, restore focus to a
meaningful ready-state target. Native implementation needs stable component identity and
separate editor/job state; a correct transaction engine does not prevent these UI losses.
The independent browser review explicitly exercises these transition races.
