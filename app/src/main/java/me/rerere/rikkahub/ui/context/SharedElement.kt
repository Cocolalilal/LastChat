package me.rerere.rikkahub.ui.context

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope> {
    error("No AnimatedVisibilityScope provided")
}
