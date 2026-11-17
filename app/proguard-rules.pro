# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# keep kotlinx serializable classes
-keep @kotlinx.serialization.Serializable class * {*;}

# keep jlatexmath
-keep class ru.noties.jlatexmath.** {*;}

# Ktor pulls a JVM-only debugger probe which references java.lang.management.
# Those types don't exist on Android and are safe to ignore for release builds.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# The embedded web UI is runtime-only: Android starts a foreground service,
# Ktor serves bundled assets from AssetManager, and the browser talks to
# kotlinx-serialized DTOs over local HTTP/SSE. Keep this path intact under R8.
-keep class me.rerere.rikkahub.web.** { *; }
-keep class me.rerere.rikkahub.service.WebServerService { *; }

# Linux workspace shell uses ProcessBuilder plus Termux terminal JNI. R8 does not
# rewrite native proot binaries, but keep this boundary intact in release builds.
-keep class me.rerere.workspace.** { *; }
-keep class me.rerere.rikkahub.ui.pages.extensions.workspace.** { *; }
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn com.termux.**

# On-device embeddings (EmbeddingGemma via the AI Edge RAG library). The library ships no consumer
# R8 rules and reaches its model/proto classes through name-based JNI + native RegisterNatives
# callbacks, so keep the RAG + MediaPipe boundary intact in release builds.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.localagents.** { *; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.ai.edge.localagents.**
-dontwarn com.google.mediapipe.**

-dontobfuscate

# ART regression tests run against the same minified stable APK we deliver. Keep
# their integration entry points callable from the separately installed test APK;
# implementation bodies and the rest of the app remain eligible for optimization.
-keep,allowoptimization class me.rerere.rikkahub.data.model.TemplatePlaceholdersKt { public *; }
-keep,allowoptimization class me.rerere.rikkahub.data.model.AssistantKt {
    public static *** getInitialMessageNodes(...);
}
-keep,allowoptimization class me.rerere.rikkahub.data.ai.transformers.PromptPlaceholdersKt { public *; }
-keep,allowoptimization class me.rerere.rikkahub.data.ai.GenerationHandler { public *; }
-keep,allowoptimization class me.rerere.rikkahub.data.ai.BuildMessagesResult {
    public java.util.List getMessages();
}
-keep,allowoptimization class me.rerere.rikkahub.data.ai.GenerationChunk$Messages { public *; }
-keep,allowoptimization class me.rerere.rikkahub.service.ChatGenerationTransformersKt { public *; }
-keep,allowoptimization class me.rerere.rikkahub.utils.JsonKt {
    public static *** getJsonInstant();
}
-keep,allowoptimization class me.rerere.ai.provider.ProviderManager { public *; }
-keep,allowoptimization class me.rerere.ai.ui.UIMessage$Companion { public *; }
# The provider smoke tests supply an HTTP implementation from the test APK.
# Preserve this open interface and its request/response contract across R8.
-keep interface me.rerere.common.platform.PlatformHttpClient { public *; }
-keep,allowoptimization class me.rerere.common.platform.PlatformHttpRequest { public *; }
-keep,allowoptimization class me.rerere.common.platform.PlatformHttpResponse { public *; }
-keep,allowoptimization class me.rerere.common.platform.PlatformServerEvent$Open { public *; }
-keep,allowoptimization class me.rerere.common.platform.PlatformServerEvent$Event { public *; }
-keep,allowoptimization class me.rerere.common.platform.PlatformServerEvent$Closed { public *; }

# Tasks GenAI (AutoValue/JavaPoet) references javax.lang.model which is not on Android
-dontwarn javax.lang.model.**
