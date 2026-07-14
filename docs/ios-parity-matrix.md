# iOS parity matrix

Android remains the acceptance reference. A row is complete only after both a
functional check and a screenshot comparison on compact and wide layouts.

| Surface | Shared/iOS foundation | Production data | Visual QA | Status |
|---|---:|---:|---:|---|
| App theme, shape, and font tokens | Shared shapes, typography, AMOLED transform, same Google Sans Flex TTF, and all six Android palettes | Persisted theme and system/light/dark mode | Pending Xcode | In progress |
| Chat shell and composer | Shared production grouped-bubble container | Persistent iOS conversations, streaming, and user cancellation | Pending Xcode | In progress |
| Conversation menu | Shared Android press physics, pill geometry, selected paint, and row spacing | App-container persistence plus select/create/rename/delete | Pending Xcode | In progress |
| Statistics | Shared production stat-card implementation | Persisted conversation, message, input, output, and cached-token totals | Pending Xcode; heatmap pending | In progress |
| Settings shell | Yes | Provider, appearance, multiple assistants, and system prompt settings | Pending Xcode | In progress |
| Provider configuration | Models and four cloud providers compile | Three chat providers, retained per-provider endpoint/model settings, and per-provider Keychain secrets | No | In progress |
| Streaming generation | Real providers plus Darwin HTTP/SSE | OpenAI-compatible, Google, and Claude chat wired; cancellation and 1-second persistence checkpoints | No | In progress |
| Attachments and media | Native media picker, sandbox copy, provider media encoder, and Coil image rendering | Pending attachment drafts and sent media parts persist; PDF/DOCX prompt parsing and playback pending | Pending Xcode | In progress |
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
