package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.common.platform.PlatformHapticPattern
import me.rerere.common.platform.PlatformHaptics
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

@OptIn(ExperimentalForeignApi::class)
class IosPlatformHaptics : PlatformHaptics {
    override fun perform(pattern: PlatformHapticPattern) {
        when (pattern) {
            PlatformHapticPattern.Tick,
            PlatformHapticPattern.ScrollEdge,
            PlatformHapticPattern.Selection -> selection()
            PlatformHapticPattern.Pop,
            PlatformHapticPattern.DragEnd -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            PlatformHapticPattern.Send -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
            PlatformHapticPattern.Thud,
            PlatformHapticPattern.Buildup,
            PlatformHapticPattern.DragStart -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
            PlatformHapticPattern.Success -> notification(
                UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
            )
            PlatformHapticPattern.Error -> notification(
                UINotificationFeedbackType.UINotificationFeedbackTypeError,
            )
            PlatformHapticPattern.Cancel -> notification(
                UINotificationFeedbackType.UINotificationFeedbackTypeWarning,
            )
        }
    }

    private fun impact(style: UIImpactFeedbackStyle) {
        UIImpactFeedbackGenerator(style).apply {
            prepare()
            impactOccurred()
        }
    }

    private fun selection() {
        UISelectionFeedbackGenerator().apply {
            prepare()
            selectionChanged()
        }
    }

    private fun notification(type: UINotificationFeedbackType) {
        UINotificationFeedbackGenerator().apply {
            prepare()
            notificationOccurred(type)
        }
    }
}
