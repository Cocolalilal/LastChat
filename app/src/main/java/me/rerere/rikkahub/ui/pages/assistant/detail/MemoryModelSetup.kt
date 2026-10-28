package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.locallm.*
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.memory.MemoryExperience
import me.rerere.rikkahub.data.memory.memoryMode
import org.koin.compose.koinInject
import java.io.File
import me.rerere.rikkahub.data.memory.CharacterMemoryRepository
import me.rerere.rikkahub.data.memory.MemoryLearningWorker

/** Shared onboarding/restore/enablement affordance. Downloaded weights are never put in backups. */
@Composable
fun EmbeddingModelCard(
    store: LocalModelStore = koinInject(),
    catalog: LiteRtCatalog = koinInject(),
    downloader: LocalDownloadManager = koinInject(),
    embedder: LiteRtEmbedder = koinInject(),
) {
    val installed by store.models.collectAsStateWithLifecycle(emptyList())
    val downloads by downloader.downloads.collectAsStateWithLifecycle()
    var metadata by remember { mutableStateOf<LocalModelMetadata?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { metadata = catalog.catalog().models.firstOrNull { it.id == TEXT_EMBEDDING_MODEL_ID } }
        catch (e: Exception) { error = e.message }
    }
    val ready = installed.any { it.hasUsableTextEmbeddingFile() }
    val download = downloads[TEXT_EMBEDDING_MODEL_ID]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("EmbeddingGemma 2 Text", style = MaterialTheme.typography.titleMedium)
        Text(if (ready) "Installed · on-device semantic recall" else
            "Advanced memory uses this on-device model for semantic recall. Basic memory works without it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        metadata?.let { meta ->
            if (!ready) {
                Text("${meta.sizeInBytes / 1_000_000} MB · shared by all characters", style = MaterialTheme.typography.labelMedium)
                if (!embedder.isSupported(meta)) {
                    Text("This device cannot run the local embedding model. Basic memory is available.", color = MaterialTheme.colorScheme.error)
                } else when (download) {
                    is LocalDownload.Running -> {
                        LinearProgressIndicator(progress = { download.progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${download.progress.percent}%", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { downloader.cancel(TEXT_EMBEDDING_MODEL_ID) }) { Text("Pause download") }
                        }
                    }
                    else -> Button(onClick = {
                        scope.launch {
                            try { error = null; downloader.download(meta) }
                            catch (e: Exception) { error = e.message ?: "Download could not start." }
                        }
                    }) { Text(if (download is LocalDownload.Failed) "Retry download" else "Download") }
                }
            }
        }
        (error ?: (download as? LocalDownload.Failed)?.message)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text("Extraction and summaries use your selected memory-processing model.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddingSetupGate(settings: Settings, store: LocalModelStore = koinInject(), repository: CharacterMemoryRepository = koinInject()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val installed by store.models.collectAsStateWithLifecycle(emptyList())
    val needs = settings.assistants.filter { it.enableMemory && it.memoryMode() == MemoryExperience.ADVANCED }.map { "${it.id}:${it.memoryContext}" }.sorted().joinToString()
    val ready = installed.any { it.hasUsableTextEmbeddingFile() }
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { store.retireLegacyEmbedding() }
        catch(error:Exception) {
            if(error is kotlinx.coroutines.CancellationException)throw error
            me.rerere.common.platform.PlatformLog.w("Memory","Old embedding files could not be removed. They will not be used.")
        }
    }
    LaunchedEffect(ready,needs) {
        if(ready && !settings.init) {
            settings.assistants.filter{it.enableMemory&&it.memoryMode()==MemoryExperience.ADVANCED}.forEach{assistant->
                try {
                repository.initialise(assistant.id.toString())
                val state=repository.metadata(assistant.id.toString())
                if(!state.activeAdvanced&&!state.preparing&&!state.paused&&!state.cancelled){
                    repository.startPreparation(assistant.id.toString())
                    MemoryLearningWorker.enqueue(context,assistant.id.toString(),0)
                }
                } catch(error:Exception) {
                    if(error is kotlinx.coroutines.CancellationException)throw error
                    me.rerere.common.platform.PlatformLog.w("Memory","Memory preparation needs attention; open the character's memory settings.")
                }
            }
        }
    }
    if (!settings.init && settings.setupCompleted && needs.isNotBlank() && !ready && dismissed != needs) {
        ModalBottomSheet(onDismissRequest = { dismissed = needs }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Set up Advanced memory", style = MaterialTheme.typography.headlineSmall)
                EmbeddingModelCard()
                TextButton(onClick = { dismissed = needs }) { Text("Continue for now") }
            }
        }
    }
}
