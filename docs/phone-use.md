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
  collapse before the activity yields to the target app. Completion or a pending
  approval brings the same conversation back into the assistant UI; automatic
  speech follows its configured setting, once per reply. If Android blocks the
  return, the shared transcript is available in the service window instead.
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
  so scrolling and selecting the reply do not move the window.

A single PhoneUseManager coordinates ownership across app, assistant, and web
conversations. Competing conversations cannot operate the phone concurrently.
Actions wait for the service's stop controls to attach. Gestures pass through the
visible window without detaching/recreating it; screenshots alone briefly hide it,
with flags restored in a finally block. Any started LastChat activity hides the
service window, including translucent activities, to avoid duplicate avatars.
System-assistant and attached popup replies own completion delivery and suppress
the duplicate generation notification. Approvals retain task ownership even when
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
