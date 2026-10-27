# Daily-use engine review — 27 October 2026

Read-only review of the current `lastchat_dev` source and `docs/memory-design-review`.
No app implementation or real-model evaluation was performed. The current proposal has
useful safeguards, but it is not yet a demonstrated good memory engine. UI fixture tests
cannot establish extraction accuracy, relevance or privacy correctness.

## Most important corrections before implementation

### 1. Basic Dreaming cannot reuse the old Advanced boolean

**Confirmed current-code behavior:** `PreferencesModels.kt:299` normalizes
`enableMemoryConsolidation=true` by also enabling RAG, recent-chat reference, Core and
Episodic inclusion. Mapping Basic's new Dreaming toggle to that setting would silently
turn Basic into much of the existing Advanced feature again.

**Required separation:** mode, automatic note organization and retrieval representation
must be separate policies. Basic stays readable notes with RikkaHub's profile/directory/
on-demand read behavior. Dreaming changes automatic writing/organization, not whether
embeddings or a graph are activated. Include normalization and mode-switch tests.

### 2. Message count is not importance

**Confirmed current-code behavior:** `MemoryConsolidationPolicy.kt:3,24` returns
NotWorthwhile for fewer than four nonblank user/assistant messages. The worker at
`MemoryConsolidationWorker.kt:163–171` can mark that conversation reviewed/complete.
A single brief exchange containing an important new address, graduation, death or move
can therefore be skipped by background processing.

**Required behavior:** queue eligible changes even in short exchanges. Small batches can
be coalesced for efficiency, but an idle flush/explicit request must not mark unexamined
important information permanently processed solely because message count was low.
A no-op must mean evaluated and nothing worth saving, not never evaluated.

### 3. Current prompts would contaminate Basic's intended behavior

**Confirmed current-code behavior:** `MemoryContextPlanner.kt:124` tells tool-capable
models to proactively record user facts and plans. The new Basic reference says ordinary
learning belongs to Dreaming and conversation writes are explicit requests. Reusing the
old prompt would violate that, even if the settings screen looks correct.

**Required behavior:** mode-aware injection/tool instructions and host permissions must
agree. Turning Dreaming off cannot leave an old proactive-memory instruction active.
Ordinary direct requests can authorize bounded checked updates; users should not have to
approve a popup after every plain “remember that I prefer tea.” The deliberate Manage
Memory work session still uses Apply/Discard as the user requested.

### 4. Recall must work for short names, languages and old dates

**Confirmed current-code mechanisms:** `MemorySearchService.kt:140,285` drops query tokens
shorter than three characters. `findConversationRecallSpans` at approximately line 76
interprets stored timezone-less LocalDateTime using the device's current timezone.

**Consequences:** deterministic recall can miss “AI,” a two-letter person's name or
short CJK terms; travel/zone changes can move historical hits across a date filter.
A model query-expansion call may sometimes mask the lexical weakness, not repair it.

**Required behavior:** direct entity/alias matching supports short names and Unicode;
lexical segmentation is evaluated on supported languages. New timestamps preserve the
original temporal context. Old uncertain offsets stay uncertain; no claim that a current
device timezone recovers them.

### 5. Everyday retrieval should not require a small agent expedition

**Confirmed current-code path:** `MemorySearchService.searchMemory` first attempts model
query expansion, can summarize two past-chat spans with separate calls and finally
synthesizes findings. A simple personal lookup can therefore take several model calls.

**Proposed requirement:** do scoped entity/note/lexical/local-semantic retrieval first.
Use deeper generative search only when evidence is insufficient, not automatically for
every question. Never fabricate “confidence” from a search score as if it established
truth. Cache only with source/revision/permission identity and invalidate privacy changes.
This is latency/resource control, not a daily request quota.

### 6. Tool support is an unresolved everyday compatibility decision

Basic's RikkaHub-style directory/read model depends on conversation tool support. The
proposal does not yet explain what happens for a configured model without tools.

**Decision to make before shipping:** either provide bounded host-side lexical note
selection for such a model, or clearly expose the capability limitation at selection.
Do not advertise identical recall and then silently omit all topic notes. The selected
memory-processing model also needs a validated execution contract; it must not silently
fall back to a different provider because tool calls fail.

Also separate storage size from request capacity: RikkaHub's allowed note size can
exceed a small model's context window. “Include profile/preferences fully” must not
silently truncate identity or overflow the actual request. Define a bounded authored
core plus relevant note reads/fallback, and disclose this deliberate compatibility
adaptation rather than claiming exact behavior for every configured model.

### 7. Basic source retraction needs a feasible granularity contract

The docs propose source-backed fragments inside readable prose, then exclusion/repair
when mapping is uncertain. That is safer than fabricated certainty, but incomplete.
If the note has twenty useful facts and one source changes, excluding the whole profile
could make the assistant seem to forget everything until repair finishes.

**Required design proof:** show how model-authored patches map to hidden stable note
fragments/revisions and how manual Save affects authorship. Preserve explicitly authored
text. Specify the smallest safely excludable unit and user-visible repair behavior. Do
not smuggle a full claim graph into Basic simply to make the provenance story easier.

### 8. Scope should be explicit once, not an interruption on every fact

The current Conversation model has one assistant ID and no complete persona/world/scene
knowledge runtime. A topic called Roleplay cannot establish permission or reality.

**Proposed default:** character-wide continuity across ordinary chats. Let users select
an explicit story/context when needed. Do not auto-isolate every new chat and then call
empty recall “privacy.” Do not require approving a context classification for every
sentence. Mixed real/fiction speech in one context remains a behavioral ambiguity to
review; the model's guess cannot establish access rights.

## Everyday conversations that reveal the actual product

| Conversation | Expected memory and reply behavior |
| --- | --- |
| “I moved to Vienna.” / “Nice!” then leave | Advanced/Dreaming must not ignore a short but durable update. Record the new reported residence only in its permitted real context. No invention of a street address. |
| “What was that doctor called?” | Retrieve the relevant person/note with support. Do not run multiple speculative model searches when a direct scoped match exists. If ambiguous, ask a short useful clarification. |
| “My cat Milo died.” after previously saying four cats | Remember the meaningful event and emotional context. Do not automatically assert the current household has exactly three cats unless the complete inventory/count is actually supported. |
| Assistant guesses “Your cat Milo…”; user ignores it | Do not turn the assistant's own guess into new user biography during Dreaming. Silence is not confirmation. |
| Character says “I killed the king” in dialogue | Story-scoped attributed claim, not automatically objective world truth or real-user crime. A narrator-authorized event is a different source authority. |
| “Clara is my ex now.” | Current relationship changes. The exact breakup date remains unknown; first mention and recording time are not onset/end-event times. Keep useful history without continually surfacing the ex in unrelated chat. |
| “Don't save my address. My cat needs medicine.” | Honor the restrictive rule immediately without storing the address in agent policy. Keep safe pet information. Memory does not become a medication-reminder service without an actual reminder action. |
| User fixes one line in a long topic note while Dreaming runs | Checked Save merges or shows a real conflict only for affected data; unrelated background edits must not repeatedly invalidate the editor. |
| User asks “How much does 15×7 equal?” | Answer normally. Deep personalization is not demonstrated by mentioning pets or school when irrelevant. |
| User opens a new chat after a month | Character continuity still works for durable facts. Old plans are historical/expired, not completed. Background scheduling must not silently lose pending facts because they are outside a recent window. |

## Current code hazards remain unresolved

The prior review's owner-unscoped memory-ID mutation, temporary-chat tool access, missing
Core source retraction, snapshot weakness and process-only deletion ledger remain actual
source findings. They have not been repaired by these documents. In particular:

- Owner checking must happen before reading/returning previous content, not only before
  the final SQL write (`GenerationHandler.kt:2164,2198`).
- Temporary mode must deny both reads and writes at the tool capability boundary
  (`ChatService.kt:1647,1706` with `GenerationHandler.kt:700`).
- Regeneration/source eligibility must use selected committed message/tool versions, not
  every stored variant or a visual message index.

## Recommended sequence and evidence

1. Agree the small daily-use contracts above, especially Basic's actual behavior and
   ordinary-chat continuity. Remove weak or contradictory defaults before adding tables.
2. Review a lean native visual prototype using actual LastChat components, including
   large text and opaque/legible modal layering. Do not promise the app will fix a bad
   browser composition automatically.
3. Implement owner/context capability and checked-write boundaries first, separately
   testable from advanced extraction. Then Basic notes/Dreaming with its minimum needed
   safety layer. Keep Advanced parsing/graph/retrieval independently reviewable.
4. Run the real selected processing model against representative conversations. Measure
   unsupported facts, false identity merges, stale-current-state recall, missed durable
   updates, irrelevant personalization, privacy leaks and latency. Agree failure-specific
   acceptance criteria; a high overall score can conceal an unacceptable address leak.

There is no basis yet to call the proposed engine perfect or daily-chat-ready. The
current review improves the requirements and identifies real failure paths; it is not
an extraction-quality result.
