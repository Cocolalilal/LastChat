package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.uuid.Uuid

/**
 * Toast types with associated colors and icons
 */
enum class ToastType {
    Normal,
    Success,
    Info,
    Warning,
    Error
}

data class ToastAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * A single toast data class
 */
data class Toast(
    val id: Any = Uuid.random(),
    val message: String,
    val type: ToastType = ToastType.Normal,
    val duration: Long = 6000L,
    val action: ToastAction? = null
)

/**
 * State holder for the app toaster
 */
@Stable
class AppToasterState {
    private val _toasts = mutableStateListOf<Toast>()
    val toasts: List<Toast> get() = _toasts
    
    companion object {
        private const val MAX_VISIBLE_TOASTS = 4
    }
    
    private fun trimOldestIfNeeded() {
        while (_toasts.size > MAX_VISIBLE_TOASTS) {
            _toasts.removeAt(0)
        }
    }

    fun show(
        message: String,
        type: ToastType = ToastType.Normal,
        duration: Long = 6000L,
        action: ToastAction? = null
    ): Toast {
        val toast = Toast(message = message, type = type, duration = duration, action = action)
        _toasts.add(toast)
        trimOldestIfNeeded()
        return toast
    }

    fun show(
        message: String,
        type: ToastType = ToastType.Normal,
        duration: kotlin.time.Duration,
        action: ToastAction? = null
    ): Toast {
        return show(message = message, type = type, duration = duration.inWholeMilliseconds, action = action)
    }

    fun show(toast: Toast) {
        _toasts.add(toast)
        trimOldestIfNeeded()
    }

    fun dismiss(toast: Toast) {
        _toasts.remove(toast)
    }

    fun dismiss(id: Any) {
        _toasts.removeAll { it.id == id }
    }

    fun dismissAll() {
        _toasts.clear()
    }
}

@Composable
fun rememberAppToasterState(): AppToasterState {
    return remember { AppToasterState() }
}

/**
 * Host composable that displays toasts
 */
@Composable
fun AppToasterHost(
    state: AppToasterState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(
                items = state.toasts,
                key = { it.id }
            ) { toast ->
                AppToastItem(
                    toast = toast,
                    onDismiss = { state.dismiss(toast) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun AppToastItem(
    toast: Toast,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    val density = LocalDensity.current
    
    // Physics parameters
    val dragFriction = 0.7f
    val unlockThresholdPx = with(density) { 80.dp.toPx() }
    val screenWidthPx = with(density) { 800.dp.toPx() }
    
    // All animation state - manual control
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(-200f) } // Start above screen
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isFlying by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }
    
    // Entry animation
    LaunchedEffect(Unit) {
        launch { offsetY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
        launch { alpha.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
    }
    
    val dismissWithAnimation = {
        if (!isFlying && !isDismissed) {
            scope.launch {
                launch { offsetY.animateTo(-200f, spring(dampingRatio = 1f, stiffness = 500f)) }
                launch { scale.animateTo(0.8f, spring(dampingRatio = 1f, stiffness = 500f)) }
                launch { alpha.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f)) }
                delay(200)
                isDismissed = true
                onDismiss()
            }
        }
        Unit
    }

    // Auto-dismiss after duration
    LaunchedEffect(toast) {
        if (toast.duration > 0) {
            delay(toast.duration)
            dismissWithAnimation()
        }
    }
    
    // Handle swipe dismissal - fly off to the side
    fun swipeOff(direction: Float) {
        if (isFlying || isDismissed) return
        isFlying = true
        haptics.perform(HapticPattern.Pop)
        scope.launch {
            // Fly horizontally off screen - fast
            launch { 
                offsetX.animateTo(
                    direction * screenWidthPx,
                    spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
            launch { alpha.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) }
            delay(250)
            isDismissed = true
            onDismiss()
        }
    }

    if (!isDismissed) {
        Box(
            modifier = modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value.absoluteValue > unlockThresholdPx) {
                                    val direction = if (offsetX.value > 0) 1f else -1f
                                    swipeOff(direction)
                                } else {
                                    if (isUnlocked) {
                                        haptics.perform(HapticPattern.Thud)
                                        isUnlocked = false
                                    }
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.55f, stiffness = 200f)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.6f))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val currentOffset = offsetX.value
                            val newOffset = (currentOffset + dragAmount * dragFriction)
                                .coerceIn(-screenWidthPx * 0.5f, screenWidthPx * 0.5f)
                            
                            scope.launch { offsetX.snapTo(newOffset) }
                            
                            val wasUnderThreshold = currentOffset.absoluteValue < unlockThresholdPx
                            val isOverThreshold = newOffset.absoluteValue >= unlockThresholdPx
                            
                            if (wasUnderThreshold && isOverThreshold && !isUnlocked) {
                                haptics.perform(HapticPattern.Pop)
                                isUnlocked = true
                            } else if (!wasUnderThreshold && !isOverThreshold && isUnlocked) {
                                haptics.perform(HapticPattern.Tick)
                                isUnlocked = false
                            }
                        }
                    )
                }
        ) {
            ToastContent(
                toast = toast,
                onDismiss = dismissWithAnimation
            )
        }
    }
}

@Composable
private fun ToastContent(
    toast: Toast,
    onDismiss: () -> Unit
) {
    val (containerColor, contentColor, icon) = getToastColors(toast.type)
    var isExpanded by remember { mutableStateOf(false) }
    val isLongMessage = toast.message.length > 80

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = contentColor.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                )
            )
    ) {
        Row(
            modifier = Modifier.padding(
                start = 20.dp,
                end = if (isLongMessage && !isExpanded) 8.dp else 20.dp,
                top = 14.dp,
                bottom = 14.dp
            ),
            verticalAlignment = if (isExpanded) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }

            // Message
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Action Button
            if (toast.action != null) {
                TextButton(
                    onClick = {
                        toast.action.onClick()
                        onDismiss()
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = toast.action.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }
            
            // Expand button for long messages
            if (isLongMessage && !isExpanded) {
                Surface(
                    onClick = { isExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = contentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.a11y_show_more),
                            modifier = Modifier.size(18.dp),
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getToastColors(type: ToastType): Triple<Color, Color, ImageVector?> {
    return when (type) {
        ToastType.Normal -> if(LocalDarkMode.current) {
            Triple(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.onSurface,
                null
            )
        } else {
            Triple(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurface,
                null
            )
        }
        ToastType.Success -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle
        )
        ToastType.Info -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.Info
        )
        ToastType.Warning -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning
        )
        ToastType.Error -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Error
        )
    }
}

