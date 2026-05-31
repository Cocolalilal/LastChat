package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun AssistantBackground(
    assistant: Assistant,
    modifier: Modifier = Modifier,
) {
    if (assistant.background != null) {
        val scrimAlpha = assistant.backgroundDim.coerceIn(0f, 0.85f)
        val scrimColor = if (LocalDarkMode.current) {
            Color.Black.copy(alpha = scrimAlpha)
        } else {
            Color.White.copy(alpha = scrimAlpha)
        }
        val edgeFadeColor = if (LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.background
        Box(modifier = modifier) {
            AsyncImage(
                model = assistant.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.4f),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to edgeFadeColor.copy(alpha = 0.45f),
                            0.18f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to edgeFadeColor.copy(alpha = 0.58f),
                        )
                    )
            )
        }
    }
}
