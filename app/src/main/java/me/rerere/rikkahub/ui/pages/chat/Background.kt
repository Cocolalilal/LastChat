package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun AssistantBackground(
    assistant: Assistant,
    modifier: Modifier = Modifier,
) {
    val motionPolicy = LocalMotionPolicy.current
    AnimatedContent(
        targetState = assistant,
        transitionSpec = {
            val fadeDuration = if (motionPolicy.reduceMotion) 80 else 180
            val scaleDuration = if (motionPolicy.reduceMotion) 80 else 220
            (fadeIn(animationSpec = tween(fadeDuration)) + scaleIn(
                initialScale = 1.015f,
                animationSpec = tween(scaleDuration),
            )) togetherWith (fadeOut(animationSpec = tween(fadeDuration)) + scaleOut(
                targetScale = 0.995f,
                animationSpec = tween(scaleDuration),
            )) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        label = "assistant_background",
        modifier = modifier,
    ) { targetAssistant ->
        AssistantBackgroundLayer(
            assistant = targetAssistant,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AssistantBackgroundLayer(
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
