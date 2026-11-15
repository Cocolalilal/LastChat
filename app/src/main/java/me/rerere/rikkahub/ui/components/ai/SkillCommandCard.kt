package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import me.rerere.rikkahub.data.model.Skill

internal data class SkillCommandCardState(val query: String, val skills: List<Skill>, val page: Int = 0)
internal const val SKILL_COMMAND_PAGE_SIZE = 3

@Composable
internal fun SkillCommandCard(
    state: SkillCommandCardState?,
    onPage: (Int) -> Unit,
    onSelect: (Skill) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedComposerPopup(
        state,
        keyFor = { Triple(it.query, it.page, it.skills.map(Skill::id)) },
        modifier = modifier,
    ) { displayed, isCurrent ->
        val pages = ((displayed.skills.size + SKILL_COMMAND_PAGE_SIZE - 1) / SKILL_COMMAND_PAGE_SIZE).coerceAtLeast(1)
        val page = displayed.page.coerceIn(0, pages - 1)
        Column((if (isCurrent()) Modifier else Modifier.clearAndSetSemantics {}).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Skills", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (pages > 1) {
                    IconButton(onClick = { if (isCurrent()) onPage(page - 1) },
                        enabled = isCurrent() && page > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowLeft, "Previous")
                    }
                    Text("${page + 1}/$pages", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { if (isCurrent()) onPage(page + 1) },
                        enabled = isCurrent() && page < pages - 1, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowRight, "Next")
                    }
                }
                IconButton(onClick = { if (isCurrent()) onDismiss() }, enabled = isCurrent(),
                    modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "Dismiss")
                }
            }
            if (displayed.skills.isEmpty()) {
                Text("No matching skills", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            displayed.skills.drop(page * SKILL_COMMAND_PAGE_SIZE).take(SKILL_COMMAND_PAGE_SIZE).forEach { skill ->
                ComposerOptionRow("/${skill.name}", skill.description.takeIf(String::isNotBlank), false, isCurrent()) {
                    if (isCurrent()) onSelect(skill)
                }
            }
        }
    }
}
