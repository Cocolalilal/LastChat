# Avatar behavior, scheduling and integration audit

Read-only audit of `lastchat_dev`; no application code changed. Line numbers reflect the working tree during review and may move as the authorized expression/aliveness fixes land. All paths below are relative to `/workspace/LastChat/`. `A/` abbreviates `app/src/main/java/me/rerere/rikkahub/ui/components/avatar/animated/`.

Scope: AvatarMind, HumanBehavior, AlivenessEngine, IdleEpisodes, AvatarContext, AvatarStage, Personality, ToneAnalyzer, MarkLifecycle, AvatarMotionState integration and ChatPage/ChatMessageV2 event producers. Rendering geometry and individual spring trajectories are covered by separate audits. Findings below are proposals requiring user approval; they do not authorize additional implementation.

## Highest-value fixes

### B1 — Lost gaze-shift notifications make coordination depend on which subsystem initiated the look (P1, code defect)

Evidence: `A/AvatarMotionState.kt:221` raises `bigShift` inside `setTarget`. `GazeDirector.update` clears it immediately at entry (`:243`). Most producers execute before that update: typing at `:1071`, aliveness/social at `:1125`; `gaze.update` runs at `:1167`, and `blink.saccade` reads the cleared flag at `:1169`. `mind.update` reads the previous frame flag at `:1053`. Later producers in `humanUpdate` (`:1334`) can survive into the next frame. Thus otherwise identical event looks can get different blink/gaze-lead treatment solely because of call order.

Proposal: publish a monotonically increasing target-shift revision with amplitude/cause. Consume the revision exactly once for blink association and face lead after all target-selection producers have run, or preserve a pending shift notification until both consumers acknowledge it. Do not infer a new shift from the per-frame tracked target.

Visible impact: event, typing and busy looks coordinate eyes, blink and expression consistently; avoids apparently arbitrary face timing. Tradeoff: existing random draws/blink sequence change; avoid introducing a blink for every tracking update. Validate the same >0.4 gaze move from typing, aliveness, perception and direct debug sources; assert one notification, equal lead timing, no repeated notification during tracking.

### B2 — A single aliveness face/accent is charged twice and can exhaust its own permission to appear (P1, code defect)

Evidence: `A/AvatarMotionState.kt:2197-2202` prechecks the spontaneous budget and records every non-blink beat as `Lane.Body`, before `playBeat`. `playBeat0` starts accents/faces at `:2022-2030`; accents independently check the budget at `:1904` and `mind.landAccent` counts again (`A/AvatarMind.kt:301-306`); a face is counted at `landFace` (`:290-297`). A selected accent using the final available slot consumes it before the real face action checks, then queues/expires without appearing. Successful accents/faces consume two slots even though there was one selected expressive action. Glances rejected because gaze is already focused (`AvatarMotionState.kt:2034`) are still charged.

Proposal: separate proposal, successful commit and audit recording. Charge one unique action ID only after the visible channel accepts it; reserve a token for a deferred action if required and release on cancellation/expiry. Decide whether compound body+face stories intentionally cost one or multiple units; make that policy explicit.

Visible impact: quiet personalities stay alive consistently instead of alternating an initial burst with unexpectedly long silence. Tradeoff: removing accidental throttling can increase real activity; preserve current observed cadence with explicit weights/caps. Validate budget with exactly one slot left for Accent, Face, Gesture and refused Glance; assert displayed actions, counts and cancellations match.

### B3 — Perception has no event coalescing or visible-action arbitration for bursts (P1, robustness defect with user-visible animation risk)

Evidence: `A/AvatarMotionState.kt:1759-1771` appends every noticed event to `pending`. `humanUpdate:1792-1804` executes every due callback in the same frame, in append order. Repertoire results can independently trigger `gaze.focus`, gestures, accents and reactions (`:1845-1866`). Frame delays can make several percepts land together. The outer stage queue cap of 16 does not coalesce pending percepts. `MotionDirector.gesture:297-302` prevents duplicate gesture kinds but allows different gestures to layer, and gaze equal priority can retarget.

Proposal: collect percepts due this frame, coalesce repeated scroll/keyboard/layout hooks, rank by directness/salience/recency, and commit one primary attention response per perceptual beat. Preserve explicit compound actions (a look plus nod) as a single intent. Expire low-value pending events that a stronger event makes obsolete. Direct touch/error remains prompt.

Visible impact: fast scroll → keyboard → send sequences feel like one decision instead of several tiny switches. Tradeoff: fewer incidental reactions; need intentional rules for which events may merge. Validate burst scenarios at 30/60/120 Hz and after a 100 ms frame gap; inspect target direction reversals, simultaneous gesture count, time-to-touch/error response.

### B4 — Deferred accents bypass the context check when they finally play (P1, code defect)

Evidence: `accent` checks `ContextRules.accentOk` when proposed (`A/AvatarMotionState.kt:1902`), but the queued closure directly runs `rig.play`/`mind.landAccent` (`:1905-1906`). The face pump checks rig availability, face ownership and budget, not context (`:1265-1268`). `contextUpdate:1990-1999` clears disallowed reactions and rest/drowsiness but does not clear/revalidate a queued or active Drowsy rig accent. A Drowsy accent proposed while idle can therefore begin after context becomes Thinking/Streaming within its 1.2 s TTL.

Proposal: route deferred accents through one commit function that revalidates current context and any spin/negative exclusions. For an already active disallowed accent, ease its weight to zero promptly with a context-aware release rather than cancel its pose instantaneously.

Visible impact: the avatar stays attentive when work begins instead of briefly getting sleepy. Tradeoff: contextual exits need a short release profile to avoid cuts. Validate idle Drowsy proposal blocked behind another owner, then start streaming before queue lands; assert no Drowsy onset and continuous lid motion.

## Integration and meaningful behavior

### B5 — Composer replacement edits look like inactivity, and gaze follows a character-count guess rather than the actual caret (P2, first part is a defect; caret upgrade is a proposal)

Evidence: `A/AvatarStage.kt:101-112` returns immediately whenever a nonempty edit has the same length. `ChatPage.kt:1320` correctly calls this for changed `composerText`, but replacements do not refresh `composerLastKeyMs` or `composerLineLen`. `AvatarMotionState.kt:2446-2448` stops following after 1.6 s based on that stale timestamp. `caretPoint:2397-2403` uses the last line's length modulo a hardcoded character count, and always puts the caret on the composer vertical center. It ignores cursor selection, variable-width glyphs, wrapping, RTL and editing earlier lines.

Proposal: first accept text revisions independent of length. Subsequently send a real caret rectangle plus edit timestamp/revision from the text field's layout/selection API, retaining the current approximation only as a fallback. Treat selection movement separately from typing.

Visible impact: gaze keeps following active editing and no longer jumps left at the arbitrary character modulo. Tradeoff: measuring caret requires UI plumbing and RTL/multiline tests. Validate same-length replacements, selection/cursor moves, newline relocation, proportional text, RTL and wrapped lines.

### B6 — Stage targets/events/composer share incompatible windows and conversations (P2, architecture defect; especially visible with dialogs/multiwindow)

Evidence: `A/AvatarStage.kt:20-29` gives avatar entries a `windowKey`, and neighbors filter it (`:165`). Targets/owners are keyed solely by target kind (`:115-124`), composer is global (`:93-112`), and Event carries no window/chat ID (`:68-79`). `Global` is the production default (`:215-220`); repository search found no production `LocalAvatarStage provides` override. `AnimatedMarkAvatar.kt:151-159` explicitly registers real window identity. `avatarTarget` publishes `boundsInWindow` without that identity (`AvatarStage.kt:227-235`). Every running avatar consumes all stage events (`AvatarMotionState.kt:2520-2544`). Tone emission in `ChatMessageV2.kt:1542-1547` likewise has no reply owner/conversation ID. `ChatUtil.kt:88-89` and `ChatPage.kt:703` bypass the composition local and call Global directly.

Proposal: make stage UI state scoped to window + conversation; include origin scope and semantic owner in event envelopes. Keep the global registry only for same-window social discovery, if that is desired. Route utility/error emitters through the current stage/scope. Define explicitly whether a reply's emotion belongs to one assistant or all on-screen avatars.

Visible impact: avatars look at the UI they are actually beside and do not inherit unrelated replies' feelings. Tradeoff: event producer signatures and dialog integration change. Validate two windows with different composer positions, overlay dialog and underlying chat, switching conversations, and multiple assistants; verify event routing and coordinate conversion.

### B7 — Reply-end appraisal is computed but does not control the happy reaction (P2, semantic defect/art-direction decision)

Evidence: `Appraisal.replyEnd` can return `Nothing` for valence ≤−0.2 (`A/AvatarMind.kt:151-155`). `AvatarMotionState.kt:2233-2248` computes `meaning`, uses Relieved only to start a sigh, then chooses the Happy branch for `r < 0.6` regardless of meaning or negative mood. Separately, body busy completion triggers this path whether the request was successful, cancelled, or simply transitioned to Idle; lifecycle hints do not distinguish cancelled completion (`MarkLifecycle.kt:18-25`, `MotionDirector.kt:399-409`).

Proposal: provide a typed outcome {success, cancelled, failed, interrupted} and use appraisal to choose a restrained success acknowledgement, relieved exhale, or quiet neutral return. For success, retain low-probability delight only when supported by content/mood. User approval needed for the desired emotional vocabulary.

Visible impact: gestures read as a reaction to what happened instead of a victory routine at the end of any work. Tradeoff: less overt cheerfulness; product needs a reliable completion/cancellation signal. Validate sad-content successful reply, ordinary success, long successful wait, cancellation, failed request, and immediate new request; no happy acknowledgement on unsupported outcomes.

### B8 — Tone loses arousal and causal priority; classifier fallback can misread ordinary technical text (P2, defects plus art-direction choice)

Evidence: tone integration exists in `ChatMessageV2.kt:1525-1547` (not dead code). It emits valence, intensity and emotion, dropping `ToneHint.arousal`; `AlivenessEngine.kt:153-155` reconstructs arousal as 0.5, making the arousal mood nudge zero (`:121`). Appraised tone sequences are created at `:174-190` but lose event priority because all `playBeat` calls use P_IDLE (`AvatarMotionState.kt:2022-2030`) and all non-blink beats pass the spontaneous budget (`:2199`). ToneAnalyzer counts raw substrings (`ToneAnalyzer.kt:26-35,55-58`): “great” inside “greater”, “sad” inside “saddle”, “lol” inside names, code/error examples or quoted text can all become an emotional cause. The Joy word list also contains duplicate “genial” at lines11-12, giving it double weight.

Proposal: retain the complete ToneHint and typed source through scheduling. Choose an explicit low but event-derived priority for genuine tone reactions; avoid making them compete as uncaused filler. Harden fallback with language-aware boundaries, code/quote exclusion and a confidence threshold; make neutral/ambiguous the preferred fallback. Keep Smart Core optional and do not add an external classifier requirement.

Visible impact: expression intensity/energy reflects the reply, with fewer smiling error examples or concerned code snippets. Tradeoff: stricter heuristic produces fewer reactions and mixed-language boundary logic needs care. Validate minimal-pair prose/code/quoted/negated/technical corpus and positive vs low-arousal hints; assert arousal propagation and causal metadata survive scheduling.

### B9 — Events and pending plans can be stale after suspension/static mode (P2, lifecycle defect)

Evidence: stage retention is only 16 events, with no age check (`A/AvatarStage.kt:179-185`); `processStageEvents:2520-2544` consumes them regardless of `atMs`. `run:3207-3218` stops ticking while fully settled and resumes without resetting its event cursor, whereas ordinary static wind-down ticks advance the cursor. App background can also pause frames without disposal; dt is clamped to 0.1 (`:1042`) so simulation delays barely age during a long real pause. Pending perception, train and AlivenessEngine scheduled steps use simulation time; social `Glance.startedMs` is stored (`AvatarStage.kt:82`) but never checked on receipt (`AvatarMotionState.kt:2261-2268`).

Proposal: explicit suspend/resume policy: retain pose/velocity continuity but invalidate old ephemeral UI percepts, story follow-ups and mutual glances by real monotonic age. Keep durable emotion/mood separate from ephemeral actions. Snapshot latest stage sequence on reactivation, or accept only fresh events and a single explicit resume event.

Visible impact: returning to the app does not replay an old copied/menu/scroll reaction. Tradeoff: a genuinely important event while backgrounded needs its own durable UI/outcome treatment. Validate 60-second background gap, static→animated toggle after several events, stale mutual glance, and same-frame fresh event at resume.

### B10 — Unbounded audit logs grow in production (P2, maintenance/performance defect)

Evidence: `MindDirector.log` is an unconditional ArrayList (`A/AvatarMind.kt:222`), appended on faces, accents and noteAct (`:297,306,311`) without pruning. `EpisodePlanner.plays` is another append-only audit list (`IdleEpisodes.kt:70-77`). Unlike these, `AvatarMotionState.behaviorLog` is gated behind `AvatarDebugMetrics.enabled` (`AvatarMotionState.kt:1745-1746`). Log data survives for the lifetime of each remembered motion state.

Proposal: use a bounded ring buffer or make full audit recording opt-in; retain rolling counters needed by behavior separately. Preserve capture/test access via an explicit diagnostics sink.

Visible impact: no deliberate animation change; prevents memory growth in long sessions with multiple live avatars. Tradeoff: full retrospective history requires diagnostics enabled. Validate a simulated 12-hour session with constant record capacity and unchanged behavior/counters.

## Purposeful animation and simplification proposals

### B11 — Multiple independent behavior planners have different definitions of “free” and different interruption rules (P2, art/architecture proposal)

Evidence: the aliveness engine has its own `scheduled` sequence and replaces it on every new sequence (`AlivenessEngine.kt:207-210`), dropping individual steps if not calm (`:242-251`). MotionState separately owns `train` with safe-point queueing (`AvatarMotionState.kt:2061-2085`), `reactionQueue` (`:2220-2227`), mirror, forgive, perk, pendingWiggle, deferred mind intents and social timelines. `engineCalm` only checks social/reaction/follow/drowsy/train (`:2196`), whereas episode `free` checks freeze/take/scan/drift/stretch/yawn/spin/recovery/rest/peek/gaze (`:3035-3037`). A new aliveness gesture may begin during a freeze/scan/lean/spin that the episode planner correctly considers occupied. The existing AlivenessEngine can also start idle daydream/hum/peekaboo routines independently of EpisodePlanner (`AlivenessEngine.kt:370-428`).

Proposal: before adding more aliveness, define one short-lived attention/gesture phrase with cause, target, occupied channels, priority, finish/abort rules and optional follow-through. Migrate existing producers gradually; do not rewrite the whole engine at once. A quiet held-look adjustment should be a local refinement of the same target, while an event interrupts the phrase deliberately.

Visible impact: a character appears to finish or abandon a thought, with fewer unrelated motions stacked on top. Tradeoff: temporary loss of variety if migrated too aggressively; retain recorded representative scenes as references. Validate competing freeze + idle hobby, lean + message arrival, scan + poke, tone + reply-end and rapid cancellation; inspect that one cause remains readable.

### B12 — “Causal” idle behavior often invents an object and then acknowledges it (P3, art-direction proposal)

Evidence: `HumanBehavior.kt:368-375` chooses an imagined random interest point; a `notice` event may produce a Happy/nod chain (`AvatarMotionState.kt:2103-2118`), and an ack fixation directly schedules nod/blink (`HumanBehavior.kt:414-417`; `AvatarMotionState.kt:2121-2124`). `EpisodePlanner` independently gives positive base weight to LookUpRemember, FollowSomething, SkepticalLR and DoubleTake without a concrete remembered/stage object (`IdleEpisodes.kt:95-116`). These are intentional pantomime, not missing physics.

Proposal for approval: bind most interest phrases to a stable attention anchor (composer, latest text, activity pill, neighbor, remembered user action), allow a small named quota of imaginary play, and return to the user with a tiny acknowledgement only after actually noticing a relevant change. Keep target-relative refinements sparse and clustered around a specific thing; reserve whole-head turns for new interest.

Visible impact: aliveness feels intentional rather than random busywork. Tradeoff: quieter on empty screens; imaginary play contributes charm and should not be eliminated wholesale. Validate side-by-side seeded 60-second idle scenes with visible target/cause labels, then remove labels and ask whether motion still reads as purposeful.

### B13 — Stale mechanisms make tuning misleading (P3, cleanup proposal)

Evidence: legacy `MomentPlanner.update` is still ticked every frame with `calm=false` (`AvatarMotionState.kt:1679`), so its selector is unreachable although allow/note clocks remain useful for externally triggered moments (`HumanBehavior.kt:504-527`). Busy branches of IdlePlanner (`HumanBehavior.kt:338-349`) are no longer used in production because `gaze.update` receives `allowIdle=false` during busy (`AvatarMotionState.kt:1167`). AlivenessEngine includes selector branches with zero weights (sneeze/yawn/twirl at `:375-378`) and unreachable wink (`:394`, no candidate). MarkPose exposes blinkMinMs/blinkMaxMs/gazeMode (`MarkLifecycle.kt:43-46`) but repository use is only construction; actual blink/gaze scheduling lives elsewhere. `MindRules.EYE_GAP` is declared but unenforced (`AvatarMind.kt:77`); `pendingHold` and `wasMorphing` are stale fields (`:212,214,249`). Comments say “holds once settled” (`:289`) though the actual ownership hold starts immediately (`:295`), and transition docs say “most” face changes blink (`AvatarContext.kt:168`) although maskChance is 12–22% (`:188`).

Proposal: split active policy from historical tables, retain only useful cooldown helpers, delete unreachable selectors after coverage confirms them unused, and document actual tuning controls. Keep removed behavior snapshots in history/docs instead of executable dormant code.

Visible impact: indirectly prevents regressions from editing a knob that has no effect or reactivating an old animation path. Tradeoff: external/test harness consumers of public helpers need a compatibility check. Validate symbol/call-site search and a seeded before/after trace with exact equivalence for cleanup-only work.

### B14 — Social interactions lack a shared session and typed provenance (P3, behavior proposal)

Evidence: pending `Glance` is delivered once without checking age/acceptance (`AvatarStage.kt:193-212`; `AvatarMotionState.kt:2261-2268`), each participant runs a different randomized duration/timing (`:2263-2283`), and the initiator may begin although the receiver will reject after it becomes busy. Nearest calm target is always selected (`AvatarStage.kt:196`), so the same neighbor can monopolize glances. `remove` removes only incoming pending glance for the removed ID (`:154-156`), though the runtime safely aborts a session if the other entry disappears (`AvatarMotionState.kt:2340-2344`). `contactSec` resets at ordinary end (`:2369,2372`) but not on the early abort (`:2341-2344`), so later eye contact can inherit its duration.

Proposal: shared short session with accept/decline, expiry, tracked target and clean abort; reset all per-session metrics on every exit. Add pair recency and vary among eligible neighbors if desired. Preserve the present rare global cadence.

Visible impact: two avatars appear to notice each other deliberately, and shy reactions do not borrow contact time from an interrupted earlier meeting. Tradeoff: more social-state plumbing; rate/meaning of skepticism is an art decision. Validate partner disappears, becomes busy before delivery, pauses, and starts a second session after an aborted one; assert contact duration resets and no stale handshake.

## Clock and queue policy follow-up

- `AvatarStage.lastGlanceMs` and composer/event stamps use wall time (`AvatarStage.kt:89,101,174`) while most scheduling uses dt floats. Manual/NTP clock jumps can suppress or prematurely trigger social checks and composer timeout. Proposal: inject monotonic elapsed time for age/cooldowns; wall clock remains only for time-of-day fatigue. Validate time rollback/forward and deterministic simulated clocks.
- `MindDirector.pump` picks the highest-priority intention first, then tests readiness (`AvatarMind.kt:325-329`), so a blocked accent can hold a ready reaction in the same lane until expiry. This is a policy choice unless deliberately intended. Proposal: define whether blocked strong intentions reserve the lane; otherwise select highest *eligible* intention and test a blocked-accent/ready-reaction case.
- Intention supersession only uses lane+name and ignores priority (`AvatarMind.kt:317-320`). All deferred story plans share name `plan` (`AvatarMotionState.kt:2067`), so a later weaker proposal could replace an older stronger one if such callers coincide. Proposal: priority-aware supersession and explicit story IDs; validate user plan followed by idle plan without depending on current incidental guards.

## Checked concerns that should NOT become findings

- EpisodePlanner clock does not count busy time in production: `aliveUpdate` returns at `AvatarMotionState.kt:2289` before `episodeUpdate`. Its documented idle-time cooldown is correct. Time while idle-but-occupied does count, which matches “idle lifecycle time,” not “free slots.”
- Stage registration filters social neighbors by actual window identity and runtime safely aborts if the partner disappears. The missing scope is specifically targets/events/composer, not neighbor lookup.
- ToneAnalyzer is wired from the latest reply in ChatMessageV2; the public `AvatarMotionState.feedTone` having no production caller does not mean tone itself is disconnected.
- Static avatars deliberately ease down and stop frames; UI event age/replay policy is the issue, not the lack of background ticking.
- Personality defaults and clamp ranges are coherent. The four trait knobs feed both temperament and aliveness; adding more user knobs would complicate tuning before ownership is fixed.
- Bored/negative expression limits and forced contextual returns are intentional policies; do not relax them merely to satisfy old dwell metrics.

## Suggested approval batches and validation artifacts

1. **Predictability/correctness:** B1, B2, B4, B5 text-revision fix, B9 and B10. Focused deterministic unit scenarios plus one event-burst film; preserve art direction.
2. **Purposeful responsiveness:** B3, B7 and B11. Produce a choice of quiet/expressive outcome and interruption demos before broad migration.
3. **Integration correctness:** B6, B8, actual caret geometry, shared monotonic clock. Test in the real Compose UI including dialogs and multiwindow; simulation-only evidence cannot prove coordinates/event ownership.
4. **Character direction and cleanup:** B12/B14 and B13. Show seeded idle/social scenes at real display size; approve behavior before adding more motifs.

For each behavioral demo, show cause/target labels in a diagnostic version and the same scene clean. Include neutral idle, held side look, fast burst of UI events, typing/editing, thinking→streaming→completion, happy/asymmetric return, negative/cancelled result, poke interruption, and suspend/resume. Deterministic traces should record requested/accepted/rejected actions with reason and unique action ID, target revisions, budget count, phrase ownership and contextual exits. Avoid relying only on smooth-value plots: a mathematically continuous sequence can still look like pointless decisions.
