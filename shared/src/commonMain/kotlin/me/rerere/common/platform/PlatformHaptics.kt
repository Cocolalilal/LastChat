package me.rerere.common.platform

enum class PlatformHapticPattern {
    Tick,
    Pop,
    Thud,
    Buildup,
    Success,
    Error,
    DragStart,
    DragEnd,
    Send,
    ScrollEdge,
    Selection,
    Cancel,
}

interface PlatformHaptics {
    fun perform(pattern: PlatformHapticPattern)
}

