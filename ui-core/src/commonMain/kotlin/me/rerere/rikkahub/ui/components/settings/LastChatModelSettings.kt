package me.rerere.rikkahub.ui.components.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Android's production model-feature settings card. */
@Composable
fun LastChatModelFeatureCard(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        icon()
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    }
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        ),
                    ) { description() }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

enum class LastChatModelGroupPosition { First, Middle, Last, Single }

/** Android's grouped model-picker row, including selected-pill corner animation. */
@Composable
fun LastChatGroupedModelRow(
    title: String,
    selected: Boolean,
    darkTheme: Boolean,
    position: LastChatModelGroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    metadata: @Composable () -> Unit = {},
    tail: @Composable RowScope.() -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val target = groupedCornerRadii(selected, position)
    val topStart by animateDpAsState(
        target.topStart,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_top_start",
    )
    val topEnd by animateDpAsState(
        target.topEnd,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_top_end",
    )
    val bottomStart by animateDpAsState(
        target.bottomStart,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_bottom_start",
    )
    val bottomEnd by animateDpAsState(
        target.bottomEnd,
        spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_bottom_end",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else if (darkTheme) Color.Black
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                )
                metadata()
            }
            tail()
        }
        dragHandle?.invoke(this)
    }
}

private data class ModelCornerRadii(
    val topStart: androidx.compose.ui.unit.Dp,
    val topEnd: androidx.compose.ui.unit.Dp,
    val bottomStart: androidx.compose.ui.unit.Dp,
    val bottomEnd: androidx.compose.ui.unit.Dp,
)

private fun groupedCornerRadii(
    selected: Boolean,
    position: LastChatModelGroupPosition,
): ModelCornerRadii {
    if (selected) return ModelCornerRadii(50.dp, 50.dp, 50.dp, 50.dp)
    return when (position) {
        LastChatModelGroupPosition.First -> ModelCornerRadii(24.dp, 24.dp, 10.dp, 10.dp)
        LastChatModelGroupPosition.Middle -> ModelCornerRadii(10.dp, 10.dp, 10.dp, 10.dp)
        LastChatModelGroupPosition.Last -> ModelCornerRadii(10.dp, 10.dp, 24.dp, 24.dp)
        LastChatModelGroupPosition.Single -> ModelCornerRadii(24.dp, 24.dp, 24.dp, 24.dp)
    }
}
