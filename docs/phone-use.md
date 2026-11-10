# Phone use

Phone use is opt-in per character under **Tools → Phone use**. It requires Android
11 or later and **LastChat Phone use** enabled in Android accessibility settings.
The setup disclosure explains that screen contents go to the selected model.
No draw-over-other-apps permission is needed: the accessibility service owns the
controls. The service reads the screen only when a tool requests it.

Supported actions: observe, screenshot, tap, long press, swipe, scroll, type,
press a system key, open/list apps, and wait. Text-only models can use element
indexes; screenshots use LastChat's existing private model-inspection pipeline.
The tool retains upstream's self-operation and blocked payment/banking-app checks.
The blocked package list is a fixed upstream list, not comprehensive detection.

## Controls and assistant integration

- Regular chats get a draggable character avatar with a stop button. Tap the
  avatar to expand the action label, conversation shortcut, and pause control.
- Tasks started from the system assistant use a full-width bottom assistant
  surface, sharing the same controls with the assistant activity. The activity
  yields to the target app; there is no second floating avatar bubble.
- Opening the conversation pauses phone use. For system assistant conversations,
  resuming yields the assistant activity again. The conversation, character,
  tool approvals, questions, and history continue through the existing ChatService.
- The floating controls expand when a tool needs a reply/approval. The conversation
  shortcut returns to the existing approval/question UI.
- Animated avatars preserve shape, eyes, colors, and traits, using the normal
  generating pose. System reduced-motion settings stop avatar motion. Image,
  resource, emoji, and letter avatars use a display-only renderer suitable for a
  service window.
- Pause blocks the next tool action; an already-dispatched Android gesture may
  finish. Stop cancels the owning conversation's generation. Disconnecting the
  accessibility service also stops the task. Completion keeps a dismissible
  conversation shortcut.

A single PhoneUseManager coordinates ownership across app, assistant, and web
conversations. Competing conversations cannot operate the phone concurrently.
Actions wait for the service's stop controls to attach. Controls yield during
screenshots and gestures and restore visibility in cancellation paths. The tool
rechecks character access before every action.

## Validation

- `:app:compileStableDebugKotlin`
- `:app:testStableDebugUnitTest --tests '*PhoneUseToolTest' --tests '*PhoneUseManagerTest'`
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
