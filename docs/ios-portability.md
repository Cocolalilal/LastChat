# LastChat iOS Portability Plan

LastChat is still an Android app today. This document defines the path for making it easy to compile for iOS without changing the current Android look, motion, haptics, or behavior.

## Non-negotiables

- Android UI parity comes first. Existing Compose screens, `AppShapes`, Material You 3 Expressive usage, and PremiumHaptics behavior must remain visually identical on Android.
- Portability work should be incremental. Move pure logic behind shared APIs first, then add iOS implementations behind the same contracts.
- Android-specific features stay Android-specific until an iOS equivalent exists. Widgets, WorkManager jobs, Android share intents, Android TTS, Chaquopy, Room, and platform file pickers must be isolated behind adapters rather than deleted or weakened.

## Recommended Architecture

Use Kotlin Multiplatform as the shared core path:

- `:shared`: the iOS-targetable Kotlin Multiplatform module. Keep platform contracts and newly extracted pure logic here first.
- `commonMain`: pure models, provider request/response shaping, prompt logic, JSON parsing, token/context utilities, cache policies, search DTOs, and cross-platform repository interfaces.
- `androidMain`: current Android implementations for Room, DataStore, WorkManager, Android file/content APIs, Android TTS, Media3 playback, Firebase, app widgets, and current UI shell.
- `iosMain`: iOS implementations for persistence, background scheduling, keychain/API-key storage, haptics, file access, audio playback, and platform share/import flows.

Do not move UI wholesale until the data and service boundaries are stable. Compose Multiplatform can preserve the current Compose mental model, but Android visual identity should be guarded with screenshots before shared UI migration begins.

## First Shared Candidates

These packages are the safest places to extract first:

- `me.rerere.ai.core`
- `me.rerere.ai.registry`
- `me.rerere.ai.provider`
- `me.rerere.ai.ui`
- `me.rerere.ai.util`
- `me.rerere.common.cache`
- `me.rerere.common.calendar`
- `me.rerere.common.html`
- `me.rerere.common.http`
- `me.rerere.highlight`
- `me.rerere.search`
- `me.rerere.tts.model`
- `me.rerere.tts.provider`
- `me.rerere.tts.provider.providers`

`me.rerere.ai.core`, provider-neutral `me.rerere.ai.provider` DTOs/enums, and the pure model matching/display pieces of `me.rerere.ai.registry` now live in `:shared`. Keep new pure AI model, registry, schema, modality, ability, custom header/body, and provider option logic there. `ProviderSetting` is now a UI-free serializable model again; the old transient Compose description lambdas have been removed, and Android-only provider configuration screens render their own text and controls without changing the visible UI. `ComfyUIProvider`, the Vertex `ServiceAccountTokenProvider`, OpenAI provider-level non-streaming calls (model listing, balance, image generation, embeddings), OpenAI text generation (`ChatCompletionsAPI.generateText`, `ChatCompletionsAPI.streamText`, `ResponseAPI.generateText`, `ResponseAPI.streamText`), Claude provider networking, and Google provider networking now use `PlatformHttpClient`. Legacy AI OkHttp proxy/SSE/request helpers have been removed after provider migration. Provider diagnostics now use the shared `PlatformLog` facade instead of Android `Log`. Provider media encoding now uses `PlatformMediaEncoder`, with Android Bitmap/File/base64 behavior isolated in `AndroidPlatformMediaEncoder`. `ProviderManager` now accepts platform adapters instead of `OkHttpClient`, with Android DI constructing those adapters. SiliconFlow free-key fallback decisions now live in shared `SiliconFlowFreeApiPolicy`; Android's `AIRequestInterceptor` remains the OkHttp/Firebase adapter that reads the request body and Remote Config values. Memory recall age labels, memory-search time-range parsing, time-awareness prompt gap/timeline generation, and message-template context keys now live in shared `me.rerere.ai.util` using Kotlin/common APIs, with Android retaining thin wrappers for existing call sites, timezone display names, locale-specific date/time formatting, and the Pebble engine adapter. Android-only local runtime file/media encoding now lives in the Android `:local-llm` module, outside the shared-candidate AI surface. Calendar heatmap grid/month/boundary math now lives in shared `CalendarHeatmapLayout` under `me.rerere.common.calendar`; Android keeps a same-shaped facade using `LocalDate`/`YearMonth`, so the existing menu heatmap UI contract and rendering stay unchanged while iOS can reuse the same layout engine. The current shared-candidate report has no `:ai` hotspots.

`:search` now depends directly on `:shared` for `InputSchema`, `SearchServiceOptions`, common search options, and search/scrape result DTOs. Continue keeping search settings/result shapes in `:shared`; request builders and parsers are now clear of Android/JVM networking imports in the portability report.

All concrete `:search` providers now call through `PlatformHttpClient`: Bing is HTML-only, and Bocha, Brave, Exa, Firecrawl, Grok, Jina, LinkUp, Metaso, NanoGPT, Ollama, Perplexity, SearXNG, Tavily, and Zhipu no longer import OkHttp or Android logging directly. The obsolete search-local `Call.await()` helper has been removed. Android DI installs a named 30-second-read-timeout OkHttp-backed `PlatformHttpClient` for search startup, so `:search` no longer constructs OkHttp and `LastChatApp` only sees the platform HTTP contract. Bing search now uses a platform-HTTP client plus a small Kotlin result parser instead of the Android app importing Jsoup for search fetching/parsing. Provider description rendering no longer lives in `SearchService`; Android keeps the same `Text`, `TextButton`, resource strings, and API-key links in `SettingSearchPage`, while the search providers stay focused on request/parse logic. The current shared-candidate report has no `:search` hotspots.

Shared-candidate networking in `:common` has been cleared from the portability report. The legacy OkHttp `Call.await()` and SSE helpers now live under `me.rerere.common.platform.android`, beside `OkHttpPlatformHttpClient`, so Android-only callers keep the same behavior. `Base64JsonKeyCodec` now uses Kotlin UTF-8/Base64 APIs instead of JVM charset/Base64 helpers. The file-backed cache stores (`FileIO`, `PerKeyFileCacheStore`, and `SingleFileCacheStore`) now live under `me.rerere.common.platform.android.cache`; the portable cache surface in `me.rerere.common.cache` is limited to cache entries, cache store contracts, key codecs, and the in-memory LRU wrapper.

The portability report now flags `java.math`, `java.security`, `java.time`, `java.util.concurrent`, `java.util.Locale`, `java.util.Base64`, `java.net.URI`, and `java.net.URLEncoder` imports as JVM-only runtime findings, flags JVM-only helper imports such as `org.apache.commons`, flags Android resource UI imports such as `androidx.compose.ui.res`, and also scans source lines for JVM reflection/runtime markers such as `::class.java` and `javaClass`. `common.cache.LruCache` uses a Kotlin atomic copy-on-write state instead of JVM `ReentrantLock`/`LinkedHashMap`, and the Vertex service-account token cache uses a Kotlin atomic map instead of `ConcurrentHashMap`, keeping shared candidates free of JVM concurrency APIs. Vertex service-account timestamps now use Kotlin `Clock`, while the Java `Instant` serializer used by Android conversation models lives in the app boundary. Vertex JWT assembly still lives in shared provider logic, but RS256 signing now goes through `PlatformJwtSigner`; Android keeps the existing PKCS#8 `KeyFactory`/`Signature` implementation in `AndroidPlatformJwtSigner`, and iOS can provide a Security/CryptoKit-backed signer later. ComfyUI image generation, SearXNG basic auth, Vertex JWT encoding, web access-token URL-safe encoding, assistant/lorebook bundled asset encoding, Mermaid image export decoding, assistant PNG metadata import decoding, image generation upload encoding, and Android media adapter encoding now use Kotlin Base64 APIs so shared and portable-boundary code does not depend on JVM or Android Base64. Shared search/provider query and form encoding now uses `me.rerere.common.http.urlEncode`, a common UTF-8 percent encoder, instead of JVM `URLEncoder`; the same common URL encoder/decoder now backs app utility and web media URL helpers, preserving existing `+` space behavior for query parameters while removing direct JVM `URLEncoder`/`URLDecoder` usage from that path. App HTML escape/unescape wrappers now delegate to `me.rerere.common.text` helpers instead of Apache Commons, clearing the report's helper-library hotspot. Provider host checks for Referer/Origin compatibility now use `me.rerere.common.http.urlHostOrNull` instead of JVM `URI`, and app favicon/catalog/provider-settings URL host/path extraction now uses `me.rerere.common.http.urlPartsOrNull` and `replaceUrlEncodedPathOrNull` instead of OkHttp `HttpUrl` parsing. Vertex private-key JSON escape handling now uses `me.rerere.common.http.unescapeJsonStringContent` instead of Apache Commons. Portable highlight token parsing no longer reports unknown JSON content through JVM class names, and portable TTS response models compare types with Kotlin `is` checks instead of `javaClass`. App diagnostic labels for tool execution, MCP status, chat tool failures, provider override chips, markdown debug output, and SharedPreferences export errors now use Kotlin class metadata instead of JVM `javaClass`/`::class.java`, reducing reflection/runtime findings while keeping Android-visible labels equivalent. `AcceptLanguageBuilder` now builds headers from plain BCP-47 language tags, while Android locale extraction lives in the app boundary and is installed into `SearchService` for Bing, preserving Android request behavior without importing `Locale` from shared candidates.

`:highlight` now separates the portable highlighter contract, token model, serializer, and Compose rendering from the Android QuickJS runtime. Android DI binds `Highlighter` to `AndroidHighlighter`, which owns `Context`, Prism raw-resource loading, QuickJS initialization, and serialized QuickJS access through a coroutine dispatcher instead of a JVM executor. Android highlighter diagnostics now use Kotlin class labels instead of JVM `Class` names. The portability report treats `me.rerere.highlight` as a shared candidate and excludes only `me.rerere.highlight.android`.

Cloud TTS transport and orchestration are platform-ready. OpenAI, Gemini, MiniMax, ElevenLabs, Qwen, Fish Audio, Cartesia, and PlayHT call through `PlatformHttpClient`; `CloudTtsManager` dispatches those providers on iOS, while Android's `TTSManager` preserves its provider-specific OkHttp timeouts and system-TTS branch. Wire-shape regression tests cover provider-specific request fields. Gemini and Qwen decoding use Kotlin Base64, and diagnostics use `PlatformLog`. `TtsController`, `TtsSynthesizer`, chunking, retry/prefetch queues, cache state, and unified playback state are common code behind `TtsSpeechGenerator` and `TtsAudioPlayer`. Android implements playback with Media3; iOS implements it with AVFoundation, including PCM-to-WAV conversion, play/pause/resume/stop, seeking, speed, duration, and position updates. Android system synthesis remains intentionally Android-only. The portability report treats common TTS models, controllers, provider contracts, and cloud providers as shared candidates and excludes only platform adapters.

Memory portability now has a working Adaptive vertical slice. `MemoryVectorMath` and
`PortableMemoryChunker` live in `:shared`, and Android's existing vector,
keyword-score, and chunking entry points delegate to them. `:ui-core` owns the
production memory mode card, settings row, and editable memory row used by both
platforms. The iOS controller persists core memories per assistant, injects the
Basic memory prompt without polluting stored messages, creates OpenAI or Google
embeddings from existing Keychain credentials, performs hybrid Searchable
recall with the same scoring/threshold/limit semantics, and exposes model-driven
create/edit/delete/search tools. The structured temporal extraction models and
evidence-bound prompt are source-shared with Android. iOS persists a
conversation-scoped evidence watermark, uses Android's same eight-message or
ten-minute trigger, applies add/reinforce/supersede/close claim operations, and
upserts read-only-managed episodes under the conversation's owning assistant.
Android retains WorkManager and its Room/FTS temporal repository. iOS registers
a permitted `BGAppRefreshTask`, submits the earliest pending Adaptive deadline,
processes all pending conversations under their owning assistants, and
reschedules after completion or state changes. The in-process inactivity job
remains as a fallback when iOS declines or delays a background request.

As of the latest report, `:ai`, `:common`, `:search`, and the cloud-provider
portion of `:tts` have no shared-candidate hotspots. Android-boundary findings remain
concentrated in app entry points, DI, UI, widgets, local runtimes, and adapter
shells. Broader Android/JVM findings in app, highlight, document, and the
platform side of TTS still need abstractions before the whole Android product
implementation itself can become one common target.

The `:shared`, `:common`, `:ai`, `:search`, and portable `:tts` source sets now
compile for `iosArm64`, `iosSimulatorArm64`, and `iosX64`. `:ai` contains the
real OpenAI, Google, Claude, and ComfyUI provider implementations, not only
DTOs. `:search` contains all current search services. The iOS UI framework
exports these modules so provider and search functionality can be wired into
the production iOS repositories without duplicating request logic.

The iOS client now has a production-facing controller rather than preview-only
message state. It persists conversations and non-secret provider preferences in
the iOS Application Support container, stores separate provider keys through
the Keychain adapter, and drives real OpenAI-compatible, Google, and Claude
streaming through `ProviderManager` and the Darwin SSE client. This is an iOS repository boundary, not a claim that
Android Room/DataStore or every Android screen has already been ported.

Visual parity is now source-enforced for the first production UI primitives.
The `:ui-core` Compose Multiplatform module owns the exact Android shape tokens,
all six production light/dark color schemes, the AMOLED surface transform,
typography metrics, grouped-message bubble
container, and animated typing indicator. Android imports those definitions
after its former local shape, bubble, and indicator files were removed, while iOS imports the same definitions. Android
continues to supply its Google Sans Flex font family. The shared module's build
generates a Compose font resource from that same canonical TTF without checking
in a duplicate 4 MB asset, so iOS now uses the same font binary and metric table.
The four large generated Android palette files are parsed into generated shared
Kotlin source at build time, while Seafoam Mint and Sakura are directly shared;
this keeps Android's existing palette files authoritative without copying
thousands of color literals into a second maintained source.
Variable-axis rendering still needs screenshot comparison in Xcode before font
parity can be marked verified.

The production conversation-row interaction surface is also shared. Android
and iOS now execute the same pill clipping, selected-state color interpolation,
spring press scaling, press alpha, and indication code. Android retains its
existing localized content and dropdown actions; iOS wires select, create,
rename, and delete actions to its app-container repository. The shared iOS
haptic contract exposes the same twelve semantic patterns as Android's
`PremiumHaptics`, mapped to UIKit selection, impact, and notification feedback.
Top-app-bar back navigation is source-shared as well: Android and iOS use the
same 48 dp Material control, canonical auto-mirrored rounded-arrow vector,
0.85 pressed scale, and damping 0.6/stiffness 300 spring. Platform wrappers
retain Android resource localization/navigation and iOS route/UIKit-haptic
behavior.
The production composer has begun moving through the same source-enforced
boundary. `:ui-core` now owns the exact bottom-aligned eight-dp row gap, 48 dp
circular add/record Surface, one-dp outline at 60 percent alpha, 24 dp input
capsule, 48 dp minimum height, and the embedded 36 dp Picker/Send/Loading action
with Android's 250/150 ms vertical depth transition and rounded Add, ArrowUpward,
and Stop vectors. Android's real `MinimalChatInput` executes the shared row,
add Surface, and capsule while retaining blur, STT long press, attachments,
editing, model/tool pickers, questionnaires, approvals, suggestions, and
PremiumHaptics. iOS now uses the same shell with the modern `TextFieldState`
API, identical 16/12/52/12 dp text padding and five-line limit, in-capsule
attachments, native file selection, Send/Cancel haptics, attachment-only sends,
and provider-settings handoff from the empty Picker state. The Android action's
full state machine is now source-shared too: one enum and renderer owns Picker,
Send, Loading, STT, STT recording/finalization, questionnaire next/submit, and
tool-denial container colors, 150/250 ms fade/scale/slide depth ordering, and
questionnaire-specific horizontal direction. Both Android's embedded 36 dp
action and full-screen 56 dp action use it, while Android retains exact Material
icons, `ModelSelector`, STT calls, questionnaire/tool routing, and haptics in
slots. iOS uses the reachable Picker/Send/Loading subset. Exact media attachment
tiles are source-shared as well: the same 84 dp lazy strip, 12 dp inset padding,
10 dp gaps, animated 22 dp scroll-edge masks, 60 dp image/video/audio/document
tiles, pressed image scale, elevations, and both production removal treatments
now render on Android and iOS. Platform adapters retain Android crop and
duplicate-URI cleanup behavior and iOS sandbox-file state and haptics.
Sent-message attachments now cross the same boundary: `:ui-core` owns Android's
full-width direction-aware lazy row, eight-dp gaps, 32 dp animated edge masks,
and 72 dp document tile. Android retains zoomable image previews, archived
grayscale state, native open intents, and attachment metadata collection in its
adapter; iOS uses Coil thumbnails and the shared file tiles outside the text
bubble instead of placeholder labels inside it. `PlatformAttachmentOpener`
keeps tile behavior portable; the iOS implementation retains a UIKit
`UIDocumentInteractionController` and presents the native preview/Open In flow
from the Compose host. The iOS picker now requests general document data in
addition to image, movie, and audio UTTypes, matching its existing PDF, DOCX,
text, JSON, and binary MIME handling.
Compact Settings now begins from the same source-enforced production layout on
both platforms. `:ui-core` owns Android's section labels, 16 dp section insets,
24 dp group clipping, four-dp row gaps, 10 dp row shape, dark/light container
selection, 16 dp internal spacing, and 0.98 damping-0.5/stiffness-400 press
spring. Android's existing `SettingsGroup` and `SettingGroupItem` are adapters
over those primitives, retaining resources, navigation, and PremiumHaptics.
iOS now opens a grouped Settings home with functional Display, Assistant,
Providers, and Data destinations instead of placing every editor in one long
screen. The compact home now mirrors Android's complete top-level hierarchy;
unported routes open an explicit unavailable detail instead of silently doing
nothing. `LastChatSettingsNavigationPane` also source-shares Android's 336 dp
wide pane, 32 dp shell, status-bar inset, 840 by 600 dp breakpoint, grouped
corner morphing, selected paints, child expansion, 48/58 dp row heights,
80/90/120/140 ms motion, and 0.96 press scale. Android retains resource lookup,
navigation, PremiumHaptics, and scroll restoration. iOS uses the same full
parent/child hierarchy and keeps unavailable destinations visibly selected.
The production settings input-card and form-row implementations are shared as
well: Android's `SettingGroupInputItem` and `FormItem` now delegate to
`:ui-core`, and the working iOS Assistant, Provider, Appearance, and Data
details consume those same container colors, 10 dp shape, 16 dp padding,
12 dp field rhythm, label typography, description alpha, and eight-dp row gap.
Platform code continues to own state, secure persistence, navigation, and
controls whose behavior differs; the surrounding detail geometry no longer
has an iOS-only approximation.
The About page body is also a single shared renderer. Both platforms use the
same Android launcher foreground asset, 240 dp logo footprint, version pill,
section spacing, grouped 10 dp rows, icon paints, 0.98 press spring, and
credits. Android injects its existing system information, source intent, and
hidden update-check action; iOS injects live UIKit system/device data, native
CPU architecture, external URL opening, and UIKit haptics.
Default-model settings now share Android's production `ModelFeatureCard` and
grouped model-row implementation as well. This includes the 10 dp feature card,
16 dp content rhythm, grouped 24/10 dp corners, animated 50 dp selected pill,
dark-mode black row paint, typography, and click/long-click hit geometry.
Android's existing `SettingModelPage` and `ModelList` delegate to these common
primitives. iOS exposes a functional searchable Default model destination and
persists the chosen per-provider model as the active generation configuration,
including recalculating the selected provider's Keychain credential state.
The chat toolbar's drawer button is source-shared too, including its 48 dp pill
surface, one-dp outline at 60 percent alpha, and canonical rounded Menu glyph.
Android continues to attach its existing blur effect and blurred container
color to that shared surface; iOS uses the same geometry and paint tokens for
its menu navigation action. The menu presentation itself now uses
`ModalNavigationDrawer` on iOS instead of navigating to an iOS-only full-screen
page. Both platforms render through the same modal sheet implementation with
the Android production 32 dp trailing corners and
`surfaceContainerLow` paint; selecting a conversation/assistant or creating a
chat dismisses back to the still-mounted chat, while settings/statistics close
the drawer before route navigation.
Conversation search inside that drawer is now source-shared as well. Android
and iOS execute the same search-field capsule, transparent indicator paint,
36/20 dp close control and glyph, focus expansion/keyboard dismissal behavior,
100 ms focus handoff, hint placement, and horizontal spacing. Android keeps its
resource-backed placeholder/hint and repository search; iOS filters its
persisted conversation titles through the same control and displays the same
16 dp empty-result surface treatment.
The drawer footer action is source-shared too: Android and iOS now execute the
same 42/48 dp action Surface, 22 dp icon slot, circular shape, 0.85 pressed
scale, 0.7 pressed alpha, damping 0.6/stiffness 300 spring, content paint, and
rounded Settings vector. Android retains its resource tooltip and
`PremiumHaptics`; iOS maps the same callback to UIKit Pop feedback and matches
the Android footer's 42 dp assistant pill, 30 dp avatar, spacing, typography,
and `surfaceContainerHighest` paint.
The production assistant picker is now source-shared rather than recreated for
iOS. The common sheet owns the disabled native sheet gestures, 48 dp manual
drag-dismiss threshold, tag chips, fixed initial height, selected capsule
paint, animated 24/10/50 dp grouped corners, 40 dp avatar slot, one-line prompt,
48 dp edit target, transition crossfade/spinner, and dismissal ordering.
Android supplies its localized strings, UUID-backed assistants/tags, `UIAvatar`,
navigation, dark-mode state, and PremiumHaptics callbacks. iOS supplies its
persisted assistant records, initial avatars, UIKit haptics, settings route, and
real assistant-to-conversation selection. The former iOS-only assistant chips
and standalone drawer New Chat button were removed because Android's drawer
does not contain those controls.
Drawer quick actions now use one shared renderer too. The common implementation
owns the 24 dp outer group clip, four-dp item spacing, 10 dp row shape,
`surfaceContainerHighest` paint, 16 dp padding and icon/text gap, 24 dp icon
slot, title typography, and the exact Android Rounded BarChart vector. Android
supplies localized Imagine/Statistics labels, its existing image glyph,
navigation, and Tick haptics in both modal and collapsed-rail presentations.
iOS places the shared Statistics row directly below search and uses Android's
same spring fade/expand collapse while search is expanded. Imagine remains
absent on iOS until the actual image-generation route/provider flow is ported;
no inert parity-only control is shown.
The production statistics-card implementation is shared as well, including its
136 dp minimum height, 24 dp card shape, icon chip, padding, typography, color
alpha, and value/subtitle alignment. Android retains its localized labels and
icons; iOS feeds the same card with totals from its persisted conversations and
provider token usage. The production activity heatmap renderer is now shared
too: both apps execute the same 264 dp card layout, month-window geometry,
horizontal scrolling and current-month positioning, cell intensity and month
boundary painting, today outline, selected-month chip, legend, and selection
haptic callback. Android still supplies its resource-backed labels, plurals,
locale month names, and number formatting. iOS groups the creation dates of its
persisted messages into the same shared calendar model and renders that data
through the same composable.

The portable `:tts` source set includes its models, settings, provider and
playback contracts, voice resolution, all eight cloud providers, queue
controller, synthesizer, text chunking, and PCM/WAV helpers. Android system
TTS, Android provider composition, and Media3 stay in `androidMain`; the iOS
source set supplies AVFoundation playback.

Current blockers in those packages should be tracked with:

```powershell
./gradlew iosPortabilityReport
```

The task writes `build/reports/ios-portability.md` and is intentionally non-failing so Android builds remain untouched.

The current shared boundary should stay green with:

```powershell
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64 :shared:compileDebugKotlinAndroid
./gradlew :common:compileKotlinIosSimulatorArm64 :common:compileDebugKotlinAndroid
./gradlew :ai:compileKotlinIosSimulatorArm64 :ai:compileDebugKotlinAndroid
./gradlew :search:compileKotlinIosSimulatorArm64 :search:compileDebugKotlinAndroid
./gradlew :tts:compileKotlinIosSimulatorArm64 :tts:compileDebugKotlinAndroid
```

## Adapter Boundaries To Add Before iOS Compilation

- Shared contracts live in `me.rerere.common.platform`; add Android and iOS implementations behind these interfaces instead of importing platform APIs from shared candidates.
- Local web media MIME detection now uses an Android boundary helper backed by `MimeTypeMap` plus explicit common fallbacks instead of JVM `URLConnection`.
- `PlatformHttpClient`: replace direct OkHttp/Retrofit usage in shared candidates with a small streaming-capable interface. Requests now carry optional `PlatformHttpProxy` settings so provider proxy behavior can stay intact when moving code out of Android. Server-sent events now expose open, event, closed, and structured failure states with optional status/body details so streaming providers can preserve current error parsing without importing OkHttp; open events also carry status and headers so streamable HTTP transports can make response-mode decisions without reaching through to an OkHttp `Response`. Android can keep OkHttp; iOS can use Ktor/Darwin or NSURLSession-backed code.
- `PlatformFileStore`: replace direct `java.io.File` usage in shared candidates and portable-facing services. Android maps this to app-private files and now exposes last-modified metadata; iOS can map it to app container storage.
- `PlatformMediaEncoder`: provider image/video/audio base64 work now goes through this contract. Android keeps the existing BitmapFactory/JPEG conversion behavior in `AndroidPlatformMediaEncoder`; iOS can use CoreGraphics/ImageIO and Foundation base64 without changing provider request JSON.
- `PlatformJwtSigner`: Vertex service-account JWT signing goes through this contract. Android keeps JVM RSA signing in `AndroidPlatformJwtSigner`; iOS can map the same input bytes and PKCS#8 PEM to native signing APIs.
- `SecureSettingsStore`: hide Android DataStore and encrypted preferences behind a shared settings contract.
- `DocumentPromptParser`: keep AI prompt construction independent from Android file URIs, MuPDF file paths, DOCX zip streams, render-cache file creation, and JVM hashing. Android currently uses `AndroidDocumentPromptParser`; iOS can provide an equivalent parser/renderer while preserving the exact generated prompt text and OCR fallback annotations.
- `PlaceholderRuntimeValues`: keep assistant placeholder definitions independent from Android `Build`, battery, location, geocoder, and `java.time` formatting. Android currently collects these values through `AndroidPlaceholderRuntimeValues`; iOS can provide the same strings from native device/time/location APIs without changing placeholder keys, display names, or replacement behavior.
- `MessageTemplateRenderer`: keep message-template transformation independent from Pebble, JVM `Reader`/`Writer`, and template-cache APIs. Android currently binds `PebbleMessageTemplateRenderer` and `AndroidMessageTemplateContextFactory`; iOS can bind a native renderer while preserving the existing template context keys and generated message text.
- `TimeAwarenessRuntimeInfo`: keep time-awareness prompt assembly independent from Android/JVM `ZonedDateTime`, timezone display APIs, and locale-specific zone labels. Android currently maps the system clock and zone through `AndroidTimeAwarenessRuntimeInfo`; iOS can provide the same neutral timestamp/zone snapshot while reusing the shared prompt logic.
- `GenerationRuntimeInfo`: keep generation-time prompt decisions independent from JVM date/time APIs. Android currently maps recent-conversation "today" checks and episodic-memory grouping through `AndroidGenerationRuntimeInfo`; iOS can provide native calendar/clock behavior while preserving the exact generated prompt labels.
- `LocalToolPlatform`: keep local-tool helper behavior independent from JVM hashing, file existence checks, Android gallery file persistence, Android content URI parsing, notification posting, notification listening, and WorkManager scheduling. Android currently owns SHA-256 sandbox suffixes, generated-image gallery writes, Python sandbox content/file URIs, assistant notifications, recent-notification snapshots, and scheduled follow-up work through `AndroidLocalToolPlatform`, `AndroidGeneratedToolImageSaver`, `AndroidLocalToolPythonSandbox`, and `AndroidLocalToolNotificationPlatform`; iOS can provide native equivalents while preserving tool JSON, sandbox filenames, generated file links, and attachment-import behavior.

iOS now persists per-assistant local-tool selection and offers the production
`send_notification` and `text_to_speech` schemas through the same model-driven
tool loop. `IosUserNotificationPlatform` requests native authorization, posts
through UserNotifications, and the Swift app delegate presents foreground
notifications. TTS delegates to the source-shared controller and selected
Keychain-backed provider. Device-wide notification reading is not available to
iOS apps, so `get_notifications` returns only LastChat's persisted notification
history. `schedule_message` is behaviorally implemented rather than reduced to
a local alarm: scheduled records survive relaunches, foreground timers and a
permitted `BGAppRefreshTask` share one delivery path, recent conversation and
memory context feed the configured model, successful output is posted through
UserNotifications, and transient failures use persisted bounded backoff.
The per-assistant Character Questions toggle now exposes `ask_user` through the
normal iOS model tool loop. Generation suspends while a persisted structured
questionnaire replaces the composer mode, uses Android's card dimensions,
colors, option motion, and shared questionnaire action renderer, normalizes
option/custom/skipped answers to the Android payload contract, and resumes from
the saved tool call after a process relaunch when no in-memory waiter survives.
The iOS assistant image tool now uses the same `generate_image` schema, system
guidance, aspect/count normalization, shared OpenAI/Google provider calls, and
tool-result JSON as Android. Base64 output is decoded through Kotlin's portable
encoder into app-container `images/` storage, with prompt/model/time metadata
persisted as iOS gallery state. Returned markdown image links are promoted to
the same tappable attachment row used for native image message parts. The
drawer Imagine action and standalone generator/gallery route now use that same
repository state, including the generator/gallery crossfade, Android-shaped
floating prompt surface, aspect/count configuration sheet, cancellation,
  preview opening, persistent adaptive grid, and deletion. The same route now
  supports optional image-to-image input for multimodal image models and a
  complete ComfyUI API-workflow configuration path (server URL, checkpoint,
  workflow JSON, and prompt/model node mappings). Per-assistant JavaScript
  execution also uses the Android `eval_javascript` tool contract. Android
  keeps QuickJS; iOS creates an isolated Apple JavaScriptCore context per call
  and exposes no browser, filesystem, or network globals.
- `ChatDatabase`: keep Room on Android, introduce repository interfaces that an iOS SQLite/SQLDelight implementation can satisfy.
- `PlatformHaptics`: keep `PremiumHaptics` as the Android implementation and add an iOS implementation that maps `Pop`, `Thud`, and `Success` to native feedback generators.
- `TtsAudioPlayer`: Media3 remains the Android implementation and AVFoundation
  is the iOS implementation of the shared TTS playback contract. System
  `TextToSpeech` remains an Android-only provider rather than a cloud parity requirement.

Android adapter seeds currently exist in `me.rerere.common.platform.android` for `PlatformHttpClient`, `PlatformFileStore`, and `PlatformMediaEncoder`. The Android HTTP adapter owns OkHttp, SSE bridging, coroutine request awaiting, and HTTP proxy/proxy-auth wiring. Android DI now registers this adapter as a reusable `PlatformHttpClient`; provider code, model-catalog refreshes, shared-webpage fallback scraping, chat remote-image saving, dynamic shortcut remote-avatar loading, assistant widget remote-avatar loading, assistant Material You remote palette extraction, update-check release metadata fetching, automatic icon-cache downloads, LobeHub icon search, settings-side ElevenLabs voice discovery, Bing search, and the app-wide Coil image loader consume DI-owned platform/network adapters instead of constructing or importing their own HTTP runtime. Downloaded model-catalog persistence now uses `PlatformFileStore`, preserving the same app-private path and refresh timestamp behavior while removing direct `File`/`Files` ownership from `ModelCatalogService`. The Android media encoder owns BitmapFactory, file URI decoding, JPEG conversion, and base64 encoding. New shared-candidate code should use the common contracts and receive these Android adapters through DI rather than importing OkHttp, Android graphics APIs, or `java.io.File` directly.

MCP transport selection now goes through `McpTransportFactory`, letting `McpManager` stay free of OkHttp client construction while Android DI still supplies the same SSE and streamable-HTTP transports. Both MCP transports now use `PlatformHttpClient`: the SSE transport streams GET events and posts JSON through the platform adapter, while the streamable-HTTP transport uses the same contract for POST, DELETE, GET SSE, response status/header inspection, JSON responses, 202 accepted responses, 405 GET fallback, and inline SSE parsing. Android keeps the same OkHttp-backed stream implementation through `OkHttpPlatformHttpClient`, and iOS can satisfy the same flow with an NSURLSession/Ktor stream later. WebDAV network construction now goes through `WebDavClientFactory`; the sync service keeps its backup/restore orchestration while Android DI owns dav4jvm OkHttp auth, URL parsing, and upload request-body creation. These are still Android-boundary adapters, but the service classes are closer to the shape an iOS implementation can satisfy.

Rich-text HTML rendering now consumes a small renderer-neutral `SimpleHtmlNode` tree instead of Jsoup node classes. The tree and `SimpleHtmlParser` contracts live in `:shared` under `me.rerere.common.html` and are covered by the shared iOS compile gates. Android still builds that tree with a `JsoupSimpleHtmlParser` default supplied through `LocalSimpleHtmlParser`, so existing HTML recovery, table/detail/image/list behavior, and visible rendering remain intact; an iOS parser can later emit the same tree and reuse the Compose renderer by providing the same contract. Hex color parsing in this renderer no longer relies on AndroidX `toColorInt`.

## Visual Parity Guardrail

Before migrating any Compose UI into shared code:

1. Capture Android screenshots for chat, menu, memory, stats, provider settings, model picker, and attachment flows.
2. Add iOS screenshots for the same states.
3. Compare layout, color, shape, typography, motion intent, and haptic timing manually before accepting the migration.
4. Keep Android-specific code paths when iOS needs platform behavior; do not simplify Android UI to fit iOS.

## Compiling iOS Application Target

The repository now contains an iOS-only Compose Multiplatform application
framework in `:iosApp` and an Xcode host at
`iosApp/xcode/LastChatIOS.xcodeproj`. The host's first build phase invokes
`:iosApp:embedAndSignAppleFrameworkForXcode`, so a developer does not need to
copy frameworks manually.

The iOS UI uses the same Material color values, shape radii, message-bubble
geometry, and top-level fade navigation intent as Android. It is intentionally
isolated from `:app`; introducing the iOS target therefore cannot alter Android
rendering. New portable screens should be added to `iosApp/src/commonMain`
first, compared against Android screenshots, and only then considered for
shared use by Android.

Current iOS app status:

- Conversations use Android's `MessageNode` branching model. The node
  layer (`MessageNode`, version-tag turn resolution, and the message
  merge) is source-shared with Android from `:ai`, the iOS state file
  migrates legacy flat conversations on load, and the chat renders the
  same per-message action row as Android: copy, regenerate, edit
  (branching the node like ChatService.editMessage), fork-from-message,
  and delete (user messages truncate, assistant messages remove their
  adjacent tool-call chain like ChatService.deleteMessage), plus the
  version selector for multi-version nodes;
- Android backup archives can be restored for configuration through
  Settings → Backup → Restore from file: providers (OpenAI, Google, and
  Claude best match per type, with API keys moved into Keychain),
  assistants (memory modes, embedding provider resolution, local tools),
  appearance, search services, and TTS providers. When the backup
  includes the database, conversations are imported losslessly into the
  shared MessageNode model (including message versions) together with
  memories and the managed attachment/media files, whose URLs are
  remapped to the iOS container; a pure-Kotlin read-only SQLite file
  reader (table B-trees, records, overflow chains, rowid aliases)
  parses the backup database with no platform dependency. Memory
  embeddings are re-computed on iOS, and usage statistics plus
  memory-system internals (episodes, claims) are reported as not
  imported;
- device, Apple Silicon simulator, and Intel simulator Kotlin targets compile;
- SwiftUI hosts the Compose root controller without rewriting the UI in Swift;
- UIKit haptics implement the existing shared `PlatformHaptics` contract;
- Darwin/NSURLSession implements HTTP and SSE, Foundation implements sandboxed
  file storage and media encoding, Keychain implements secure settings, and
  Apple Security implements PKCS#8 RSA/RS256 signing;
- chat, menu, and settings navigation shells are present as parity foundations;
- conversations, provider preferences, appearance, assistant name, and system
  prompt persist in the app container, while provider secrets persist in Keychain;
- production provider networking and streaming chat orchestration are wired;
- Search settings expose 14 portable providers (all except the advanced
  SearXNG configuration), keep API keys in Keychain, and feed a real
  `search_web` tool into the OpenAI, Google, and Claude generation loop. Tool
  calls and results use the normal message protocol with Android's 256-step
  safety limit rather than a keyword-triggered search mode;
- all eight cloud TTS providers are configurable on iOS with Keychain-only
  credentials. Android and iOS now share the same chunking, prefetch, retry,
  queue, pause/resume, seeking, and playback-state controller; Android retains
  Media3 and iOS supplies AVFoundation through `TtsAudioPlayer`. Assistant
  messages consume one source-shared play/stop action on both platforms;
- generation can be cancelled from the composer without retaining a blank
  assistant message, multiple assistant profiles persist with conversation
  ownership, and endpoint/model preferences persist independently per provider;
- the native iOS document picker copies selected image/video/audio media into
  Application Support, persists pending attachment drafts by relative storage
  path, sends standard `UIMessagePart` media values, and renders local images;
- iOS state writes are serialized through a mutex and streaming messages use
  the same one-second persistence checkpoint interval as Android, preventing an
  older asynchronous save from overwriting newer conversation or draft state;
- Roleplay optimization rules now persist on iOS and use the Android pattern
  contract for Markdown emphasis, headings, blockquotes, inline code, and
  custom paired delimiters. Add/edit/delete/toggle controls feed the live chat
  renderer rather than a preview-only settings state;
- the Fonts destination applies its persisted phone-system-font toggle to the
  entire Material theme and previews app and code typography. The bundled
  Google Sans Flex family remains the default, matching Android;
- inline attachment audio, local models, and the remaining
  screens still require iOS adapters or portable repositories before the iOS
  app is feature-complete.

On macOS:

1. Open `iosApp/xcode/LastChatIOS.xcodeproj`.
2. Select an Apple development team when running on a physical device.
3. Choose an iPhone simulator or device and run `LastChatIOS`.

Cross-platform compile gates:

```powershell
./gradlew :iosApp:compileKotlinIosArm64 `
  :iosApp:compileKotlinIosSimulatorArm64 `
  :iosApp:compileKotlinIosX64
```

Only macOS with Xcode can perform the final link, code-sign, simulator launch,
and screenshot comparison.

`.github/workflows/ios-build.yml` performs the Kotlin/Native compile gates and
an unsigned simulator `xcodebuild` on macOS for iOS-related pull requests.

## Practical Migration Order

1. Generate the portability report and clear blockers from the first shared candidates.
2. Introduce platform interfaces for HTTP streaming, file storage, secure settings, haptics, and audio.
3. Move remaining provider DTOs, JSON parsing, model registry, prompt/context logic, and search request builders into `:shared`.
4. Add `iosMain` implementations for the platform interfaces.
5. Only then evaluate Compose Multiplatform UI sharing, guarded by screenshots and interaction checks.
