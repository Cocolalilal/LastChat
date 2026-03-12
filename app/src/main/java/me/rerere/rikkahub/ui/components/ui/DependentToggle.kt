package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * A toggle that can show dependency requirements when disabled.
 * 
 * @param checked Current toggle state
 * @param onCheckedChange Callback when toggle changes
 * @param label Main label text
 * @param description Optional description text
 * @param enabled Whether the toggle is interactable (true = can be clicked)
 * @param dependencyReason Optional text explaining why the toggle is disabled (e.g., "Requires RAG Memory")
 * @param modifier Modifier for the container
 */
@Composable
fun DependentToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    dependencyReason: String? = null,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.5f
    
    Card(
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) 
                MaterialTheme.colorScheme.surfaceContainerLow 
            else 
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.alpha(alpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!enabled && dependencyReason != null) {
                    Text(
                        text = "⚠️ $dependencyReason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            HapticSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
