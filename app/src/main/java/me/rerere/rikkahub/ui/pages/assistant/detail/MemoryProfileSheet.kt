package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.CharacterMemory
import me.rerere.rikkahub.data.memory.MemoryNote
import me.rerere.rikkahub.data.memory.MemoryParagraph
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar

/** Reading a profile is independent from creating or editing any stored note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryProfileSheet(
    view: CharacterMemory,
    userName: String,
    userAvatar: Avatar,
    onDismiss: () -> Unit,
    onOpenNote: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val name = userName.ifBlank { "You" }
    val notes = remember(view.notes) {
        view.notes.filter { it.category == "You" && !it.sourceOnly && it.paragraphs.isNotEmpty() }
            .sortedWith(compareByDescending<MemoryNote> { it.pinned }.thenByDescending { it.updatedAt })
    }
    val hasUnorganizedNotes = view.notes.any { !it.sourceOnly && it.category != "You" }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .9f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UIAvatar(name, userAvatar, Modifier.size(64.dp).clearAndSetSemantics {
                    contentDescription = "$name avatar"
                })
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close profile") }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (notes.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Profile details", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (hasUnorganizedNotes) "Your saved notes haven’t been organized into a profile yet."
                                    else "No profile details saved yet.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilledTonalButton(onClick = onAdd) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                    Text("Add details", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val title = if (note.title.trim().lowercase() in genericMemoryNoteTitles) "About you" else note.title
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(onClick = { onEditNote(note.id) }) {
                                        Icon(Icons.Rounded.Edit, "Edit $title")
                                    }
                                }
                                MemoryReadableBody(note.paragraphs)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        relativeMemoryTime(note.updatedAt), Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = { onOpenNote(note.id) }) { Text("Details") }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = onAdd) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Text("Add details", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MemoryReadableBody(paragraphs: List<MemoryParagraph>) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            paragraphs.forEach { paragraph ->
                Text(paragraph.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val genericMemoryNoteTitles = setOf("", "saved memory")

/** Presentation only: never rewrite imported facts merely to generate a better list label. */
internal fun memoryNoteTitle(note: MemoryNote): String {
    val title = note.title.trim()
    if (title.lowercase() !in genericMemoryNoteTitles) return title
    val text = note.body.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return if (note.category == "You") "About you" else "Untitled note"
    val sentence = text
    if (sentence.length <= 96) return sentence
    val prefix = sentence.take(93).substringBeforeLast(' ', sentence.take(93)).trimEnd()
    return "$prefix…"
}

internal fun memoryNotePreview(note: MemoryNote): String {
    if (note.description.isNotBlank()) return note.description.trim().take(160)
    if (note.title.trim().lowercase() in genericMemoryNoteTitles) return ""
    val text = note.body.replace(Regex("\\s+"), " ").trim()
    if (text.equals(note.title.trim(), true)) return ""
    return if (text.length > 160) text.take(157).trimEnd() + "…" else text
}
