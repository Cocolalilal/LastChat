package me.rerere.lastchat.ios.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import me.rerere.rikkahub.ui.theme.rememberDefaultGenericalPainter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewChatContent(
    assistantName: String,
    assistantAvatarUrl: String? = null,
    onTemplateClick: (String) -> Unit,
    onNavigateToImageGen: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currentHour = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val greetingText = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good Morning!"
            in 12..13 -> "Good Noon!"
            in 14..17 -> "Good Afternoon!"
            in 18..20 -> "Good Evening!"
            else -> "Good Night!"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Hero Avatar & Greeting
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = if (onAvatarClick != null) Modifier.clickable { onAvatarClick() } else Modifier,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!assistantAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = assistantAvatarUrl,
                        contentDescription = assistantName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (assistantName.equals("Generical", ignoreCase = true) || assistantName.isBlank()) {
                    Image(
                        painter = rememberDefaultGenericalPainter(),
                        contentDescription = "Generical",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = assistantName.firstOrNull()?.uppercase() ?: "A",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Text(
                text = greetingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Action Suggestion Pills
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onNavigateToImageGen != null) {
                    IosActionPill(
                        icon = Icons.Rounded.Image,
                        text = "Create image",
                        iconColor = primaryColor,
                        onClick = onNavigateToImageGen,
                    )
                }
                IosActionPill(
                    icon = Icons.Rounded.Lightbulb,
                    text = "Brainstorm",
                    iconColor = tertiaryColor,
                    onClick = { onTemplateClick("Brainstorm ideas for ") },
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IosActionPill(
                    icon = Icons.Rounded.Code,
                    text = "Code",
                    iconColor = secondaryColor,
                    onClick = { onTemplateClick("Write code for ") },
                )
                IosActionPill(
                    icon = Icons.Rounded.Edit,
                    text = "Write",
                    iconColor = primaryColor,
                    onClick = { onTemplateClick("Write a ") },
                )
            }
        }
    }
}

@Composable
private fun IosActionPill(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconColor,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
