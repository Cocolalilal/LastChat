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

/**
 * A composable that displays a resizable crop box overlay on an image.
 * Users can drag corners, edges, or the entire box to adjust the crop area.
 */
@Composable
fun CropBox(
    containerWidth: Float,
    containerHeight: Float,
    mediaAspectRatio: Float,
    lockedAspectRatio: Float? = null,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAreaChanged: (area: Rect, original: Size) -> Unit,
    onCropDone: () -> Unit
) {
    val containerAspectRatio = rememberSaveable(containerWidth, containerHeight) {
        containerWidth / containerHeight
    }

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

    val initialCropSize = fitCropSize(originalWidth, originalHeight, lockedAspectRatio)
    var width by rememberSaveable { mutableFloatStateOf(initialCropSize.width) }
    var height by rememberSaveable { mutableFloatStateOf(initialCropSize.height) }
    var top by rememberSaveable { mutableFloatStateOf((containerHeight - height) / 2) }
    var left by rememberSaveable { mutableFloatStateOf((containerWidth - width) / 2) }

    LaunchedEffect(mediaAspectRatio, lockedAspectRatio, containerWidth, containerHeight) {
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

        val cropSize = fitCropSize(originalWidth, originalHeight, lockedAspectRatio)
        width = cropSize.width
        height = cropSize.height
        top = (containerHeight - height) / 2
        left = (containerWidth - width) / 2
    }

    LaunchedEffect(top, left, width, height, originalWidth, originalHeight) {
        onAreaChanged(
            Rect(
                top = top - ((containerHeight - originalHeight) / 2),
                left = left - ((containerWidth - originalWidth) / 2),
                bottom = top + height - ((containerHeight - originalHeight) / 2),
                right = left + width - ((containerWidth - originalWidth) / 2)
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
                        size = Size(width - strokeWidth, height - strokeWidth),
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
                                right = left + width,
                                bottom = top + height,
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
                        start = Offset(left + width / 3, top + strokeWidth / 2),
                        end = Offset(left + width / 3, top + height - strokeWidth / 2),
                        strokeWidth = guidelineStrokeWidth
                    )
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + width * 2 / 3, top + strokeWidth / 2),
                        end = Offset(left + width * 2 / 3, top + height - strokeWidth / 2),
                        strokeWidth = guidelineStrokeWidth
                    )

                    // Horizontal guidelines
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + strokeWidth / 2, top + height / 3),
                        end = Offset(left + width - strokeWidth / 2, top + height / 3),
                        strokeWidth = guidelineStrokeWidth
                    )
                    drawLine(
                        color = animatedColor,
                        start = Offset(left + strokeWidth / 2, top + height * 2 / 3),
                        end = Offset(left + width - strokeWidth / 2, top + height * 2 / 3),
                        strokeWidth = guidelineStrokeWidth
                    )

                    if (lockedAspectRatio == null) {
                        val arcStrokeWidth = 6.dp.toPx()
                        drawCornerHandle(left, top, -90f, arcStrokeWidth, selectedArea == SelectedCropArea.TopLeftCorner)
                        drawCornerHandle(left + width, top, 0f, arcStrokeWidth, selectedArea == SelectedCropArea.TopRightCorner)
                        drawCornerHandle(left, top + height, 180f, arcStrokeWidth, selectedArea == SelectedCropArea.BottomLeftCorner)
                        drawCornerHandle(left + width, top + height, 90f, arcStrokeWidth, selectedArea == SelectedCropArea.BottomRightCorner)
                    }
                }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(lockedAspectRatio) {
                            val maxTop = (containerHeight - originalHeight) / 2
                            val maxLeft = (containerWidth - originalWidth) / 2

                            detectDragGestures(
                                onDrag = { event, offset ->
                                    val distanceToTop = abs(top - event.position.y)
                                    val distanceToLeft = abs(left - event.position.x)
                                    val distanceToBottom = abs((top + height) - event.position.y)
                                    val distanceToRight = abs((left + width) - event.position.x)
                                    val threshold = 56.dp.toPx()

                                    when {
                                        // Center drag (whole crop box)
                                        lockedAspectRatio != null || selectedArea == SelectedCropArea.Whole ||
                                            (
                                                event.position.y in top + height / 3..top + height * 2 / 3 &&
                                                    event.position.x in left + width / 3..left + width * 2 / 3
                                                ) -> {
                                            if (left + offset.x >= maxLeft && left + offset.x + width <= maxLeft + originalWidth) {
                                                left += offset.x
                                            }
                                            if (top + offset.y >= maxTop && top + offset.y + height <= maxTop + originalHeight) {
                                                top += offset.y
                                            }
                                            selectedArea = SelectedCropArea.Whole
                                        }

                                        // Top left corner
                                        ((distanceToTop <= threshold && distanceToTop <= distanceToBottom)
                                            && (distanceToLeft <= threshold && distanceToLeft <= distanceToRight)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.TopLeftCorner -> {
                                            val newTop = top + offset.y
                                            val newHeight = height - offset.y
                                            if (newTop >= maxTop && newTop < (newTop + newHeight) - threshold) {
                                                top = newTop
                                                height = newHeight
                                            }
                                            val newLeft = left + offset.x
                                            val newWidth = width - offset.x
                                            if (newLeft >= maxLeft && newLeft < (newLeft + newWidth) - threshold) {
                                                left = newLeft
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.TopLeftCorner
                                        }

                                        // Bottom left corner
                                        ((distanceToBottom <= threshold && distanceToBottom <= distanceToTop)
                                            && (distanceToLeft <= threshold && distanceToLeft <= distanceToRight)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.BottomLeftCorner -> {
                                            val newHeight = height + offset.y
                                            if (top + newHeight <= maxTop + originalHeight && newHeight > threshold) {
                                                height = newHeight
                                            }
                                            val newLeft = left + offset.x
                                            val newWidth = width - offset.x
                                            if (newLeft >= maxLeft && newLeft < (newLeft + newWidth) - threshold) {
                                                left = newLeft
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.BottomLeftCorner
                                        }

                                        // Top right corner
                                        ((distanceToTop <= threshold && distanceToTop <= distanceToBottom)
                                            && (distanceToRight <= threshold && distanceToRight <= distanceToLeft)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.TopRightCorner -> {
                                            val newTop = top + offset.y
                                            val newHeight = height - offset.y
                                            if (newTop >= maxTop && newTop < (newTop + newHeight) - threshold) {
                                                top = newTop
                                                height = newHeight
                                            }
                                            val newWidth = width + offset.x
                                            if (left + newWidth <= maxLeft + originalWidth && newWidth > threshold) {
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.TopRightCorner
                                        }

                                        // Bottom right corner
                                        ((distanceToBottom <= threshold && distanceToBottom <= distanceToTop)
                                            && (distanceToRight <= threshold && distanceToRight <= distanceToLeft)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.BottomRightCorner -> {
                                            val newHeight = height + offset.y
                                            if (top + newHeight <= maxTop + originalHeight && newHeight > threshold) {
                                                height = newHeight
                                            }
                                            val newWidth = width + offset.x
                                            if (left + newWidth <= maxLeft + originalWidth && newWidth > threshold) {
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.BottomRightCorner
                                        }

                                        // Top edge
                                        ((distanceToTop <= threshold && distanceToTop <= distanceToBottom)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.TopEdge -> {
                                            val newTop = top + offset.y
                                            val newHeight = height - offset.y
                                            if (newTop >= maxTop && newTop < (newTop + newHeight) - threshold) {
                                                top = newTop
                                                height = newHeight
                                            }
                                            selectedArea = SelectedCropArea.TopEdge
                                        }

                                        // Left edge
                                        ((distanceToLeft <= threshold && distanceToLeft <= distanceToRight)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.LeftEdge -> {
                                            val newLeft = left + offset.x
                                            val newWidth = width - offset.x
                                            if (newLeft >= maxLeft && newLeft < (newLeft + newWidth) - threshold) {
                                                left = newLeft
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.LeftEdge
                                        }

                                        // Bottom edge
                                        ((distanceToBottom <= threshold && distanceToBottom <= distanceToTop)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.BottomEdge -> {
                                            val newHeight = height + offset.y
                                            if (top + newHeight <= maxTop + originalHeight && newHeight > threshold) {
                                                height = newHeight
                                            }
                                            selectedArea = SelectedCropArea.BottomEdge
                                        }

                                        // Right edge
                                        ((distanceToRight <= threshold && distanceToRight <= distanceToLeft)
                                            && selectedArea == SelectedCropArea.None)
                                            || selectedArea == SelectedCropArea.RightEdge -> {
                                            val newWidth = width + offset.x
                                            if (left + newWidth <= maxLeft + originalWidth && newWidth > threshold) {
                                                width = newWidth
                                            }
                                            selectedArea = SelectedCropArea.RightEdge
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

private fun fitCropSize(
    availableWidth: Float,
    availableHeight: Float,
    lockedAspectRatio: Float?
): Size {
    val targetRatio = lockedAspectRatio?.takeIf { it.isFinite() && it > 0f }
        ?: return Size(availableWidth, availableHeight)
    return if (availableWidth / availableHeight > targetRatio) {
        Size(width = availableHeight * targetRatio, height = availableHeight)
    } else {
        Size(width = availableWidth, height = availableWidth / targetRatio)
    }
}

/**
 * Draws a corner handle arc at the specified position.
 */
private fun DrawScope.drawCornerHandle(
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
