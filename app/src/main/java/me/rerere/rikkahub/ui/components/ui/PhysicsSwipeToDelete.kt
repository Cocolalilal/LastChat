package me.rerere.rikkahub.ui.components.ui

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Position of item in a group for corner radius calculation
 */
enum class ItemPosition {
    ONLY,   // Only item in group - all corners rounded
    FIRST,  // First item - top corners rounded
    MIDDLE, // Middle item - no corners rounded
    LAST    // Last item - bottom corners rounded
}

/**
 * Physics-based swipe-to-delete component with:
 * - Heavy drag resistance (item moves slower than finger)
 * - Magnetic unlock behavior (sticks then snaps)
 * - Position-based corner radius (top/bottom/middle)
 * - Corner radius animation on unlock (all corners become rounded)
 * - Spring snap-back animation
 * - Edge fade gradient
 * - Haptic feedback on unlock/lock
 * - Neighbor drag influence support
 * 
 * @param deleteEnabled Whether delete is allowed. When false, swipe is very difficult and always springs back.
 * @param neighborOffset Offset from a neighboring item being dragged (0 = not influenced)
 * @param onDragProgress Callback reporting current drag offset and unlock state for neighbor coordination
 */
@Composable
fun PhysicsSwipeToDelete(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    deleteEnabled: Boolean = true,
    position: ItemPosition = ItemPosition.ONLY,
    groupCornerRadius: Dp = 24.dp,
    itemCornerRadius: Dp = 10.dp,
    neighborOffset: Float = 0f,
    onDragProgress: ((offset: Float, isUnlocked: Boolean) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    content: @Composable (shape: Shape) -> Unit
) {
    val density = LocalDensity.current
    val haptics = rememberPremiumHaptics()
    val scope = rememberCoroutineScope()
    
    // Physics parameters - when delete is disabled, make it much harder to move
    val dragFriction = if (deleteEnabled) 0.6f else 0.15f // Item moves at 60% or 15% of finger speed
    val revealDistancePx = with(density) { 140.dp.toPx() }
    val unlockThresholdPx = revealDistancePx * 0.25f // 1/4 of reveal distance = 35dp
    val magneticPullStrength = 0.3f // How strongly item "sticks" before unlock
    
    // Animation state
    val offsetX = remember { Animatable(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Reset state when deleteEnabled changes (e.g., when item becomes selected)
    LaunchedEffect(deleteEnabled) {
        if (!deleteEnabled) {
            offsetX.snapTo(0f)
            isUnlocked = false
            isDragging = false
        }
    }
    
    // Report drag progress for neighbor coordination
    LaunchedEffect(offsetX.value, isUnlocked, isDragging) {
        if (isDragging && !isUnlocked) {
            onDragProgress?.invoke(offsetX.value, isUnlocked)
        }
    }
    
    // Animate neighbor offset with spring
    val animatedNeighborOffset = remember { Animatable(0f) }
    var wasNeighborInfluenced by remember { mutableStateOf(false) }
    
    LaunchedEffect(neighborOffset) {
        if (neighborOffset != 0f) {
            animatedNeighborOffset.snapTo(neighborOffset)
            wasNeighborInfluenced = true
        } else if (wasNeighborInfluenced) {
            // Immediately spring back - no delay conditions
            wasNeighborInfluenced = false
            animatedNeighborOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 1000f)
            )
        }
    }
    
    // Total offset is own drag + neighbor influence
    val totalOffset = offsetX.value + animatedNeighborOffset.value
    
    // Calculate unlock progress (0 = locked, 1 = unlocked) - only for own drag, not neighbor
    val unlockProgress by remember {
        derivedStateOf {
            (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f)
        }
    }
    
    // Position-based corner radius with smooth animation on position change and unlock
    val groupRadiusPx = with(density) { groupCornerRadius.toPx() }
    val itemRadiusPx = with(density) { itemCornerRadius.toPx() }
    
    // Animate base radii when position changes
    val targetTopRadius = when (position) {
        ItemPosition.ONLY, ItemPosition.FIRST -> groupRadiusPx
        ItemPosition.MIDDLE, ItemPosition.LAST -> itemRadiusPx
    }
    val targetBottomRadius = when (position) {
        ItemPosition.ONLY, ItemPosition.LAST -> groupRadiusPx
        ItemPosition.MIDDLE, ItemPosition.FIRST -> itemRadiusPx
    }
    
    val animatedTopRadius by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetTopRadius,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "topRadius"
    )
    val animatedBottomRadius by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetBottomRadius,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "bottomRadius"
    )
    
    // Calculate shape based on animated position and unlock progress
    // Only apply unlock progress if this item is being dragged (not neighbor influence)
    val shape by remember {
        derivedStateOf {
            // Only interpolate corners for own unlock progress (not neighbor influence)
            val ownUnlockProgress = if (neighborOffset == 0f) unlockProgress else 0f
            
            val finalTopStart = animatedTopRadius + (groupRadiusPx - animatedTopRadius) * ownUnlockProgress
            val finalTopEnd = animatedTopRadius + (groupRadiusPx - animatedTopRadius) * ownUnlockProgress
            val finalBottomEnd = animatedBottomRadius + (groupRadiusPx - animatedBottomRadius) * ownUnlockProgress
            val finalBottomStart = animatedBottomRadius + (groupRadiusPx - animatedBottomRadius) * ownUnlockProgress
            
            RoundedCornerShape(
                topStart = with(density) { finalTopStart.toDp() },
                topEnd = with(density) { finalTopEnd.toDp() },
                bottomEnd = with(density) { finalBottomEnd.toDp() },
                bottomStart = with(density) { finalBottomStart.toDp() }
            )
        }
    }
    
    // Background color
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val fadeColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
    
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Background with action buttons
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button - only show when delete is enabled
            if (deleteEnabled) {
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Cancel)
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.5f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            isUnlocked = false
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    alpha = (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // Delete button - only show when delete is enabled
            if (deleteEnabled) {
                PhysicsSwipeActionButton(
                    onClick = {
                        haptics.perform(HapticPattern.Error)
                        onDelete()
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    alpha = (offsetX.value.absoluteValue / unlockThresholdPx).coerceIn(0f, 1f)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        
        // Foreground content with swipe gesture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(totalOffset.roundToInt(), 0) }
                .clip(shape)
                .background(fadeColor)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            onDragEnd?.invoke() // Call immediately so neighbors reset right away
                            scope.launch {
                                // When delete is disabled, always spring back regardless of threshold
                                if (!deleteEnabled) {
                                    // Spring back with haptic feedback
                                    if (offsetX.value.absoluteValue > 10f) {
                                        haptics.perform(HapticPattern.Thud)
                                    }
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.55f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else if (offsetX.value.absoluteValue > unlockThresholdPx) {
                                    // Snap to reveal position
                                    if (!isUnlocked) {
                                        haptics.perform(HapticPattern.Pop)
                                        isUnlocked = true
                                    }
                                    offsetX.animateTo(
                                        targetValue = -revealDistancePx,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f, // Bouncy/Clicky for snap
                                            stiffness = 300f     // Consistent stiffness
                                        )
                                    )
                                } else {
                                    // Snap back to locked
                                    if (isUnlocked) {
                                        haptics.perform(HapticPattern.Thud)
                                        isUnlocked = false
                                    }
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.55f,
                                            stiffness = Spring.StiffnessLow // Slower lock animation
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = if (isUnlocked) -revealDistancePx else 0f,
                                    animationSpec = spring(dampingRatio = 0.6f)
                                )
                            }
                            onDragEnd?.invoke()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            
                            val currentOffset = offsetX.value
                            val newOffset: Float
                            
                            // Only allow left swipe (negative direction)
                            if (dragAmount < 0 || currentOffset < 0) {
                                // Apply friction - movement is slower than finger
                                val friction = if (currentOffset.absoluteValue < unlockThresholdPx && !isUnlocked) {
                                    // Extra resistance before unlock threshold (magnetic pull)
                                    dragFriction * (1f - magneticPullStrength * (currentOffset.absoluteValue / unlockThresholdPx))
                                } else {
                                    dragFriction
                                }
                                
                                newOffset = (currentOffset + dragAmount * friction)
                                    .coerceIn(-revealDistancePx * 1.2f, 0f)
                                
                                scope.launch {
                                    offsetX.snapTo(newOffset)
                                }
                                
                                // Haptic feedback when crossing threshold
                                val wasUnderThreshold = currentOffset.absoluteValue < unlockThresholdPx
                                val isOverThreshold = newOffset.absoluteValue >= unlockThresholdPx
                                
                                if (wasUnderThreshold && isOverThreshold && !isUnlocked) {
                                    haptics.perform(HapticPattern.Pop)
                                } else if (!wasUnderThreshold && !isOverThreshold && currentOffset.absoluteValue > 0) {
                                    haptics.perform(HapticPattern.Tick)
                                }
                            }
                        }
                    )
                }
        ) {
            content(shape)
        }
    }
}

/**
 * Physics-animated action button for swipe-to-delete cancel/delete actions
 */
@Composable
private fun PhysicsSwipeActionButton(
    onClick: () -> Unit,
    containerColor: Color,
    alpha: Float,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f, // Round/Clicky Standard
            stiffness = 300f     // Round/Clicky Standard
        ),
        label = "button_scale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "button_alpha"
    )
    
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 4.dp,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha * pressAlpha
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}
