package me.rerere.rikkahub.service.workspace

import android.os.Build

data class WorkspaceRuntimeSupport(
    val supported: Boolean,
    val abi: String?,
    val message: String,
)

fun workspaceRuntimeSupport(nativeLibraryDir: String): WorkspaceRuntimeSupport {
    val nativePath = nativeLibraryDir.lowercase()
    val abi = when {
        "x86_64" in nativePath -> "x86_64"
        "arm64" in nativePath || "aarch64" in nativePath -> "arm64-v8a"
        "armeabi" in nativePath || "/arm" in nativePath || "\\arm" in nativePath -> "armeabi-v7a"
        else -> Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
            ?: Build.SUPPORTED_ABIS.firstOrNull()
    }
    return when (abi) {
        "arm64-v8a", "x86_64", "armeabi-v7a", "armeabi" -> WorkspaceRuntimeSupport(
            supported = true,
            abi = abi,
            message = "",
        )
        else -> WorkspaceRuntimeSupport(
            supported = false,
            abi = abi,
            message = "Linux workspaces are not available for this device ABI: ${abi ?: "unknown"}.",
        )
    }
}

fun defaultRootfsUrl(nativeLibraryDir: String): String {
    val nativePath = nativeLibraryDir.lowercase()
    val abi = when {
        "x86_64" in nativePath -> "x86_64"
        "arm64" in nativePath || "aarch64" in nativePath -> "arm64-v8a"
        "armeabi" in nativePath || "/arm" in nativePath || "\\arm" in nativePath -> "armeabi-v7a"
        else -> Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
            ?: Build.SUPPORTED_ABIS.firstOrNull()
            ?: "arm64-v8a"
    }
    if (abi == "armeabi-v7a" || abi == "armeabi") {
        return "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/armv7/alpine-minirootfs-3.19.9-armv7.tar.gz"
    }
    val arch = when (abi) {
        "x86_64" -> "amd64"
        "arm64-v8a" -> "arm64"
        else -> "arm64"
    }
    return "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-$arch.tar.gz"
}
