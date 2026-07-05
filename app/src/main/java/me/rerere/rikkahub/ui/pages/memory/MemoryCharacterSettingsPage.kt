package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.memory.MemoryPreset
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Per-character memory settings, opened from the gear FAB on the character's memory page.
 * ALL behavioural memory settings live here (per character); the three memory models are global and
 * configured in Settings → Default Models → Memory (footer link).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCharacterSettingsPage(assistantId: String) {
    val vm: MemoryVM = koinViewModel(
        key = "memsettings_$assistantId",
        parameters = { parametersOf(assistantId) },
    )
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val a = assistant ?: return
    val enabled = a.enableMemory

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Memory settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            a.name.ifBlank { "Assistant" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Memory
            Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormItem(
                        label = { Text("Memory") },
                        description = { Text("Let this character remember. Off keeps everything stored but stops learning and recall.") },
                        tail = {
                            HapticSwitch(checked = a.enableMemory, onCheckedChange = { on ->
                                vm.updateAssistant { it.copy(enableMemory = on) }
                            })
                        },
                    )
                    FormItem(
                        label = { Text("Shared user memory") },
                        description = { Text("Read and write the cross-character layer. Off = this character's memory only.") },
                        tail = {
                            HapticSwitch(checked = a.useSharedUserMemory, enabled = enabled, onCheckedChange = { on ->
                                vm.updateAssistant { it.copy(useSharedUserMemory = on) }
                            })
                        },
                    )
                }
            }

            // Cost preset
            Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormItem(
                        label = { Text("Cost preset") },
                        description = { Text("Balances how often memories are extracted against background API usage") },
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemoryPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = a.memoryPreset.equals(preset.name, true),
                                    enabled = enabled,
                                    onClick = {
                                        haptics.perform(HapticPattern.Tick)
                                        vm.updateAssistant { it.copy(memoryPreset = preset.name) }
                                    },
                                    label = { Text(preset.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                                )
                            }
                        }
                    }
                }
            }

            // Behaviour
            Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormItem(
                        label = { Text("Time awareness") },
                        description = { Text("Fuzzy ages and past-tense phrasing in recall") },
                        tail = {
                            HapticSwitch(checked = a.memoryTimeAwareness, enabled = enabled, onCheckedChange = { on ->
                                vm.updateAssistant { it.copy(memoryTimeAwareness = on) }
                            })
                        },
                    )
                    FormItem(
                        label = { Text("Proactive curiosity") },
                        description = { Text("Occasional gentle in-character questions to fill knowledge gaps") },
                        tail = {
                            HapticSwitch(checked = a.memoryProactiveCuriosity, enabled = enabled, onCheckedChange = { on ->
                                vm.updateAssistant { it.copy(memoryProactiveCuriosity = on) }
                            })
                        },
                    )
                    FormItem(
                        label = { Text("Web lookups for curiosity") },
                        description = { Text("Let curiosity pre-research answers via web search") },
                        tail = {
                            HapticSwitch(
                                checked = a.memoryCuriosityWebLookups,
                                enabled = enabled && a.memoryProactiveCuriosity,
                                onCheckedChange = { on -> vm.updateAssistant { it.copy(memoryCuriosityWebLookups = on) } },
                            )
                        },
                    )
                    HorizontalDivider()
                    FormItem(
                        label = { Text("Habit induction") },
                        description = { Text("Compress repeated episodes into habit memories") },
                        tail = {
                            HapticSwitch(checked = a.memoryHabitInduction, enabled = enabled, onCheckedChange = { on ->
                                vm.updateAssistant { it.copy(memoryHabitInduction = on) }
                            })
                        },
                    )
                }
            }

            // Models footer hint
            Surface(shape = AppShapes.CardMedium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Memory models (parser, embedding, consolidation) are configured for all characters in Default Models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { navController.navigate(Screen.SettingModels) }) {
                        Text("Open Default Models")
                    }
                }
            }
        }
    }
}
