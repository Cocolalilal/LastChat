package me.rerere.rikkahub.ui.components.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.AppShapes

/** The production chat-toolbar drawer button shared by Android and iOS. */
@Composable
fun LastChatMenuButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier.size(48.dp),
    size: Dp = 48.dp,
    shape: Shape = AppShapes.ButtonPill,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: BorderStroke = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    ),
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = border,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = RoundedMenuIcon,
                contentDescription = contentDescription,
            )
        }
    }
}

private val RoundedMenuIcon by lazy {
    ImageVector.Builder(
        name = "Rounded.Menu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M4,18L20,18c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1L4,16c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM4,13L20,13c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1L4,11c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM3,7c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1L4,6c-0.55,0 -1,0.45 -1,1z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
