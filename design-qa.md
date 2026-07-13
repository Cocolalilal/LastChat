# Graph Memory UI Design QA

## Reference material

- `C:\Users\julian\Downloads\lastchat character memory subpage sketch.png`
- `C:\Users\julian\Downloads\Character memories page when there's no active work but available suggestions.png`
- `C:\Users\julian\Downloads\Browse all memories screen when clicking on a memory.png`

## Implementation reviewed

- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/GraphMemoryOverview.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemorySubPage.kt`

## Static design review

- Uses LastChat's existing Material 3 Expressive theme, `AppShapes`, rounded Material icons, premium haptics, dialogs, and bottom-sheet patterns.
- Preserves the sketch hierarchy: segmented statistics, transfer/rebuild status, a strongly framed graph viewport, browse action, activity section, and memory detail sheet.
- Uses different outer and inner radii for grouped cards, including asymmetric first/middle/last statistics shapes.
- Memory details use status chips, a focused related-node graph, source provenance, and native edit/forget actions.
- Graph Memory is shown through the existing assistant-memory settings route and adaptive settings scaffold rather than a separate visual mode.

## Runtime verification

- Debug APK assembled successfully for `x86_64`, `arm64-v8a`, `armeabi-v7a`, and universal targets.
- The `x86_64` APK installed successfully on AVD `medium_phone`.
- Runtime screenshot comparison is blocked: the AVD's Android System UI enters an ANR loop immediately after boot, before LastChat can draw a window. A normal cold boot and System UI restart produced the same operating-system dialog.

## Final result

**Blocked for screenshot-level visual sign-off by the emulator environment.** Compilation, packaging, installation, and static design-system review passed. The reference-versus-runtime screenshot comparison must be repeated on a healthy emulator or physical device before calling the visual match fully passed.
