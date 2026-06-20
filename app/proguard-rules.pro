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
-keep class org.scilab.forge.jlatexmath.** {*;}

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

-dontobfuscate
