package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.LongTextPasteMode
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ChatAttachmentManager
import me.rerere.rikkahub.data.repository.ImportedChatFile
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.isLongTextPaste
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

internal data class PendingLongTextPaste(
    val text: String,
    val replacedRange: TextRange,
    val originalDraft: String,
    val draftEpoch: Int,
    val id: Uuid = Uuid.random(),
)

/** Runs before the editor lays out a paste, including keyboard and fullscreen-editor pastes. */
@Composable
internal fun rememberLongTextPasteHandler(
    state: ChatInputState,
    mode: LongTextPasteMode,
    enabled: Boolean,
    onImportStarted: () -> Unit,
    onImportFinished: () -> Unit,
): InputTransformation {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val currentMode by rememberUpdatedState(mode)
    val currentEnabled by rememberUpdatedState(enabled)
    var pending by remember(state, state.sttCommitEpoch) { mutableStateOf<PendingLongTextPaste?>(null) }
    var queuedPastes by remember(state, state.sttCommitEpoch) {
        mutableStateOf(emptyList<PendingLongTextPaste>())
    }
    var showSheet by remember(state, state.sttCommitEpoch) { mutableStateOf(false) }
    var rememberChoice by remember(state, state.sttCommitEpoch) { mutableStateOf(false) }
    var importing by remember(state, state.sttCommitEpoch) { mutableStateOf(false) }

    suspend fun applyChoice(choice: LongTextPasteMode) {
        val paste = pending ?: return
        if (importing) return
        if (paste.draftEpoch != state.sttCommitEpoch) return
        val shouldRememberChoice = rememberChoice
        try {
            when (choice) {
                LongTextPasteMode.TEXT -> state.textContent.edit {
                    // Keep edits made while the sheet was open (e.g. a dictation result).
                    val range = if (asCharSequence().toString() == paste.originalDraft) {
                        paste.replacedRange
                    } else {
                        selection
                    }
                    replace(range.min, range.max, paste.text)
                    selection = TextRange(range.min + paste.text.length)
                }
                LongTextPasteMode.ATTACHMENT -> {
                    importing = true
                    onImportStarted()
                    try {
                        val imported = importPastedTextFile(context.cacheDir, paste.text) { uri ->
                            ChatAttachmentManager.importChatFile(
                                uri = uri,
                                fileNameHint = "Pasted text.txt",
                                mimeHint = "text/plain",
                            )
                        }
                        // A send/clear can finish while file I/O is in flight. Never attach to the next draft.
                        if (paste.draftEpoch != state.sttCommitEpoch) return
                        state.addFiles(listOf(UIMessagePart.Document(
                            url = imported.uri.toString(),
                            fileName = imported.fileName,
                            mime = "text/plain",
                        )))
                    } finally {
                        importing = false
                        onImportFinished()
                    }
                }
                LongTextPasteMode.ASK -> return
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retain the paste so the user can retry or insert it as text after an import failure.
            showSheet = true
            toaster.show("Could not attach pasted text. Try again or paste as text.", type = ToastType.Error)
            return
        }
        pending = queuedPastes.firstOrNull()
        queuedPastes = queuedPastes.drop(1)
        showSheet = false
        if (shouldRememberChoice) {
            try {
                settingsStore.update { settings ->
                    settings.copy(displaySetting = settings.displaySetting.copy(longTextPasteMode = choice))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toaster.show("Your paste was added, but the preference could not be saved.", type = ToastType.Error)
            }
        }
    }

    LaunchedEffect(pending?.id) {
        if (pending != null) {
            rememberChoice = false
            if (currentMode == LongTextPasteMode.ATTACHMENT) {
                applyChoice(LongTextPasteMode.ATTACHMENT)
            } else {
                showSheet = true
            }
        }
    }

    if (showSheet && pending != null) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { !importing },
            ),
            onDismissRequest = {
                if (!importing) {
                    pending = queuedPastes.firstOrNull()
                    queuedPastes = queuedPastes.drop(1)
                    showSheet = false
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Large text", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "The text you’re pasting is quite large. Would you like to attach it as a file instead?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { scope.launch { applyChoice(LongTextPasteMode.ATTACHMENT) } },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (importing) "Attaching…" else "Attach as file") }
                OutlinedButton(
                    onClick = { scope.launch { applyChoice(LongTextPasteMode.TEXT) } },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paste as text") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Remember my choice")
                    HapticSwitch(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                        enabled = !importing,
                    )
                }
                Text(
                    "You can change this in Settings → Display → Long pasted text.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    return remember(state, state.sttCommitEpoch) {
        longTextPasteTransformation(
            enabled = { currentEnabled },
            mode = { currentMode },
            draftEpoch = { state.sttCommitEpoch },
        ) { paste ->
            if (pending == null) {
                pending = paste
            } else {
                // Auto-attachment imports run in the background; keep subsequent pastes too.
                queuedPastes = queuedPastes + paste
            }
        }
    }
}

/** The managed attachment importer owns the result; the temporary staging file never survives the import. */
internal suspend fun importPastedTextFile(
    cacheDirectory: File,
    text: String,
    importFile: suspend (Uri) -> ImportedChatFile?,
): ImportedChatFile = withContext(Dispatchers.IO) {
    val temporaryFile = File.createTempFile("pasted-text-", ".txt", cacheDirectory)
    try {
        temporaryFile.writeText(text, Charsets.UTF_8)
        importFile(temporaryFile.toUri()) ?: error("Could not import pasted text")
    } finally {
        temporaryFile.delete()
    }
}

/** Shared by both editors; keeping this independent of the sheet also permits edit-buffer regression tests. */
internal fun longTextPasteTransformation(
    enabled: () -> Boolean,
    mode: () -> LongTextPasteMode,
    draftEpoch: () -> Int,
    onPaste: (PendingLongTextPaste) -> Unit,
): InputTransformation = InputTransformation {
    if (enabled() && mode() != LongTextPasteMode.TEXT && changes.changeCount == 1) {
        val insertedRange = changes.getRange(0)
        val insertedText = asCharSequence().subSequence(insertedRange.min, insertedRange.max)
        if (isLongTextPaste(insertedText)) {
            onPaste(PendingLongTextPaste(
                text = insertedText.toString(),
                replacedRange = changes.getOriginalRange(0),
                originalDraft = originalText.toString(),
                draftEpoch = draftEpoch(),
            ))
            revertAllChanges()
        }
    }
}
