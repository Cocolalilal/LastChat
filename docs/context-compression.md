# Conversation compression

LastChat's previous compression was unnecessarily lossy. This change keeps the existing
append-only milestone format and stored original messages, while changing when compression
happens, what the summarizer sees, and how much recent history stays verbatim.

## Upstream comparison

Primary sources inspected on 2026-10-20; upstream `main` and documentation can change.

| Agent | Publicly observable approach | Lesson applied here |
| --- | --- | --- |
| [Codex local compaction](https://github.com/openai/codex/blob/main/codex-rs/core/src/compact.rs) and [checkpoint prompt](https://github.com/openai/codex/blob/main/codex-rs/prompts/templates/compact/prompt.md) | Structured continuation checkpoint covering progress, decisions, constraints, next steps and references. The local history builder also retains user text within a separate token allowance. | Write a continuation checkpoint with concrete constraints and next steps, rather than a short topic recap. |
| [Claude Code](https://code.claude.com/docs/en/how-claude-code-works#when-context-fills-up) and [what survives compaction](https://code.claude.com/docs/en/context-window#what-survives-compaction) | Clears older tool output before summarizing when needed. Persistent instructions are reloaded. Users can supply compaction focus instructions. Its documentation acknowledges that early instructions can be lost. | Delay destructive changes until needed, keep system instructions separate, and explicitly preserve requirements and corrections. |
| [Aider's history implementation](https://github.com/Aider-AI/aider/blob/main/aider/history.py) | Token-based head/tail split: summarize older dialogue and retain recent dialogue, with recursion if the combined result remains oversized. | Select the smallest necessary old prefix and retain recent original turns. |
| [OpenCode configuration](https://opencode.ai/docs/config/#compaction) | Separately configurable automatic compaction, optional pruning of old tool output, and a reserved token buffer. | Treat tool-output reduction, semantic summarization and input/output budgeting as separate concerns. |

These are design references, not proof that any agent has lossless memory. Codex also has remote
provider compaction paths; its local checkpoint prompt does not describe every Codex session.
Claude Code's public documentation does not expose its complete production summarization prompt.
Provider-specific opaque compaction was not adopted because LastChat supports multiple providers.

## Problems found in LastChat

- `toSummarizableDigest` cut dialogue at 1,000 characters per part, tool arguments at 250,
  and tool results at 400. A requirement near the end of a long message never reached the model.
- Every segment had the same instruction: under 300 words. Large segments had no additional
  space for constraints, exact identifiers, failures, or next steps.
- Large context windows could summarize after 20 turns even with ample space available.
- Automatic pressure planning counted the full original history after it had already been
  replaced by a summary. It also subtracted the output reserve twice.
- Slicing started with a fixed chunk even if removing a smaller prefix satisfied the token
  target. A critical pressure tier could reduce the recent tail to one turn immediately.
- The helper model's capacity determined chat retention. An independent smaller summarizer
  could therefore discard more recent dialogue, while its request was not itself bounded.
- Tool receipts were produced even when history fit. Some receipts asserted successful edits
  or exit code zero without supporting evidence, and `web_search` matched the shell category
  because the classifier searched for the substring `sh`.
- Blank summary text could still advance the coverage index.

## Revised behavior

1. **Preserve evidence that fits.** History preparation leaves dialogue and tool results
   intact while within the budget. Existing media limits still apply. On overflow, old tool
   results can be reduced before whole turns are dropped. Receipts retain both ends of long
   command/search output and mark omissions; short results are kept if a receipt would be longer.
2. **Use active context for automatic triggers.** Covered raw history is excluded from the
   estimate; the existing summary is counted as fixed input. Turn/token cooldowns remain,
   but there is no periodic 20-turn trigger. Output reservation is deducted once.
3. **Retain more original dialogue.** Automatic slicing removes the smallest complete prefix
   needed for the target. The target leaves 60%/65%/70% of discretionary input for small,
   medium and large windows, with an allowance for the new checkpoint. Manual refresh uses
   bounded chunks and normally keeps two/four/six recent turns. Actual token pressure can
   still require a smaller tail, but never consumes the last active turn. Unresolved tool
   exchanges and boundaries inside turns cannot be marked covered.
4. **Separate chat and helper budgets.** The chat model determines retention. The helper's
   own input, response reserve and safety margin determine each summary batch. Each request
   has at most 16k estimated input tokens and normally up to 4,096 tokens for checkpoint text.
   The character's detail preference scales that allowance, with an 8,192-token hard ceiling. Explicit
   thinking budgets reserve additional output space, subject to the helper model's limits.
5. **Send the source text.** Dialogue, tool arguments and tool results are no longer clipped
   by default. Oversized transcripts are split into ordered requests without skipping source
   characters. Line breaks are preferred; UTF-16 surrogate pairs are kept together. Splitting
   a giant turn across helper requests does not split its persisted coverage boundary.
6. **Produce structured checkpoints.** The prompt prioritizes exact user requirements,
   corrections, decisions and rationale, rejected approaches, current state, pending work,
   references, and observed execution results. Its target grows with the source size rather
   than enforcing 300 words. Instructions and transcript are separate system/user messages.
7. **Commit after success.** All batches must return nonblank checkpoints without a reported
   truncation/filter stop. The combined milestone must save estimated tokens. Failed requests
   and cancellation do not advance coverage. Before saving, the source prefix and previous
   summary are checked against the latest conversation; appended messages are preserved.

Existing milestones remain frozen and new ones append, avoiding repeated summary-of-summary
rewrites. No database migration is required. The original conversation remains
stored. An old summary cannot
recover details it already lost; revert it and summarize the original messages to rebuild it.

## Unified controls and quiet background work

Updated 2026-10-21:

- Characters have one context mode: **Automatic** or **Only when I ask**. Automatic mode
  responds to token pressure; manual mode still enforces the model's request budget.
  Legacy smart-management and auto-summary preferences migrate into this single choice.
  Competing message-count, search-result and image-age retention controls are retired.
- **More detail**, **Balanced**, and **More room** adjust both when optimization happens
  and how much space its checkpoint receives. Advanced controls retain a chosen number of
  recent exchanges and supply details to preserve. Tight context limits can require a smaller
  recent tail. A dedicated summarizer model remains optional.
- The context meter blends into the native Material expressive loading indicator while
  queued, optimizing or waiting to apply. Successful compression morphs into a circle before
  returning to the meter. Reduced-motion settings skip that animation. There are no completion
  notifications, banners, toasts or haptics.
- The meter and composer menu open the same dismissible **Chat context** popup. It uses
  the shared floating dialog surface, typography and pill actions, with compact rows rather
  than stacked cards. It shows usage, the eligible source range, background progress,
  an editable checkpoint and context
  details. Closing it leaves work running. New checkpoints record coverage boundaries so
  **Undo last optimization** restores just the previous checkpoint. Legacy or manually edited
  checkpoints offer **Restore original context** instead. Neither action deletes messages.
- Persisted conversations use unique, ordinary WorkManager jobs, without a foreground
  service or notification. A per-conversation shared job prevents duplicate helper requests;
  a global semaphore limits compression to one helper operation at a time. Cancelling a UI
  waiter does not cancel compression; the popup provides an explicit Stop action. Transient
  conversations stay in application memory rather than being persisted for background work.
- The executor waits for any current reply before applying. Source edits, context resets,
  checkpoint edits and changed retention preferences invalidate its draft. A conditional,
  metadata-only database update preserves messages appended while the helper was running.
  Older streaming snapshots preserve newer checkpoint metadata. Clearing context cancels
  compression and respects the new active-history boundary.

WorkManager can restart pending work after process death, subject to Android scheduling.
Individual helper-batch drafts are not persisted, so an interrupted operation may need to
generate its checkpoint again. Leaving the popup or navigating elsewhere in the app does not
interrupt an active operation. Failed automatic attempts have a five-minute cooldown.

## Validation and remaining limits

Validated in the task environment:

```sh
./gradlew :ai:testDebugUnitTest :app:testStableDebugUnitTest \
  --tests 'me.rerere.rikkahub.service.ContextCompressionTest' \
  --tests 'me.rerere.rikkahub.data.model.AssistantContextSettingsTest' \
  --tests 'me.rerere.rikkahub.data.ai.HelperModelGenerationParamsTest' \
  --tests '*ContextManagementRenderTest' \
  -Dlastchat.renderDir=/workspace/scratch/context-popup \
  iosPortabilityReport --configure-on-demand --max-workers=2 --console=plain
```

Result: 216 AI-module tests and 20 app tests passed, including app Kotlin compilation and
four Robolectric native-graphics renders of the popup (light/dark, ready/running), using
LastChat's default Seafoam Mint palette, AMOLED surface conversion, typography and shapes.
The non-failing iOS portability report was generated. `git diff --check` passed.
The requested repository graph refresh could not run because `graphify` is not installed.

Regression coverage exercises late requirements and tool details beyond the old clipping
limits; a giant multilingual/emoji turn batched for a 4k helper; exact source reconstruction;
per-request budget fit; blank/truncated intermediate failures; ordered successful batches;
unchanged in-budget tool evidence; truthful receipt outcomes; no low-pressure 20-turn trigger;
single output reservation; minimal-prefix slicing; unresolved tool-call protection; style and
recent-tail preferences; legacy settings migration; source validation; checkpoint undo;
streaming metadata preservation; explicit context reset; shared-job deduplication; caller
cancellation; and explicit cancellation followed by retry.

The tests validate selection, budgeting and failure handling without calling a paid model.
They do not measure semantic recall of generated summaries. A follow-up evaluation should use
real long chats, ask factual/constraint questions before and after compression, and compare
recall and input cost across the user's actual models over several compaction cycles.

Summaries still depend on model quality. Token counts are conservative estimates rather than
provider tokenization. Batching costs additional calls for long segments, and isolated fragments
can lose cross-fragment nuance. Signed thinking and raw media are not transcribed by this path;
existing text/OCR and attachment descriptors are used. Accumulated frozen milestones can still
eventually exhaust a window; the final context gate remains the overflow fallback. Hierarchical
archive retrieval would be a separate change, not a guarantee supplied by this implementation.
