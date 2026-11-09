# Avatar character engine

> Selective rollback (2026-11-08): behavior is restored to `d0ad749a`, before
> this chat's animation passes. Retained: continuous rotational spins with eye
> follow-through, no clip camera zoom or spin squash, and shared Happy-to-Neutral
> blink masking for Generical and Grok. Original gaze, lean, busy-motion and
> spontaneous blink timing are restored. Avatar-only model/classifier code and
> stored/exposed personality overrides remain removed; internal fixed style
> temperament and mood are retained because they drive the original behavior.
> Grok Sad/Worried, Angry and Pouty use full-height pills and stronger mirrored
> tilts. Actual blinks may still close these eyes.
>
> The rejected intentional-attention rewrite is not active. See
> [rollback validation](avatar-selective-rollback.md) for scope and checks.

Animated avatars (Generical and Grok eye styles) are driven by three layers:

1. **Motion director** (`MotionDirector`) – body pose: turns, look-arounds, tilts,
   nods, hops. Weighted beats with cooldowns, eased in and out.
2. **Face** (`ExpressionDirector`, `FaceRig`, `GazeDirector`, `Blinker`) – eased
   expression morphs, parametric accents, gaze with micro-saccades, irregular blinks.
3. **Character engine** (`AlivenessEngine`) – decides *what* the character does.

## Character engine

- **Personality** (`Personality`): continuous traits 0…1 – energy, curiosity,
  warmth, playfulness, shyness, expressiveness, attentiveness, mischief, calmness.
  Derived knobs: `tempo` (beat frequency), `showiness` (how readily non-neutral
  faces appear), `blinkRate`. Defaults: `Personality.GENERICAL` (warm, curious,
  a little playful) and `Personality.GROK` (composed, drier, more mischievous);
  `Personality.defaultFor(eyeType)`.
- **Mood and emotion**: shared `MoodEngine` (valence/arousal, slow decay, time of
  day) plus a short-term `Emotion` (Joy, Interest, Concern, Confusion, …) whose
  level decays over 6–14 s depending on expressiveness.
- **Drives**: boredom (rises while idle, falls on any event), attention (spikes on
  events), social interest (wish to look at other avatars).
- **Selector**: utility weights = base × trait factors × emotion boosts ×
  cooldown factor × freshness (recently used beats lose weight). Timings are
  log-distributed, so there is no rhythm.
  - Busy phases (thinking / working / streaming) rotate through a varied set of
    faces (neutral, focused, squint, happy, confused, skeptical, surprised, wink,
    worried when concerned) with random holds; the focused face is only one option.
  - Busy beats: glance up-left or down at the text, focus, puzzled, small smile,
    tilt, blinks, and nodding along with its own reply while it streams (a few
    times per reply at most). After a reply, sometimes a small nod.
  - Idle beats: glances, curiosity, smile, drowsiness when bored, tilt, wink
    (mischief × playfulness), shy look-down, a rare hop.
- **Events**: every stage event (`AvatarStage.EventKind`: send, typing, error,
  copy, regenerate, other avatars' faces, …) updates drives and emotions.

The engine only proposes beats; the motion state turns them into eased
gestures, morphs and gaze moves, so nothing cuts.

## External API (for a future decision model)

- `AnimatedMarkAvatar(personality = …)` or `AvatarMotionState.personality` – set
  traits, e.g. inferred from a character's system prompt.
- `AvatarMotionState.feedTone(ToneHint(valence, arousal, emotion, intensity))` –
  per-message tone; nudges mood and sets a short-term emotion.

Rules kept throughout: no hard cuts, no size jumps. (Since engine6 gaze is unrestricted in every direction; see below.)

## Eye shade

Generical eyes fade toward 80 % of the body colour at the bottom
(`MarkColors.EYE_SHADE_MIX`). The avatar editor has an "Eye shade" switch
(`Avatar.Animated.eyeShade`, default on); toggling glides over 380 ms.

## Layered behaviours (engine pass)

Grounded in the usual character-animation and game-AI toolbox: Pixar/Disney
principles (anticipation, follow-through, secondary motion), Cozmo/Vector-style
emotion-driven behaviour selection, utility AI with cooldowns, and a PAD-like
valence/arousal mood with OCC-style named emotions on top.

- **Sequences**: multi-step behaviours scheduled on the engine clock; random beats
  pause while one runs, and a step that can't play (another system owns the face)
  is dropped rather than delayed into a cut.
- **Anticipation**: big moves (spin) start with a blink; startles lead with the
  face, then the body, then a double blink (follow-through).
- **Reply tone**: `ToneAnalyzer` reads a finished reply (emoji, punctuation,
  short EN/RO/DE/ZH word lists) and emits `ReplyTone`; the engine feeds the mood
  and, scaled by expressiveness, reacts (happy face, giggle + nod, worried,
  sad droop, curious tilt, confused face, smug).
- **Startle**: user activity after a long daze (high boredom) gives a small jolt.
- **Hobbies** (grow with boredom): daydream (gaze up-left, soft lids, slow blink),
  survey the screen (3 irregular stops), humming (2–3 irregular small nods with a
  smile), peekaboo (shy + playful).
- **Easter eggs** (rare): sneeze, late-night yawn, a happy twirl.
- **Fatigue**: late night / early morning slows the beat tempo and enables yawns.
- **Editor**: "Advanced personality" section with nine trait sliders, stored as
  `Avatar.Animated.traits` overrides on top of the style default; "Reset" clears.

## engine2 update

### Personality: four knobs
`Personality(energy, warmth, playfulness, expressiveness)`, each in 0..1. The engine derives
curiosity, shyness, attentiveness, mischief, calmness, tempo, showiness and blinkRate internally.
The editor exposes no sliders. Per-character defaults are in `Personality.resolve(eyeType, traits)`.

### Motion model
- Pose layers crossfade in 0.14–0.34 s (`FADE_SNAP`) through an underdamped spring
  (ω19, ζ0.62 on angles; position, squash and scale use ω16, critically damped; drowsy uses ω9, ζ1).
- Large target changes add an anticipation kick (`ANTICIPATION`): the head dips back before it goes.
- Gestures (nod, recoil, busy beats) use `snapCurve`: anticipation, fast attack, then a damped
  rebound that settles to exactly 0.
- Holds are short (most under 2 s), so there are no long stares or slow creeping turns.
- An up-right guard removes upward pitch when the head is turned right.

### Grok eye gap
`GrokEyeGap` keeps the two Grok eye paths at least 0.085 × the mark radius apart in every
expression, shape, accent and transition. `GrokEyeGapTest` asserts this across 445 frames per shape.

## engine3 update (motion review fixes)
- Expression changes are a 0.45–0.68 s sine-eased morph (13–20 frames at 30 fps). The masking
  blink on expression change is gone.
- A closed Grok eye is drawn as a thin slit (`GROK_SLIT`, 12 % of open height). The eyes never vanish.
- A final up-right guard runs on the composed look (body pitch + head gaze + eye lead), so no
  layer can produce the up-right stare.
- The motion filter is calmer: ω14, ζ0.74, anticipation 1.8. Busy turns go to 10–20°. Look-around
  makes 2–3 stops, each ≤ 16° and at most ~14° from the previous one (no head shake).
- Nods use `nodCurve`: the eyes dip first, then the head makes one eased down-and-up arc with a
  soft ~12 % settle. There is no ringing.
- `GrokEyeGap.MIN_GAP` = 13 % of mark radius. `GrokEyeGapTest` requires a ~9 % clear gap on the
  rendered pixels and fails when an eye vanishes. It covers blink and lid states, squash, and frames
  taken from a live simulation.
- `AvatarMotionVideoTest` renders at 30 fps (120 Hz simulation) with 120 dp cells.

## engine4 update
- Grok "confused" uses two pills of the same size; only one is tilted (−18°). No pill shrinks.
- `GrokEyeGap.MIN_GAP` = 17 %. The gap test requires a ~12 % clear gap, samples tilted faces
  mid-blend on every shape, heart included, and runs 767 frames per shape.
- No nod starts while a face is morphing, and the face does not change while a nod plays. The
  asymmetric "hurt or cute" face is no longer in the busy rotation.
- Continuous micro life: breathing (squash plus bob, 3.4 s period) and micro-saccades every 0.5–1.6 s.
  No idle hold is fully still for more than ~1.4 s.
- The up-right guard runs on the final composed look, so it also covers reaction and startle gaze.

## engine5 update
- Grok Happy is drawn as clean ^ ^ arcs (stroke half-width 0.085, with a bend radius well above
  the stroke width). Closed arcs are 0.07 wide.
- Arc ↔ pill morphs go through a straight bar (`cleanBlend`): first straighten, then morph, then
  bend. The eye stays a clean capsule at every t. A blink on an arc eye only thins it.
- `EyeSafeRegion` scales the turn applied to the eyes (not the body) down only as far as needed to
  keep every eye outline point, plus a margin, inside the projected silhouette (heart, etc.).
- "hurt or cute" is out of all automatic rotation (busy, idle wink, poke reaction).
- Visible-life guarantee: if the combined head and gaze turn changes less than 8° for 1.0–1.8 s
  with no blink, the eyes make a quick glance (sideways or down). No 2.5 s window has less than
  8° of travel. Filter ω17, ζ0.7. The up-right guard starts at a combined yaw of −1°.

## engine6 update (review fixes)
- Gaze is unrestricted: every up-right guard is gone (gaze target, body filter, clip pose
  softening, final composed look). The avatar can look in any direction. Only two rules
  remain: no long frozen hold, and the eyes stay on the surface.
- No frozen holds: a slow organic drift (two incommensurate sines) runs on the head gaze, and
  micro-saccades are larger (±0.13). A visible-life glance in any direction fires when the
  eyes themselves (head gaze + eye lead, ignoring the body) have moved less than 6° for 0.9–1.6 s.
- Eyes stay on the surface: `EyeSafeRegion` margins are now 0.12 (Grok) and 0.11 (Generical) ×
  mark radius. After the turn-scaling fit, `EyeSafeRegion.nudge` slides both eyes toward the
  silhouette centre just far enough to stay inside, on every shape (sphere included).
- Grok `MIN_GAP` = 24 % of mark radius (was 17 %), enforced after every morph and pose.
- Springy nods (`springNod` / `springNodPose`): a quick dip, then a damped spring through an
  overshoot. The body compresses on the way down and stretches on the rebound.
- Real startle (`startlePose`): a ~70 ms pop up and back with stretch, a squashy landing and a
  spring settle. Startle beats are never dropped while the lifecycle changes, and the Startled
  eyes are wider. Bob and squash filter ω 24.
- Bent (^) arc eyes scale uniformly under face accents, so the two Happy arcs stay clean
  mirror images.
- `AvatarMotionVideoTest` writes `metrics.csv`: the Grok gap and the edge inset per drawn frame.

## engine7 update (engine6 review fixes)
- Busy Happy: when the eye spine changes direction (vertical pill → horizontal arc bar), the bar
  shrinks to a stub and grows out along the new direction (`barBlend`). It no longer swings
  through an angry \ / diagonal.
- Grok `MIN_GAP` = 40 % of mark radius. The metrics show ≥ 0.40 on every frame, Sad and Worried included.
- Surface: edge margin is 0.20 (Grok) and 0.17 (Generical). The fit keeps ≥ 75 % of the turn,
  then shrinks the pair about its centre (≥ 0.78; gaze motion survives), then nudges inward.
- No frozen stares: the range of the composed look over a rolling 1.5 s window has to reach
  10°, or a priority glance fires (also on the micro channel) within 0.55–0.9 s.
- Hold cap: gaze is unrestricted, but more than 1.5 s off-centre, or more than 0.85 s
  up-right, triggers an eased return near centre (body turn included).
- All up-right biases are gone: idle glances, the survey, daydream, thinking look-aside and
  ponder now go to either side and up or down. The brand-pose clips (Idle_D, Streaming_B,
  Working_E) stay unscheduled because they REST up-right.
- Startle: the pop is added after the smoothing filter (`startlePose`, ~70 ms rise, stretch,
  squash landing, spring settle), so it is not rounded off. `AvatarMotionVideoTest` asserts it plays.
- `metrics.csv` carries the expression, gap, inset and rendered eye centroid per frame
  (`engine7_report.py` turns it into the gap, inset, freeze and up-right-hold report).

## engine8
- **Perceptible life beats** (AvatarMotionState): every 1.5–2.2 s a visible beat alternates look-away (eyes 0.35–0.65 + real head/body turn 7–13°, tilt, roll) and look-back to centre. Hold cap raised to 2.4 s (up-right still 0.85 s) so it no longer cancels beats; recenter keeps 35% motion. Report: longest gap between perceptible events (≥0.08 R eye move, blink or ≥6° turn) ≤ 2.23 s in every clip.
- **Sad/Worried Grok**: inward tilt 28° → 19°, set wider (+0.015 offX, +0.03 spread); inner tops read clearly apart.
- **Heart edge**: inset measured on the true projected silhouette path; Generical margin 0.17 → 0.21 R.
- **Startle**: 0.6 s startle window forces lids fully open (no blink), Startled accent stronger (sx 1.2, sy 1.4) and 0.75 s; double-blink moved to 1.15 s after.

## engine9
- **Gaze itinerary** (AvatarMotionState): rest near centre 0.4–1.0 s → look point (eyes 0.5–0.8 of range, any direction) held 0.5–1.2 s → 60% chain to a second point → rest. Each point carries a 10–18° body/head turn, pitch tilt and (40%) a head tilt, on underdamped springs (ω≈14, ζ≈0.42) for follow-through. Hold cap resets per itinerary point (no more dead 1.5 s recenter); hard recenter is 0.5–0.75 s.
- **Breathing** squash/bob roughly 2× (visible), plus a small bounce every 3.5–6.5 s with landing squash.
- **Startle**: life targets snap to front during the startle window and resume 0.3 s after, so the clip keeps living to the end.
- **Check**: `contact_check.py` samples every 0.5 s, measures eye centre from pixels; FAIL if 5 consecutive samples move < 6 px and turn < 8°. engine9: 0 windows.

## engine10
- **Visual turn cap** (drawMark): total body + head yaw/pitch soft-capped with tanh per shape — sphere 22°/15°, heart 13°/10° (its 3D projection lumps past ~14°), other shapes 17°/12°. Life turns toned down to 6–11° body, 0.45–0.75 eye range. Non-sphere eye in-face shift raised (px 0.07→0.10) so the heart still reads its look direction.
- **Black-eye blends**: blendPairs matches spine direction (opposite spines crossed mid-blend → dot) and floors blend length at 60% of the shorter endpoint; barBlend stub keeps ≥65% of the longer bar.

## Human behavior model (engine11)

Engine8–10 chased a reviewer metric ("no 2.5 s without visible change") and the result was a
hyperactive, sped-up character. Engine11 throws that layer away (life itinerary, anti-freeze
glances, hold-cap recenters, bounces, drift sines) and rebuilds behaviour from how people
actually behave in front of a screen. Goal: a small Generical that lives in the phone —
intentional but unscripted; calm by default; stillness is allowed.

Code: `HumanBehavior.kt` (decisions) + `AvatarMotionState` / `MotionDirector` (execution).

### 1. Temperament (slow mood / energy)
State: `energy`, `boredom`, `curiosity`, `valence` (from the mood engine). Energy and curiosity
wander as Ornstein–Uhlenbeck processes (τ ≈ 1–2 min) around a baseline from the personality
knobs (energy, curiosity derived from energy/playfulness/warmth); time-of-day fatigue lowers it.
Boredom builds over minutes of quiet (≈ 150 s to saturate) and drops fast on any event.
Readable states: Tired, Bored, Calm (default), Curious, Lively.
Outputs: `activity` 0.1–0.8 (**capped: never hyperactive**), `tempo` 0.7–1.1 (move speed),
`amplitude` 0.5–1.0 (turn size), `playful` (good mood + energy + playful personality).
Why: mood should change *how* it moves over minutes, not flip per second.

### 2. Attention & perception
`Attention.perceive()` decides whether and when a stimulus is noticed:
- Probability rises with salience, falls with eccentricity from the current gaze (acuity cone),
  absorption in a task (thinking 0.75, reading 0.6, idle 0.3), boredom, low energy. Out-of-view
  ("behind") events are heard, not seen: lower probability.
- Latency 150–600 ms: longer when tired, absorbed, or the event is far from gaze; higher salience
  is faster. Touch (poke) is felt directly: 100–280 ms, never missed.
- Off-gaze / out-of-view: +0.55–1.35 s, and the response is mostly a **double-take**
  (glance, start to look back, snap back and hold, blink, sometimes Surprised).
- Low-salience events can be missed entirely (logged as `missed`).
No omniscience: everything the avatar reacts to (stage events, typing, pokes, messages) goes
through this gate; the old per-event handlers now run only *after* a percept lands.

### 3. Response repertoire
`Repertoire.choose()` picks from: None, EyesOnly, Glance, GlanceBlink, Acknowledge (nod),
Smile, CuriousTilt, SlowBlink, Sigh, and for big things Startle / dumbfounded Freeze.
Weights depend on salience, energy, boredom, curiosity, warmth, expressiveness. History per
stimulus kind: the previous pick ×0.25, anything in the last three ×0.6, so the same event
gets a different answer. "No visible reaction" is always on the table (more when tired, bored,
low salience, low expressiveness). Startle is likeliest when caught off guard (bored/tired).

### 4. Idle as human timing
`IdlePlanner` fixations are log-normal: median ≈ 4.8 s when calm down to ≈ 2.5 s when lively,
range 2–8 s. Kinds: rest near front (most), slow drift (smooth pursuit 1.4–2.8 s), thinking
stare off to the side (2–7 s), look down at the conversation, occasional side glance that
returns. Small shifts are eyes only; the head joins bigger ones (~75 % of the way, 80–140 ms
after the eyes). Micro-saccades every 1.2–4 s, tiny. Blinks: log-normal gaps (median 3.4 s,
sometimes a long stare), ~9 % doubles, rare triples. Fidgets: a 2–4° posture shift every
10–28 s / activity. Sighs only when bored or tired, every 25–65 s at most.
Up-right is only ever a short glance (≤ 0.9 s, then back to centre).

### 5. Motion vocabulary
| Action | Curve | Parameters | Why |
|---|---|---|---|
| Saccade | critically damped | ω 38 | ballistic, 30–80 ms, no visible overshoot |
| Micro-saccade | critically damped | ω 38, ≤0.02 | re-fixation inside a stare |
| Smooth pursuit / drift | ease-in-out glide | 1.4–2.8 s | eyes following / wandering, constant-ish speed |
| Head turn | critically damped | ω 7.5×tempo | heads are heavy and controlled — no wobble |
| Head turn (playful) | underdamped | ω 9, ζ 0.62 | overshoot only reads right when happy/lively |
| Body filter | critically damped | ω 8–12 by activity | pose changes land softly; ζ 0.7 only when playful |
| Nod | ease-in-out arc | 0.62–0.8 s | acknowledgement: one dip, no ringing |
| Nod (playful) | spring | springNodPose | happy agreement gets follow-through |
| Startle | snap + spring | 70 ms pop, squash, settle; eyes wide 0.6 s | reflex; the one deliberately springy reaction |
| Dumbfounded freeze | snap + rigid hold | 1.1–1.7 s, thaw on a blink | stiffness as a stylistic choice |
| Settle / layer crossfade | ease (smootherstep) | 0.35–1.3 s | returning to rest is quiet |
| Breathing | sinusoid | 4.2 s × (1.25−0.35·energy), tiny | presence without motion noise |
| Blink | ease-in close / ease-out open | ~60 / ~120 ms | real blink profile |
| Sigh | ease-in-out bump | 2.2 s, head sinks 4°, squash | boredom/tiredness made visible once |
| Fidget | critically damped | ω 6, 2–4° | weight shift, not a gesture |
| Double-take | critically damped | 0.45 s look, 0.25 s back, snap | the second look is faster |
| Curious tilt | eased hold | 1.2–1.6 s | interest, held while looking |
Anticipation (counter-move before a big turn) is only used when playful.

### 6. Expression transitions
Faces change quickly, with a style picked from context:
- **Blink swap** (38 % by default): the face changes while the lids are shut — the blink is the transition.
- **Snap** (40 %): 120–220 ms ease-out; used for reactions (Surprised always snaps).
- **Hesitate** (22 %): a partial move to ~45 %, a short hold, then settle — a micro-expression.
- **Gradual** (0.9–1.4 s ease-in-out): mood drift, tiredness, Bored/Sleeping.
The old 0.45–0.7 s uniform morph read as slow and mechanical.

### 7. Knobs (decision model, no user sliders)
energy → temperament baseline (activity, tempo, amplitude), blink rate.
warmth → acknowledging / smiling responses, social glances, attentiveness.
playfulness → playful mood gate (springy heads, playful nods, anticipation), curiosity.
expressiveness → face responses, startle / freeze likelihood, fewer "no reaction" picks.

### Motion budget
Measured as summed |Δyaw|+|Δpitch|+|Δroll| of the composed look plus eye travel per second
(`budget.py`); engine11 targets ≤ 1/3 of engine10. Measured: idle 9.8 vs 71.9 deg/s (≈1/7),
busy 9.9 vs 64.6 deg/s (≈1/6.5); eye travel 15.8–21 vs 59–69 px/s (≈1/3–1/4); squash ≈1/4.

### What was removed and why
- engine8–10 "life itinerary" (forced look every 1.5–2.2 s), anti-freeze glances, hold-cap
  recenters, periodic bounces, drift sines: they existed to satisfy a "no 2.5 s stillness"
  metric that was wrong. Stillness is now allowed; liveliness comes from timing and variety.
- Underdamped springs as the default for body/head: now critically damped; springiness is
  reserved for startle and playful moods.
- Uniform 0.45–0.7 s expression morphs: replaced by the four transition styles above.

### Verification clips (engine11)
idle 40 s, busy 30 s, reactions 38 s (same ping 6×: EyesOnly, GlanceBlink, Smile,
Acknowledge, EyesOnly, None — 310–360 ms latency; an out-of-view event noticed 1.0 s late →
double-take), startle, expr. Behaviour logs: `<clip>_behavior.csv`.

## Engine12 additions

### Expression transitions without stutter
engine11's `Hesitate` style (partial move → plateau → settle) read as a hitch, and a blink-masked
change could start its morph before the lids actually closed, so two systems fought mid-transition.
Now one system (`ExpressionDirector`) owns the morph, progress is strictly monotonic, a new target
queues behind a running morph (never retargets it), and the styles are:
Blink (waits for lids ≥ 85 % closed, swaps in ~50 ms; falls back to Snap after 180 ms),
Snap 120–220 ms ease-out (reactions), Soft 280–380 ms ease-in-out (self-initiated),
Gradual 0.9–1.4 s (mood drift, drowsiness).

### Character moments (`MomentPlanner`)
Rare, motivated beats between the calm stretches. Global spacing 26 s (calm) … 10 s (lively),
per-moment cooldowns, recency penalty, a weighted "nothing" option, and randomised variants.

| Moment | Motive | Motion |
|---|---|---|
| Spin | playful/happy (or very bored, lazier) | 360° yaw, underdamped spring ζ≈0.5–0.7: slight overshoot, settle |
| PartialSpin | curious / bored | turn 95–150° "to look behind", hold 0.7–1.4 s, spring back |
| Scan | curious / bored | 2–4 fixations with 0.7–1.5 s purposeful holds, then back |
| Peek | curious | critically damped lean + tilt 7–11° to one side, eyes follow |
| Stretch | quiet > 40 s and bored/tired | rise + elongate, eyes shut, hold, settle, blink |
| SleepyDrift | low energy / very bored | head sinks over 3.5–5 s, lids heavy, perks up (double blink) |
| NoticePill | curious, pill on screen | looks at the pill, maybe tilts / small smile |
| Wiggle | nice message (tone valence > 0.4), ~35–65 % | damped roll wiggle + Happy |
| SmugNod | reply finished, ~25 % | Smug accent + nod |

Spins are drawn as a card turn (silhouette narrows by |cos|, real 3D yaw only leans within the shape cap, eyes hidden on the back) so every shape, including the heart, keeps its outline through the spin.

### Up-right never rests
If the combined pose stays up-right for > 0.75 s the up component eases out (150 ms) and gaze
is pulled back near centre.

### Grok eye spacing per shape
Circle/blob is the reference and unchanged (min gap 0.40 × mark radius). Shapes with a smaller
or narrower face scale the minimum gap (`WhEyeSeat.grokGapFix`: heart 0.72, star 0.62, wedge 0.74,
flower 0.87, hex 0.88, sparkle 0.90) so separation relative to the face matches the circle.

### engine12c: rigid, flat, single-curve spins
- Real soft-3D rotation: the spin adds to the solid's yaw after the per-shape cap; the silhouette
  comes from the ring-mesh projection. The body is one flat fill, always. There is no mesh shading.
- Eyes are rigid on the front surface: seated through the same projection at the full yaw with no
  safe-region squeeze, gap push or drift. The rest-pose gap offset is baked into the surface seating
  once per spin. An eye's tangent plane is culled exactly when it turns past edge-on (normal z <= 0),
  and the silhouette clip occludes any part beyond the limb. There is no early fade.
- During a spin, gaze, head look, lead, lids, expression and the other body rotation layers are held.
  Afterwards they hand back over 0.35 s.
- The spin curve is one ease-in-out sweep to just past the stop, then one small ease back.
  Velocity is unimodal, and the only sign change is the end overshoot. Nothing re-targets it.
- Heart loft depth is 0.30, down from 0.62, so edge-on the heart is a slim lens instead of a lump.

## Engine13: motion craft per behaviour

General principles: anticipation only where a real body would wind up. The eyes lead deliberate
looks and lag inertial moves. Endings decelerate to rest; bounce is reserved for genuinely bouncy
emotions (startle, playful hop). Every moment varies its size, timing and side, and avoids
repeating its last variant. Restraint wins: when a moment could read as canned, it was cut or
made smaller.

| Behaviour | Anticipation | Main curve | Eyes vs body | Ending | Variety / restraint |
|---|---|---|---|---|---|
| **Spin** | Optional run-up (≈45–60 %, history-aware): 3–7° counter-turn plus 1.5–3.5 % crouch over 0.2–0.34 s (ease-in-out), released as the turn starts. | Velocity rises from rest on a half-cosine to a peak at 24–36 % of the turn, then falls on a longer half-cosine to exactly zero. Friction-like, unimodal, no body overshoot. 0.95–1.2 s (playful) or 1.25–1.6 s. | Rigid on the surface during the turn (face, gaze and rotation layers held). At the stop the face keeps sliding 0.05–0.1 face units in the spin direction (ease-out, 0.14–0.22 s), then glides back (ease-in-out, 0.38–0.6 s). | Dead stop. Optional shake-off (more likely when tired or playful, less likely if it happened last time): 2–3 decaying 4–5 Hz head shakes of 3.5–6.5°, eyes lagging slightly, 50 % with a blink. | Direction is random but never three times the same way. If the run-up/shake combination would repeat, one part flips. |
| Half turn "look behind" | — | — | — | — | **Removed** (read as canned). |
| **Scan** (look-around) | A blink rides the first big shift ~30 % of the time. | Saccades (fast, no bounce); the head follows bigger shifts ~100 ms later, critically damped. | Eyes lead, head follows. | Final fixation near centre with a 1.5–3 s rest. | 2–4 points with 0.7–1.5 s holds, sometimes doubling back. |
| **Peek** | The eyes go first. | The lean and tilt start 120–220 ms later, critically damped. | Eyes lead. | Critically damped return, no bounce. | 5–8° tilt (was 7–11°), 50 % Curious face. |
| **Stretch** | A small crouch (first 12 %). | Ease-in-out rise and elongation, eyes shut, hold. | Lids close after the rise begins. | Ease-in-out settle, a blink, 50 % a relaxed exhale (sigh). | Only after > 40 s quiet when bored or tired; 180 s cooldown. |
| **Sleepy drift** | — | The head sinks slowly (ease-in-out, 3.5–5 s), lids heavy. | Lids lag the head. | Perk up with a quick ease-out lift, a squint, a blink (double only 35 %). | Only at low energy or high boredom. |
| **Pill glance** | — | A saccade to the real pill. | Eyes land first; a head tilt follows 250–450 ms later (45 %). | Gaze returns by the normal planner. | 35 % small smile. |
| **Wiggle** (nice message) | — | 1.5 decaying roll cycles of 3–5° (was 2.5 cycles of 5–8°). | Eyes counter-lag slightly. | Decays to rest. | Only for clearly positive tone (valence > 0.5), 25–50 %, 40 s cooldown. Toned down (was cringe-prone). |
| **Smug nod** | — | A nod (springy only when playful). | Smug accent first. | — | ~25 % of finished replies, 50 s cooldown. |
| **Reactions** | — | Perception latency 150–600 ms; the response comes from the repertoire (including no visible reaction); intensity scales with mood and salience. | Eyes orient first. | Per motion vocabulary. | History-aware; nothing behind the avatar is noticed before it is perceived. |
| **Idle** | — | Human fixation timing (log-normal 2–8 s), slow drifts, micro-shifts. | — | — | Low motion budget. Up-right never rests (> 0.75 s eases back). |

### Generical expressions: Julian's Figma set (engine13)

`assets/avatar/generical-eyes.json` is generated from Julian's per-frame SVG export
("Generical expressions" Figma, 500×500 frames). Rect and path data, corner radii and gradients
are copied unchanged (`port.py`). The outline is drawn at the house width of 5.

| Julian frame | Engine id |
|---|---|
| Neutral | Neutral |
| Bored | Bored |
| Sad | Sad |
| Happy | Happy |
| Eyes closed | Eyes closed |
| Sad/hurt/confused | **not used** (Julian disliked it) |
| Surprised | Surprised |
| Worried | Worried |
| Suspicious/angered | angered or concentrated |
| Angry | Angry |
| Angry/pouty | pouty |
| hurt/cute | hurt or cute |
| Sleeping | Sleeping |
| Looking to the right | **confused** (raised eyebrow, "hm, really?"), plus "confused left" mirrored about x = 250. A normal expression, never tied to gaze or head turn |
| Looking up | not used (gaze is the 3D turn only) |

Only "Group 3" (playful) is kept from the old set, because no frame of his matches it.

All frames are resampled to the same 96-point outline (top-start, clockwise), so morphs between
any two frames, path or rect, are a single point-to-point blend.

**Confused** (engine13.3): Julian's raised-eyebrow frame is the only confused face, and every
confused trigger uses it:
- confused tone,
- thinking variety,
- the 🤨 skeptic in mutual glances,
- 8 % of curious-tilt responses to something odd.

The variant is chosen in `setReaction`: the raised brow goes on the side it is looking toward
(head look beyond ±0.12). Looking ahead, it alternates sides with a 35 % chance of repeating.

The extra skeptical triggers from engine13.2 (for example on surprised tone) are removed, so
confused appears no more often than in engine13.1. Grok keeps its confused capsules for both
variants.

**No gaze-shape swaps** (engine13.2, Julian): the look-right/look-up frames are not used. Eyes
keep their expression shape while looking around. The look comes only from the real 3D turn,
with the eyes riding the surface projection.

**Angry tone-down.** The Annoyed accent slant went from 0.32 to 0.14 and the upper lid from 0.60
to 0.74, per Julian's note that our angry was too angry.

**Grok** keeps its capsule set. His shapes don't map naturally onto Grok's black capsule eyes.

### engine13.1 polish

- **Recovering pause after a spin.** When a spin (and any shake-off) ends, the eyes rest near
  centre for a random 0.8–2.5 s with only micro-saccades. Idle glances and moments wait; real
  stimuli still get through.
- **No self-noticing.** The avatar no longer "perceives" its own accent events. They used to
  trigger a second look right after its own reaction (a double movement).
- **No moment on top of a reaction.** Self-started moments wait at least 3 s after any
  perceived stimulus.
- **Spin direction history fix.** The never-three-in-a-row rule now also works for leftward
  runs.
- **Grok cues for Angry and Group 3.** Angry is a mild 11° opposite tilt of the normal pills
  with a touch of spread; it never touches. Playful (Group 3) has one eye a hair shorter and
  tipped −10°. Both are small lid/tilt cues in the existing capsule style.
- **Angry stays rare.** Classifier tone "angry" maps to Concern, which shows the Worried
  accent. The Annoyed accent appears only after 3+ rapid pokes, and is milder since engine13.

## Engine14: spacing and Figma 1:1

### Grok spacing
- **One spacing per shape.** The neutral face at rest gets the circle's 0.40 gap, scaled per
  shape (`GROK_GAP_FIX`). That centre distance is baked into every expression and pose.
- **Calibration.** `GROK_GAP_FIX` is calibrated so that neutral centre distance / inscribed face
  diameter = 0.322 on every picker shape (the circle's value; the circle itself is unchanged).
  Inscribed diameter is used because it is the solid "face" of spiky and lobed shapes (cloud,
  sparkle, clover, star), where the bounding box overstates the face.
- **No expression widens the spacing.** The per-expression `offX` spreads and `spreadFor` are
  gone. Wide expressions that would come too close (flat bars, happy arcs on small faces) first
  shrink about their own centres (down to 72 %). Only the remainder is solved by the tiny
  never-touch guard (`TOUCH_GAP` 0.13 × radius).
- **Sphere.** The sphere path carries the baked spacing in `offX`.

Measured table: `lastchat-renders/engine14/grok_spacing_table.csv`. The only rows above
neutral + 3 % are guard-bound (gap = the never-touch minimum).

### Generical = Julian's Figma, 1:1
- **Circle at fit 1.** On the circle the eyes are exactly his 500×500 frames: same eye size,
  spacing and vertical placement, with no re-centring of any frame.
- **Decal mapping.** The sphere decal uses an orthographic lift instead of the arc-length map,
  so at rest it projects back exactly onto the flat design.
- **Outline.** His outline is 8 on 500, scaled with eye size. This replaces our 5.9; the
  earlier "thick outline" rejection predates his designs.
- **Shade.** The gradient runs 4 above to 4 below the eye box, like his. The shade mix is 50 %
  toward the surface (his white → #87D2E9 on #009FE0), replacing the old 80 %. The toggle is kept.
- **Pixel diff.** The diff against his PNGs, rendered with his body colour, gives IoU 0.988–0.999
  and boxes within 1 px. The remaining error is anti-aliasing only
  (`engine14/generical_figma_diff.{png,txt}`).
- **Other shapes.** Eye size scales by the shape's face tune relative to the circle, and spacing
  scales with it (gap = fit). His clear-space-to-eye-width ratio of about 0.41 therefore holds
  on every shape.

### Engine14: message actions collapse
The actions row and toolbar under chat messages now use `ExpandableContent` (one 240ms tween of height+alpha to exactly 0) inside `CollapseSpacingColumn`, so the spacedBy gap shrinks with the row instead of snapping at the end — same fix as the settings menus.

### Engine14.1: weighty spin
360° now takes ~1.8–2.0s with a peak of ~295–317°/s (was ~0.9–1s and ~385–433°/s). Velocity rises along a half-cosine over the first 22% (mass), holds momentum, then glides out over the last 40% with a friction-like (1-s)² decay to exactly zero, so there is no bounce. The run-up is 0.30–0.44s, and the eye drift is longer and scaled to the slower turn.

### Engine14.2: Generical eye size
Eyes stay Figma-faithful for shape, gradient, outline (8/500), spacing and placement, but size on every shape is scaled by `GENERICAL_FIGMA_SIZE = 0.91` (~midway between the pre-engine14 ~0.81 and full 1:1). Grok is unchanged.

### Engine14.3: Generical gap bump
Size stays at 0.91× Figma. Interocular gap is `fit × GENERICAL_GAP_OVER_FIT` with `GENERICAL_GAP_OVER_FIT = 1.04` (~4% more distance). Grok unchanged.

## Engine15: intentional aliveness

The root fix: nothing on the face happens "to do something". Every change has a cause, and
idle looking is attention, not random glances.

### Behaviour model
- **State**: mood (valence/arousal), temperament, an attention episode, and a short
  recent-event memory (`remember` / `recently`: "nice", "notice", "ack", "face:X").
- **Causes**: external (message tone, typing, poke, a message arriving, the pill, a
  neighbour avatar) or internal (noticing something during an attention episode, a
  thought completing, boredom settling after a long stillness, afterglow of a recent nice
  moment, recovering from a spin). Every face change is logged as
  `expr from→to cause=…` or `face from→to cause=…`; the audit asserts there are no
  `cause=unspecified` changes.
- **Micro-stories** (`tell` / `trainUpdate`): 1–3 beats with perception delays, e.g.
  - notice → (0.45–0.75 s) curious → (0.7–1 s) Happy (soft) → nod — "likes what it noticed";
  - notice → curious (+ sometimes a tilt);
  - notice → just a blink;
  - acknowledge → nod → blink.
  While a story runs, spontaneous engine beats wait.
- **No cuts across the mood map**: low → bright (Bored/Sleeping/Sad/Eyes closed/Worried →
  Happy/Group 3/playful) always goes through a blink-swap to Neutral ("notice"), a curious
  accent, then the target. Bright → low settles through ~1.2 s of neutral. The busy-face
  rotation also never jumps Bored/Sad/Worried → Happy.
- **Removed**: the random idle mood faces (Happy/Glee/Smug/Curious/Worried rolls) and the
  idle engine beats that were random faces or glances (curious, smile, glance, survey,
  shyLook — curious/smile remain only while a matching tone emotion is active).
  Stillness and holding a face for seconds is the default.

### Looking around = attention episodes (`IdlePlanner.nextAttention`)
1. A long rest looking roughly ahead (4–12 s, never up-right).
2. An interest: an imagined point anywhere in the full gaze range (sides/below favoured;
   up-right only briefly), saccade with the head, held 1–3 s (micro-saccades on top).
   25–45 % of interests are "noticed" (a story above).
3. Then one of: soft drift away (pursuit), a slow pan across 2–3 related points in one
   direction (pursuit, 0.5–1.4 s holds), an acknowledge (nod + blink), or just look back.
4. Back to rest (slow pursuit).
Eyes lead; the head follows 120–220 ms later and carries ~80 % of the turn on its own
spring (lead-lag, not lockstep). Gaze is only ever the real 3D turn with eyes on the surface.
The Scan moment is now a pan across related points with pursuit, not random teleports.

### Spins
Own cadence: every 45–90 s of calm (×0.65 when lively), only when nothing else is going on
(no reaction/story/social/focus, not recovering). Every spin path (cadence, poke flurry,
twirl beat) uses the engine14.1 weighty spin with run-up / eye drift / shake variants and
the recover pause; the old plain-tween gesture spin is no longer used. Audit (600 s idle,
5 seeds): 7–10 spins ⇒ one every ~60–85 s.

## Engine16: the full character (episodes, spin axes, rest, eye colour, editor)

Goal: a quiet little character that is alive *for a reason*. Stillness is the default; the
catalogue is large but sparse, so a user keeps discovering beats for hours. Every idea was
filtered through "would a small character on a phone do this, with a beat of thought?" —
anything showy or busy-for-its-own-sake was cut or made very rare.

### Idle episode planner (`IdleEpisodes.kt`)
One planner owns every *self-started* idle beat (it replaces the engine12 spontaneous
`MomentPlanner` roll; that class now only serves event moments like Wiggle / SmugNod).

- Checks every 3.5–8.5 s, only when **free** (idle lifecycle, no reaction / train / social /
  focus / spin / recovery / rest, ≥ 3 s after any outside stimulus).
- Global spacing 30 s calm … 14 s lively (×1.3 when drowsy). "Nothing" is always the
  heaviest single option; choosing stillness backs off half a spacing.
- Weight = tier × reason(context) × cooldown² × history (last pick ×0.15, recent ×0.5).
  Reason 0 = cannot happen (e.g. RestEyes needs drowsy + 90 s untouched; Smug needs a nice
  moment in the last 2 min; ContentSlowBlink needs good mood).
- Tiers: Common 1 · Uncommon 0.38 · Rare 0.1 · VeryRare 0.03.

| Episode | Tier | Reason / what it looks like |
|---|---|---|
| CuriousLR | Common | look left, then right, back to rest (sometimes a curious brow), maybe a blink |
| CheckOnYou | Common | looks at the person (front, slightly down); soft smile if mood is good, else a blink |
| TiltChain | Common | eyes to a point, soft head tilt one way, then the other, straighten |
| Scan | Common | slow pan across a few related points |
| SkepticalLR | Uncommon | look left with the left brow raised (`confused left`), then right with the right brow (`confused`), back to rest, blink |
| HmmLean | Uncommon | small lean + puzzled eyes toward something low; sometimes a "got it" nod |
| DoubleTake | Uncommon | glance, start back, snap back (sometimes surprised) |
| Peek | Uncommon | leans to peek to one side |
| FollowSomething | Uncommon | eyes track something drifting across (smooth pursuit), lose it, blink, maybe tilt |
| LookUpRemember | Uncommon | looks up-**left** (never up-right), puzzled … "ah" nod |
| ContentSlowBlink | Uncommon | cat-like slow blink with a soft smile (good mood only) |
| NoticePill | Uncommon | looks at the activity pill (when present) |
| Stretch | Rare | stiff after stillness: stretch up, eyes shut, exhale |
| Yawn | Rare | rises, head back, eyes squeezed (`Eyes closed`) ~1.5 s; 50 % a little head shake + blink-blink + squint to wake up, else a slow blink |
| SleepyDrift | Rare | head sinks … catches itself |
| RestEyes | Rare | after a long untouched spell: lids close slowly (head dips a touch), 5–10 s; may doze (`Sleeping`, 35–80 s) then wake on its own (sometimes with a yawn) |
| WorriedGlance | Rare | glance down-side, worried, blink, back, sometimes a reassured nod |
| ComeToSenses | Rare | zoned out → blink-blink, small head shake, squint, back to the present (no spin) |
| Smug | VeryRare | remembers a nice exchange: small smug nod |
| LookAround360 | VeryRare | long survey far-left → far-right with a tilt |
| Sneeze | VeryRare | scrunch, tiny recoil, blink-blink |

Every episode is logged (`episode X (tier) why=…`) and played as a `tell()` train, so each
step is logged too; any face change inside carries its cause (e.g. `skeptical: sizing
something up on the left`, `yawn (tired)`, `dozed off while resting eyes`).

### Resting eyes / dozing / waking
`restPhase` 1 = resting eyes (lid → 0.06, head dips ~5°, slower breath look), 2 = dozing
(`Sleeping` face, lid 0). Any outside activity (poke, typing, events) wakes **gently**:
lids open over ~0.5 s, WakeSquint + double blink, 35 % a small wake shake. Its own face
accents no longer count as activity (they used to reset the quiet timer, so drowsiness
never set in during a long idle).

### Spins: only when lively, and not always horizontal
- **Idle never spins.** The engine15 45–90 s idle cadence is gone, the aliveness "twirl"
  is disabled, and an aliveness spin beat is only honoured when not idle (or right after a
  nice moment / pokes). Audit asserts 0 idle spins.
- Spin triggers (`trySpin(cause)`, ≥ 20 s apart): poked a lot (6), reply finished in a good
  mood (14 %, ≥ 50 s since last), a really nice message in a playful mood (30 % instead of
  the wiggle), rarely while streaming in a great mood (15 % per 20–40 s check, ≥ 90 s).
- `SpinPath` varies the axis (history-aware, never the same twice in a row):
  Turn 0.28 (vertical) · Top 0.2 (turn leaning into it like a top) · Cartwheel 0.18 (in-plane
  roll) · Flip 0.16 (forward/back somersault) · Tumble 0.1 (diagonal yaw+pitch) · Barrel 0.08
  (roll with a quarter-yaw twist). All share the same weighty timeline (run-up, half-cosine
  rise, momentum, friction glide, follow-through — vertical for a flip — optional shake-off),
  and all end exactly at rest. Flips / tumbles / barrels are ~5 % slower (more mass).
- Recovery varies: after a non-Turn path 30 % brief Dizzy; else blink-blink, a pleased look
  (good mood), a small nod, or simply still. A shake-off already counts as recovery.

### Busy faces
Thinking rotation gains `Eyes closed` (concentrating), `pouty` (effort), `confused left`,
and a little `Worried`; still history-aware with holds, never stuck on one face; never
low → Happy directly.

### Generical eye colour
The eye outline is now **exactly the eye colour** (it was contrast-nudged against the shade
colour, so with shade off — fade == eye — it was always pushed toward white/black, and some
custom colours got a different rim). Fill gradient top = eye colour, bottom = mix toward the
surface (unchanged, `EYE_SHADE_MIX`), shade toggle unchanged. The *default* Generical eye on
a near-white body is ink (#1C1C1E) instead of invisible white.

### Avatar editor
Stage card with the live avatar on a soft spotlight of its own colour and a "Eyes · Shape"
caption; sections as rounded cards (Shape → Colour → Eyes); shape tiles 54 dp with a
body-colour ring when selected; swatches with a selection ring (no check glyph over the
colour); a colour-wheel "custom" swatch that fills with the chosen colour; a segmented
Generical/Grok control with tiny live previews; full-width Save with a pinned bar.

### Audit (AvatarMotionVideoTest.behaviourAudit)
5 seeds × 10 min untouched idle + one untouched hour. Asserts: spins = 0, uncaused face
changes = 0, low → bright cuts = 0. Writes `audit_summary.csv`, `audit_hour_episodes.csv`.

### Engine16.1 (Julian's review)
- Spin paths reduced to **Turn 0.45 · Top 0.33 · Flip 0.22**. Removed Barrel (flat clock-hand
  rotation), Tumble (too wild, upside down) and Cartwheel (pure screen-plane roll — also reads as
  clock rotation). Test: no path rolls in the screen plane by more than the Top lean (16°).
- Grok expression shapes (Generical untouched): no lopsided faces. `confused` / `confused left`
  → new symmetric `hm` (both pills a little shorter); `hurt or cute` (wink) and `Group 3`
  (playful) → the clean `happy` ^ ^ arcs. Face accents on Grok are symmetrised
  (`FaceParams.grokSymmetric`: sizes / lids averaged, only the mirrored rotation kept), so
  Curious / Puzzled / Dizzy / Worried no longer make one eye crooked.
- Grok eyes that are already closed (`sleepy`, `happy` arcs) ignore blinks / lid in proportion
  to how far the morph is into them — closed eyes never "blink".

## Engine16.2: live editor, seamless morphs, gaze holds

**Editor layout** (`AnimatedAvatarEditor.kt`)
- Everything is centred on the phone-width column: the preview, caption, section titles, and the shape grid and colour/eye swatches (centred wrapping `FlowRow`s with no hidden horizontal scroll), plus the eye toggle and the shade row.
- The Shape section has no card; the shapes sit on the sheet background. Colour and Eyes keep their cards.
- The preview stage has no glow (a plain `surfaceContainer` card).
- Cancel/Save is a floating pill. It reuses the floating bar style of the Lorebooks/Skills settings screens: `Surface` with 28 dp corners, `surfaceContainerHigh`, tonal elevation 6, shadow 8, 4 dp padding, and circular items. The primary item (Save) is `primaryContainer`.
- The preview is the live engine. `livePreview=false` exists only for the static Robolectric harness.

**Seamless morphs** (`AnimatedMarkAvatar.kt`)
- `drawMark(..., sink = MarkGeom())` captures the geometry instead of drawing it: the projected body silhouette, the seated eye paths with their gradient axes, the size, and the colours.
- On a shape or eye-style change, the last drawn geometry is frozen as the "from" state. `drawMorph` then blends it toward the live target over 560 ms:
  - contours are resampled (`resamplePath` first union-simplifies multi-contour 3D hulls, then takes the largest-area contour) and blended point by point
  - start points are aligned for the least travel
  - the Generical outline fades in or out, and the eye gradient and colours tween
- Body colour, eye colour and the shade tween over 380 ms.
- Interrupting a morph starts from what is on screen, so there is no pop.
- The old 0.94 size "settle" is gone.
- `MorphSeamTest` checks that the morph endpoints cover exactly what `drawMark` draws, for all shapes, both eye styles and turned poses.

**Editor reactions** (`AvatarMotionState.editorCue`)
- Each control change sends an `EditorCue(kind, at = control centre)`. It is perceived after 0.25–0.5 s.
- About 55% of changes get a glance with a real head turn toward that control, rate-limited to one per 1.6 s.
- Three or more changes within 4 s count as browsing: the avatar gets a curious look, with an 8 s cooldown.
- An eye-style swap sometimes carries a blink.
- About 1.3 s after the last change, at most one after-beat plays:
  - after a shape change, a look down at itself (30%)
  - pleased Glee
  - a nod
  - or it just settles

**Idle gaze** (`IdlePlanner.restFixation` and the `explore` branch)
- Rest directions vary:
  - front 40%
  - side 32%
  - low-side 14%, held at most 9 s
  - up-left 10%, held at most 10 s
  - slightly up 4%
- It never rests up-right.
- 20% of rests are long holds of 12–24 s (kind `*-long`).
- Area exploration: 3–5 eye-only fixations within ±0.12/±0.08 of a comfortable region (|x| ≤ 0.5, not low), each held 0.6–3.2 s.
- Debug logs: `fix <kind> → (x,y) hold`.

Renders are in `/workspace/lastchat-renders/engine16.2/` (box).

## Engine16.2b: gaze specials made very rare, setup-style editor buttons

**Gaze.** The "gaze-staying" moments from 16.2 are now extremely rare special moments. These are:
- long 12–24 s holds
- resting anywhere other than roughly ahead (side, low-side, up-left, slightly up)
- area exploration

How it works:
- `IdlePlanner.special()` puts all of them behind one shared cooldown of 15–35 min of idle time (counted as the sum of fixation time).
- Once the cooldown has passed, each rest or interest decision has a 2% chance to spend it.
- The first special can come 5–35 min into idle.
- Otherwise rests are front/roughly ahead, as before 16.2.
- When a special does happen, the old mix applies (35% of them are long holds).
- `gazeSpecialsAudit` checks this over 60-min runs: planner awake on 8 seeds, plus the full engine on 4 seeds. It asserts ≤5 specials per hour.

**Editor buttons.** Cancel/Save now belong to the first-time-setup button family:
- 52 dp `AppShapes.ButtonPill` pills, side by side with a 12 dp gap
- Cancel is outlined with a 3 dp soft ring; Save is filled `primary` with a check icon
- bold `titleMedium` labels
- spring press scale
- a soft fade-to-sheet gradient behind them

They are implemented in `EditorActionButton`.

`SetupButton` in onboarding is split into a haptics wrapper plus `SetupButtonFace` (the visual only, internal). Onboarding behaviour is unchanged. The split lets `renderSetupButtons` draw the real setup look next to the editor.

## Engine16.2c: editor fixes (dismiss, surfaces, white eyes, flicker, morph cost)

**Scroll closed the editor.** Root cause: `ModalBottomSheet` drag-to-dismiss. When the scrolled content reached its top, the leftover downward drag/fling went to the sheet through nested scroll, and the sheet hid.
- Fix: `sheetGesturesEnabled = false`. Content scrolling can no longer move the sheet.
- Dismissal is explicit: Cancel, system back, or tapping the scrim.

**Surfaces.** Cards, shape tiles, the eye-style track, the caption pill and the Cancel ring use `softSurface(k)`, a small step from the sheet colour (`surfaceContainerLow`) toward `surfaceContainerHigh`. The steps are 0.2–0.55, so the surfaces are low contrast in light and dark themes.

**Eye defaults.** Both Grok and Generical default to white (`DEFAULT_GROK_EYE_HEX = #FFFFFF`). The near-white-body → ink auto-switch is removed. The swatch order is identical for both styles: white (default) first, then ink, then the colours.

**Morph first-frame flash.** Root cause: on the change frame the morph animation still read 1. It can only snap to 0 inside the `LaunchedEffect`, which runs after that frame is drawn, so the target shape was drawn for one frame.
- Fix: `MorphHolder.signatureChanged()` sets `forceZero`, which pins progress at 0 until the effect has snapped.
- The "from" geometry is captured in that same draw, from the last drawn shape (or from the current blend, if a morph is interrupted).
- The draw logic lives in `drawLive()`, the code path the app uses.
- `MorphSeamTest.firstMorphFrameIsPreviousShape` checks that frame 0 matches the previous shape. Only a ≤1 px outline ring may differ, and frame 0 must be far from the target.

**Morph cost.** Root cause of the lag: a turned mesh body's projected hull is about 770 face contours. The resample union-simplified it with `Path.op` every frame, about 460 ms per call, roughly 100 ms per eye-style morph frame on a turned square. The fixes:
- The body is now a radial silhouette: `radialSilhouette`, the max radius in 288 fixed angle bins, a single pass. Blending is per-angle, so the alignment search is gone too.
- In steady state nothing is captured per frame; capture happens only on the change frame.
- Colour, eye-colour and shade tweens are read in the draw phase, so there is no recomposition per tween frame.
- `MorphBenchTest` logs the per-frame costs.

## Engine17 — continuous motion & smooth outlines

**Why ("manual gearbox"):** the engine16 `ExpressionDirector` never let a new face interrupt a
running morph: it queued the target until the morph finished *plus* a 0.25 s dwell, then started a
fresh two-pose lerp from rest. Every change of mind therefore froze the face on a plateau and then
restarted (measured: 46 stall frames in a 6.5 s expression train). Reactions were 120–220 ms
ease-out "snaps"; face accents ran on an underdamped ω17/ζ0.62 spring (wobbly pops); gaze holds
were dead still between saccades; the spin had a constant-velocity middle third.

**Expressions — layered blend.** The face is `base → layer1 (w1) → layer2 (w2)`
(`MotionFrame.exprFrom/exprTo/exprT` + new `exprTop/exprTopT`). A new target is a new layer that
grows over whatever is currently displayed, starting the very next frame. A change of mind back to
the face underneath makes the newest layer *retreat* along its own path (smootherstep from its
current weight). A finished layer collapses everything beneath it. Only a third change inside one
morph whose oldest pose is still >4 % visible waits (≤ a few frames). Eases: reactions quartic-out
0.24–0.34 s (quick attack, long soft landing — poke bounce kept), self-initiated smootherstep
0.48–0.66 s, mood drift 1.0–1.5 s. Blink-masked swaps are unchanged (Generical blinks kept).
Renderers: `GenericalFace.outline(..., top, topT)` (also fixes the closure index key mid-morph) and
`WhSphereEyes.morphPair(..., top, topT)` via `cleanBlend`; Grok closed-eye lid suppression
includes the top layer, so closed Grok eyes still never blink.
`ExpressionContinuityTest` compares with a verbatim legacy copy: max per-frame pop 9.2 → 6.6,
stall frames 46 → 6, max jerk 9.2 → 5.1.

**Gaze.** Continuous fixational drift (incommensurate sines, random phases) on eye (~0.01) and
head (~0.02) targets: holds are alive, never plateaus. Saccade/head-lead/pursuit springs unchanged;
front rest default, specials still via `IdlePlanner.special()` (rare), up-right still glance-only.

**Face accents.** Spring softened to ω11/ζ0.82 (shape) and ω14/ζ1 (offset).

**Spins.** `spinCurve` is the CDF of a smooth asymmetric velocity bump v ∝ u^1.5(1−u)^3.2 (peak
≈⅓ in, long frictional glide), no linear plateau; main turn 2.05–2.3 s. Still never in idle.

**Outlines.** `SmoothPath.closed` (uniform Catmull-Rom → cubics) replaces the lineTo polylines in
Generical seated eyes, Grok sphere/mesh eyes, `radialPath` and `blendContours` — no facets at
editor size. `ArtifactStripTest` renders the strips.

**Chat input.** Hint text uses `chatInputHintColor()` = onSurfaceVariant @ 50 % (both themes).

## Engine17.1 — Grok audit, one-piece morphs, shake, gaze rules

**Grok audit** (`GrokAuditTest`: 16 likely transitions × t 0…1 and a blink row per expression, on
circle/heart/square). Findings at 0d6bf864: Neutral→Bored and Angry→Sad swung through `\ /`
diagonals (straight↔straight pairs bypassed the stub guard); arc↔pill changes were staged
(straighten → turn → bend) with flat-bar plateaus and pops (Happy→Surprised, Happy→Sad,
Neutral→Happy, Neutral→Eyes closed); blinks squashed pills into dots / hairlines (Sad: pointy specks,
Surprised: squarish blobs); happy arcs thinner than the neutral stroke; "angered or concentrated"
reused the bored bars.
Fixes:
- `WhSphereEyes.smoothBlend`: every capsule pair morphs in ONE continuous motion — spine decomposed
  into chord centre / angle / half-length / bend profile, all blended at once; large turns get a
  single smooth length dip (sin πu) so the stroke never reads as a diagonal. `barBlend`/`straight`
  stages removed.
- Blink: `EyeState.closeK` — straight eyes squash (radial per-angle blend) into a soft lid bar at
  the lower middle, about eye-wide, gentle ‿, following the eye's tilt (Generical-like). Arcs only thin.
  Closed Grok faces still never blink.
- `HAPPY_W` 0.085 → 0.097; new Grok "focused" (shorter pills, 6° inward) for "angered or concentrated".

**Worried retired** (Julian): the worried-neutral face is no longer used in either style.
`EyeStyle` maps "worried" → "angered or concentrated"; `MarkLifecycle.Failed`, the Concern
emotion beat (`concern`), the error event and the `ConcernGlance` episode (was WorriedGlance) all
use the calm concentrated face (log causes: "error event: concentrates on it", "concern glance:
concentrated").

**Spins:** expressions keep blending during a turn (changes there use the soft blend, never a
blink swap); no frozen face.

**Head shake:** one shared `wakeShake(amp, cause)` for wake, come-to-senses, yawn and the spin
shake-off: eyes squeeze shut (0.14 s), 2–3 slow decaying swings (1.7–2.1 Hz, 7.5–11°, soft attack),
eyes open with the settle, then a blink. Logged as `head-shake cause=…`.

**Gaze rules:** interest/explore/think/ack glances are ≥ 0.24 from rest (pushed out along their
own direction, never up-right); `focus()` skips near-rest glances while resting; holds scale with
the move (planner ≥ 0.45 + 2.2·d, focus ≥ 0.35 + 1.6·d); every return to rest is an eased glide
(`glideHome`, 0.55–1.15 s by distance). `GazeAuditTest` reports near-rest glances (must be 0),
minimum glance offset and peak return eye speed.

## Engine17.2

- **Squircle / corner smoothing (question).** Preserved. All 26 eye shapes in the asset match the Figma export; path eyes (Bored, Sad, Happy, Angry, pouty, …) carry the Figma smoothing as ~3 cubics per corner and render within ~0.05 px of the export. Neutral / Surprised / Looking up / Eyes closed / Sleeping are exported as `<rect rx>` (pills), so they never had smoothing to lose. Morphs and blinks lerp between those true samplings.
- **Streaming token fade (richtext `Markdown.kt`).** Opacity now outlasts the blur: settle time × `STREAMING_ALPHA_STRETCH` (1.4), the blur finishes at 1/1.4 of it, and alpha = smoothstep(t)^`STREAMING_ALPHA_EASE_POWER` (1.6). Alpha is ≈0.70 when the blur clears and keeps ramping. Same path for prose and code. Capture: `engine17.2/stream/stream_fade_before_after.png`.
- **Shapes removed.** House and wedge (triangle/cone) are gone from the picker and the engine. `MarkShapes.RETIRED` (house, wedge, plus the earlier teardrop/droplet) maps saved ids to `blob` via `MarkShapes.migrate`, which `resolve`, the editor and `AnimatedMarkAvatar` use. Test: `ShapeMigrationTest`.
- **Morph resolution.** Root causes: the morph body was a max-per-angular-bin radial silhouette (stair-stepped, wobbly edges), and morph eyes were resampled to 64 points. Now `radialSilhouette` interpolates the exact contour crossing at each bin-centre angle (falling back to per-bin max for tiny contours), `RADIAL_STEP` 1.5→3 (the old step was already capped by the 400-sample limit on big bodies), and morph eyes use 96 points like the static ones. Bench (median/p95 ms, before → after): shape morph 2.35/3.26 → 2.29/2.77, eye-style morph 4.56/5.50 → 3.90/4.41, change frame 9.72/10.54 → 8.27/9.25, steady 3.68 → 3.84 (noise). Strips: `engine17.2/morph_{before,after}/morph_res.png`, zoom `morph_zoom_before_after.png`.
- **Extreme-contrast safety outline (`ContrastGuard.kt`).** Body vs backdrop and eyes vs body. Strength = (1 − smoothstep(1.12, 1.30, WCAG contrast ratio)) × (1 − smoothstep(0.06, 0.13, RGB distance)), so only near-identical pairs get it; every normal pairing scores exactly 0 (`normalColoursNeverOutlined`). It's a flat, thin stroke (body 2.2 % of the head radius, eyes 1.8 %) in the colour pushed toward white (dark pairs) or black (light pairs). It's computed from the animated colours, so it fades with colour glides. It strokes the outer silhouette of the projected body and the seated eye paths in static, live, morph and spin draws. The Generical eye outline is unchanged (eye colour) otherwise. Backdrop: `ContrastGuard.LocalAvatarBackdrop`, default `colorScheme.background`; the editor provides its container surface. Renders: `engine17.2/contrast/`.
- **Editor container surfaces.** The drawer search bar is `LastChatDrawerSearch` (ui-core), a `TextField` with `TextFieldDefaults.colors()` whose container is M3's default `colorScheme.surfaceContainerHighest`. The editor's `softSurface` now returns exactly that token in both themes (light #DEE4E0, dark #303634 on the default preset). Render: `engine17.2/contrast/surfaces_drawer_vs_editor.png`.

## Engine18 — the avatar mind

Engine18 puts a "mind" between everything that wants the avatar to move and the motion itself (`AvatarMind.kt`). Systems no longer change the face or body directly. They propose **intentions**, and a single **director** decides what lands, when, and for how long.

```
event → Perception (noticed after a mood-varying delay)
      → Appraisal (what it means here)
      → Intention {lane, priority, cause, expiry}
      → MindDirector (owner, safe point, hold, budget, cross-lane rules)
      → motion lanes: Body · Gaze · Face · Lids · Spin
```

### Pass 1 — director, lanes, audit
- **One owner for the face.** A landed face change (expression morph or accent) owns the face. It starts its **hold** once its morph has *settled*. The hold is `MindRules.hold`: 0.7–2.6 s, longer when calm or low-energy, shorter when lively, scaled by expressiveness and patience. Weaker proposals wait in the **priority queue** and **expire** (reaction 3 s, accent 1.2 s, forced accent 2.5 s, plan 2.5 s). A stronger one (poke P3, error P4) only skips the hold. It still waits for the **safe point**: no morph running, ≥ 0.42 s since the last eye change, ≥ 0.16 s after a big gaze shift ("gaze leads, face follows"). Nothing cuts a morph mid-way any more; the engine17 retreat/third-layer paths are never reached in normal play.
- **Reactions** keep their countdown paused until they are actually on screen. A reaction that never reaches a safe point expires and is logged.
- **Lids lane.** No blink while an expression morphs. Requests are deferred and land right after the morph settles; spontaneous blinks wait. A morph waits for a blink already under way ("blink just before"). The blink-masked swap (Style.Blink) is the only morph under closed lids. Slow blinks and the closed-eye head shake also start only on a settled face.
- **Budget.** Per rolling minute, spontaneous changes (≤ P1) are limited to `MindRules.budget`: 2–9, idle ≈ 4, busy ≈ 6, scaled by expressiveness and arousal. Everything below P3 is under a hard cap of 14. Pokes are exempt, and returning to rest is not new activity. Aliveness beats (other than blinks), idle episodes, glance-backs and non-forced accents all ask first.
- **Spins.** Never self-started while idle (blocked and counted). **Never while angry, sad, pouty, annoyed or bored**, including 2 s after such a face and while mood valence < −0.25. A negative face also can't start *during* a spin. The "fine, you win" spin after a long poke burst now comes *after* forgiving, only once the face is no longer cross; if that doesn't happen within 6 s, it never comes.
- **Planner.** Beat plans (notice → pause → react → settle; the old `tell` trains) carry their cause. A new plan replaces a running one only at a safe point: next beat ≥ 0.35 s away and no morph. Otherwise it is queued.
- **Rare behaviours.** Stretch 20 min, Yawn 25, SleepyDrift 15, ConcernGlance 20, ComeToSenses 18, Smug 30, LookAround360 40, Sneeze 50 idle minutes, strict. A fresh avatar starts part-cooled, so the first one comes after 60–80 % of its cooldown. RestEyes, a drowsiness response, keeps 10 min.
- **Causes.** Every handler runs under `because(cause, priority)`. Every face, accent, spin and beat is logged with its cause.

### Pass 2 — perception, appraisal, mood
- **Perception.** The repertoire's latency is scaled by `MindRules.perceptionScale`: ×0.6–2.2 (alert and curious = quicker; tired or drowsy = slower).
- **Appraisal.**
  - Pokes: first = Startled (keeps the surprised bounce); a burst reaching the patience threshold (2–5) = Annoyed; three more = FedUp. Poked again and again across minutes without a burst = Amused, then Habituated (calm blink + look, no bounce).
  - Reply end: Satisfied, or Relieved after a long one (adds a sigh).
  - Events: Interested / Concerned / Curious.
- **Mood.** `MoodEngine` events now push the mood's *target*. The push fades over about 4 min, valence follows with τ 90 s and arousal with τ 120 s, and drift is capped at 0.006/s. The mood moves over minutes; the face reacts at once.

### Pass 3 — memory, knobs
- **ShortTermMemory.**
  - No repeated looks: a glance at a spot looked at in the last 25 s goes the other way instead.
  - Poke habituation: familiarity +1 per poke, τ 150 s.
  - Places where things happened: 8–45 s later it may glance back once, with chance from curiosity, inside the budget.
- **`MindKnobs`** (expressiveness, curiosity, patience, playfulness) is the API for LC Smart Core. Set it with `AvatarMotionState.mindKnobs`, or use `MindKnobs.resolve(base, map)`. When unset, defaults come from `Personality`.

### Also in engine18
- The contrast safety outline is thicker: body 3.6 % and eyes 3.0 % of the head radius (was 2.2 / 1.8). The body outline uses the true outer boundary (path union) instead of the radial silhouette, so the star's side tips no longer kink mid-spin.
- Angry and Sad are a touch softer. Grok: lid 0.5 → 0.51, angry tilt 11° → 9.5°, sad −19° → −17°. Generical: both frames moved 10 % toward Neutral.

### Audit
- `MindAuditTest` runs 12-minute mixed sessions (idle, a 4-poke burst, a 7-poke burst, single pokes, typing, a thinking + streaming reply, idle) over 4 seeds. It fails on:
  - morph interrupted before settling;
  - eye changes < 0.4 s apart;
  - blinks starting mid-morph;
  - uncaused changes;
  - budget violations;
  - idle spins;
  - spins while negative;
  - more than 8 changes/min in quiet idle.

  It also checks the rare cooldowns over 45 idle minutes, perception, appraisal, memory and knobs, and the minutes-scale mood drift.
- `MindFrameAuditTest` measures the same from rendered frames only, so it also runs unchanged on engine17.2 for before/after (`/workspace/lastchat-renders/engine18/{before,after}/frame_audit.csv`).

## Engine19 — direct morphs, one execution layer

Engine18's mind stays; what it drives was rebuilt (Critically's review, `engine19/critically_review.md`).
Engine18 stepped: a face waited for the previous one to finish, passed back through Neutral,
then animated; a poke during a look-around first recentred the head. Engine19 removes every
such gate.

**Face = weight vector.** `ExpressionDirector` is one critically damped spring per expression
weight, shared ω, stepped in exact closed form (frame-rate invariant, Σw = 1). A new target is
just a new goal: the face morphs from wherever it is, mid-transition included. Profiles: Snap ω16
(blink alias), Soft 9 (static avatar), Gradual 4.5, Settle 3.4 (relax / baseline). Renderers
blend the weights directly: `WhSphereEyes.morphWeighted` (Grok, merged by eye name, stable fold)
and `GenericalSeatedEyes.outlineWeighted` (outlines aligned to Neutral, weighted mean).
`MotionFrame.exprWeights` carries the vector whenever no single face has w > 0.999.
When a reaction ends with a follow-up pending (queued face, train, stimuli) the face stays put
instead of relaxing to Neutral first. `mapClipExpression("neutral")` no longer forces Neutral.

**Arbiter (AvatarMind.faceSafe).** Reactive owners (≥ P_EVENT) retarget at once unless a
stronger owner is inside its hold; spontaneous faces need 1.2 s dwell (3.2 s from Neutral) plus
gaze lead. Owner flips are counted (gate ≤ 0.3/s).

**Body.** Beat switches inertialize the base target (quintic, Bollo clamps) before the output
spring. Gestures are additive layers (`Add(name, dur, fn)`): Spin (pre-spring, keeps the turn-rate
cap), Hop, Nod, Recoil (stackable startle pops, 1 − 0.6·live), TurnAway/Tilt (side follows the
current yaw — never swings back through centre), Attend, Droop. Busy nod/bob/spin are additive
over the held pose. No crossfade / anticipation queue.

**Poke reflex.** At the poke moment: base freeze 0.35 s (holds the spring's *current* position,
not where it was heading), beats held, eyes-only saccade toward the touch (head doesn't join,
pending head moves cancelled; bypasses lower-priority holds), Startled accent 0.3 s (0.18 s when
habituated). Perception 0.04–0.1 s. Habituated/default branches no longer recentre the gaze.

**Blinks.** One scheduler: refractory 1.8 s, max interval 7 s, log-normal (median 3 s, σ 0.65),
spontaneous doubles p 0.04 (≥ 6 s apart), requested doubles ≤ 1 / 40 s, saccade-coupled p 0.75.
A blink only starts when the held lid is ≥ 0.98 open, so it never overlaps a lid gesture.
Lid = held-lid spring (ω 22, 30 in a startle) × (1 − blink).

**Trace + CI.** `ChannelTrace` records every channel per tick (face weights, owner, cause,
beat, blinks, pokes); `ChannelMetrics` derives neutral returns, stop-go (per channel and whole
figure), jerk, reversals, owner flips, blink stats, poke reaction times and recentre-before-react.
`ChannelMetricsTest` hard-fails on: neutral returns > 0, blink overlap > 0, gaze react > 120 ms,
face react > 180 ms, any recentre before a poke reaction, owner flips > 0.3/s
(scenarios pan_poke, rapid_pokes, idle12, session × seeds). `BaselineTraceTest` runs the same
scenarios from frames only, so the numbers compare across engines.

Later fixes found by the trace: the up-right ease-out was a first-order lag (it started at full
speed — the biggest jerk spike left); it is now a smootherstep ramp and waits while attention is on
a poke. A beat never switches while the poke freeze is still blending, the new beat's velocity is
measured on the slowed clock, and the body drifts back in after a freeze (ω 2.6) instead of
restarting. Stop-go counts a pause only when the figure is dead for 0.12–0.4 s (a smooth reversal
through zero velocity is not a stop); jerk ignores spin-speed turns (> 120 °/s).

Frame-only comparison (`BaselineTraceTest`, 8 seeds, means; E17.2 = ef1ea0d9, E18 = 317fd1fb):

| scenario | metric | E17.2 | E18 | E19 |
|---|---|---|---|---|
| pan_poke | neutral dips / run | 0.88 | 0.25 | 0.00 |
| pan_poke | stop-go, whole figure /min | 0.05 | 0.05 | 0.14 |
| pan_poke | stop-go, face /min | 0.04 | 0.00 | 0.00 |
| pan_poke | yaw jerk RMS °/s³ | 5318 | 5479 | 1487 |
| pan_poke | pitch jerk RMS °/s³ | 17907 | 12988 | 11634 |
| pan_poke | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| pan_poke | recentre before poke react / run | 0.25 | 0.38 | 0.00 |
| rapid_pokes | neutral dips / run | 0.75 | 0.12 | 0.00 |
| rapid_pokes | stop-go, whole figure /min | 0.38 | 0.38 | 0.38 |
| rapid_pokes | stop-go, face /min | 1.33 | 0.00 | 0.00 |
| rapid_pokes | yaw jerk RMS °/s³ | 5266 | 4654 | 1828 |
| rapid_pokes | pitch jerk RMS °/s³ | 31945 | 32716 | 32026 |
| rapid_pokes | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| rapid_pokes | recentre before poke react / run | 0.12 | 0.00 | 0.12 |
| idle12 | neutral dips / run | 0.75 | 0.00 | 0.00 |
| idle12 | stop-go, whole figure /min | 0.03 | 0.03 | 0.04 |
| idle12 | stop-go, face /min | 0.02 | 0.00 | 0.00 |
| idle12 | yaw jerk RMS °/s³ | 1972 | 1827 | 534 |
| idle12 | pitch jerk RMS °/s³ | 3054 | 1630 | 1174 |
| idle12 | poke → gaze react, worst s | 0.00 | 0.00 | 0.00 |
| idle12 | recentre before poke react / run | 0.00 | 0.00 | 0.00 |
| session | neutral dips / run | 5.12 | 2.00 | 0.00 |
| session | stop-go, whole figure /min | 0.34 | 0.26 | 0.19 |
| session | stop-go, face /min | 0.34 | 0.03 | 0.00 |
| session | yaw jerk RMS °/s³ | 3782 | 4119 | 1809 |
| session | pitch jerk RMS °/s³ | 12183 | 12356 | 13840 |
| session | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| session | recentre before poke react / run | 0.12 | 0.00 | 0.00 |

Internal gate (`ChannelMetricsTest`, worst seed): neutral returns 0, blink overlaps 0, gaze and
face react on the poke frame, recentres 0, owner flips ≤ 0.26/s. Known limits: whole-figure
stop-go in `pan_poke` is ~0.14/min vs 0.05 (the brief, eased poke pause still counts once in a
while); pitch jerk is unchanged (startle pops); blink rate under repeated pokes is ~24/min.

## Engine19.1 — review fixes (look-then-poke, chain)

An outside video review of the E17/E18/E19 compares was checked against the labelled frames and
the E19 trace. The chain clip was already on time and morphing directly (Happy at 0.0/3.6/7.2 s
as scripted; the late Happy and the neutral snaps were in the E18 row). The look-then-poke
findings were real and are fixed:

- **Blink stall on a side look:** a story blink, a slow-blink response and a post-poke blink
  landed within ~2.4 s. Requested blinks now keep ≥ 0.9 s apart, and a poke keeps the eyes open
  for 0.7 s (`Blinker.inhibit`) so the startle is seen; the blink follows.
- **Late side look:** salient peripheral events that are in view no longer get the ~1 s
  "unseen" delay (0.1–0.6 s extra by salience); only out-of-view events are noticed late.
- **Poke with no visible reaction:** a first poke appraised as *Startled* always gets the
  surprised bounce and a 0.45 s Startled widen, whatever the picked response.
- **Recentre under a poke:** the freeze grips the *visible* pose within 0.1 s (it used to ramp
  in with the clock, so an idle clip heading back to centre carried the body there); an
  eyes-only poke look also settles a head turn already under way, and ends any gaze glide.

Compare videos now carry a header and boxed row labels (E17.2 / E18 / E19.1, top to bottom).

| scenario | metric | E17.2 | E18 | E19.1 |
|---|---|---|---|---|
| pan_poke | neutral dips / run | 0.88 | 0.25 | 0.00 |
| pan_poke | stop-go, whole figure /min | 0.05 | 0.05 | 0.19 |
| pan_poke | stop-go, face /min | 0.04 | 0.00 | 0.00 |
| pan_poke | yaw jerk RMS °/s³ | 5318 | 5479 | 1131 |
| pan_poke | pitch jerk RMS °/s³ | 17907 | 12988 | 26038 |
| pan_poke | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| pan_poke | recentre before poke react / run | 0.25 | 0.38 | 0.00 |
| rapid_pokes | neutral dips / run | 0.75 | 0.12 | 0.00 |
| rapid_pokes | stop-go, whole figure /min | 0.38 | 0.38 | 0.22 |
| rapid_pokes | stop-go, face /min | 1.33 | 0.00 | 0.00 |
| rapid_pokes | yaw jerk RMS °/s³ | 5266 | 4654 | 1484 |
| rapid_pokes | pitch jerk RMS °/s³ | 31945 | 32716 | 35614 |
| rapid_pokes | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| rapid_pokes | recentre before poke react / run | 0.12 | 0.00 | 0.00 |
| idle12 | neutral dips / run | 0.75 | 0.00 | 0.00 |
| idle12 | stop-go, whole figure /min | 0.03 | 0.03 | 0.08 |
| idle12 | stop-go, face /min | 0.02 | 0.00 | 0.00 |
| idle12 | yaw jerk RMS °/s³ | 1972 | 1827 | 317 |
| idle12 | pitch jerk RMS °/s³ | 3054 | 1630 | 1517 |
| idle12 | poke → gaze react, worst s | 0.00 | 0.00 | 0.00 |
| idle12 | recentre before poke react / run | 0.00 | 0.00 | 0.00 |
| session | neutral dips / run | 5.12 | 2.00 | 0.00 |
| session | stop-go, whole figure /min | 0.34 | 0.26 | 0.36 |
| session | stop-go, face /min | 0.34 | 0.03 | 0.00 |
| session | yaw jerk RMS °/s³ | 3782 | 4119 | 2550 |
| session | pitch jerk RMS °/s³ | 12183 | 12356 | 19381 |
| session | poke → gaze react, worst s | 9.00 | 9.00 | 0.00 |
| session | recentre before poke react / run | 0.12 | 0.00 | 0.00 |

Pitch jerk is higher than before in poke scenarios: that is the startle bounce (now on every
startling first poke). Whole-figure stop-go stays in the E17/E18 range except pan_poke.

## Engine20 — snappier, springier, intentional

Julian's 19.1 review asked for a broad sweep ("don't overdo, don't underdo"), followed by a
second note on idle emotions, gaze steps and aliveness. The design premise for everything
below is: **the figure is a body with mass that is driven by a mind with reasons.** Motion
comes from physics (springs, inertia, anticipation, follow-through, overlapping action), and
any change of feeling comes from a cause that the viewer could name.

### Behaviour design rationale

* **Feelings have causes and durations, not random rolls.** A real person does not flash
  sadness for one second while resting. Negative feelings come from appraising an event (a
  poke, the tone of a reply, an error). They build, hold and fade over seconds. Surprise is
  the one emotion that is brief by nature. In code:
  * `MindRules.APPRAISED_NEGATIVE` (Angry, Sad, pouty) and `APPRAISED_NEGATIVE_ACCENTS`
    (Sad, Annoyed, Worried) are dropped unless `hasEventCause(prio, cause)` holds, that is,
    priority ≥ P_EVENT or a cause such as poke, tone appraisal, reply or error.
  * `MindRules.minDwell(face)` sets the shortest time each face is held: Surprised 0.4 s;
    concentration and confusion 1.6 s; Happy and others 1.4 s; Drowsy 2.2 s; Bored 3 s;
    negative faces 3.5 s. Spontaneous faces and accents (below P_EVENT) are stretched to
    this dwell. A **dwell guard** at the face target stops another spontaneous change from
    replacing a face early; real events still pre-empt it.
  * Tone reactions are tagged `appraised` and log "tone appraisal". The busy rotation no
    longer pouts as filler and is not bored while its own reply streams, and every busy face
    holds at least its dwell.
* **A look is one act.** The eyes start first and the head sets off 20–45 ms later (it
  used to start 120–220 ms later). The two overlap and travel A → B in one go, and the eyes
  counter-roll as the head arrives. The eye spring has ζ 0.8: a fast start, an eased landing
  and an overshoot-and-settle of about 1.5 %. An *intersaccadic refractory* period (0.24 s)
  keeps a new self-paced look waiting until the current shift lands, so a look is never
  redirected half-way. The planner also waits for the head to finish its last move before
  starting a new look. A poke reflex is never delayed.
* **Secondary motion everywhere, small.** The body rides a soft under-damped spring
  (ω 24, ζ 0.42). Closing lids give it a tiny squash and a big look gives it a small bob,
  so a blink or glance carries through the figure.
* **Anticipation → action → follow-through.** Every spin now has a run-up bounce: a crouch
  that springs out into a short stretch as the turn launches. The turn starts from rest,
  peaks early and decelerates smoothly. While the body brakes, the eyes carry on in the spin
  direction; that lead builds through the deceleration, continues past the stop, then
  springs home slightly past rest.
* **Pokes always land in the body.** The Recoil pop plays on every poke. The eyes vary:
  a full widen about 45 % of the time, a small one about 27 %, or none ("body only"). Once
  the avatar is used to being poked, it widens its eyes less often.
* **Faces change faster, sometimes behind a blink.** The ω profiles are faster: Snap 20,
  Soft 13, Gradual 6.5 and Settle 5.2 (previously 16, 9, 4.5 and 3.4). A calm, non-snap
  change has a 30–40 % chance of starting a blink (`Blinker.tryNow`), and the new face is
  swapped in once the lids reach 90 % closed (`ExpressionDirector.wantBehindBlink`, waiting
  up to 0.16 s). Closed-eye faces never use this.
* **Blink.** The curve was identical from E17 to E19.1. Its reopen used `r(2−r)` on a
  reversed clock, so it started slowly and *landed at full speed*, which read as a stiff
  snap-open. The lids now reopen with an ease-out cubic (fast off the mark, gentle landing),
  `openT` is 0.13–0.21 s, and the body spring adds the squash.

### Editor

* Shape options have no tile behind them. The selection is a true contour offset: a wide
  stroke of the silhouette with the shape and a 3 dp gap band cleared in a layer. It is the
  same 2.5 dp weight as the colour rings and fades and grows in with a spring.
* The colour ring takes the selected colour itself. A colour too close to the card colour
  gets a faint contrast mix.
* The eye-type control's track uses the sheet colour, and its sliding indicator uses the
  card colour (spring). Per-type options animate only when the option set changes
  (AnimatedContent). The eye-shade row expands and collapses.
* The scroll content has 72 dp of padding past the floating buttons.
* `AvatarRenderHarnessTest.renderEditorRecording` records 30 fps frames of real
  composition with live animations. Robolectric needs `ShadowChoreographer.setPaused(true)`
  with a 16 ms frame delay, otherwise every animation snaps.

### New CI gates (ChannelMetricsTest)

| gate | meaning |
|---|---|
| `emo_short == 0` | no spontaneous face is replaced by an equal-or-weaker non-event cause before 0.85 × its dwell |
| `neg_uncaused == 0` | no negative face without an appraised cause |
| `gaze_stepgo == 0` | no shift lands, nearly stops (< 15 % of peak) and sets off again the same way within 0.15 s |
| `gaze_reversal == 0` | no shift falls back > 10 % of its distance before landing (flight ≤ 0.4 s; poke-interrupted shifts excluded) |

These gates catch the old behaviour: E19.1's head timing fails `gaze_stepgo` in every
scenario (for example idle12 4–6 of 7–10 shifts, session 18–19 of about 31).

## Engine20.1 — editor rings and stage

* **Shape selection rings are true offsets of the drawn silhouette.** `SelectionRing.silhouette`
  rebuilds the exact outline a calm 36 dp preview draws: the centred body, the face-on
  projection and the body transform. The ring is the band between offsets at distance
  gap (3 dp) and gap + stroke (2.5 dp). Each offset is a Minkowski sum with a disk (fill ∪
  round-joined stroke outline), followed by a morphological closing with radius 3 dp
  (grow by r, shrink by r). Convex corners come out as arcs and concave notches become
  fillets, with no cusps or pinches. The silhouette is first resampled into a clean
  360-point polygon, because a few SVG contours (the sparkle) defeated the path ops; the
  raw union is the fallback (the heart needed it).
* **Measured on every shape** with `renderSelectionRings` (red ring over the real preview)
  and `ring_check.py`, which takes the distance-transform from the ring's inner edge to the
  body. The gap minimum is 3.0–3.07 dp on all 10 shapes. The maximum is 3.4 dp on convex
  shapes (pixel quantisation) and 3.6–4.0 dp only inside concave fillets (cloud, clover,
  flower, heart, star, sparkle). The stroke is even.
* **Colour rings:** the dot inset is exactly stroke + gap (2.5 + 3 dp), so swatches and
  shapes share one ring weight and gap.
* **Preview stage:** no surface behind the avatar; only the label underneath sits in a pill.

## Engine21 — expressions that mean something, context fit, UI hooks

The architecture is unchanged (one face spring vector, the mind, the lanes). Engine21 is an
audit of *what* the avatar shows and *when*.

### Grok expression audit (pill language)

Every Grok face now has one readable meaning. Merges are deliberate: in pill language some
Generical faces read identically, so they share one shape instead of two near-duplicates.

| Generical id | Grok shape | Meaning |
|---|---|---|
| Neutral | neutral pills | resting, attentive |
| Happy, hurt or cute, Group 3 | `happy`: bent pills ^ ^ at pill weight (stroke 0.097 → 0.112) | happy (wink / playful merged) |
| Surprised | tall pills | surprised |
| Sad, Worried | `sad`: / \ tilt, set a little lower | sad / worried (merged) |
| Angry | `angry`: \ / 15° and a bit shorter (was 9.5° ≈ neutral) | cross, still gentle |
| angered or concentrated | `focused`: short squint, no tilt (0.36 high) | concentrating |
| Bored, pouty | flat bars | unimpressed (sulk merged) |
| confused, confused left | `hm`: both pills tipped the same way, same size | "hm?" |
| Eyes closed | `closed`: the neutral pill with its lid shut, a little wider | eyes closed (morphs by the lid only — no spin through a tilted bean) |
| Sleeping | `sleepy`: soft ∪ strokes, thicker (0.07 → ~0.092) | asleep |

Closed Grok shapes (happy, sleepy, closed) never blink. Grok accents are the averaged
(symmetric) FaceRig deltas, amplified ×1.7 (slant ×2.6, the pill's only "brow"), so Curious
is taller, Annoyed/Smug/Drowsy tilt, Shy/Glee/Focused/WakeSquint squint by different amounts.
Sheet: `grok_contact_before_after.png`.

### Generical: Figma only, no lopsided growth

* Accents that grew one eye while the other stayed (Curious, Puzzled, Smug) are now symmetric
  adaptations of a Figma face: Curious = a mild Surprised / Looking-up, Puzzled = a light
  Sad/hurt/confused, Smug = a soft Suspicious. Dizzy keeps both eyes the same size (they only
  circle in opposite phase).
* `confused` stays: it *is* Julian's "Looking to the right" frame (a designed perspective
  look); `confused left` is its mirror.
* Group 3 (a tilted cluster, not a Figma frame) is removed from `generical-eyes.json`; the id
  resolves to the designed hurt/cute wink.
Sheet: `generical_contact_before_after.png`.

### Per-transition speed (`TransitionRules`)

Faces sit in a small valence × arousal map; the distance between two faces is the size of
the emotional shift. A default (Soft) change gets ω = 15 for small shifts (d ≤ 0.3, ≈ 0.23 s
to 90 %) easing to ω = 8.5 for big swings (d ≥ 1.3, ≈ 0.47 s), smoothstep in between.
Snap (poke, startle) stays ω 20, Gradual / Settle keep theirs. Measured on `e21_speed`:
concentrated → confused 0.23 s, → Happy 0.37 s, Happy → Sad 0.47 s, Happy → Angry 0.47 s.

### The blink-mask rule (`TransitionRules.mask`)

Most face changes now happen behind a blink (lids close on one face, open on the next):
72 % of eligible changes, 85 % for big ones (d ≥ 0.8), when a blink can start (≥ 0.6 s after
the last one) or a full blink is already closing. A masking blink always closes fully, holds
≥ 35 ms and opens ~12 % slower onto the new face. A **direct morph** is kept for:

* fast reactions — Snap style (poke surprise, startles) and user-priority causes;
* closed-eye targets or sources (Eyes closed, Sleeping, Bored): the lids *are* the expression;
* Happy as the source (Grok's happy is a closed arc — a swap would pop);
* tiny intensity tweaks (d < 0.25);
* while drowsy or spinning.
Clip: `e21_mask` (each change is labelled "behind a blink" or "direct (reason, d)").

### Context appropriateness (`ContextRules`)

| context | long eyes-closed | eye rest / doze | sleepy face | bored face | yawn | drowsy accent | idle episodes | short squeeze | UI reactions | spins |
|---|---|---|---|---|---|---|---|---|---|---|
| Idle (nobody around) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓* |
| UserActive (≤ 12 s since any interaction, typing, listening) | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ | ✓* |
| Thinking / Working | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ (≤ 0.75 s) | ✓ (½ chance) | ✓* |
| Streaming | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ (½ chance) | ✓* |
| Outcome (finished / failed) | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✓* |
| Asleep (own lifecycle) | ✓ | ✓ | ✓ | — | ✓ | — | — | ✓ | — | — |

\* spins keep their own cause rules (classic Turn/Top/Flip only, never while negative, no idle spins).

Enforced in three places: `setReactionRaw` / `accent` / `playEpisode0` refuse what the table
bans (closed eyes are clamped to 0.75 s when attentive); `currentExpressionTarget` replaces a
banned busy/clip face (concentration while thinking, Neutral otherwise); and `contextUpdate`
re-checks every frame, ending a rest, doze, drowsy spell or banned face the moment the
context turns attentive. That wake is *brisk*: no shut-eyed head shake, lids open with τ 0.18 s
(was 0.45), the rest blend fades in 0.25 s (was 1.6) and the face leaves Sleeping with a Soft
spring, bypassing dwell / budget holds. The busy "eyes closed to concentrate" face is capped at
0.45 s and its dwell floor is 0.6 s (it inherited 1.4 s before — the long closed-eyes-during-
generation bug). **CI gate** `ContextFitTest`: the table bans every low-energy behaviour in
every attentive context, and over 8 seeds × 162 s of generation / typing (each preceded by
resting eyes or a doze) eyes are never shut > 1.0 s and Sleeping/Bored are never shown after
a 1.2 s grace (measured worst: 0.78 s, a blink-adjacent concentration squeeze).

### UI hooks

All hooks go through `hookGo(key, cooldown, p)`: per-hook cooldown, base chance halved while
generating, decayed by 1/(1 + 0.6·uses in the last 5 min), only where the table allows UI
reactions; variants via `pickVariant` never repeat back-to-back. Events still pass perception
first (they can be missed or noticed late).

| hook (event) | emitted from | reaction | cooldown / p |
|---|---|---|---|
| Timeline expanded (`PillExpanded`) | ActivityPill expand | when it notices: waits for the expansion, eyes to the top of the timeline, slow pursuit to its bottom (1.1–2.2 s by height), blink, back; otherwise a peek + attend | 35 s / 0.6 calm, 0.4 busy (pill 8 s / 0.7) |
| Attachment added (`AttachmentAdded`) | ChatPage (pending attachments grew) | look at the composer, then curious / a nod / (3+ files) a startle → surprised | 20 s / 0.6 |
| Model picker (`ModelPickerOpened`) | MinimalChatInput sheet | glance down, puzzled or curious, sometimes a tilt | 45 s / 0.45 |
| Message editor (`EditorOpened`) | ChatPage (editing started) | reads along at the composer, often focused | 20 s / 0.6 |
| Setting toggled (`SettingToggled`) | composer search toggle | on: small glee; else a blink; sometimes a nod | 15 s / 0.4 |
| Scroll | ChatPage | glance the way content moves / short pursuit along it / curious look | 12 s / 0.25 |
| Copy | ChatUtil | glee (+nod) / proud nod + smug / glance + blink | 10 s / 0.55 |
| Regenerate | message actions | puzzled + blink / curious tilt / determined blink + focused | 6 s / 0.85 |
| Error, Deleted, Menu, Keyboard, Fling, AppResumed | (unchanged) | as before | — |

### Fine-tuning (motion)

* Gaze: a big self-paced look against the head's still-running turn now waits for the head to
  slow (momentum used to drag the first frame backwards — a gate-visible reversal); the
  chooseFixation head-motion postpone may wait up to 0.8 s (was 0.4).
* Blink: masking blinks are full-depth with a held bottom and a slightly slower reopen.
* Wake on context: brisk profile (above). Nothing was rebuilt; all changes are tunings.

### Verification

Clips (`/workspace/lastchat-renders/engine21/clips`): `e21_timeline` (two expansions, short
and tall timeline), `e21_ui` (every hook, forced to notice for the demo via
`debugHooksAlways`), `e21_gen` (dozing, then a 30 s reply: eyes open, worst closed 0.07 s),
`e21_speed`, `e21_mask`. All Engine19/20 gates (`ChannelMetricsTest`) still pass.

## Engine21.1 — eyelids from the top, readable Grok faces, no teleports

### Generical: asymmetric is fine, growth from the bottom is not
Julian's rule: a lid sits on TOP. A more-closed eye closes top-down, and its bottom edge
stays level with the other eye's bottom. `FaceRig.shapeOutline` now works as an eyelid model:
* **Lower lid** first (the top stays put): squash below a kept band, flatten the bottom corners.
* **Upper lid** per outline point, `f = upper − slant·x` (clamped ≤ 1 unless the eye is meant
  to grow): the top drops to `bottom − f·h`, the bottom never moves.
  * Hard lids (Curious, Smug, Annoyed, Focused…) squash the top cap and **flatten the top
    corners** (`flattenX`, more the more closed) → a smaller top radius, as in Julian's
    "Looking to the right" frame. The bottom corners keep their radius.
  * Soft lids (slant < −0.05: Sad, Worried, Puzzled lead) slide the top down with a smoothstep
    taper, so corners stay round and the top tilts gently (Figma Sad / Worried).
* Size changes (`sx`, `sy`) and rotation pivot on the bottom centre, so bottoms stay level.
* Squircle smoothing is unchanged.

Accent rework (asymmetric where his frames are): Curious = lead eye a touch taller, the other
lidded from the top with flattened top corners (both bottoms level); Puzzled = lopsided tipped
lids; Smug = asymmetric heavy lids; Annoyed = Angry-style top lid, inner corner lower; Sad /
Worried = soft tilted lids (tilt reduced to match the Figma frames). Group 3 is not one of the
15 Figma frames, so it stays removed. Zoom: `lid_zoom_confused_curious.png`.

### Grok: every face reads as its name (pill language)
* **confused** = o_O: one tall open pill, the other short, tipped 24° and raised (a brow up);
  `confused left` mirrors it.
* **Angry** 28° `\ /`, **Sad** 30° `/ \` set low (were 15°, too weak).
* **pouty** = short pills, 18° frown, dropped low (no longer merged into Bored).
* **hurt or cute** = a wink: one eye the happy ^ arc, the other open (wink is in `GROK_CLOSED`,
  so the closed side never blinks).
* **Surprised** taller and slimmer; **Bored** flat bars set a little low.
* Eyes never touch (GrokEyeGapTest).

### Reading interrupted (steering a)
While the timeline-pan story is actually reading (`readingUntil`), an interruption — the
timeline collapsed (new `PillCollapsed` event from `ChatMessageV2`), a message sent, a poke, any
other UI event — stops the pan and brings the eyes back. ~40 % of the time (45 s cooldown) it
answers with a brief (1.2–1.7 s) **Bored** beat: "hey, I was reading that". The context table
has a new `BriefUnimpressed` behaviour (Bored ≤ `BRIEF_BORED` = 1.8 s) that every awake context
allows; long Bored stays banned when attentive. Clip: `e211_reading`.

### Reopen settle (steering b)
After a full, brisk blink (not a half-blink, a drowsy blink or the first of a double) the eye
opening gets a microscopic spring settle: `+1.7 %` overshoot of eye height ≈ 33 ms after
opening, one small undershoot, gone by 0.4 s (`Blinker.settle` → `MotionFrame.eyePop`; Generical
grows from the bottom like the lid, Grok pills lengthen; closed shapes ignore it). Measured on
the strips: +1 px of 77 (Generical) and +2 px of 78 (Grok). Gate: `SettleStripTest` (peak
≤ 2 %, never while the lids still move). Strip: `settle_compare.png`.

### Fix: the start-of-clip "spasm" (e21_ui)
Root cause (engine, not harness): the beat hand-over inertialization (`MotionDirector.steer`)
fits a quintic per channel; `setQuintic` may **shorten** the blend (when the old motion already
heads back toward the new beat), but the offset was evaluated up to the *nominal* fade. Past its
own length the t⁵ term explodes: the beat target's pitch accelerated through ±180° in a few
frames, the output filter's unwrap counted those wraps as ~6 turns, and the body then whirled at
the 700°/s turn cap for ~3 s. On screen: eyes jumping position to position frame by frame. It
was seed/timing dependent, so it could also hit in the app after any beat switch.
Fixes: per-channel blend lengths (`inDurC`, returned by `setQuintic`); the hand-over frame
evaluates the blend at 0 (velocity was counted twice: a one-frame kick); the output filter
never trails its input by more than ~1.5 turns (drops corrupt extra turns instead of whirling).
App start: the first frame initialises every spring at the beat pose (no jump), dt is clamped
to 0.1 s; nothing is written raw to the render.

Two smaller gaze discontinuities found on the way (ChannelMetrics reversal gate):
* After a spin, the gaze is held on screen while the live gaze kept wandering unseen; the
  hand-back blend then showed a look one way, then the other. The live gaze now adopts the held
  (visible) look when the hand-back starts (`GazeDirector.adoptVisible`).
* A fully settled (static) avatar snaps its hidden gaze to rest (`snapRest`, invisible at weight
  0), so it resumes from rest when it animates again (list → live, first appearance).
* A look against a head turn still running brakes the head's velocity (position continuous).

New CI gate `TeleportGateTest`:
* `noTeleports`: cold starts (idle, UI hooks from 0.1 s, timeline + interrupt, rapid pokes,
  generation) plus the exact e21_ui set-up (seed 83). Per 60 fps frame: body angle step ≤ 9°
  (non-spin), |pitch|/|roll| ≤ 45° outside spins, gaze step ≤ 0.16, eye lead step ≤ 0.06 (a
  saccade peaks ≈ 0.04), from the first frame on. Old engine: 11.7°/frame whirl → fails.
* `beatTargetNeverWhirls`: 60 seeds × 40 s of random lifecycle changes and gestures; the beat
  target stays < 60° and steps < 12°/frame. Old engine: 178° whirl at seed 6 → fails.

### Verification
Full avatar suite green (incl. ContextFitTest, ChannelMetricsTest, GrokEyeGapTest,
TeleportGateTest, SettleStripTest). Clips: `e21_ui` re-rendered (first 3 s checked frame by
frame), `e211_faces` (morph through the new faces), `e211_reading`.

## Engine21.2 — no parallel tilts, Puzzled redone, a cleaner Grok confused

Julian's rule: **two eyes tilted the same direction (`//` or `\\`) is never good.** Mirrored
tilts (`/\`, `\/`) are fine, and so is one tilted eye next to an upright one.

### Audit
`ParallelTiltTest` measures each eye's tilt (+ = "/", screen space): Generical on the top and
bottom lid edges (line fitted over the middle 60 %), Grok on the pill's long axis (deviation
from the nearest upright / level axis, faded out for round blobs). A pair fails when both share a
sign above 3°. Found in Engine21.1:
* Generical **Puzzled** was parallel by construction (lead lid slant −0.08 = inner higher, the
  other +0.07: both "/"), and its soft lid slid the corner (the kink).
* Accents on top of tilted faces (Smug / Curious / Annoyed on Sad, Worried, confused, hurt).
* Grok mid-morphs between differently tilted faces (confused, wink ↔ Bored, Happy, Sleeping…)
  pass through same-way leans up to ~40°; Grok diagonal gaze adds 4–7° of perspective lean.

### Fixes
* **Puzzled** (`FaceRig`): one eye lidded from the top with a *hard* brow-down lid (upper 0.7,
  slant +0.1: inner corner lower, top corners flattened, bottom level, no soft slide → no kink),
  the other upright and open a touch wider (sy 1.04). One tilted + one upright.
* **Grok confused** redesigned (`WhSphereEyes.confusedPair`, explored on
  `grok_confused_options.png`): A "brow up" (chosen) — one neutral pill, the other a little
  shorter (0.46) and raised so its top clears the other's: one raised eyebrow in pill language, nothing tipped. (Engine21.3: the shorter eye is now bottom-aligned — see below.) B "one
  squint", C "tipped brow" (one pill tipped 14°), D "soft lopsided" (mirrored 7°) remain
  selectable via `confusedVariant`. "confused left" is now the exact mirror (`reversed()`).
* **`TiltGuard`** (general enforcement for everything in between): measures both eyes and
  removes only the lean they share, minus a 1.5° dead zone — continuous, so it never pops and a
  mirrored or one-eye tilt is untouched. Generical: sheared out of the top lids only (bottoms
  stay level and in place), right after `FaceRig.shapeOutline`. Grok: each pill turned about its
  own centre, measured relative to the head roll (a rolled head is kept), after seating and
  before the gap pass; rigid (off) while spinning.

### CI gate
`ParallelTiltTest.noParallelTilt`: rest poses of every expression and accent as authored (no
guard) in both styles; and, as rendered, every expression × accent, all expression-pair
mid-morphs at t = 0.25 / 0.5 / 0.75, Grok gaze (pitch ±14 × yaw ±18) and head roll ±9°.
`-Dlastchat.gridModes=raw` writes the unguarded audit for comparison.

Caveat: the Generical seat/gaze perspective is not guarded (the guard runs on the flat outline,
before seating); it is small on the Generical seat.

## Engine21.3 — half-lidded eyes rest on the bottom edge, no grin on a cross poke, Generical in light blue, turns that read

### Grok half-lidded eyes are bottom-aligned (`WhSphereEyes.bottomAligned`)
When one eye of a pair is shorter than its partner (a squint / half-closed lid), the shorter pill used
to be nudged *up* so the two **tops** met. It read as a small shape floated toward the ceiling rather
than as a lid coming down. Rule now: the half-closed eye's **bottom** edge sits on the open eye's
bottom edge — the lid is what moved. One helper, in eye-local units (+y up), tilt included:
`offY_short = offY_open − (halfExtentDown(open) − halfExtentDown(short))`.
Applies to `confused` and to `confusedPair` variants B / C / D (A is the one in use) and `playful`.
Rendered check on the 8× audit sheet: the confused face's shorter eye went from top −6 px / bottom
−21 px against the open eye to top −15 px / **bottom 0 px** — on every shape (heart / star keep ≤2 px
of projection residue). A *closed* eye is not this pattern: the wink's ^ arc stays centred on the
open eye, because parking that arc at the other eye's bottom edge read as a droop, not a wink.

### A poke never pulls a happy face while it is cross (`AvatarMotionState.crossNow`)
`Appraisal.poke` decides `Amused` from mood valence alone, so a poke taken while the face was still
`Angry` / `pouty` could land a `Glee` accent, a queued `"Happy"` reaction or a smile. A poke now
checks `crossNow()` — the angry family on the face lane (face owner, current reaction, queued
reaction, the expression pair, an `Annoyed` accent) plus a clearly negative mood — and while it holds
the poke answers with a cross beat (a refreshed glare, a turn-away, a flat blink), never a happy one.
Not cross: unchanged, the 40 % happy-after-startle and the amused Glee still happen. The forgiveness
beat ("can't stay cross for long" → blink, small smile, mood lift) is deliberately untouched: it is
the moment it stops being cross, not a reaction to a poke.

### Generical's default body colour is the light blue (`#009FE0`)
The stock Generical animated avatar is now the **azure** swatch — the light blue immediately right of
the old stock blue in the editor grid — instead of `#0078D7`. `MarkColors.PRESETS` keeps all 14
swatches and the old blue stays selectable; `Avatar.Animated.defaultGenerical()` and
`MarkColors.DEFAULT_GENERICAL_BODY_*` point at the new pair. `migrateGenericalDefaultAvatar()` also
treats the untouched old blue pair as stock and moves it over on next start (idempotent); any avatar
that picked its own colour is left alone.

### Turns that read (`AnimatedMarkAvatar.LOOK_TURN_GAIN`)
"Looking around" now turns the 3D body about 20 % more: the head-look euler gets `LOOK_TURN_GAIN =
1.2`, and the per-shape soft caps give it room (sphere 25° / 17°, other shapes 19.5° / 14°; the heart
stays 13° / 10° because its projection lumps past ~14°). Eye seating and the silhouette stay
enforced by `EyeSafeRegion` / `GrokEyeGap`; the spin paths are untouched.

## Engine21.4 — a turn foreshortens, it never scales the eyes down

Reported: "when generical turns his head a little too much the eyes seem to become smaller — not a
natural head turn, it just looks like they scale down from a certain point", and it happened on the
circle too, sometimes.

The seat was never the problem; the guard after it was. `EyeSafeRegion.shrink` inflated each eye
outline by `GEN_EDGE_MARGIN` (21 % of the mark radius) and, if any of it fell outside the projected
silhouette, applied ONE scalar to BOTH eyes about their common centroid. So a threshold angle turned
into a pair-wide, uniform downscale — vertical included. Measured with `TurnSquashAuditTest` (eye
height at yaw 24° vs 0°): heart 88.9 %, star 90.2 %, sparkle 93.9 %; and on the sphere, wide faces with
a tilted head tripped it too — Surprised 91.0 %, Happy 90.7 % (which is why it looked
shape-independent "sometimes").

Now:
* the clip guard has its own small margin, `GEN_CLIP_MARGIN = 0.05` (the 21 % margin stays what it was
  for the turn cap — "stay well inside", not "we are about to clip");
* `nudge` (slide the pair toward the body centre) runs FIRST — continuous, and it keeps the size the
  perspective produced;
* only then does each eye get `EyeSafeRegion.shrinkX`: a horizontal squash about its own centre
  (floor 0.80), i.e. foreshortening along the turn axis. A yaw can no longer shorten an eye, and the
  pair can never scale as one object. The Grok capsules keep the old path — measured, they never
  trip it.

After: 100 % eye height at every yaw on every shape (heart, star, sparkle included), 100 % for
Surprised/Happy on the circle at yaw 24° with pitch ±10°. `TurnSquashAuditTest` is the gate: per-shape
yaw sweep plus expression × yaw × pitch on blob/cloud, asserting eye height ≥ 97 %, the near eye never
collapsing, the far eye never growing, and every eye inside the silhouette with the clip margin.

### The character widget shows the animated mark
A Glance widget can only show a static bitmap, so `WidgetAvatarRenderer` paints a real still frame
with the mark engine (`drawMark` into a `CanvasDrawScope`, `calm = true`) at the widget's size:
`render(context, avatar, seed, sizePx, variant, presetId)`. When adding an animated character,
`WidgetConfigActivity` shows eight still previews: Calm, Happy, Cheerful, Curious, Surprised, Cute,
Unimpressed, and Sleepy. Each `WidgetAvatarPreset` pairs an expression with a handpicked angle,
scaled to the shape's turn cap. Preview and widget use the same renderer. The stable preset ID is
saved per widget in both WidgetPrefs and Glance state; refreshes and restarts preserve that look.

Existing widgets without a preset keep their random seed/variant behavior: a no-anger expression
and settled attitude, with the variant advanced once per system update. Unknown preset IDs also
fall back to this behavior. Other avatar types retain their existing configuration flow.
