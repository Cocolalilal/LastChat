package me.rerere.rikkahub.ui.components.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.core.generated.resources.Res
import me.rerere.rikkahub.ui.core.generated.resources.ic_launcher_lastchat_foreground
import org.jetbrains.compose.resources.painterResource

/** Android's production About-page body, shared without platform runtime dependencies. */
@Composable
fun LastChatAboutContent(
    appName: String,
    versionName: String,
    darkTheme: Boolean,
    platformTitle: String,
    platformSubtitle: String,
    platformIcon: ImageVector,
    deviceSubtitle: String,
    deviceIcon: ImageVector,
    architectureSubtitle: String,
    architectureIcon: ImageVector,
    onSourceCode: () -> Unit,
    onVersionLongPress: (() -> Unit)? = null,
    onHaptic: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Image(
            painter = painterResource(Res.drawable.ic_launcher_lastchat_foreground),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = CircleShape,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .pointerInput(onVersionLongPress) {
                    detectTapGestures(
                        onLongPress = {
                            if (onVersionLongPress != null) {
                                onHaptic()
                                onVersionLongPress()
                            }
                        },
                    )
                },
        ) {
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        AboutSectionTitle("Links")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LastChatAboutItem(
                icon = Icons.Rounded.Code,
                title = "Source Code",
                subtitle = "GitHub Repository",
                trailing = Icons.AutoMirrored.Rounded.OpenInNew,
                darkTheme = darkTheme,
                onHaptic = onHaptic,
                onClick = onSourceCode,
            )
        }
        Spacer(Modifier.height(24.dp))
        AboutSectionTitle("System Information")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LastChatAboutItem(
                icon = platformIcon,
                title = platformTitle,
                subtitle = platformSubtitle,
                darkTheme = darkTheme,
            )
            LastChatAboutItem(
                icon = deviceIcon,
                title = "Device",
                subtitle = deviceSubtitle,
                darkTheme = darkTheme,
            )
            LastChatAboutItem(
                icon = architectureIcon,
                title = "Architecture",
                subtitle = architectureSubtitle,
                darkTheme = darkTheme,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Based on RikkaHub by rerere",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

@Composable
private fun LastChatAboutItem(
    icon: ImageVector,
    title: String,
    darkTheme: Boolean,
    subtitle: String? = null,
    trailing: ImageVector? = null,
    onHaptic: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "about_item_scale",
    )
    Surface(
        onClick = {
            if (onClick != null) {
                onHaptic()
                onClick()
            }
        },
        enabled = onClick != null,
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(10.dp),
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        trailing,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
