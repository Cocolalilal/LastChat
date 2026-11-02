# Renderer, integration and validation audit

Read-only audit of `/workspace/LastChat`, branch `lastchat_dev`, initial HEAD `b26e3fb6`. No application files changed. Lines refer to the audited snapshot; current authorized edits may move them. These are proposals, not authorization to implement additional product changes.

## Confirmed implementation defects

### P1 — Grok neutral spacing depends on render history and has unsafe shared ownership

Confidence: high. User-visible magnitude needs a reproducible clip.

Evidence: `app/src/main/java/me/rerere/rikkahub/ui/components/avatar/animated/AnimatedMarkAvatar.kt:434-444` caches neutral extra spacing with key `shapeId|bodyKind|he|eyeGap|eyeScale`, but the computation at 435 applies `f.face.eye(i)` to neutral eyes. `FaceRig.kt:366-384` shows those parameters change position, width, rotation, openness and spine geometry, affecting the subsequent separation calculation. Face parameters are omitted from the cache key. The first frame/first avatar to populate a key can therefore alter later spacing.

The cache is a global plain HashMap (`AnimatedMarkAvatar.kt:273`). `widget/WidgetConfigActivity.kt:357-359` renders previews on `Dispatchers.Default`; `WidgetAvatarRenderer.kt:192-207` uses this same drawMark path. Main-thread canvas draws and worker previews can access the cache concurrently. Float keys also vary continuously during lifecycle easing, and the implementation clears the entire cache once its size exceeds64 (`AnimatedMarkAvatar.kt:442`).

Proposal: compute canonical neutral spacing using identity face parameters, independently of transient accents. Make cache ownership explicit and bounded/thread-safe. Cache an invariant static quantity rather than transient float states where possible.

Risk: existing history-dependent eye spacing may have become an accidental visual baseline; compare representative happy/worried/asymmetric expressions before accepting the canonical result.

Validation: reverse expression/avatar render order with cold/warm caches; assert identical final pixels/centres; render widget and Compose frames concurrently; exercise changing eyeGap/eyeScale through whole lifecycle transitions.

### P2 — A scroll beginning over an avatar triggers a poke

Confidence: high; behavior follows directly from gesture implementation.

Evidence: `AnimatedMarkAvatar.kt:173-179` uses `awaitFirstDown(requireUnconsumed=false)` and immediately calls `motionState.poke`, without waiting for UP or cancelling on drag. The comment says parent click handlers should continue to work, but this also treats a scrolling DOWN as intentional attention.

Proposal: confirm a tap after UP, cancelling on drag, cancellation or relevant consumption, while preserving parent avatar click/edit behavior. A DOWN can optionally provide a tiny attention preview only if explicitly desired.

Validation: real Compose tests for ordinary tap, scrolling across avatar, long drag, cancelled gesture, consumed parent gesture and parent onClick. Direct drawing simulations cannot test this.

### P2 — Mesh cache byte accounting drifts after size eviction

Confidence: high. This is a performance defect; measured stutter impact remains unproven.

Evidence: `WhMx.kt:90-94` automatically evicts the eldest entry when size exceeds48, but does not subtract its estimated bytes. `WhMx.kt:100-110` increments cacheBytes and subtracts only overwritten values or byte-budget evictions. Repeated unique poses inflate tracked bytes despite retaining at most48 entries, causing unnecessary later evictions/rebuilds. RV is16MB (`WhMx.kt:50`).

Proposal: one explicit eviction path, or correctly subtract size-evicted entries; keep accounting under the existing synchronization.

Validation: insert thousands of unique poses; assert tracked bytes match the retained cache and that hot poses remain cached within the real budget. No product motion should change.

## High-confidence integration gaps requiring a product proposal

### P1/P2 — System reduced-motion policy is not consumed by avatars

Confidence: high. Desired calm-mode behavior is an art/product decision.

Evidence: `UIAvatar.kt:129-140` passes `LocalAvatarAnimate.current`, with no `LocalMotionPolicy` use. `AnimatedMarkAvatar.kt:77-84` derives its live flag solely from animate/frozen state. `AvatarMotionState.kt:3224-3248` similarly has no policy input. `ChatList.kt:861-863` gates to the newest assistant and newest user turns, not system motion preference. Existing `ui/motion/MotionPolicy.kt:39-79` already observes Android motion scales for other UI.

Proposal: agree on a calm reduced-motion presentation: suppress spatial gestures/spins while retaining useful state communication via static/subtle facial changes, or settle fully if that is preferred. Simply setting animate=false may lose meaningful busy-state communication, so choose behavior first.

Validation: toggle policy during an active spin and while busy; ensure no abrupt cut, no new ambient gestures, and a usable state indication. Verify editor previews obey the chosen policy consistently.

### P2 — Stage registration effects do not track all their inputs

Confidence: medium; needs a Compose reproduction.

Evidence: `AnimatedMarkAvatar.kt:132` remembers an ID from stage without keying remember by stage. Disposal at139 depends only on stageId. Registration captures signature at143/157, but effect at164 keys only live; the other refresh comes from onGloballyPositioned at169. A signature change that does not trigger layout may leave the stage's identity stale. A changed stage provider may also leave stale registration/disposal ownership.

Proposal: update registration using explicit stage/signature/live/bounds keys, and key cleanup by stage ownership. Prefer committed-effect work over mutating registration-related state during composition.

Validation: swap shape/eye type without changing bounds; swap stage provider; dispose during swap; confirm stage entries and neighbour identity are correct without requiring another layout pass.

### P2 — No explicit app-lifecycle/viewport policy in the avatar loop

Confidence: high for missing explicit policy; actual background frame delivery depends on Compose/window lifecycle and should be profiled before claiming battery waste.

Evidence: `AvatarMotionState.kt:3206-3219` is a withFrameNanos loop that waits only when isSettled; `rememberAvatarMotionState` at3246 is keyed by enabled, not Lifecycle. `tick` at1041-1042 clamps elapsed time to100ms. LocalAvatarAnimate is newest-turn gating, not visibility gating.

Proposal: define pause/resume and offscreen behavior. Preserve pose/momentum on brief suspension, avoid replaying an accumulated sequence, and intentionally refresh long-lived attention after resume. Do not alter it solely on speculation.

Validation: device pause/resume, sheet covering chat, navigation retention, scrolling newest turn outside viewport, 100-500ms dropped frame intervals. Record CPU/frame delivery and pose continuity.

## Motion/render investigations: plausible perceptual issues, not established root causes

### P2 — Cached projection introduces history-dependent small angle steps

Confidence: high about quantization; medium/low that it is perceptually significant at32dp.

Evidence: `WhMx.kt:650-665` truncates yaw/pitch/roll to0.001 radians (about0.057 degrees) and head radius to an integer for its key, but builds with the first original unquantized values encountered in that bucket. A later nearby pose reuses the first raw projection. Eye seating uses current continuous angles. This can produce body/eye mismatch and staircase behavior during very slow turns. CacheKey at76-86 also omits ink although MeshDraw.faces carry fill (`WhMesh.kt:85-89`); current normal renderer paints a flat fill from the hull, so wrong cached face colours are a latent API issue, not demonstrated visible app colour corruption.

Proposal: cache invariant topology and apply continuous projection, or devise an error-bounded projection cache. First quantify screen-space error and cost. Increasing precision blindly trades memory/rebuild cost for uncertain benefit.

Validation: cold/warm-cache slow turns forward and backward, identical pose reached by different histories, at32dp and enlarged demo size; compare silhouette and eye trajectories. Benchmark on a device before changing cache structure.

### P2 — Eye safety constraints have discrete boundaries

Confidence: high about mechanics; unproven as current reported shake cause.

Evidence: `EyeSafeRegion.kt:14-31` creates an integer Region at4x model coordinates; contains truncates coordinates. fit at53-66 uses five binary-search iterations with a minimum0.75 turn. nudge at75-87 uses seven iterations; shrink/shrinkX use separate six-step corrections. `AnimatedMarkAvatar.kt:470-512` applies multiple Grok fit/tilt/shrink/separation/nudge stages; Generical has corresponding corrections around541-578. Separate constraints can activate at adjacent thresholds.

Proposal: instrument guard activation and the resulting eye centres/scales, then render slow sweeps. Only replace demonstrated discontinuities, perhaps using a smoother signed-distance or continuous constraint solution. Do not add another arbitrary smoothing layer that delays intentional expression changes.

Risk: weakening guards can cause eye overlap or silhouette escape; a coupled solver is a significant geometry change needing approval and broad validation.

Validation: every picker shape, both eye styles, slow yaw/pitch/roll combined with happy/asymmetric/neutral transitions and changing gap; check eye separation, containment and per-frame motion.

### P2 — Body projection fallback can be a hard silhouette change

Confidence: high about fallback; no reproduced failing pose in this audit.

Evidence: `Soft3DMark.kt:164-174` rejects empty/undersized mesh hulls and returns the original unrotated body path. The size cutoff scales by facing, which correctly makes room for edge-on spin silhouettes, but crossing the rejection threshold can still replace one very different contour with another.

Proposal: identify offending meshes/poses first; fix union/projection robustness or use a continuous fallback instead of a categorical contour swap.

Validation: full360-degree spin sweeps around all paths/shapes, multi-axis turns, capture rejected-hull events and adjacent silhouettes. This is distinct from uniform avatar growth, which prior work removed from the live motion frame.

## Performance opportunities, pending measurement

### P2 — Asset parsing can block first composition

Confidence: high.

Evidence: `AnimatedMarkAvatar.kt:74-75` schedules ensureLoaded but also calls it synchronously in composition. `AvatarPackData.kt:108-120` does asset reads and JSON parsing under synchronization. This only costs the first successful load, but can make first avatar/editor display hitch.

Proposal: initialize immutable assets off the main thread before showing the live avatar, with a stable loading/fallback presentation and safe publication. Avoid simply deleting the synchronous path without supplying a usable loading state.

Validation: cold-process first avatar load on slower device, trace main-thread time and first-frame latency; verify failure fallback and concurrent widget loading.

### P2/P3 — Repeated invariant parsing and geometry allocation in draw

Confidence: high; performance impact needs profiling.

Evidence: `AnimatedMarkAvatar.kt:334-336` reparses SVG path data and allocates centred paths on every draw. The normal draw obtains projected body at368, then calls drawBody at374-385 with mainFillPath already supplied; `Soft3DMark.kt:120-122` still obtains a mesh before selecting mainFillPath at126-130. EyeSafeRegion.Body allocates a scaled Android Path/Region at428/565, even for conditions that may bypass fitting. GrokEyeGap.outline uses repeated contour sampling; clearance is pairwise sampling and gets called many times within separation/shrink searches (`GrokEyeGap.kt:23-125`).

Proposal: cache immutable canonical shape geometry; reuse projected mesh results; skip guards proven irrelevant for a path; profile sampled-outline reuse within one frame. Paths must remain immutable or copied before transformations, and shared caches must account for widget worker access.

Validation: allocations/GC and frame CPU for one32dp avatar, enlarged editor preview, busy animation, spin and rapid shape swaps. Preserve pixel output at fixed frames where the change is only optimization.

## Art-direction experiments only

- A softer visual constraint solver may improve narrow-shape turns, but requires side-by-side clips before committing.
- Any squash/stretch behavior is a separate proposal: live frames carry squashX/Y (`AnimatedMarkAvatar.kt:360`), but current body transform does not apply it. This was already a known pending proposal; do not silently enable it during the current fixes.
- Stage registration and poke correction should not be used as a reason to add more spontaneous reactions. Keep attention causes legible.

## Test and simulation coverage

The `/tmp/avatar-demo/prepare.py` harness uses real motion and native drawing sources but strips AnimatedMarkAvatar's Composable wrapper: it constructs a file from graphics imports plus everything after `data class EditorCue`. Runtime/Stage stubs stand in for Compose integration. The demos are useful evidence of engine/render appearance but cannot establish gesture correctness, stage effect ownership, accessibility policy, lifecycle, real UI morph effects, or device frame performance. The stripped harness also must keep its source list synchronized with new helpers.

The repository has a real Compose/native render harness, `app/src/test/java/me/rerere/rikkahub/ui/components/avatar/animated/AvatarRenderHarnessTest.kt`. Its class documentation explicitly describes screenshot output rather than pass/fail comparison. Tests skip unless lastchat.renderDir is configured (around47-52 and129). Existing real harness coverage should be used when build infrastructure is available rather than assuming the stripped renderer replaces it.

`GrokBlinkRenderTest.kt` verifies equal pixels for fully closed Happy/Neutral masks and no clock-driven changes in a fixed quiet pose. Those are useful endpoint invariants, but do not detect intervening eye centroid reversals, partial-close shape glitches or excessive expression-return duration. `MotionDynamicsTest.kt` checks spring frame-rate agreement and specific join continuity, but engine-level continuity does not guarantee rendered continuity after nonlinear geometry constraints.

Recommended targeted validation, ordered by value:

1. Deterministic transition clips for both eye styles, happy/asymmetric-to-neutral, interrupted transitions, and real app-size plus enlarged views. Inspect rendered eye centre, width, height and angular trajectory across the complete transition.
2. Cold/warm/reversed render-order tests and concurrent widget/app rendering for canonical-spacing cache determinism.
3. Real Compose gesture/policy/stage tests. Cover scroll start, tap cancellation, signature swap at fixed bounds and reduced motion toggling mid-animation.
4. Sweeps for every picker shape through slow turns and360 spins, logging safety/fallback activation. Check containment and separation alongside smoothness.
5. Variable frame cadence, dropped frames and lifecycle pause/resume. Existing exact spring tests are not an end-to-end substitute.
6. Device profiling for32dp chat avatars, busy activity, list scroll and editor swaps. Record frame CPU/allocation/JankStats or Macrobenchmark metrics before optimizing.

No new tests were executed as part of this read-only audit. All causal/perceptual uncertainties are explicitly marked above.

## Additional eye-transition proposals from the motion audit

These are independent of the confirmed busy-nod shake and are not implemented.

- `FaceRig.kt:293` selects a different lid deformation algorithm with `p.slant < -0.05f`. Smooth input can therefore cross a hard contour boundary. Proposal: blend the two deformation outputs over a small slant interval. First validate fixed-body slow sweeps, measuring corresponding outline vertex displacement and checking Happy/worried/asymmetric identity.
- `TiltGuard.kt:44` estimates top tilt from ten discrete x bins of sampled points. Membership and the selected topmost point can change during morphs; the correction is then applied twice. Proposal: evaluate the top polyline by intersections at fixed normalized x coordinates. Confirm this actually reduces contour movement and retains the common-lean appearance before replacing it.
- `AvatarMotionState.kt` Blinker.update computes a damped sinusoidal reopen settle lasting up to 0.4 s, contributing roughly 1.5% eye-height motion. Proposal: compare a single positive settle, or suppress ringing specifically after an expression replacement. Ordinary blinks should remain expressive; do not assume all reopen motion is a bug.
