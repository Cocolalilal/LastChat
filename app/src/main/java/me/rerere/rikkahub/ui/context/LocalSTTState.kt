package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.ui.hooks.CustomSttState

val LocalSTTState = compositionLocalOf<CustomSttState> { error("Not provided yet") }
