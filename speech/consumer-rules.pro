# sherpa-onnx's JNI layer resolves Kotlin configuration classes and their
# fields by their original names (for example, OfflineRecognizerConfig's
# maxActivePaths). The upstream AAR currently does not publish consumer rules,
# so R8 would otherwise rename those members in minified app builds and crash
# recognizer initialization with "Failed to get field ID".
-keep class com.k2fsa.sherpa.onnx.** { *; }
