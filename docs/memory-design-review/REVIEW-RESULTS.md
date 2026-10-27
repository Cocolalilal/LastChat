# Review record and remaining gates

**Superseded visual confidence:** see REVISION-2.md. The earlier passing bounds/state
checks did not catch whole-panel transparency, clutter, weak grouping or graph quality.
The user correctly identified these as defects.

Four independent subagent reviews examined current-code engine integration, actual
RikkaHub behavior, native component reuse and adversarial memory semantics. The parent
combined their findings into the proposal and a 44-case behavioral ledger.

The adversarial review required these corrections to the proposal:

- Do not reduce the personalized remembering instructions to a fixed categorical enum.
- Uncertain privacy redaction must exclude/quarantine the affected unit before rebuild.
- Backup-contained tombstones alone cannot stop an older restore resurrecting data.
- Erasure and policy generations must apply across Basic/Advanced conversion.
- Differentiate explicit ordinary-chat memory requests, automatic learning and the
  deliberately reviewed management popup. Do not require a popup for every memory.
- Keep Basic's safety infrastructure an implementation proposal, not an excuse to
  require the entire Advanced graph subsystem for a note browser.

Independent prototype inspection caught fixture bugs before delivery: some non-Clara
edits falsely reported success, forgetting could leave a dating claim in the summary,
and substring matching confused expert with ex. The fixture was corrected and rechecked.
These are simulation bugs caught in review, not evidence that a real extractor works.

The browser interaction checks cover the modeled fixture flows: Basic Save/Cancel,
Advanced edits, source/node opening, popup tool activity, staged changes, Apply/Discard,
close/reopen, cancellation and Undo. Layout checks include narrow/light/large-text CSS
stress cases, visible control bounds and last-row clearance above the floating footer.

Limits remain explicit:

- No production memory feature or permission fixes were implemented in this review.
- No model was evaluated for extraction accuracy, hallucination, identity resolution,
  privacy, temporal interpretation or relevance. The fixture ledger is a proposed test
  set, not a passing scorecard.
- HTML rendering does not establish native Compose fidelity, actual device keyboard,
  TalkBack, Android font scaling, haptics, performance, battery or thermal behavior.
- Graphs are illustrative; density scaling, long-chain rendering, retrieval and replay
  need their own native/engine validation.
- Mode controls preview layouts; no real conversion/migration is demonstrated.

Before full implementation: review the two screen flows and popup; agree the few
behavioral decisions in README.md; render the agreed flow with actual native components;
then implement and test the engine in incremental, reviewable slices. Run the real
processing model on the fixture ledger before a daily-chat-readiness claim.
