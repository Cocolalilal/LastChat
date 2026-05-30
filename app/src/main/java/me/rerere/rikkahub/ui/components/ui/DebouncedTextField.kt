package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import me.rerere.rikkahub.ui.theme.AppShapes

/**
 * A text field that debounces updates to external state while maintaining
 * responsive local editing. Prevents race conditions when typing fast.
 *
 * Key features:
 * - Local state for immediate UI responsiveness using TextFieldState
 * - Debounced sync to external state (saves after typing stops)
 * - Focus-aware incoming sync (doesn't overwrite while user is editing)
 * - Immediate commit on blur
 */
@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun DebouncedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    debounceMs: Long = 300L,
    showSavingIndicator: Boolean = false,
    isSecure: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    isError: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val textFieldState = androidx.compose.runtime.key(stateKey) {
        rememberTextFieldState(initialText = value)
    }
    var isFocused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    // Sync from external state ONLY when NOT focused
    LaunchedEffect(value, stateKey, isFocused) {
        if (!isFocused && textFieldState.text.toString() != value) {
            textFieldState.edit {
                replace(0, length, value)
            }
        }
    }

    // Debounced sync to external state
    LaunchedEffect(stateKey) {
        snapshotFlow { textFieldState.text }
            .drop(1) // Skip initial emission
            .debounce(debounceMs)
            .collect {
                if (it.toString() != value) {
                    onValueChange(it.toString())
                }
            }
    }

    // Immediate commit on losing focus
    LaunchedEffect(isFocused) {
        if (!isFocused) {
            val currentText = textFieldState.text.toString()
            if (currentText != value) {
                onValueChange(currentText)
            }
        }
    }

    val hasUnsavedChanges = remember(textFieldState.text, value) {
        textFieldState.text.toString() != value
    }

    OutlinedTextField(
        state = textFieldState,
        modifier = modifier.onFocusChanged {
            isFocused = it.isFocused
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        outputTransformation = if (isSecure && !visible) {
            OutputTransformation {
                val len = length
                replace(0, len, "•".repeat(len))
            }
        } else null,
        isError = isError,
        keyboardOptions = keyboardOptions,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSavingIndicator && hasUnsavedChanges) {
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                if (isSecure) {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = if (visible) "Hide" else "Show",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    trailingIcon?.invoke()
                }
            }
        },
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(
                minHeightInLines = minLines,
                maxHeightInLines = maxLines
            )
        },
        shape = AppShapes.InputField
    )
}

