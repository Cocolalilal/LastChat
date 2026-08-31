# Group Chat Implementation Plan (v2)

> Revised from Gemini's initial plan after codebase audit.
> Scope: Android app only (`:app` + `:ai` module). Web-ui deferred.

## Feature Summary

Add "groups" — a special type of Assistant that bundles multiple regular Assistants as members. In a group chat:

- **Manual mode (default):** A horizontal bar of member pills sits above the chat input (where quick-message suggestions normally live). Clicking a member pill sends the message and triggers a reply from that specific character.
- **Auto mode:** An "Auto" toggle (first pill in the row, off by default) activates the Host Model. When on, the host model uses a tool call to pick which member(s) reply. The other member pills fade out. When off, pills reappear.
- Characters can respond to each other. The chat history is formatted as `Name: message` so each member sees prior messages from other members.
- Quick-message suggestions are suppressed in group chats (the member bar takes their place).

## Design Decisions (resolved)

| Question | Decision | Rationale |
|---|---|---|
| Which member sent a message? | New `UIMessageAnnotation.Sender(val assistantId: Uuid)` | `UIMessageAnnotation` is the extensible annotation system already on `UIMessage` in `:ai`. No app-domain leak. Backward-compatible (old messages just have empty annotations). |
| Host model orchestration | Tool call: `select_member(memberId: String)` | User confirmed tool-call approach. Check `ModelAbility.TOOL` on the host model; if unsupported, fall back to JSON `{"memberId": "..."}`. |
| Who picks the speaker? | Auto toggle in the member bar | User-confirmed UI. Auto OFF = user clicks a member. Auto ON = host model decides via tool call. |
| Member context loading | Each member's full `buildMessages` pipeline runs with its own lorebooks, skills, memory, transformers, etc. The group's `hostInstructions` are prepended to context. | Keeps member individuality. |
| Memory in groups | Group conversations skip `MemoryConsolidationWorker` and `SpontaneousWorker`. Members' individual memory systems are not updated from group chats. | Groups are social contexts, not individual memory sources. Avoid polluting member memories. |
| TTS in groups | Use the replying member's `ttsVoiceId` if set, else group's, else global default. | Per-member voice identity. |
| Orphaned members | Runtime filtering: skip missing member IDs with a Log.w. No hard delete guard (too annoying). | Simple and non-blocking. |
| Web-ui | Out of scope for this phase. Group chats are Android-only. Web-ui shows them as normal conversations (last member's avatar/name). | The React SPA needs its own separate implementation. |
| Auto mode chain | Host can trigger multiple members in sequence (chain), up to a configurable max (default 5). Both host can stop naturally AND user can interrupt with a stop button. | Matches the "group chat feel" where characters talk to each other. |
| Chain stop signal | Host calls `select_member` with `memberId: "none"` to signal completion. User can also press a stop button at any point. | Two stop paths: model-driven and user-driven. |
| Group avatar in lists | Show all members' avatars stacked (like a party icon). Group's own `avatar` field is an override option. | Visual identity at a glance. |
| Chain repeats | Host CAN pick the same member multiple times in a row. No hard alternation rule. | Lets the host model decide what makes sense conversationally. |
| Chain rendering | Each member's reply is a separate bubble, appended in sequence. Not bundled as a turn group. | Feels like a real group chat — messages flow in. |
| Regeneration | Per-member. Regenerating a specific member's reply only regenerates that one message. Other members' replies are preserved. | Each reply is an independent `MessageNode`, so this maps naturally. |
| Attachments in group | Always include images/files/audio for all members. Rely on existing OCR fallback for non-multimodal models. | Simple, consistent. The OCR pipeline already handles this. |

---

## Data Model Changes

### `Assistant.kt` — add group fields

```kotlin
// In data class Assistant(...)
val isGroup: Boolean = false
val memberIds: List<Uuid> = emptyList()        // ordered — controls pill order
val hostModelId: Uuid? = null                   // model used for speaker selection
val hostInstructions: String? = null           // system prompt for the host model
val groupMaxChain: Int = 5                       // max member replies per user message in auto mode
```

All fields have defaults → backward compatible with existing DataStore serialization. No migration needed.

**Guard:** `AssistantVM.removeAssistant()` must warn (not block) if the assistant is a member of any group. Add a `groupsReferencing(assistantId)` helper to surface this in the UI as a confirmation dialog.

### `MessageUtils.kt` (`:ai` module) — new annotation type

```kotlin
// In sealed class UIMessageAnnotation
@Serializable
@SerialName("group_sender")
data class GroupSender(
    val assistantId: String  // UUID as string for serialization portability
) : UIMessageAnnotation()
```

This is the only `:ai` module change. The app layer reads it to render the correct avatar/name.

### `Conversation.kt` — no changes

`Conversation.assistantId` already points to the group Assistant. No structural changes needed. `MessageNode` is unchanged — the sender identity lives in `UIMessage.annotations`.

---

## UI Changes

### 1. Assistant List Page (`AssistantPage.kt`)

**File:** `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt`

Add a `SegmentedButton` (or `FilterChip` row) below the search bar to switch between **Characters** and **Groups** tabs.

- **Characters tab (default):** existing list, filtered to `!isGroup`
- **Groups tab:** list filtered to `isGroup`, same card layout but showing member avatars stacked
- FAB `+` in groups tab creates a new group Assistant (opens `GroupDetailPage`)

### 2. Group Detail Page (`GroupDetailPage.kt` — NEW)

**File:** `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/GroupDetailPage.kt`

A simplified config page (NOT a copy of `AssistantDetailPage` — groups need far fewer settings). Sections:

1. **Profile:** Name, Avatar (group icon)
2. **Members:** Reorderable list of member Assistants (select from `!isGroup` assistants). Uses `rememberReorderableLazyListState` like `AssistantPage` already does. Shows each member's avatar + name. Add/remove via a picker sheet.
3. **Host Model:** Model picker (reuse `ModelSelector` from `ModelList.kt`). Field for `hostInstructions`.
4. **Group UI settings:** Background, etc. (subset of `AssistantUISettings`).

Route: `Screen.GroupDetail(id: String)` — new `@Serializable data class` in `RouteActivity.kt`'s `Screen` sealed interface, alongside `Screen.AssistantDetail`.

### 3. Chat Input — Member Bar (replaces suggestions)

**File:** `app/src/main/java/me/rerere/rikkahub/ui/components/ai/MinimalChatInput.kt`

In the `Column` inside `MinimalChatInput`, where `ChatSuggestionsRow` is currently rendered (line ~575-613):

```kotlin
val isGroupChat = assistant?.isGroup == true
val showSuggestions = !isGroupChat && !isQuestionnaireActive && !isToolApprovalActive && chatSuggestions.isNotEmpty()
val showMemberBar = isGroupChat

if (showMemberBar) {
    GroupMemberBar(
        members = groupMembers,      // List<Assistant> resolved from memberIds
        autoMode = autoMode,
        onToggleAuto = { autoMode = !autoMode },
        onSelectMember = { member -> /* trigger reply from this member */ },
        modifier = Modifier.fillMaxWidth()
    )
} else {
    // existing AnimatedVisibility for suggestions + scroll-to-bottom
}
```

`GroupMemberBar` is a new composable in the same file (or a new `GroupMemberBar.kt`):

- Horizontal scrollable `Row` of pills
- First pill: "Auto" toggle. Off = gray outline. On = highlighted/filled. When on, member pills animate out (fade + shrink). When off, they animate back in.
- Member pills: avatar on the left, name on the right. Clicking triggers a reply from that member.
- Animated transitions using `AnimatedVisibility` + `animateFloatAsState` (same patterns already used in `ChatSuggestionsRow`)

**New state needed:**
- `autoMode: Boolean` — remember-saveable, per conversation
- `targetMemberId: Uuid?` — null in auto mode (host decides), set when user clicks a pill

### 4. Chat Page — Message Rendering

**File:** `app/src/main/java/me/rerere/rikkahub/ui/components/chat/ChatMessageV2.kt`

In `AssistantMessageTurn` (line ~1373), the `assistant` and `model` params are used for avatar/name:

```kotlin
// Current (line ~1433):
val avatarName = assistant?.name?.ifEmpty { null } ?: model?.displayName ?: defaultAssistantName
val avatarValue = assistant?.avatar ?: Avatar.Dummy
```

Change to: extract sender from message annotations:

```kotlin
val senderAssistantId = group.firstNode()?.currentMessage?.annotations
    ?.filterIsInstance<UIMessageAnnotation.GroupSender>()
    ?.firstOrNull()?.assistantId
    ?.let { Uuid.parse(it) }

val senderAssistant = senderAssistantId?.let { settings.getAssistantById(it) }
val effectiveAssistant = senderAssistant ?: assistant  // fallback to group

val avatarName = effectiveAssistant?.name?.ifEmpty { null } ?: model?.displayName ?: defaultAssistantName
val avatarValue = effectiveAssistant?.avatar ?: Avatar.Dummy
```

This way, each message bubble shows the replying member's avatar and name.

### 5. Assistant Picker Sheet (`AssistantPicker.kt`)

**File:** `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AssistantPicker.kt`

Add a segmented control at the top: **Characters | Groups**. Filter the list accordingly. When a group is selected, it works the same as selecting an assistant (conversation's `assistantId` points to the group).

---

## AI Orchestration Changes

### `ChatService.kt` — group dispatch

**File:** `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

`handleMessageComplete()` (line ~1430) is the main generation entry point. Currently it:
1. Resolves `assistant` and `model` from `conversation.assistantId`
2. Calls `generationHandler.generateText(...)` in a loop (for tool calls)
3. Collects chunks, updates conversation

For groups, we intercept **before** step 2:

```kotlin
// In handleMessageComplete(), after resolving conversationContext:
val assistant = conversationContext.assistant
if (assistant.isGroup) {
    handleGroupMessageComplete(
        conversationId = conversationId,
        conversation = conversation,
        groupAssistant = assistant,
        settings = settings,
        suppressCompletionNotification = suppressCompletionNotification,
    )
    return
}
// ... existing single-assistant flow continues
```

### New: `handleGroupMessageComplete()`

In `ChatService.kt` (or a new `GroupChatHandler.kt` — recommend separate file for clarity):

```
handleGroupMessageComplete(conversationId, conversation, groupAssistant, settings, ...):
    1. Resolve members: memberIds.map { settings.getAssistantById(it) }.filterNotNull()
       If empty, emit error and return.

    2. Format chat history as multi-user transcript:
       For each message in conversation.currentMessages:
         - If role == USER: "User: <text>"
         - If role == ASSISTANT:
             val senderId = annotation<GroupSender>?.assistantId
             val senderName = settings.getAssistantById(senderId)?.name ?: "Assistant"
             "$senderName: <text>"

    3. Determine speaker:
       a. If targetMemberId != null (manual mode — user clicked a member pill):
            speaker = members.find { it.id == targetMemberId }
       b. If autoMode == true:
            - Build host prompt: systemPrompt = groupAssistant.hostInstructions
              + formatted transcript + list of available members (id + name)
            - Call generationHandler.generateText() with host model + a local tool:
                Tool("select_member", params: { memberId: String })
            - Parse tool call result → speakerId → speaker
            - Fallback: if host model doesn't support tools, parse JSON from response text
            - Fallback: if host model picks an invalid member, use first member
       c. If autoMode == false AND targetMemberId == null:
            - This shouldn't happen (user must click a member). Return.

    4. Generate the actual reply:
       - Use speaker's chatModelId (or group's, or global default)
       - Build messages using speaker's full pipeline:
           generationHandler.generateText(
               settings = settings,
               model = speakerModel,
               messages = conversation.currentMessages,  // but see below
               assistant = speaker,  // ← speaker's config (systemPrompt, lorebooks, etc.)
               memories = retrieveMemories(speaker),  // speaker's memories
               tools = buildConversationTools(settings, speaker, conversation, speakerModel),
               ...
           )
       - BUT: the message history needs to be formatted so the speaker sees other members' names.
         Two approaches:
         a. Transform messages: inject `GroupSender` name into each assistant message's text
            prefix (e.g., "[Programmer]: Here's the code..."). This is an InputMessageTransformer.
         b. Use the group's systemPrompt to explain the multi-user format, and format
            each message with `Name: text` in a custom transformer.

       RECOMMENDATION: Use approach (a) — a new `GroupChatInputTransformer : InputMessageTransformer`
       that reformats the conversation history into a multi-user transcript before sending
       to the speaker's model. This keeps the speaker's entire pipeline (lorebooks, skills,
       memory, etc.) intact and just changes how history is formatted.

    5. Attach annotation:
       The generated UIMessage gets a GroupSender annotation:
           message.copy(annotations = message.annotations + UIMessageAnnotation.GroupSender(speaker.id.toString()))

    6. Chain loop (auto mode only):
       After first member replies:
       a. Re-run host model with updated transcript (includes the new reply)
       b. Host calls select_member again:
          - If memberId == "none" → chain complete, stop
          - If memberId == valid member → generate that member's reply, loop back to (a)
          - If memberId == invalid → fallback to first member, loop back to (a)
       c. Stop conditions:
          - Host returns "none"
          - Chain length reaches maxChain (default 5, configurable in group settings)
          - User presses stop button (cancels the generation job)
       In manual mode: only the selected member replies (one shot, no chain).

    7. Suggestions are suppressed in group chat (member bar takes their place).
```

### New: `GroupChatInputTransformer`

**File:** `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/GroupChatInputTransformer.kt`

```kotlin
class GroupChatInputTransformer : InputMessageTransformer {
    // For each UIMessage in the history:
    //   - If role == ASSISTANT and has GroupSender annotation:
    //       prefix text with "[SenderName]: "
    //   - If role == USER: prefix with "[User]: "
    //   - If role == ASSISTANT without GroupSender (e.g., pre-group messages): leave as-is
    // This makes the conversation read like a group chat transcript to the model.
}
```

### New: Host Model tool

In `GenerationHandler` or `GroupChatHandler`:

```kotlin
val selectMemberTool = Tool(
    name = "select_member",
    description = "Select which group member should reply next.",
    inputSchema = InputSchema(
        properties = mapOf(
            "memberId" to mapOf("type" => "string", "description" => "UUID of the member to speak")
        ),
        required = listOf("memberId")
    )
)
```

The host model receives: system prompt (hostInstructions) + formatted transcript + member list + this tool. It must call the tool to select a speaker.

---

## Worker / Background Handling

### `SpontaneousWorker`
Skip groups entirely:
```kotlin
if (assistant.isGroup) return Result.success()  // groups don't send spontaneous messages
```

### `MemoryConsolidationWorker`
Skip groups:
```kotlin
if (assistant.isGroup) return Result.success()  // no memory for groups
```

### `ChatService.generateSuggestion()`
Suppress for groups:
```kotlin
if (assistant.isGroup) return  // no suggestions in group chat
```

### TTS
In the TTS playback logic (wherever `ttsVoiceId` is resolved), check for `GroupSender` annotation:
```kotlin
val senderId = message.annotations.filterIsInstance<GroupSender>().firstOrNull()?.assistantId
val ttsVoiceId = senderId?.let { settings.getAssistantById(it)?.ttsVoiceId }
    ?: assistant.ttsVoiceId  // group's voice
```

---

## Web-UI

Out of scope. Group conversations will appear as normal conversations in the web UI, using the group's avatar/name for all messages. A TODO comment should be left in `web-ui/app/components/` for future implementation.

---

## Files Changed Summary

| File | Change |
|---|---|
| `data/model/Assistant.kt` | Add `isGroup`, `memberIds`, `hostModelId`, `hostInstructions` fields |
| `ai/src/main/java/me/rerere/ai/ui/MessageUtils.kt` | Add `UIMessageAnnotation.GroupSender` |
| `ui/pages/assistant/AssistantPage.kt` | Add Characters/Groups tab switch |
| `ui/pages/assistant/detail/GroupDetailPage.kt` | **NEW** — group config page |
| `ui/components/ai/MinimalChatInput.kt` | Replace suggestions with `GroupMemberBar` in group mode |
| `ui/components/chat/ChatMessageV2.kt` | Resolve sender from `GroupSender` annotation for avatar/name |
| `ui/components/ai/AssistantPicker.kt` | Add Characters/Groups segmented control |
| `service/ChatService.kt` | Add `handleGroupMessageComplete()` dispatch |
| `data/ai/transformers/GroupChatInputTransformer.kt` | **NEW** — formats history as multi-user transcript |
| `data/ai/GenerationHandler.kt` | Add `select_member` host tool (or in new `GroupChatHandler.kt`) |
| `RouteActivity.kt` | Add `Screen.GroupDetail` route |
| `service/SpontaneousWorker.kt` | Skip groups |
| `service/MemoryConsolidationWorker.kt` | Skip groups |

---

## Testing Plan

### Unit Tests

1. **Serialization round-trip** (`AssistantSerializationTest.kt`):
   - Deserialize an old `Assistant` JSON (pre-group fields) → all group fields default correctly
   - Serialize → deserialize a group Assistant → fields preserved

2. **GroupChatInputTransformer test**:
   - Given messages with `GroupSender` annotations, verify output is `"[Name]: text"` format
   - Given messages without annotations (pre-group), verify they pass through unchanged

3. **GenerationHandler group test** (`GenerationHandlerGroupTest.kt`):
   - Mock host model returns `select_member` tool call with a valid memberId
   - Verify the correct member's `generateText` is called with their config
   - Mock host model returns invalid memberId → verify fallback to first member
   - Mock host model without tool support → verify JSON parsing fallback

4. **Existing tests still pass**:
   - `GenerationHandlerTest.kt`
   - `GenerationHandlerSkillToolTest.kt`
   - `GenerationHandlerOcrPlaceholderTest.kt`
   - `ConversationContextSettingsTest.kt`

### Manual Verification

1. Create a group with 2-3 members, set host model
2. Open chat, verify member bar appears (suggestions gone)
3. Auto OFF: click a member → verify that member replies with their name/avatar
4. Auto ON: send message → verify host model picks a member → verify reply
5. Auto ON: verify host can trigger multiple members in sequence
6. Verify TTS uses the replying member's voice
7. Verify memory consolidation doesn't run on group conversations
8. Verify spontaneous messages don't fire for groups
9. Delete a member that's in a group → verify group still works (member skipped)
10. Verify non-group conversations are completely unaffected (regression)

---

## Implementation Order

1. **Data model** — `Assistant.kt` fields + `UIMessageAnnotation.GroupSender`
2. **GroupDetailPage** — config UI (so we can create groups to test with)
3. **AssistantPage** — tab switch (so groups are visible)
4. **AssistantPicker** — segmented control (so groups are selectable in chat)
5. **GroupChatInputTransformer** — history formatting
6. **ChatService.handleGroupMessageComplete()** — core orchestration
7. **GenerationHandler** — host model tool
8. **MinimalChatInput** — `GroupMemberBar` replacing suggestions
9. **ChatMessageV2** — sender-aware avatar/name rendering
10. **Worker guards** — skip groups in spontaneous/consolidation
11. **TTS** — per-member voice
12. **Tests** — unit + manual
13. **Web-ui** — TODO comments only
