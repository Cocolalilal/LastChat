package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.settings.LastChatAboutContent
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.UpdateChecker
import org.koin.compose.koinInject

@Composable
fun SettingAboutPage() {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val updateChecker = koinInject<UpdateChecker>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_page_about)) },
                navigationIcon = { BackButton() },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LastChatAboutContent(
                appName = stringResource(R.string.app_name),
                versionName = BuildConfig.VERSION_NAME,
                darkTheme = LocalDarkMode.current,
                platformTitle = "Android Version",
                platformSubtitle = "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                platformIcon = Icons.Rounded.Android,
                deviceSubtitle = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                deviceIcon = Icons.Rounded.PhoneAndroid,
                architectureSubtitle = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
                architectureIcon = Icons.Rounded.Memory,
                onSourceCode = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Cocolalilal/LastChat"),
                        ),
                    )
                },
                onVersionLongPress = {
                    updateChecker.forceUpdateCheck()
                    toaster.show(
                        message = "Update banner forced for testing.",
                        type = ToastType.Info,
                    )
                },
                onHaptic = { haptics.perform(HapticPattern.Pop) },
            )
        }
    }
}
