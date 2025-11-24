package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Slider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Settings
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.ui.pages.assistant.detail.EmbeddingProgress
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState

@Composable
fun AssistantMemorySettings(
    assistant: Assistant,
    memories: List<AssistantMemory>,
    onUpdateAssistant: (Assistant) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
    onRegenerateEmbeddings: (() -> Unit)? = null,
    embeddingProgress: EmbeddingProgress? = null,
    onTestRetrieval: ((String) -> Unit)? = null,
    retrievalResults: List<Pair<AssistantMemory, Float>> = emptyList()
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    
    // Embedding progress dialog
    if (embeddingProgress != null && embeddingProgress.isRunning) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Generating Embeddings")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Processing ${embeddingProgress.current} of ${embeddingProgress.total} items...")
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { embeddingProgress.current.toFloat() / embeddingProgress.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { }
        )
    }

    // 记忆对话框
    memoryDialogState.EditStateContent { memory, update ->
        AlertDialog(
            onDismissRequest = {
                memoryDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.assistant_page_manage_memory_title))
            },
            text = {
                TextField(
                    value = memory.content,
                    onValueChange = {
                        update(memory.copy(content = it))
                    },
                    label = {
                        Text(stringResource(R.string.assistant_page_manage_memory_title))
                    },
                    minLines = 1,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_memory))
                },
                description = {
                    Text(
                        text = stringResource(R.string.assistant_page_memory_desc),
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.enableMemory,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableMemory = it
                                )
                            )
                        }
                    )
                }
            )
        }

        if (assistant.enableMemory) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_recent_chats))
                    },
                    description = {
                        Text(
                            text = stringResource(R.string.assistant_page_recent_chats_desc),
                        )
                    },
                    tail = {
                        Switch(
                            checked = assistant.enableRecentChatsReference,
                            onCheckedChange = {
                                onUpdateAssistant(
                                    assistant.copy(
                                        enableRecentChatsReference = it
                                    )
                                )
                            }
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterStart)
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onRegenerateEmbeddings != null) {
                    IconButton(
                        onClick = onRegenerateEmbeddings
                    ) {
                        Icon(
                            imageVector = Lucide.RefreshCw,
                            contentDescription = "Regenerate Embeddings"
                        )
                    }
                }
                IconButton(
                    onClick = {
                        memoryDialogState.open(AssistantMemory(0, ""))
                    }
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = null
                    )
                }
            }
        }

        memories.fastForEach { memory ->
            key(memory.id) {
                MemoryItem(
                    memory = memory,
                    onEditMemory = {
                        memoryDialogState.open(it)
                    },
                    onDeleteMemory = onDeleteMemory
                )
            }
        }
    }
}

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (memory.hasEmbedding) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = if (memory.type == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (memory.type == 0) "CORE" else "EPISODIC",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = if (memory.type == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = memory.content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(
                onClick = { onEditMemory(memory) }
            ) {
                Icon(Lucide.Pencil, null)
            }
            IconButton(
                onClick = { onDeleteMemory(memory) }
            ) {
                Icon(
                    Lucide.Trash2,
                    stringResource(R.string.assistant_page_delete)
                )
            }
        }
    }
}

@Composable
private fun MemoryDebugger(
    onTestRetrieval: (String) -> Unit,
    retrievalResults: List<Pair<AssistantMemory, Float>>
) {
    val (query, setQuery) = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Memory Retrieval Debugger",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Test RAG retrieval with a query to see which memories are retrieved and their similarity scores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = setQuery,
                    label = { Text("Test Query") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                androidx.compose.material3.Button(
                    onClick = { onTestRetrieval(query) },
                    enabled = query.isNotBlank()
                ) {
                    Text("Test")
                }
            }

            if (retrievalResults.isNotEmpty()) {
                Text(
                    text = "Results (${retrievalResults.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                retrievalResults.forEachIndexed { index, (memory, score) ->
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Score: ${String.format("%.4f", score)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (score >= 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = memory.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Type: ${if(memory.type == 0) "CORE" else "EPISODIC"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ID: ${memory.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (query.isNotBlank()) {
                Text(
                    text = "No memories retrieved. Try lowering the similarity threshold or check if embeddings are generated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AdvancedMemorySettings(
    assistant: Assistant,
    memories: List<AssistantMemory>,
    onUpdateAssistant: (Assistant) -> Unit,
    onRegenerateEmbeddings: (() -> Unit)?
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header - clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Lucide.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "More about memories",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider()
                    
                    // Memory Statistics
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Memory Statistics",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val coreMemories = memories.count { it.type == 0 }
                        val episodicMemories = memories.count { it.type == 1 }
                        val withEmbeddings = memories.count { it.hasEmbedding }
                        val withoutEmbeddings = memories.size - withEmbeddings
                        
                        Text(
                            text = "Total Memories: ${memories.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "  • Core Memories: $coreMemories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  • Episodic Memories: $episodicMemories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  • With Embeddings: $withEmbeddings",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (withoutEmbeddings > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (withoutEmbeddings > 0) {
                            Text(
                                text = "  • Without Embeddings: $withoutEmbeddings (use Regenerate Embeddings)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // RAG Configuration Details
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RAG Configuration",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "RAG Enabled: ${if (assistant.useRagMemoryRetrieval) "Yes" else "No"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (assistant.useRagMemoryRetrieval) {
                            Text(
                                text = "Similarity Threshold: ${String.format("%.2f", assistant.ragSimilarityThreshold)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Memory Limit: ${assistant.ragLimit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Recent Chats Reference: ${if (assistant.enableRecentChatsReference) "Enabled" else "Disabled"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "All memories are included in every request (no filtering)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Memory Type Information
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Memory Types",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "CORE: Permanent facts about the user (name, preferences, etc.). These don't decay over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "EPISODIC: Summaries of past conversations. These decay over time (7-day half-life) and are weighted by recency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    HorizontalDivider()

                    // How it works
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "The memory system consists of two tracks: Episodic and Core.\n\n" +
                                    "1. Episodic Memory: The AI automatically summarizes conversations into 'episodes'. These help the AI recall recent context but fade over time to keep the context window clean.\n\n" +
                                    "2. Core Memory: The AI reflects on these episodes to extract permanent facts (Core Memories). These are deduplicated and never expire, forming the AI's long-term knowledge about you.\n\n" +
                                    "When you chat, the AI retrieves the most relevant memories based on your current message using vector similarity (RAG).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Actions
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (onRegenerateEmbeddings != null) {
                            Text(
                                text = "Regenerate Embeddings: Click the refresh icon above to regenerate embeddings for all memories. This is useful if the embedding model changed or embeddings are missing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
