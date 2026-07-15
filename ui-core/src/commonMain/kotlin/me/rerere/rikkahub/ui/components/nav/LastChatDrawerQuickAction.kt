package me.rerere.rikkahub.ui.components.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Exact grouped container used by the production drawer quick actions. */
@Composable
fun LastChatDrawerQuickActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** Exact production drawer quick-action row; navigation and haptics stay platform-owned. */
@Composable
fun LastChatDrawerQuickAction(
    label: String,
    onClick: () -> Unit,
    onHaptic: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {
            onHaptic()
            onClick()
        },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Material Rounded BarChart from the Android production icon artifact. */
@Composable
fun LastChatBarChartIcon(contentDescription: String?) {
    Icon(
        imageVector = RoundedBarChartIcon,
        contentDescription = contentDescription,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val RoundedBarChartIcon by lazy {
    ImageVector.Builder(
        name = "Rounded.BarChart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M6,20c1.1,0 2,-0.9 2,-2v-7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v7c0,1.1 0.9,2 2,2z" +
                    "M18,20c1.1,0 2,-0.9 2,-2v-3c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v3c0,1.1 0.9,2 2,2z" +
                    "M12,20c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v12c0,1.1 0.9,2 2,2z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
