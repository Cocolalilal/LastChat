# LastChat iOS

The iOS client uses Compose Multiplatform so visual tokens and, progressively,
whole screens can be shared without changing the Android app.

## Requirements

- macOS with Xcode 16 or newer
- JDK 17 or newer
- An Apple development team selected in Xcode for device builds

## Run

1. Open `iosApp/xcode/LastChatIOS.xcodeproj` in Xcode.
2. Select the `LastChatIOS` scheme and an iPhone simulator.
3. Press Run. The Xcode build phase invokes
   `:iosApp:embedAndSignAppleFrameworkForXcode` automatically.

The Kotlin side can be checked without Xcode:

```powershell
./gradlew :iosApp:compileKotlinIosSimulatorArm64
```

The framework already includes the shared core, common utilities, the real
OpenAI/Google/Claude/ComfyUI provider layer, every search service, and the
portable TTS slice. Native iOS adapters provide Darwin HTTP/SSE, sandboxed file
storage, media encoding, Keychain secrets, RS256 signing, and UIKit haptics.

The current iOS surface has persistent conversations, real OpenAI-compatible,
Google, and Claude streaming, app-container storage, per-provider
Keychain-backed API keys, persisted assistant/system-prompt settings, and all
six Android theme palettes with system/light/dark modes. Conversation rows use
the production Android interaction surface and persist create, select, rename,
and delete operations. Statistics use the same production stat cards and
activity heatmap as Android, populated from persisted iOS conversations,
messages, token usage, and message creation dates. Child screens also use the
same auto-mirrored rounded back control, press animation, and Pop haptic
semantic as Android. The chat toolbar uses Android's same outlined 48 dp drawer
button and rounded Menu glyph. It opens a real modal drawer using the same
Android sheet shape and container color instead of navigating to an iOS-only
full-screen menu. Drawer search uses Android's same expandable search capsule,
focus behavior, close control, hint layout, and empty-result treatment while
filtering persisted iOS conversation titles. The settings footer uses the same
Android circular action Surface, press spring/alpha, rounded Settings vector,
42 dp assistant pill, 30 dp avatar, spacing, typography, and UIKit Pop haptic.
When multiple assistants exist, that pill opens Android's same shared picker
sheet with grouped corner animation, selected paint, transition spinner,
drag/click dismissal, and real persisted assistant-to-conversation handoff.
Statistics also uses Android's grouped drawer quick-action renderer and Rounded
BarChart vector directly beneath search, including the same Tick haptic and
spring hide/show behavior. Imagine is intentionally not displayed until its
real iOS generation route is available.
Multiple assistant profiles retain their own system
prompts and conversations, provider endpoint/model settings are retained per
provider, and an active stream can be cancelled from the composer. It is not yet a
complete port of every LastChat screen or Android repository. Android remains
the source of truth while assistants, attachments, memory, tools, local models,
and the remaining screens move behind common contracts. See
`docs/ios-parity-matrix.md` for the exact status; do not treat the current
screens as screenshot-parity complete until they pass Xcode visual QA.

Settings now opens on the same compact grouped-card renderer used by Android:
matching section typography/insets, 24 dp group clipping, 10 dp rows, paint
tokens, 20 by 18 dp home-row padding, rounded icons, and spring press behavior.
The available Display, Assistant, Providers, and Data rows navigate to their
working iOS editors. Android's wide adaptive pane and the remaining settings
destinations are not yet ported.

The composer can pick images, video, audio, and general documents through the native iOS
document picker. Selections are copied into LastChat's Application Support
directory before the picker callback completes, pending drafts persist across
launches, and sent image parts render from the sandboxed copy. Its visible shell
now comes from Android's production composer: the same 48 dp add control, 24 dp
outlined capsule, text padding/five-line limit, in-capsule attachments, and
36 dp Picker/Send/Loading action with matching depth transitions and vectors.
That action is the same nine-state renderer used by Android's normal and
full-screen composers; iOS currently reaches its Picker, Send, and Loading
states while STT/questionnaire/tool controllers remain to be ported. Pending
files now use the production Android attachment renderer too: the same 84 dp
lazy strip and edge fades, 60 dp image previews and media/document tiles,
pressed image scale, elevations, spacing, and removal affordances.
Sent attachments also use Android's production full-width row above the text
bubble, with 72 dp cropped image/file tiles, eight-dp spacing, directional
alignment, and 32 dp animated scroll-edge fades. Tapping a sent tile now uses a
platform attachment-opening contract backed by UIKit's native preview/Open In
flow; inline Android-equivalent media controls remain separate parity work.
Attachment-only sends are supported. Document prompt
parsing and inline audio/video playback remain separate parity items.
