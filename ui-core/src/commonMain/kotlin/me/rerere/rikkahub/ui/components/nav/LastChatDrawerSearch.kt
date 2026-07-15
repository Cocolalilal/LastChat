package me.rerere.rikkahub.ui.components.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.theme.AppShapes

/** Android's production expandable drawer search, shared without platform resources. */
@Composable
fun LastChatDrawerSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    placeholder: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) onExpandedChange(true)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(0.dp)
                .focusable(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (expanded) {
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onExpandedChange(false)
                            onQueryChange("")
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = RoundedCloseIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !expanded) onExpandedChange(true)
                        },
                    shape = AppShapes.SearchField,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                )
            }

            AnimatedVisibility(visible = expanded || query.isNotBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (expanded) 44.dp else 12.dp),
                )
            }
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // The drawer may have left composition during the focus delay.
            }
        }
    }
}

private val RoundedCloseIcon by lazy {
    ImageVector.Builder(
        name = "Rounded.Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M18.3,5.71c-0.39,-0.39 -1.02,-0.39 -1.41,0L12,10.59L7.11,5.7c-0.39,-0.39 -1.02,-0.39 -1.41,0c-0.39,0.39 -0.39,1.02 0,1.41L10.59,12L5.7,16.89c-0.39,0.39 -0.39,1.02 0,1.41c0.39,0.39 1.02,0.39 1.41,0L12,13.41l4.89,4.89c0.39,0.39 1.02,0.39 1.41,0c0.39,-0.39 0.39,-1.02 0,-1.41L13.41,12l4.89,-4.89c0.38,-0.38 0.38,-1.02 0,-1.4z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
