package me.rerere.rikkahub.ui.pages.setting

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.WebServerService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.rikkahub.web.WebServerPhase
import me.rerere.rikkahub.web.WebServerState
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class PendingWebServerStart(
    val port: Int,
    val password: String,
)

@Composable
fun SettingWebPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webServerManager: WebServerManager = koinInject()
    val serverState by webServerManager.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val serverLocked = serverState.phase == WebServerPhase.Starting ||
        serverState.phase == WebServerPhase.Running ||
        serverState.phase == WebServerPhase.Stopping
    val canStopServer = serverState.phase == WebServerPhase.Running ||
        serverState.phase == WebServerPhase.Stopping
    val localDeviceUrl = "http://localhost:${serverState.port}"
    val lanUrl = serverState.address?.let { "http://$it:${serverState.port}" }
    val mdnsUrl = serverState.hostname?.let { "http://$it:${serverState.port}" }
    val batteryOptimizationIgnored = context.isIgnoringBatteryOptimization()
    val shouldShowBackgroundWarning = settings.webServerEnabled && !batteryOptimizationIgnored

    var portText by remember(settings.webServerPort, serverLocked) {
        mutableStateOf(settings.webServerPort.toString())
    }
    var passwordText by remember(settings.webServerAccessPassword, serverLocked) {
        mutableStateOf(settings.webServerAccessPassword)
    }
    var portPending by remember { mutableStateOf(false) }
    var passwordPending by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<PendingWebServerStart?>(null) }
    var showBackgroundSetupDialog by remember { mutableStateOf(false) }

    fun persistServerPreferencesAndStart(request: PendingWebServerStart) {
        vm.updateSettings(
            settings.copy(
                webServerPort = request.port,
                webServerAccessPassword = request.password,
                webServerJwtEnabled = settings.webServerJwtEnabled && request.password.isNotBlank(),
                webServerEnabled = true,
                webServerBackgroundSetupShown = true,
            )
        ) {
            WebServerService.start(context, request.port)
            pendingStart = null
        }
    }

    fun launchBatteryOptimizationRequest(
        launcher: ActivityResultLauncher<Intent>,
        request: PendingWebServerStart,
    ) {
        if (context.isIgnoringBatteryOptimization()) {
            persistServerPreferencesAndStart(request)
            return
        }

        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        val launched = runCatching {
            launcher.launch(intent)
        }.isSuccess

        if (!launched) {
            toaster.show(
                message = context.getString(R.string.setting_page_web_server_background_warning_desc),
                type = ToastType.Warning,
            )
            persistServerPreferencesAndStart(request)
        }
    }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val request = pendingStart ?: return@rememberLauncherForActivityResult
        if (!context.isIgnoringBatteryOptimization()) {
            toaster.show(
                message = context.getString(R.string.setting_page_web_server_background_warning_desc),
                type = ToastType.Warning,
            )
        }
        persistServerPreferencesAndStart(request)
    }

    val batteryOptimizationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!context.isIgnoringBatteryOptimization()) {
            toaster.show(
                message = context.getString(R.string.setting_page_web_server_background_warning_desc),
                type = ToastType.Warning,
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingStart ?: return@rememberLauncherForActivityResult
        if (!granted) {
            toaster.show(
                message = context.getString(R.string.setting_page_web_server_notification_permission_warning),
                type = ToastType.Warning,
            )
        }
        launchBatteryOptimizationRequest(batteryOptimizationLauncher, request)
    }

    fun startServerWithSetup(request: PendingWebServerStart) {
        if (!settings.webServerBackgroundSetupShown) {
            pendingStart = request
            showBackgroundSetupDialog = true
            return
        }
        persistServerPreferencesAndStart(request)
    }

    fun requestBackgroundSetupAndStart(request: PendingWebServerStart) {
        pendingStart = request
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchBatteryOptimizationRequest(batteryOptimizationLauncher, request)
        }
    }

    fun requestBatteryOptimizationExemption() {
        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching {
            batteryOptimizationSettingsLauncher.launch(intent)
        }.onFailure {
            toaster.show(
                message = context.getString(R.string.setting_page_web_server_background_warning_desc),
                type = ToastType.Warning,
            )
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_page_web_server),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                val settingsSurface = if (LocalDarkMode.current) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Card(
                    shape = AppShapes.CardLarge,
                    colors = CardDefaults.cardColors(containerColor = settingsSurface),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                        .padding(bottom = 12.dp)
                        .clip(AppShapes.CardLarge)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to MaterialTheme.colorScheme.primaryContainer,
                                        0.35f to MaterialTheme.colorScheme.primaryContainer,
                                        1.0f to settingsSurface,
                                    )
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_hero_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(serverState.statusTextResId()),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_hero_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            WebActionButtons(
                                showOpenButton = serverState.phase == WebServerPhase.Running,
                                canStopServer = canStopServer,
                                serverLoading = serverState.isLoading,
                                onPrimaryClick = {
                                    if (canStopServer) {
                                        WebServerService.stop(context)
                                    } else {
                                        val parsedPort = portText.toIntOrNull()?.coerceIn(1024, 65535)
                                            ?: settings.webServerPort
                                        startServerWithSetup(
                                            PendingWebServerStart(
                                                port = parsedPort,
                                                password = passwordText,
                                            )
                                        )
                                    }
                                },
                                onOpenClick = {
                                    context.openUrl(localDeviceUrl)
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_web_server_access)
                ) {
                    SettingGroupInputItem(
                        title = stringResource(R.string.setting_page_web_server_port),
                        subtitle = stringResource(R.string.setting_page_web_server_port_desc),
                        icon = { Icon(Icons.Rounded.Public, null) },
                        trailing = { AutoSaveIndicator(visible = portPending) },
                    ) {
                        DebouncedTextField(
                            value = portText,
                            onValueChange = { value ->
                                val digits = value.filter(Char::isDigit).take(5)
                                portText = digits
                                val port = digits.toIntOrNull()
                                if (port != null && port in 1024..65535 && !serverLocked) {
                                    vm.updateSettings(settings.copy(webServerPort = port))
                                }
                            },
                            stateKey = "web_server_port_${serverLocked}",
                            enabled = !serverLocked,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = portText.toIntOrNull()?.let { it !in 1024..65535 } == true,
                            onPendingChange = { portPending = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp),
                        )
                    }

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_web_server_protect),
                        subtitle = stringResource(R.string.setting_page_web_server_protect_desc),
                        icon = { Icon(Icons.Rounded.Lock, null) },
                        trailing = {
                            HapticSwitch(
                                checked = settings.webServerJwtEnabled,
                                enabled = !serverLocked && (settings.webServerJwtEnabled || passwordText.isNotBlank()),
                                onCheckedChange = { enabled ->
                                    vm.updateSettings(
                                        settings.copy(
                                            webServerJwtEnabled = enabled,
                                            webServerAccessPassword = passwordText,
                                        )
                                    )
                                }
                            )
                        }
                    )

                    SettingGroupInputItem(
                        title = stringResource(R.string.setting_page_web_server_password),
                        icon = { Icon(Icons.Rounded.Lock, null) },
                        trailing = { AutoSaveIndicator(visible = passwordPending) },
                    ) {
                        DebouncedTextField(
                            value = passwordText,
                            onValueChange = { value ->
                                passwordText = value
                                if (!serverLocked) {
                                    vm.updateSettings(
                                        settings.copy(
                                            webServerAccessPassword = value,
                                            webServerJwtEnabled = settings.webServerJwtEnabled && value.isNotBlank(),
                                        )
                                    )
                                }
                            },
                            stateKey = "web_server_password_${serverLocked}",
                            enabled = !serverLocked,
                            label = stringResource(R.string.setting_page_web_server_password),
                            isSecure = true,
                            onPendingChange = { passwordPending = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp),
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_web_server_addresses)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_web_server_lan_address),
                        subtitle = lanUrl ?: stringResource(R.string.setting_page_web_server_waiting_for_network),
                        icon = { Icon(Icons.Rounded.Public, null) },
                        trailing = {
                            if (lanUrl != null) {
                                CopyAddressButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        context.writeClipboardText(lanUrl)
                                    }
                                )
                            }
                        }
                    )

                    if (mdnsUrl != null) {
                        SettingGroupItem(
                            title = stringResource(R.string.setting_page_web_server_mdns_address),
                            subtitle = mdnsUrl,
                            icon = { Icon(Icons.Rounded.Language, null) },
                            trailing = {
                                CopyAddressButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        context.writeClipboardText(mdnsUrl)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (shouldShowBackgroundWarning) {
                item {
                    Card(
                        shape = AppShapes.CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_background_warning_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = stringResource(R.string.setting_page_web_server_background_warning_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.92f)
                            )
                            TextButton(
                                onClick = {
                                    requestBatteryOptimizationExemption()
                                }
                            ) {
                                Text(stringResource(R.string.setting_page_web_server_background_warning_action))
                            }
                        }
                    }
                }
            }

            if (serverState.error != null) {
                item {
                    Card(
                        shape = AppShapes.CardLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setting_page_web_server_error),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = serverState.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackgroundSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showBackgroundSetupDialog = false
                pendingStart = null
            },
            title = {
                Text(stringResource(R.string.setting_page_web_server_background_setup_title))
            },
            text = {
                Text(stringResource(R.string.setting_page_web_server_background_setup_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundSetupDialog = false
                        pendingStart?.let(::requestBackgroundSetupAndStart)
                    }
                ) {
                    Text(stringResource(R.string.setting_page_web_server_background_setup_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackgroundSetupDialog = false
                        pendingStart = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun WebActionButtons(
    showOpenButton: Boolean,
    canStopServer: Boolean,
    serverLoading: Boolean,
    onPrimaryClick: () -> Unit,
    onOpenClick: () -> Unit,
) {
    val spacing = 10.dp

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val openWidth = ((maxWidth - spacing) / 2f).coerceAtLeast(0.dp)
        val primaryTargetWidth = if (showOpenButton) {
            openWidth
        } else {
            maxWidth
        }
        val primaryWidth by animateDpAsState(
            targetValue = primaryTargetWidth,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "web_primary_width"
        )
        val openAlpha by animateFloatAsState(
            targetValue = if (showOpenButton) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
            label = "web_open_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            WebActionButton(
                label = stringResource(R.string.setting_page_web_server_open),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                enabled = showOpenButton,
                onClick = onOpenClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(openWidth)
                    .graphicsLayer {
                        alpha = openAlpha
                    }
            )

            WebActionButton(
                label = if (canStopServer) {
                    stringResource(R.string.setting_page_web_server_stop)
                } else {
                    stringResource(R.string.setting_page_web_server_start)
                },
                icon = if (canStopServer) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                enabled = !serverLoading,
                loading = serverLoading,
                hapticPattern = if (canStopServer) HapticPattern.Thud else HapticPattern.Pop,
                onClick = onPrimaryClick,
                containerColor = if (canStopServer) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (canStopServer) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(primaryWidth)
            )
        }
    }
}

@Composable
private fun WebActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    loading: Boolean = false,
    hapticPattern: HapticPattern = HapticPattern.Pop,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "web_action_scale"
    )

    Surface(
        onClick = {
            haptics.perform(hapticPattern)
            onClick()
        },
        enabled = enabled,
        color = containerColor,
        contentColor = contentColor,
        shape = AppShapes.ButtonPill,
        interactionSource = interactionSource,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                LoadingIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                )
                Box(modifier = Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.width(10.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CopyAddressButton(
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = AppShapes.ButtonPill,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun WebServerState.statusTextResId(): Int {
    return when (phase) {
        WebServerPhase.Idle -> R.string.setting_page_web_server_status_idle
        WebServerPhase.Starting -> R.string.setting_page_web_server_status_starting
        WebServerPhase.Running -> R.string.setting_page_web_server_status_running
        WebServerPhase.Stopping -> R.string.setting_page_web_server_status_stopping
        WebServerPhase.Error -> R.string.setting_page_web_server_status_error
    }
}

private fun Context.isIgnoringBatteryOptimization(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return true
    }
    val powerManager = getSystemService(PowerManager::class.java) ?: return true
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}
