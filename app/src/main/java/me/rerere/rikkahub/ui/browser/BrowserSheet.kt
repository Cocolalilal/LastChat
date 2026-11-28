package me.rerere.rikkahub.ui.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.browser.BrowserAction
import me.rerere.rikkahub.data.browser.BrowserActionInput
import me.rerere.rikkahub.data.browser.BrowserHistoryStore
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.BrowserViewMode
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.modifier.lastChatSheetContainerColor
import me.rerere.rikkahub.ui.theme.AppShapes
import java.net.URI
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSheet(
    tabPool: BrowserTabPool,
    browserSettings: BrowserSettings,
    assistantName: String = "Assistant",
    onOpenSettings: () -> Unit,
    onOpenSecrets: () -> Unit,
    onSendPageToChat: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = lastChatSheetContainerColor(),
        shape = AppShapes.BottomSheet,
        dragHandle = null,
    ) {
        BrowserPaneContent(
            tabPool = tabPool,
            browserSettings = browserSettings,
            assistantName = assistantName,
            onOpenSettings = onOpenSettings,
            onOpenSecrets = onOpenSecrets,
            onSendPageToChat = onSendPageToChat,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun BrowserPaneContent(
    tabPool: BrowserTabPool,
    browserSettings: BrowserSettings,
    assistantName: String = "Assistant",
    onOpenSettings: () -> Unit,
    onOpenSecrets: () -> Unit,
    onSendPageToChat: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs by tabPool.tabs.collectAsState()
    val selectedTabId by tabPool.selectedTabId.collectAsState()
    val isAgentBusy by tabPool.isAgentBusyFlow.collectAsState()
    val downloadEntries by tabPool.downloads.collectAsState()
    val activeDownload by tabPool.activeDownload.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = rememberPremiumHaptics()

    val selectedTab = tabs.find { it.id == selectedTabId }
    val currentURL = selectedTab?.manager?.currentURL?.collectAsState()?.value.orEmpty()
    val pageTitle = selectedTab?.manager?.pageTitle?.collectAsState()?.value.orEmpty()
    val isLoading = selectedTab?.manager?.isLoading?.collectAsState()?.value ?: false
    val canGoBack = selectedTab?.manager?.canGoBack?.collectAsState()?.value ?: false
    val canGoForward = selectedTab?.manager?.canGoForward?.collectAsState()?.value ?: false

    var urlInput by remember(currentURL) { mutableStateOf(currentURL) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }

    val accent = MaterialTheme.colorScheme.primary
    val secondaryBg = MaterialTheme.colorScheme.surfaceContainer
    val tertiaryBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val maxTabs = browserSettings.maxTabs.coerceIn(1, 3)

    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) tabPool.ensureTabForUI()
    }

    val webViewScrollGuard = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available
        }
    }

    Column(modifier = modifier) {
        // Header row: Desktop / Mobile / Fit switcher + title + actions + close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (browserSettings.showViewportToggleInBar) {
                Row(
                    modifier = Modifier
                        .background(tertiaryBg, CircleShape)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewportPillButton(
                        icon = Icons.Rounded.Computer,
                        label = "Desktop",
                        selected = browserSettings.defaultViewMode == BrowserViewMode.DESKTOP,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            scope.launch { tabPool.applyQuickViewMode(BrowserViewMode.DESKTOP) }
                        },
                    )
                    ViewportPillButton(
                        icon = Icons.Rounded.PhoneAndroid,
                        label = "Mobile",
                        selected = browserSettings.defaultViewMode == BrowserViewMode.MOBILE,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            scope.launch { tabPool.applyQuickViewMode(BrowserViewMode.MOBILE) }
                        },
                    )
                    ViewportPillButton(
                        icon = Icons.Rounded.FitScreen,
                        label = "Fit",
                        selected = browserSettings.defaultViewMode == BrowserViewMode.FIT_SCREEN,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            scope.launch { tabPool.applyQuickViewMode(BrowserViewMode.FIT_SCREEN) }
                        },
                    )
                }
            }
            Text(
                text = pageTitle.ifBlank { "Browser" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (browserSettings.incognitoMode) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = "Incognito",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            IconButton(onClick = onOpenSecrets, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Key, contentDescription = "Secrets", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Browser settings", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close browser", modifier = Modifier.size(19.dp))
            }
        }

        // Tab strip (+, up to maxTabs, History)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tertiaryBg)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { tabPool.newTabFromUI() } },
                enabled = tabs.size < maxTabs && !isAgentBusy,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New tab", modifier = Modifier.size(18.dp))
            }
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val title = tab.manager.pageTitle.collectAsState().value
                    val domain = tab.manager.currentURL.collectAsState().value.let { u ->
                        runCatching { URI(u).host }.getOrNull()
                    }
                    val displayTitle = title.ifEmpty { domain ?: "Tab ${tab.id}" }.take(20)
                    TabChip(
                        title = displayTitle,
                        isSelected = tab.id == selectedTabId,
                        showClose = !isAgentBusy,
                        accent = accent,
                        onClick = { tabPool.selectTab(tab.id) },
                        onClose = { scope.launch { tabPool.closeTabFromUI(tab.id) } },
                    )
                }
                item {
                    Text(
                        text = "${tabs.size}/$maxTabs",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            IconButton(
                onClick = { showHistory = true },
                enabled = !isAgentBusy,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.History, contentDescription = "History", modifier = Modifier.size(18.dp))
            }
        }

        // Editable URL Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(secondaryBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(tertiaryBg, RoundedCornerShape(10.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddressBarIcon(isLoading = isLoading, accent = accent)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        enabled = !isAgentBusy,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val trimmed = urlInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    val normalized = normalizeUrlInput(trimmed)
                                    val tab = selectedTab ?: tabPool.ensureTabForUI()
                                    tab.manager.loadURL(normalized)
                                    urlInput = normalized
                                }
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Enter URL or search…",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLoading) {
                    IconButton(
                        onClick = { selectedTab?.manager?.stopLoading() },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Stop", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        // WebView + Agent Browsing Takeover pill
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(webViewScrollGuard),
        ) {
            if (selectedTab != null && currentURL.isNotEmpty()) {
                BrowserWebView(
                    webView = selectedTab.manager.webView,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedTab == null) "No open tab" else "Enter a URL above to browse",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isAgentBusy) {
                AgentBrowsingOverlay(
                    assistantName = assistantName,
                    accent = accent,
                    onTakeover = {
                        haptics.perform(HapticPattern.Pop)
                        tabPool.releaseAllTabs()
                    },
                )
            }
        }

        BrowserHandOverBanner(accent = accent)

        activeDownload?.let { dl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(secondaryBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                Text(dl.filename, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dl.progress >= 0f) {
                    LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.weight(1f).height(3.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.weight(1f).height(3.dp))
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(secondaryBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { selectedTab?.manager?.goBack() },
                    enabled = canGoBack && !isAgentBusy,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { selectedTab?.manager?.goForward() },
                    enabled = canGoForward && !isAgentBusy,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                }
                if (downloadEntries.isNotEmpty()) {
                    val badgeCount = tabPool.downloadBadgeCount(downloadEntries)
                    BadgedBox(
                        badge = { if (badgeCount > 0) Badge { Text("$badgeCount") } },
                    ) {
                        IconButton(onClick = { showDownloads = true }) {
                            Icon(Icons.Rounded.Download, contentDescription = "Downloads", tint = accent)
                        }
                    }
                }
                IconButton(
                    onClick = {
                        if (isLoading) selectedTab?.manager?.stopLoading() else selectedTab?.manager?.reload()
                    },
                    enabled = !isAgentBusy,
                ) {
                    Icon(
                        if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Reload",
                        tint = accent,
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    scope.launch {
                        val url = currentURL
                        val readable = runCatching {
                            tabPool.execute(BrowserActionInput(action = BrowserAction.GET_READABLE), singleTab = true)
                        }.getOrNull()?.text.orEmpty()
                        val payload = buildString {
                            if (pageTitle.isNotBlank()) appendLine("Page: $pageTitle")
                            if (url.isNotBlank()) appendLine("URL: $url")
                            if (readable.isNotBlank()) {
                                appendLine()
                                append(readable.take(2_500))
                            }
                        }.trim()
                        if (payload.isNotBlank()) {
                            onSendPageToChat(payload)
                        }
                    }
                },
                enabled = currentURL.isNotBlank(),
                shape = AppShapes.ButtonPill,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send Page to Chat", fontSize = 12.sp)
            }
        }
    }

    if (showDownloads) {
        BrowserDownloadsSheet(tabPool = tabPool, onDismiss = { showDownloads = false })
    }
    if (showHistory) {
        BrowserHistorySheet(
            historyStore = BrowserHistoryStore.getInstance(context),
            onNavigate = { url ->
                scope.launch {
                    val tab = selectedTab ?: tabPool.newTabFromUI()
                    tab?.manager?.loadURL(url)
                    showHistory = false
                }
            },
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun ViewportPillButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun TabChip(
    title: String,
    isSelected: Boolean,
    showClose: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val borderColor = if (isSelected) accent.copy(alpha = 0.45f) else Color.Transparent
    val textColor = if (isSelected) accent else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .background(bg, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (showClose) 6.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showClose) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun AddressBarIcon(isLoading: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "addrIcon")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "angle",
    )
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isLoading) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).rotate(angle),
                color = accent,
                strokeWidth = 1.5.dp,
            )
        }
    }
}

@Composable
private fun AgentBrowsingOverlay(assistantName: String, accent: Color, onTakeover: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "breathingAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .background(Color.Black.copy(alpha = 0.78f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent.copy(alpha = breathingAlpha), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${assistantName.ifBlank { "Assistant" }} is browsing",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Takeover",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onTakeover),
            )
        }
    }
}

private fun normalizeUrlInput(input: String): String {
    val trimmed = input.trim()
    if (trimmed.contains("://")) return trimmed
    if (trimmed.contains(' ') || !trimmed.contains('.')) {
        return "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
    }
    return "https://$trimmed"
}
