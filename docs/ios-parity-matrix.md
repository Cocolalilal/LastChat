# iOS parity matrix

Android remains the acceptance reference. A row is complete only after both a
functional check and a screenshot comparison on compact and wide layouts.

| Surface | Shared/iOS foundation | Production data | Visual QA | Status |
|---|---:|---:|---:|---|
| App theme, shape, and font tokens | Shared shapes, typography, AMOLED transform, same Google Sans Flex TTF, and all six Android palettes | Persisted theme and system/light/dark mode | Pending Xcode | In progress |
| Chat shell and composer | Shared production grouped-bubble container, 48 dp add Surface, baseline row, 24 dp input capsule, outline tokens, five-line text geometry, nine-state Picker/Send/Loading/STT/questionnaire/tool action renderer, exact composer attachment strip, and sent-message attachment row/tiles used by Android and iOS | Persistent iOS conversations, attachment-only/text sends, native file selection, streaming, cancellation, and empty-state provider-settings handoff | Pending Xcode; model/STT/tool controllers remain platform gaps | In progress |
| Conversation menu | Shared Android modal drawer sheet, expandable search control, grouped quick-action rows and search animation, circular action Surface/press physics, BarChart/Settings glyphs, footer pill geometry, assistant picker sheet, selected paint, grouped corner animation, and row spacing | App-container persistence, title filtering, select/create/rename/delete, drawer lifecycle, statistics/settings navigation, and assistant-to-conversation handoff | Pending Xcode; Imagine remains until iOS image generation is functional | In progress |
| Top-app-bar navigation | Shared Android back control plus the outlined 48 dp chat drawer control and rounded Menu glyph | Native route callbacks and Pop haptic semantics | Pending Xcode | In progress |
| Statistics | Shared production stat-card and activity-heatmap implementations | Persisted conversation, message, input, output, cached-token, and per-day activity totals | Pending Xcode | In progress |
| Settings shell | Yes | Provider, appearance, multiple assistants, and system prompt settings | Pending Xcode | In progress |
| Provider configuration | Models and four cloud providers compile | Three chat providers, retained per-provider endpoint/model settings, and per-provider Keychain secrets | No | In progress |
| Streaming generation | Real providers plus Darwin HTTP/SSE | OpenAI-compatible, Google, and Claude chat wired; cancellation and 1-second persistence checkpoints | No | In progress |
| Attachments and media | Native media picker, sandbox copy, provider media encoder, Coil image rendering, shared production 84 dp composer strip, and shared full-width sent-message row with 72 dp tiles, directional alignment, edge fades, spacing, elevations, and remove controls | Pending drafts and sent media persist; Android crop/URI cleanup, archived grayscale, zoom, and opening remain adapter-owned; iOS document opening and media playback pending | Pending Xcode | In progress |
| Memory and RAG | Core utilities are shared | No | No | Pending |
| Search providers | All services compile for iOS | No | No | In progress |
| Cloud TTS | All eight cloud providers compile for iOS | No | No | In progress |
| Local models/workspace | Android implementation | No | No | Pending |
| Backup and restore | Android implementation | No | No | Pending |

## Acceptance rules

- Do not change Android spacing, typography, colors, shapes, motion, or haptics
  merely to accommodate iOS.
- Use Compose Multiplatform for shared UI. SwiftUI is only the native host.
- Keep platform-only capabilities behind contracts and show an explicit
  unavailable state until the iOS adapter exists.
- Never store provider secrets in preferences; the iOS implementation must use
  Keychain before provider configuration is enabled.
- Validate the Android app after every common UI extraction.
