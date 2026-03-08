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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.QuickAskAttachment
import me.rerere.rikkahub.ui.activity.QuickAction
import me.rerere.rikkahub.ui.activity.TextSelectionState
import me.rerere.rikkahub.ui.activity.TextSelectionVM
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.DocumentChip
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun TextSelectionSheet(
    viewModel: TextSelectionVM,
    onDismiss: () -> Unit,
    onContinueInApp: () -> Unit,
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    
    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.5f else 0f,
        animationSpec = tween(300),
        label = "background_alpha"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Full screen semi-transparent background with animated fade
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
        // Animated bottom sheet with slide-up entry
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            CompositionLocalProvider(LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                    .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume click to prevent dismissing
                        ),
                    shape = QuickAskOuterShape,
                    color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 8.dp
                ) {
                    AnimatedContent(
                        targetState = viewModel.state,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                        },
                        // Use state class name as key to only animate when state TYPE changes,
                        // not when responseText changes within the same Result state
                        contentKey = { it::class.simpleName },
                        label = "state_transition"
                    ) { state ->
                        when (state) {
                            is TextSelectionState.ActionSelection -> {
                                ActionSelectionContent(
                                    selectedText = viewModel.selectedText,
                                    attachments = viewModel.inputData.attachments,
                                    onActionSelected = { viewModel.onActionSelected(it) },
                                    onDismiss = onDismiss
                                )
                            }
                            is TextSelectionState.CustomPrompt -> {
                                CustomPromptContent(
                                    prompt = viewModel.customPrompt,
                                    onPromptChange = { viewModel.updateCustomPrompt(it) },
                                    onSubmit = { viewModel.submitCustomPrompt() },
                                    onBack = { viewModel.backToActionSelection() }
                                )
                            }
                            is TextSelectionState.Loading -> {
                                LoadingContent()
                            }
                            is TextSelectionState.Result -> {
                                ResultContent(
                                    responseText = state.responseText,
                                    isStreaming = state.isStreaming,
                                    isReasoning = state.isReasoning,
                                    isTranslate = viewModel.lastAction == QuickAction.TRANSLATE,
                                    onBack = { viewModel.backToActionSelection() },
                                    onStop = { viewModel.cancelGeneration() },
                                    onContinueInApp = onContinueInApp
                                )
                            }
                            is TextSelectionState.Error -> {
                                ErrorContent(
                                    message = state.message,
                                    onRetry = { viewModel.backToActionSelection() }
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
private fun ActionSelectionContent(
    selectedText: String,
    attachments: List<QuickAskAttachment>,
    onActionSelected: (QuickAction) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header - just title
        Text(
            text = stringResource(R.string.text_selection_menu_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
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

        // Action buttons grid (2x2)
        val quickActions = listOf(
            Triple(Icons.Rounded.Translate, stringResource(R.string.text_selection_translate), QuickAction.TRANSLATE),
            Triple(Icons.Rounded.Lightbulb, stringResource(R.string.text_selection_explain), QuickAction.EXPLAIN),
            Triple(Icons.Rounded.Summarize, stringResource(R.string.text_selection_summarize), QuickAction.SUMMARIZE),
            Triple(Icons.Rounded.AutoAwesome, stringResource(R.string.text_selection_ask), QuickAction.CUSTOM)
        )
        val rows = quickActions.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { rowIndex, rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowActions.forEachIndexed { colIndex, (icon, label, action) ->
                        val isLastOdd = rowIndex == rows.lastIndex && rowActions.size == 1
                        QuickActionButton(
                            modifier = Modifier.weight(if (isLastOdd) 2f else 1f),
                            icon = icon,
                            label = label,
                            shape = quickAskGroupedButtonShape(rowIndex, colIndex, rows.size, rowActions.size),
                            onClick = { onActionSelected(action) }
                        )
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "button_scale"
    )

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides if (amoledMode && isDarkMode) 0.dp else LocalAbsoluteTonalElevation.current) {
        Surface(
            modifier = modifier
                .heightIn(min = 64.dp)
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
            color = if (amoledMode && isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (amoledMode && isDarkMode) 0.dp else 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
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
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileIconButton(
                onClick = onBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.text_selection_ask),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Custom prompt input
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.text_selection_custom_placeholder))
            },
            shape = QuickAskInnerShape,
            trailingIcon = {
                TactileIconButton(
                    onClick = onSubmit,
                    enabled = prompt.isNotBlank(),
                    contentDescription = stringResource(R.string.send)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = if (prompt.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LoadingIndicator()
        Text(
            text = stringResource(R.string.text_selection_generating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultContent(
    responseText: String,
    isStreaming: Boolean,
    isReasoning: Boolean = false,
    isTranslate: Boolean = false,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onContinueInApp: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current
    val copiedMessage = stringResource(R.string.copy)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TactileIconButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LoadingIndicator()
                        Text(
                            text = if (isReasoning) "Reasoning..." else stringResource(R.string.text_selection_generating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (isStreaming) {
                TactileIconButton(
                    onClick = onStop,
                    contentDescription = stringResource(R.string.stop),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Response text with markdown rendering - scrollable if long
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 300.dp),
            shape = QuickAskInnerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (responseText.isBlank()) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MarkdownBlock(
                        content = responseText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Action buttons
        if (!isStreaming && responseText.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy button
                TactileActionSurface(
                    modifier = Modifier.weight(1f),
                    shape = QuickAskInnerShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(responseText))
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

                // Continue in app button - hide for translate action
                if (!isTranslate) {
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
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚠️ $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TactileActionSurface(
            shape = QuickAskInnerShape,
            color = MaterialTheme.colorScheme.errorContainer,
            onClick = onRetry
        ) {
            Text(
                text = stringResource(R.string.back),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "icon_button_scale"
    )

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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "action_surface_scale"
    )

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
