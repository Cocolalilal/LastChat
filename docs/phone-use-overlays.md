# Phone-use overlay motion

On Android 13 and later, the accessibility service owns a full-screen, transparent native
window. `FloatingOverlayViewport` keeps its native dimensions and origin fixed. Compose
changes the card size, and View springs move the regular popup inside that viewport.
The public `AttachedSurfaceControl.setTouchableRegion` API limits input to the visible
surface; touches elsewhere reach the app underneath. Tool gestures temporarily clear
that region instead of hiding and recreating the window. The service dialog must set
FLAG_HARDWARE_ACCELERATED before show/addView: ViewRootImpl chooses the renderer once
at attachment, so setting the flag later cannot enable hardware rendering or native blur.
The fixed host also uses a non-floating theme and full-display bounds from its first add.
Only the overlay's own focused composer reserves IME space; another app's keyboard must
not reduce the working pill's drag area.

The system assistant uses one `AssistantOverlaySession`, panel ComposeView, ViewModel and input
owner throughout normal and phone-use modes. Its Activity supplies permission and
activity-result ownership, while the service retains the scene when the Activity yields.
Completion or cancellation brings the Activity back and reveals the existing input.
Hidden scenes pause their lifecycle and composition frame clock. Compact assistant scenes stop
rendering the transcript after its outgoing fade. The regular popup keeps its measured reply
body through the full expansion spring; it sizes to actual content rather than an empty viewport.
A separate full-screen decoration ComposeView shares the scene lifecycle and recomposer. It
sits behind the card, outside its IME-inset layout, so the screen-edge color effect remains
visible in normal and phone-use modes without expanding the card's touch region.

The phone dock stays hidden over LastChat's own screens. The normal system assistant
remains usable there, using a neutral assistant icon in its existing avatar slot so a
second assistant portrait is never layered over the in-app avatar. Fresh assist gestures
wait for the saved system character rather than using the quick startup cache, and never
adopt an unrelated ordinary phone conversation. They can resume their own active system
work or an unpresented final-answer handoff. Entering the main app pauses unfinished
work and permanently retires completed overlay results; leaving it cannot resurrect them.
Popup expansion is scoped to conversation and work/input/result phase. New work resets
the popup to compact while manual expansion survives individual tool-step updates.

Native background blur follows the child's transformed bounds. Keep the original window
background installed once: replacing `decorView.background` with Android's generated
blur LayerDrawable would recursively wrap the background. Change the existing layer
insets and drawable bounds instead. DecorView's public background getter hides that
layer, so capture the generated parent during its public padding query and follow its
public callback chain. This also survives View clearing the original child's callback
while installing the wrapper. Accessibility grants the trusted overlay host; it
does not grant a separate live-blur API. Blur still depends on Android's cross-window
blur support, an actually hardware-rendered window and the user's blur setting. The
OverlayBlur diagnostic reports all three conditions; the global support flag alone is
not evidence that the current overlay has a hardware renderer. Screenshots are used by phone tools, not as
the assistant's visual backdrop. Track alpha writes locally: Android's native blur drawable
overrides `setAlpha` but can inherit a constant `getAlpha`. Comparing that getter before
restoring opacity leaves compositor blur disabled after a fade.

Picker and editor dialogs must receive the real framework WindowManager from the retained
context. A delegated WindowManager proxy crashes Android's internal WindowManagerImpl cast.
`AssistantDialogHost` leaves Compose dialogs owned by the Activity. While one is visible,
the service card fades away and releases focus/touch to the normal Material dialog; its
retained composition stays alive. The card and full-screen glow fade together so the
picker remains unobstructed; completed phone work also fades its glow. This avoids changing an
already-attached native window's type or token. Dialogs hide when the Activity yields or
capture begins. The Activity owns activity results; no special picker permission is needed.

`PhoneUseTouchCursor` is a non-touchable, non-focusable accessibility window. It uses the
same sanitized screen coordinates as the injected gesture, follows its real-time stroke,
and fades its press/ripple feedback. Capture hides it immediately. It never becomes an
accessibility target or adds a second assistant avatar.

Android 12 and earlier retain the smaller legacy phone window because precise public
touch regions are unavailable. The Activity fallback remains modal on those versions.

## Regression checks

- `PhoneOverlayHardwareTest`: captures the actual service dialog flags at its first
  WindowManager.addView, and proves a later hardware flag cannot repair initial attachment.
- `OverlayKeyboardBoundsTest`: target-app IME does not constrain a working pill; own input
  reserves keyboard space and releases it immediately when focus is disabled.
- `AssistantInvocationPolicyTest`: loaded character settings and fresh/return invocation ownership.
- `PhonePopupPhaseTest`: automatic compact work cycles and manual expansion scope.
- `PhoneUseMotionTest`: edge attachment, throws, expansion, drag coordinates and temporary
  zero travel during keyboard or display changes.
- `AssistantTransitionTest`: interrupted drags, release from the presented size, restored
  input and constrained-panel transitions without traversing invisible height.
- `AssistantFloatingWindowTest`: viewport geometry, transforms, blur notification and
  alpha restoration with the platform's constant-alpha-getter behavior.
- `AssistantDialogHostTest`: framework manager ownership, Activity dialog visibility and
  modal handoffs without changing an attached window's type or token.
- `PhoneUseControlsRenderTest`: compact working controls and content-sized short replies.
- `PhoneCursorStrokeTest`: shared gesture/cursor coordinates and invalid input handling.
- `FloatingOverlayNativeWindowTest`: populated transcript and real composer, repeated
  morph reversals, native touch pass-through, drag resizing and a real keyboard.
- `PersistentAssistantSessionNativeTest`: actual accessibility service and assistant
  Activity, completion/cancellation, draft preservation and Activity recreation without
  replacing the scene or its native window.

The instrumentation tests require Android 13 or later. Native geometry checks on a
software emulator establish correctness, not animation performance on physical hardware.
Inspect the captured screenshots, frame reports and recording alongside test assertions.
Record any emulator timeout scaling with the results. Unaccelerated runs may require
Android's hardware-timeout multiplier to complete startup; those runs cannot validate
physical-device startup time, ANR thresholds, or frame pacing.
