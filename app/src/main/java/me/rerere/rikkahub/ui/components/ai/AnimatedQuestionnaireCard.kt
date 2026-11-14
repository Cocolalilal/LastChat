package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.AskUserOption
import me.rerere.rikkahub.data.ai.tools.AskUserQuestionnaire
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_DAMPING
import me.rerere.rikkahub.phoneuse.PHONE_MOTION_STIFFNESS
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.roundToInt

internal data class QuestionnaireCardState(
    val toolCallId: String,
    val questionnaire: AskUserQuestionnaire,
    val currentIndex: Int,
    val selectedOptionLabel: String?,
)

/** One rounded surface owns geometry; outgoing question snapshots survive until their fade ends. */
@Composable
internal fun AnimatedQuestionnaireCard(
    state: QuestionnaireCardState?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onSelectOption: (AskUserOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberSystemMotionPolicy().reduceMotion
    val target by rememberUpdatedState(state)
    val transition = updateTransition(state, label = "questionnaireCard")
    val opacity by transition.animateFloat(
        transitionSpec = { if (reduced) snap() else tween(200) },
        label = "questionnaireSurfaceOpacity",
    ) { if (it == null) 0f else 1f }
    val gap by transition.animateDp(
        transitionSpec = { if (reduced) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS) },
        label = "questionnaireComposerGap",
    ) { if (it == null) 0.dp else 8.dp }
    Column(modifier.fillMaxWidth()) {
        Surface(
            shape = AppShapes.CardLarge,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f * opacity)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            transition.AnimatedContent(
                transitionSpec = {
                    (fadeIn(if (reduced) snap() else tween(200)) togetherWith
                        fadeOut(if (reduced) snap() else tween(160))).using(
                        SizeTransform(clip = false) { _, _ ->
                            if (reduced) snap() else spring(
                                dampingRatio = PHONE_MOTION_DAMPING,
                                stiffness = PHONE_MOTION_STIFFNESS,
                                visibilityThreshold = IntSize(1, 1),
                            )
                        },
                    )
                },
                contentAlignment = Alignment.TopStart,
                contentKey = { it?.let { value -> value.toolCallId to value.currentIndex } },
            ) { displayed ->
                if (displayed == null) {
                    Spacer(Modifier.fillMaxWidth().height(0.dp))
                } else {
                    fun isCurrentQuestion() = target?.let {
                        it.toolCallId == displayed.toolCallId && it.currentIndex == displayed.currentIndex
                    } == true
                    val interactive = isCurrentQuestion()
                    CharacterQuestionsContent(
                        state = displayed,
                        enabled = interactive,
                        onPrevious = { if (isCurrentQuestion()) onPrevious() },
                        onNext = { if (isCurrentQuestion()) onNext() },
                        onDismiss = { if (isCurrentQuestion()) onDismiss() },
                        onSelectOption = { if (isCurrentQuestion()) onSelectOption(it) },
                        modifier = if (interactive) Modifier else Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        Spacer(Modifier.height(gap))
    }
}

@Composable
private fun CharacterQuestionsContent(
    state: QuestionnaireCardState,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onSelectOption: (AskUserOption) -> Unit,
    modifier: Modifier,
) {
    val question = state.questionnaire.questions.getOrNull(state.currentIndex) ?: return
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPrevious, enabled = enabled && state.currentIndex > 0,
                    modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    stringResource(R.string.character_questions_progress,
                        state.currentIndex + 1, state.questionnaire.questions.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onNext,
                    enabled = enabled && state.currentIndex < state.questionnaire.questions.lastIndex,
                    modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowRight, stringResource(R.string.next))
                }
            }
            IconButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.banner_dismiss))
            }
        }
        Text(question.question, style = MaterialTheme.typography.titleMedium)
        question.options.forEach { option ->
            CharacterQuestionOptionRow(option, state.selectedOptionLabel == option.label, enabled) {
                onSelectOption(option)
            }
        }
    }
}

@Composable
private fun CharacterQuestionOptionRow(
    option: AskUserOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics(enabled = LocalSettings.current.displaySetting.enableUIHaptics)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberSystemMotionPolicy().reduceMotion
    val scale by animateFloatAsState(
        if (isPressed) 0.95f else 1f,
        if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "questionOptionScale",
    )
    Surface(
        onClick = { haptics.perform(HapticPattern.Pop); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            option.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The add pill and its row spacing use one fraction, including their final pixel. */
@Composable
internal fun rememberComposerLeadingProgress(visible: Boolean): Float {
    val reduced = rememberSystemMotionPolicy().reduceMotion
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        if (reduced) snap() else spring(PHONE_MOTION_DAMPING, PHONE_MOTION_STIFFNESS,
            visibilityThreshold = 0.0005f),
        label = "composerLeadingPill",
    )
    return progress.coerceIn(0f, 1f)
}

@Composable
internal fun ComposerLeadingReveal(
    progress: Float,
    interactive: Boolean,
    content: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    Box(Modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout((child.width * progress).roundToInt(), child.height) { child.placeRelative(0, 0) }
    }.graphicsLayer {
        alpha = progress
        scaleX = progress
        scaleY = progress
        transformOrigin = TransformOrigin(if (direction == LayoutDirection.Ltr) 0f else 1f, 0.5f)
    }.then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) { content() }
}
