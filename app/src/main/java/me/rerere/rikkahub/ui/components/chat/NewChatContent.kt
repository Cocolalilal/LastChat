package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.NewChatContentStyle
import me.rerere.rikkahub.data.datastore.NewChatHeaderStyle
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.modifier.blurredContainerColor
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewChatContent(
    assistant: Assistant,
    headerStyle: NewChatHeaderStyle,
    contentStyle: NewChatContentStyle,
    showAvatarInHeader: Boolean = true,
    hasBackgroundImage: Boolean = false,
    onTemplateClick: (String) -> Unit,
    onNavigateToImageGen: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val writePrompt = stringResource(R.string.new_chat_template_write_prompt) + " "
    val codePrompt = stringResource(R.string.new_chat_template_code_prompt) + " "
    val brainstormPrompt = stringResource(R.string.new_chat_template_brainstorm_prompt) + " "
    val learnPrompt = stringResource(R.string.new_chat_template_learn_prompt) + " "

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            modifier = Modifier.size(80.dp),
                            onClick = onAvatarClick
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
                            modifier = Modifier.size(44.dp),
                            onClick = onAvatarClick
                        )
                        Greeting(
                            style = MaterialTheme.typography.titleLarge,
                            assistant = assistant
                        )
                    }
                } else {
                    Greeting(
                        style = MaterialTheme.typography.titleLarge,
                        assistant = assistant
                    )
                }
            }

            NewChatHeaderStyle.NONE -> Unit
        }

        when (contentStyle) {
            NewChatContentStyle.TEMPLATES -> {
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

            NewChatContentStyle.ACTIONS -> {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val writeColor = MaterialTheme.colorScheme.inversePrimary
                val brainstormColor = MaterialTheme.colorScheme.tertiary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

            NewChatContentStyle.NONE -> Unit
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
        modifier = modifier
            .wrapContentHeight()
            .lastChatBlurEffect(cardColor, AppShapes.CardMedium),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = blurredContainerColor(cardColor)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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

@Composable
private fun ActionPill(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit,
    hasBackgroundImage: Boolean = false,
    modifier: Modifier = Modifier
) {
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

    val pillShape = RoundedCornerShape(24.dp)
    val pillModifier = if (hasBackgroundImage) {
        modifier.lastChatBlurEffect(backgroundColor, pillShape)
    } else {
        modifier
    }
    val containerColor = if (hasBackgroundImage) {
        blurredContainerColor(backgroundColor)
    } else {
        backgroundColor
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            onClick = onClick,
            modifier = pillModifier,
            shape = pillShape,
            color = containerColor,
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
}
