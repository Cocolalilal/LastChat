package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.theme.AppShapes

/**
 * A text field that debounces updates to external state while maintaining
 * responsive local editing. Prevents race conditions when typing fast.
 *
 * Key features:
 * - Local state for immediate UI responsiveness
 * - Debounced sync to external state (saves after typing stops)
 * - Focus-aware incoming sync (doesn't overwrite while user is editing)
 * - Immediate commit on blur
 */
@OptIn(FlowPreview::class)
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
    debounceMs: Long = 300L
) {
    // Local editing state - completely independent from external value while focused
    var localText by remember(stateKey) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    // Track if we need to sync from external on next unfocused recomposition
    var hasPendingExternalSync by remember(stateKey) { mutableStateOf(false) }
    
    // When external value changes while NOT focused, sync it to local
    LaunchedEffect(value, stateKey) {
        if (!isFocused && localText != value) {
            localText = value
        } else if (isFocused && localText != value) {
            // Mark that external changed while we were focused - we'll ignore it
            // but if our local text equals external value, we're synced
            hasPendingExternalSync = true
        }
    }
    
    // Debounce: after user stops typing, commit to external
    LaunchedEffect(localText, stateKey) {
        if (isFocused && localText != value) {
            delay(debounceMs)
            onValueChange(localText)
        }
    }
    
    OutlinedTextField(
        value = localText,
        onValueChange = { newText ->
            localText = newText
        },
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            
            // When gaining focus, always use the external value as starting point
            // This ensures we have the latest value when starting to edit
            if (!wasFocused && focusState.isFocused) {
                if (localText != value) {
                    localText = value
                }
            }
            
            // When losing focus, commit local to external immediately
            if (wasFocused && !focusState.isFocused) {
                if (localText != value) {
                    onValueChange(localText)
                }
                hasPendingExternalSync = false
            }
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = AppShapes.InputField
    )
}
