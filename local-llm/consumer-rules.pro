# LiteRT-LM's native library resolves Java classes and methods by JNI names.
# Keep the whole boundary intact in app builds that consume this library.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class com.google.ai.edge.litertlm.** {
    native <methods>;
}
-dontwarn com.google.ai.edge.litertlm.**
