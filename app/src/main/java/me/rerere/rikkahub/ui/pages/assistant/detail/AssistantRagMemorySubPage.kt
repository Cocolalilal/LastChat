package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.ui.components.ui.FormItem

@Composable
fun AssistantRagMemorySubPage(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onTestRetrieval: ((String) -> Unit)? = null,
    retrievalResults: List<Pair<AssistantMemory, Float>> = emptyList()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // RAG Enable Switch
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("Use RAG Memory Retrieval")
                },
                description = {
                    Text(
                        text = "When enabled, only relevant memories are retrieved based on context. When disabled, all memories are included.",
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.useRagMemoryRetrieval,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    useRagMemoryRetrieval = it
                                )
                            )
                        }
                    )
                }
            )
        }

        if (assistant.useRagMemoryRetrieval) {
            // RAG Similarity Threshold
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RAG Similarity Threshold",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Minimum similarity score (0.0 = include all, 1.0 = only perfect matches). Lower values include more memories. Current: ${String.format("%.2f", assistant.ragSimilarityThreshold)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var threshold by remember(assistant.ragSimilarityThreshold) {
                        mutableFloatStateOf(assistant.ragSimilarityThreshold)
                    }
                    Slider(
                        value = threshold,
                        onValueChange = { newValue ->
                            threshold = newValue
                            onUpdateAssistant(
                                assistant.copy(ragSimilarityThreshold = newValue)
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19 // 0.05 increments
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.0 (All)", style = MaterialTheme.typography.labelSmall)
                        Text("1.0 (Perfect)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // RAG Limit
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RAG Memory Limit",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Maximum number of memories to retrieve per query. Higher values include more context but use more tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var limit by remember(assistant.ragLimit) {
                        mutableIntStateOf(if (assistant.ragLimit > 0) assistant.ragLimit else 5)
                    }
                    Slider(
                        value = limit.toFloat(),
                        onValueChange = { newValue ->
                            val intValue = newValue.toInt()
                            limit = intValue
                            onUpdateAssistant(
                                assistant.copy(ragLimit = intValue)
                            )
                        },
                        valueRange = 1f..20f,
                        steps = 18 // 1 increment
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1", style = MaterialTheme.typography.labelSmall)
                        Text("20", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = "Current: $limit memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Advanced RAG Settings
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advanced RAG Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()

                    FormItem(
                        label = { Text("Include Core Memories") },
                        description = { Text("Include permanent facts in retrieval.") },
                        tail = {
                            Switch(
                                checked = assistant.ragIncludeCore,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeCore = it))
                                }
                            )
                        }
                    )

                    FormItem(
                        label = { Text("Include Episodic Memories") },
                        description = { Text("Include conversation history in retrieval.") },
                        tail = {
                            Switch(
                                checked = assistant.ragIncludeEpisodes,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeEpisodes = it))
                                }
                            )
                        }
                    )

                    FormItem(
                        label = { Text("Enable RAG Logging") },
                        description = { Text("Log detailed retrieval info to Logcat (tag: RAG).") },
                        tail = {
                            Switch(
                                checked = assistant.enableRagLogging,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(enableRagLogging = it))
                                }
                            )
                        }
                    )
                }
            }

            // Recent Chats Reference
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text("Recent Chats Reference")
                    },
                    description = {
                        Text(
                            text = "Include recent chat history in the context window.",
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

            // Memory Debugger
            if (onTestRetrieval != null) {
                MemoryDebugger(
                    onTestRetrieval = onTestRetrieval,
                    retrievalResults = retrievalResults
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
    val (query, setQuery) = remember { mutableStateOf("") }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                androidx.compose.material3.TextField(
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
