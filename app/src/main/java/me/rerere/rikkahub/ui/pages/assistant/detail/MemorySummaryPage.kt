package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.MemorySummaryEntity
import me.rerere.rikkahub.data.memory.DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT
import me.rerere.rikkahub.data.memory.DiveDeeperAction
import me.rerere.rikkahub.data.memory.MemorySummaryData
import me.rerere.rikkahub.data.memory.MemorySummarySection
import me.rerere.rikkahub.data.memory.parseMemorySummaryJson
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.base64Encode
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySummaryPage(
    assistantId: String,
    navBackStack: NavHostController,
    vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(assistantId) }),
) {
    val memorySummary: MemorySummaryEntity? by vm.memorySummary.collectAsStateWithLifecycle(null)
    val isGenerating: Boolean by vm.isSummaryGenerating.collectAsStateWithLifecycle(false)
    val summaryError: String? by vm.summaryError.collectAsStateWithLifecycle(null)
    val settings: Settings by vm.settings.collectAsStateWithLifecycle(Settings.dummy())
    val assistant = remember(settings, assistantId) {
        settings.assistants.find { it.id.toString() == assistantId }
    }

    val summaryData: MemorySummaryData? = remember(memorySummary) {
        memorySummary?.summaryJson?.let { parseMemorySummaryJson(it) }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var askInputText by remember { mutableStateOf("") }

    val isDark = LocalDarkMode.current

    // Dialog for editing system prompt
    if (showPromptDialog && assistant != null) {
        var tempPrompt by remember {
            mutableStateOf(assistant.customMemorySummaryPrompt ?: DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT)
        }
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Summarizer System Prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize the prompt that instructs the AI how to synthesize and organize your memories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempPrompt,
                        onValueChange = { tempPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        maxLines = 12,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.update(
                            assistant.copy(
                                customMemorySummaryPrompt = tempPrompt.takeIf { it != DEFAULT_MEMORY_SUMMARY_SYSTEM_PROMPT }
                            )
                        )
                        showPromptDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for adjusting token budget
    if (showBudgetDialog && assistant != null) {
        var tempBudget by remember { mutableFloatStateOf(assistant.memorySummaryTokenBudget.toFloat()) }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Summary Token Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sets the target length of your synthesized summary. Larger budgets capture more nuances across vast memory sets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Budget", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${tempBudget.toInt()} tokens",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = tempBudget,
                        onValueChange = { tempBudget = it },
                        valueRange = 200f..10000f,
                        steps = 48,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.update(
                            assistant.copy(
                                memorySummaryTokenBudget = tempBudget.toInt().coerceIn(200, 10000)
                            )
                        )
                        showBudgetDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Memory Summary", style = MaterialTheme.typography.titleLarge)
                            val subtitleText = memorySummary?.let {
                                formatRelativeAgo(it.updatedAt)
                            } ?: "Not synthesized yet"
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navBackStack.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { vm.regenerateMemorySummary() },
                            enabled = !isGenerating
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Regenerate summary") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = {
                                        showMenu = false
                                        vm.regenerateMemorySummary()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("System prompt") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = {
                                        showMenu = false
                                        showPromptDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Token budget") },
                                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                                    onClick = {
                                        showMenu = false
                                        showBudgetDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
                AnimatedVisibility(visible = isGenerating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = askInputText,
                        onValueChange = { askInputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Ask or update",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (askInputText.isNotBlank() && !isGenerating) {
                                val text = askInputText.trim()
                                askInputText = ""
                                vm.askOrUpdateMemorySummary(text)
                            }
                        })
                    )
                    IconButton(
                        onClick = {
                            if (askInputText.isNotBlank() && !isGenerating) {
                                val text = askInputText.trim()
                                askInputText = ""
                                vm.askOrUpdateMemorySummary(text)
                            }
                        },
                        enabled = askInputText.isNotBlank() && !isGenerating,
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (askInputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (askInputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Error banner if any
            if (summaryError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = summaryError.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (summaryData == null && !isGenerating) {
                // Empty state card
                Surface(
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            text = "No Memory Summary Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Synthesize all of your stored memories into a cohesive, high-level profile that gets referenced on every turn.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.regenerateMemorySummary() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Memory Summary")
                        }
                    }
                }
            } else if (summaryData != null) {
                // Main Summary Container Card (M3 Expressive)
                Surface(
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Header: Avatar / "Your Summary"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = summaryData.title.ifBlank { "Your Summary" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (summaryData.subtitle.isNotBlank()) {
                                    Text(
                                        text = summaryData.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Dynamic sections with tinted icon tags
                        summaryData.sections.forEachIndexed { index, section ->
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                            )
                            MemorySummarySectionItem(section = section, index = index)
                        }
                    }
                }

                // "Dive Deeper" exploration cards
                if (summaryData.diveDeeper.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(
                                text = "Dive Deeper",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Explore specific areas of your memory in more detail.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        summaryData.diveDeeper.forEach { action ->
                            DiveDeeperCard(
                                action = action,
                                onClick = {
                                    vm.switchToAssistant(assistantId)
                                    navBackStack.navigate(
                                        Screen.Chat(
                                            id = java.util.UUID.randomUUID().toString(),
                                            text = action.prompt.base64Encode(),
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemorySummarySectionItem(
    section: MemorySummarySection,
    index: Int,
) {
    val icon = remember(section.icon) { resolveSectionIcon(section.icon) }
    val tintColor = when (index % 4) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = when (index % 4) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = section.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
    }
}

@Composable
private fun DiveDeeperCard(
    action: DiveDeeperAction,
    onClick: () -> Unit,
) {
    val isDark = LocalDarkMode.current
    val icon = remember(action.icon) { resolveSectionIcon(action.icon) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (action.subtitle.isNotBlank()) {
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun resolveSectionIcon(tag: String): ImageVector {
    val lower = tag.lowercase()
    return when {
        "device" in lower || "tablet" in lower || "hardware" in lower -> Icons.Rounded.Devices
        "code" in lower || "tech" in lower || "dev" in lower -> Icons.Rounded.Code
        "heart" in lower || "interest" in lower || "like" in lower -> Icons.Rounded.Favorite
        "target" in lower || "goal" in lower || "context" in lower -> Icons.Rounded.Adjust
        "star" in lower || "pref" in lower -> Icons.Rounded.Star
        else -> Icons.Rounded.AutoAwesome
    }
}

private fun formatRelativeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = (diff / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated $minutes min. ago"
        hours < 24 -> "Updated $hours hr. ago"
        else -> "Updated $days d. ago"
    }
}
