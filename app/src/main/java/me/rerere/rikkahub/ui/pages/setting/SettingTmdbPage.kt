package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.service.TmdbService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingTmdbPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tmdb = settings.tmdbSetting
    val tmdbService = koinInject<TmdbService>()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showManualTokenDialog by remember { mutableStateOf(false) }
    var manualTokenInput by remember(tmdb.userAccessToken) { mutableStateOf(tmdb.userAccessToken) }
    var manualAccountIdInput by remember(tmdb.accountId) { mutableStateOf(tmdb.accountId) }
    var manualUsernameInput by remember(tmdb.username) { mutableStateOf(tmdb.username) }

    // Direct Login Dialog State
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    fun updateTmdb(transform: (me.rerere.rikkahub.data.datastore.TmdbSetting) -> me.rerere.rikkahub.data.datastore.TmdbSetting) {
        vm.updateSettings { current ->
            current.copy(tmdbSetting = transform(current.tmdbSetting))
        }
    }

    val isConnected = tmdb.userAccessToken.isNotBlank() || tmdb.sessionId.isNotBlank()

    // Direct Login Dialog
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoggingIn) showLoginDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Login, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sign in with TMDB")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your TMDB account username and password to connect directly without manual tokens:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = loginUsername,
                        onValueChange = { loginUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        enabled = !isLoggingIn
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (loginUsername.isBlank() || loginPassword.isBlank()) {
                            toaster.show("Username and password are required")
                            return@Button
                        }
                        scope.launch {
                            isLoggingIn = true
                            haptics.perform(HapticPattern.Pop)
                            val res = tmdbService.loginWithCredentials(loginUsername, loginPassword)
                            isLoggingIn = false
                            if (res.success) {
                                haptics.perform(HapticPattern.Success)
                                updateTmdb {
                                    it.copy(
                                        username = res.username,
                                        accountId = res.accountId,
                                        sessionId = res.sessionId,
                                        avatarPath = res.avatarPath,
                                        includeAdult = res.includeAdult
                                    )
                                }
                                toaster.show("Signed in as @${res.username}")
                                showLoginDialog = false
                                loginPassword = ""
                            } else {
                                toaster.show(res.errorMessage)
                            }
                        }
                    },
                    enabled = !isLoggingIn
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign In")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLoginDialog = false },
                    enabled = !isLoggingIn
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = { showManualTokenDialog = false },
            title = { Text("Manual TMDB Token Setup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste your TMDB v4 User Access Token and Account ID directly:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = { manualTokenInput = it },
                        label = { Text("User Access Token (v4)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = manualAccountIdInput,
                        onValueChange = { manualAccountIdInput = it },
                        label = { Text("Account ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualUsernameInput,
                        onValueChange = { manualUsernameInput = it },
                        label = { Text("Username (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.perform(HapticPattern.Success)
                        updateTmdb {
                            it.copy(
                                userAccessToken = manualTokenInput.trim(),
                                accountId = manualAccountIdInput.trim(),
                                username = manualUsernameInput.trim()
                            )
                        }
                        toaster.show("TMDB credentials saved")
                        showManualTokenDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Movies & TV Shows (TMDB)",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Service Switch
            item {
                SettingsGroup(title = "SERVICE STATUS") {
                    SettingGroupItem(
                        title = "Enable Movies & TV Integration",
                        subtitle = "Allows assistant to search media, pull posters/backdrops, and track your watch progress",
                        icon = { Icon(Icons.Rounded.Movie, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            HapticSwitch(
                                checked = tmdb.enabled,
                                onCheckedChange = { enabled ->
                                    haptics.perform(HapticPattern.Pop)
                                    updateTmdb { it.copy(enabled = enabled) }
                                }
                            )
                        }
                    )
                }
            }

            // TMDB v4 Account Authorization
            item {
                SettingsGroup(title = "TMDB ACCOUNT AUTHORIZATION") {
                    if (isConnected) {
                        // Authorized State Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = AppShapes.CardLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (tmdb.username.isNotBlank()) "@${tmdb.username}" else "Connected TMDB Account",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (tmdb.accountId.isNotBlank()) "Account ID: ${tmdb.accountId}" else "Active TMDB Session",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showManualTokenDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Edit Token")
                                    }
                                    Button(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            updateTmdb {
                                                it.copy(
                                                    userAccessToken = "",
                                                    sessionId = "",
                                                    accountId = "",
                                                    username = "",
                                                    avatarPath = ""
                                                )
                                            }
                                            toaster.show("Disconnected from TMDB")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                            }
                        }
                    } else {
                        // Unauthorized State Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = AppShapes.CardLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Link TMDB Account",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Sign in directly with your TMDB username & password or paste an API token to access your watchlist and track shows.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            showLoginDialog = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Login, null, modifier = Modifier.size(16.dp))
                                        Text(modifier = Modifier.padding(start = 6.dp), text = "Sign In")
                                    }
                                    OutlinedButton(
                                        onClick = { showManualTokenDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Paste Token")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Media Display & 3D Interactive Decks
            item {
                SettingsGroup(title = "MEDIA DISPLAY & ASSETS") {
                    SettingGroupItem(
                        title = "3D Interactive Cover Flow",
                        subtitle = "Renders recommendation carousels as tactile 3D swipe decks",
                        icon = { Icon(Icons.Rounded.Visibility, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            HapticSwitch(
                                checked = tmdb.enable3DDecks,
                                onCheckedChange = { enabled ->
                                    haptics.perform(HapticPattern.Pop)
                                    updateTmdb { it.copy(enable3DDecks = enabled) }
                                }
                            )
                        }
                    )

                    SettingGroupItem(
                        title = "Poster Resolution",
                        subtitle = "Current: ${tmdb.posterResolution.uppercase()} (Default: W500)",
                        icon = { Icon(Icons.Rounded.PhotoSizeSelectActual, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            val next = when (tmdb.posterResolution) {
                                "w342" -> "w500"
                                "w500" -> "w780"
                                "w780" -> "original"
                                else -> "w342"
                            }
                            updateTmdb { it.copy(posterResolution = next) }
                        }
                    )

                    SettingGroupItem(
                        title = "Backdrop Resolution",
                        subtitle = "Current: ${tmdb.backdropResolution.uppercase()} (Default: W780)",
                        icon = { Icon(Icons.Rounded.PhotoSizeSelectActual, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            val next = when (tmdb.backdropResolution) {
                                "w300" -> "w780"
                                "w780" -> "w1280"
                                "w1280" -> "original"
                                else -> "w300"
                            }
                            updateTmdb { it.copy(backdropResolution = next) }
                        }
                    )
                }
            }

            // Content & Privacy Filters
            item {
                SettingsGroup(title = "CONTENT & PRIVACY") {
                    SettingGroupItem(
                        title = "Include 18+ Adult Content",
                        subtitle = "Allows adult films, anime, and unrated media in search and recommendation decks",
                        icon = { Icon(Icons.Rounded.Security, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            HapticSwitch(
                                checked = tmdb.includeAdult,
                                onCheckedChange = { enabled ->
                                    haptics.perform(HapticPattern.Pop)
                                    updateTmdb { it.copy(includeAdult = enabled) }
                                }
                            )
                        }
                    )

                    SettingGroupItem(
                        title = "Auto-sync Watchlist to Companion Context",
                        subtitle = "Injects active watch titles into chat so assistant naturally knows what you're watching",
                        icon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            HapticSwitch(
                                checked = tmdb.autoSyncWatchlistToContext,
                                onCheckedChange = { enabled ->
                                    haptics.perform(HapticPattern.Pop)
                                    updateTmdb { it.copy(autoSyncWatchlistToContext = enabled) }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
