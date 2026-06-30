package me.rerere.common.platform

enum class PlatformHapticPattern {
    Pop,
    Thud,
    Success
}

interface PlatformHaptics {
    fun perform(pattern: PlatformHapticPattern)
}

