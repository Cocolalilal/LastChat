package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.NewChatContentStyle
import me.rerere.rikkahub.data.datastore.NewChatHeaderStyle
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.pages.menu.TimeLabel
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * Stats data for new chat widgets
 */
data class NewChatStats(
    val dailyStreak: Int = 0,
    val totalChats: Int = 0,
    val timeLabel: TimeLabel = TimeLabel.DAYTIME_CHATTER,
    val hasChattedToday: Boolean = false,
    // Per-assistant stats (when viewing specific assistant)
    val assistantChats: Int = 0,
    val mostUsedModelName: String? = null
)

/**
 * New chat content shown when there are no preset messages.
 * Layout matches MenuPage stats exactly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewChatContent(
    assistant: Assistant,
    headerStyle: NewChatHeaderStyle,
    contentStyle: NewChatContentStyle,
    showAvatarInHeader: Boolean = true,
    stats: NewChatStats,
    hasBackgroundImage: Boolean = false,
    onTemplateClick: (String) -> Unit,
    onNavigateToImageGen: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Pre-fetch string resources - add trailing space for cursor positioning
    val writePrompt = stringResource(R.string.new_chat_template_write_prompt) + " "
    val codePrompt = stringResource(R.string.new_chat_template_code_prompt) + " "
    val brainstormPrompt = stringResource(R.string.new_chat_template_brainstorm_prompt) + " "
    val learnPrompt = stringResource(R.string.new_chat_template_learn_prompt) + " "

    // Full-width container centered in parent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === HEADER SECTION ===
        when (headerStyle) {
            NewChatHeaderStyle.BIG_ICON -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = if (onAvatarClick != null && showAvatarInHeader) Modifier.clickable { onAvatarClick() } else Modifier
                ) {
                    if (showAvatarInHeader) {
                        UIAvatar(
                            name = assistant.name.ifBlank { "Character" },
                            value = assistant.avatar,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Text(
                        text = assistant.name.ifBlank { "Character" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            NewChatHeaderStyle.GREETING -> {
                if (showAvatarInHeader) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = if (onAvatarClick != null) Modifier.clickable { onAvatarClick() } else Modifier
                    ) {
                        UIAvatar(
                            name = assistant.name.ifBlank { "Character" },
                            value = assistant.avatar,
                            modifier = Modifier.size(44.dp)
                        )
                        Greeting(
                            style = MaterialTheme.typography.titleLarge,
                            assistant = assistant
                        )
                    }
                } else {
                    // Just greeting text without avatar
                    Greeting(
                        style = MaterialTheme.typography.titleLarge,
                        assistant = assistant
                    )
                }
            }
            NewChatHeaderStyle.NONE -> {
                // No header
            }
        }

        // === CONTENT SECTION ===
        when (contentStyle) {
            NewChatContentStyle.TEMPLATES -> {
                // 2x2 grid of template cards - vertical layout with icon above text
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2
                ) {
                    TemplateCard(
                        icon = Icons.Rounded.Edit,
                        title = stringResource(R.string.new_chat_template_write),
                        onClick = { onTemplateClick(writePrompt) },
                        modifier = Modifier.weight(1f)
                    )
                    TemplateCard(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.new_chat_template_code),
                        onClick = { onTemplateClick(codePrompt) },
                        modifier = Modifier.weight(1f)
                    )
                    TemplateCard(
                        icon = Icons.Rounded.Lightbulb,
                        title = stringResource(R.string.new_chat_template_brainstorm),
                        onClick = { onTemplateClick(brainstormPrompt) },
                        modifier = Modifier.weight(1f)
                    )
                    TemplateCard(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.new_chat_template_learn),
                        onClick = { onTemplateClick(learnPrompt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            NewChatContentStyle.STATS -> {
                StatsWidgets(stats = stats)
            }
            NewChatContentStyle.ACTIONS -> {
                // ChatGPT-style action pills with colored icons
                // Material You derived colors
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val writeColor = MaterialTheme.colorScheme.inversePrimary
                val brainstormColor = MaterialTheme.colorScheme.tertiary
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // First row: Create image, Brainstorm
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onNavigateToImageGen != null) {
                            ActionPill(
                                icon = Icons.Rounded.Image,
                                text = stringResource(R.string.new_chat_action_create_image),
                                iconColor = primaryColor,
                                hasBackgroundImage = hasBackgroundImage,
                                onClick = onNavigateToImageGen
                            )
                        }
                        ActionPill(
                            icon = Icons.Rounded.Lightbulb,
                            text = stringResource(R.string.new_chat_template_brainstorm),
                            iconColor = brainstormColor,
                            hasBackgroundImage = hasBackgroundImage,
                            onClick = { onTemplateClick(brainstormPrompt) }
                        )
                    }
                    
                    // Second row: Code, Write
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionPill(
                            icon = Icons.Rounded.Code,
                            text = stringResource(R.string.new_chat_template_code),
                            iconColor = secondaryColor,
                            hasBackgroundImage = hasBackgroundImage,
                            onClick = { onTemplateClick(codePrompt) }
                        )
                        ActionPill(
                            icon = Icons.Rounded.Edit,
                            text = stringResource(R.string.new_chat_template_write),
                            iconColor = writeColor,
                            hasBackgroundImage = hasBackgroundImage,
                            onClick = { onTemplateClick(writePrompt) }
                        )
                    }
                }
            }
            NewChatContentStyle.NONE -> {
                // No content
            }
        }
    }
}

@Composable
private fun TemplateCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    
    Card(
        onClick = onClick,
        modifier = modifier.wrapContentHeight(),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Vertical layout: icon above text
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * ChatGPT-style action pill button with colored icon
 * When hasBackgroundImage is true, uses surfaceContainer color with background-colored outline
 * to match the minimal input bar appearance
 */
@Composable
private fun ActionPill(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit,
    hasBackgroundImage: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Use surfaceContainer color with background outline when there's a background image
    // to match the minimal input bar appearance
    val backgroundColor = if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (hasBackgroundImage) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatsWidgets(
    stats: NewChatStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Streak card on top (full width) - gray if no chat today
        val streakContainerColor = if (stats.hasChattedToday) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow 
            else MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val streakContentColor = if (stats.hasChattedToday) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
        
        // Streak card - full width, matching MenuPage StatCard layout
        StatCard(
            title = "Daily Streak",
            value = "${stats.dailyStreak} Days",
            icon = Icons.Rounded.LocalFireDepartment,
            containerColor = streakContainerColor,
            contentColor = streakContentColor,
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        )

        // Row of 2 stats below (Most Used Model & Time Label)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Most Used Model
            val modelName = stats.mostUsedModelName ?: "—"
            StatCard(
                title = stringResource(R.string.new_chat_stat_most_used_model),
                value = modelName,
                icon = Icons.Rounded.SmartToy,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // Time Label with appropriate icon
            val (timeLabelText, timeLabelIcon) = when (stats.timeLabel) {
                TimeLabel.EARLY_BIRD -> stringResource(R.string.time_label_early_bird) to Icons.Rounded.WbSunny
                TimeLabel.DAYTIME_CHATTER -> stringResource(R.string.time_label_daytime_chatter) to Icons.Rounded.WbSunny
                TimeLabel.NIGHT_OWL -> stringResource(R.string.time_label_night_owl) to Icons.Rounded.NightsStay
            }
            StatCard(
                title = "Chat Style",
                value = timeLabelText,
                icon = timeLabelIcon,
                containerColor = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/**
 * StatCard matching MenuPage layout exactly
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = AppShapes.CardMedium
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.2f),
                contentColor = contentColor,
                shape = AppShapes.Chip,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Auto-scale text for long values (like model names)
                AutoSizeText(
                    text = value,
                    maxLines = 2,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    ),
                    maxTextSize = MaterialTheme.typography.headlineMedium.fontSize,
                    minTextSize = MaterialTheme.typography.titleSmall.fontSize
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AutoSizeText(
    text: String,
    maxLines: Int,
    style: androidx.compose.ui.text.TextStyle,
    maxTextSize: androidx.compose.ui.unit.TextUnit,
    minTextSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    var textSize by remember { mutableStateOf(maxTextSize) }
    var readyToDraw by remember { mutableStateOf(false) }
    
    Text(
        text = text,
        style = style.copy(fontSize = textSize),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && textSize > minTextSize) {
                textSize = (textSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        }
    )
}
