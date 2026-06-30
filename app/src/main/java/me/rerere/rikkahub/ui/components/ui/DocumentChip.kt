package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A chip that displays a document/file attachment with an appropriate style.
 * Matches the 60x60 style of image attachments.
 */
@Composable
fun DocumentChip(
    fileName: String,
    mimeType: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val extension = fileName.substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() } ?: "FILE"

    Box(
        modifier = modifier
            .let { baseModifier ->
                if (onClick != null) {
                    baseModifier.clickable { onClick() }
                } else {
                    baseModifier
                }
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = extension.take(4),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    ),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
