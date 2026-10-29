# LastChat Feature Roadmap & Studio Architecture

This document tracks the active development, architectural specs, and implementation progress of the **Studio** creative suite, **Voice Memos** (native recorder replacement + agent memory bridge), **Story Studio** (chained novel engine), and the **Chat Composer Smart Mic Gesture**.

---

## 🗺️ Master Status Overview

| Component | Status | Target Milestone | Description |
|---|---|---|---|
| **Studio Hub** | 🟡 In Progress | v1.5.0 | Central creative launcher in the drawer (Imagine, Voice Memos, Story Studio). Group Chat deferred. |
| **Voice Memos: Audio Engine** | 🟡 In Progress | v1.5.0 | Full device voice recorder replacement writing directly to `/sdcard/Recordings/LastChat/`. |
| **Voice Memos: STT & Intelligence** | ⚪ Planned | v1.5.0 | Whisper/Aqua STT transcription, smart titles, summaries, bullet points, and action items. |
| **Workspace & Agent Memory Bridge** | ⚪ Planned | v1.5.0 | Transcripts synced to `/workspace/transcripts/` with `index.json` catalog injected into AI memory. |
| **Chat Composer: Slide Mic Profiles** | ⚪ Planned | v1.5.1 | Hold-and-slide gesture on mic button to select post-processing prompts (Clean, Bullets, Polish, Translate). |
| **Story Studio: Core State Machine** | ⚪ Planned | v1.5.2 | Architect model for Story Bible + autonomous sequential beat writer with rolling context compactor. |
| **Multi-Character Group Chat** | ⏸️ Deferred | v1.6.0 | Orchestrate multi-agent roleplay in a single shared room. |

---

## 1. Studio Hub (`StudioPage.kt`)
- [x] Integrate Studio into Chat Drawer navigation (`Screen.Studio` replacing standalone Imagine button).
- [x] Responsive layout with OneUI top app bar, Charcoal surface ladder, and zero emojis.
- [ ] Remove "NEW" badges and "Group Chat" card for now (clean 3-tool grid: Imagine, Voice Memos, Story Studio).
- [ ] Bind "Recent Activity" shelf to live local storage repositories instead of hardcoded previews.

---

## 2. Voice Memos — Native Device Recorder Replacement & Intelligence Hub

### A. Recording & Device Storage
- **Public Storage Directory:** Recorded files save directly to `/sdcard/Recordings/LastChat/<timestamp>.m4a` via Android `MediaRecorder` or `AudioRecord` (high-quality AAC, 44.1kHz / 16kHz, 128kbps mono).
- **Default Landing UI:** 
  - **Hero Record Card (Top):** Instant "Ready to Record" state with one-tap start, pause, resume, discard, elapsed timer, and live waveform amplitude visualizer.
  - **Recordings Library (Bottom):** Chronological list of all recordings found in `/sdcard/Recordings/LastChat/` with duration, file size, and transcription status.
- **Playback Controller:**
  - Tapping a recording opens the floating player dock with seek scrubber, $\pm 15$s jump, speed multiplier (`1.0x`, `1.25x`, `1.5x`, `2.0x`).

### B. STT Transcription & AI Intelligence Tools
- **Model Selection:**
  - **STT Model Selector:** Select remote Whisper (Aqua, Groq, OpenAI) or local on-device Sherpa ONNX.
  - **Processing LLM Selector:** Select model for summaries and analysis (e.g., DeepSeek, Claude, GPT).
- **Actions on Audio:**
  - `Transcribe`: Generates timestamped karaoke-synced text.
  - `Smart Title`: Replaces raw filename with 3-5 word AI title.
  - `Summarize`: 2-sentence executive summary.
  - `Bullet Points`: Key discussion points.
  - `Action Items`: Extracted tasks.
  - `Send to Story Studio`: Exports transcript into new story outline.

### C. Workspace & Agent Memory Bridge
- **Destination:** Transcripts and metadata auto-sync to `/workspace/transcripts/`.
  - File structure: `/workspace/transcripts/<id>.json` + `/workspace/transcripts/<id>.md`.
- **Catalog Index:** Maintained at `/workspace/transcripts/index.json` containing `[ { id, title, date, duration, summary, path } ]`.
- **Agent Memory Injection:**
  - Agent system instructions include a lightweight index table: *"Voice memos catalog available in `/workspace/transcripts/index.json`. Use file tools to inspect transcripts when relevant."*
  - Zero token bloat: Full transcripts are read on demand, only titles and dates sit in the index.

---

## 3. Chat Composer: Hold & Slide Smart Mic Gesture (`MinimalChatInput.kt`)

### A. Settings & Profile Manager Location (`SettingModelPage.kt`)
- Accessed directly via the **Settings gear icon (⚙️)** next to `STT Providers` on the **Default Models** page.
- Opens the **Smart Mic Profiles Manager**:
  - Create unlimited custom profiles.
  - Configure **Profile Name** (e.g., "Clean & Fix", "Bullet Points", "Prompt Polish", "Translate to English", "Raw Dictation").
  - Assign a **Dedicated Text Model** per profile via `ModelSelector(type = ModelType.CHAT)` (e.g., DeepSeek V4.1 Flash, Gemini 3.8 Flash, Claude).
  - Customize the **Post-Processing Prompt** per profile.
  - Reorder, edit, or delete profiles with persistent storage in DataStore.

### B. Gesture Interaction
- **Short Tap:** Normal STT dictation into chat input.
- **Hold / Long-Press:** Activates a floating profile pill strip above the mic button.
- **Slide While Holding:** Sliding finger over a profile highlights it; releasing locks in the selection and starts recording with that profile.

### C. Default Preset Profiles
1. **Raw / Verbatim:** Emits raw STT output with zero LLM modification (instant).
2. **Clean & Fix:** Specifically tuned prompt that fixes voice dictation mangles (e.g., "silly tervan" -> "SillyTavern", "the boat" -> "the bot", "delete my issue" -> "read my issue"), fixes punctuation, removes stuttering, and preserves authentic voice.
3. **Bullet Points:** Formats rambling spoken thoughts into crisp, structured bullet points.
4. **Prompt Polish:** Transforms a spoken conceptual idea into a structured, production-ready LLM prompt.
5. **Translate to English:** Auto-detects spoken multilingual speech (Urdu/Hindi/etc.) and outputs fluent, natural English text.

---

## 4. Story Studio: Autonomous Chained Novel Engine (`StoryStudioPage.kt`)

### A. Two-Phase Pipeline
1. **Phase 1: The Story Bible (Architect Model):**
   - User inputs premise $\rightarrow$ Architect model generates structured JSON: Characters, Setting/Rules, and a Beat Outline (e.g., 6-10 milestone beats).
2. **Phase 2: Chained Sequential Writer (Prose Model):**
   - Autonomous background loop executing beat-by-beat.
   - Context window per beat: `[Story Bible] + [Rolling summary of previous beats] + [Exact last 500 words of previous beat] + [Current beat objective] + [User steer note]`.
   - Auto-saves chunks into `/workspace/stories/<id>/`.

### B. UI & Controls
- **Manuscript Tab:** Book-formatted reading canvas with chapter headers and beat dividers.
- **Story Bible Tab:** Live editable character sheets and beat outline (tweak future beats while current beat generates).
- **Generation Dock:** Pause, resume, skip beat, word counter, and mid-flight steering input.
