package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Skill

internal data class SkillCommandCardState(val query: String, val skills: List<Skill>)

@Composable
internal fun SkillCommandCard(
    state: SkillCommandCardState?,
    onSelect: (Skill) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedComposerPopup(
        state,
        keyFor = { it.query to it.skills.map(Skill::id) },
        modifier = modifier,
    ) { displayed, isCurrent ->
        LazyColumn(
            modifier = (if (isCurrent()) Modifier else Modifier.clearAndSetSemantics {})
                .fillMaxWidth().heightIn(max = 240.dp).padding(vertical = 4.dp),
        ) {
            if (displayed.skills.isEmpty()) item {
                Text("No matching skills", modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(displayed.skills, key = { it.id }) { skill ->
                ComposerOptionRow(
                    label = "/${skill.name}",
                    description = skill.description.takeIf(String::isNotBlank),
                    selected = false,
                    enabled = isCurrent(),
                    outlined = false,
                ) { if (isCurrent()) onSelect(skill) }
            }
        }
    }
}
