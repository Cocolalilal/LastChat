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
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
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
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
            colors = CardDefaults.cardColors(
                    containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_memory_rag_retrieval))
                },
                description = {
                    Text(
                        text = stringResource(R.string.assistant_memory_rag_retrieval_desc),
                    )
                },
                tail = {
                    HapticSwitch(
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
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.assistant_memory_similarity_threshold),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_memory_similarity_threshold_desc,
                            assistant.ragSimilarityThreshold
                        ),
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
                        Text(stringResource(R.string.assistant_memory_similarity_all), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.assistant_memory_similarity_perfect), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // RAG Max Memories
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.assistant_memory_rag_limit),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_memory_rag_limit_desc,
                            if (assistant.ragLimit > 50) stringResource(R.string.assistant_memory_rag_limit_unlimited) else assistant.ragLimit.toString()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var limit by remember(assistant.ragLimit) {
                        mutableFloatStateOf(assistant.ragLimit.toFloat())
                    }
                    Slider(
                        value = limit,
                        onValueChange = { newValue ->
                            limit = newValue
                            onUpdateAssistant(
                                assistant.copy(ragLimit = newValue.toInt())
                            )
                        },
                        valueRange = 5f..55f,
                        steps = 9
                    )
                }
            }

            // Advanced RAG Settings
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.assistant_memory_rag_settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )


                    FormItem(
                        label = { Text(stringResource(R.string.assistant_memory_include_core)) },
                        description = { Text(stringResource(R.string.assistant_memory_include_core_desc)) },
                        tail = {
                            HapticSwitch(
                                checked = assistant.ragIncludeCore,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeCore = it))
                                }
                            )
                        }
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.assistant_memory_include_episodic)) },
                        description = { Text(stringResource(R.string.assistant_memory_include_episodic_desc)) },
                        tail = {
                            HapticSwitch(
                                checked = assistant.ragIncludeEpisodes,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeEpisodes = it))
                                }
                            )
                        }
                    )


                }
            }

            // Recent Chats Reference
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_memory_recent_chats_reference))
                    },
                    description = {
                        Text(
                            text = stringResource(R.string.assistant_memory_recent_chats_reference_desc),
                        )
                    },
                    tail = {
                        HapticSwitch(
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
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_memory_retrieval_debugger),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.assistant_memory_retrieval_debugger_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = setQuery,
                    label = { Text(stringResource(R.string.assistant_memory_test_query)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                )
                androidx.compose.material3.Button(
                    onClick = { onTestRetrieval(query) },
                    enabled = query.isNotBlank()
                ) {
                    Text(stringResource(R.string.assistant_memory_test))
                }
            }

            if (retrievalResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.assistant_memory_results, retrievalResults.size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                retrievalResults.forEachIndexed { index, (memory, score) ->
                    Card(
                        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
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
                                    text = stringResource(
                                        R.string.assistant_memory_score,
                                        String.format("%.4f", score)
                                    ),
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
                                    text = stringResource(
                                        R.string.assistant_memory_type_label,
                                        stringResource(
                                            if (memory.type == 0) {
                                                R.string.assistant_memory_type_core
                                            } else {
                                                R.string.assistant_memory_type_episodic
                                            }
                                        )
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.assistant_memory_id_label, memory.id),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.assistant_memory_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
