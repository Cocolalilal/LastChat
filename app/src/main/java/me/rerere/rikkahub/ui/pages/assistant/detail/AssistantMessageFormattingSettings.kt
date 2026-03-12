package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.uuid.Uuid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun MessageTemplateSettingsCard(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_message_template),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.assistant_page_message_template_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.assistant_page_template_variables_label)); append(" ")
                    append(stringResource(R.string.assistant_page_template_variable_role)); append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("{{ role }}") }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_message)); append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("{{ message }}") }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_time)); append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("{{ time }}") }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_date)); append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("{{ date }}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DebouncedTextField(
                value = assistant.messageTemplate,
                onValueChange = { onUpdate(assistant.copy(messageTemplate = it)) },
                stateKey = "advanced_message_template_${assistant.id}",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8
            )
        }
    }
}

@Composable
fun MessageRegexSettingsCard(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_regex_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.assistant_page_regex_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            assistant.regexes.fastForEachIndexed { index, regex ->
                RegexEditorCard(regex = regex, assistant = assistant, index = index, onUpdate = onUpdate)
            }

            Button(
                onClick = {
                    onUpdate(assistant.copy(regexes = assistant.regexes + AssistantRegex(id = Uuid.random())))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RegexEditorCard(
    regex: AssistantRegex,
    assistant: Assistant,
    index: Int,
    onUpdate: (Assistant) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = regex.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).widthIn(max = 200.dp)
                )
                HapticSwitch(
                    checked = regex.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(enabled = enabled) else r }))
                    }
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null)
                }
            }

            if (expanded) {
                DebouncedTextField(
                    value = regex.name,
                    onValueChange = { value -> onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(name = value) else r })) },
                    label = stringResource(R.string.assistant_page_regex_name),
                    stateKey = "adv_regex_name_${regex.id}",
                    modifier = Modifier.fillMaxWidth()
                )

                DebouncedTextField(
                    value = regex.findRegex,
                    onValueChange = { value -> onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(findRegex = value) else r })) },
                    label = stringResource(R.string.assistant_page_regex_find_regex),
                    stateKey = "adv_regex_find_${regex.id}",
                    modifier = Modifier.fillMaxWidth()
                )

                DebouncedTextField(
                    value = regex.replaceString,
                    onValueChange = { value -> onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(replaceString = value) else r })) },
                    label = stringResource(R.string.assistant_page_regex_replace_string),
                    stateKey = "adv_regex_replace_${regex.id}",
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.assistant_page_regex_affecting_scopes), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AssistantAffectScope.entries.forEach { scope ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = scope in regex.affectingScope,
                                onCheckedChange = { checked ->
                                    val newScopes = if (checked) regex.affectingScope + scope else regex.affectingScope - scope
                                    onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(affectingScope = newScopes) else r }))
                                }
                            )
                            Text(scope.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = regex.visualOnly,
                        onCheckedChange = { checked -> onUpdate(assistant.copy(regexes = assistant.regexes.mapIndexed { i, r -> if (i == index) r.copy(visualOnly = checked) else r })) }
                    )
                    Text(stringResource(R.string.assistant_page_regex_visual_only))
                }

                TextButton(
                    onClick = {
                        onUpdate(assistant.copy(regexes = assistant.regexes.filterIndexed { i, _ -> i != index }))
                    }
                ) {
                    Icon(Icons.Rounded.Delete, null)
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}
