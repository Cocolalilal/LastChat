package me.rerere.rikkahub.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.browser.BrowserHandOver
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatBlurEffect
import me.rerere.rikkahub.ui.modifier.lastChatFloatingSurfaceColor
import me.rerere.rikkahub.ui.modifier.lastChatSoftEdgeBorder
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
fun BrowserHandOverCard(
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending by BrowserHandOver.pending.collectAsState()
    val haptics = rememberPremiumHaptics()

    AnimatedVisibility(
        visible = pending != null,
        enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        val p = pending ?: return@AnimatedVisibility
        val surfaceColor = lastChatFloatingSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)

        Surface(
            shape = AppShapes.CardMedium,
            color = surfaceColor,
            border = lastChatSoftEdgeBorder(surfaceColor),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .lastChatBlurEffect(surfaceColor, AppShapes.CardMedium),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Your turn in the browser",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (p.url.isNotBlank()) {
                            Text(
                                p.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (p.reason.isNotBlank()) {
                        "The page needs you to finish: ${p.reason}"
                    } else {
                        "The page needs your input (sign-in, code, CAPTCHA, or payment). Open the browser and tap Done when ready."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                val openInteraction = remember { MutableInteractionSource() }
                val openPressed by openInteraction.collectIsPressedAsState()
                val openScale by animateFloatAsState(
                    targetValue = if (openPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 350f),
                    label = "handover_open_scale",
                )
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onOpenBrowser(p.url)
                    },
                    interactionSource = openInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .graphicsLayer { scaleX = openScale; scaleY = openScale },
                    shape = AppShapes.ButtonPill,
                ) {
                    Text("Open Browser", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        BrowserHandOver.finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = AppShapes.ButtonPill,
                ) {
                    Text("Done", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun BrowserHandOverBanner(accent: Color) {
    val pending by BrowserHandOver.pending.collectAsState()
    val p = pending ?: return
    val haptics = rememberPremiumHaptics()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (p.reason.isNotBlank()) "Your turn: ${p.reason}" else "Your turn in the browser — tap Done when finished",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = {
                haptics.perform(HapticPattern.Success)
                BrowserHandOver.finish()
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text("Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
