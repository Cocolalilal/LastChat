package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf

val LocalChatSendMessage = compositionLocalOf<(String) -> Unit> { {} }
