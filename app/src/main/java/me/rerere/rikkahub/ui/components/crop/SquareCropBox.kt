package me.rerere.rikkahub.ui.components.crop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * A composable that displays a square crop box overlay on an image.
 * The crop area is always square, based on the shorter dimension of the image.
 * Users can drag corners (which resize proportionally) or the entire box to adjust position.
 */
@Composable
fun SquareCropBox(
    containerWidth: Float,
    containerHeight: Float,
    mediaAspectRatio: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAreaChanged: (area: Rect, original: Size) -> Unit,
    onCropDone: () -> Unit
) {
    val containerAspectRatio = rememberSaveable(containerWidth, containerHeight) {
        containerWidth / containerHeight
    }

    // Calculate the displayed image size
    var originalWidth by rememberSaveable {
        mutableFloatStateOf(
            if (containerAspectRatio > mediaAspectRatio) {
                containerHeight * mediaAspectRatio
            } else {
                containerWidth
            }
        )
    }
    var originalHeight by rememberSaveable {
        mutableFloatStateOf(
            if (containerAspectRatio > mediaAspectRatio) {
                containerHeight
            } else {
                containerWidth / mediaAspectRatio
            }
        )
    }

    // Square crop size - use the shorter dimension
    val squareSize = min(originalWidth, originalHeight)
    
    // Crop box position and size (always square)
    var size by rememberSaveable { mutableFloatStateOf(squareSize) }
    var top by rememberSaveable { mutableFloatStateOf((containerHeight - squareSize) / 2) }
    var left by rememberSaveable { mutableFloatStateOf((containerWidth - squareSize) / 2) }

    LaunchedEffect(mediaAspectRatio, containerWidth, containerHeight) {
        originalWidth = if (containerAspectRatio > mediaAspectRatio) {
            containerHeight * mediaAspectRatio
        } else {
            containerWidth
        }
        originalHeight = if (containerAspectRatio > mediaAspectRatio) {
            containerHeight
        } else {
            containerWidth / mediaAspectRatio
        }

        val newSquareSize = min(originalWidth, originalHeight)
        size = newSquareSize
        top = (containerHeight - newSquareSize) / 2
        left = (containerWidth - newSquareSize) / 2
    }

    LaunchedEffect(top, left, size, originalWidth, originalHeight) {
        onAreaChanged(
            Rect(
                top = top - ((containerHeight - originalHeight) / 2),
                left = left - ((containerWidth - originalWidth) / 2),
                bottom = top + size - ((containerHeight - originalHeight) / 2),
                right = left + size - ((containerWidth - originalWidth) / 2)
            ),
            Size(width = originalWidth, height = originalHeight)
        )
    }

    var selectedArea by remember { mutableStateOf(SelectedCropArea.None) }

    val animatedColor by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (selectedArea == SelectedCropArea.None) 0f else 0.6f),
        animationSpec = tween(durationMillis = CropAnimationConstants.DURATION),
        label = "guideline_color"
    )

    val localDensity = LocalDensity.current
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = CropAnimationConstants.expressiveTween(CropAnimationConstants.DURATION),
        label = "crop_alpha"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .background(Color.Transparent)
            .requiredSize(
                width = with(localDensity) { containerWidth.toDp() + 32.dp },
                height = with(localDensity) { containerHeight.toDp() + 32.dp }
            )
    ) {
        // Shading box (gives cutout effect)
        Box(
            modifier = modifier
                .offset(x = 16.dp, y = 16.dp)
                .size(width = containerWidth.dp, height = containerHeight.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val strokeWidth = 4.dp.toPx()

                    drawRect(
                        color = Color.Black.copy(alpha = 0.75f),
                        topLeft = Offset(
                            (containerWidth - originalWidth) / 2,
                            (containerHeight - originalHeight) / 2
                        ),
                        size = Size(originalWidth, originalHeight)
                    )

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left + strokeWidth / 2, top + strokeWidth / 2),
                        size = Size(size - strokeWidth, size - strokeWidth),
                        blendMode = BlendMode.DstOut
                    )
                }
        )

        // CropBox itself with handles
        Box(
            modifier = modifier
                .offset(x = 16.dp, y = 16.dp)
                .size(width = containerWidth.dp, height = containerHeight.dp)
                .drawWithContent {
                    drawContent()
                    val strokeWidth = 4.dp.toPx()
                    val cornerRadius = 2.dp.toPx()

                    // Border
                    drawOutline(
                        outline = Outline.Rounded(
                            roundRect = RoundRect(
                                left = left,
                                top = top,
                                right = left + size,
                                bottom = top + size,
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        ),
                        style = Stroke(width = strokeWidth),
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    val guidelineStrokeWidth = 2.dp.toPx()
                    
                    // Vertical guidelines
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + size / 3, top + strokeWidth / 2),
                        end = Offset(left + size / 3, top + size - strokeWidth / 2),
                        strokeWidth = guidelineStrokeWidth
                    )
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + size * 2 / 3, top + strokeWidth / 2),
                        end = Offset(left + size * 2 / 3, top + size - strokeWidth / 2),
                        strokeWidth = guidelineStrokeWidth
                    )

                    // Horizontal guidelines
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + strokeWidth / 2, top + size / 3),
                        end = Offset(left + size - strokeWidth / 2, top + size / 3),
                        strokeWidth = guidelineStrokeWidth
                    )
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + strokeWidth / 2, top + size * 2 / 3),
                        end = Offset(left + size - strokeWidth / 2, top + size * 2 / 3),
                        strokeWidth = guidelineStrokeWidth
                    )

                    // Corner handles
                    val arcStrokeWidth = 6.dp.toPx()
                    drawSquareCornerHandle(left, top, -90f, arcStrokeWidth, selectedArea == SelectedCropArea.TopLeftCorner)
                    drawSquareCornerHandle(left + size, top, 0f, arcStrokeWidth, selectedArea == SelectedCropArea.TopRightCorner)
                    drawSquareCornerHandle(left, top + size, 180f, arcStrokeWidth, selectedArea == SelectedCropArea.BottomLeftCorner)
                    drawSquareCornerHandle(left + size, top + size, 90f, arcStrokeWidth, selectedArea == SelectedCropArea.BottomRightCorner)
                }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            val maxTop = (containerHeight - originalHeight) / 2
                            val maxLeft = (containerWidth - originalWidth) / 2
                            val minSize = 80.dp.toPx() // Minimum crop size

                            detectDragGestures(
                                onDrag = { event, offset ->
                                    val distanceToTop = abs(top - event.position.y)
                                    val distanceToLeft = abs(left - event.position.x)
                                    val distanceToBottom = abs((top + size) - event.position.y)
                                    val distanceToRight = abs((left + size) - event.position.x)
                                    val threshold = 56.dp.toPx()

                                    when {
                                        // Center drag (whole crop box)
                                        (event.position.y in top + size / 3..top + size * 2 / 3 
                                            && event.position.x in left + size / 3..left + size * 2 / 3) 
                                            || selectedArea == SelectedCropArea.Whole -> {
                                            // Move the whole box, keeping it within image bounds
                                            val newLeft = (left + offset.x).coerceInSafe(maxLeft, maxLeft + originalWidth - size)
                                            val newTop = (top + offset.y).coerceInSafe(maxTop, maxTop + originalHeight - size)
                                            left = newLeft
                                            top = newTop
                                            selectedArea = SelectedCropArea.Whole
                                        }

                                        // Top left corner - resize proportionally
                                        ((distanceToTop <= threshold && distanceToTop <= distanceToBottom)
                                            && (distanceToLeft <= threshold && distanceToLeft <= distanceToRight)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.TopLeftCorner -> {
                                            // Use the average of x and y offset for uniform scaling
                                            val delta = (offset.x + offset.y) / 2
                                            val maxSize = min(originalWidth, originalHeight).coerceAtLeast(0f)
                                            val effectiveMinSize = min(minSize, maxSize)
                                            val newSize = (size - delta).coerceInSafe(effectiveMinSize, maxSize)
                                            val sizeDiff = size - newSize
                                            val newLeft = left + sizeDiff
                                            val newTop = top + sizeDiff
                                            
                                            if (newLeft >= maxLeft && newTop >= maxTop) {
                                                size = newSize
                                                left = newLeft
                                                top = newTop
                                            }
                                            selectedArea = SelectedCropArea.TopLeftCorner
                                        }

                                        // Bottom right corner - resize proportionally
                                        ((distanceToBottom <= threshold && distanceToBottom <= distanceToTop)
                                            && (distanceToRight <= threshold && distanceToRight <= distanceToLeft)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.BottomRightCorner -> {
                                            val delta = (offset.x + offset.y) / 2
                                            val maxSize = min(originalWidth, originalHeight).coerceAtLeast(0f)
                                            val effectiveMinSize = min(minSize, maxSize)
                                            val newSize = (size + delta).coerceInSafe(effectiveMinSize, maxSize)
                                            
                                            // Check bounds
                                            if (left + newSize <= maxLeft + originalWidth && top + newSize <= maxTop + originalHeight) {
                                                size = newSize
                                            }
                                            selectedArea = SelectedCropArea.BottomRightCorner
                                        }

                                        // Top right corner - resize proportionally
                                        ((distanceToTop <= threshold && distanceToTop <= distanceToBottom)
                                            && (distanceToRight <= threshold && distanceToRight <= distanceToLeft)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.TopRightCorner -> {
                                            val delta = (offset.x - offset.y) / 2
                                            val maxSize = min(originalWidth, originalHeight).coerceAtLeast(0f)
                                            val effectiveMinSize = min(minSize, maxSize)
                                            val newSize = (size + delta).coerceInSafe(effectiveMinSize, maxSize)
                                            val sizeDiff = newSize - size
                                            val newTop = top - sizeDiff
                                            
                                            if (newTop >= maxTop && left + newSize <= maxLeft + originalWidth) {
                                                size = newSize
                                                top = newTop
                                            }
                                            selectedArea = SelectedCropArea.TopRightCorner
                                        }

                                        // Bottom left corner - resize proportionally
                                        ((distanceToBottom <= threshold && distanceToBottom <= distanceToTop)
                                            && (distanceToLeft <= threshold && distanceToLeft <= distanceToRight)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.BottomLeftCorner -> {
                                            val delta = (-offset.x + offset.y) / 2
                                            val maxSize = min(originalWidth, originalHeight).coerceAtLeast(0f)
                                            val effectiveMinSize = min(minSize, maxSize)
                                            val newSize = (size + delta).coerceInSafe(effectiveMinSize, maxSize)
                                            val sizeDiff = newSize - size
                                            val newLeft = left - sizeDiff
                                            
                                            if (newLeft >= maxLeft && top + newSize <= maxTop + originalHeight) {
                                                size = newSize
                                                left = newLeft
                                            }
                                            selectedArea = SelectedCropArea.BottomLeftCorner
                                        }
                                    }
                                },
                                onDragEnd = {
                                    selectedArea = SelectedCropArea.None
                                    onCropDone()
                                }
                            )
                        }
                    } else Modifier
                )
        )
    }
}

private fun Float.coerceInSafe(minimumValue: Float, maximumValue: Float): Float {
    return if (maximumValue < minimumValue) {
        minimumValue
    } else {
        coerceIn(minimumValue, maximumValue)
    }
}

/**
 * Draws a corner handle arc at the specified position.
 */
private fun DrawScope.drawSquareCornerHandle(
    x: Float,
    y: Float,
    rotation: Float,
    strokeWidth: Float,
    isSelected: Boolean
) {
    val radius = 16.dp.toPx()
    val path = Path().apply {
        moveTo(x - radius / 2, y - radius / 2)
        lineTo(x, y - radius / 2)
        arcTo(
            rect = Rect(
                left = x - radius / 2,
                top = y - radius / 2,
                right = x + radius / 2,
                bottom = y + radius / 2
            ),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(x + radius / 2, y + radius / 2)
    }
    
    val bounds = path.getBounds()
    val center = Offset(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
    
    rotate(rotation, center) {
        drawPath(
            color = Color.White.copy(alpha = if (isSelected) 0.8f else 1f),
            path = path,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
