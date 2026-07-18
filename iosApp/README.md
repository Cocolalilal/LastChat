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

To exercise Adaptive memory background maintenance in a debug simulator build,
pause the process in Xcode after enabling Adaptive memory and entering a chat,
then run this LLDB command:

```text
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"lastchat.rikkafork.cocolal.ios.memory.refresh"]
```

The scheduled-message recovery task can be simulated the same way after a
model schedules a follow-up:

```text
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"lastchat.rikkafork.cocolal.ios.scheduled-message.refresh"]
```

iOS treats `earliestBeginDate` as a lower bound rather than an exact alarm; the
existing foreground jobs remain active as fallbacks.

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
The compact home exposes Android's full top-level hierarchy. Display,
Assistant, Providers, and Data navigate to working iOS editors; unported routes
show an explicit unavailable detail. At Android's same 840 by 600 dp breakpoint
iOS switches to the source-shared 336 dp adaptive pane, including identical
grouping, expandable children, selected paint, corner morphing, row heights,
press scale, and animation timing. The working Assistant, Provider,
Appearance, and Data editors also use Android's source-shared production input
cards and form rows, including their shape, paint, padding, typography,
description alpha, and vertical rhythm. About is functional rather than an
unavailable placeholder and renders from the same Android production body and
launcher asset, with live iOS version, device, architecture, URL opening, and
UIKit haptics injected at the platform boundary.
Default model is functional too: its feature card and grouped selectable model
rows are the same production implementations consumed by Android. The iOS
picker searches the persisted provider configurations, activates the selected
provider/model for generation, and refreshes that provider's Keychain-key
availability.
The compact Providers/Search/TTS bottom selector is the same shared production
renderer used by Android. Search settings support Bing and 13 API-backed
portable providers, persist the provider/result limit without secrets, and put
API keys only in Keychain. Enabled search is offered to OpenAI, Google, and
Claude as the real `search_web` tool; model tool calls and tool results run
through the normal message loop with the same 256-step ceiling as Android.
SearXNG's custom URL/basic-auth editor is still pending.
The TTS destination is functional for OpenAI, Gemini, MiniMax, ElevenLabs,
Qwen, Fish Audio, Cartesia, and PlayHT. Provider configuration persists without
credentials, API keys remain in Keychain, and the production assistant-message
speech action is source-shared with Android. Both platforms run the same
chunking/prefetch/retry queue controller: Android keeps Media3 playback and iOS
implements the playback boundary with AVFoundation. Physical-device audio and
pixel-level action placement still require Xcode QA.

Assistant Tools now persists native Notifications and Text-to-speech toggles
per assistant using Android's source-shared production settings rows. Enabled
tools join the normal model-driven tool loop: `send_notification` requests iOS
authorization and posts through UserNotifications, including foreground banner
presentation, while `text_to_speech` uses the configured Keychain-backed TTS
provider and shared playback controller. iOS cannot inspect other apps'
notifications. Android's scheduled follow-up tool also performs a future model
call; iOS now matches that behavior with persisted scheduled work, foreground
timers, BGAppRefreshTask recovery, recent chat and memory context, model-based
notification generation, and bounded retry backoff. `get_notifications`
returns LastChat's persisted notification history because iOS does not permit
device-wide notification inspection. `ask_user` is now behaviorally available with its
per-assistant toggle, persisted questionnaire state, Android-matched card and
composer controls, option/custom/skipped answer normalization, generation
pause/resume, and relaunch recovery. The assistant image tool is now available for the
configured OpenAI-compatible or Google image model, uses the production shared
provider implementations and Android tool contract, stores decoded images and
metadata in LastChat's app-container gallery state, and turns returned
`markdown_image` links into tappable chat image attachments. The drawer's
Imagine action now opens the standalone generator/gallery route with Android's
generator/gallery switch, floating prompt surface, aspect/count sheet,
generation cancellation, persistent media grid, preview opening, and deletion.
The ComfyUI workflow editor remains to be ported.

Assistant memory is now functional on iOS for Off, Basic, Searchable, and Adaptive modes.
Core memories are persisted per assistant, and the mode cards, settings rows,
memory rows, badges, grouped corners, press animation, and delete confirmation
are the same source-shared production components used by Android. Basic mode
injects the memory prompt without modifying persisted user messages. Searchable
mode creates real OpenAI or Google embeddings, applies the same shared cosine,
keyword, threshold, and result-limit logic, and exposes create/edit/delete/search
memory tools to the normal model-driven tool loop. Embedding credentials reuse
the existing provider Keychain entries and are never written into preferences.
Adaptive uses the same source-shared evidence-bound extraction contract as
Android, persists a per-conversation message watermark, and triggers after the
same eight pending messages or ten minutes of inactivity. It applies structured
add/reinforce/supersede/close claim operations and upserts assistant-scoped
episodes. Android retains WorkManager durability. iOS registers a permitted
`BGAppRefreshTask`, submits the earliest pending Adaptive deadline, processes
all pending conversations under their owning assistants, and reschedules after
completion. The in-process inactivity job remains as a fallback when iOS
declines or delays the refresh request.

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
