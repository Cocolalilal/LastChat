package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexAccount
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexOAuthManager
import me.rerere.rikkahub.data.codex.CodexOAuthStatus
import me.rerere.rikkahub.data.codex.CodexTokenStatus
import me.rerere.rikkahub.data.codex.CodexUsageWindow
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun CodexProviderConfigure(
    provider: ProviderSetting.Codex,
    showSavingIndicator: Boolean = false,
    onEdit: (ProviderSetting.Codex) -> Unit,
) {
    val repository = koinInject<CodexAccountRepository>()
    val oauthManager = koinInject<CodexOAuthManager>()
    val accounts by repository.accounts.collectAsStateWithLifecycle()
    val oauthStatus by oauthManager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val account = accounts.singleOrNull()
    val canEnable = account != null && account.tokenStatus != CodexTokenStatus.INVALID

    LaunchedEffect(oauthStatus) {
        when (val status = oauthStatus) {
            is CodexOAuthStatus.Success -> {
                toaster.show(
                    context.getString(R.string.codex_oauth_success),
                    type = ToastType.Success,
                )
                oauthManager.consumeResult()
            }

            is CodexOAuthStatus.Error -> {
                toaster.show(status.message, type = ToastType.Error)
                oauthManager.consumeResult()
            }

            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderConfigure(
            provider = provider,
            modifier = Modifier.fillMaxWidth(),
            showSavingIndicator = showSavingIndicator,
            showProviderTypeSelector = false,
            enabledToggleEnabled = provider.enabled || canEnable,
            enabledSupportingText = if (canEnable) null else {
                stringResource(R.string.codex_sign_in_required)
            },
            onEdit = { updated -> onEdit(updated as ProviderSetting.Codex) },
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.codex_provider_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.codex_account_title),
            style = MaterialTheme.typography.titleMedium,
        )

        if (account == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.codex_no_accounts),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = oauthManager::startLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.ButtonPill,
            ) {
                Text(stringResource(R.string.codex_sign_in))
            }
        } else {
            CodexAccountCard(
                account = account,
                onRefresh = {
                    scope.launch {
                        runCatching { repository.refreshAccount(account.id) }
                            .onFailure {
                                toaster.show(
                                    it.message ?: context.getString(
                                        R.string.codex_refresh_failed
                                    ),
                                    type = ToastType.Error,
                                )
                            }
                    }
                },
                onReauthenticate = oauthManager::startLogin,
                onDelete = {
                    scope.launch {
                        repository.delete(account.id)
                        if (provider.enabled) {
                            onEdit(provider.copy(enabled = false))
                        }
                    }
                },
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.CardLargeInner12,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.codex_access_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun CodexAccountCard(
    account: CodexAccount,
    onRefresh: () -> Unit,
    onReauthenticate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.codex_remove_account_title)) },
            text = { Text(stringResource(R.string.codex_remove_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(
                        stringResource(R.string.codex_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardLargeInner12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                if (account.email.isNotBlank()) {
                    Text(
                        account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = when (account.tokenStatus) {
                    CodexTokenStatus.AVAILABLE -> stringResource(R.string.codex_token_available)
                    CodexTokenStatus.EXPIRED -> stringResource(R.string.codex_token_expired)
                    CodexTokenStatus.INVALID -> stringResource(R.string.codex_token_unavailable)
                    CodexTokenStatus.UNKNOWN -> stringResource(R.string.codex_token_not_checked)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (account.tokenStatus) {
                    CodexTokenStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
                    CodexTokenStatus.INVALID, CodexTokenStatus.EXPIRED -> MaterialTheme.colorScheme.error
                    CodexTokenStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            account.usage?.primary?.let {
                CodexUsageRow(
                    window = it,
                    fallbackName = stringResource(R.string.codex_five_hour_limit),
                )
            }
            account.usage?.secondary?.let {
                CodexUsageRow(
                    window = it,
                    fallbackName = stringResource(R.string.codex_weekly_limit),
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onReauthenticate()
                    },
                ) {
                    Text(stringResource(R.string.codex_reauthenticate))
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onRefresh()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.codex_check_status),
                    )
                }
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showDeleteConfirmation = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.codex_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexUsageRow(
    window: CodexUsageWindow,
    fallbackName: String,
) {
    val remaining = (100.0 - window.usedPercent).coerceIn(0.0, 100.0)
    val name = when (window.windowMinutes) {
        300L -> stringResource(R.string.codex_five_hour_limit)
        10_080L -> stringResource(R.string.codex_weekly_limit)
        43_200L -> stringResource(R.string.codex_monthly_limit)
        null -> fallbackName
        else -> stringResource(R.string.codex_minute_limit, window.windowMinutes)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.codex_percent_remaining, remaining.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { (remaining / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        window.resetsAt?.let { epochSeconds ->
            Text(
                text = stringResource(
                    R.string.codex_resets_at,
                    RESET_FORMAT.format(Instant.ofEpochSecond(epochSeconds)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RESET_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
