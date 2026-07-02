# AGENTS.md

Guidance for AI coding agents (Claude Code, Codex, Cursor, etc.) working in this repo.

## Project

**LastChat** — Android AI assistant app, fork of RikkaHub. Kotlin + Jetpack Compose + Koin + Room. Single-activity architecture with type-safe Compose Navigation. Packages an embedded Ktor web server that serves a React Router 7 SPA (`web-ui/`) for browser/remote access.

- `applicationId`: `lastchat.rikkafork.cocolal`
- Kotlin namespace: `me.rerere.rikkahub` (DO NOT rename — would break Room migrations, DataStore keys, catalog UUIDs, schema paths)
- Version: 1.4.5 / versionCode 34
- SDK: compile=36, target=36, min=28, JVM=17
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, universal APK (splits auto-disabled when building AAB)

## Modules (`settings.gradle.kts`)

| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | UI, ViewModels, Koin DI, Room, WorkManager, Ktor web server, AI orchestration, widgets, share |
| `:shared` | KMP (Android + iOS) | Platform contracts (`PlatformHttpClient`, `PlatformFileStore`, `PlatformMediaEncoder`, `PlatformJwtSigner`, `PlatformLog`, `SecureSettingsStore`, `PlatformHaptics`), pure AI types (`MessageRole`, `TokenUsage`, `ModelType`, `Modality`, registry, util) |
| `:common` | Android lib | Android adapters for `:shared` contracts (`OkHttpPlatformHttpClient`, `AndroidFileStore`, etc.), URL/JSON/HTML utilities, in-memory log ring buffer |
| `:ai` | Android lib | LLM provider abstraction: `Provider` interface + `OpenAIProvider`, `GoogleProvider`, `ClaudeProvider`, `ComfyUIProvider` |
| `:search` | Android lib | 15 web search providers (Tavily, Exa, Zhipu, Bing, SearXNG, LinkUp, Brave, Metaso, Ollama, Perplexity, Firecrawl, Jina, Bocha, NanoGPT, Grok) |
| `:tts` | Android lib | 9 TTS providers (OpenAI, Gemini, MiniMax, ElevenLabs, Qwen, FishAudio, Cartesia, PlayHT, Android System) + Media3 playback |
| `:speech` | Android lib | 6 ASR providers (OpenAI-compatible, OpenAI-Realtime, DashScope, Volcengine, MiMo, Step) |
| `:highlight` | Android lib (Compose) | QuickJS + PrismJS syntax highlighter (single-threaded, max 4096 chars per block) |
| `:document` | Android lib | PDF (MuPDF) + DOCX (XmlPullParser → Markdown) parsing |
| `:workspace` | Android lib + CMake | On-device Linux sandbox via PRoot for AI tool use |
| `:app:baselineprofile` | `com.android.test` | Baseline Profile generator |
| `web-ui/` | (non-Gradle) React Router 7 SPA | Embedded web frontend, built via `npm run build`, served by Ktor |

Dependency graph: `:app` → all other modules. `:common` →(`api`)→ `:shared`. `:ai` → `:common`. iOS targets `:shared` only.

## Build

- **Node.js + npm required** — `:app:preBuild` triggers `buildWebUi` which runs `npm run build` in `web-ui/`.
- Signing: release config reads from `local.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); falls back to debug signing if absent.
- Firebase: `google-services.json` must be in `app/` for release builds. Plugin no-ops if missing. Crashlytics + RemoteConfig only (Analytics removed for privacy — don't re-add).
- `lastchat.release.minify` Gradle property (default `true`) — pass `-Plastchat.release.minify=false` to disable R8/resource shrinking.
- Build types: `debug`, `release` (minify per flag), `baseline` (profileable, debug-signed, `.debug` suffix — used to generate baseline profile).
- APK output naming: `LastChat_${versionName}_${variantName}.apk`.
- CI (`.github/workflows/release.yml`): `workflow_dispatch` only, builds APK only (no AAB, no Play Store upload).
- `./gradlew iosPortabilityReport` — non-failing report of Android/JVM-only imports in shared-candidate packages.
- `./gradlew :app:generateReleaseBaselineProfile` — regenerates `app/src/release/baseline-prof.txt`.
- `./gradlew :app:buildAll` — `assembleRelease` + `bundleRelease` (local use; CI only does APK).
- `prepareBundledCatalogAssets` Sync task copies `catalog/lastchat_catalog.json` + `catalog/icons/**` into `build/generated/assets/catalog/`, skipping files already in `app/src/main/assets/` (per-app overrides win).

## App Architecture (`:app`)

Single-activity: `RouteActivity` is the only Compose host. All other activities are thin trampolines that route to `RouteActivity`: `ShareWithCharacterActivity`, `TextSelectionActivity`, `ShortcutHandlerActivity`, `AskLastChatShareActivity` (in `ui/activity/`), plus `WidgetConfigActivity` (in `widget/`, not `ui/activity/`).

### Navigation
- Compose Navigation 2 (NOT Nav 3 — Nav3 entries are declared in `gradle/libs.versions.toml` but the `implementation` calls are commented out in `app/build.gradle.kts`). Type-safe routes via `@Serializable` subclasses of the `Screen` sealed interface defined inline in `RouteActivity.kt` (~line 1282).
- Settings screens also register a `SettingsDestination` enum entry + `SettingsPaneEntry` in `SettingsAdaptiveScaffold.kt`. Adaptive layout: `AdaptiveSettingsScaffold` switches compact↔wide at `width ≥ 840.dp AND height ≥ 600.dp`.
- Use `navigateToChatPage(...)` (in `utils/ChatUtil.kt`) — never call `navController.navigate(Screen.Chat(...))` directly. To update an existing chat route in place, set `CHAT_ROUTE_TARGET_KEY` in `savedStateHandle`.

### DI (Koin)
- 4 modules: `AppModule`, `DataSourceModule`, `RepositoryModule`, `ViewModelModule` (in `di/`).
- Registered in `LastChatApp.onCreate` via `startKoin { androidContext(...); workManagerFactory(); modules(...) }`.
- `WorkManagerInitializer` removed via `tools:node="remove"` in manifest — Koin's `workManagerFactory()` is used so workers can `inject(...)`.
- ViewModels: `viewModel<X> { params -> X(...) }` or `viewModelOf(::X)` in `ViewModelModule`. Inject in activities with `by viewModel<X>()`, in Compose with `koinViewModel<X>()`.

### MVVM
ViewModels live in `ui/pages/<feature>/<Feature>VM.kt`. Expose `StateFlow`/`SharedFlow`. UI state via `stateIn(viewModelScope, WhileSubscribed(5000), initial)`.

### Package layout (`me.rerere.rikkahub.*`)
- `data/ai/` — `GenerationHandler` (orchestrator, ~1768 lines), `MemorySearchService`, `AILogging`, `AIRequestInterceptor`, `BuiltInToolResolution.kt` (top-level functions, NOT a class), transformers (`transformers/`), tools (`tools/`), MCP (`mcp/`), RAG (`rag/`), model catalog (`models/`). (`ChatService` lives in `service/`, NOT here.)
- `data/db/` — Room v33, 10 entities, 10 DAOs, schemas at `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/{1..33}.json`
- `data/datastore/` — `PreferencesStore.kt` (canonical `SettingsStore.update { ... }` API), `SecureStore.kt` (EncryptedSharedPreferences), `QuickSettingsCache.kt`, `SecretKeyManager.kt`, `DefaultProviders.kt`, `migration/PreferenceStoreV1Migration.kt`
- `data/repository/` — 7 repos (Conversation with paging3, ChatAttachment with sha256-dedup + OCR, Memory with RAG, GenMedia, AppStorage, Workspace, ChatAttachmentManager)
- `data/sync/` — `WebdavSync.kt` (dav4jvm + zip), `BackupArchiveFormat.kt`, `DatabaseSanitizer.kt`, importers (`ChatboxImporter`, `CherryStudioProviderImporter`)
- `data/api/` — `SponsorAPI.kt` (live). `LastChatAPI.kt` and `RikkaHubAPI.kt` are EMPTY STUBS — don't add code to them.
- `data/model/` — `Assistant.kt`, `Conversation.kt` (incl. `MessageNode`), `Lorebook.kt`, `Skill.kt`, `Mode.kt`, `Avatar.kt`, `Tag.kt`, `Sponsor.kt`, `Leaderboard.kt`, `ChatAttachment.kt`, `CharacterCard.kt`, `TextSelectionConfig.kt`, `AppStorage.kt`
- `data/provider/` — `WorkspaceDocumentsProvider.kt` (SAF)
- `data/search/` — `AndroidBingSearchClient.kt`
- `service/` — `ChatService.kt`, 4 WorkManager workers (`SpontaneousWorker`, `ScheduledMessageWorker`, `MemoryConsolidationWorker`, `ChatStorageMaintenanceWorker`), `WebServerService` (foreground, holds WifiLock), `AssistantNotificationListener` (NotificationListenerService), `ChatPersistenceMode.kt`, `ChatGenerationTransformers.kt`, `SpontaneousMessaging.kt`, `ScheduledMessageReceiver.kt`, `stt/ChatMultimodalASRController.kt`
- `web/` — Ktor server: `Entry.kt` (`startWebServer`), `WebServerManager.kt` (lifecycle + NSD/mDNS `_http._tcp.local.`), `WebApi.kt` (~1636 lines, JWT HMAC-SHA256 + REST + SSE + static assets), `WebDtos.kt` (plural), `WebMedia.kt` (server-side URI→`/api/files/content?uri=...` resolver for `file://`/`content://`/`android.resource://` only; relative-path handling is web-ui client-side — see Web UI Integration), `WebUploadRegistry.kt`, `NsdServiceRegistrar.kt`, `Exceptions.kt`
- `widget/` — `AssistantWidget.kt` (Glance) + `AssistantWidgetReceiver` + `WidgetConfigActivity` + `WidgetPrefs`
- `share/` — `ShareIntentResolver.kt` for ACTION_SEND/SEND_MULTIPLE
- `ui/` — `theme/` (Theme, AppShapes, Color, Type, PresetTheme with 6 presets, AssistantChatTheme), `motion/` (MotionPolicy), `hooks/` (PremiumHaptics with 12 patterns, plus 14 other hooks), `components/` (ai/, chat/, message/, nav/, richtext/, table/, textselection/, webview/, crop/, ui/), `pages/` (assistant/, backup/, chat/, developer/, imggen/, menu/, onboarding/, setting/ with 16+ pages, share/handler/, webview/, extensions/workspace/), `activity/` (4 trampoline activities + `TextSelectionVM` + `QuickAskSupport`), `context/`, `modifier/`, `image/` (Coil3)
- `utils/` — `Json.kt` (JsonInstant, JsonInstantPretty, jsonPrimitiveOrNull), `ChatUtil.kt`, `AppLocale.kt`, `AppShortcutManager.kt`, `BidiText.kt`, `CoroutineUtils.kt` (Flow.toMutableStateFlow), `DatabaseUtil.kt` (cursor window 16MB), `InstantSerializer.kt`, `OwnedFileStorage.kt`, etc.
- `navigation/` — `ChatRouteTarget.kt` (DTO for in-place route updates)
- `LastChatApp.kt` (Application + Koin startup + WorkManager scheduling + notification channels), `AppScope` (on `Dispatchers.Default`), `RouteActivity.kt` (single Compose host + `Screen` sealed interface)

## AI Integration

### Entry points (in `:app`)
- `GenerationHandler.generateText(...): Flow<GenerationChunk>` — orchestrator, max 256 tool-use steps
- `ChatService` — Koin single, owns `Map<Uuid, MutableStateFlow<Conversation>>` + reference counts + generation jobs + `Map<Uuid, ChatPersistenceMode>` + error/generationDone SharedFlows
- Transformers: input/output pipeline in `service/ChatGenerationTransformers.kt`. Output has 3 hooks: `transform` (every chunk), `visualTransform` (visual-only during streaming), `onGenerationFinish`. Built-ins: `TemplateTransformer` (Pebble), `PlaceholderTransformer`, `OcrTransformer`, `DocumentAsPromptTransformer` (uses `:document` PdfParser), `ThinkTagTransformer`, `RegexOutputTransformer`, `Base64ImageToLocalFileTransformer`, `UnsupportedFileTransformer`, `WorkspaceReminderTransformer`
- Tools: `LocalTools.kt` (5 active options: JS via QuickJS, Notifications, TTS, ImageGeneration, CharacterQuestions/ask_user — plus a legacy `PythonEngine` enum entry kept ONLY for backwards-compatible settings deserialization; Python execution is now via the PRoot Linux workspace tools; `app/src/main/python/executor.py` is no longer wired up by any Kotlin source), `WorkspaceTools.kt` (read/write/edit/shell via PRoot), memory tools, `manage_skills`, MCP tools via `McpManager`
- RAG: `EmbeddingService`, `VectorEngine` (object), `MemoryChunker` (object), `VectorUtils`, `MemoryRepository.retrieveRelevantMemories` (function defaults: `limit=5, similarityThreshold=0.5f` — NOT 0.45/10; `ChatService` passes `limit = if (assistant.ragLimit > 50) 9999 else assistant.ragLimit` at `ChatService.kt:1503`)
- Catalog: `ModelCatalogService` downloads `lastchat_catalog.json` from `raw.githubusercontent.com/Cocolalilal/LastChat/LastChat/...`, `CatalogSettingsMerger.kt` (top-level `mergeCatalogIntoSettings` function, NOT a class) + `ModelMetadataResolver` (class) resolve models against catalog + `ModelRegistry` (object in `:shared`)
- MCP: `McpManager` reactively syncs clients when `settings.mcpServers` changes. Custom transports (NOT SDK built-ins) in `transport/` (`SseClientTransport`, `StreamableHttpClientTransport`), both built on `PlatformHttpClient`.

### Provider layer (in `:ai`)
- `Provider<T : ProviderSetting>` is an **interface** (not abstract class). Implement, don't extend.
- `ProviderManager(platformHttpClient, platformMediaEncoder, platformJwtSigner)` — pre-registers OpenAI/Google/Claude/ComfyUI in `init {}`. No `OkHttpClient` param.
- SSE via `PlatformHttpClient.streamEvents(): Flow<PlatformServerEvent>` (sealed: `Open`, `Event`, `Closed`, `Failure`). Android adapter: `OkHttpPlatformHttpClient` in `:common`.
- OpenAI split: `ChatCompletionsAPI` (`/chat/completions`, ends on `[DONE]`) vs `ResponseAPI` (`/responses`, ends on `response.completed`). Toggle per-provider via `ProviderSetting.OpenAI.useResponseApi`.
- Host-specific dispatch is everywhere — `withReferHeaders(baseUrl)` adds `X-Title`/`HTTP-Referer` for OpenRouter and `APP-Code: DKHA9468` for AiHubMix (NOT `Referer`/`X-Title` for both); reasoning field shape varies (OpenRouter `reasoning`, DashScope `enable_thinking`+`thinking_budget`, Volcengine `thinking.type`, Mistral nothing, InternLM `thinking_mode`, SiliconFlow `enable_thinking`, Zhipu `thinking.type`, OpenAI official `reasoning_effort`). Copy from existing provider if adding a host.
- `KeyRoulette.default()` splits on `[\\s,]+`, returns random key.
- `AIRequestInterceptor` (OkHttp) overrides auth for `api.siliconflow.cn` free models per Firebase RemoteConfig; decision delegated to `SiliconFlowFreeApiPolicy` in `:shared`.
- Vertex: `ServiceAccountTokenProvider` builds RS256 JWT, signs via `PlatformJwtSigner`, caches token (5-min pre-expiry buffer, Kotlin atomic — no `java.util.concurrent`).
- 429 retry: all providers use `retryWhen` up to 3 attempts with linear backoff `1000ms * (attempt + 1)`.

### Adding a new provider
1. Add `ProviderSetting` subtype in `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` (with `@SerialName`). Implement the 4 abstract model mutators (`addModel`, `editModel`, `delModel`, `moveMove` — **note: source spelling, NOT `moveModel`; appears to be a typo in the source itself**) and `copyProvider(...)`.
2. Create provider class at `ai/src/main/java/me/rerere/ai/provider/providers/YourNameProvider.kt` implementing `Provider<ProviderSetting.YourName>`. Use `me.rerere.ai.util.json`, `PlatformLog`, `PlatformHttpClient`. Copy the `callbackFlow { ... }.retryWhen { ... 429 ... }` SSE pattern from `ClaudeProvider.streamText`.
3. Register in `ProviderManager.init {}` + add `when` branch in `getProviderByType`.
4. Add model-ability patterns to `ModelRegistry` in `:shared` (`shared/src/commonMain/kotlin/me/rerere/ai/registry/ModelRegistry.kt`) if model IDs need pattern-based ability inference.
5. Add UI in `:app` (`ui/pages/setting/components/ProviderPresets.kt`, `ProviderConfigure.kt`, `SettingProviderDetailPage.kt`). Optional: add a default preset in `data/datastore/DefaultProviders.kt`.
6. **Add a `SecretKeyManager.when` branch** in `:app` — or API keys won't persist securely.
7. Add tests in `ai/src/test/.../YourNameProviderTest.kt` — instantiate with stub `PlatformHttpClient` + `PlatformMediaEncoder`.

### Adding a new tool
- `Tool` type lives in `ai/src/main/java/me/rerere/ai/core/Tool.kt` (in `:ai`, not `:shared`). `@Serializable data class` with non-serializable `parameters: () -> InputSchema?`, `systemPrompt`, `execute` function fields. `approvalMode: ToolApprovalMode { Auto, RequiresApproval }`.
- Built-in tools live in `:app` at `data/ai/tools/LocalTools.kt` (lazy `Tool` instances) and `WorkspaceTools.kt`.
- `BuiltInTools.Search`/`UrlContext` are **not** `Tool`s — they're a separate sealed class in `:shared` (`ModelTypes.kt`), translated to provider-specific payloads (Google: `google_search`/`url_context`; OpenAI/Claude: no-op). Set on `Model.tools: Set<BuiltInTools>`, resolved per-request by top-level functions in `BuiltInToolResolution.kt` (`resolveActiveBuiltInTools`, `shouldUseBuiltInSearch`) in `:app`.

## UI Conventions

- **Material You 3 Expressive / Android 16**. 10 global Compose opt-ins enabled in `app/build.gradle.kts` (ExperimentalMaterial3Api, ExperimentalMaterial3ExpressiveApi, ExperimentalMaterial3AdaptiveApi, ExperimentalAnimationApi, ExperimentalSharedTransitionApi, ExperimentalFoundationApi, ExperimentalLayoutApi, kotlin.uuid.ExperimentalUuidApi, kotlin.time.ExperimentalTime, kotlinx.coroutines.ExperimentalCoroutinesApi).
- **`AppShapes`** (`ui/theme/Shape.kt`) — full token list: `CardLarge=28dp`, `CardMedium=24dp`, `CardSmall=16dp`, `ButtonPill=50%`, `ButtonRounded=20dp`, `ButtonSquared=12dp`, `InputField=20dp`, `SearchField=ButtonPill`, `Chip=12dp`, `Tag=50%`, `Dialog=28dp`, `BottomSheet=28dp top`, `Avatar=50%`, `IconButton=50%`, `ListItem=16dp` (+First/Last for grouping). M3 shapes overridden: extraSmall=8, small=12, medium=16, large=24, extraLarge=28. Plus optical-roundness helpers (CardLargeInner12, CardLargeInner8, CardMediumInner12, CardSmallInner8, MessageBubbleInner=8dp, MessageOutgoing 20/20/20/6, MessageIncoming 20/20/6/20).
- **`PremiumHaptics`** (`ui/hooks/PremiumHaptics.kt`) — 12 patterns: `Tick`, `Pop` (clicks/toggles), `Thud` (heavy), `Buildup`, `Success`, `Error`, `DragStart`, `DragEnd`, `Send` (whoosh), `ScrollEdge`, `Selection`, `Cancel`. Use `rememberPremiumHaptics()`. Respects `settings.displaySetting.enableUIHaptics`. **NEVER use `LocalHapticFeedback` directly.**
- **`MotionPolicy`** (`ui/motion/MotionPolicy.kt`) — `LocalMotionPolicy` + `rememberSystemMotionPolicy()`. Respects system "reduce motion" (reads `Settings.Global.ANIMATOR_DURATION_SCALE` via ContentObserver). Top-level routes (Chat↔Menu) fade-only (120ms in / 90ms out). Other routes slide+fade (200ms slide with FastOutSlowInEasing, 150ms in / 100ms out; slide offset `it/2` forward-enter, `-it/4` forward-exit). `lateralEnterTransition`/`lateralExitTransition` for sibling-tab navigation. `hierarchicalEnterTransition`/`hierarchicalExitTransition` for parent→child. When `useWideSettingsLayout` AND both source+target are settings-pane routes, transitions are None. `BackButton` uses `spring(dampingRatio=0.6f, stiffness=300f)` and scales to 0.85f on press — golden standard for round/clicky elements.
- **Animation specs**: Standard spring `spring(dampingRatio = 0.5f, stiffness = 400f)`. Bouncy/clicky `spring(dampingRatio = 0.6f, stiffness = 300f)`. Non-spring timing (incl. `tween`) acceptable where it improves UX.
- **Icons**: `Icons.Rounded.XXX` (Material, `androidx.compose.material.icons.Icons.Rounded`) is the ONLY icon set actually in use (~680 occurrences across ~97 files). Lucide (`com.composables.icons.lucide`) is declared in `:app` deps but has ZERO usages — don't add new Lucide usages; either use `Icons.Rounded.XXX` or remove the dep.
- **Toasts**: `LocalToaster.current` (`ui/context/ToasterContext.kt`). `ToastType.Normal/Success/Info/Warning/Error`. Activities outside `AppRoutes` must provide their own `LocalToaster`.
- **Form rows**: `FormItem(label = {...}, description = {...}, tail = { HapticSwitch(...) })` (in `ui/components/ui/Form.kt`).
- **How to add a screen**:
  1. Add `@Serializable data class`/`data object` to `Screen` sealed interface in `RouteActivity.kt` (~line 1282).
  2. Register `composable<Screen.X> { backStackEntry -> ... }` in `AppRoutes`'s `NavHost`.
  3. For settings: add `SettingsDestination` enum entry + `SettingsPaneEntry`; wrap page in `AdaptiveSettingsScaffold(selected = SettingsDestination.Yours) { ... }`.
  4. Create `<Feature>VM : ViewModel()`. Register in `di/ViewModelModule.kt` with `viewModel<FeatureVM> { ... }` or `viewModelOf(::FeatureVM)`.
  5. Inject in Compose with `koinViewModel<FeatureVM>()`.

## State & Data Rules

- **`AppScope`** defaults to `Dispatchers.Default` — MUST switch to `Dispatchers.IO` for I/O. (`CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("AppScope") + CoroutineExceptionHandler)` in `LastChatApp.kt`.)
- **`!!` STRICTLY PROHIBITED on JSON elements** — use `is JsonArray`, `jsonObjectOrNull`, `jsonArrayOrNull`, `jsonPrimitiveOrNull` (custom helpers in `:common` `me.rerere.common.http`); `contentOrNull`/`intOrNull`/`longOrNull`/etc. come from kotlinx.serialization stdlib (`JsonPrimitive.contentOrNull` extension).
- **Snapshot rule for `StateFlow` in services** — always snapshot current value into local val before complex transformations: `val current = stateFlow.value; stateFlow.value = current.copy(...)`. Critical for concurrent writes in `ChatService` and `GenerationHandler`.
- **`SettingsStore.update { ... }` is canonical** — normalizes (5 stages in main block: `normalizeWebServerSettings`, `migrateLegacyModesToSkills`, `normalizeFontSettings`, `normalizeThemeId`, `normalizeTtsSettings` — see `PreferencesStore.kt:491-496`; plus a re-normalize after secret migration), migrates secrets, updates in-memory state, persists. **Never call `dataStore.edit` directly** (bypasses normalization + secret migration + in-memory state).
- **`JsonInstant`** (`utils/Json.kt`): `ignoreUnknownKeys=true, encodeDefaults=true, coerceInputValues=true` — **NO `explicitNulls=false`** (defaults to `true`). NO snake_case strategy — field mapping for external APIs must be manual via `@SerialName`. `JsonInstantPretty` = same MINUS `coerceInputValues` PLUS `prettyPrint=true` (i.e., NOT exactly the same). Only `JsonInstant` is registered as Koin `single<Json>` (`AppModule.kt:22`); `JsonInstantPretty` is NOT. Provider code in `:ai` uses its own `me.rerere.ai.util.json` with config `ignoreUnknownKeys=true, encodeDefaults=true, explicitNulls=false` — **different from `JsonInstant`** (has `explicitNulls=false`, lacks `coerceInputValues`). Don't construct a local `Json { ... }` in provider code.
- **`derivedStateOf` rule for `LazyColumn`** — never pass mutable collections (`SnapshotStateList`) to `items(...)`; use `derivedStateOf` to pass simple immutable values (e.g., `Boolean`) to prevent unnecessary recompositions.
- **Secrets MUST go through `SecretKeyManager`** (uses `SecureStore` = EncryptedSharedPreferences). Key naming: `provider_apikey_<id>`, `provider_privatekey_<id>`, `tts_provider_apikey_<id>`, `stt_provider_apikey_<id>`, `webdav_password`. **Never persist plaintext API keys in Settings.**
- **`Settings.init = true` is a dummy flag** — `SettingsStore.update` refuses to persist if `init==true`. Use `Settings.dummy()` for placeholders.
- **Embeddings stored in BOTH source entities AND `EmbeddingCacheDAO`** (keyed by `memory_id+memory_type+model_id` so model switches don't invalidate cache). Sync both. Prefer existing entity embeddings over re-computation.
- **`MessageNode` branching**: each node has `messages: List<UIMessage>` + `selectIndex`. Users can regenerate/edit to create branches. `selectConversationTurnVersion` switches versions.
- **`ChatPersistenceMode`**: `NORMAL`/`TEMPORARY`/`PERSIST_ON_REPLY` (used by spontaneous "unrelated" messages).
- **Streaming checkpoint**: `STREAMING_CHECKPOINT_INTERVAL_MS=1000` (saves to DB every 1s). `AUTO_RESUME_MAX_RETRIES=3`, `AUTO_RESUME_RETRY_DELAY_MS=700`.
- **`MemoryItemEntity`/`MemoryItemFtsEntity` exist as files but are NOT in `@Database` yet** — WIP, don't assume queryable.
- **`handleMessageChunk(messages, chunk, model)`** is the streaming-merge entry point in `:ai` `ui/MessageUtils.kt`. Don't write your own merger. `List<UIMessage>.limitContext(size)` walks backwards through tool-call→tool-result dependency chains — don't replace with `takeLast(size)`.
- **`Model.providerOverwrite: ProviderSetting?`** — a model can carry its own `ProviderSetting` that overrides the user's selected provider. `GenerationHandler` consults this; if non-null, routes the request through the overwrite's provider. This is how the model catalog routes specific models to specific providers.

## Database

- Room v33. KSP (not kapt). `room.schemaLocation = "$projectDir/schemas"`. 10 entities, 10 DAOs.
- **Adding a migration**:
  1. Bump `version = N` in `@Database`.
  2. Prefer `AutoMigration(from=N-1, to=N)` (no spec) → `AutoMigration(..., spec=...)` (@DeleteColumn/@RenameColumn/@RenameTable/@DeleteTable) → manual `val MIGRATION_X_Y = object : Migration(X, Y) { ... }` in companion object of `AppDatabase`.
  3. For manual migrations, ALWAYS check `PRAGMA table_info(...)` for existing columns before `ALTER TABLE ADD COLUMN`.
  4. Register in `dataSourceModule`'s `Room.databaseBuilder(...).addMigrations(...)`.
  5. **Commit the new `app/schemas/.../N.json`** (auto-generated by KSP). Don't delete old schemas (used for migration tests).
- `onOpen` callback runs `PRAGMA busy_timeout = 5000`. Cursor window size 16MB (`DatabaseUtil.setCursorWindowSize(16 * 1024 * 1024)` in `LastChatApp.onCreate`).
- Type converters: `TokenUsageConverter` (only one). New converters go in `data/db/` and registered in `@Database(typeConverters = [...])`.

## i18n

- **7 locales** (default `values/` = English): `values-ar` (Arabic, RTL), `values-b+zh+Hans` (Simplified Chinese — BCP-47 form, NOT `values-zh-rCN`), `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru`.
- String files per locale: `strings.xml` (~1402 strings in default), `strings_rtl_followup.xml` (RTL-specific — default + ar + zh-Hans), `strings_ui_locale.xml` (UI locale — default + ar only).
- String key naming: snake_case. Top prefixes: `setting_*`, `assistant_*`, `activity_*`, `chat_*`, `local_llm_*`, `workspace_*`, `backup_*`, `context_*`, `skills_*`, `lorebook*`. Page-specific use page prefix (`setting_page_*`, `assistant_page_*`).
- `generateLocaleConfig = true` — locale config auto-generated from `values-*`.
- `app/src/main/res/resources.properties` present (likely `unqualifiedResLocale=en-US`).
- **DO NOT submit new languages unless explicitly requested.**
- If the user does not explicitly request localization, prioritize implementing functionality without considering localization (e.g., `Text("Hello world")`).
- The standalone `i18n/` tool (Bun + Ink TUI) translates missing entries via AI — see `i18n/AGENTS.md`.

## Web UI Integration

- Ktor/CIO server in `:app` `web/` module. Static assets served from `web-ui/build/client/` (bundled as Android assets via `assets.srcDir("../web-ui/build/client")`).
- Auth: JWT HMAC-SHA256, 30-day TTL, issuer `"lastchat-web"`, audience `"lastchat-web-client"`. SPA reads `window.__LASTCHAT_WEB_BOOT__.authRequired` (injected into `index.html` by `buildWebClientBootConfig`) to show password gate.
- API base `/api/`. SSE for settings + conversation updates. `WebDtos.kt` (plural) mappers (`Settings.toWebSettingsDto`, `Conversation.toDto`, `UIMessage.toDto`, `List<WebMessagePartDto>.toUiMessageParts`, `Assistant.toWebAssistantDto`). Server-side `WebMedia.kt::toWebMediaUrl` resolves `file://`/`content://`/`android.resource://` → `/api/files/content?uri=<encoded>` (server emits the URL **without** an auth token; the `?access_token=` query is appended client-side by web-ui's `appendWebAuthQuery`). Relative-path → `/api/files/path/<path>` resolution is **web-ui client-side only** (`lib/files.ts::resolveFileUrl`), not in `WebMedia.kt`.
- `buildWebUi` Gradle task (in `:app`) runs `npm run build` in `web-ui/` (Windows: `cmd /c npm run build`). **Node.js + npm required** or build fails. Wired as `preBuild` dependency. Inputs: `app/`, `public/`, `package.json`, `react-router.config.ts`, `tsconfig.json`, `vite.config.ts`, `package-lock.json`.
- For web-ui development, see `web-ui/AGENTS.md`.

## Catalog & Skills

- `catalog/lastchat_catalog.json` — model catalog with **10 top-level keys**: `schema_version`, `updated_at`, `providers`, `model_families`, `models` (currently `[]` — reserved), `global_rules`, `model_overrides`, `search_providers`, `tts_providers`, `stt_providers`. Resolved by `ModelCatalogService` + `CatalogSettingsMerger.kt` (top-level function `mergeCatalogIntoSettings`, NOT a class) + `ModelMetadataResolver` (class) in `:app`.
- `.agents/skills/lastchat-catalog/SKILL.md` — authoring guide. **Read it before editing the catalog.**
- `tools/catalog_editor/catalog_editor.py` — Tkinter GUI editor (Python 3, stdlib + tkinter only). Run via `python tools/catalog_editor/catalog_editor.py` or `tools/catalog_editor/run_catalog_editor.bat`. **Hardcoded `MODEL_TYPES = ["CHAT", "IMAGE", "EMBEDDING"]` and `MODALITIES = ["TEXT", "IMAGE"]` (no STT/AUDIO)** — use SKILL.md + hand-edit JSON for STT models.
- Resolution pipeline: `global_rules` → `model_families` → `model_families[].versions` → `model_overrides`. Each layer overrides previous; only resolved if at least one rule matched.
- `CatalogModelOverride` has **no `icon` field** — icons always come from the matched `model_families` entry. Putting `"icon"` in an override is silently ignored.
- `ModelType`: `CHAT` (in/out TEXT), `EMBEDDING` (in/out TEXT), `IMAGE` (in TEXT, out IMAGE), `STT` (in AUDIO, out TEXT). Chat models must NEVER have `"AUDIO"` input — policy enforced by `.agents/skills/lastchat-catalog/SKILL.md`, no runtime validation in `ModelMetadataResolver`.
- `stt_providers` use slug `id` (e.g. `"groq"`), NOT UUID — different from `providers[]`.

## iOS Portability

`:shared` is KMP (Android + iOS arm64/sim-arm64/x64 → framework `LastChatShared`, static). Platform contracts (`PlatformHttpClient`, `PlatformFileStore`, `PlatformMediaEncoder`, `PlatformJwtSigner`, `PlatformLog`, `SecureSettingsStore`, `PlatformHaptics`) live in `:shared`/`me.rerere.common.platform`. Android adapters live in `:common`/`me.rerere.common.platform.android`. iOS impls don't yet exist.

When working in shared-candidate packages (see `iosCandidateModules` in root `build.gradle.kts`): `shared`, `ai`, `common`, `search`, `highlight`, `document`, `tts`, `app`:
- Use `PlatformHttpClient`, not `OkHttp` directly.
- Use `PlatformLog`, not Android `Log` or `println`.
- Use `me.rereere.common.http.urlHostOrNull()`, not `java.net.URI` or `okhttp3.HttpUrl`.
- Use `me.rereere.common.http.urlEncode()`, not `java.net.URLEncoder`.
- Use `kotlin.io.encoding.Base64`, not `java.util.Base64` or `android.util.Base64`.
- No `java.util.concurrent` — use Kotlin atomics (`kotlin.concurrent.atomics.AtomicReference`).
- No `::class.java`/`javaClass` — use `::class.simpleName` for diagnostic labels.
- Run `./gradlew iosPortabilityReport` after changes to `:ai`/`:common`/`:shared`.

See `docs/ios-portability.md` for the full plan.

## What NOT to do

- **Don't call the app "RikkaHub"** — it's LastChat (fork).
- **Don't rename the Kotlin namespace** `me.rerere.rikkahub` — would break Room migrations, DataStore keys, catalog UUIDs, schema paths.
- **Don't use `LocalHapticFeedback`** — use `PremiumHaptics` via `rememberPremiumHaptics()`.
- **Don't persist plaintext API keys** in Settings — use `SecretKeyManager` + `SecureStore`.
- **Don't call `dataStore.edit` directly** — use `SettingsStore.update { ... }`.
- **Don't add code to `LastChatAPI`/`RikkaHubAPI`** — they're empty stubs. Use `PlatformHttpClient` or follow `SponsorAPI.create(...)` pattern.
- **Don't assume `MemoryItemEntity`/`MemoryItemFtsEntity` are queryable** — not yet in `@Database`.
- **Don't add a Room migration without registering it** in `dataSourceModule.addMigrations(...)`.
- **Don't add a `Screen` without `composable<Screen.X>` in `AppRoutes`** — and `SettingsDestination` for settings screens.
- **Don't add a new ViewModel without registering it** in `di/ViewModelModule.kt`.
- **Don't add new languages** unless explicitly requested.
- **Don't call `navController.navigate(Screen.Chat(...))` directly** — use `navigateToChatPage(...)`.
- **Don't reduce OkHttp `readTimeout` below 10 minutes** — streaming. (MCP traffic uses a separate `MCP_OKHTTP_CLIENT` with no logging/AIRequestInterceptor; search uses `SEARCH_PLATFORM_HTTP_CLIENT` with 30s readTimeout.)
- **Don't strip `__from_message_attachment` / `__from_message_source_index` metadata** from attachment parts — `buildEditedParts()` reads them.
- **Don't re-add Firebase Analytics** — only Crashlytics + RemoteConfig (privacy).
- **Don't replace `takeLast(size)` for context** — `List<UIMessage>.limitContext(size)` walks backwards through tool-call→tool-result dependency chains.
- **Don't write your own streaming-merge** — use `handleMessageChunk(messages, chunk, model)` from `:ai`.
- **Don't import from `lucide-react` in `web-ui`** — see `web-ui/AGENTS.md`.
- **Don't import `okhttp3.*` in `:ai` provider code or `:shared`** — use `PlatformHttpClient`.
- **Don't construct ComfyUI models manually** — `ProviderSetting.ComfyUI.addModel`/`editModel`/`copyProvider` auto-call `Model.withComfyDefaults()` (forces `.safetensors`/`.ckpt`/`.pt`/`.pth`/`.bin` extension, `type=IMAGE`, etc.).
- **Don't touch the leading-assistant compatibility user prompt** in `ChatCompletionsAPI` (synthetic user message before any first-non-system assistant message).
- **Don't simplify the Qwen 3.5 reasoning-OFF trick** (`<think></think>\n` prefill + `chat_template_kwargs.enable_thinking=false`).
- **Don't rename `applicationId` or namespace** — Room DB path `me.rerere.rikkahub.data.db.AppDatabase` is locked in schema files.

## Conventional Commits

Use `feat:`, `fix:`, `chore:`, etc. (Conventional Commits spec).

## Editor config

`.editorconfig`: 4-space indent for `kt`/`kts`, 2-space for `xml`/`json`/`md`/`yml`/`yaml`. Max line 120. Trim trailing whitespace (except `md`/`yml`/`yaml` — note `yaml` extension is ALSO excluded). Insert final newline. UTF-8.
