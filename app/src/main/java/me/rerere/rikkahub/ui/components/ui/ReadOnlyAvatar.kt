package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape

/** Avatar display without activity-result launchers; safe in service-owned Compose windows. */
@Composable
fun ReadOnlyAvatar(
    name: String, avatar: Avatar, modifier: Modifier = Modifier, loading: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = rememberAvatarShape(loading),
) {
    Box(
        modifier.size(32.dp).clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (avatar) {
            is Avatar.Image -> AsyncImage(avatar.url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is Avatar.Resource -> AsyncImage(avatar.id, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is Avatar.Emoji -> Text(avatar.content, fontSize = 24.sp, modifier = Modifier.padding(2.dp))
            else -> Text(name.take(1).uppercase(), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
