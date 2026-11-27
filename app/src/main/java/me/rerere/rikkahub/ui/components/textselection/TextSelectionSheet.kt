package me.rerere.rikkahub.ui.components.textselection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getChatModelForAssistant
import me.rerere.rikkahub.data.datastore.resolveTextSelectionAssistant
import me.rerere.rikkahub.data.model.TextSelectionAction
import me.rerere.rikkahub.ui.activity.QuickAskAttachment
import me.rerere.rikkahub.ui.activity.TextSelectionState
import me.rerere.rikkahub.ui.activity.TextSelectionVM
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.ThinkingOrb
import me.rerere.rikkahub.ui.components.richtext.ThinkingOrbMode
import me.rerere.rikkahub.ui.components.ui.DocumentChip
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.rememberControlPressValue
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.max

@Composable
fun TextSelectionSheet(
    viewModel: TextSelectionVM,
    onDismiss: () -> Unit,
    onContinueInApp: () -> Unit,
    onOpenInStudio: (String) -> Unit = {},
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val settings = LocalSettings.current
    val reduceMotion = LocalMotionPolicy.current.reduceMotion

    var isVisible by remember { mutableStateOf(false) }
    val targetAlpha = if (isVisible) 0.5f else 0f
    val backgroundAlpha by if (reduceMotion) {
        rememberUpdatedState(targetAlpha)
    } else {
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(220),
            label = "background_alpha"
        )
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val resolvedAssistant = settings.resolveTextSelectionAssistant()
    val defaultModel = settings.getChatModelForAssistant(resolvedAssistant)
    val defaultBadge = buildString {
        append(resolvedAssistant.name.ifBlank { "Assistant" })
        defaultModel?.let {
            append(" · ")
            append(it.displayName.ifBlank { it.modelId })
        }
    }

    val containerColor = if (amoledMode && isDarkMode) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = if (reduceMotion) {
                fadeIn(tween(90))
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                ) + fadeIn(animationSpec = tween(180))
            },
            exit = if (reduceMotion) {
                fadeOut(tween(80))
            } else {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f)
                ) + fadeOut(animationSpec = tween(140))
            }
        ) {
            CompositionLocalProvider(
                LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .lastChatBlurEffect(containerColor, QuickAskOuterShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .then(
                            if (reduceMotion) {
                                Modifier
                            } else {
                                Modifier.animateContentSize(
                                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                                )
                            }
                        ),
                    shape = QuickAskOuterShape,
                    color = containerColor,
                    border = lastChatSoftEdgeBorder(containerColor),
                    tonalElevation = if (amoledMode && isDarkMode) 0.dp else 4.dp
                ) {
                    AnimatedContent(
                        targetState = viewModel.state,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(tween(90)) togetherWith fadeOut(tween(80))
                            } else {
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            }
                        },
                        contentKey = { it::class.simpleName },
                        label = "state_transition"
                    ) { state ->
                        when (state) {
                            is TextSelectionState.ActionSelection -> {
                                val enabledActions = settings.textSelectionConfig.actions.filter { it.enabled }
                                ActionSelectionContent(
                                    selectedText = viewModel.selectedText,
                                    attachments = viewModel.inputData.attachments,
                                    actions = enabledActions,
                                    assistantModelBadge = defaultBadge,
                                    onActionSelected = { viewModel.onActionSelected(it) },
                                    onDismiss = onDismiss
                                )
                            }

                            is TextSelectionState.CustomPrompt -> {
                                CustomPromptContent(
                                    action = state.action,
                                    selectedText = viewModel.selectedText,
                                    attachments = viewModel.inputData.attachments,
                                    prompt = viewModel.customPrompt,
                                    assistantModelBadge = defaultBadge,
                                    onPromptChange = { viewModel.updateCustomPrompt(it) },
                                    onSubmit = { viewModel.submitCustomPrompt() },
                                    onBack = { viewModel.backToActionSelection() },
                                    onDismiss = onDismiss
                                )
                            }

                            is TextSelectionState.Loading -> {
                                LoadingContent(
                                    action = state.action,
                                    actionName = state.actionName,
                                    badge = "${state.assistantName} · ${state.modelName}",
                                    onBack = { viewModel.backToActionSelection() },
                                    onStop = { viewModel.cancelGeneration() },
                                )
                            }

                            is TextSelectionState.Result -> {
                                ResultContent(
                                    state = state,
                                    reduceMotion = reduceMotion,
                                    onBack = { viewModel.backToActionSelection() },
                                    onStop = { viewModel.cancelGeneration() },
                                    onRegenerate = { viewModel.regenerate() },
                                    onToggleThinking = { viewModel.toggleThinkingExpanded() },
                                    onContinueInApp = onContinueInApp,
                                    onDismiss = onDismiss,
                                )
                            }

                            is TextSelectionState.Error -> {
                                ErrorContent(
                                    message = state.message,
                                    canRetry = state.action != null,
                                    onRetry = {
                                        if (state.action != null) {
                                            viewModel.regenerate()
                                        } else {
                                            viewModel.backToActionSelection()
                                        }
                                    },
                                    onBack = { viewModel.backToActionSelection() },
                                    onDismiss = onDismiss,
                                )
                            }

                            is TextSelectionState.SavedToStudio -> {
                                SavedToStudioContent(
                                    state = state,
                                    onOpenInStudio = { onOpenInStudio(state.itemId) },
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAskHeaderBar(
    title: String,
    badge: String?,
    icon: ImageVector? = null,
    showAppMark: Boolean = false,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            TactileIconButton(
                onClick = onBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
            }
        } else if (showAppMark) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_lastchat_foreground),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun ActionSelectionContent(
    selectedText: String,
    attachments: List<QuickAskAttachment>,
    actions: List<TextSelectionAction>,
    assistantModelBadge: String,
    onActionSelected: (TextSelectionAction) -> Unit,
    onDismiss: () -> Unit
) {
    var previewExpanded by remember(selectedText) { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAskHeaderBar(
            title = stringResource(R.string.text_selection_menu_label),
            badge = assistantModelBadge,
            showAppMark = true,
            trailing = {
                TactileIconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        if (selectedText.isNotBlank()) {
            val isLong = selectedText.length > 140 || selectedText.count { it == '\n' } >= 3
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(QuickAskInnerShape)
                    .then(
                        if (isLong) {
                            Modifier.clickable {
                                haptics.perform(HapticPattern.Selection)
                                previewExpanded = !previewExpanded
                            }
                        } else {
                            Modifier
                        }
                    ),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (previewExpanded) 10 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLong) {
                        Text(
                            text = if (previewExpanded) "Tap to collapse" else "Tap to expand selection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        attachments.forEach { attachment ->
            if (attachment.mimeType?.startsWith("image/") == true) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    )
                }
            } else {
                DocumentChip(
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    shape = QuickAskInnerShape
                )
            }
        }

        if (actions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "No Ask LastChat presets are enabled. Enable or add presets in Settings -> Android Integration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            val rows = actions.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { rowIndex, rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowActions.forEachIndexed { colIndex, action ->
                            val isLastOdd = rowIndex == rows.lastIndex && rowActions.size == 1
                            QuickActionButton(
                                modifier = Modifier.weight(if (isLastOdd) 2f else 1f),
                                icon = quickAskIconForName(action.icon),
                                label = action.name,
                                shape = quickAskGroupedButtonShape(rowIndex, colIndex, rows.size, rowActions.size),
                                onClick = { onActionSelected(action) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberControlPressValue(isPressed, 0.95f, "button_scale")

    CompositionLocalProvider(
        LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current
    ) {
        Surface(
            modifier = modifier
                .heightIn(min = 60.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    haptics.perform(HapticPattern.Pop)
                    onClick()
                },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = if (amoledMode && isDarkMode) 0.dp else 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CustomPromptContent(
    action: TextSelectionAction,
    selectedText: String,
    attachments: List<QuickAskAttachment>,
    prompt: String,
    assistantModelBadge: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSubmit = prompt.isNotBlank() || action.saveToStudio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAskHeaderBar(
            title = action.name,
            badge = assistantModelBadge,
            onBack = onBack,
            trailing = {
                TactileIconButton(
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        if (selectedText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        attachments.forEach { attachment ->
            if (attachment.mimeType?.startsWith("image/") == true) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    )
                }
            } else {
                DocumentChip(
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    shape = QuickAskInnerShape
                )
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (action.saveToStudio) "Add an optional note (or tap send to save)…"
                    else stringResource(R.string.text_selection_custom_placeholder)
                )
            },
            shape = QuickAskInnerShape,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSubmit) onSubmit() }
            ),
            trailingIcon = {
                TactileIconButton(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    contentDescription = stringResource(R.string.send)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = if (canSubmit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun LoadingContent(
    action: TextSelectionAction,
    actionName: String,
    badge: String,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickAskHeaderBar(
            title = actionName,
            badge = badge,
            icon = quickAskIconForName(action.icon),
            onBack = onBack,
            trailing = {
                TactileIconButton(
                    onClick = onStop,
                    contentDescription = stringResource(R.string.stop),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoadingIndicator()
            Text(
                text = stringResource(R.string.text_selection_generating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultContent(
    state: TextSelectionState.Result,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onToggleThinking: () -> Unit,
    onContinueInApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()
    val copiedMessage = stringResource(R.string.copy)

    val answerScrollState = rememberScrollState()
    var userScrolledUp by remember(state.action.id) { mutableStateOf(false) }

    LaunchedEffect(answerScrollState.isScrollInProgress, answerScrollState.value, answerScrollState.maxValue) {
        if (answerScrollState.isScrollInProgress) {
            userScrolledUp = answerScrollState.value < (answerScrollState.maxValue - 24).coerceAtLeast(0)
        } else if (answerScrollState.value >= (answerScrollState.maxValue - 12).coerceAtLeast(0)) {
            userScrolledUp = false
        }
    }

    LaunchedEffect(state.responseText, state.isStreaming) {
        if (state.isStreaming && !userScrolledUp && answerScrollState.maxValue > 0) {
            answerScrollState.scrollTo(answerScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .then(
                if (reduceMotion) Modifier
                else Modifier.animateContentSize(spring(dampingRatio = 0.84f, stiffness = 360f))
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAskHeaderBar(
            title = state.actionName,
            badge = "${state.assistantName} · ${state.modelName}",
            onBack = onBack,
            trailing = {
                if (state.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                        TactileIconButton(
                            onClick = onStop,
                            contentDescription = stringResource(R.string.stop),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                } else {
                    TactileIconButton(
                        onClick = onDismiss,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        if (state.isReasoning || state.reasoningText.isNotBlank()) {
            QuickAskThinkingCard(
                reasoningText = state.reasoningText,
                isReasoning = state.isReasoning,
                startedAtMs = state.reasoningStartedAtMs,
                durationMs = state.reasoningDurationMs,
                expanded = state.isThinkingExpanded,
                reduceMotion = reduceMotion,
                onToggle = {
                    haptics.perform(HapticPattern.Selection)
                    onToggleThinking()
                },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 320.dp),
            shape = QuickAskInnerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(answerScrollState)
            ) {
                if (state.responseText.isBlank()) {
                    Text(
                        text = if (state.isReasoning) {
                            "Thinking through response…"
                        } else {
                            stringResource(R.string.text_selection_generating)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MarkdownBlock(
                        content = state.responseText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (state.isStreaming) {
            TactileActionSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.errorContainer,
                onClick = onStop
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (state.responseText.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileActionSurface(
                    modifier = Modifier.weight(1f),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(state.responseText))
                        toaster.show(copiedMessage, type = ToastType.Success)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.text_selection_copy),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                TactileActionSurface(
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = onRegenerate
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.regenerate),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.action.allowContinueInChat) {
                    TactileActionSurface(
                        modifier = Modifier.weight(1f),
                        shape = QuickAskInnerShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = onContinueInApp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.text_selection_continue_chat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAskThinkingCard(
    reasoningText: String,
    isReasoning: Boolean,
    startedAtMs: Long?,
    durationMs: Long?,
    expanded: Boolean,
    reduceMotion: Boolean,
    onToggle: () -> Unit,
) {
    var nowMs by remember(startedAtMs, isReasoning) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(isReasoning, startedAtMs) {
        if (isReasoning) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(250)
            }
        }
    }

    val liveElapsedSec = startedAtMs?.let { max(0L, (nowMs - it) / 1000L) }
    val finalElapsedSec = durationMs?.let { max(1L, (it + 500L) / 1000L) }
    val statusLabel = when {
        isReasoning && liveElapsedSec != null && liveElapsedSec > 0L -> "Thinking… ${liveElapsedSec}s"
        isReasoning -> "Thinking…"
        finalElapsedSec != null -> "Thought for ${finalElapsedSec}s"
        else -> "Thought process"
    }

    val chevronRotation by if (reduceMotion) {
        rememberUpdatedState(if (expanded) 180f else 0f)
    } else {
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
            label = "thinking_chevron_rotation"
        )
    }

    val previewLine = remember(reasoningText) {
        reasoningText.trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QuickAskInnerShape),
        shape = QuickAskInnerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isReasoning && !reduceMotion) {
                    ThinkingOrb(
                        size = 20.dp,
                        mode = ThinkingOrbMode.ORBITS
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!expanded && previewLine.isNotBlank()) {
                        Text(
                            text = previewLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }

            ExpandableContent(visible = expanded && reasoningText.isNotBlank()) {
                val traceScrollState = rememberScrollState()
                LaunchedEffect(reasoningText, isReasoning) {
                    if (isReasoning && traceScrollState.maxValue > 0) {
                        traceScrollState.scrollTo(traceScrollState.maxValue)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .verticalScroll(traceScrollState)
                ) {
                    Text(
                        text = reasoningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedToStudioContent(
    state: TextSelectionState.SavedToStudio,
    onOpenInStudio: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Saved to Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TactileIconButton(
                onClick = onDismiss,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (state.isSummarizing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = "Generating AI title & summary…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TactileActionSurface(
                modifier = Modifier.weight(1f),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenInStudio,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Open in Studio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            TactileActionSurface(
                modifier = Modifier.weight(0.7f),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                onClick = onDismiss,
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TactileIconButton(
                onClick = onDismiss,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileActionSurface(
                modifier = Modifier.weight(1f),
                shape = QuickAskInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                onClick = onBack
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.back),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (canRetry) {
                TactileActionSurface(
                    modifier = Modifier.weight(1f),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    onClick = onRetry
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.regenerate),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberControlPressValue(isPressed && enabled, 0.88f, "icon_button_scale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun TactileActionSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    color: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberControlPressValue(isPressed, 0.94f, "action_surface_scale")

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            },
        shape = shape,
        color = color
    ) {
        content()
    }
}
