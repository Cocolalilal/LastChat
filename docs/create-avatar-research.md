# Create Avatar v3 — Research

Research notes backing the Create Avatar v3 rewrite (`ui/components/avatar/`).
This documents why the previous blob engine (PR #218, branch
`cursor/create-blob-avatar-2624`) was rejected, what the *real* Grok bot avatar
actually does, how the attached "Generical" expression sheet differs from it, and
the fix for the creator sheet dismissing on a fast scroll.

Everything below is measured/cited, not guessed. The goal is resemblance, so the
constants are treated as data, not knobs.

---

## 0. Why v2 (PR #218) was rejected

Concrete defects observed in the rejected build (screenshots + `BlobAvatar.kt`,
`BlobEngine.kt` on `cursor/create-blob-avatar-2624`):

1. **Grok eyes drawn as transparent holes.** `drawGrokEye` punched the eyes with
   `BlendMode.DstOut` and a faint white rim. That mirrors what x.ai's site does
   (holes in the body), but x.ai draws a **light page behind the body**, so the
   holes read as light eyes. In LastChat the avatar floats on dark chat bubbles /
   drawers, so `DstOut` holes revealed the *dark* background — the eyes vanished
   (`grok-dark.jpg`, `grok-idle-broken.jpg`).
2. **Generical = mis-proportioned Grok eyes.** The Generical pack reused the
   sphere-projected capsule pipeline instead of the flat, upright rounded-rect
   glyph from the attached sheet.
3. **Constant 2D spin ≠ Grok's flat-3D.** `bodySpin()` rotated the radial
   silhouette every frame (`BlobShapes.rotated`), i.e. a 2-D silhouette spin.
   The real bot body does **not** spin at rest; volume is implied by lighting and
   by measured per-state moves (orbit/tumble), never an endless turntable.
4. **Boring expressions.** Idle was essentially one pose with a blink.
5. **Creator sheet dismissed on fast scroll.** Content was a
   `Column(Modifier.verticalScroll(...))` directly inside `ModalBottomSheet`; a
   fling leaked residual velocity to the sheet, which then dismissed.

The rewrite keeps the *good* idea (one body engine, two eye packs, lifecycle
faces) and fixes all five.

---

## 1. The real Grok "bot" avatar

The relevant Grok mark is the **x.ai / Grok voice-mode "bot"**: a single filled
blob with two eyes, on a plain background — *not* the 3-D anime "Companions"
(Ani/Rudy), which are a separate feature.[^decoder][^testingcatalog]

### 1.1 Primary source — `jeremy-prt/bloub` (MIT)

`bloub` is a frame-by-frame SVG recreation of the x.ai bot avatar, measured off
the reference video cut at 10 fps.[^bloub] It is framework- and clock-free:
`engine.sample(t)` is a pure function of time, which is exactly the property we
want for deterministic tests and contact sheets.

Counter-intuitive facts it calls out (and that v2 got wrong):

| Assumption | What the video actually shows |
|---|---|
| Eyes lean `//` | They lean `\\`, ~26° off vertical |
| Body is a squircle | It's a **perfect circle**, radial deviation < 0.7% |
| Transitions are springs | **Exponential ease-outs**; the body never overshoots |
| The comet crosses the screen | The dot stays put, the **trail orbits** it |
| The avatar floats at rest | It doesn't — **the life is gaze drift + blinking** |

### 1.2 Eyes: small slanted black slits on the flat coloured mark (Grokky)

Julian's Grokky Character / mark screenshots (01–09) superseded the `bloub`
sphere-projected **white capsules**. The Grok pack must look like the real Bot
**mark**, not the x.ai homepage video bot:

- The coloured shape **is** the body. Eyes are **minimal dark marks** near the
  upper area — small slanted ovals / slits, typically leaning `\\`.
- They sit **on** the flat fill. No sphere tangent frame, no near/far size
  asymmetry from projection, no glowing white capsules.
- Default body is a mid coloured mark (`#4A7DC7`) so black slits read. Near-white
  requested eye colours collapse to `#171717`; if the body is itself near-black,
  the slit lifts to a light mark so it does not vanish.

`AvatarSphere.kt` still owns the **blink schedule** and idle liveliness noise
from `bloub` (those measurements remain good). It is **not** used to lay out
Grok eyes.

### 1.3 Rest life: blink + gaze drift only

- **Blink schedule** (`face.ts`): first at 1.4 s, then every 1.9–4.6 s, with an
  18% chance of a quick double-blink. `BLINK_DUR = 0.18 s`; the lid closes fast
  (first 45%) and reopens slower. Screen-space vertical squash:
  `blinkScale(lid) = 0.06 + 0.94·lid` (width preserved). The closed eye is a
  wide horizontal dash, **wider** than the open eye (measured 0.447 vs 0.236 in
  the `wink` state), not merely a flattened capsule.
- **Gaze drift** (`liveliness`): sums of `loopNoise` on mutually-prime periods so
  it never visibly repeats; amplitude a few degrees of yaw/pitch. Center drift is
  ±0.006–0.007 r and breath is ±0.005 — i.e. essentially still. "The avatar
  floats at rest? It doesn't."

### 1.4 Lifecycle faces (Grok slits)

Grok expressions stay **small dark marks**; they change pose, not glyph language:

| Lifecycle | Pose | look | split | eyes |
|---|---|---|---|---|
| Idle | rest slits, upper half, `\\` lean | lookY −0.24 | 0.155 | 0.145 × 0.052, tilt −20° |
| Thinking | look up-right | lookX +0.14, lookY −0.30 | 0.145 | slightly smaller, −14° / −16° |
| Working | a touch more open | lookY −0.20 | 0.16 | 0.150 × 0.078, −12° |
| Waiting | thin dashes | lookY −0.22 | 0.16 | 0.165 × 0.026 |
| Blocked | angry mirrored slants | lookY −0.18 | 0.16 | 0.140 × 0.038, ±26° |
| Done | happy squint | lookY −0.18 | 0.16 | 0.150 × 0.030, ±14° |

Idle also gets rare **micro-accents** (a saccade, a widen, a wink, a squint) on a
long, deterministic cooldown so it is alive but not busy — matching the video's
"quiet with occasional expression" and fixing "boring expressions".

### 1.5 Body: flat colour, subtle 3-D by *lighting*, never a 2-D spin

`bloub` bodies are flat fills. The task asks for *subtle* 3-D ("cloud tumble via
lighting — NOT endless 2D spin"). So v3:

- Keeps the **default shape a perfect circle** and the body **fill flat**.
- Adds a soft, offset **key-light highlight** + a faint rim shade computed from
  the silhouette normal — a spherical read on top of flat colour. This is
  optional (`flat3d`) and defaults on but stays subtle.
- For organic shapes the light direction **drifts slowly** (loopNoise), so a
  cloud/droplet appears to *turn under the light* (a lighting tumble), while the
  **silhouette itself is never rotated frame-to-frame**. No turntable.
- Body morphs between shapes use **exponential ease-out** (`easeOutQuint`), never
  springs — no overshoot, per the measurement.

Shapes are radial profiles `r(θ)` at a fixed sample count (circle, pebble,
squircle, capsule, triangle, hexagon, cloud, droplet), so morphing is a linear
interpolation of radii — same technique as `bloub/src/bot/shape.ts`.

### 1.6 Eyes must be portable across backgrounds

x.ai/`bloub` render eyes as holes to a light "paper" behind the body. That is
**not portable** onto arbitrary chat surfaces, and Julian rejected both the hole
trick and the light-capsule stand-in. Grok now paints **dark slits** (with a
contrast lift on near-black bodies). Generical paints an **opaque** white-border
glyph with a pale inner gradient, clipped to the body path.

### 1.7 Historical note — `bloub` / `nasawz/GrokBot`

An earlier pass treated `jeremy-prt/bloub` and `nasawz/GrokBot` as the Grok
source of truth (sphere-projected white capsules, REST_GAZE 28.49/28.62/−13).
Julian rejected that look against the Grokky Character screenshots. Those
repos remain useful for **blink timing**, **ease-out morphs**, and "the body
never spins" — not for Grok eye glyphs.

---

## 2. The Generical pack — Julian's painted face (hard visual target)

Source of truth is the attached **photographs**, not the outline sketch sheet
and not an invented glow-capsule:

| Ref | What it is |
|---|---|
| `generical-base-face.jpg` | **Locked rest face**: bright cyan/blue circle (`#009FE0`); two identical **vertical rounded-rects / squircles**; thick **white stroke**; interior **vertical gradient** white → pale cyan. Centered, symmetric. |
| `sheet-4up.jpg` | (a) both vertical eyes shifted right; (b) sad/worried, inner tops slanted toward centre; (c) wink = one tall glyph + one thin horizontal pill; (d) happy squint = two thin arched horizontal pills. |
| `eyes-horizontal-pills.jpg` | Solid-looking white horizontal capsules (closed / waiting). |
| `look-up-right.jpg` | Irregular tilted glyphs clustered upper-right. |
| `neutral-rounded.jpg` | Shorter, wider rounded rects, centered. |
| `goggle-flat-top.jpg` | Flatter top, deeply rounded bottom. |

Rules Julian locked:

- Personality is **only the two eye shapes morphing** (position, rotation, path,
  open vs squint). No mouth, no nose.
- Keep the **white border + soft inner gradient** language. Do not invent
  glowing capsules or Grok slits for Generical.
- Idle / look / emotion must **feel like this sheet**, not a fade-only of one
  glyph.

Lifecycle mapping:

| Lifecycle | Pose |
|---|---|
| Idle | Base rest face |
| Thinking | Look-up-right |
| Working | Goggles |
| Waiting | Horizontal pills |
| Blocked | Sad / slanted tops |
| Done | Happy squint |

Idle also morphs through look-left/right, wink, happy, and goggles on a long
deterministic cooldown so the rest face is alive without leaving Julian's
vocabulary.

Default body is LastChat blue (`#009FE0`). Optional body glow stays **behind**
the silhouette; it is never drawn on the eyes.

Both packs share the **same body engine** (shapes, colour, lighting, lifecycle
timing); only the eye layer differs.

---

## 3. Creator sheet: fixing dismiss-on-fast-scroll

Root cause: the sheet content was a `Column(Modifier.verticalScroll())` directly
inside `ModalBottomSheet`. Material3's sheet handles `onDismissRequest` during
**nested-scroll flings**,[^aosp] so a fast fling inside the content settles the
sheet and dismisses it. Community reports confirm this class of bug and that a
plain `verticalScroll` column is the worst case.[^so-detach][^so-intercept]

v3 fix (works on the project's `material3:1.5.0-alpha08`):

1. Content is a single **`LazyColumn`** with its own `rememberLazyListState()` —
   one scroll container the sheet's nested-scroll can cooperate with.
2. A `NestedScrollConnection` on the list **swallows residual fling velocity** so
   a content fling can never reach the sheet's dismiss path:
   - `onPostFling(consumed, available)` returns `available` (reports it fully
     consumed) whenever the fling originated inside the list;
   - `onPreScroll` consumes a downward drag when the list is already at the top,
     so an over-drag inside the content doesn't start dragging the sheet.
3. Dismissal remains available via the **drag handle** and the Cancel/Save
   actions.

This keeps normal in-list scrolling and fling, but the sheet only leaves on an
explicit gesture — no accidental dismiss mid-scroll.

---

## 4. Material / product fit

- Sheet uses `ModalBottomSheet` with `AppShapes.BottomSheet`, `FilterChip`s for
  the lifecycle preview, `SingleChoiceSegmentedButtonRow` for the eye pack,
  `FormItem` + `Slider` for knobs, `PremiumHaptics` (`rememberPremiumHaptics`) —
  all per the repo's `AGENTS.md` UI conventions (Material 3 Expressive,
  `Icons.Rounded`, no `LocalHapticFeedback`).
- Existing emoji / image / URL / resource avatars are **kept**; "Create avatar"
  is an added option in the avatar picker, and `Avatar.Blob` is a new
  `@SerialName("blob")` variant of the sealed `Avatar` (round-trips through
  settings, web DTOs, and assistant export/import).
- Lifecycle is derived at render time from `ActivityState`
  (`Reasoning/LoadingModel → Thinking`, `ToolUse/Ocr/Replying → Working`,
  `Waiting → Waiting`, pending tool approval → `Blocked`, otherwise `Idle` with a
  brief `Done` hold when a turn finishes). It is **not** persisted.

---

## 5. Sources

[^bloub]: jeremy-prt/bloub — "SVG recreation of the x.ai bot avatar. One shape
morphing through 14 states, measured off the reference video frame by frame."
Source read directly: `src/bot/face.ts`, `expressions.ts`, `states.ts`,
`shape.ts`, `skins.ts`, `math.ts`, `components/BloubBot.vue`.
<https://github.com/jeremy-prt/bloub>
[^grokbot]: nasawz/GrokBot — pure-Flutter `CustomPaint` GrokBot avatar (sphere
head-turn, 320 ms blink, 48-point morph). <https://github.com/nasawz/GrokBot>
[^decoder]: "Grok introduces interactive AI avatars for iOS app" (Ani/Rudy are
3-D Companions — a different feature from the bot mark).
<https://the-decoder.com/grok-introduces-interactive-ai-avatars-for-ios-app/>
[^testingcatalog]: "Grok debuts interactive AI Companions on iOS with 3D
avatars." <https://www.testingcatalog.com/grok-debuts-interactive-ai-companions-on-ios-with-anime-avatars/>
[^aosp]: AOSP commit "[Material3][BottomSheet] Add onDismissRequest logic to
nested scroll flings" (Bug 268433166) — confirms flings can trigger dismiss.
androidx `frameworks/support`.
[^so-detach]: StackOverflow 76392987 — M3 `ModalBottomSheet` detaches when
flinging inner content. <https://stackoverflow.com/questions/76392987>
[^so-intercept]: StackOverflow 78534461 / 79589423 — `ModalBottomSheet`
intercepts / dismisses on `LazyColumn` scroll; fix via `NestedScrollConnection`
consuming top-drag / residual fling.
<https://stackoverflow.com/questions/78534461>
