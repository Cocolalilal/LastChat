package me.rerere.rikkahub.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import me.rerere.rikkahub.ui.theme.AppShapes

enum class LastChatStatIcon {
    Conversations,
    Messages,
    InputTokens,
    OutputTokens,
    CachedTokens,
}

@Composable
fun LastChatStatIconGlyph(icon: LastChatStatIcon) {
    when (icon) {
        LastChatStatIcon.Conversations -> Icon(ConversationIcon, null, Modifier.size(18.dp))
        LastChatStatIcon.Messages -> Icon(MessageIcon, null, Modifier.size(18.dp))
        LastChatStatIcon.InputTokens -> Icon(InputIcon, null, Modifier.size(18.dp))
        LastChatStatIcon.OutputTokens -> Icon(
            NonMirroringInputIcon,
            null,
            Modifier.size(18.dp).graphicsLayer { scaleX = -1f },
        )
        LastChatStatIcon.CachedTokens -> Icon(SavingsIcon, null, Modifier.size(18.dp))
    }
}

private val ConversationIcon by lazy {
    materialIcon(
        name = "Conversation",
        autoMirror = true,
        pathData = "M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM7,9h10c0.55,0 1,0.45 1,1s-0.45,1 -1,1L7,11c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1zM13,14L7,14c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h6c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM17,8L7,8c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h10c0.55,0 1,0.45 1,1s-0.45,1 -1,1z",
    )
}
private val MessageIcon by lazy {
    materialIcon(
        name = "Message",
        autoMirror = true,
        pathData = "M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM17,14L7,14c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h10c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM17,11L7,11c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h10c0.55,0 1,0.45 1,1s-0.45,1 -1,1zM17,8L7,8c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h10c0.55,0 1,0.45 1,1s-0.45,1 -1,1z",
    )
}
private const val InputPath = "M21,3.01L3,3.01c-1.1,0 -2,0.9 -2,2L1,8c0,0.55 0.45,1 1,1s1,-0.45 1,-1L3,5.99c0,-0.55 0.45,-1 1,-1h16c0.55,0 1,0.45 1,1v12.03c0,0.55 -0.45,1 -1,1L4,19.02c-0.55,0 -1,-0.45 -1,-1L3,16c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3.01c0,1.09 0.89,1.98 1.98,1.98L21,20.99c1.1,0 2,-0.9 2,-2L23,5.01c0,-1.1 -0.9,-2 -2,-2zM11.85,15.15l2.79,-2.79c0.2,-0.2 0.2,-0.51 0,-0.71l-2.79,-2.79c-0.31,-0.32 -0.85,-0.1 -0.85,0.35L11,11L2,11c-0.55,0 -1,0.45 -1,1s0.45,1 1,1h9v1.79c0,0.45 0.54,0.67 0.85,0.36z"
private val InputIcon by lazy { materialIcon("Input", InputPath, autoMirror = true) }
private val NonMirroringInputIcon by lazy { materialIcon("Output", InputPath, autoMirror = false) }
private val SavingsIcon by lazy {
    materialIcon(
        name = "Savings",
        autoMirror = false,
        pathData = "M19.83,7.5l-2.27,-2.27c0.07,-0.42 0.18,-0.81 0.32,-1.15c0.11,-0.26 0.15,-0.56 0.09,-0.87C17.84,2.49 17.14,1.99 16.4,2c-1.59,0.03 -3,0.81 -3.9,2l-5,0C4.46,4 2,6.46 2,9.5c0,2.25 1.37,7.48 2.08,10.04C4.32,20.4 5.11,21 6.01,21L8,21c1.1,0 2,-0.9 2,-2v0h2v0c0,1.1 0.9,2 2,2l2.01,0c0.88,0 1.66,-0.58 1.92,-1.43l1.25,-4.16 2.14,-0.72c0.41,-0.14 0.68,-0.52 0.68,-0.95V8.5c0,-0.55 -0.45,-1 -1,-1H19.83zM12,9H9C8.45,9 8,8.55 8,8v0c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v0C13,8.55 12.55,9 12,9zM16,11c-0.55,0 -1,-0.45 -1,-1c0,-0.55 0.45,-1 1,-1s1,0.45 1,1C17,10.55 16.55,11 16,11z",
    )
}

private fun materialIcon(name: String, pathData: String, autoMirror: Boolean): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()

@Composable
fun LastChatStatCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Card(
        modifier = modifier.heightIn(min = 136.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = AppShapes.CardMedium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(AppShapes.Chip)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}
