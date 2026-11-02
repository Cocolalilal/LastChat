# Avatar motion review — 2 November 2026

Scope: the full animated-avatar engine on `lastchat_dev`, starting from `b26e3fb6`. This review separates the requested fixes from additional proposals requiring user review. Source line numbers in the detailed audits describe that starting snapshot.

## Requested fixes implemented

- **Post-Happy shake:** the native busy replay (seed 71) changes Happy→Neutral at 7.23 s, then pitches +5.50° at 8.0 s, −1.64° at 8.2 s and +0.39° at 8.4 s. This is the busy nod's oscillating spring plus absolute-pitch-linked yaw, not an eye morph defect. Busy acknowledgements now make one dip, a 4% follow-through, and a quiet finish, without the extra sideways flick or random second nod. Explicit playful/reaction gestures retain their separate profiles.
- **Half-closed eye release:** the demonstrated expression is `confused`. Returning from either confused variant to Neutral now uses the prompt Soft profile, reaching 96% in approximately 350 ms instead of the roughly 900 ms Settle release. It remains a direct continuous morph; no extra compulsory blinks are introduced.
- **Quiet-look life:** replace independent gaze micro-targets and sinusoidal drift with occasional bounded refixations around the existing attention target. A quiet opportunity occurs after 5–10 seconds of settled attention and can be skipped. Each accepted point is held; there is no automatic return or repeated corrective oscillation. The eyes lead; a small head follow shares the same destination through the existing springs, which supply motion and momentum. Continuous tracking, spins, leans, social attention, drowsiness and important focus take precedence. Blinks and expression changes pause scheduling without recentering the held eyes. Independent parametric-face jitter and renderer eye drift are suppressed for engine-generated frames.

These timings and amplitudes are project tuning choices, not measured Pixar/Anki/EMO settings. Short busy fixations remain quiet; this feature is intended for longer held looks, not a requirement to keep moving.

## Additional proposals — not implemented

| Priority | Finding / opportunity | Proposed next step |
| --- | --- | --- |
| High | Grok neutral spacing depends on whichever transient face first populates its shared cache; widget workers also access it. | Compute canonical identity spacing and give the bounded cache explicit safe ownership. Compare render order and concurrent widget/app draws. |
| High | Generical outline code switches deformation algorithms at slant −0.05. | Blend across that boundary and measure outline displacement during fixed-body transitions. |
| High | Pending UI perceptions can all land in one frame, repeatedly overwriting gaze/body intentions. | Coalesce stale repeated events, arbitrate one meaningful attention change, and preserve urgent reactions. |
| Medium | Pointer DOWN immediately pokes the avatar, including when starting a scroll. | Confirm a tap and cancel on drag; verify parent click behavior in real Compose. |
| Medium | Same-length composer edits do not refresh typing attention; shared stage inputs are not window-scoped. | Track content/edit revision and scope stage inputs/events to their owning window/chat. |
| Medium | Gaze big-shift flags can be cleared before the blink/mind consumers read them. | Emit one persistent consumable shift event or sequence, independent of call order. |
| Medium | Mesh cache eviction leaks byte accounting; projected geometry is reused in 0.001-radian bins. | Repair accounting, then profile continuous projection with cached topology. Do not assume quantization is visible at chat size. |
| Medium | TiltGuard's discrete point bins and raster safety guards can produce piecewise corrections. | Run slow all-shape boundary sweeps, then replace only measured unstable boundaries. |
| Medium | Blink reopening adds a small damped oscillation over other transitions. | Compare a single positive reopen settle, especially after masking a face replacement. |
| Medium | Existing reduced-motion policy does not reach avatars. | Agree on a calm presentation that still communicates busy state; test mid-animation policy changes. |
| Medium | Stage registration, app pause/resume and viewport behavior have integration gaps. | Add real Compose/device interaction coverage before changing lifecycle policy. |
| Performance | Asset parsing, per-frame SVG parsing, redundant mesh work and always-on diagnostic history may add cost. | Cache immutable work, bound diagnostics, and profile frame time on device at normal avatar size. |
| Art direction | Reply completion and sentiment reactions can imply emotion without a clear cause. | Preserve provenance/priority through the scheduler and agree on appropriate expression vocabulary. |

Previously discussed changes remain pending: enabling the currently ignored squash/stretch channels and removing the forced up-right gaze correction. Neither is included in these fixes.

## Detailed evidence

- [Rendering, integration and validation](rendering-and-integration.md): confirmed defects, geometry hypotheses, performance opportunities, confidence, risks and proposed tests.
- [Behavior and scheduling](behavior.md): context, event arbitration, window ownership and emotional intent.
- [Primary references and interpretation](references.md): Lasseter/Pixar, Carlos Baena, HRI research, head-eye coordination, Anki SDK/archived notes, and LivingAI EMO Pet. Recommendations are distinguished from documented reference behavior.

The shared reference theme is one readable thought at a time: preparation when useful, a clear destination, subordinate secondary action, and a completed finish. Quietness is part of the performance.

## Validation and limits

See the companion demo README/validation record for exact checks and clips. Native simulations use the actual Kotlin engine, real assets and drawing functions. Their scratch harness strips the Compose wrapper, so they do not validate gestures, stage registration, lifecycle or system motion policy. No full APK/device build is available in this environment. Enlarged clips reveal motion; acceptance at normal chat size still requires device review.

The long-run gate initially flagged one false recenter at an authored raw yaw of 166°→159°. The renderer applies a tanh cap (at most 25°), so that interval has negligible displayed angular change. The recenter metric now applies that conservative largest cap; a new regression test still rejects a visible 12°→5° return. Other historical raw-channel metrics retain their original definitions. The Grok integration fixture explicitly requests its Neutral endpoint because spontaneous busy planning can legitimately select Happy again after a reaction expires.

Final checks: 43 Kotlin engine/trajectory/behavior tests and 8 Robolectric native render/long-session checks pass. Comparison artifacts are in `/workspace/avatar-motion-demos-v4/` (busy session, asymmetric release, held look, isolated nod). `git diff --check` passes. `graphify update .` was attempted, but the executable is unavailable. Full Android build and device acceptance remain outstanding.
