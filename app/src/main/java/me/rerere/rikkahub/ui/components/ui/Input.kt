package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
fun <T : Number> OutlinedNumberInput(
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        modifier = modifier,
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (textFieldValue.isValidNumberInput()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val newVal = when (value) {
                        is Int -> newValue.toInt() as T
                        is Float -> newValue.toFloat() as T
                        is Double -> newValue.toDouble() as T
                        else -> throw IllegalArgumentException("Unsupported number type")
                    }
                    onValueChange(newVal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !textFieldValue.isValidNumberInput(),
        colors = colors,
        shape = AppShapes.InputField
    )
}

@Composable
fun AppOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    shape: Shape = AppShapes.InputField,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = colors,
        shape = shape,
    )
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = {
        Icon(Icons.Rounded.Search, contentDescription = null)
    },
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    clearContentDescription: String = "Clear",
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    AppOutlinedField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon ?: if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = clearContentDescription)
                }
            }
        } else {
            null
        },
        singleLine = true,
        colors = colors,
        shape = AppShapes.SearchField,
    )
}

@Composable
fun <T : Number> NumberInput(
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }
    TextField(
        modifier = modifier,
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (textFieldValue.isValidNumberInput()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val newVal = when (value) {
                        is Int -> newValue.toInt() as T
                        is Float -> newValue.toFloat() as T
                        is Double -> newValue.toDouble() as T
                        else -> throw IllegalArgumentException("Unsupported number type")
                    }
                    onValueChange(newVal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !textFieldValue.isValidNumberInput(),
        colors = colors
    )
}

private val NumberRegex = Regex("^[+-]?\\d+(\\.\\d+)?$")
private fun String.isValidNumberInput() = this.isNotEmpty() && NumberRegex.matches(this)
