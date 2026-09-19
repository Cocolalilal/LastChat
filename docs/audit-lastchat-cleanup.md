# LastChat cleanup audit

**Status:** audit only. No app behavior changes in this branch.  
**Base:** `LastChat_dev` at `c9b67596` (`ci: disable automatic iOS Build on pull requests`).  
**Scope:** Android app (`:app` + related modules), web-ui, catalog, iOS leftovers that still affect Android backups.  
**Method:** code reading of delete/undo/backup/theme/motion/provider paths. Not a device QA pass.

Julian: two pick-lists. **§2 Deez Einer** is the visual north star and mapping table. **§3 Prioritized backlog** is engineering severity (P0–P3). Suggested later Cursor split is in **§13**.

---

## 1. Executive summary

Two tracks, do not collapse them:

- **Engineering P0** is still delete/undo that does not actually undo, plus restore that wipes files before it can fail. That work is not on Deez’s visual list, and it should land first so “one destructive pattern” is not built on a broken undo.
- **Deez Fix first** is tokens, blur-off charcoal surfaces, one irreversible-confirm pattern, and chat chrome sizes. Those are the visual P2s he wants treated as product-first, not nits.

Bing → Keyless and local-provider sync are largely landed on Android; leftovers are iOS/web-ui/catalog/docs, plus a TTS secret-delete gap.

**Do first if you want safety (engineering):**

1. Conversation delete: stop generation, tombstone, defer attachment/file/episode cleanup until undo expires.
2. Assistant (and lorebook) undo: align 4s wipe with 6s toast; cancel cleanup on undo.
3. Backup restore: do not `deleteRecursively` live dirs until DB/settings commit succeeds.

**Do first if you want Deez’s canvas (visual):** tokens (U9, N4, D1, D3) → blur-off solid charcoal (U8, D4) → one destructive confirm pattern (U1, S7, S12, S17) → chat chrome sizes (D2). Ask Julian before changing default blur, confirm-vs-undo semantics, or true-black / accent.

**Already landed on this tip (do not re-fix):**

| Change | Evidence |
|---|---|
| iOS Build is `workflow_dispatch` only | `.github/workflows/ios-build.yml` |
| Keyless is default search; Bing settings migrate to Keyless | `SearchServiceOptions.DEFAULT`, `Settings.normalizeSearchServices()` |
| Keyless UI import + Wikipedia HTML strip | `SettingSearchPage`, `KeylessSearchService` |
| Unread drawer dots skip the open chat; quieter intro UI | `completedGenerationIdsAfterDone`, `AssistantPromptSubPage` |
| Local provider create-when-installed / collapse duplicates / wipe-on-delete | `LocalModelSettingsSync`, `normalizeLocalProvider()`, `SettingVM.deleteProvider` |

**Stale docs vs code:** `AGENTS.md` still says Room v35 and “15 search providers … Bing”. Live DB is Room **v39**; search catalog has Keyless + Bing + 14 API providers.

There is **no `.lcvault` format**. Portable backup is `LastChat_backup_*.zip` (format v2) via `WebdavSync` / `BackupArchiveFormat`.

---

## 2. Deez Einer design brief

Source: Deez Einer (Julian’s designer). Visual north star for LastChat chrome. **Does not override engineering P0 data-loss work.** Still audit only — no app behavior changes in this branch.

### North star

True-black canvas, floating charcoal surfaces, extreme optical rounding, tasteful blur **when on**, **soft edges always**, floating chrome/composer, sparse copy, **same tokens/behaviours everywhere**. Motion: physics + ease, **never a parade**.

How the code sits against that today:

| North-star line | Code reality |
|---|---|
| True-black canvas | Already forced. `withLastChatAmoledSurface(dark)` sets `background`/`surface` to `Color.Black`. `rememberAmoledDarkMode()` is a no-op `true`. **Do not “fix” this into tonal Material dark** without Julian (breaking true-black). |
| Floating charcoal surfaces | Dark `surfaceContainer*` on presets are charcoal (e.g. Sakura dark `surfaceContainer = #261D1E`). Blur-off still leaks translucent glass on some chrome (U8, D4) — that fights “solid charcoal floating layers.” |
| Extreme optical rounding | `AppShapes` has 28/24/16 cards + inner optical helpers, but ~265 inline `RoundedCornerShape` and `LastChatComposerInputShape = 24.dp` vs `AppShapes.InputField = 20.dp` (U9, D3, U25). |
| Tasteful blur when on | `Blur.kt` + Haze is the right centralizer (dark glass α 0.34 / light 0.28). Problem is blur **off**, not blur on. |
| Soft edges always | No shared elevation/shadow/scrim token. Softness is ad-hoc `copy(alpha)` and 1.dp `outlineVariant` borders (`LastChatMenuButton`). |
| Floating chrome/composer | Menu / composer / + pill exist as floating surfaces, but sizes are not one token (D2: 40 / 44 / 48 / 56.dp). |
| Sparse copy | Loud `errorContainer` / `primaryContainer` banners (U17); hardcoded intro strings (N6); dual settings row systems (U15). |
| Same tokens/behaviours everywhere | Radius + type live in `ui-core` `LastChatDesignTokens.kt`. **No** `AppSpacing`, **no** charcoal surface recipe, **no** accent token object. web-ui radii drift (U25). |
| Motion: physics + ease, never a parade | NavHost policy is restrained. ~199 unguarded springs + `scaleIn(0.8)` list swap + 0.34 logo bounce = a parade (U12–U14, N5). Deez wants a **light** pass next, not a new motion system now. |

### Fix first (Deez)

1. **Design tokens — single source of truth** (radius / space / surface / type / accent); audit one-offs.  
   Tokens today: `AppShapes` + `buildLastChatTypography` in `LastChatDesignTokens.kt`. Missing: space, surface (charcoal floating layer + soft-edge), accent. One-offs: U9, N4, D1, D3, U25. **New accent → ask Julian.**
2. **Surfaces when blur OFF** — solid charcoal floating layers with a soft edge; **same opacity rules as blur-on** for composer / chrome / sheets / dialogs.  
   `blurredContainerColor` returns caller `fallback` unchanged when blur is off, so pre-alpha’d chrome stays glass (U8). Sheets/dialogs never go through that helper (D4). **Changing the default blur on/off → ask Julian.** Making blur-off *solid* is the brief, not a default change.
3. **Destructive confirms — one pattern for irreversible actions.**  
   Two patterns exist (AlertDialog vs swipe+undo toast). Chats are the dangerous outlier (S1/S2 plus no dialog). Mapping: U1, U2, U3, S7, S12, S17. **Switching “undo toast” → “always confirm” (or the reverse) is a semantics change → ask Julian.** Engineering still needs undo to actually restore files (S1/S3) whichever pattern he picks.
4. **Chat chrome consistency — menu / status / composer / + pill sizes.**  
   `LastChatMenuButton` default 48.dp; composer + pill 48.dp; composer send 56.dp; several `ChatPage` toolbar icons 44.dp; `topPillSize = 48.dp` hardcoded; `MinimalChatInput` also uses 40.dp. No `AppSize` token (D2).

### Fix next (Deez)

Intro / empty states (U16, N6, S24). Settings density (U15, U17). Light motion pass (U12, U13, U14, N5) — tame the parade, keep physics + ease, honor reduce-motion.

### Later (Deez)

Model picker sheets (`ModelList` / picker `ModalBottomSheet`s). Capability badges (`ModelAbilityTag` WARNING/INFO tags). Bot marks (assistant model icon via `ChatMessageV2` `showModelIcon` — not a dedicated token). Do not start these in the first visual wave.

### Ask Julian only

| Topic | Why it is a product call | Not a product call (just implement) |
|---|---|---|
| **Default blur on/off** | User toggle exists; flipping the factory default changes first-run feel | Making blur-off **solid charcoal** (U8, D4) |
| **Destructive semantics** | Confirm-always vs undo-toast-always vs “dialog for cascade, undo only when complete” | Using **one** pattern and fixing copy (U1); making today’s undo actually restore (S1, S3, S5) |
| **New accent / breaking true-black** | New accent token, character themes that tint the canvas, or a real AMOLED toggle that restores tonal dark (U10, U11) | Deduplicating radii/spacing onto existing tokens; keeping the black canvas |

### Deez priority mapping

Every audit ID → one Deez bucket. `—` means engineering/safety, not on his visual list.

| ID | Finding (short) | Deez bucket |
|---|---|---|
| **D1** | No `AppSpacing` / `AppSurface` / `AppAccent` token objects (radius+type only) | **Fix first** — tokens |
| **D2** | Chat chrome sizes 40/44/48/56.dp; no shared pill size token | **Fix first** — chat chrome |
| **D3** | `LastChatComposerInputShape` 24.dp vs `AppShapes.InputField` 20.dp | **Fix first** — tokens |
| **D4** | Sheets/dialogs skip `blurredContainerColor`; blur-off is not a shared charcoal recipe | **Fix first** — surfaces blur OFF |
| **D5** | Model picker sheets are one-off `ModalBottomSheet`s | **Later** — model picker sheets |
| **D6** | `ModelAbilityTag` WARNING/INFO capability chips | **Later** — capability badges |
| **D7** | Assistant “bot mark” is `showModelIcon`, not a token | **Later** — bot marks |
| U8 | Blur-off scroll FABs stay α 0.65 | **Fix first** — surfaces blur OFF |
| U9 | `AppShapes` vs ~265 inline `RoundedCornerShape` | **Fix first** — tokens |
| U25 | Android 20.dp input/bubble vs web-ui 24px | **Fix first** — tokens |
| N4 | No `AppSpacing`; `ExtendColors` almost unused | **Fix first** — tokens |
| U1 | Mixed Delete vs Confirm labels; several skips | **Fix first** — destructive confirms |
| U2 | Memory tool-sheet / imggen / fonts / embeddings: no confirm | **Fix first** — destructive confirms |
| U3 | Provider model swipe-delete: no AlertDialog | **Fix first** — destructive confirms |
| S7 | Character swipe-delete: no cascade confirm | **Fix first** — destructive confirms |
| S12 | WebDAV backup delete: no confirm | **Fix first** — destructive confirms |
| S17 | Zip restore: no overwrite confirm | **Fix first** — destructive confirms |
| U16 | `EmptyStateCard` underused; ImgGen placeholder empty | **Fix next** — intro/empty |
| N6 | Hardcoded EN intro copy | **Fix next** — intro/empty |
| S24 | web-ui still regenerates intros | **Fix next** — intro/empty |
| U15 | Dual settings row systems; unused pane descriptions | **Fix next** — settings density |
| U17 | Loud banners; unused quiet `DismissibleBannerCard` | **Fix next** — settings density |
| U12 | Reduce-motion barely applied outside NavHost | **Fix next** — light motion |
| U13 | Overbouncy 0.34/0.4 springs | **Fix next** — light motion |
| U14 | List `scaleIn(0.8)`; lateral settings slide with no fade | **Fix next** — light motion |
| N5 | Overlay/onboarding long springs; ActivityPill hard cuts | **Fix next** — light motion |
| U10 | Dark always forced OLED (matches true-black) | **Ask Julian** if breaking true-black; otherwise leave |
| U11 | Assistant palettes custom HSL vs M3 / canvas | **Ask Julian** — accent / character vs true-black |
| U24 | Default “Generical” is a full RP character | **Later** (adjacent to bot marks / system chrome) |
| S1 S2 S3 S4 S5 S6 | Delete/undo/restore data-loss cluster | **— engineering** (land before visual confirms on those surfaces) |
| S8 S9 S10 S11 S13 S14 S15 S16 | Orphans, WAL, backup completeness, random model UUIDs | **— engineering** |
| S18 S19 S20 S21 S22 S23 S25 | Regen truncate, web `content://`, TTS secrets, iOS Bing, crashes, unread | **— engineering** |
| U4 U5 U6 U7 | Workspace cascade, stale settings undo, plaintext zip, importers | **— engineering** |
| U18 U19 U20 U21 U22 U23 | Bing leftovers, catalog UUID, web-ui label, local TOCTOU, STT, edit-no-regen | **— engineering** |
| N1 N2 N3 N7 N8 N9 | Docs, dead APIs, catalog nits, AutoBackup, `getBalance` | **— engineering** |

---

## 3. Prioritized backlog (pick from this)

IDs are stable for later tickets. Effort: **S** hours-scale, **M** a focused PR, **L** multi-file / architectural. **Deez** column is the §2 bucket (`Fix first` / `Fix next` / `Later` / `Ask Julian` / `—`).

### P0 — data loss / restore corruption / silent undo failure

| ID | Area | Finding | Effort | Deez |
|---|---|---|---|---|
| S1 | Deletion / chats | Chat “undo delete” immediately orphans and deletes attachment files; `deleteFiles=false` is a lie | M | — |
| S2 | Deletion / chats | Deleting a generating chat does not stop generation; persist **re-inserts** the deleted row | S–M | — |
| S3 | Undo / characters | Assistant undo toast (6s) outlives hard wipe (4s); undo restores the shell, chats/memories already gone | S | — |
| S4 | Backup | Restore wipes managed file dirs **before** DB/settings commit; mid-failure is a mixed tree | L | — |

### P1 — cascade surprise, silent no-op, security, wrong data

| ID | Area | Finding | Effort | Deez |
|---|---|---|---|---|
| S5 | Undo / chats | Undo still clickable 4s–6s after grace; silent no-op | S | — |
| S6 | Deletion / chats | Web `DELETE /conversations/{id}` hard-deletes (no undo; same file cleanup) | M | — |
| S7 | Deletion / characters | Character swipe-delete has no confirm that all chats + memories will be wiped | S | Fix first |
| S8 | Deletion / characters | Selected `assistantId` / `recentlyUsedAssistants` not pruned on delete | S | — |
| S9 | Undo / lorebooks | Lorebook/entry undo does not cancel delayed file cleanup (4.5s) | S | — |
| S10 | Deletion / skills | Skill swipe never deletes on-disk packages (`filesDir/skills/…`) | S | — |
| S11 | Deletion / lorebooks & skills | Orphan `enabledLorebookIds` / `enabledSkillIds` left on assistants | S | — |
| S12 | Deletion / backup | WebDAV backup file Delete has **no** confirm | S | Fix first |
| S13 | Deletion / providers | Last LLM provider can be deleted; missing models become `Uuid.random()` and get persisted | M | — |
| S14 | Backup | `includesFiles=true` with empty staged dirs still `deleteRecursively`s live dirs | S | — |
| S15 | Backup | WAL checkpoint failure is logged-only; staged WAL unused on restore | M | — |
| S16 | Backup | Local LLM weights + MCP OAuth tokens are not in the zip | M (docs S) | — |
| S17 | Restore | Zip restore has **no pre-confirm** that current data will be overwritten | S | Fix first |
| S18 | Regenerate | User-message regenerate truncates later turns; confirmed, no undo snapshot | M | — |
| S19 | Web media | `/api/files/content` allows any `content://` authority | M | — |
| S20 | Secrets | TTS provider delete does not call `removeTtsProviderSecrets` | S | — |
| S21 | iOS search | iOS still offers Bing; Keyless gets an API-key field; backup fallback is Bing | S | — |
| S22 | Crash | `exportImage` / `saveMessageImage` force-unwrap `OutputStream?` / `getActivity()` | S | — |
| S23 | Crash | Failed fork returns empty `Conversation.ofId(Uuid.random())` and UI still navigates | S | — |
| S24 | Consistency | web-ui still offers regenerate on character intros | M | Fix next |
| S25 | Unread | Unread dots exist only in the Android drawer, in-memory, not persisted | M | — |

### P2 — UX / leftover / theming / motion

| ID | Area | Finding | Effort | Deez |
|---|---|---|---|---|
| U1 | Confirm | Mixed Delete vs Confirm labels; chats/assistants/WebDAV/imggen/fonts skip dialogs | S–M | Fix first |
| U2 | Deletion | Memory tool-sheet, generated images, custom fonts, unused-embeddings purge: no confirm | S | Fix first |
| U3 | Deletion | Provider model swipe-delete has no AlertDialog | S | Fix first |
| U4 | Deletion | Assistant delete does not offer workspace cleanup | S | — |
| U5 | Undo | Skills/lorebooks undo writes a stale full `settings` snapshot | S | — |
| U6 | Backup | Secrets stored plaintext in unencrypted zip; skipped zip files still “success” | M | — |
| U7 | Import | Chatbox/Cherry are merge-only; Chatbox drops providers with blank API key | S | — |
| U8 | Theme | Blur-off still translucent on chat scroll FABs (`alpha = 0.65f` fallback) | S | Fix first |
| U9 | Theme | `AppShapes.ListItem*` underused; ~265 inline `RoundedCornerShape` | M | Fix first |
| U10 | Theme | Dark always forced OLED; `rememberAmoledDarkMode()` is a no-op `true` | S–M | Ask Julian |
| U11 | Theme | Assistant palettes are custom HSL, not M3 tonal generation | M | Ask Julian |
| U12 | Motion | Reduce-motion barely applied outside NavHost (~199 springs, ~6 policy consumers) | M | Fix next |
| U13 | Motion | Overbouncy springs (0.34 / 0.4) vs AGENTS floor 0.5 / 0.6 | S–M | Fix next |
| U14 | Motion | Chat list preview↔normal `scaleIn(0.8)`; lateral settings slides have no fade | S | Fix next |
| U15 | Settings | Dual row systems (`FormItem` vs `SettingGroupItem`); pane `descriptionRes` unused | L | Fix next |
| U16 | Empty states | `EmptyStateCard` underused; ImgGen empty is a prompt placeholder | M | Fix next |
| U17 | Banners | Loud `errorContainer` / `primaryContainer` tips; `DismissibleBannerCard` unused | S | Fix next |
| U18 | Search leftover | Bing type/UI/strings/catalog still present after Keyless migration | S–M | — |
| U19 | Catalog | `kimi-k3` override `provider_ids` UUID is not in `providers[]` (Moonshot is `64a4b12c-…`) | S | — |
| U20 | web-ui | `SEARCH_SERVICE_LABELS` missing `keyless`, still has `bing_local` | S | — |
| U21 | Local LLM | Delete vs sync TOCTOU if wipe `runCatching` partially fails | M | — |
| U22 | STT | Dedicated ASR controllers mostly unwired; STT secret helpers dead | M | — |
| U23 | Edit | Message edit creates a new version and does not regenerate | S–M | — |
| U24 | System chrome | Default assistant “Generical” is a full RP character in overlay/widget/notifs | L | Later |
| U25 | Cross-platform | Android input/bubble radius 20dp vs web-ui 24px | S | Fix first |

### P2 — Deez-brief gaps (not in the original engineering pass)

| ID | Area | Finding | Effort | Deez |
|---|---|---|---|---|
| D1 | Tokens | `LastChatDesignTokens` has radius + type only — no space / charcoal-surface / accent objects | M | Fix first |
| D2 | Chat chrome | Menu 48 / composer + 48 / send 56 / toolbar 44 / input 40 — no shared pill size | S | Fix first |
| D3 | Tokens | `LastChatComposerInputShape` 24.dp vs `AppShapes.InputField` 20.dp (comment in composer says 24 was confirmed) | S | Fix first |
| D4 | Surfaces | Sheets/dialogs never use `blurredContainerColor`; blur-off has no shared solid-charcoal + soft-edge recipe | M | Fix first |
| D5 | Model picker | Picker sheets are one-off `ModalBottomSheet`s, not a chrome token | M | Later |
| D6 | Badges | `ModelAbilityTag` uses `TagType.WARNING`/`INFO` — later capability-badge pass | S | Later |
| D7 | Bot marks | Assistant identity is `ChatMessageV2.showModelIcon`, not a dedicated mark token | S | Later |

### P3 — nits / docs / dead code

| ID | Area | Finding | Effort | Deez |
|---|---|---|---|---|
| N1 | Docs | AGENTS.md: Room v35, Bing in search list, no Keyless | S | — |
| N2 | Dead API | `Context.deleteAllChatFiles()`, `checkFilesDelete`, `checkUserAvatarDelete` empty | S | — |
| N3 | Catalog | Reserved empty `models[]`; Keyless reuses SearXNG icon; unused `sttProviderIconUri()` | S | — |
| N4 | Spacing | No `AppSpacing` tokens; `ExtendColors` almost unused | L / M | Fix first |
| N5 | Motion | Overlay/onboarding long springs; ActivityPill hard cuts (likely intentional) | S | Fix next |
| N6 | Intro copy | Hardcoded English “Delete intro?” / “Add intro…” | S | Fix next |
| N7 | Backup | Android AutoBackup includes only `upload/`; not a full restore path | S | — |
| N8 | Provider | `Provider.getBalance` default `"TODO"` | S | — |
| N9 | Naming | `AndroidBingSearchClient` still named Bing while Keyless uses it as a backend | S | — |

---

## 4. Deletion flows

### 4.1 What is confirmed well

| Action | Pattern |
|---|---|
| LLM providers | `AlertDialog`; LiteRT copy warns that local models will be wiped |
| TTS / search / MCP | `AlertDialog` |
| Local LLM / Sherpa | Confirm before disk delete |
| Character intros | Confirm (hardcoded EN) |
| Memories (settings + timeline) | Confirm |
| Workspaces | Confirm; clears assistant `workspaceId`; deletes disk root |
| Chat storage files / orphan uploads / categories | Confirm |
| User message delete (truncates thread) | Confirm + undo toast |
| Web-ui conversation/message delete | `window.confirm` (permanent) |

### 4.2 Cascade map

| Delete A | Also deleted / cleaned | Left behind / orphaned |
|---|---|---|
| **Conversation** (Android) | DB row; episodic memories; attachment **refs**; unreferenced upload **files immediately** | Generation may continue and **re-insert** the row; undo restores row but files may be gone; workspace untouched |
| **Conversation** (web) | Same hard path, no undo | Same file cleanup |
| **Assistant** | Settings entry immediately; after 4s: all its chats (+ attachment cascade), core/episodic memories + embedding cache; avatar/background if unreferenced | `assistantId` / recents; linked workspace; global lorebooks/skills; tags |
| **Intro** | `presetMessages` / `alternateGreetings` only | — |
| **LLM provider** | Settings; secrets via `handleExplicitSecretDeletions`; LiteRT also wipes all local LLM+STT installs | Can leave zero providers; model IDs rewritten (possibly random UUID) |
| **TTS provider** | Settings; selection fallback | SecureStore TTS API key |
| **MCP server** | Settings; session; OAuth; assistant MCP ID refs filtered | — |
| **Lorebook / entry** | Settings; delayed media cleanup | `enabledLorebookIds` on assistants/conversations |
| **Skill** | Settings entry | On-disk `skills/skill-…`; `enabledSkillIds` |
| **Memory** | Row + embedding cache | — |
| **Workspace** | DB + disk; clears assistant `workspaceId` | — |
| **WebDAV backup** | Remote file only | No confirm |
| **Gen image / custom font** | DB + file | No confirm |

### 4.3 Findings

**S1 — P0 — Chat undo deletes attachment files immediately**  
`ChatService.deleteConversation` comments “Soft delete (DB only, preserve files)” and passes `deleteFiles = false`. `ConversationRepository.deleteConversation` **never reads** `deleteFiles`. It always:

1. Deletes the Room row  
2. `memoryRepository.deleteEpisodesByConversationId`  
3. `chatAttachmentRepository.removeConversationReferences` → `cleanupOrphans()` which **deletes unreferenced files under the chat upload dir**

Undo re-`insertConversation`s and `syncConversationAttachments`, but files may already be gone. `forgetDeletedConversation` only drops the in-memory snapshot — no deferred cleanup.

*Fix direction:* Defer ref removal + orphan cleanup + episode delete until the undo window expires; honor `deleteFiles` or remove it. Re-sync refs on undo without deleting files.  
*Evidence:* `ChatService.deleteConversation`, `ConversationRepository.deleteConversation`, `ChatAttachmentRepository.cleanupOrphans`.

**S2 — P0 — Generation re-inserts a deleted chat**  
`deleteConversation` never calls `stopGeneration`. Streaming checkpoints and completion call `persistConversationToRepository`, which **inserts** if the row is missing. Combined with S1, a generating chat can vanish, lose files, then reappear as a zombie without media.

*Fix direction:* `stopGeneration` + tombstone set; persist must refuse insert for recently-deleted IDs; cancel in-memory conversation state.  
*Evidence:* `ChatService.persistConversationToRepository` (~1028 insert-if-null), `stopGeneration` unused by delete, streaming persist ~1761.

**S7 — P1 — Character delete: swipe + undo toast, no cascade warning**  
`AssistantVM.removeAssistant` removes from settings immediately; after 4s deletes all memories and `deleteConversationOfAssistant` (which uses the same attachment-wipe path). UI: swipe / button + `assistant_deleted` toast. No dialog listing chat/memory impact.

*Fix direction:* Confirm dialog with counts; keep undo but make cascade explicit (and fix S3).  
*Evidence:* `AssistantVM.removeAssistant`, `AssistantPage`.

**S8 — P1 — Stale selected assistant**  
`removeAssistant` only filters `assistants`. Does not retarget `assistantId` or prune `recentlyUsedAssistants` / overlay / text-selection refs. Runtime `getCurrentAssistant()` falls back, but prefs keep dead UUIDs.

*Fix direction:* On delete, pick a remaining assistant (or default) and prune recents.  
*Evidence:* `AssistantVM.removeAssistant`.

**S10 / S11 — P1 — Skills and lorebooks leave orphans**  
Skill swipe removes settings + undo toast; never `SkillExportImport.deletePackage`. Lorebook/skill IDs are not stripped from `enabledLorebookIds` / `enabledSkillIds` (MCP IDs *are* filtered in `PreferencesStore`).

*Fix direction:* After undo window, delete skill packages; normalize assistant (and conversation override) ID sets when lorebooks/skills change.

**S12 — P1 — WebDAV backup delete has no confirm**  
`BackupItemCard` Delete → `vm.deleteWebDavBackupFile` immediately. Remote destructive.

*Fix direction:* `AlertDialog` before delete (restore is even more destructive — see S17).

**S13 — P1 — Last provider + random model UUIDs**  
`SettingProviderPage` `deleteEnabled = true` always. `clearMissingModelReferences()` uses `Uuid.random()` when no CHAT/IMAGE models remain (`DISABLED_MODEL_ID` is used only for embedding/suggestion). `Settings` field defaults are also `Uuid.random()`. Phantom IDs get persisted.

*Fix direction:* Block deleting the last provider (or restore built-ins); use `DISABLED_MODEL_ID` / null instead of random UUIDs.  
*Evidence:* `PreferencesModels.clearMissingModelReferences`, `Settings` defaults ~54–72, `ModelSelector` `findModelById(modelId ?: Uuid.random())`.

**U2 — P2 — Other missing confirms**  
Tool-call memory delete (`ChatMessageTools`), ImgGen image delete, custom font delete, “Delete unused embeddings” on `SettingChatStoragePage`. Other storage actions on that page *do* confirm.

**U4 — P2 — Assistant delete does not delete linked workspace**  
Workspace delete *does* clear assistant `workspaceId`. Reverse is not offered.

No factory-reset / “delete all chats” / bulk multi-select conversation delete found (not necessarily a bug).

---

## 5. Reversion / undo

### 5.1 Undo matrix

| Action | Undable? | Notes |
|---|---|---|
| Delete conversation (drawer) | Partial, ~4s | Toast lives 6s (`AppToast` default); files may already be gone (S1); 4–6s Undo is a silent no-op (S5) |
| Delete message (Android) | Yes, snapshot | User delete also has a confirm; assistant delete is toast-only |
| Delete assistant | Partial | Settings undo; data wiped after 4s (S3) |
| Delete skill | Settings only | Package files never deleted |
| Delete lorebook/entry | Settings yes | Files may still delete at 4.5s (S9) |
| Branch / version switch | N/A | Non-destructive `selectConversationTurnVersion` |
| Cancel edit | Draft only | Sent edit is a new version, no auto-regen (U23) |
| Regenerate assistant | Soft | Old versions kept |
| Regenerate user | **No** | Truncates later turns after confirm (S18) |
| Delete provider / LiteRt | **No** | Local models deleted from disk |
| Web-ui / API delete | **No** | `window.confirm` only |
| Context “revert summary” | **No** | Clears summary |
| Full zip restore | Overwrite | No undo except restoring another backup |
| Chatbox / Cherry import | Merge | Additive |

### 5.2 Findings

**S3 — P0 — Assistant toast outlives wipe**  
`AssistantVM` delays 4000ms then wipes memories/chats/files. `AppToast` default duration is 6000ms. `undoRemoveAssistant` only re-adds the assistant to settings. Clicking Undo after 4s looks successful.

*Fix direction:* Same grace as toast (or longer); cancel the wipe job on undo (job cancel exists — the mismatch is the duration); hide Undo after expiry; optionally keep data until grace ends.

**S5 — P1 — Conversation Undo 4–6s silent miss**  
Same 4000 vs 6000 mismatch. `undoDeleteConversation` returns quietly if the map miss.

*Fix direction:* Match durations; error toast on miss.

**S9 — P1 — Lorebook cleanup not cancelled**  
`cleanupFilesIfUnreferenced(..., delayMs = 4500L)` with no cancel token tied to undo.

**S18 — P1 — User regenerate is a hard truncate**  
`ChatService.regenerateAtMessage` for `USER` keeps `subList(0, indexAt + 1)` then generates. UI confirms (`chat_regenerate_user_message_warning`) but does not snapshot for undo. Assistant regenerate keeps versions.

*Fix direction:* Snapshot + undo toast, or branch like assistant turns.

**U5 — P2 — Skills/lorebooks undo can clobber concurrent edits**  
Undo uses a closed-over `settings.copy(...)` rather than `settingsStore.update { current -> … }` that only re-inserts the deleted item.

**U23 — P2 — Edit does not regenerate**  
`editMessage` appends a new version and updates `selectIndex`. Cancel only clears the draft. Users must regenerate manually.

Silent failures: undo after expiry (S5), persist insert after delete (S2), fork failure navigating to empty chat (S23), backup per-file zip errors only logged (U6).

---

## 6. Backup & restore

**Format:** zip v2 (`settings.json`, `backup_manifest.json`, portable DB, `prefs/*.json`, managed file dirs). **Not** `.lcvault`. No web-ui / Ktor backup routes.

### 6.1 Include / exclude

| Data | In zip? | Restore |
|---|---|---|
| Settings (assistants, providers, lorebooks, skills, MCP configs, search keys, …) | Yes | Overwrite via `settingsStore.update` after sanitize |
| Provider / TTS / WebDAV secrets | Yes, **plaintext in settings.json** | Re-migrated into SecureStore |
| MCP OAuth tokens | **No** | Re-auth required |
| Room portable tables | If DATABASE selected | Replace via `DatabaseSanitizer` |
| Managed files (`upload`, avatars, skills, chat_files, workspaces files, …) | If FILES selected | Wipe + mirror |
| Workspace Linux rootfs (`linux/`, `tmp/`) | **Excluded** (intentional) | Left local |
| Local LLM weights (`filesDir/local_models/`) | **No** | Not restored |
| Encrypted SecureStore file | **No** | Secrets only via settings.json |
| Android AutoBackup | `upload/` only (`backup_rules.xml`) | Not a full restore path |

Overwrite vs merge: LastChat zip = overwrite. Chatbox = merge providers (imported win) + append assistants + new conversation UUIDs. Cherry Studio = providers only. Chatbox `parseProvider` returns null if `apiKey` is blank.

### 6.2 Findings

**S4 — P0 — Non-atomic restore**  
`WebdavSync.restoreFromBackupFile` order: stage zip → parse settings → **`restoreManagedFileDirectories` (`deleteRecursively`)** → prefs → DB → `settingsStore.update`. A DB failure after file wipe leaves wiped files + old DB/settings.

*Fix direction:* Stage all, commit DB/settings first, then swap files; keep a backup of live dirs until success; or two-phase rename.

**S14 — P1 — Empty FILES restore wipes live dirs**  
If manifest `includesFiles` lists dirs but the archive has no content, live dirs are still deleted then `mkdirs()`.

*Fix direction:* Only wipe a dir if the staged copy exists (or is explicitly empty in the manifest).

**S15 — P1 — WAL**  
`checkpointDatabase()` is `runCatching` + warn. Zip may include WAL/SHM; restore sanitizes the main DB file only.

*Fix direction:* Fail backup if checkpoint fails, or apply WAL beside the staged DB before open.

**S16 — P1 — Incomplete portable restore**  
`MANAGED_FILE_DIRS` has no `local_models`. `populateSecretsForExport` does not export `mcp_oauth_*`.

*Fix direction:* Document exclusions in the backup UI; optionally include models / MCP OAuth.

**S17 — P1 — Restore has no “this will replace everything” confirm**  
WebDAV Restore and local import run immediately; success then forces restart (`BackupDialog`). Delete of a remote zip also has no confirm (S12).

**U6 — P2 — Plaintext secrets + silent skipped files**  
Intentional for portability, but the zip is unencrypted. `addManagedFileEntries` continues on per-file errors; UI still shows success. `DatabaseSanitizer` skip counts appear only on the post-restore restart dialog.

---

## 7. UI tokens / theming

**What’s solid:** `AppShapes` in `ui-core` (`LastChatDesignTokens`), Material You dynamic color (API 31+) + 6 presets, ~1583 `colorScheme` usages, `Blur.kt` + Haze for glass, web-ui `--lc-radius-*` for cards. Dark canvas is already true-black (Deez north star).

**Deez Fix first on this section:** D1, D2, D3, D4, U8, U9, N4, U25. U10/U11 are Ask Julian (true-black / accent).

**D1 — P2 — Token objects incomplete**  
`LastChatDesignTokens.kt` is radius (`AppShapes` / `Shapes`) + type (`buildLastChatTypography`). There is no `AppSpacing`, no charcoal-surface recipe (fill + soft edge + when to use `surfaceContainer` vs glass), no accent object. `ExtendColors` exists and is almost unused. web-ui has `--lc-radius-*` but not a shared space/surface contract with Android.

*Fix direction:* Add space / surface / accent tokens in `ui-core` next to `AppShapes`; migrate one-offs. **New accent values → ask Julian.**

**D2 — P2 — Chat chrome sizes are not one token**  
`LastChatMenuButton` default 48.dp. Composer + pill `Modifier.size(48.dp)`. Composer send `56.dp`. `ChatPage` toolbar icon buttons 44.dp. `topPillSize = 48.dp` local val. `MinimalChatInput` also uses 40.dp. Status/search field min height 56.dp. Deez wants menu / status / composer / + pill to share a size.

*Fix direction:* One `AppSize` (or similar) for floating chrome pills; composer send can be the same size as + unless Julian wants send heavier.

**D3 — P2 — Composer radius vs `AppShapes.InputField`**  
`LastChatComposerInputShape = RoundedCornerShape(24.dp)` with an in-file comment that 24 was user-confirmed. `AppShapes.InputField` is 20.dp. web-ui input/bubble is 24px (U25). Extreme rounding wants one number.

*Fix direction:* Pick 24 (composer+web) or 20 (token) as the single input radius; update the other. Small enough to do with tokens pass; don’t invent a third value.

**D4 — P2 — Sheets/dialogs have no blur-off charcoal recipe**  
`blurredContainerColor` is used by composer/chat chrome. ~67 `ModalBottomSheet`s and most `AlertDialog`s use M3 `surfaceContainerLow` / defaults and never go through the blur helper. Blur-off therefore cannot “use the same opacity rules as blur-on” for sheets/dialogs because there is no shared rule.

*Fix direction:* One surface helper: blur-on → glass α; blur-off → opaque charcoal `surfaceContainer` + soft edge. Apply to composer, toolbar, sheets, dialogs.

**U8 — P2 — Blur off still translucent**  
`blurredContainerColor` returns `fallback` unchanged when blur is off. `ChatList` scroll FABs pass `surfaceColorAtElevation(4.dp).copy(alpha = 0.65f)` — stays 65% opaque with no blur. `MinimalChatInput` / most of `ChatPage` pass opaque `surfaceContainer` and are fine.

*Fix direction:* Pass opaque charcoal as fallback; apply glass alpha only inside `blurredContainerColor` when blur is on. This is Deez “surfaces when blur OFF,” **not** a default-blur change.

**U9 — P2 — Shape tokens half-adopted**  
~318 `AppShapes.` vs ~265 `RoundedCornerShape(` in `:app`. `ListItemFirst/Middle/Last` exist; mainly `SettingMcpPage` uses them. Same 24/10 grouped-list shape copied across provider/search/TTS/local-LLM/onboarding. Worst inline counts: `AssistantMemorySubPage`, `MinimalChatInput`, `ChatPage`, `InputPickerSheets`. `QuickAskShapes` (32dp) sits outside the token set.

**U10 — P2 — Forced OLED (matches true-black; don’t silently revert)**  
`ColorScheme.withLastChatAmoledSurface(dark)` always sets `background`/`surface` to `Color.Black`. `rememberAmoledDarkMode()` always returns `true` with a no-op setter. `SettingDisplayPage` still declares unused `amoledDarkMode`. `AssistantChatTheme` also forces `AMOLED_DARK_BACKGROUND`. **This already is Deez’s true-black canvas.** Wiring a real toggle that restores tonal dark **breaks true-black → ask Julian.** Cleanup of the dead setter/UI is fine if the canvas stays black.

**U11 — P2 — Character themes vs Material You**  
`AssistantChatTheme.buildAssistantColorScheme` HSL-lerps a Palette seed onto primary/secondary/tertiary only; dark is still blacked out. Dynamic system scheme can be the base, then overwritten. **Tinting the canvas or adding a new accent → ask Julian.**

**U25 / N4 — P2–P3**  
Android input/bubble 20dp vs web 24px. ~329 `Color(0x…)` — mostly presets + `CodeColor.kt`; chat has decorative/status literals. No `AppSpacing`. `ExtendColors` ~11 call sites vs ~1583 `colorScheme`.

**D5 / D6 / D7 — Later (do not start in the first visual wave)**  
Model picker sheets: `ModelList` + `ModalBottomSheet` pickers. Capability badges: `ModelAbilityTag` (`TagType.WARNING`/`INFO`). Bot marks: `ChatMessageV2` `showModelIcon`.

---

## 8. Confirm dialogs & destructive actions

Deez: **one pattern for irreversible actions** (Fix first). Today there are two. Picking confirm-always vs undo-always (or hybrid) is **Ask Julian**. Do not invent a third pattern.

Two patterns dominate:

1. **AlertDialog** — providers, MCP, TTS, search, local models, workspace, storage, intros, many memory UIs, user-message delete/regen.  
2. **PhysicsSwipeToDelete + undo toast** — assistants, lorebooks, skills (provider *list* swipe only *requests* a dialog).

**Chats are the dangerous outlier:** no swipe, no dialog, undo toast, worst cascade (S1/S2). A visual “one pattern” on top of S1 is still data loss.

**U1 — P2 — Copy inconsistency**  
Provider confirm uses `R.string.delete`; TTS/search use `R.string.confirm` with title `confirm_delete`; intro uses `delete`; user regen uses `regenerate`; web-ui uses `window.confirm`.

*Fix direction:* After Julian picks semantics: one destructive pattern, title stating the object + impact, confirm labeled **Delete**, cancel labeled **Cancel**. Keep action-named confirms for regen. If undo stays, it must restore files (S1/S3/S5) — Deez’s pattern cannot ship on a fake undo.

| Action | Confirm? |
|---|---|
| Conversation delete (Android) | No dialog |
| Conversation delete (web) | `window.confirm` |
| Message delete (user, Android) | Yes + undo |
| Message delete (assistant, Android) | Undo only |
| Assistant delete | No dialog |
| Intro delete | Yes (hardcoded EN) |
| Provider delete | Yes (LiteRT stronger copy) |
| Provider model swipe | Swipe UI only |
| Local LLM / Sherpa | Yes |
| TTS / Search / MCP | Yes |
| Lorebook / skill swipe | Swipe + undo |
| WebDAV backup delete | **No** |
| Zip restore overwrite | **No** |
| ImgGen / custom font | **No** |
| Unused embeddings | **No** |
| Workspace / storage files | Yes |

---

## 9. Motion

Deez: **physics + ease, never a parade** — light pass is **Fix next**, not Fix first. NavHost is already closer to the north star than the micro-interactions.

**What’s solid:** `MotionPolicy` honors system animator/transition/window scale. Chat↔Menu fade 120/90ms. Hierarchical settings slide+fade. Wide settings pane↔pane `None`. Shared-element `HeroAnimation` uses `DampingRatioNoBouncy`. web-ui `chat-motion.ts` is more consistent than Android micro-interactions.

**U12 — P1 (a11y) — Reduce-motion barely applied outside NavHost**  
`LocalMotionPolicy` used in ~6 files (`RouteActivity`, `ChatPage`, `MenuPage`, `Background`, `Shimmer`, `AssistantDetailPage`). ~199 `spring(` calls elsewhere (`MinimalChatInput`, `ChatMessageV2`, `ConversationList`, overlay, onboarding, most settings).

*Fix direction:* `MotionPolicy.spring` / `snapOrSpring` helpers; gate micro-interactions and `AnimatedVisibility`.

**U13 — P2 — Overbouncy vs AGENTS.md**  
Documented: standard `0.5/400`, bouncy `0.6/300`. Observed: 84× `0.6`, 23× `0.5`, also 7× `0.4` (`ActivityPill`, crop, `ImgGenPage`) and `0.34` (`OnboardingPage` logo). Stiffness 120–1000 (`PhysicsSwipeToDelete` 1000f; overlay 120f). Fourteen distinct damping values.

**U14 — P2 — Heavy list / hard lateral edges**  
`ChatList` preview↔normal: `fadeIn + scaleIn(0.8)` without policy; `sharedBounds("conversation_list")` on the whole list. `lateralEnter/ExitTransition` is slide-only (no fade); hierarchical/root add slide+fade.

**N5 — P3**  
`ActivityPill` `EnterTransition.None` (reasonable for streaming churn). Overlay 1300ms entrance + infinite loops. Sheets (~67 `ModalBottomSheet`) use M3 defaults only.

---

## 10. Consistency

Deez **Fix next:** intro/empty (U16, N6, S24), settings density (U15, U17). Unread (S25) is engineering. Generical chrome (U24) is Later / adjacent to bot marks.

Recent fix `3286744a` (unread dots + quieter intros) is real on Android chat. Remaining gaps:

**S24 — P1 — web-ui still regenerates intros**  
Android: `shouldOfferMessageRegenerate` hides regen when `previousGroup == null`; overlay uses `isCharacterIntroMessage`. web-ui always passes `onRegenerate` into assistant turns (`chat-message.tsx`). Neither Android path hides regen for tool-only / image-only last turns.

**S25 — P1 — Unread is drawer-only and ephemeral**  
`completedGenerationIds` is `remember { }` in `ChatDrawerContent`. Menu recents, widget, web-ui sidebar have no equivalent. Sidebar badges are tags/generating, not unread. Dot uses `LocalContentColor` (can look decorative on a selected row).

**U15 — P2 — Settings row systems**  
Display / Web / Android Integration / assistant detail: `SettingGroupItem` + `SettingsGroup`. Search / Model / Skills / TTS / lorebook depth: `FormItem`. `SettingsPaneEntry.descriptionRes` exists and is always `null`. Confirm labels mixed (U1). Intro cycle row has no description. One hardcoded subtitle on Android Integration (“Choose where the glow wave comes from”).

**U16 — P2 — Empty states**  
`EmptyStateCard` used for provider models, local LLM, assistant skills/lorebooks, `ModelList`. Duplicated card empties: Search, MCP, TTS, lorebooks list, provider list. ImgGen: icon + prompt placeholder, no title.

**U17 — P2 — Banners that shout**  
Settings hub `ProviderConfigWarningCard` = full `errorContainer`. Memory/context `SummarizerModelTipBanner` = `primaryContainer`. Quiet `DismissibleBannerCard` is **defined in `SettingSkillsPage` and never called**. Skills/lorebooks compact tab selected icon uses `primaryContainer`.

**U24 — P2 — System assistant chrome**  
`DEFAULT_ASSISTANTS` name `"Generical"` with character system prompt and resource avatar. Overlay, widget, and spontaneous notifications use `assistant.name` / avatar like any RP character.

**N6 — P3 — Intro copy**  
Hardcoded EN: “Add intro…”, “Delete intro?”, “Cycle through intros…”.

---

## 11. Provider / catalog / search leftovers

### Already fixed on `LastChat_dev`

- Bing default → Keyless router (`KeylessSearchService` over DDG / Wikipedia / Bing HTML).
- `normalizeSearchServices()` converts `BingLocalOptions` → `KeylessOptions` (keeps id); tests exist.
- Bing removed from Android `SEARCH_SERVICE_PRESETS`.
- Catalog: Keyless `preset: true`, Bing `preset: false`.
- Local provider: create when installs exist, do not recreate when empty, collapse duplicates, delete wipes LiteRT+Sherpa with dialog.
- `SecretKeyManager` covers all current `ProviderSetting` types (OpenAI / Google / Claude / ComfyUI / LiteRtLocal).

`PlatformBingSearchClient` / `AndroidBingSearchClient` are **not dead** — Keyless still scrapes Bing as one backend.

### Remaining

**S20 — P1 — TTS secrets linger**  
`handleExplicitSecretDeletions` removes LLM provider secrets on id removal. TTS loop only clears keys when an *existing* provider’s `apiKey` is blanked. `removeTtsProviderSecrets` exists and is unused on delete.

**S21 — P1 — iOS still Bing-first in places**  
`IosSearchProviderType.BING` still selectable. API-key field gated with `!= BING`, so **Keyless also gets a key field**. Backup fallback `providerType ?: BING` (`IosBackupImport.kt` ~317).

**U18 — P2 — Bing leftovers**  
Type still in `SearchModels.TYPES`, `SearchService` dispatch, `SettingSearchPage` `when` (no-config copy), `WebDtos` `bing_local`, strings `bing_desc` / `setting_search_bing_no_config` / unused `setting_search_preset_bing_desc`, catalog `id: "bing"`.

**U19 — P2 — Dead catalog UUID**  
`model_overrides` `kimi-k3` `provider_ids: ["0ff8e630-f703-4f95-a2f2-95f0cf52b202"]` is not in `providers[]`. Moonshot is `64a4b12c-c820-4e12-b91c-2df5a3c10b42`. Override never matches `matchesProviderConstraints`.

**U20 — P2 — web-ui Keyless label**  
`search-picker.tsx` `SEARCH_SERVICE_LABELS` missing `keyless`; still has `bing_local`. Falls back to raw `"keyless"`.

**U21 — P2 — Local delete vs sync**  
`deleteProvider` uses `runCatching` per model. `withSyncedLocalProviderModels` recreates Local if `local == null && models.nonEmpty()`. Concurrent `SettingLocalLlmViewModel` sync vs wipe is a TOCTOU.

**U22 — P2 — STT stack half-wired**  
App STT builds OpenAI-compatible + Sherpa. DashScope / Volcengine / Realtime / MiMo / Step controllers unused from `:app`. `migrateStt*` / `populateStt*` never called; Settings has `sttModelId` on LLM providers, not a separate ASR list.

**N1 / N3 / N8 / N9 — P3**  
AGENTS.md search count and Bing. Keyless catalog icon = SearXNG. `sttProviderIconUri()` zero call sites. `Provider.getBalance` `"TODO"`. Client still named Bing.

---

## 12. Obvious bugs / crash risks (list only)

Verified by reading, realistic path:

1. **S2** — Generation persist re-inserts deleted conversation (data resurrection).  
2. **S1** — Immediate attachment file delete vs undo (data loss).  
3. **S3** — Assistant undo after 4s restores a hollow character.  
4. **S4 / S14** — Restore can wipe files then fail.  
5. **S13** — `Uuid.random()` model IDs persisted; picker looks up `modelId ?: Uuid.random()`.  
6. **S19** — `Uri.isAllowedWebMediaUri`: `"content" -> !authority.isNullOrBlank()` — any ContentProvider the app can open. Worse when web auth is off (`requireAuth = jwtEnabled`). `file://` is correctly jailed to filesDir/cacheDir; `android.resource` is package-jailed.  
7. **S22** — `ContextUtil` `outputStream!!`; `ChatUtil.saveMessageImage` `getActivity()!!`.  
8. **S23** — `forkConversationAtMessage` returns empty random conversation; `ChatPage` always navigates.  
9. **Concurrency** — `_generationJobs` / persistence-mode maps are read-modify-write on `StateFlow.value` (lost updates if two chats finish together).  
10. **Edit/delete message while generating** — neither path cancels the job; streaming can overwrite the edit.  
11. **Undo after resurrection** — `insertConversation` has no `REPLACE`; PK failure if S2 already re-inserted.  
12. **`EmojiUtils`** `json["emojis"]!!.jsonObject` — AGENTS.md forbids `!!` on JSON; crash if asset malformed (bundled, rare).  
13. **OpenAI embeddings** `jsonPrimitive.content.toFloat()` — `NumberFormatException` on non-numeric values.

Searched, **not** filed as live bugs: Room 1→39 migrations look registered; sealed `Screen` routes appear wired in `AppRoutes`; provider streaming JSON mostly uses `?.` / `contentOrNull`.

---

## 13. Recommended Cursor concurrency (later — do not start now)

Do **not** spawn a “fix everything” wave. Suggested split after Julian picks. Deez visual work is the **UI tokens** agent; motion stays optional/next.

| Agent | Owns | First tickets | Notes |
|---|---|---|---|
| **Safety** (1) | Delete/undo/backup/secrets/web files | S1, S2, S3, S4, S5, S9, S14, S19, S20 | Highest leverage. Land before visual “one destructive pattern” on those surfaces. |
| **Deez / UI tokens** (1) | Tokens, blur-off charcoal, confirms, chat chrome sizes | D1, D2, D3, D4, U8, U9, N4, U1, S7, S12, S17 | Deez **Fix first**. Do not retint the canvas or add accents (U10/U11) without Julian. Can run parallel to Safety if it does not touch `ChatService` delete. |
| **Motion** (1, optional, Deez **Fix next**) | Policy helpers, springs, list/lateral | U12, U13, U14, N5 | Light pass — physics + ease, never a parade. Skip until Fix first visual lands if slots are scarce. |

**2–3 agents is the right width.** A fourth (web-ui parity: S24, U20, confirms) only after Safety’s delete contract exists, or it will reimplement a second delete path.

Leave iOS Bing (S21), Generical chrome (U24), model picker / badges / bot marks (D5–D7) as later or explicit product calls.

**Ask Julian before the visual agent:** default blur change, destructive confirm-vs-undo semantics, new accent / breaking true-black.

---

## 14. Suggested first sprint (if Julian wants a default)

Two tracks. Do not mix “make undo real” with “retint the canvas.”

**Engineering (data):**

1. **S1 + S2 + S5** — one PR: conversation delete that actually undoes (stop generation, tombstone, defer files).  
2. **S3 + S7 + S9** — character/lorebook undo durations + confirm copy.  
3. **S4 + S14 + S17** — restore atomicity + overwrite confirm.

**Deez Fix first (visual), after or parallel if it doesn’t touch `ChatService`:**

4. **D1 + U9 + N4 + D3** — tokens (radius/space/surface/type; accent only if Julian says).  
5. **U8 + D4** — blur-off = solid charcoal floating layers, same helper for composer/chrome/sheets/dialogs.  
6. **U1 + S12 + D2** — one irreversible pattern (semantics from Julian) + chrome pill sizes.

Deez **Fix next** after that: U16/N6 (intro/empty), U15/U17 (settings density), U12–U14 (light motion). **Later:** D5–D7.

---

## Appendix A. Key symbols

| Symbol | Path |
|---|---|
| `ChatService.deleteConversation` / `undoDeleteConversation` / `persistConversationToRepository` | `app/.../service/ChatService.kt` |
| `ConversationRepository.deleteConversation` | `app/.../data/repository/ConversationRepository.kt` |
| `ChatAttachmentRepository.removeConversationReferences` / `cleanupOrphans` | `app/.../data/repository/ChatAttachmentRepository.kt` |
| `AssistantVM.removeAssistant` | `app/.../ui/pages/assistant/AssistantVM.kt` |
| `WebdavSync.restoreFromBackupFile` / `restoreManagedFileDirectories` | `app/.../data/sync/WebdavSync.kt` |
| `BackupArchiveFormat.MANAGED_FILE_DIRS` | `app/.../data/sync/BackupArchiveFormat.kt` |
| `Settings.clearMissingModelReferences` | `app/.../data/datastore/PreferencesModels.kt` |
| `SecretKeyManager.handleExplicitSecretDeletions` | `app/.../data/datastore/SecretKeyManager.kt` |
| `blurredContainerColor` | `app/.../ui/modifier/Blur.kt` |
| `AppShapes` / `buildLastChatTypography` | `ui-core/.../theme/LastChatDesignTokens.kt` |
| `LastChatComposerInputShape` | `ui-core/.../chat/LastChatComposerShell.kt` |
| `LastChatMenuButton` | `ui-core/.../nav/LastChatMenuButton.kt` |
| `ModelAbilityTag` | `app/.../ui/components/ai/ModelList.kt` |
| `MotionPolicy.lateralEnterTransition` | `app/.../ui/motion/MotionPolicy.kt` |
| `rememberAmoledDarkMode` | `app/.../ui/hooks/ColorMode.kt` |
| `Uri.isAllowedWebMediaUri` | `app/.../web/WebMedia.kt` |
| Web conversation delete | `app/.../web/WebApi.kt` `delete("/{id}")` |
| iOS Build | `.github/workflows/ios-build.yml` `on: workflow_dispatch` |

## Appendix B. Confirm-pattern cheat sheet

Use this when implementing U1 / Deez Fix first #3: **one pattern**. Candidate (if Julian agrees): dialog for irreversible / cascade, undo only when restore is complete (including files). Today several surfaces promise undo they cannot keep. **Do not switch confirm-vs-undo without Julian.**
