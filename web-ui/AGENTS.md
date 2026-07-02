# AGENTS.md — web-ui

Embedded React Router 7 SPA bundled into the Android app. Build output `web-ui/build/client/` is consumed by `:app` via `assets.srcDir("../web-ui/build/client")` and served by the in-app Ktor server. See root `AGENTS.md` for the Android side.

## Stack (exact versions from `package.json`)

- React Router 7.13.0 (SPA mode, `ssr: false`), React 19.2.4, TypeScript 5.9.2 (`strict: true`, `verbatimModuleSyntax: true` — must use `import type`)
- Tailwind CSS v4 (oklch colors via CSS variables)
- shadcn/ui (New York style, `components.json`)
- Zustand 5.0.11 (composed slices pattern, single store)
- ky 1.14.3 (HTTP) + manual SSE parser over `ReadableStream` (because `EventSource` can't send `Authorization` headers)
- i18next + react-i18next — **5 namespaces**: `common`, `input`, `markdown`, `message`, **`page`**
- `motion` (motion/react, NOT framer-motion)
- `@tanstack/react-query`, `react-resizable-panels`, `use-stick-to-bottom` (autoscroll — NOT `react-infinite-scroll-component`), `vaul`, `sonner`, `shiki`, `katex`, `rehype-katex`, `rehype-raw`, `remark-gfm`, `remark-math`, `file-type`, `dayjs`, `uuid`, `isbot`
- **Markdown renderer**: `streamdown` ^2.2.0 + `@streamdown/cjk` + custom `remark-rp` plugin (NOT `react-markdown`)
- `radix-ui` (the new unified package, used in 20 ui/ files — NOT legacy `@radix-ui/react-*`)
- `@material-symbols/svg-400`, `vite-plugin-svgr`
- **Bun** is the local package manager/runtime, but `:app:buildWebUi` Gradle task shells out to **`npm run build`** — don't introduce bun-only APIs.

## Commands

```bash
bun run dev          # Vite dev server, proxies /api → http://localhost:8080
bun run build        # react-router build → build/client/ + build/server/
bun run start        # Run build/server/index.js (unused in SPA mode)
bun run typecheck    # react-router typegen + tsc
bun run lint         # oxlint app --fix
bun run fmt          # oxfmt app (NOT Prettier)
bun run fmt:check    # oxfmt app --check
```

Build output: `build/client/` (HTML + JS + CSS). Gradle consumes this — no manual copy. **For Android builds, `npm run build` is invoked (not `bun run build`)**, and the `lint`/`fmt` scripts use **`oxlint`/`oxfmt`** (not ESLint/Prettier).

## Critical gotchas

1. **Icons**: `vite.config.ts` + `tsconfig.json` alias `lucide-react` → `app/lib/material-icons.tsx`, which shims `@material-symbols/svg-400` SVGs under lucide-compatible names. The alias exists for shadcn/ui compatibility, but **ZERO `app/components/ui/*.tsx` files actually import from `lucide-react`** — the only literal `lucide-react` import in the entire codebase is `app/routes/conversations.tsx:67` (`MessageSquare`). **For new components, import from `~/lib/material-icons` directly.** Add new icons by editing `app/lib/material-icons.tsx`:
   ```tsx
   import IconSvg from "@material-symbols/svg-400/rounded/<name>.svg?react";
   export const IconName = createIcon(IconSvg);
   ```
2. **Markdown lib is `streamdown`**, not `react-markdown`. Custom `remark-rp` plugin in `app/components/markdown/remark-rp.ts` does RP-style colorization. LaTeX via `\(...\)` and `\[...\]` (rewritten to `$...$`/`$$...$$`). Code highlighting via Shiki. Theme-aware. (Note: `<think>` tag rendering is NOT handled in web-ui — `<think>` content arrives already-parsed as `ReasoningPart` from the backend; if you see stray `<think>` tags in markdown output, that's a backend transformer issue, not a web-ui one.)
3. **3 store slices, not 2**: `settings-slice`, `chat-input-slice`, **`clock-slice`** (`clockOffset`/`setClockOffset`). `lib/utils.ts::serverNow()` uses `clockOffset` — use it instead of `Date.now()` for server-relative timestamps.
4. **5 i18n namespaces** (not 4): `common` (default), `input`, `markdown`, `message`, **`page`**.
5. **WebAuth gate**: ~half of `api.ts` handles a token-based auth flow. `window.__LASTCHAT_WEB_BOOT__` is injected by the Kotlin server into `index.html`. Key APIs: `requestWebAuthToken`, `isWebAuthLocked`, `onWebAuthStateChange`, `appendWebAuthQuery`, `Authorization: Bearer` header. `root.tsx` calls `useSettingsSubscription(!webAuthLocked)` and renders `WebAuthGate`. **Pause new top-level data fetching while locked.**
6. **`resolveFileUrl(url)`** (`lib/files.ts`) handles **5 cases** (NOT 3):
   - `data:` / `http(s):` → as-is
   - `/api/...` URLs → `appendWebAuthQuery(url)` (injects `?access_token=`)
   - `file://` / `content://` / `android.resource://` → `/api/files/content?uri=<encoded>` + `appendWebAuthQuery`
   - **Relative paths** (no scheme, no leading `/api/`) → `/api/files/path/<path>` + `appendWebAuthQuery`
   
   Use `resolveFileUrl()` for ALL file URLs in the UI — never construct `/api/files/...` paths by hand.
7. **SSE URLs are relative** (no `/api/` prefix) because `ky.prefixUrl = "/api"`. So `sse("settings/stream", ...)`.
8. **`verbatimModuleSyntax: true`** — must use `import type { X }` for type-only imports.
9. **Path alias `~`** → `app/` directory.
10. **Build for Android uses `npm run build`**, not `bun run build`. Don't introduce bun-only APIs.
11. **Unused deps in `package.json`**: `immer` and `zod` are declared but have ZERO imports — don't add to them. `react-infinite-scroll-component` IS used legitimately in `app/components/extended/infinite-scroll-area.tsx` for list pagination (NOT chat autoscroll — that uses `use-stick-to-bottom`).
12. **Persona placeholders** (`{{char}}`, `{char}`, `{{user}}`, `{user}`) are substituted ONLY in `TextPart` rendering via `replacePersonaPlaceholders` — not in previews, exports, or quick-jump.

## Directory structure

```
app/
├── routes/              # React Router 7 file routes
│   ├── home.tsx         # / — re-exports conversations.tsx
│   ├── c.$id.tsx        # /c/:id — re-exports conversations.tsx
│   ├── conversations.tsx # Main page (~1196 lines)
│   └── conversation-draft-support.ts
├── components/
│   ├── ui/              # shadcn/ui (34 components) — New York style
│   ├── message/         # ChatMessage, MessagePart dispatcher, parts/{text,image,video,audio,document,reasoning,reasoning-step,tool}-part.tsx, assistant-turn-message.tsx, chain-of-thought.tsx, activity-timeline.tsx, activity-pill.tsx, chat-message-annotations.tsx, chat-message-avatar-row.tsx
│   ├── markdown/        # markdown.tsx + code-block.tsx + remark-rp.ts + markdown.css
│   ├── input/           # chat-input, model-list, pickers (search/reasoning/injection/mcp), picker-error-alert
│   ├── workbench/       # Code execution workbench (workbench-host, workbench-context, code-preview-language)
│   ├── extended/        # conversation, infinite-scroll-area
│   ├── conversation-sidebar.tsx, conversation-quick-jump.tsx, conversation-search-button.tsx
│   ├── custom-theme-dialog.tsx, theme-provider.tsx, web-auth-gate.tsx, conversation-greeting.tsx, logo.tsx
├── stores/              # Zustand
│   ├── app-store.ts     # Combines all slices
│   ├── settings.ts, chat-input.ts # Re-exports useAppStore as useSettingsStore / useChatInputStore
│   ├── slices/          # types, settings-slice, chat-input-slice, clock-slice
│   └── hooks/           # use-settings-subscription.ts (SSE)
├── hooks/               # use-conversation-list, use-current-assistant, use-current-model, use-mobile, use-picker-popover
├── services/api.ts      # ky HTTP + manual SSE (~364 lines, includes WebAuth)
├── types/               # TS types aligned with Kotlin — see mapping table below
├── lib/                 # utils, material-icons, files, display, error, persona-placeholders, tool-activity, message-turns, chat-motion, chat-appearance, chat-layout, clipboard, export-markdown, type-guards
├── locales/{zh-CN,en-US}/{common,input,markdown,message,page}.json
├── i18n.ts, root.tsx, routes.ts, app.css
```

## Type mapping (TS ↔ Kotlin)

| TypeScript | Kotlin | Kotlin path |
|---|---|---|
| `MessageRole` | `MessageRole` | `shared/.../ai/core/MessageRole.kt` |
| `TokenUsage` | `TokenUsage` | `shared/.../ai/core/Usage.kt` |
| `UIMessagePart` | `UIMessagePart` | `ai/.../ui/MessageUtils.kt` |
| `UIMessage` | `UIMessage` | `ai/.../ui/MessageUtils.kt` |
| `MessageNode` | `MessageNode` | `app/.../data/model/Conversation.kt` |
| `Conversation` | `Conversation` | `app/.../data/model/Conversation.kt` |
| `ConversationDto` | `ConversationDto` | `app/.../web/WebDtos.kt` (plural, NOT `WebDto.kt`) |
| `Settings` | `Settings` | `app/.../data/datastore/PreferencesModels.kt` |

Additional types in `app/types/`: `dto.ts` (API DTOs), `settings.ts` (DisplaySetting, AssistantProfile), `helpers.ts` (type guards), `annotations.ts`, `parts.ts`, `message.ts`, `conversation.ts`, `core.ts`, `index.ts`.

**Type changes MUST be synced TS ↔ Kotlin.** Run `bun run typecheck` after.

## Message parts (exact shapes)

`UIMessagePart` is a union. Field shapes from `app/types/parts.ts`:

- `TextPart { type: "text", text: string }`
- `ImagePart { type: "image", url: string }`
- `VideoPart { type: "video", url: string }`
- `AudioPart { type: "audio", url: string }`
- `DocumentPart { type: "document", url, fileName, mime }`
- `ReasoningPart { type: "reasoning", reasoning, createdAt?, finishedAt? }` — **NO `steps[]` field** (steps are a UI-level grouping in `message-part.tsx::groupMessageParts()`)
- `ToolPart { type: "tool", toolCallId, toolName, input, output, approvalState }` — `toolName: string` is required; `approvalState: ToolApprovalState` is a 5-variant union: `auto | pending | approved | denied{reason} | answered{answer}`

`UIMessageAnnotation`: `UrlCitationAnnotation { type: "url_citation", title, url }` OR `OcrActivityAnnotation { type: "ocr_activity", source: "image"|"pdf", fileName?, pageNumbers: number[] }`.

## Routing

```ts
// app/routes.ts
[
  index("routes/home.tsx"),          // /
  route("c/:id", "routes/c.$id.tsx") // /c/:id
]
```

Both route files are 1-line re-exports from `conversations.tsx`. SPA mode (`ssr: false`). Type-safe routes auto-generated into `.react-router/types/`. Error boundary and loading placeholders in `root.tsx`.

## API client (`app/services/api.ts`)

```ts
api.get<T>(url, opts)
api.post<T>(url, data, opts)
api.postMultipart<T>(url, formData)  // file upload
api.put<T>(url, data)
api.patch<T>(url, data)
api.delete<T>(url, opts)

sse<T>(url, { onMessage, onError, onOpen, onClose }, { signal })
```

- Default prefix: `/api` (Vite proxies to `http://localhost:8080` in dev)
- Timeout: 30s
- Auto error → `ApiError`
- SSE: manual line-parser over `ky`'s `ReadableStream`. Supports `event:`/`data:`/`id:` lines, multi-line `data:` joined with `\n`. Dispatches on empty line. `timeout: false`, `Accept: text/event-stream`.

## Adding a new message part type

1. Add type to `app/types/parts.ts`
2. Sync Kotlin type in `ai/src/main/java/me/rerere/ai/ui/MessageUtils.kt`
3. Create `app/components/message/parts/<type>-part.tsx`
4. Add dispatch case in `app/components/message/message-part.tsx`
5. Add type guard in `app/types/helpers.ts`
6. Add preview switch in `app/routes/conversation-draft-support.ts`
7. Add markdown export case in `app/lib/export-markdown.ts`
8. Add `hasRenderableContentPart` switch in `app/components/message/assistant-turn-message.tsx` (NOT `lib/message-turns.ts` — that file does not contain it)
9. Add i18n keys in **both** `zh-CN/message.json` and `en-US/message.json`

## Conventions

- shadcn/ui New York style, Tailwind v4 oklch colors via CSS variables
- `cn()` helper (`lib/utils.ts`) = `clsx` + `tailwind-merge`
- Zustand: use selectors to avoid re-renders: `useSettingsStore(s => s.settings?.currentModelId)`
- Toasts: `sonner` (NOT `LocalToaster` — that's the Android side)
- Animations: `motion/react` (NOT framer-motion)
- Local state with `useState`; pickers via `@tanstack/react-query`
- Component props pattern: `interface MyComponentProps extends ComponentProps<"div"> { ... }`
- Default language: zh-CN. Language detection order: `localStorage["lang"]` → browser language (`zh*` → zh-CN, **everything else → en-US**) → default zh-CN. So a browser set to French will resolve to **en-US**, not zh-CN. The `fallbackLng: "zh-CN"` in i18next only kicks in for missing translation keys.

## API endpoints (served by Kotlin Ktor backend)

- `GET /api/settings/stream` — settings SSE stream
- `GET /api/conversations` — conversation list
- `GET /api/conversations/paged` — paged conversation list
- `GET /api/conversations/search` — search conversations
- `GET /api/conversations/:id` — get conversation
- `GET /api/conversations/:id/stream` — conversation SSE stream
- `POST /api/conversations/:id/messages` — send message (**NOT** `/send` — that endpoint does not exist)
- `POST /api/conversations/:id/messages/:messageId/edit` — edit a message
- `POST /api/conversations/:id/regenerate` — regenerate last assistant turn
- `POST /api/conversations/:id/stop` — stop active generation
- `POST /api/conversations/:id/tool-approval` — approve/deny a pending tool call
- `POST /api/conversations/:id/fork` — fork conversation at a node
- `POST /api/conversations/:id/nodes/:nodeId/select` — switch branch
- `DELETE /api/conversations/:id/messages/:messageId` — delete a message
- `POST /api/files/upload` — file upload
- `GET /api/files/content?uri=...` — file access (with `?access_token=` for WebAuth)
- `GET /api/files/path/*` — relative path file access
- `POST /api/auth/token` — WebAuth token issue (JWT HMAC-SHA256, 30-day TTL)
- `GET /api/bootstrap` — boot config (incl. `authRequired`)
- `GET /api/ai-icon?...` — provider/model icon proxy

SSE event types from backend: `ConversationSnapshotEventDto` (full snapshot), `ConversationNodeUpdateEventDto` (incremental).

## Data flow

```
root.tsx renders
  → useSettingsSubscription() subscribes to /api/settings/stream (SSE)
  → backend pushes Settings object
  → useSettingsStore.setSettings() updates global state
  → all components react (assistants, models, providers, etc.)

User selects/creates conversation
  → GET /api/conversations/:id (initial snapshot)
  → returns ConversationDto (full message tree)
  → SSE: GET /api/conversations/:id/stream
  → backend pushes ConversationSnapshotEventDto | ConversationNodeUpdateEventDto
  → UI renders messages and generation progress in real time

User sends message
  → useChatInputStore.getSubmitParts(conversationId)
  → POST /api/conversations/:id/messages { parts: UIMessagePart[] }   # NOT /send
  → SSE stream: node_update events per token/part
  → conversation.isGenerating = true → false
  → on completion: useChatInputStore.clearDraft(conversationId)
```

## What NOT to do

- **Don't import from `lucide-react`** in new code — import from `~/lib/material-icons` directly. The `lucide-react` alias exists for shadcn/ui compatibility but **no `ui/*.tsx` file actually imports from it** (the only literal `lucide-react` import is `routes/conversations.tsx:67`).
- **Don't use `react-markdown`** — use `streamdown`.
- **Don't call `Date.now()`** for server-relative timestamps — use `serverNow()` from `lib/utils.ts`.
- **Don't strip `__from_message_attachment` / `__from_message_source_index`** metadata from attachment parts — `buildEditedParts()` reads them.
- **Don't add a part type without updating all 9 places** listed above.
- **Don't assume `ReasoningPart` has `steps[]`** — it doesn't.
- **Don't use bun-only APIs** — Gradle builds with `npm run build`.
- **Don't conflate `react-infinite-scroll-component` with chat autoscroll** — the package IS used legitimately in `extended/infinite-scroll-area.tsx` for paginated lists; chat autoscroll uses `use-stick-to-bottom`. Don't remove the dep, but don't use it for chat message scrolling either.
- **Don't add `immer` or `zod` patterns** — both deps are unused and shouldn't grow.

## Troubleshooting

- Port 5173 conflict: `lsof -ti:5173 | xargs kill -9`
- API requests fail in dev: ensure Kotlin backend running on `:8080` (run `RouteActivity` from Android Studio, or start the app on a device/emulator — there is no `./gradlew :app:run` task for this Android app).
- Type errors: `bun run typecheck`, check `.react-router/types/`
- Build fails: `rm -rf node_modules .react-router build && bun install && bun run build`
