# Long-message and paste recovery

## Provenance

The integration starts at `69962d13db07f696625098db5f791090ebcd7ba1` on
`LastChat_dev`, which discards the pending memory redesign. Neither that tree nor
`e7b0d2765c9d27b39894713ccf5201846e4a82a2` contained the earlier long-text feature.

The recovery inspection found a clean workspace at `b8a2cb52`, no stash, no other
worktree, no unreachable Git objects, and no commit in the available history for
`LongTextPasteHandler.kt`, `ExpandableMessageContent.kt`, or `LongText.kt`.
The reflog contained repeated workspace setup resets. The earlier claim that the
feature was already included on the development branch was incorrect.

The helper, chooser, expansion UI and original regression tests were reconstructed
from the original implementation recorded in this chat's tool calls. A copy of
that reconstruction was preserved before adapting it to the current renderer.
This is a recovery from the recorded source, not a cherry-pick from a recovered
commit.

## Behavior and integration review

- Messages initially show at most 1,200 characters or 12 line breaks. The down
  arrow reveals up to another 4,000 characters or 60 line breaks per tap; after
  reaching the end, the up arrow minimizes the message. Expansion state survives
  scrolling/recreation. UTF-16 surrogate pairs are never split.
- Truncation happens before Markdown parsing and layout. The newer assistant
  bubble-stack renderer uses the same visible prefix for bubble counting and
  rendering, preserving attachment order and grouped bubble positions. Active
  streaming remains fully visible. Stored messages, model input and copy actions
  retain the complete text.
- Pasting at least 8,000 characters or 120 lines opens a Text/File chooser before
  the editor lays out the pasted content. Both compact and fullscreen editors use
  the same interception. A cancelled chooser leaves the draft and selection intact.
- File mode imports the entire text as a managed UTF-8 `text/plain` attachment,
  removes the staging file, and leaves the existing draft intact. Import errors
  retain the pending paste for retry or insertion as text. Rapid pastes are queued.
  An import finishing after a send/clear cannot attach to the next draft, and its
  busy state cannot block a paste in the fresh draft.
- Remembered choices use `SettingsStore.update`. **Settings → Display → Long
  pasted text** offers **Ask every time**, **Paste as text**, and **Attach as file**.
  Older settings default to asking; backup/settings serialization retains the choice.
- The compact editor no longer splits the complete draft into lines to decide
  whether to show its expand control, and only one editor measures the draft while
  fullscreen is open. Closing fullscreen restores composer focus.
- The Markdown AST cache retains at most 64 entries and a combined 512,000 source
  and preprocessed characters, excluding source entries larger than 64,000
  characters. The current branch's parser isolation and short cache locking are
  preserved.

The existing memory system and context-management controls are preserved. This
change adds no memory engine, migration, schema, worker, model restriction, or
memory compatibility layer.

## Validation

Use a full JDK 21 to run host tests, with JDK 17 available to the library toolchains.
Android source and bytecode targets remain 17.

```sh
mkdir -p build/long-text-renders
./gradlew -Dlastchat.renderDir="$PWD/build/long-text-renders" \
  :app:testStableDebugUnitTest \
  --tests '*LongText*' --tests '*ExpandableMessage*' --tests '*Markdown*' \
  --tests '*Memory*' --tests '*Context*' --tests '*ChatInput*' --tests '*ChatPageTest' \
  :app:compileStableDebugAndroidTestKotlin --max-workers=2
./gradlew :app:assembleDevDebug -Plastchat.abi.splits=false --max-workers=2
```

The targeted run passed **126 tests, with zero failures and zero skips**. This
includes 20 long-text tests, existing memory/context regressions and host Compose
interaction tests. The complete Android instrumentation test sources also compile.
The dev debug APK build (`assembleDevDebug`, with ABI splits disabled) succeeds.
The screenshot directory is needed because existing context-menu tests capture
renders; leaving the property empty makes them attempt to write at the filesystem root.

The restored device tests cover chunked expansion/minimization, a million-character
preview and selected-text paste handling. Host regressions additionally exercise
actual Compose edit buffers, all three persisted preferences, Unicode boundaries,
rapid-paste capture, UTF-8 file fidelity/cleanup, and the shared bubble presentation.
Existing memory and context regressions are included in the targeted run.

A connected Android device or emulator is still required to verify real scrolling
performance, IME/clipboard integration, chooser behavior in fullscreen, remembered
file imports, orientation changes and accessibility. Host tests and compilation do
not establish device performance; no connected device/emulator was available in
this workspace.
