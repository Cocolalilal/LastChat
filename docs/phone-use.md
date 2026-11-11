# Phone use

Phone use is opt-in per character under **Tools → Phone use**. It requires Android
11 or later and **LastChat Phone use** enabled in Android accessibility settings.
The setup disclosure explains that screen contents go to the selected model.
No draw-over-other-apps permission is needed: the accessibility service owns the
controls. The service reads the screen only when a tool requests it.

Supported actions: observe, screenshot, tap, long press, swipe, scroll, type,
press a system key, open/list apps, wait, and finish. Text-only models can use element
indexes; screenshots use LastChat's existing private model-inspection pipeline.
The tool retains upstream's self-operation and blocked payment/banking-app checks.
The blocked package list is a fixed upstream list, not comprehensive detection.

## Controls and assistant integration

- Regular chats get a draggable character avatar with a stop button. Tap the
  avatar to expand the action label, conversation shortcut, and pause control.
- Tasks started from the system assistant use a full-width bottom assistant
  surface using the assistant's actual transcript header. The transcript and input
  collapse before the activity yields to the target app. Completion expands the
  full assistant interface, including its message input, over the live app. If Android
  blocks that return, the floating transcript provides its own follow-up input and
  transfers any draft when the assistant opens. Automatic speech follows the
  assistant setting, once per reply.
  Pending approvals return to the assistant's existing input/approval UI. The drag
  handle moves the expanded service window; compact controls can be dragged by
  their row. The resize handle fades and collapses during phone use, leaving a
  56 dp capsule, and returns with the transcript. Status labels crossfade; the
  reply has an explicit dismiss control.
- Opening the conversation pauses phone use. For system assistant conversations,
  resuming yields the assistant activity again. The conversation, character,
  tool approvals, questions, and history continue through the existing ChatService.
- The floating controls expand when a tool needs a reply/approval. The conversation
  shortcut returns to the existing approval/question UI.
- Animated avatars preserve shape, eyes, colors, and traits, using the normal
  generating pose. System reduced-motion settings stop avatar motion. Image,
  resource, emoji, and letter avatars use a display-only renderer suitable for a
  service window. Their generating shape slowly cycles through rounded variants.
- Pause blocks the next tool action; an already-dispatched Android gesture may
  finish. Stop cancels the owning conversation's generation. Disconnecting the
  accessibility service also stops the task. Completion keeps a dismissible
  reply with Markdown and a follow-up input. Dragging is restricted to the header,
  so scrolling and selecting the reply do not move the window. Throws preserve
  finger velocity and spring toward the nearest side or corner, keeping a safe
  gap from system bars. Reduced motion docks immediately. The minimized control
  is a capsule with concentric avatar and outer curves; nested reply fields use
  an inset radius. Completed messages appear immediately when expanded.
- Transcript resizing uses one critically damped height spring, and direct dragging
  follows the finger. The rounded surface and scrolling viewport share that height;
  there is no second content-size animation or streaming-driven resize. Input
  remains composed and slides through the bottom screen edge. Window handoff waits
  for both panel and input to settle, and return starts from the same compact pose.
  Cancelling work retains that surface and restores its input.
- Normal popup replies are laid out at their final width while their surface morphs,
  so paragraphs do not rewrap on every animation frame. Completed replies have no
  persistent status label. The compact popup is a 108 × 56 dp capsule.
- Native drag updates are coalesced once per display frame and use raw screen touch
  coordinates. Resizing retargets an in-flight throw without resetting its velocity.
  Scrolling back stops automatic following until the user returns to the latest
  text. The assistant input and phone controls retain bottom breathing room.

A single PhoneUseManager coordinates ownership across app, assistant, and web
conversations. Competing conversations cannot operate the phone concurrently.
Actions wait for the service's stop controls to attach. Gestures pass through the
visible window without detaching/recreating it. On Android 14+, model screenshots
capture the target window directly and keep controls visible. Images are aligned to
screen coordinates so window insets do not shift the model's grid. Older Android
versions (or a window that has just closed) briefly hide controls for a display
screenshot; flags are restored in a finally block. Secure-window failures do not
fall back to capturing the display. Screenshot buffers are released after encoding. Any started LastChat activity hides the
service window, including translucent activities, to avoid duplicate avatars.
An assistant return keeps its matching compact service surface until the activity's
first pre-draw, rather than removing it at activity start before it can draw.
A moved assistant bar springs back to that shared anchor before the return starts.
Regular popups fade in after the app yields. Dismissing a popup fades its retained
content before releasing its window. The dialog preserves hardware acceleration
when focus/touch flags change. Both assistant hosts use the character's theme;
palette seeds are retained across the handoff to avoid reloading the default colors.
System-assistant and attached popup replies own completion delivery and suppress
the duplicate generation notification. Toast decorations are constrained to the content
size so an empty host cannot turn a popup into a full-screen touch region. Drag bounds
use the usable window frame and are clamped again after resizing or rotation.
Approvals retain task ownership even when
there is no active generation job. The tool rechecks character access before every
action.

The phone tool asks the model to verify the outcome and call `finish` before its
final reply. An unclosed task or missing reply gets one additional generation
checkpoint; it cannot retry forever, and it must not repeat completed actions.
Model/provider behavior still determines successful task execution.

## Validation

- `:app:compileStableDebugKotlin`
- `:app:testStableDebugUnitTest --tests '*PhoneUseToolTest' --tests '*PhoneUseManagerTest' --tests '*PhoneUseContinuationTest'`
- `:app:testStableDebugUnitTest --tests '*PhoneUseControlsRenderTest' -Dlastchat.renderDir=/tmp/phone-use`
- `:app:testStableDebugUnitTest --tests '*AssistantTransitionTest' --tests '*PhoneOverlayDragGestureTest'`
- `npm run typecheck` and `npm run build` in `web-ui`

On a physical Android 11+ device, verify accessibility onboarding/revocation,
rotation, dragging, screenshot exclusion, target-app typing, stopping during a
request, and system-assistant pause/reopen/resume. Check both avatar eye styles,
static avatars, light/dark themes, large text, and reduced motion. Android OEM
window/gesture behavior needs device validation beyond JVM tests.

The action executor and accessibility controller are adapted from RikkaHub at
`90269e39dcf1beabf3066139c03f29162ebb082b`, under the repository's AGPL-3.0 license.
LastChat's task ownership, controls, assistant continuation, settings, result
conversion, and avatar integration are implemented locally.

## Live assistant window

The system assistant uses a fixed transparent activity with bottom-anchored panels.
Tapping outside the panels dismisses it. Compose resizing and message streaming do
not resize the platform window. A microphone bar appears while the conversation is
being created; opening does not wait for database I/O or stacked window/Compose
animations. Phone-use service popups retain their separate wrap-content windows.
On Android 12+, supported devices use native background blur and blur-behind on
that fixed activity. The accessibility-owned assistant uses a floating Android dialog with native
background blur limited to its window, rather than blurring the target app fullscreen.
It supplies the same translucent surface theme. Both follow LastChat's
existing blur setting. Changes to the OS blur capability are observed (for example, battery saver). The blur request
stays installed while the OS temporarily disables it; redundant state callbacks
cannot trigger window relayouts. Availability and requested radius are recorded in
app diagnostics. Other devices
keep the live app visible around readable assistant surfaces. Accessibility is not
required for native window blur, and does not provide cross-app pixels to Compose's
Haze renderer.

Assist screenshots are retained only at model-inspection resolution for
`look_at_screen` and optional context attachment. They are never displayed behind
the assistant, and no capture loop is used for visual effects. Accessibility-owned
phone-use windows keep the target app active during work. Completion returns to
the transparent assistant host, with a floating reply/input as a fallback.

Release deliveries build stable APKs only (`:app:assembleStableRelease`); the
stable ARM64 APK goes into the shared LC folder.
