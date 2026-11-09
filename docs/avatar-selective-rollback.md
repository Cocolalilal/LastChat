# Avatar selective rollback

Requested scope, 2026-11-08:

- Restore animation behavior from `d0ad749a`, immediately before the first avatar
  pass in this conversation (`e9e5b5b5`). Preserve unrelated app/release changes.
- Keep continuous rotational spin anticipation and follow-through, live gaze during
  spins, and continuous surface guards at spin entry/exit.
- Keep clip camera zoom excluded and remove spin crouch/stretch enlargement.
- Keep Happy expression release hidden by a shared full blink for both eye styles.
- Keep avatar-only local-model/classifier/dependency removal and removal of stored
  personality overrides. Preserve fixed internal style temperament/mood needed by
  the original engine. Chat local-LLM and speech features are unaffected.
- Restore original spontaneous behavior, gaze, side leans, busy movement, expression
  release speed and blink cadence. Do not use the rejected attention-engine rewrite.
- Additional requested appearance change: Grok Sad/Worried and Angry use full-height
  pills tilted 51 degrees in opposite directions; Pouty uses a 46-degree tilt.
  On 2026-11-09 the user clarified that the desired adjustment was toward
  horizontal. The earlier steps toward upright were reversed, with the same
  cumulative increase from the initial 42/38-degree candidate.
  Actual blinks retain full closure.

The user approved this selective rollback and the final Grok tilts for commit,
push and release on 2026-11-09. Author and committer: Cocolalilal. Earlier
review candidates were kept uncommitted pending that approval.

## Verification

App and unit-test source compilation succeeded (`compileDevDebugKotlin`,
`compileDevDebugUnitTestKotlin`, Java17 compiler). Further runtime/visual results
are recorded below after execution. Tests use a Java21 launcher for LiteRT-LM.

`graphify update .` was attempted; the executable is unavailable (exit127).

Review artifacts are in `/workspace/avatar-selective-rollback-demos` and are
candidates for visual review, not claims of subjective animation quality.


Runtime command (explicitly forwards the opt-in preview property to the test JVM):

```sh
source /workspace/.cloud-setup/activate.sh
./gradlew :app:testDevDebugUnitTest -I /tmp/avatar-rollback-runtime.gradle \
  --tests 'me.rerere.rikkahub.ui.components.avatar.animated.*' \
  --tests 'me.rerere.rikkahub.data.model.AvatarSerializationTest' \
  --tests 'me.rerere.rikkahub.widget.WidgetAvatarRendererTest' \
  -Dlastchat.rollbackPreviewDir=/workspace/avatar-selective-rollback-demos \
  --no-parallel --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx5g -Dfile.encoding=UTF-8'
```

Final result: BUILD SUCCESSFUL. 98 tests passed, 30 opt-in studies skipped, zero failures/errors (128 cases total). The Grok expression sheet and all three opt-in actual-runtime movies rendered successfully. ffprobe verifies 560x300,30fps, durations30/8/8seconds. Full logs, per-suite results, PNGs and traces are retained in the review artifact directory.

Final appearance verification: after the horizontal-tilt clarification, all three focused open-eye, blink-closure and native-render checks passed.
