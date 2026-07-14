package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Production three-dot generation indicator shared by Android and iOS. */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bounceHeight: Dp = 4.dp,
) {
    val animatables = remember { List(3) { Animatable(0f) } }
    animatables.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 150L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animatables.forEach { animatable ->
            Box(
                modifier = Modifier
                    .offset(y = -(bounceHeight * animatable.value))
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.6f + 0.4f * animatable.value)),
            )
        }
    }
}
