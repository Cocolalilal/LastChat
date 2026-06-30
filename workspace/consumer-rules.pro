# Workspace shell/proot startup crosses ProcessBuilder and JNI boundaries.
# Keep the API stable for release builds even when the app shrinker runs.
-keep class me.rerere.workspace.** { *; }
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn com.termux.**
