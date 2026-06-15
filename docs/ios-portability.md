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
- `me.rerere.common.http`
- `me.rerere.search`
- `me.rerere.tts.model`
- `me.rerere.tts.provider`
- `me.rerere.tts.provider.providers`

`me.rerere.ai.core`, provider-neutral `me.rerere.ai.provider` DTOs/enums, and the pure model matching/display pieces of `me.rerere.ai.registry` now live in `:shared`. Keep new pure AI model, registry, schema, modality, ability, custom header/body, and provider option logic there. `ComfyUIProvider`, the Vertex `ServiceAccountTokenProvider`, OpenAI provider-level non-streaming calls (model listing, balance, image generation, embeddings), OpenAI text generation (`ChatCompletionsAPI.generateText`, `ChatCompletionsAPI.streamText`, `ResponseAPI.generateText`, `ResponseAPI.streamText`), Claude provider networking, and Google provider networking now use `PlatformHttpClient`. Legacy AI OkHttp proxy/SSE/request helpers have been removed after provider migration. Provider diagnostics now use the shared `PlatformLog` facade instead of Android `Log`. Provider media encoding now uses `PlatformMediaEncoder`, with Android Bitmap/File/base64 behavior isolated in `AndroidPlatformMediaEncoder`. `ProviderManager` now accepts platform adapters instead of `OkHttpClient`, with Android DI constructing those adapters. The current shared-candidate report has no `:ai` hotspots.

`:search` now depends directly on `:shared` for `InputSchema`, `SearchServiceOptions`, common search options, and search/scrape result DTOs. Continue keeping search settings/result shapes in `:shared`; request builders and parsers are now clear of Android/JVM networking imports in the portability report.

All concrete `:search` providers now call through `PlatformHttpClient`: Bing is HTML-only, and Bocha, Brave, Exa, Firecrawl, Grok, Jina, LinkUp, Metaso, NanoGPT, Ollama, Perplexity, SearXNG, Tavily, and Zhipu no longer import OkHttp or Android logging directly. The obsolete search-local `Call.await()` helper has been removed. Android installs the same 30-second-read-timeout OkHttp-backed `PlatformHttpClient` from `LastChatApp`, so `:search` no longer constructs OkHttp. Bing's jsoup fetch/parser behavior now lives in the Android app's `AndroidBingSearchClient`, installed into `SearchService` at startup. The current shared-candidate report has no `:search` hotspots.

Shared-candidate networking in `:common` has been cleared from the portability report. The legacy OkHttp `Call.await()` and SSE helpers now live under `me.rerere.common.platform.android`, beside `OkHttpPlatformHttpClient`, so Android-only callers keep the same behavior. `Base64JsonKeyCodec` now uses Kotlin UTF-8/Base64 APIs instead of JVM charset/Base64 helpers. The file-backed cache stores (`FileIO`, `PerKeyFileCacheStore`, and `SingleFileCacheStore`) now live under `me.rerere.common.platform.android.cache`; the portable cache surface in `me.rerere.common.cache` is limited to cache entries, cache store contracts, key codecs, and the in-memory LRU wrapper.

Cloud TTS provider transport is now platform-ready. OpenAI, Gemini, MiniMax, ElevenLabs, and Qwen TTS providers call through `PlatformHttpClient`; the Android `TTSManager` is the remaining OkHttp composition boundary and preserves the existing provider-specific timeouts. Gemini and Qwen audio decoding now use Kotlin Base64 APIs, and cloud TTS diagnostics use `PlatformLog` instead of Android `Log`. The `TTSProvider` contract no longer accepts Android `Context`; Android `TTSManager` and local TTS discovery now live in `me.rerere.tts.provider.android`, while Android system synthesis lives in `me.rerere.tts.provider.providers.android`. `TtsSynthesizer` now combines streamed audio chunks without `java.io.ByteArrayOutputStream`, leaving that JVM helper only in Android playback WAV wrapping. The portability report now treats `me.rerere.tts.model`, `me.rerere.tts.provider`, and cloud `me.rerere.tts.provider.providers` as shared candidates, with Android adapter subpackages explicitly excluded. The remaining `:tts` portability findings are Android-boundary playback and platform integration: system `TextToSpeech`, Media3 audio playback/controller code, temp-file handling for system synthesis, and the Android HTTP adapter construction in `TTSManager`.

As of the latest report, `iosPortabilityReport` finds no Android/JVM-only imports in the configured shared-candidate packages (`:ai`, `:common`, `:search`, and the portable `:tts` model/provider surface). The remaining Android/JVM findings are Android-boundary code in app/common/tts/highlight/document modules that still need platform abstractions before a full iOS target can compile the whole product.

Current blockers in those packages should be tracked with:

```powershell
./gradlew iosPortabilityReport
```

The task writes `build/reports/ios-portability.md` and is intentionally non-failing so Android builds remain untouched.

The current shared boundary should stay green with:

```powershell
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosX64 :shared:compileDebugKotlinAndroid
./gradlew :common:compileDebugKotlin
```

## Adapter Boundaries To Add Before iOS Compilation

- Shared contracts live in `me.rerere.common.platform`; add Android and iOS implementations behind these interfaces instead of importing platform APIs from shared candidates.
- `PlatformHttpClient`: replace direct OkHttp/Retrofit usage in shared candidates with a small streaming-capable interface. Requests now carry optional `PlatformHttpProxy` settings so provider proxy behavior can stay intact when moving code out of Android. Server-sent events now expose open, event, closed, and structured failure states with optional status/body details so streaming providers can preserve current error parsing without importing OkHttp. Android can keep OkHttp; iOS can use Ktor/Darwin or NSURLSession-backed code.
- `PlatformFileStore`: replace direct `java.io.File` usage in shared candidates. Android can map this to app-private files; iOS can map it to app container storage.
- `PlatformMediaEncoder`: provider image/video/audio base64 work now goes through this contract. Android keeps the existing BitmapFactory/JPEG conversion behavior in `AndroidPlatformMediaEncoder`; iOS can use CoreGraphics/ImageIO and Foundation base64 without changing provider request JSON.
- `SecureSettingsStore`: hide Android DataStore and encrypted preferences behind a shared settings contract.
- `ChatDatabase`: keep Room on Android, introduce repository interfaces that an iOS SQLite/SQLDelight implementation can satisfy.
- `PlatformHaptics`: keep `PremiumHaptics` as the Android implementation and add an iOS implementation that maps `Pop`, `Thud`, and `Success` to native feedback generators.
- `PlatformAudio`: keep Media3/TextToSpeech on Android and use AVFoundation on iOS.

Android adapter seeds currently exist in `me.rerere.common.platform.android` for `PlatformHttpClient`, `PlatformFileStore`, and `PlatformMediaEncoder`. The Android HTTP adapter owns OkHttp, SSE bridging, coroutine request awaiting, and HTTP proxy/proxy-auth wiring. The Android media encoder owns BitmapFactory, file URI decoding, JPEG conversion, and base64 encoding. New shared-candidate code should use the common contracts and receive these Android adapters through DI rather than importing OkHttp, Android graphics APIs, or `java.io.File` directly.

## Visual Parity Guardrail

Before migrating any Compose UI into shared code:

1. Capture Android screenshots for chat, menu, memory, stats, provider settings, model picker, and attachment flows.
2. Add iOS screenshots for the same states.
3. Compare layout, color, shape, typography, motion intent, and haptic timing manually before accepting the migration.
4. Keep Android-specific code paths when iOS needs platform behavior; do not simplify Android UI to fit iOS.

## Practical Migration Order

1. Generate the portability report and clear blockers from the first shared candidates.
2. Introduce platform interfaces for HTTP streaming, file storage, secure settings, haptics, and audio.
3. Move remaining provider DTOs, JSON parsing, model registry, prompt/context logic, and search request builders into `:shared`.
4. Add `iosMain` implementations for the platform interfaces.
5. Only then evaluate Compose Multiplatform UI sharing, guarded by screenshots and interaction checks.
