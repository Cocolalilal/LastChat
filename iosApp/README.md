# LastChat iOS

The iOS client uses Compose Multiplatform so visual tokens and, progressively,
whole screens can be shared without changing the Android app.

## Requirements

- macOS with Xcode 16 or newer
- JDK 17 or newer
- An Apple development team selected in Xcode for device builds

## Run

1. Open `iosApp/xcode/LastChatIOS.xcodeproj` in Xcode.
2. Select the `LastChatIOS` scheme and an iPhone simulator.
3. Press Run. The Xcode build phase invokes
   `:iosApp:embedAndSignAppleFrameworkForXcode` automatically.

The Kotlin side can be checked without Xcode:

```powershell
./gradlew :iosApp:compileKotlinIosSimulatorArm64
```

The framework already includes the shared core, common utilities, the real
OpenAI/Google/Claude/ComfyUI provider layer, every search service, and the
portable TTS slice. Native iOS adapters provide Darwin HTTP/SSE, sandboxed file
storage, media encoding, Keychain secrets, RS256 signing, and UIKit haptics.

The current iOS surface has persistent conversations, real OpenAI-compatible,
Google, and Claude streaming, app-container storage, per-provider
Keychain-backed API keys, persisted assistant/system-prompt settings, and all
six Android theme palettes with system/light/dark modes. Conversation rows use
the production Android interaction surface and persist create, select, rename,
and delete operations. Multiple assistant profiles retain their own system
prompts and conversations, provider endpoint/model settings are retained per
provider, and an active stream can be cancelled from the composer. It is not yet a
complete port of every LastChat screen or Android repository. Android remains
the source of truth while assistants, attachments, memory, tools, local models,
and the remaining screens move behind common contracts. See
`docs/ios-parity-matrix.md` for the exact status; do not treat the current
screens as screenshot-parity complete until they pass Xcode visual QA.

The composer can pick image, video, and audio files through the native iOS
document picker. Selections are copied into LastChat's Application Support
directory before the picker callback completes, pending drafts persist across
launches, and sent image parts render from the sandboxed copy. Document prompt
parsing and native audio/video playback remain separate parity items.
