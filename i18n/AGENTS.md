# AGENTS.md — i18n tool

Standalone TypeScript TUI tool (Bun + React 19 + Ink 6) that scans Android `strings.xml` files across configured modules and uses AI (via Vercel AI SDK 5) to translate missing entries. Manages translations for the main LastChat Android app.

## Stack

- TypeScript 5.9.2 (strict), ESNext modules, bundler resolution
- React 19.1.1 + Ink 6.1.0 (terminal UI)
- Vercel AI SDK 5.0.8 — **actually used**: `@ai-sdk/google` and `@ai-sdk/openai-compatible` (via `createOpenAICompatible`). `@ai-sdk/openai` is declared in `package.json` but **NOT imported anywhere** — don't add new code depending on it; either use `@ai-sdk/openai-compatible`'s `createOpenAICompatible` or remove the unused dep.
- xml2js 0.6.2 (Android string resources), yaml 2.8.1 (config), dotenv 16.4.5
- Bun (runtime + package manager); `tsx` for dev, `tsc` for build

## Commands

```bash
bun run dev    # Start interactive TUI (recommended) — runs tsx src/index.tsx
bun run build  # Compile to dist/ via tsc
bun start      # Run compiled dist/index.js
```

## Configuration (`config.yml`)

```yaml
# Target languages (Android resource directory suffixes)
targets:
  - ar           # Arabic (RTL)
  - b+zh+Hans    # Simplified Chinese (BCP-47 form)
  - ja           # Japanese
  - zh-rTW       # Traditional Chinese
  - ko-rKR       # Korean
  - ru           # Russian

# Workspace root relative to i18n directory
workspaceRoot: ".."

# Android modules to scan for string resources
# (Non-existent modules are skipped with warnings)
modules:
  - app
  - search

# Concurrent translation requests (optional, default 1)
concurrency: 4

# AI provider configuration
provider:
  type: openai                    # "google" | "openai" (NOT "openai-compatible" — see warning below)
  model: moonshotai/kimi-k2-0905
```

**⚠️ Provider type caveat**: `getModel()` in `src/translator.ts` only handles `"google"`/`"gemini"`/`"openai"`. Setting `type: "openai-compatible"` will throw `Unsupported provider: openai-compatible` at runtime, even though `@ai-sdk/openai-compatible` is installed. To add real `openai-compatible` support, extend the `switch` in `getModel()`.

**Only `app` and `search` are currently configured** — strings in `:tts`/`:speech`/`:highlight`/`:document`/`:workspace` are NOT auto-translated.

## Environment (`.env`)

```env
# For Google Gemini
GOOGLE_GENERATIVE_AI_API_KEY=...

# For OpenAI or OpenAI-compatible (e.g. Moonshot, DeepSeek, etc.)
OPENAI_API_KEY=...
OPENAI_BASE_URL=https://...    # optional, for OpenAI-compatible providers
```

## File paths

- Default strings: `{modulePath}/src/main/res/values/strings.xml`
- Localized strings: `{modulePath}/src/main/res/values-{locale}/strings.xml`
- Config: `config.yml`
- Logs: `logs.txt` (auto-generated)
- Package: `package.json`
- TS config: `tsconfig.json` (strict)
- Entry: `src/index.tsx`
- **`src/xml-parser.ts`** — core XML parse/write/merge module (107 lines). Used by `module-loader.ts` and `translator.ts` to read/write `strings.xml`. Not previously mentioned here; documented in `i18n/README.md`.

## Translation workflow

1. Scans configured modules for `strings.xml` files
2. Compares default strings with existing translations
3. Calculates completion statistics per module/language
4. AI-translates missing entries with context (module + key information)
5. Preserves Android formatting (`%1$d`, `%1$s`, `\n`, `\'`)
6. Saves to appropriate `values-{locale}/strings.xml` files
7. 100ms rate-limiting delays between requests; error handling with **fallback to original text (NO retry)** — failed translations are kept as the source string and logged to `logs.txt`, but not re-attempted; comprehensive logging to `logs.txt`

## TUI navigation

- **Module selection**: ↑↓ navigate, Enter select, shows completion progress
- **Language selection**: ↑↓ navigate, Enter select, shows translation statistics
- **Translation table**: ↑↓ navigate, `e` edit, `t` translate missing, `f` filter missing, `q` back
- **Edit mode**: type to edit, Enter save, Esc cancel

## Supported languages

Current targets (full names provided to AI for context):
- `ar`: Arabic (العربية)
- `b+zh+Hans`: Simplified Chinese (简体中文)
- `zh-rTW`: Traditional Chinese (繁體中文)
- `ja`: Japanese (日本語)
- `ko-rKR`: Korean (한국어) — **⚠️ latent bug**: `LANGUAGE_NAMES` in `src/translator.ts` only has the key `ko` (NOT `ko-rKR`), so `getLanguageName("ko-rKR")` returns the raw locale string `"ko-rKR"` instead of `"Korean (한국어)"`. Fix by adding `'ko-rKR': 'Korean (한국어)'` to the map (or rename the `ko` key to `ko-rKR`).
- `ru`: Russian (Русский)

Add new languages to the `LANGUAGE_NAMES` mapping in `src/translator.ts` and to `targets` in `config.yml`. **Verify the `LANGUAGE_NAMES` key exactly matches the `targets` entry** (e.g. `ko-rKR`, not `ko`) or the AI will receive the raw locale string instead of the language name.

## Code style

- Strict TypeScript
- ESNext modules, bundler resolution
- React functional components with hooks
- Proper error handling and logging

## Adding a new AI provider

Extend `getModel()` in `src/translator.ts` and add configuration options to support additional Vercel AI SDK providers.

## Module detection

Auto-scans for modules containing `src/main/res/values/strings.xml`. Non-existent modules are skipped with warnings.

## Error handling

- File permissions: check write access to Android module directories
- API rate limits: built-in 100ms delays between requests (**NO retry logic** — failed translations fall back to the original source string and are skipped, not re-attempted)
- Missing translations: filter functionality to focus on incomplete items
- API key issues: verify `.env` config and quota

## What NOT to do

- **Don't add `ai`/`highlight`/`rag`/`tts`/`speech`/`document`/`workspace` to `modules`** unless you intend to translate their strings — only `app` and `search` are configured by default.
- **Don't assume `rag` is a module** — it doesn't exist (it's a package inside `:app`).
- **Don't submit new target languages** to the Android app without also updating `LANGUAGE_NAMES` in `src/translator.ts` and the `values-*` directories in `:app`.
- **Don't edit translated `strings.xml` files by hand** while a TUI session is running — they may be overwritten on save.
