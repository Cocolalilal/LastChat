# Avatar motion reference research

Read-only research, 2 November 2026. No application changes made by this researcher. Recommendations below are hypotheses for this avatar, not claims that a reference product uses our proposed implementation. Broader additions remain proposals for user review.

## Main finding

The useful common thread is **a readable reason for each movement**, with coordinated eyes/body and room for the thought to register. The references do not support continuously adding small random movements to make a character alive. They also do not establish universal blink rates, mandatory anticipation before every action, fixed gaze delays, or a requirement for perpetual asymmetry.

## Primary sources and exactly what they support

### Pixar/Disney principles: Lasseter, SIGGRAPH 1987

[John Lasseter, Principles of Traditional Animation Applied to 3D Computer Animation (paper PDF, university mirror)](https://users.cs.northwestern.edu/~animation/Lasseter_1987.pdf), DOI 10.1145/37401.37407. Original paper, Pixar affiliation; this is an actual primary source applying Disney animation practice to computer animation.

Relevant sections: timing (2.2), anticipation (2.3), staging (2.4), follow-through/overlap (2.5), secondary action (2.10), appeal (2.11), character thought (3). Paraphrase: timing communicates weight; preparation/action/termination form a whole; parts lead and follow rather than stopping together; secondary motion must remain subordinate. Complex, awkward motion can reduce appeal. Asymmetry can avoid stiff mirrored poses. The conclusion connects actions through a character's thought and mood instead of unrelated movements. This is art-direction guidance, not a controlled experiment or a spring-parameter prescription.

Application inference: a spin should read as one decision and one impulse with a finish; a small tilt should have one focus and release. Asymmetry is an expressive option, not grounds to keep the eyes uneven forever. No source basis for enlarging the avatar during a spin. The paper's constant-volume squash/stretch discussion argues against arbitrary apparent growth, though projected size in 3D requires separate rendering diagnosis.

### Carlos Baena: simplify the acting

[Carlos Baena, Simplicity in Animation, Animation Mentor, 2019](https://www.animationmentor.com/blog/carlos-baenas-journey-simplicity-in-animation/). This is authored advice from Baena, not an anonymous paraphrase. [His own Cozmo teaser credit](https://carlosbaena.com/cozmoteaser) confirms his direction of storyboards, layout, animation/lighting and voice work for that piece.

Paraphrase: avoid too many simultaneous ideas; keep a shot readable; start with a simple idea and add or remove detail based on feedback. The concise quote is “Less is more.” This directly fits the user's criticism that purposeless micro-adjustments are worse than quietness.

Application inference: remove a competing motion before increasing damping or adding another corrective layer. Compare held-look aliveness against a genuinely quiet baseline and require every added beat to make the avatar's attention more readable.

### Animation principles tested on a robot

[Leila Takayama, Doug Dooley, Wendy Ju, Expressing Thought: Improving Robot Readability with Animation Principles, HRI 2011 (author-hosted preprint)](https://www.leilatakayama.org/downloads/Takayama.Animation_HRI2011_prepress.pdf). Dooley's affiliation in the paper is Pixar Animation Studios.

Paraphrase: an online video-prototype study with 273 participants manipulated forethought and goal-oriented reaction around successful/failed robot tasks. It found effects on perceptions of the robot. The paper distinguishes communicating a robot's internal intention from simply moving; its background connects eye/head leads to anticipation. Importantly, this is a video study of a physical-robot design, not evidence that any eye dart or longer anticipation improves a small UI avatar.

Application inference: prefer a brief look toward what will happen, then an action, then a context-appropriate response. An urgent response can omit deliberation. Validate readability with a viewer question such as “What is it attending to?” rather than only “Is it smooth?”

### Gaze needs a target and coordination

[Andrist, Pejsa, Mutlu, Gleicher, A Head-Eye Coordination Model for Animating Gaze Shifts of Virtual Characters, 2012, authors' university page](https://graphics.cs.wisc.edu/Papers/2012/APMG12a/).

Paraphrase: the computational gaze model incorporates environment, gaze-target and character parameters. A user study found effective communication of targets and subjective preference over comparison models. It is informed by human neurophysiology; it does not validate human timing constants for abstract pet eyes.

[The same authors, Designing Effective Gaze Mechanisms for Virtual Agents, CHI 2012, university page](https://graphics.cs.wisc.edu/Papers/2012/APMG12/).

Paraphrase: their study separates affiliative from referential gaze. Affiliative gaze increased reported connection; referential gaze improved learning. This supports distinguishing looking at the user from looking at relevant content, rather than treating every gaze shift as decoration.

Application inference: hold a stable attention anchor, occasionally inspect a nearby part of that anchor, and re-establish it. Eyes can lead a subtle head/lean follow for a deliberate look. Do not copy a humanoid eye/head model literally onto marks that have no head, eyeball, or visible pupil.

### An animator's concrete account of quiet eye shifts

[Keith Lango, Saccadic Eye Movement, 2005, original animator blog](https://keithlango.blogspot.com/2005/12/saccadic-eye-movement.html).

Paraphrase: subtle, well-placed eye shifts can convey thought in quiet moments; overdoing them looks fake. Their pace can suggest emotional energy or deliberation. Another motivation is examining another character's face. This is animator practice, not a quantitative blink/saccade-rate study.

Application inference: occasional refixation within one meaningful region should have a selected destination, a completed arrival, and a pause. Regenerating jitter each frame or returning repeatedly to precisely the same path is not required by this reference.

## Anki: usable primary API evidence versus archival design material

### Official source: coordinated animation tracks and semantic triggers

[Anki's original Vector Python SDK, animation.py](https://github.com/anki/vector-python-sdk/blob/master/anki_vector/animation.py).

Paraphrase of module/API documentation: animations coordinate movements, faces, lights and sounds across head, lift, treads, face, audio and backpack-light tracks. A trigger selects an animation class rather than one fixed clip. Selection can vary with mood/emotion or random weighting. This is direct evidence of Anki's public animation API; it does not expose or prove the full runtime behavior architecture.

Application inference: share a single phrase/intention across this engine's body, gaze, face and lids instead of separately timed random accents. Choose a variant at phrase onset, then execute coherently. Trigger variation and seamless interruption are different concerns; the SDK excerpt alone does not prove how Anki solved interruptions.

### Archived Cozmo notes: valuable with a provenance limit

[Community archive: Cozmo Character Design](https://randym32.github.io/Anki.Vector.Documentation/guides/Cozmo%20design%20guide.html), containing [Cozmo Notes & Sketches image](https://randym32.github.io/Anki.Vector.Documentation/guides/cozmo-animation-guide.jpg).

I inspected the actual image. It gives different treatment to sleepy waking, idle/bored looks, curious exploration, sneezes and catches. Idle looks allow eyes toward display edges before head follow; curiosity brings head/body along sooner. Catch notes explicitly allow quick actions without anticipation every time. The drawings contrast quick short nods with slower broad nods. This is a community-hosted artifact labelled as an Anki guide; the page does not independently establish authorship/date/version or whether all notes shipped. Treat it as archived production-reference material, not an authenticated current official specification.

[Community Vector character-design adaptation](https://randym32.github.io/Anki.Vector.Documentation/guides/Vector%20character%20design%20guide.html) explicitly says it adapts Cozmo's brief, and cites an animator interview for eye-rig references. Its central goal is personality through context. Its statements about Vector's implementation require their own provenance checks; do not cite this page as original Anki code. In particular, the separate community eye-tools page speculates about style guides; that speculation is not evidence.

## EMOPET and EMO are not interchangeable names

[LivingAI's manufacturer page](https://living.ai/emo/) calls its walking desktop robot **EMO**, and its navigation calls it **EMO PET**. It describes reactions to people, sound and touch, plus independent exploration; its expression/action count is manufacturer marketing, not an independent evaluation.

[EMOPET's about page](https://emopet.net/about) identifies EMO and AIBI as distinct companions and points users to LivingAI's instructions; its footer calls it a shopping guide. Therefore the likely robot reference in the user's wording is LivingAI EMO Pet. This does not prove that the user meant this exact model, and EMOPET is not evidence of a separate robot animation engine. No official EMO timing curves or internal animation-scheduler documentation were found. Do not claim EMO specifically uses eye-leading, a given blink rate, springs, or our proposed phrase architecture.

Application inference: borrow the relationship between a sensed event and an expressive response; do not borrow unsupported numerical parameters. Our avatar can respond to genuine app events, avoiding fabricated environmental sensing.

## Match to the actual engine

Read-only spot check of the current shared tree (concurrent implementation work may change these locations):

| Existing seam | Reference-informed implication | Proposal scope |
| --- | --- | --- |
| `AvatarMind.kt`: lanes Body/Gaze/Face/Lids/Spin, `Intention` cause/priority/expiry, `MindRules` gaze lead, dwell, budget | There is already an architecture for readable, prioritized intentions. Add any new quiet refixation through it, rather than bypassing it with renderer noise. | Within requested held-look tuning; avoid a second autonomous scheduler. |
| `HumanBehavior.kt`: `Fixation`, `IdlePlanner.nextAttention`, rest fixation and temperament | Refixation can remain related to the current interest. A focused thought and a new interest are different actions. | User-requested occasional aliveness; stable target and rare bounded adjustment. |
| `AlivenessEngine.kt`: `Beat` sequence and separate busy/idle choices | Existing semantic beats can form one coordinated phrase. Additional varieties should retain the same cause and cooldown, not merely multiply events. | Wider vocabulary is a proposal, not an automatic addition. |
| `LeanPhrase.kt`: `FocusedLook` has prompt attack, exact held goal, gentle release | The exact hold is an appropriate insertion point for a small purposeful reinspection, while keeping the primary goal readable. A head/eye variant may need different lag; no paper prescribes this code's constants. | User-requested motion/aliveness pass; inspect at real avatar size. |
| `GrokTransitionCoordinator.kt`: Happy-to-open change committed behind full blink | This is an engine-specific solution to geometric eye-shape compatibility. The sources support staging/readability; none proves a universal blink mask rule. | Apply only to demonstrated unpleasant morphs, including the user's asymmetric-eye-to-neutral report if needed. |

## Concrete experiments and risks

1. **Held-look comparison**: same stable off-center target, with (A) no secondary motion, (B) one occasional small reinspection related to that target, (C) the previous continuous micro-motion. Keep scenario, seed and real display size constant. Ask which feels attentive and what it is looking at. Risk: a rare beat can still resemble a nervous tic if eyes/head disagree or the beat always resets to center.
2. **Expression-pair comparison**: Happy→Neutral and asymmetric/half-closed→Neutral in every face pack. Compare faster direct morph against one blink-mediated change. Include interruption during closure/reopening. Risk: blanket blink masking can create excessive blinks or hide important reactions; only use where geometry/readability warrants it.
3. **Reactive lean comparison**: start a lean, redirect before it settles, then release. Judge first response and final quietness separately. Risk: slowing all motion can hide a seam while making intent unresponsive; adding multiple corrective settles can recreate the reported gear changes.
4. **Phrase collision review**: Busy→Streaming→Idle while looking aside, then an event. Inspect face/gaze/lids/body ownership and stale queued beats. Risk: multiple individually smooth systems can still produce incoherent combined acting. Technical continuity checks should accompany visual review, not substitute for it.
5. **Future proposal: contextual variants**: one clearly motivated look/acknowledgment phrase with restrained mood-dependent amplitude and timing. Risk: sentiment-only random smiles or negative looks can imply an emotion unsupported by context. Needs user review before expanding behavior vocabulary.
6. **Future proposal: art-direction profiles**: a short style card per avatar pack defining eye-shape-compatible transitions, acceptable asymmetry, focused versus drowsy gaze, and expressive size limits. Risk: a single humanoid animation rule can damage a graphic mark. Needs user review; no new visual identity should be imposed in a motion cleanup.

Numbers used in any implementation should be described as tuned project choices, not Pixar/Anki/EMO measurements. The strongest acceptance criterion is a coherent thought that remains readable at actual size over minutes, without repeated purposeless adjustments.
