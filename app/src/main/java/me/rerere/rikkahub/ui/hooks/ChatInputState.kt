package me.rerere.rikkahub.ui.hooks

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

@Composable
fun rememberChatInputState(
    textContent: String = "",
    message: List<UIMessagePart> = emptyList(),
    loading: Boolean = false,
): ChatInputState {
    return remember(textContent, message, loading) {
        ChatInputState().apply {
            this.textContent.setTextAndPlaceCursorAtEnd(textContent)
            this.messageContent = message
            this.loading = loading
        }
    }
}

class ChatInputState {
    val textContent = TextFieldState()
    var messageContent: List<UIMessagePart>
        get() = pendingAttachments.map { it.part }
        set(value) {
            pendingAttachments = reconcileAttachments(
                previousAttachments = pendingAttachments,
                nextContent = value,
            )
        }

    var pendingAttachments by mutableStateOf(listOf<ChatInputAttachment>())
        private set
    var editingMessage by mutableStateOf<Uuid?>(null)
    var loading by mutableStateOf(false)
    
    // FocusRequester for the text field - allows external focus requests
    val focusRequester = FocusRequester()

    fun clearInput() {
        textContent.setTextAndPlaceCursorAtEnd("")
        setMessageContentWithIds(emptyList(), emptyList())
        editingMessage = null
    }

    fun isEditing() = editingMessage != null

    fun setMessageText(text: String) {
        textContent.setTextAndPlaceCursorAtEnd(text)
    }
    
    /**
     * Sets message text and requests focus on the text field.
     * Use this when setting text from templates to show keyboard.
     */
    fun setMessageTextAndFocus(text: String, scope: CoroutineScope) {
        textContent.setTextAndPlaceCursorAtEnd(text)
        // Request focus with a small delay to ensure the UI has updated
        scope.launch {
            delay(50)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester may not be attached yet
            }
        }
    }

    fun appendText(content: String) {
        textContent.setTextAndPlaceCursorAtEnd(textContent.text.toString() + content)
    }

    fun setContents(contents: List<UIMessagePart>) {
        val text = contents.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
        textContent.setTextAndPlaceCursorAtEnd(text)
        setMessageContentWithIds(contents.filter { it.isEditableAttachment() })
    }

    fun getContents(): List<UIMessagePart> {
        return listOf(UIMessagePart.Text(textContent.text.toString())) + messageContent
    }

    fun isEmpty(): Boolean {
        return textContent.text.isEmpty() && messageContent.isEmpty()
    }

    fun addImages(uris: List<Uri>) {
        addAttachments(uris.map { uri -> UIMessagePart.Image(uri.toString()) })
    }

    fun addVideos(uris: List<Uri>) {
        addAttachments(uris.map { uri -> UIMessagePart.Video(uri.toString()) })
    }

    fun addAudios(uris: List<Uri>) {
        addAttachments(uris.map { uri -> UIMessagePart.Audio(uri.toString()) })
    }

    fun addFiles(uris: List<UIMessagePart.Document>) {
        addAttachments(uris)
    }

    fun replaceAttachment(instanceId: String, part: UIMessagePart): UIMessagePart? {
        val index = pendingAttachments.indexOfFirst { it.id == instanceId }
        if (index < 0) return null
        val previousPart = pendingAttachments[index].part
        pendingAttachments = pendingAttachments.toMutableList().apply {
            set(index, ChatInputAttachment(id = instanceId, part = part))
        }
        return previousPart
    }

    fun removeAttachment(instanceId: String): UIMessagePart? {
        val index = pendingAttachments.indexOfFirst { it.id == instanceId }
        if (index < 0) return null
        val removedPart = pendingAttachments[index].part
        pendingAttachments = pendingAttachments.toMutableList().apply {
            removeAt(index)
        }
        return removedPart
    }

    private fun addAttachments(parts: List<UIMessagePart>) {
        if (parts.isEmpty()) return
        pendingAttachments = pendingAttachments + parts.map { part ->
            ChatInputAttachment(id = newAttachmentInstanceId(), part = part)
        }
    }

    private fun setMessageContentWithIds(
        parts: List<UIMessagePart>,
        ids: List<String> = List(parts.size) { newAttachmentInstanceId() },
    ) {
        pendingAttachments = parts.mapIndexed { index, part ->
            ChatInputAttachment(
                id = ids.getOrNull(index) ?: newAttachmentInstanceId(),
                part = part,
            )
        }
    }

    private fun reconcileAttachments(
        previousAttachments: List<ChatInputAttachment>,
        nextContent: List<UIMessagePart>,
    ): List<ChatInputAttachment> {
        val usedPreviousIndexes = mutableSetOf<Int>()
        return nextContent.mapIndexed { index, part ->
            if (previousAttachments.getOrNull(index)?.part == part) {
                usedPreviousIndexes += index
                previousAttachments[index].copy(part = part)
            } else {
                val matchedIndex = previousAttachments.indices.firstOrNull { previousIndex ->
                    previousIndex !in usedPreviousIndexes &&
                        previousAttachments[previousIndex].part == part
                }
                if (matchedIndex != null) {
                    usedPreviousIndexes += matchedIndex
                    previousAttachments[matchedIndex].copy(part = part)
                } else {
                    ChatInputAttachment(id = newAttachmentInstanceId(), part = part)
                }
            }
        }
    }

    private fun newAttachmentInstanceId(): String {
        return Uuid.random().toString()
    }
}

private fun UIMessagePart.isEditableAttachment(): Boolean {
    return this is UIMessagePart.Image ||
        this is UIMessagePart.Video ||
        this is UIMessagePart.Audio ||
        this is UIMessagePart.Document
}

data class ChatInputAttachment(
    val id: String,
    val part: UIMessagePart,
)
