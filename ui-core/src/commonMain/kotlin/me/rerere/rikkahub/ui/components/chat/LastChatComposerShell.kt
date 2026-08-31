package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val LastChatComposerInputShape = RoundedCornerShape(24.dp)

enum class LastChatComposerAction {
    Picker,
    Stt,
    SttRecording,
    SttFinalizing,
    Send,
    Loading,
    QuestionnaireNext,
    QuestionnaireSubmit,
    ToolApprovalDeny,
}

/** Exact bottom-aligned production composer row used by Android and iOS. */
@Composable
fun LastChatComposerRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** The production 48 dp add/record Surface; platform adapters provide behavior and icon. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LastChatComposerAddButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        ) {
            content()
        }
    }
}

/** The production 24 dp input capsule; text, attachments, and actions are slotted inside. */
@Composable
fun RowScope.LastChatComposerCapsule(
    modifier: Modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = LastChatComposerInputShape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        content()
    }
}

/** The production embedded 36 dp action with the same depth transition as Android. */
@Composable
fun LastChatComposerActionButton(
    action: LastChatComposerAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(36.dp),
    containerColorOverride: Color? = null,
    content: @Composable (LastChatComposerAction) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = containerColorOverride ?: action.defaultContainerColor(),
        animationSpec = tween(250),
        label = "ActionContainerColor",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    val outFadeSpec = tween<Float>(150)
                    val inFadeSpec = tween<Float>(150, delayMillis = 100)
                    val depthScale = 0.6f
                    val initialRank = initialState.depthRank()
                    val targetRank = targetState.depthRank()
                    if (targetState == LastChatComposerAction.QuestionnaireNext) {
                        (slideInHorizontally(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) +
                            scaleIn(tween(250), initialScale = depthScale)) togetherWith
                            (slideOutHorizontally(tween(250)) { it / 2 } + fadeOut(outFadeSpec) +
                                scaleOut(tween(250), targetScale = depthScale))
                    } else if (initialState == LastChatComposerAction.QuestionnaireNext) {
                        (slideInHorizontally(tween(250)) { it / 2 } + fadeIn(inFadeSpec) +
                            scaleIn(tween(250), initialScale = depthScale)) togetherWith
                            (slideOutHorizontally(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) +
                                scaleOut(tween(250), targetScale = depthScale))
                    } else if (targetRank > initialRank) {
                        (slideInVertically(tween(250)) { it / 2 } + fadeIn(inFadeSpec) +
                            scaleIn(tween(250), initialScale = depthScale)) togetherWith
                            (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(outFadeSpec) +
                                scaleOut(tween(250), targetScale = depthScale))
                    } else if (targetRank < initialRank) {
                        (slideInVertically(tween(250)) { -it / 2 } + fadeIn(inFadeSpec) +
                            scaleIn(tween(250), initialScale = depthScale)) togetherWith
                            (slideOutVertically(tween(250)) { it / 2 } + fadeOut(outFadeSpec) +
                                scaleOut(tween(250), targetScale = depthScale))
                    } else {
                        (fadeIn(inFadeSpec) + scaleIn(tween(250), initialScale = depthScale)) togetherWith
                            (fadeOut(outFadeSpec) + scaleOut(tween(250), targetScale = depthScale))
                    }
                },
                contentAlignment = Alignment.Center,
                label = "ActionContent",
            ) { current -> content(current) }
        }
    }
}

@Composable
fun LastChatComposerDefaultActionContent(
    action: LastChatComposerAction,
    pickerContent: @Composable () -> Unit,
) {
    when (action) {
        LastChatComposerAction.Picker -> pickerContent()
        LastChatComposerAction.Send,
        LastChatComposerAction.QuestionnaireSubmit -> Icon(
            RoundedArrowUpwardIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
        LastChatComposerAction.Loading -> Icon(
            RoundedStopIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Unit
    }
}

@Composable
private fun LastChatComposerAction.defaultContainerColor(): Color = when (this) {
    LastChatComposerAction.Loading,
    LastChatComposerAction.ToolApprovalDeny -> MaterialTheme.colorScheme.errorContainer
    LastChatComposerAction.Send,
    LastChatComposerAction.QuestionnaireNext,
    LastChatComposerAction.QuestionnaireSubmit,
    LastChatComposerAction.SttRecording -> MaterialTheme.colorScheme.primary
    LastChatComposerAction.Picker,
    LastChatComposerAction.Stt,
    LastChatComposerAction.SttFinalizing -> Color.Transparent
}

private fun LastChatComposerAction.depthRank(): Int = when (this) {
    LastChatComposerAction.Picker,
    LastChatComposerAction.Stt,
    LastChatComposerAction.SttRecording,
    LastChatComposerAction.SttFinalizing -> 0
    LastChatComposerAction.Send,
    LastChatComposerAction.QuestionnaireNext,
    LastChatComposerAction.QuestionnaireSubmit -> 1
    LastChatComposerAction.Loading,
    LastChatComposerAction.ToolApprovalDeny -> 2
}

@Composable
fun LastChatComposerAddIcon(contentDescription: String? = null) {
    Icon(
        RoundedAddIcon,
        contentDescription = contentDescription,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun composerIcon(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()

private val RoundedAddIcon by lazy {
    composerIcon(
        "Rounded.Add",
        "M18,13h-5v5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-5H6c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h5V6c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v5h5c0.55,0 1,0.45 1,1s-0.45,1 -1,1z",
    )
}

private val RoundedArrowUpwardIcon by lazy {
    composerIcon(
        "Rounded.ArrowUpward",
        "M13,19V7.83l5.59,5.58L20,12l-8,-8 -8,8 1.41,1.41L11,7.83V19c0,0.55 0.45,1 1,1s1,-0.45 1,-1z",
    )
}

private val RoundedStopIcon by lazy {
    composerIcon(
        "Rounded.Stop",
        "M8,6h8c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2H8c-1.1,0 -2,-0.9 -2,-2V8c0,-1.1 0.9,-2 2,-2z",
    )
}
