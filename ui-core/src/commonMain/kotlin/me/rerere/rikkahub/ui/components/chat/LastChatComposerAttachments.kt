package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Exact 84 dp production attachment strip, including animated scroll-edge masks. */
@Composable
fun LastChatComposerAttachmentRow(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val canScrollLeft by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val canScrollRight by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 ||
                lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
        }
    }
    val leftFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollLeft) 1f else 0f,
        animationSpec = tween(180),
        label = "attachment_left_fade",
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollRight) 1f else 0f,
        animationSpec = tween(180),
        label = "attachment_right_fade",
    )

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if ((leftFadeAlpha > 0f || rightFadeAlpha > 0f) && size.width > 0f) {
                    val fadeWidthPx = 22.dp.toPx()
                    val leftEnd = (fadeWidthPx / size.width).coerceAtMost(0.35f)
                    val rightStart = (1f - fadeWidthPx / size.width).coerceAtLeast(0.65f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 1f - leftFadeAlpha),
                                leftEnd to Color.Black,
                                rightStart to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - rightFadeAlpha),
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        content = content,
    )
}

/** Exact production sent-message attachment row used above Android message bubbles. */
@Composable
fun LastChatMessageAttachmentRow(
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val canScrollLeft by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val canScrollRight by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 ||
                lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
        }
    }
    val leftFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollLeft) 1f else 0f,
        animationSpec = tween(180),
        label = "message_attachment_left_fade",
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollRight) 1f else 0f,
        animationSpec = tween(180),
        label = "message_attachment_right_fade",
    )
    val horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp, horizontalAlignment),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if ((leftFadeAlpha > 0f || rightFadeAlpha > 0f) && size.width > 0f) {
                    val fadeWidthPx = 32.dp.toPx()
                    val leftEnd = (fadeWidthPx / size.width).coerceAtMost(0.35f)
                    val rightStart = (1f - fadeWidthPx / size.width).coerceAtLeast(0.65f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 1f - leftFadeAlpha),
                                leftEnd to Color.Black,
                                rightStart to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - rightFadeAlpha),
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        content = content,
    )
}

/** Production image tile. Image loading stays in the platform adapter through [content]. */
@Composable
fun LastChatComposerImageAttachment(
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    removeContentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "image_attachment_scale",
    )
    Box(
        modifier = modifier
            .size(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AttachmentShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.Center).size(60.dp),
            shape = AttachmentShape,
            tonalElevation = 4.dp,
            content = content,
        )
        LastChatComposerInsetRemoveButton(
            onClick = onRemove,
            contentDescription = removeContentDescription,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Production video/audio tile with the original secondary-color corner removal button. */
@Composable
fun LastChatComposerMediaAttachment(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = AttachmentShape,
            tonalElevation = 4.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        Icon(
            imageVector = RoundedCloseIcon,
            contentDescription = null,
            modifier = Modifier
                .clip(CircleShape)
                .size(24.dp)
                .clickable(onClick = onRemove)
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.secondary),
            tint = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

/** Production document chip, source-shared so both platforms render the same tile. */
@Composable
fun LastChatDocumentAttachmentTile(
    fileName: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val extension = fileName.substringAfterLast('.', "").uppercase()
        .takeIf { it.isNotBlank() } ?: "FILE"
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = AttachmentShape,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = extension.take(4),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (onRemove != null) {
            LastChatComposerInsetRemoveButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun LastChatComposerVideoIcon() {
    Icon(RoundedVideoLibraryIcon, contentDescription = null)
}

@Composable
fun LastChatComposerAudioIcon() {
    Icon(RoundedAudioFileIcon, contentDescription = null)
}

@Composable
private fun LastChatComposerInsetRemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            modifier = Modifier.padding(4.dp).size(22.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = RoundedCloseIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val AttachmentShape = RoundedCornerShape(12.dp)

private fun attachmentIcon(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    )
}.build()

private val RoundedCloseIcon by lazy {
    attachmentIcon("Rounded.Close", "M18.3,5.71a0.996,0.996 0,0 0,-1.41 0L12,10.59 7.11,5.7A0.996,0.996 0,1 0,5.7 7.11L10.59,12 5.7,16.89a0.996,0.996 0,1 0,1.41 1.41L12,13.41l4.89,4.89a0.996,0.996 0,1 0,1.41 -1.41L13.41,12l4.89,-4.89c0.38,-0.38 0.38,-1.02 0,-1.4z")
}

private val RoundedVideoLibraryIcon by lazy {
    attachmentIcon(
        "Rounded.VideoLibrary",
        "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM15,11.47l-3.21,2.14c-0.33,0.22 -0.79,-0.01 -0.79,-0.42V8.81c0,-0.4 0.45,-0.64 0.79,-0.42L15,10.53c0.3,0.2 0.3,0.74 0,0.94z",
    )
}

private val RoundedAudioFileIcon by lazy {
    attachmentIcon(
        "Rounded.AudioFile",
        "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM15,13h-2v2.75C13,16.99 11.99,18 10.75,18S8.5,16.99 8.5,15.75s1.01,-2.25 2.25,-2.25c0.46,0 0.89,0.14 1.25,0.38V12c0,-0.55 0.45,-1 1,-1h2c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM13,9V3.5L18.5,9H13z",
    )
}
