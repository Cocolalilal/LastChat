---
name: lastchat-catalog
description: >
  Use this skill whenever you need to add, edit, or review entries in the LastChat model catalog
  (catalog/lastchat_catalog.json). Covers the resolution pipeline, family/version/override
  hierarchy, modality rules, provider UUIDs, and step-by-step checklists for adding new
  models, families, and providers correctly.
---

# LastChat Catalog — Authoring Guide

The catalog (`catalog/lastchat_catalog.json`) is the source-of-truth for model metadata,
provider presets, and service provider registries. It is a **layered inheritance system**.
Always express metadata through the highest applicable layer — avoid repeating fields that
a parent layer already provides.

---

## 1. Top-Level Sections

| JSON key | Kotlin type | Purpose |
|---|---|---|
| `providers` | `List<CatalogProvider>` | Preset AI provider configs (base URL, auth type, icon, setup defaults) |
| `model_families` | `List<CatalogModelFamily>` | Pattern-based metadata inherited by all matching model IDs |
| `global_rules` | `List<CatalogModelRule>` | Cross-family rules evaluated before family matching |
| `model_overrides` | `List<CatalogModelOverride>` | Exact-ID entries; primarily bind model IDs to provider UUIDs |
| `search_providers` | `List<CatalogServiceProvider>` | Web search service registry |
| `tts_providers` | `List<CatalogTTSProvider>` | Text-to-speech provider registry |
| `stt_providers` | `List<CatalogServiceProvider>` | Speech-to-text provider registry |

Kotlin data models live in:
`app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelCatalog.kt`

---

## 2. Resolution Pipeline

For every model ID the app encounters, metadata is built up in this exact order.
**Each layer overrides the previous**:

```
1. global_rules     — pattern rules across all families (e.g. "embed" → EMBEDDING type)
2. model_families   — family defaults: type, icon, modalities, abilities, provider_slug
3.   └─ versions    — sub-patterns within a family that override specific fields
4. model_overrides  — exact ID + provider_id match; sets provider_ids and narrow corrections
```

The builder starts empty and each matched layer is applied with `applyRule` → `applyFamily`
→ `applyVersion` → `applyOverride`. A model is only resolved if at least one rule matched
(`hasMatchedRule = true`).

### Critical: Icon Resolution

`CatalogModelOverride` has **no `icon` field**. Icons are always inherited from the matched
`model_families` entry (set via `builder.iconUrl = matchedFamily.icon?.toCatalogIconUrl()`).

**Never put an icon in a `model_overrides` entry — it will be silently ignored.**

---

## 3. ModelType Values

| Value | Meaning | Auto input modality | Auto output modality |
|---|---|---|---|
| `CHAT` | Conversational LLM | `["TEXT"]` | `["TEXT"]` |
| `EMBEDDING` | Vector embedding model | `["TEXT"]` | `["TEXT"]` |
| `IMAGE` | Image generation | `["TEXT"]` | `["IMAGE"]` |
| `STT` | Speech-to-text transcription | `["AUDIO"]` | `["TEXT"]` |

`toModelTypeOrNull()` in `ModelMetadataResolver.kt` maps these strings:

| String | → ModelType |
|---|---|
| `"chat"` | CHAT |
| `"embedding"` | EMBEDDING |
| `"image"`, `"image_generation"` | IMAGE |
| `"stt"` | STT |

---

## 4. Modality Rules

- `"AUDIO"` must **only** appear in `input_modalities` of `ModelType.STT` entries.
- **Chat models must never have `"AUDIO"` in their `input_modalities`** — there is no
  audio-to-chat pipeline in the app.
- The UI modality selector only exposes `TEXT` and `IMAGE` for user-editable chat models.
- `"AUDIO"` in `output_modalities` is not used by any current feature.

---

## 5. Layer-by-Layer Authoring Guide

### 5.1 `model_families` — Define a family when models share a brand/architecture name

**Required fields:** `id`, `match_patterns`, `icon`, `type`
**Optional:** `input_modalities`, `output_modalities`, `abilities`, `provider_slug`, `versions`

```json
{
  "id": "whisper",
  "aliases": ["whisper"],
  "match_patterns": ["whisper", "distil-whisper"],
  "icon": "icons/openai.svg",
  "type": "STT",
  "input_modalities": ["AUDIO"],
  "output_modalities": ["TEXT"],
  "abilities": [],
  "provider_slug": "openai",
  "versions": []
}
```

`match_patterns` are regex patterns tested against the model ID and its canonical form.
Use anchors like `(^|[/._-])` and `(?=$|[:/._-])` to avoid false positives.

**Available icons:** All SVG files in `catalog/icons/`. Common ones:
`openai.svg`, `gemini.svg`, `claude.svg`, `groq.svg`, `deepseek.svg`, `mistral.svg`,
`meta.svg`, `qwen.svg`, `siliconflow.svg`, `dashscope.svg`, `deepinfra.svg`,
`fireworks.svg`, `together.svg`, `volcengine.svg`, `minimax.svg`, `zhipu.svg`,
`aihubmix.svg`, `novita.svg`, `gemma.svg`, `internlm.svg`, `baidu.svg`, `spark.svg`

---

### 5.2 `versions` (inside a family) — Override specific fields for model sub-variants

Use a version when a subset of models within a family differs in `type`, `input_modalities`,
`output_modalities`, `abilities`, or `reasoning_behavior`.

**Fields are nullable** — only set what differs from the family default:

```json
{
  "id": "gpt-transcribe",
  "match_patterns": ["gpt-.*-transcribe"],
  "type": "STT",
  "input_modalities": ["AUDIO"],
  "output_modalities": ["TEXT"],
  "abilities": []
}
```

```json
{
  "id": "gemini-image",
  "match_patterns": ["gemini.*image"],
  "type": "IMAGE",
  "image_generation_method": "multimodal",
  "input_modalities": ["TEXT", "IMAGE"],
  "output_modalities": ["IMAGE"],
  "abilities": []
}
```

Versions are matched in order — first match wins within a family.

---

### 5.3 `model_overrides` — Bind model IDs to provider UUIDs (and nothing else if possible)

The minimal correct form is **only three fields**:

```json
{
  "id": "whisper-large-v3",
  "canonical_model_id": "whisper-large-v3",
  "provider_ids": ["f9fe0a18-2b30-46e1-9c57-5562654e8d64"]
}
```

Only add extra fields when the model genuinely needs to differ from its matched family:

```json
{
  "id": "dall-e-3",
  "canonical_model_id": "dall-e-3",
  "provider_ids": ["8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91"],
  "type": "IMAGE",
  "image_generation_method": "diffusion",
  "input_modalities": ["TEXT"]
}
```

`api_aliases` can list alternate API IDs that map to the same canonical model:

```json
{
  "id": "gemini-2.5-flash-image",
  "canonical_model_id": "gemini-2.5-flash-image",
  "api_aliases": ["models/gemini-2.5-flash-image", "nano-banana"],
  "provider_ids": ["4e28ef61-8b96-4a1e-9f91-15b32ce6d886"]
}
```

---

### 5.4 `global_rules` — Cross-family rules applied to every model ID

Useful for patterns that span families, e.g. auto-classifying any model ID containing
`embed` as an embedding model:

```json
{
  "id": "global-embedding",
  "match_patterns": ["embed"],
  "type": "EMBEDDING",
  "input_modalities": ["TEXT"],
  "output_modalities": ["TEXT"],
  "abilities": []
}
```

---

### 5.5 `providers` — Preset AI provider configs

```json
{
  "id": "f9fe0a18-2b30-46e1-9c57-5562654e8d64",
  "name": "Groq",
  "description": "Ultra-fast inference for open models.",
  "type": "openai",
  "base_url": "https://api.groq.com/openai/v1",
  "icon": "icons/groq.svg",
  "preset": true
}
```

Provider `type` is one of: `"openai"`, `"google"`, `"claude"`.

Optional fields: `chat_completions_path`, `use_response_api`, `stream_options_mode`,
`image_response_modalities_mode`, `reasoning_content_replay_mode`, `balance_option`,
`signup_url`, `api_key_url`, `setup_recommended`, `setup_models`, `setup_defaults`.

---

### 5.6 `stt_providers` — Speech-to-text provider registry

These are displayed in the STT provider setup screen. They are **not** the same as
`providers` (which are full AI provider presets with UUIDs):

```json
{
  "id": "groq",
  "name": "Groq",
  "icon": "icons/groq.svg"
}
```

The `id` here is a slug used to match providers by name — not a UUID.

---

## 6. Key Provider UUIDs Reference

Always use these UUIDs in `provider_ids` arrays:

| UUID | Provider |
|---|---|
| `8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91` | OpenAI |
| `4e28ef61-8b96-4a1e-9f91-15b32ce6d886` | Google AI Studio |
| `f9fe0a18-2b30-46e1-9c57-5562654e8d64` | Groq |
| `448c27de-c5e0-4434-8efa-932d0613dd9b` | DeepSeek |
| `5a4e6046-8309-40a0-a294-2e621e876f4d` | Mistral |
| `c7e2b7da-a579-4a94-9b24-9b24bfa1e9ad` | DeepInfra |
| `8e9f2910-c114-411a-b302-18cb92193b2a` | Fireworks AI |
| `b38e1192-5b47-4d2a-a163-f622a2eaf717` | SiliconFlow |
| `c5e85cbb-8e28-454c-96d0-0585750d081c` | AiHubMix |
| `5e729975-9794-4013-af7e-63a548cf3356` | Alibaba Cloud (DashScope / Qwen) |
| `d2eb13cb-8c0a-4885-9e74-f4b56c8d5b24` | Volcengine Ark |
| `bf741ca1-57d6-4444-93ff-18305c43d9b4` | Together AI |
| `e4fce9f0-b4d2-453f-90b6-e1c5b615269c` | Zhipu AI |
| `391d9f85-7824-4c19-9697-09b1f2a33e3b` | Anthropic Claude |
| `ce456892-f6bb-4598-bbb5-2658fe0d2955` | InternLM |

To find a UUID for a provider not listed above, search for `"name": "<ProviderName>"` in
`catalog/lastchat_catalog.json`.

---

## 7. Existing STT Model Families

These families are already defined and auto-classify matching model IDs as STT:

| Family ID | `match_patterns` | Icon |
|---|---|---|
| `whisper` | `["whisper", "distil-whisper"]` | `openai.svg` |
| `sensevoice` | `["sensevoice", "FunAudioLLM"]` | `siliconflow.svg` |
| `paraformer` | `["paraformer"]` | `dashscope.svg` |
| `gpt` → `gpt-transcribe` version | `["gpt-.*-transcribe"]` | (inherits `openai.svg` from `gpt` family) |

If a new STT model's name contains `whisper`, `distil-whisper`, `sensevoice`,
`FunAudioLLM`, or `paraformer`, **no new family is needed** — add a minimal override only.

---

## 8. Anti-Patterns ❌

| Wrong | Correct |
|---|---|
| Adding `type`/`icon`/`modalities` to an override when a family already defines them | Minimal override: `id` + `canonical_model_id` + `provider_ids` only |
| Putting `"icon"` in a `model_overrides` entry | Icons come from the family — `CatalogModelOverride` has no icon field |
| `"AUDIO"` in `input_modalities` of a CHAT model | Only STT-type models should have `"AUDIO"` input |
| Creating a new family for a single model with no naming siblings | Use a lean override, or a `versions` entry in the closest existing family |
| Leaving out `canonical_model_id` in an override | Always set it — it's used for deduplication and alias resolution |

---

## 9. Step-by-Step Checklists

### Adding a new STT model

1. **Check if a family already matches** the model ID:
   - `whisper*`, `distil-whisper*` → `whisper` family ✓
   - `gpt-*-transcribe` → `gpt` family + `gpt-transcribe` version ✓
   - `sensevoice*`, `FunAudioLLM/*` → `sensevoice` family ✓
   - `paraformer*` → `paraformer` family ✓
2. If matched: add **only** `{ "id", "canonical_model_id", "provider_ids" }` to `model_overrides`.
3. If not matched: create a new `model_families` entry (type STT, icon, match_patterns), then step 2.
4. **Never** add `"AUDIO"` to any chat model.

### Adding a new chat model family

1. Add to `model_families`: `id`, `match_patterns`, `icon`, `type: "CHAT"`, `input_modalities: ["TEXT"]`.
2. Add `"IMAGE"` to `input_modalities` only if the family is genuinely vision-capable.
3. Add `versions` for meaningful sub-variants (vision, reasoning, embedding, image-gen, STT).
4. Add `model_overrides` entries only for models that need `provider_ids` binding.
5. Update `updated_at` date at the top of the catalog file.

### Adding a new provider preset

1. Generate or reuse a UUID for the `id` field.
2. Add to `providers` array with `name`, `description`, `type`, `base_url`, `icon`, `preset: true`.
3. Add the UUID to relevant `model_overrides` entries' `provider_ids` arrays.
4. If the provider supports STT, add a corresponding entry to `stt_providers` (slug-based, no UUID).
