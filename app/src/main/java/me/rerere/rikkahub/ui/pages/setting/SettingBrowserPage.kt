package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.browser.BrowserSecretsStore
import me.rerere.rikkahub.data.browser.BrowserTabPool
import me.rerere.rikkahub.data.datastore.AgentSecretWritePolicy
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.BrowserViewMode
import me.rerere.rikkahub.data.datastore.SetOfMarkMode
import me.rerere.rikkahub.data.datastore.mergeSettingsValue
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.LastChatDestructiveConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun SettingBrowserPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val browserTabPool = koinInject<BrowserTabPool>()
    val secretsStore = koinInject<BrowserSecretsStore>()
    val secrets by secretsStore.entriesFlow.collectAsStateWithLifecycle()

    var browserSettings by remember(settings.browserSettings) {
        mutableStateOf(settings.browserSettings)
    }

    fun updateBrowserSettings(next: BrowserSettings) {
        val before = browserSettings
        val normalized = next.normalized()
        browserSettings = normalized
        vm.updateSettings { current ->
            current.copy(
                browserSettings = mergeSettingsValue(
                    before,
                    normalized,
                    current.browserSettings,
                ).normalized(),
            )
        }
    }

    var customWidthText by remember(browserSettings.customViewportWidth) {
        mutableStateOf(browserSettings.customViewportWidth.toString())
    }
    var customHeightText by remember(browserSettings.customViewportHeight) {
        mutableStateOf(browserSettings.customViewportHeight.toString())
    }
    var customUaText by remember(browserSettings.customUserAgent) {
        mutableStateOf(browserSettings.customUserAgent)
    }
    var blockedDomainsText by remember(browserSettings.blockedDomains) {
        mutableStateOf(browserSettings.blockedDomains.joinToString(", "))
    }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_page_browser),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 1. Viewport & Identity
            item {
                SettingsGroup(title = "Viewport & Identity") {
                    SettingGroupItem(
                        title = "Default View Mode",
                        subtitle = "Initial layout and User-Agent profile for new tabs",
                        trailing = {
                            Select(
                                options = BrowserViewMode.entries,
                                selectedOption = browserSettings.defaultViewMode,
                                onOptionSelected = { mode ->
                                    updateBrowserSettings(browserSettings.copy(defaultViewMode = mode))
                                },
                                optionToString = { mode ->
                                    when (mode) {
                                        BrowserViewMode.DESKTOP -> "Desktop (1280×800)"
                                        BrowserViewMode.MOBILE -> "Mobile (412×915)"
                                        BrowserViewMode.FIT_SCREEN -> "Fit Screen"
                                        BrowserViewMode.CUSTOM -> "Custom"
                                    }
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    if (browserSettings.defaultViewMode == BrowserViewMode.CUSTOM) {
                        SettingGroupInputItem(
                            title = "Custom Viewport Size",
                            subtitle = "CSS pixels (200–4096)",
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = customWidthText,
                                    onValueChange = { customWidthText = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Width") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                Text("×")
                                OutlinedTextField(
                                    value = customHeightText,
                                    onValueChange = { customHeightText = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Height") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val w = customWidthText.toIntOrNull() ?: 1280
                                        val h = customHeightText.toIntOrNull() ?: 800
                                        updateBrowserSettings(
                                            browserSettings.copy(
                                                customViewportWidth = w,
                                                customViewportHeight = h,
                                            )
                                        )
                                        toaster.show("Viewport saved", type = ToastType.Success)
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }

                    SettingGroupInputItem(
                        title = "Custom User-Agent",
                        subtitle = "Optional override (blank uses selected view mode UA)",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = customUaText,
                                onValueChange = { customUaText = it },
                                placeholder = { Text("Mozilla/5.0 ...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = {
                                    updateBrowserSettings(browserSettings.copy(customUserAgent = customUaText.trim()))
                                    toaster.show("User-Agent updated", type = ToastType.Success)
                                },
                                shape = AppShapes.ButtonPill,
                            ) {
                                Text("Save")
                            }
                        }
                    }

                    SettingGroupItem(
                        title = "Viewport Switcher in Browser Bar",
                        subtitle = "Show Desktop / Mobile / Fit switcher at top of browser sheet",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.showViewportToggleInBar,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(showViewportToggleInBar = it))
                                },
                            )
                        },
                    )
                }
            }

            // 2. Preview & Interface
            item {
                SettingsGroup(title = "Preview & Interface") {
                    SettingGroupItem(
                        title = "Floating Mini-Preview Bar",
                        subtitle = "Show live browser step preview bar above the chat composer",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.showFloatingMiniPreviewBar,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(showFloatingMiniPreviewBar = it))
                                },
                            )
                        },
                    )

                    val snapshotOptions = listOf(0L, 1_500L, 3_000L, 5_000L)
                    SettingGroupItem(
                        title = "Live Snapshot Interval",
                        subtitle = "Refresh thumbnail while browser actions run",
                        trailing = {
                            Select(
                                options = snapshotOptions,
                                selectedOption = snapshotOptions.minByOrNull {
                                    kotlin.math.abs(it - browserSettings.liveSnapshotIntervalMs)
                                } ?: 3_000L,
                                onOptionSelected = { ms ->
                                    updateBrowserSettings(browserSettings.copy(liveSnapshotIntervalMs = ms))
                                },
                                optionToString = { ms ->
                                    when (ms) {
                                        0L -> "Off"
                                        1_500L -> "1.5s"
                                        3_000L -> "3s"
                                        else -> "5s"
                                    }
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Tablet Split-Pane Mode",
                        subtitle = "Dock browser beside chat on wide / landscape screens",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.enableTabletSplitPane,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(enableTabletSplitPane = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Agent Tap Ripple",
                        subtitle = "Flash a visual ripple on elements clicked by the agent",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.showAgentTapRipple,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(showAgentTapRipple = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Set-of-Mark Element Badges",
                        subtitle = "Number interactive elements on screenshots for easier targeting",
                        trailing = {
                            Select(
                                options = SetOfMarkMode.entries,
                                selectedOption = browserSettings.setOfMarkMode,
                                onOptionSelected = { mode ->
                                    updateBrowserSettings(browserSettings.copy(setOfMarkMode = mode))
                                },
                                optionToString = { mode ->
                                    when (mode) {
                                        SetOfMarkMode.OFF -> "Off"
                                        SetOfMarkMode.AUTO_SMALL_MODELS -> "Auto (Small Models)"
                                        SetOfMarkMode.ALWAYS -> "Always"
                                    }
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )
                }
            }

            // 3. Performance & Memory
            item {
                SettingsGroup(title = "Performance & Memory") {
                    SettingGroupItem(
                        title = "Max Open Tabs",
                        subtitle = "Keep at 1–2 on 4GB RAM devices (${browserSettings.maxTabs} tabs)",
                        trailing = {
                            Select(
                                options = listOf(1, 2, 3),
                                selectedOption = browserSettings.maxTabs,
                                onOptionSelected = { count ->
                                    updateBrowserSettings(browserSettings.copy(maxTabs = count))
                                },
                                optionToString = { "$it" },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Idle Tab Eviction Timeout",
                        subtitle = "Evict inactive background tabs after ${browserSettings.idleTabTimeoutMinutes} min",
                        trailing = {
                            Select(
                                options = listOf(5, 10, 15, 30, 60, 120),
                                selectedOption = listOf(5, 10, 15, 30, 60, 120).minByOrNull {
                                    kotlin.math.abs(it - browserSettings.idleTabTimeoutMinutes)
                                } ?: 15,
                                onOptionSelected = { mins ->
                                    updateBrowserSettings(browserSettings.copy(idleTabTimeoutMinutes = mins))
                                },
                                optionToString = { "${it}m" },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    SettingGroupInputItem(
                        title = "Screenshots Retained in Context",
                        subtitle = "Newest ${browserSettings.maxScreenshotsInContext} browser screenshots kept per LLM turn",
                    ) {
                        Slider(
                            value = browserSettings.maxScreenshotsInContext.toFloat(),
                            onValueChange = { v ->
                                updateBrowserSettings(
                                    browserSettings.copy(maxScreenshotsInContext = v.roundToInt())
                                )
                            },
                            valueRange = 2f..6f,
                            steps = 3,
                        )
                    }

                    SettingGroupInputItem(
                        title = "Live Frame Cache Cap",
                        subtitle = "In-memory thumbnail LRU cap: ${browserSettings.frameCacheCapMb} MB",
                    ) {
                        Slider(
                            value = browserSettings.frameCacheCapMb.toFloat(),
                            onValueChange = { v ->
                                updateBrowserSettings(
                                    browserSettings.copy(frameCacheCapMb = v.roundToInt())
                                )
                            },
                            valueRange = 4f..24f,
                            steps = 4,
                        )
                    }

                    SettingGroupItem(
                        title = "Screenshot Max Resolution",
                        subtitle = "Long-edge pixel cap for captured screenshots",
                        trailing = {
                            Select(
                                options = listOf(720, 1080, 1440),
                                selectedOption = browserSettings.screenshotMaxLongEdgePx,
                                onOptionSelected = { px ->
                                    updateBrowserSettings(browserSettings.copy(screenshotMaxLongEdgePx = px))
                                },
                                optionToString = { "${it}px" },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    SettingGroupInputItem(
                        title = "Screenshot JPEG Quality",
                        subtitle = "Compression quality: ${browserSettings.screenshotJpegQuality}%",
                    ) {
                        Slider(
                            value = browserSettings.screenshotJpegQuality.toFloat(),
                            onValueChange = { v ->
                                updateBrowserSettings(
                                    browserSettings.copy(screenshotJpegQuality = v.roundToInt())
                                )
                            },
                            valueRange = 60f..90f,
                            steps = 5,
                        )
                    }

                    SettingGroupItem(
                        title = "Evict WebViews on Low Memory",
                        subtitle = "Unload non-selected tabs when Android signals memory pressure",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.evictWebViewsOnLowMemory,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(evictWebViewsOnLowMemory = it))
                                },
                            )
                        },
                    )
                }
            }

            // 4. Safety & Autonomy
            item {
                SettingsGroup(title = "Safety & Autonomy") {
                    SettingGroupItem(
                        title = "Full Autonomy Mode",
                        subtitle = "Disable secret-field hand-off and risky-click prompts (ideal for local models)",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.fullAutonomyMode,
                                onCheckedChange = { enabled ->
                                    updateBrowserSettings(
                                        browserSettings.copy(
                                            fullAutonomyMode = enabled,
                                            autoHandoffOnSecretFields = !enabled,
                                            requireApprovalForRiskyClicks = !enabled,
                                        )
                                    )
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Auto-Handoff on Secret Fields",
                        subtitle = "Refuse typing passwords/OTPs and bring browser forward for user",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.autoHandoffOnSecretFields,
                                onCheckedChange = { enabled ->
                                    updateBrowserSettings(
                                        browserSettings.copy(
                                            autoHandoffOnSecretFields = enabled,
                                            fullAutonomyMode = if (enabled) false else browserSettings.fullAutonomyMode,
                                        )
                                    )
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Require Approval for Risky Clicks",
                        subtitle = "Confirm pay, send, post, and delete actions before tapping",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.requireApprovalForRiskyClicks,
                                onCheckedChange = { enabled ->
                                    updateBrowserSettings(
                                        browserSettings.copy(
                                            requireApprovalForRiskyClicks = enabled,
                                            fullAutonomyMode = if (enabled) false else browserSettings.fullAutonomyMode,
                                        )
                                    )
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Allow Agent Hand-Over Action",
                        subtitle = "Allow the model to call hand_over for logins, 2FA, or CAPTCHAs",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.allowAgentHandOverAction,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(allowAgentHandOverAction = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Hand-Over Timeout",
                        subtitle = "Wait up to ${browserSettings.handOverTimeoutMinutes} minutes for user Done tap",
                        trailing = {
                            Select(
                                options = listOf(5, 10, 15, 30, 60),
                                selectedOption = listOf(5, 10, 15, 30, 60).minByOrNull {
                                    kotlin.math.abs(it - browserSettings.handOverTimeoutMinutes)
                                } ?: 15,
                                onOptionSelected = { mins ->
                                    updateBrowserSettings(browserSettings.copy(handOverTimeoutMinutes = mins))
                                },
                                optionToString = { "${it}m" },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Redact Secrets in Tool Output",
                        subtitle = "Mask stored secret values if they appear in page text or JS output",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.redactSecretsInToolOutput,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(redactSecretsInToolOutput = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Incognito Mode",
                        subtitle = "Skip saving browsing history and session tab state to disk",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.incognitoMode,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(incognitoMode = it))
                                },
                            )
                        },
                    )

                    SettingGroupInputItem(
                        title = "Blocked Domains",
                        subtitle = "Comma-separated domains the browser tool cannot open or fetch",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = blockedDomainsText,
                                onValueChange = { blockedDomainsText = it },
                                placeholder = { Text("example.com, ads.example.org") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = {
                                    val list = blockedDomainsText.split(",")
                                        .map { it.trim().lowercase() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                    updateBrowserSettings(browserSettings.copy(blockedDomains = list))
                                    toaster.show("Blocklist saved (${list.size})", type = ToastType.Success)
                                },
                                shape = AppShapes.ButtonPill,
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // 5. Passwords & Secrets
            item {
                SettingsGroup(title = "Passwords & Secrets") {
                    SettingGroupItem(
                        title = "Browser Secrets & Saved Sessions",
                        subtitle = "${secrets.size} encrypted ${if (secrets.size == 1) "entry" else "entries"} stored",
                        icon = { Icon(Icons.Rounded.Key, null, modifier = Modifier.size(20.dp)) },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null) },
                        onClick = { navController.navigate(Screen.BrowserSecrets) },
                    )

                    SettingGroupItem(
                        title = "Sync .secrets to Linux Workspace",
                        subtitle = "Mirror encrypted secrets into active workspace .secrets (chmod 600)",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.syncSecretsToWorkspace,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(syncSecretsToWorkspace = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Agent Secret Write Policy",
                        subtitle = "Controls save_secret and save_cookies_as_secret tool calls",
                        trailing = {
                            Select(
                                options = AgentSecretWritePolicy.entries,
                                selectedOption = browserSettings.agentSecretWritePolicy,
                                onOptionSelected = { policy ->
                                    updateBrowserSettings(browserSettings.copy(agentSecretWritePolicy = policy))
                                },
                                optionToString = { policy ->
                                    when (policy) {
                                        AgentSecretWritePolicy.ASK_BEFORE_SAVING -> "Ask Before Saving"
                                        AgentSecretWritePolicy.AUTO_SAVE -> "Auto Save"
                                        AgentSecretWritePolicy.READ_ONLY -> "Read Only"
                                    }
                                },
                                modifier = Modifier.wrapContentWidth(),
                            )
                        },
                    )
                }
            }

            // 6. Prompt & Data
            item {
                SettingsGroup(title = "Prompt & Data") {
                    SettingGroupItem(
                        title = "Compact Tool Schema (PromptDiet)",
                        subtitle = "Save ~4–5k chars of schema tokens while preserving all 26 actions",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.useCompactToolSchema,
                                onCheckedChange = {
                                    updateBrowserSettings(browserSettings.copy(useCompactToolSchema = it))
                                },
                            )
                        },
                    )

                    SettingGroupItem(
                        title = "Search → Browser Escalation Hint",
                        subtitle = "Guide the model to escalate interactive/JS pages from Search to Browser",
                        trailing = {
                            HapticSwitch(
                                checked = browserSettings.enableSearchToBrowserEscalationHint,
                                onCheckedChange = {
                                    updateBrowserSettings(
                                        browserSettings.copy(enableSearchToBrowserEscalationHint = it)
                                    )
                                },
                            )
                        },
                    )

                    SettingGroupInputItem(
                        title = "Clear Cookies, Cache & History",
                        subtitle = "Wipes WebView cookies, localStorage, saved tabs, thumbnails, and history",
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                showClearDataConfirm = true
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            shape = AppShapes.ButtonPill,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear All Browser Data")
                        }
                    }
                }
            }
        }
    }

    if (showClearDataConfirm) {
        LastChatDestructiveConfirmDialog(
            title = "Clear all browser data?",
            message = "This will remove all WebView cookies, site storage, browsing history, open tabs, and cached screenshots. Encrypted entries in Browser Secrets are kept.",
            confirmLabel = "Clear Data",
            onConfirm = {
                showClearDataConfirm = false
                scope.launch {
                    browserTabPool.clearAllBrowserData()
                    toaster.show("Browser cookies, cache & history cleared", type = ToastType.Success)
                }
            },
            onDismiss = { showClearDataConfirm = false },
        )
    }
}
