package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.AppShapes

/** The character prompt field, shared without changing its presentation. */
@Composable
fun PromptInput(
    title: String,
    state: TextFieldState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocusLost: () -> Unit = {},
    tokenCount: @Composable () -> Unit = { PromptTokenCount(state) { it.length / 4 } },
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapes.CardLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                tokenCount()
                Spacer(Modifier.weight(1f))
                IconButton(enabled = enabled, onClick = { focusManager.clearFocus(); onExpand() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Fullscreen, "Expand $title")
                }
            }
            OutlinedTextField(
                state = state, enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = title }
                    .onFocusChanged { if (!it.isFocused) onFocusLost() },
                trailingIcon = null,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5, maxHeightInLines = 10),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = AppShapes.InputField,
            )
            content()
        }
    }
}

@Composable
fun ExpandedPromptEditor(
    systemPrompt: String,
    onDraft: (String) -> Unit,
    onUpdate: (String) -> Unit,
    title: String = stringResource(R.string.assistant_page_system_prompt),
    commitOnDismiss: Boolean = true,
    closeOnSave: Boolean = true,
    saving: Boolean = false,
    onDone: () -> Unit
) {
    // Use TextFieldState for stable, reliable text editing
    // Initialize once with the current value - do NOT re-sync from parent
    val textFieldState = rememberTextFieldState(initialText = systemPrompt)
    val currentText = textFieldState.text.toString()
    val draft = rememberUpdatedState(onDraft)
    val commit = rememberUpdatedState(onUpdate)

    // In-memory only. Writing the datastore here made typing hitch, and the
    // debounce was cancelled when the dialog or the screen went away.
    SideEffect {
        draft.value(currentText)
    }
    DisposableEffect(Unit) {
        onDispose {
            draft.value(textFieldState.text.toString())
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (!saving) {
                val latest = textFieldState.text.toString()
                draft.value(latest)
                if (commitOnDismiss) commit.value(latest)
                onDone()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !saving,
            dismissOnClickOutside = !saving
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.BottomSheet,
                color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val latest = textFieldState.text.toString()
                                draft.value(latest)
                                commit.value(latest)
                                if (closeOnSave) onDone()
                            },
                            enabled = !saving,
                        ) {
                            Text(stringResource(R.string.assistant_page_save))
                        }
                    }
                    OutlinedTextField(
                        state = textFieldState,
                        enabled = !saving,
                        modifier = Modifier
                            .imePadding()
                            .fillMaxSize()
                            .semantics { contentDescription = "$title editor" },
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                        placeholder = {
                            Text(title)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun PromptTokenCount(
    state: TextFieldState,
    estimate: (String) -> Int,
) {
    Text(
        text = "${estimate(state.text.toString())} tokens",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

