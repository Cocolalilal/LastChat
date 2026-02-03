# Chat UI Feature Completion Plan

## Overview
Complete the chat message UI feature with toolbar improvements, avatar sizing fixes, and smooth shape morphing animations.

## 1. User Message Toolbar Improvements (ChatMessageV2.kt)

### Changes Needed:

#### Add imports at the top:
```kotlin
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.HapticPattern
```

#### Update UserMessageTurn composable to add haptics:
Replace the entire `UserMessageTurn` function (lines 439-543) with:

```kotlin
/**
 * User message turn - right-aligned stacked bubbles.
 * Tap to show/hide action toolbar.
 */
@Composable
private fun UserMessageTurn(
    group: MessageTurnGroup,
    assistant: Assistant?,
    maxWidth: androidx.compose.ui.unit.Dp,
    showToolbar: Boolean,
    onToggleToolbar: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPremiumHaptics()
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Message bubbles
        group.nodes.forEachIndexed { nodeIndex, node ->
            val textParts = node.currentMessage.parts.filterIsInstance<UIMessagePart.Text>()
            textParts.forEachIndexed { partIndex, part ->
                // Calculate bubble position based on overall position in group
                val isFirst = nodeIndex == 0 && partIndex == 0
                val isLast = nodeIndex == group.nodes.lastIndex && partIndex == textParts.lastIndex
                val totalBubbles = group.nodes.sumOf { n -> 
                    n.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().size 
                }
                val position = when {
                    totalBubbles == 1 -> BubblePosition.SINGLE
                    isFirst -> BubblePosition.FIRST
                    isLast -> BubblePosition.LAST
                    else -> BubblePosition.MIDDLE
                }
                
                GroupedMessageBubble(
                    position = position,
                    role = BubbleRole.USER,
                    modifier = Modifier.widthIn(max = maxWidth),
                    onClick = {
                        haptics.performHapticFeedback(HapticPattern.Pop)
                        onToggleToolbar()
                    }
                ) {
                    MarkdownBlock(
                        content = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = true,
                        ),
                        onClickCitation = {}
                    )
                }
            }
        }
        
        // Toolbar - appears on tap
        AnimatedVisibility(
            visible = showToolbar,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeOut()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // Copy button
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticPattern.Pop)
                            onCopy()
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Regenerate button
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticPattern.Pop)
                            onRegenerate()
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Refresh,
                        contentDescription = "Regenerate",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // More options button
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticPattern.Pop)
                            onOpenMenu()
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.MoreHoriz,
                        contentDescription = "More Options",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

## 2. Assistant Avatar Sizing Fix (ChatMessageV2.kt)

### Update AssistantMessageTurn composable:

In `AssistantMessageTurn`, change the avatar size from 32.dp to 36.dp to match the ActivityPill height:

**Current code (around line 623 and 649):**
```kotlin
UIAvatar(
    name = avatarName,
    modifier = Modifier.size(32.dp),
    value = avatarValue,
    loading = loading,
)
```

**Change to:**
```kotlin
UIAvatar(
    name = avatarName,
    modifier = Modifier.size(36.dp),
    value = avatarValue,
    loading = loading,
)
```

Also update the PhantomLoadingTurn in ChatList.kt (around line 828):

**Current:**
```kotlin
me.rerere.rikkahub.ui.components.ui.UIAvatar(
    name = avatarName,
    modifier = Modifier.size(32.dp),
    value = avatarValue,
    loading = true,
)
```

**Change to:**
```kotlin
me.rerere.rikkahub.ui.components.ui.UIAvatar(
    name = avatarName,
    modifier = Modifier.size(36.dp),
    value = avatarValue,
    loading = true,
)
```

## 3. Smooth Avatar Shape Morphing (AvatarShape.kt)

Replace the entire file with:

```kotlin
package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import kotlin.math.roundToInt

@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate the rotation of the flower shape
    val rotateAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
        )
    )
    
    // Animate morphing between flower and circle
    // We use the number of sides to morph: 6 sides (flower) -> 100 sides (circle-like)
    val targetSides = if (loading) 6 else 100
    val animatedSides by animateIntAsState(
        targetValue = targetSides,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "shape_morph"
    )
    
    // Create shape based on animated sides
    return if (animatedSides >= 50) {
        // Close enough to circle, use CircleShape for better performance
        CircleShape
    } else {
        // Use Cookie6Sided with rotation when loading, or morph to circle
        MaterialShapes.Cookie6Sided.toShape(rotateAngle.value.roundToInt())
    }
}
```

## 4. Add Required Import in ChatMessageV2.kt

At the top of ChatMessageV2.kt, add this import:
```kotlin
import androidx.compose.foundation.layout.Box
```

## Summary of Changes

1. **User Toolbar**: Added haptic feedback (Pop pattern) to all toolbar buttons
2. **Avatar Size**: Changed from 32.dp to 36.dp to match ActivityPill height
3. **Shape Morphing**: Implemented smooth spring-based animation between flower (6 sides) and circle (100 sides)

## Testing Checklist

- [ ] Tap on user message shows/hides toolbar with smooth animation
- [ ] Copy button works and provides haptic feedback
- [ ] Regenerate button works and provides haptic feedback  
- [ ] More options (3 dots) button opens action sheet with haptic feedback
- [ ] Assistant avatar is 36.dp (same height as activity pill)
- [ ] Avatar smoothly morphs from rotating flower to circle when loading completes
- [ ] Avatar smoothly morphs from circle back to flower when new generation starts
