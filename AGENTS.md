# AGENTS.md

## 1. Product Intent

**App name:** LastChat  
**Repo name:** RikkaHub

LastChat should feel like a polished "fidget toy":
- Interactions should be playful, tactile, and satisfying.
- Haptics are part of the product, not decoration.
- Visual cleanup should come from iterative glow-ups, not broad risky rewrites.
- Robustness matters as much as polish. Avoid crashes, null mistakes, and brittle UI state.

## 2. Architecture Snapshot

### Modules
- `app/`: Android app UI, DI, data, Room, settings, and most product logic.
- `ai/`: AI provider abstraction layer.
- `common/`: Shared utilities and extensions.
- `highlight/`: Syntax highlighting.
- `search/`: Search providers and integration.
- `tts/`: Text-to-speech implementation.

### Core stack
- Kotlin with experimental `kotlin.uuid.Uuid`
- Jetpack Compose
- Material 3 Expressive / Android 16 design direction
- Koin
- Room
- OkHttp / SSE
- Kotlinx Serialization

## 3. Engineering Rules

### Performance and concurrency
- Run I/O explicitly on `Dispatchers.IO`.
- `AppScope` defaults to `Dispatchers.Default`; do not block it with file, database, or network I/O.
- In Compose lists, do not pass mutable collections like `SnapshotStateList` directly into item content if a smaller immutable derived value will do.
- Prefer cache reuse over recomputing expensive AI, embedding, or formatting state.

### Robustness and state
- Never use `!!` on JSON content.
- Use safe JSON checks such as `is JsonArray`, `jsonPrimitiveOrNull`, and explicit null handling.
- When updating `StateFlow` from services, snapshot the current value first before doing multi-step transforms.
- Keep UI state deterministic. Avoid hidden visual branches that make equivalent surfaces behave differently.

### Serialization
- Use `me.rerere.rikkahub.utils.JsonInstant` or `JsonInstantPretty`.
- These serializers ignore unknown keys but do not apply snake_case automatically. Map external API fields manually.

## 4. Stabilized UI System

This repo now has a stabilized shared UI layer. New UI work should adopt it instead of creating local one-off styling.

### Source-of-truth primitives
- Shapes live in `me.rerere.rikkahub.ui.theme.AppShapes`.
- Grouped list geometry lives in `me.rerere.rikkahub.ui.theme.groupedItemShape(...)` and `groupedItemRadii(...)`.
- AMOLED-aware surface hierarchy lives in `me.rerere.rikkahub.ui.theme.appSurfaceColor(...)` with `AppSurfaceLevel`.
- Neutral placed surfaces should use `me.rerere.rikkahub.ui.theme.placedSurfaceColor()`.
- Shared text field wrappers live in `me.rerere.rikkahub.ui.components.ui.Input.kt`:
  - `AppSearchField(...)`
  - `AppOutlinedField(...)`
- Shared compact top bar lives in `me.rerere.rikkahub.ui.components.nav.AppCompactTopBar`.
- Shared hero top bar lives in `me.rerere.rikkahub.ui.components.nav.OneUITopAppBar`.
- Shared sheet and dialog shells live in `me.rerere.rikkahub.ui.components.ui.AppSheet.kt`:
  - `AppModalSheet(...)`
  - `AppAlertDialog(...)`
- Shared swipe/group container behavior lives in `me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete`.

### Page hierarchy and navigation
- Use `OneUITopAppBar` only for primary destinations and management hubs:
  settings root, assistant list, provider/search/TTS/skills/lorebook roots, and similar top-level pages.
- Use `AppCompactTopBar` for detail, tool, and utility screens:
  webview, backup, developer tools, detail pages, editors, and transient management pages.
- Do not mix hero and compact bar patterns arbitrarily on visually similar screens.
- Hero pages should use nested-scroll collapsing behavior.
- Compact pages should use a single top bar plus content padding from `Scaffold`.
- Keep edge-to-edge behavior consistent by page type. Do not stack multiple unrelated inset/padding strategies.

### Containers, grouped rows, and cards
- Prefer shared grouped row behavior over custom `RoundedCornerShape(...)` math.
- Neutral placed surfaces should resolve through one token only: `placedSurfaceColor()`.
- If a list behaves like a grouped settings stack, use grouped item shapes and shared surface colors.
- If a row is swipeable/reorderable, route the shape logic through `PhysicsSwipeToDelete` plus `groupedItemShape(...)`.
- Avoid equivalent combinations like `Card + ListItem`, `Surface + ListItem`, and raw `Row` implementations unless there is a real behavioral difference.
- Use `AppShapes.CardLarge`, `CardMedium`, `CardSmall`, `ListItem`, `Tag`, and `ButtonPill` instead of ad hoc radii whenever possible.
- Grouped neutral rows use `24.dp` outer corners and `8.dp` inner corners.
- Selected grouped rows should animate from grouped corners into a fully rounded pill.

### Fields, sheets, and dialogs
- Use `AppSearchField(...)` for search bars and searchable pickers.
- Use `AppOutlinedField(...)` for standard outlined text entry unless a Material field API requirement makes it impossible.
- Use `AppModalSheet(...)` for bottom sheets by default.
- Use `AppAlertDialog(...)` for alerts, confirmations, and lightweight info dialogs by default.
- Only drop to raw `ModalBottomSheet`, `AlertDialog`, or `OutlinedTextField` when the shared wrapper is missing a required capability. If that happens, extend the shared wrapper instead of creating another local pattern.

### AMOLED and color hierarchy
- AMOLED dark mode is a product constraint. Keep the black base dark surface.
- Preserve hierarchy with `appSurfaceColor(...)` instead of page-by-page `surfaceContainerLow` / `High` / `Highest` decisions.
- Use dynamic color and expressive color hierarchy when they do not fight the AMOLED baseline.

### Haptics and motion
- Use `rememberPremiumHaptics` and `HapticPattern`.
- Do not use `LocalHapticFeedback`.
- Standard interaction defaults:
  - Round/icon buttons: press scale `0.85f`, `HapticPattern.Pop`
  - Standard row/card press: softer spring, still tactile
  - Drag start: `HapticPattern.Pop`
  - Drop/heavy destructive action: `HapticPattern.Thud`
  - Success: `HapticPattern.Success`
- Default animation specs:
  - Standard spring: `spring(dampingRatio = 0.5f, stiffness = 400f)`
  - Clicky spring: `spring(dampingRatio = 0.6f, stiffness = 300f)`

### Material 3 Expressive intent
- Favor clear hierarchy, strong grouping, large-shape confidence, and obvious primary actions.
- Preserve the app's playful identity, but do it through shared primitives, not custom per-screen styling.
- When aligning with Material 3 Expressive, standardize the implementation, not just the colors.

## 5. Feature-Specific Rules

### RAG and embeddings
- Embeddings are persisted in both source entities (`MemoryEntity`, `ChatEpisodeEntity`) and `EmbeddingCacheDAO`.
- Add/update/delete flows must keep both stores synchronized.
- Prefer existing persisted embeddings over recomputation.

## 6. Testing and Maintenance

- Unit tests go in `src/test` and should cover helpers, parsing, and non-UI logic.
- Instrumented / Compose UI tests go in `src/androidTest` and should cover shared components and user flows.
- When adding a new shared UI primitive, add at least one regression test for it.
- When standardizing an existing screen, prefer compile-safe migration plus targeted UI tests over visual-only cleanup.
- Use Conventional Commits: `feat:`, `fix:`, `chore:`.
- Do not add new language support unless explicitly requested.

## 7. Practical Defaults for Agents

- Before creating a new UI component, search for an existing shared primitive first.
- Before adding a new radius, surface color rule, or top bar variant, ask whether `AppShapes`, `appSurfaceColor`, `AppCompactTopBar`, `OneUITopAppBar`, `AppModalSheet`, or `AppAlertDialog` already solve it.
- When you find old UI that looks the same but is implemented differently, standardize it toward the shared layer instead of preserving both versions.
