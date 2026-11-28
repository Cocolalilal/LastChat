package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.context.ContextCountConfidence
import me.rerere.ai.context.ContextMapCategory
import me.rerere.ai.context.ContextMapEntry
import me.rerere.ai.context.ContextUsageBreakdown
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor
import me.rerere.rikkahub.ui.motion.ExpandableContent
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailRoutes
import me.rerere.rikkahub.ui.theme.AppShapes
import kotlin.math.roundToInt

internal fun contextCategoryColor(category: ContextMapCategory, isDark: Boolean): Color =
    when (category) {
        ContextMapCategory.CONVERSATION -> if (isDark) Color(0xFF8AB4F8) else Color(0xFF2457C5)
        ContextMapCategory.SYSTEM_PROMPT -> if (isDark) Color(0xFFFF8A80) else Color(0xFFC43D3D)
        ContextMapCategory.CUSTOM_INSTRUCTIONS -> if (isDark) Color(0xFFFFAB91) else Color(0xFFBF360C)
        ContextMapCategory.MEMORY_SUMMARY -> if (isDark) Color(0xFF80CBC4) else Color(0xFF00695C)
        ContextMapCategory.CONVERSATION_SUMMARY -> if (isDark) Color(0xFFFFD166) else Color(0xFF9A6500)
        ContextMapCategory.MEMORIES -> if (isDark) Color(0xFF7ED99B) else Color(0xFF187A3B)
        ContextMapCategory.SKILLS_MODES -> if (isDark) Color(0xFFC69AF7) else Color(0xFF7139B6)
        ContextMapCategory.LOREBOOKS -> if (isDark) Color(0xFF4DD0E1) else Color(0xFF087F8C)
        ContextMapCategory.FEATURE_PROMPTS -> if (isDark) Color(0xFFFFE082) else Color(0xFF8D6E00)
        ContextMapCategory.TOOL_DEFINITIONS -> if (isDark) Color(0xFFFFA45B) else Color(0xFFB84A00)
        ContextMapCategory.TOOL_CALLS -> if (isDark) Color(0xFFF48FB1) else Color(0xFFA92868)
        ContextMapCategory.MEDIA -> if (isDark) Color(0xFFB6D957) else Color(0xFF5F7300)
        ContextMapCategory.RESERVED -> if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161)
    }

private data class ContextMapSectionUi(
    val category: ContextMapCategory,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tokens: Int,
    val items: List<ContextMapEntry>,
    val primaryAction: ContextMapTarget,
    val secondaryChips: List<Pair<String, ContextMapTarget>> = emptyList(),
)

private sealed interface ContextMapTarget {
    data class Route(val screen: Screen) : ContextMapTarget
    data object OpenContextManagement : ContextMapTarget
    data object OpenLimitEditor : ContextMapTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ContextMapSheet(
    usage: ContextUsageBreakdown,
    assistant: Assistant,
    model: Model?,
    initialFocusCategory: ContextMapCategory? = null,
    onNavigate: (Screen) -> Unit,
    onOpenContextManagement: () -> Unit,
    onOpenLimitEditor: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val assistantId = assistant.id.toString()

    val expandedCategories = remember(initialFocusCategory) {
        mutableStateMapOf<ContextMapCategory, Boolean>().apply {
            if (initialFocusCategory != null) {
                put(initialFocusCategory, true)
            }
        }
    }

    val entriesByCategory = remember(usage.mapEntries) {
        usage.mapEntries.groupBy { it.category }
    }

    val sections = remember(usage, assistant, entriesByCategory) {
        buildMapSections(
            usage = usage,
            assistant = assistant,
            assistantId = assistantId,
            entriesByCategory = entriesByCategory,
        )
    }

    fun dispatchTarget(target: ContextMapTarget) {
        haptics.perform(HapticPattern.Selection)
        when (target) {
            is ContextMapTarget.Route -> onNavigate(target.screen)
            ContextMapTarget.OpenContextManagement -> onOpenContextManagement()
            ContextMapTarget.OpenLimitEditor -> onOpenLimitEditor()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        containerColor = lastChatSheetContainerColor(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.context_meter_full_map),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(assistant.name.ifBlank { "Assistant" })
                            model?.let {
                                append(" · ")
                                append(it.displayName.ifBlank { it.modelId })
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item {
                HeroContextOverviewCard(
                    usage = usage,
                    isDark = isDark,
                    reduceMotion = reduceMotion,
                    onEditLimit = { dispatchTarget(ContextMapTarget.OpenLimitEditor) },
                )
            }

            items(
                items = sections,
                key = { it.category.name },
            ) { section ->
                val isFocused = initialFocusCategory == section.category
                val hasSubItems = section.items.size > 1 ||
                    section.category == ContextMapCategory.FEATURE_PROMPTS ||
                    section.category == ContextMapCategory.TOOL_DEFINITIONS
                val expanded = expandedCategories[section.category] == true

                ContextMapSectionCard(
                    section = section,
                    totalWindowTokens = usage.totalTokens,
                    color = contextCategoryColor(section.category, isDark),
                    isFocused = isFocused,
                    hasSubItems = hasSubItems,
                    expanded = expanded,
                    onToggleExpand = {
                        haptics.perform(HapticPattern.Selection)
                        expandedCategories[section.category] = !expanded
                    },
                    onPrimaryClick = { dispatchTarget(section.primaryAction) },
                    onItemClick = { entry ->
                        dispatchTarget(
                            resolveEntryTarget(
                                entry = entry,
                                assistant = assistant,
                                assistantId = assistantId,
                            )
                        )
                    },
                    onChipClick = { target -> dispatchTarget(target) },
                )
            }
        }
    }
}

@Composable
private fun HeroContextOverviewCard(
    usage: ContextUsageBreakdown,
    isDark: Boolean,
    reduceMotion: Boolean,
    onEditLimit: () -> Unit,
) {
    val total = usage.totalTokens.coerceAtLeast(1)
    val usedFraction = (usage.usedTokens.toFloat() / total).coerceIn(0f, 1f)
    val animatedUsedFraction by if (reduceMotion) {
        rememberUpdatedState(usedFraction)
    } else {
        animateFloatAsState(
            targetValue = usedFraction,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
            label = "context_map_hero_fraction",
        )
    }

    val segments = remember(usage, isDark) {
        listOf(
            ContextMapCategory.CONVERSATION to usage.conversationTokens,
            ContextMapCategory.SYSTEM_PROMPT to usage.systemPromptTokens,
            ContextMapCategory.CUSTOM_INSTRUCTIONS to usage.customInstructionsTokens,
            ContextMapCategory.MEMORY_SUMMARY to usage.memorySummaryTokens,
            ContextMapCategory.CONVERSATION_SUMMARY to usage.summaryTokens,
            ContextMapCategory.MEMORIES to usage.memoryTokens,
            ContextMapCategory.SKILLS_MODES to usage.skillTokens,
            ContextMapCategory.LOREBOOKS to usage.lorebookTokens,
            ContextMapCategory.FEATURE_PROMPTS to usage.featurePromptsTokens,
            ContextMapCategory.TOOL_DEFINITIONS to usage.toolDefinitionTokens,
            ContextMapCategory.TOOL_CALLS to usage.toolCallTokens,
            ContextMapCategory.MEDIA to usage.mediaTokens,
        ).filter { it.second > 0 }
    }
    val reserveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.context_meter_used,
                            formatCompactTokens(usage.usedTokens),
                            formatCompactTokens(usage.totalTokens),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Usable ${formatCompactTokens(usage.usableInputTokens)} · Free ${formatCompactTokens(usage.availableTokens)} · Reserved ${formatCompactTokens(usage.reservedTokens)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = AppShapes.ButtonPill,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(
                            when (usage.confidence) {
                                ContextCountConfidence.EXACT -> R.string.context_meter_exact
                                ContextCountConfidence.PROVIDER_COUNTED -> R.string.context_meter_provider_counted
                                ContextCountConfidence.ESTIMATED -> R.string.context_meter_estimated
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp)),
                ) {
                    segments.forEach { (category, tokens) ->
                        Spacer(
                            modifier = Modifier
                                .weight(tokens.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(contextCategoryColor(category, isDark)),
                        )
                    }
                    if (usage.availableTokens > 0) {
                        Spacer(
                            modifier = Modifier
                                .weight(usage.availableTokens.toFloat())
                                .fillMaxHeight(),
                        )
                    }
                    if (usage.reservedTokens > 0) {
                        Spacer(
                            modifier = Modifier
                                .weight(usage.reservedTokens.toFloat())
                                .fillMaxHeight()
                                .background(reserveColor),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val pct = (animatedUsedFraction * 100f).roundToInt().coerceIn(0, 100)
                Text(
                    text = "$pct% of context window used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = onEditLimit,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("Adjust context limit") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextMapSectionCard(
    section: ContextMapSectionUi,
    totalWindowTokens: Int,
    color: Color,
    isFocused: Boolean,
    hasSubItems: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPrimaryClick: () -> Unit,
    onItemClick: (ContextMapEntry) -> Unit,
    onChipClick: (ContextMapTarget) -> Unit,
) {
    val percentLabel = if (totalWindowTokens > 0) {
        val pct = (section.tokens.toFloat() / totalWindowTokens.toFloat()) * 100f
        if (pct > 0f && pct < 1f) "<1%" else "${pct.roundToInt()}%"
    } else {
        "0%"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = color.copy(alpha = 0.7f),
                        shape = AppShapes.CardSmall,
                    )
                } else {
                    Modifier
                }
            ),
        shape = AppShapes.CardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = if (hasSubItems && section.items.isNotEmpty()) {
                            onToggleExpand
                        } else {
                            onPrimaryClick
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCompactTokens(section.tokens),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (section.tokens > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = percentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = when {
                        hasSubItems && section.items.isNotEmpty() && expanded -> Icons.Rounded.ExpandLess
                        hasSubItems && section.items.isNotEmpty() -> Icons.Rounded.ExpandMore
                        else -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (hasSubItems && section.items.isNotEmpty()) {
                ExpandableContent(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        section.items.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onItemClick(entry) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = iconForEntry(entry),
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    entry.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    text = formatCompactTokens(entry.tokens),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        if (section.secondaryChips.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                section.secondaryChips.forEach { (chipLabel, target) ->
                                    AssistChip(
                                        onClick = { onChipClick(target) },
                                        label = { Text(chipLabel) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildMapSections(
    usage: ContextUsageBreakdown,
    assistant: Assistant,
    assistantId: String,
    entriesByCategory: Map<ContextMapCategory, List<ContextMapEntry>>,
): List<ContextMapSectionUi> = buildList {
    val systemItems = entriesByCategory[ContextMapCategory.SYSTEM_PROMPT].orEmpty()
    add(
        ContextMapSectionUi(
            category = ContextMapCategory.SYSTEM_PROMPT,
            title = "System Prompt & Learning Mode",
            subtitle = when {
                assistant.systemPrompt.isBlank() && !assistant.learningMode -> "Not configured · Tap to edit"
                assistant.learningMode -> "Persona prompt + Learning Mode active"
                else -> assistant.systemPrompt.trim().take(48).replace('\n', ' ')
            },
            icon = Icons.Rounded.Description,
            tokens = usage.systemPromptTokens,
            items = systemItems,
            primaryAction = ContextMapTarget.Route(
                Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.PROMPTS)
            ),
        )
    )

    val instructionItems = entriesByCategory[ContextMapCategory.CUSTOM_INSTRUCTIONS].orEmpty()
    add(
        ContextMapSectionUi(
            category = ContextMapCategory.CUSTOM_INSTRUCTIONS,
            title = "Custom Instructions",
            subtitle = if (instructionItems.isEmpty()) {
                "None active · Tap to configure"
            } else {
                instructionItems.joinToString(", ") { it.label }
            },
            icon = Icons.Rounded.Tune,
            tokens = usage.customInstructionsTokens,
            items = instructionItems,
            primaryAction = ContextMapTarget.Route(Screen.SettingInstructions),
        )
    )

    add(
        ContextMapSectionUi(
            category = ContextMapCategory.MEMORY_SUMMARY,
            title = "Memory Summary Profile",
            subtitle = when {
                !assistant.enableMemory -> "Memories disabled"
                !assistant.enableMemorySummary -> "Summary profile off · Tap to open"
                usage.memorySummaryTokens > 0 -> "Baseline user profile injected"
                else -> "Enabled (no profile generated yet)"
            },
            icon = Icons.Rounded.Psychology,
            tokens = usage.memorySummaryTokens,
            items = entriesByCategory[ContextMapCategory.MEMORY_SUMMARY].orEmpty(),
            primaryAction = ContextMapTarget.Route(Screen.MemorySummary(assistantId = assistantId)),
        )
    )

    add(
        ContextMapSectionUi(
            category = ContextMapCategory.MEMORIES,
            title = "Long-Term Memories",
            subtitle = when {
                !assistant.enableMemory -> "Disabled · Tap to enable"
                assistant.useRagMemoryRetrieval -> "RAG recall (Core & Episodic)"
                else -> "Direct core memory injection"
            },
            icon = Icons.Rounded.Memory,
            tokens = usage.memoryTokens,
            items = entriesByCategory[ContextMapCategory.MEMORIES].orEmpty(),
            primaryAction = ContextMapTarget.Route(
                Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.MEMORY)
            ),
        )
    )

    add(
        ContextMapSectionUi(
            category = ContextMapCategory.CONVERSATION_SUMMARY,
            title = "Conversation Summary",
            subtitle = if (usage.summaryTokens > 0) {
                "Compressed earlier chat turns"
            } else {
                "No summary yet · Tap to summarize"
            },
            icon = Icons.Rounded.Compress,
            tokens = usage.summaryTokens,
            items = entriesByCategory[ContextMapCategory.CONVERSATION_SUMMARY].orEmpty(),
            primaryAction = ContextMapTarget.OpenContextManagement,
        )
    )

    val skillItems = entriesByCategory[ContextMapCategory.SKILLS_MODES].orEmpty()
    add(
        ContextMapSectionUi(
            category = ContextMapCategory.SKILLS_MODES,
            title = "Skills & Modes",
            subtitle = if (skillItems.isEmpty()) {
                "No active skills · Tap to browse"
            } else {
                "${skillItems.size} active: ${skillItems.joinToString(", ") { it.label }}"
            },
            icon = Icons.Rounded.AutoAwesome,
            tokens = usage.skillTokens,
            items = skillItems,
            primaryAction = ContextMapTarget.Route(Screen.SettingSkills()),
            secondaryChips = listOf(
                "Assistant skills tab" to ContextMapTarget.Route(
                    Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.SKILLS)
                )
            ),
        )
    )

    val lorebookItems = entriesByCategory[ContextMapCategory.LOREBOOKS].orEmpty()
    val singleLorebookId = lorebookItems.singleOrNull()?.id?.removePrefix("lorebook:")
    add(
        ContextMapSectionUi(
            category = ContextMapCategory.LOREBOOKS,
            title = "Lorebooks",
            subtitle = if (lorebookItems.isEmpty()) {
                "No active lorebooks · Tap to manage"
            } else {
                lorebookItems.joinToString(", ") { it.label }
            },
            icon = Icons.Rounded.Book,
            tokens = usage.lorebookTokens,
            items = lorebookItems,
            primaryAction = ContextMapTarget.Route(
                if (singleLorebookId != null) {
                    Screen.SettingLorebookDetail(id = singleLorebookId)
                } else {
                    Screen.SettingLorebooks
                }
            ),
            secondaryChips = listOf(
                "All lorebooks" to ContextMapTarget.Route(Screen.SettingLorebooks),
                "Assistant lorebooks" to ContextMapTarget.Route(
                    Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.LOREBOOKS)
                ),
            ),
        )
    )

    val featureItems = entriesByCategory[ContextMapCategory.FEATURE_PROMPTS].orEmpty()
    if (usage.featurePromptsTokens > 0 || featureItems.isNotEmpty()) {
        add(
            ContextMapSectionUi(
                category = ContextMapCategory.FEATURE_PROMPTS,
                title = "Built-in Feature Prompts",
                subtitle = featureItems.joinToString(", ") { it.label }.ifBlank { "Active feature prompts" },
                icon = Icons.Rounded.Widgets,
                tokens = usage.featurePromptsTokens,
                items = featureItems,
                primaryAction = ContextMapTarget.Route(Screen.SettingArtifacts),
            )
        )
    }

    val toolItems = entriesByCategory[ContextMapCategory.TOOL_DEFINITIONS].orEmpty()
    val hasBrowserTool = me.rerere.rikkahub.data.ai.tools.LocalToolOption.Browser in assistant.localTools
    add(
        ContextMapSectionUi(
            category = ContextMapCategory.TOOL_DEFINITIONS,
            title = "Tool Definitions",
            subtitle = if (toolItems.isEmpty()) {
                "No tools enabled · Tap to configure"
            } else {
                toolItems.joinToString(", ") { it.label }
            },
            icon = Icons.Rounded.Build,
            tokens = usage.toolDefinitionTokens,
            items = toolItems,
            primaryAction = ContextMapTarget.Route(
                Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.TOOLS)
            ),
            secondaryChips = if (hasBrowserTool) {
                listOf("Browser settings" to ContextMapTarget.Route(Screen.SettingBrowser))
            } else {
                emptyList()
            },
        )
    )

    if (usage.toolCallTokens > 0) {
        add(
            ContextMapSectionUi(
                category = ContextMapCategory.TOOL_CALLS,
                title = "Tool Calls & Results",
                subtitle = "In-chat tool outputs · Tap to manage context",
                icon = Icons.Rounded.DataObject,
                tokens = usage.toolCallTokens,
                items = entriesByCategory[ContextMapCategory.TOOL_CALLS].orEmpty(),
                primaryAction = ContextMapTarget.OpenContextManagement,
            )
        )
    }

    add(
        ContextMapSectionUi(
            category = ContextMapCategory.CONVERSATION,
            title = "Conversation History",
            subtitle = entriesByCategory[ContextMapCategory.CONVERSATION]
                ?.firstOrNull()?.subtitle
                ?: "Active turns · Tap to compress or trim",
            icon = Icons.AutoMirrored.Rounded.Chat,
            tokens = usage.conversationTokens,
            items = entriesByCategory[ContextMapCategory.CONVERSATION].orEmpty(),
            primaryAction = ContextMapTarget.OpenContextManagement,
        )
    )

    if (usage.mediaTokens > 0) {
        add(
            ContextMapSectionUi(
                category = ContextMapCategory.MEDIA,
                title = "Images & Attachments",
                subtitle = "${usage.imageCount} images in context · Tap to manage",
                icon = Icons.Rounded.Image,
                tokens = usage.mediaTokens,
                items = entriesByCategory[ContextMapCategory.MEDIA].orEmpty(),
                primaryAction = ContextMapTarget.OpenContextManagement,
            )
        )
    }

    if (usage.reservedTokens > 0) {
        add(
            ContextMapSectionUi(
                category = ContextMapCategory.RESERVED,
                title = "Reserved Output & Safety Buffer",
                subtitle = "Output ceiling + framing margin",
                icon = Icons.Rounded.History,
                tokens = usage.reservedTokens,
                items = emptyList(),
                primaryAction = ContextMapTarget.Route(
                    Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.MODEL)
                ),
            )
        )
    }
}

private fun resolveEntryTarget(
    entry: ContextMapEntry,
    assistant: Assistant,
    assistantId: String,
): ContextMapTarget = when {
    entry.id == "system_prompt" || entry.id == "learning_mode" ->
        ContextMapTarget.Route(Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.PROMPTS))

    entry.id.startsWith("instruction:") ->
        ContextMapTarget.Route(Screen.SettingInstructions)

    entry.id == "memory_summary" ->
        ContextMapTarget.Route(Screen.MemorySummary(assistantId = assistantId))

    entry.id == "memories" ->
        ContextMapTarget.Route(Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.MEMORY))

    entry.id == "conversation_summary" ->
        ContextMapTarget.OpenContextManagement

    entry.id.startsWith("skill:") ->
        ContextMapTarget.Route(Screen.SettingSkills(scrollToSkillId = entry.id.removePrefix("skill:")))

    entry.id.startsWith("lorebook:") ->
        ContextMapTarget.Route(Screen.SettingLorebookDetail(id = entry.id.removePrefix("lorebook:")))

    entry.id == "feature:artifacts" ->
        ContextMapTarget.Route(Screen.SettingArtifacts)

    entry.id == "feature:tmdb" ->
        ContextMapTarget.Route(Screen.SettingTmdb)

    entry.id == "feature:voice_memos" ->
        ContextMapTarget.Route(Screen.VoiceMemos)

    entry.id == "studio_hub" ->
        ContextMapTarget.Route(Screen.Studio())

    entry.id == "feature:time_awareness" ->
        ContextMapTarget.Route(Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.ADVANCED))

    entry.id == "feature:visual_privacy" ->
        ContextMapTarget.Route(Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.TOOLS))

    entry.id == "tools:local" ->
        ContextMapTarget.Route(Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.TOOLS))

    entry.id == "tools:mcp" ->
        ContextMapTarget.Route(Screen.SettingMcp)

    entry.id == "tools:workspace" -> {
        val wsId = assistant.workspaceId?.toString()
        ContextMapTarget.Route(
            if (wsId != null) Screen.WorkspaceDetail(id = wsId) else Screen.Workspaces
        )
    }

    entry.id == "conversation" || entry.id == "tool_calls" || entry.id == "media" ->
        ContextMapTarget.OpenContextManagement

    else -> ContextMapTarget.Route(
        Screen.AssistantDetail(id = assistantId, startRoute = AssistantDetailRoutes.MODEL)
    )
}

private fun iconForEntry(entry: ContextMapEntry): ImageVector = when {
    entry.id == "feature:artifacts" -> Icons.Rounded.AutoAwesome
    entry.id == "feature:tmdb" -> Icons.Rounded.Movie
    entry.id == "feature:voice_memos" -> Icons.Rounded.GraphicEq
    entry.id == "studio_hub" -> Icons.Rounded.EditNote
    entry.id == "feature:time_awareness" -> Icons.Rounded.Schedule
    entry.id == "feature:visual_privacy" -> Icons.Rounded.Shield
    entry.id == "tools:local" -> Icons.Rounded.Build
    entry.id == "tools:mcp" -> Icons.Rounded.Widgets
    entry.id == "tools:workspace" -> Icons.Rounded.Terminal
    entry.category == ContextMapCategory.CUSTOM_INSTRUCTIONS -> Icons.Rounded.Tune
    entry.category == ContextMapCategory.SKILLS_MODES -> Icons.Rounded.AutoAwesome
    entry.category == ContextMapCategory.LOREBOOKS -> Icons.Rounded.Book
    else -> Icons.Rounded.Description
}

private fun formatCompactTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000f)
    tokens >= 1_000 -> "%.1fK".format(tokens / 1_000f)
    else -> tokens.toString()
}
