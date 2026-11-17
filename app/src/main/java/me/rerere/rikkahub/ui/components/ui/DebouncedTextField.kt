package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
    inputTransformation: InputTransformation? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    debounceMs: Long = 300L,
    isSecure: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    isError: Boolean = false,
    onPendingChange: (Boolean) -> Unit = {},
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val textFieldState = androidx.compose.runtime.key(stateKey) {
        rememberTextFieldState(initialText = value)
    }
    var isFocused by remember { mutableStateOf(false) }
    var syncedText by remember(stateKey) { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnPendingChange by rememberUpdatedState(onPendingChange)

    // On blur, publish a local draft before accepting incoming values. A focus
    // change must never replace the last keystrokes with the pre-debounce value.
    LaunchedEffect(value, stateKey, isFocused) {
        if (!isFocused) {
            val text = textFieldState.text.toString()
            if (text == syncedText) {
                if (text != value) textFieldState.edit { replace(0, length, value) }
                syncedText = value
            } else {
                syncedText = text
                if (text != latestValue) latestOnValueChange(text)
            }
        }
    }

    // Debounced sync to external state
    LaunchedEffect(stateKey) {
        snapshotFlow { textFieldState.text }
            .drop(1) // Skip initial emission
            .debounce(debounceMs)
            .collect {
                val text = it.toString()
                syncedText = text
                if (text != latestValue) latestOnValueChange(text)
            }
    }

    // Leaving the screen inside the debounce window cancels both effects above, which used to
    // drop the last keystrokes (an API key or prompt that "didn't apply"). Commit them here.
    // Also commit on ON_PAUSE so backgrounding or killing the app right after typing keeps it.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(stateKey, lifecycleOwner) {
        fun commitPending() {
            val currentText = textFieldState.text.toString()
            if (currentText != syncedText) {
                syncedText = currentText
                if (currentText != latestValue) latestOnValueChange(currentText)
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) commitPending()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            commitPending()
        }
    }

    val hasPendingChanges = textFieldState.text.toString() != value
    LaunchedEffect(hasPendingChanges) {
        latestOnPendingChange(hasPendingChanges)
    }

    OutlinedTextField(
        state = textFieldState,
        inputTransformation = inputTransformation,
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
        trailingIcon = if (isSecure) {
            {
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
            }
        } else {
            trailingIcon
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
