package me.rerere.rikkahub.navigation

import android.net.Uri
import androidx.core.net.toUri
import me.rerere.rikkahub.Screen
import java.io.Serializable
import kotlin.uuid.Uuid

const val CHAT_ROUTE_TARGET_KEY = "chat_route_target"

data class ChatRouteTarget(
    val id: String,
    val text: String? = null,
    val files: List<String> = emptyList(),
    val searchQuery: String? = null,
    val persistenceMode: String? = null,
    val focusLatestMessageKey: String? = null,
) : Serializable {
    val uuid: Uuid
        get() = Uuid.parse(id)

    val fileUris: List<Uri>
        get() = files.map { it.toUri() }
}

fun Screen.Chat.toChatRouteTarget(): ChatRouteTarget {
    return ChatRouteTarget(
        id = id,
        text = text,
        files = files,
        searchQuery = searchQuery,
        persistenceMode = persistenceMode,
        focusLatestMessageKey = focusLatestMessageKey,
    )
}
