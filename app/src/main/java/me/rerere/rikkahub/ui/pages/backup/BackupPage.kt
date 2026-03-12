package me.rerere.rikkahub.ui.pages.backup

import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.PermissionChecker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.ui.components.nav.AppCompactTopBar
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppAlertDialog
import me.rerere.rikkahub.ui.components.ui.AppFloatingActionButton
import me.rerere.rikkahub.ui.components.ui.AppFloatingActionColumn
import me.rerere.rikkahub.ui.components.ui.AppFloatingControlsOverlay
import me.rerere.rikkahub.ui.components.ui.AppFloatingTabBar
import me.rerere.rikkahub.ui.components.ui.AppFloatingTabButton
import me.rerere.rikkahub.ui.components.ui.AppFloatingOverlayContentBottomPadding
import me.rerere.rikkahub.ui.components.ui.AppModalSheet
import me.rerere.rikkahub.ui.components.ui.AppOutlinedField
import me.rerere.rikkahub.ui.components.ui.AppPickerRow
import me.rerere.rikkahub.ui.components.ui.AppPickerRowStyle
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupInputItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroupCustomItem
import me.rerere.rikkahub.ui.theme.groupedItemShape
import me.rerere.rikkahub.ui.theme.placedSurfaceColor
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onLoading
import me.rerere.rikkahub.utils.onSuccess
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

@Composable
fun BackupPage(vm: BackupVM = koinViewModel()) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var isBackingUp by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppCompactTopBar(
                title = {
                    Text(stringResource(R.string.backup_page_title))
                },
                navigationIcon = {
                    BackButton()
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding
            ) { page ->
                when (page) {
                    0 -> {
                        WebDavPage(
                            vm = vm,
                            contentPadding = PaddingValues(bottom = AppFloatingOverlayContentBottomPadding)
                        )
                    }

                    1 -> {
                        ImportExportPage(
                            vm = vm,
                            contentPadding = PaddingValues(bottom = AppFloatingOverlayContentBottomPadding)
                        )
                    }
                }
            }

            AppFloatingControlsOverlay {
                AppFloatingTabBar(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    AppFloatingTabButton(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = Icons.Rounded.CloudSync,
                        contentDescription = stringResource(R.string.backup_page_webdav_backup)
                    )
                    AppFloatingTabButton(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = Icons.Rounded.Folder,
                        contentDescription = stringResource(R.string.backup_page_import_export)
                    )
                }

                if (pagerState.currentPage == 0) {
                    AppFloatingActionColumn(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        AppFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    isBackingUp = true
                                    runCatching {
                                        vm.backup()
                                        vm.loadBackupFileItems()
                                        toaster.show(
                                            context.getString(R.string.backup_page_backup_success),
                                            type = ToastType.Success
                                        )
                                    }.onFailure {
                                        it.printStackTrace()
                                        toaster.show(
                                            it.message ?: context.getString(R.string.backup_page_unknown_error),
                                            type = ToastType.Error
                                        )
                                    }
                                    isBackingUp = false
                                }
                            }
                        ) {
                            if (isBackingUp) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavPage(
    vm: BackupVM,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<me.rerere.rikkahub.data.sync.WebdavSync.RestoreResult?>(null) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    
    // Permission handling after restore
    var pendingFeatureAccess by remember {
        mutableStateOf(PermissionChecker.MissingFeatureAccess())
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        scope.launch {
            val missing = PermissionChecker.getMissingFeatureAccess(context, vm.getAssistantsSnapshot())
            if (missing.specialAccesses.isNotEmpty()) {
                pendingFeatureAccess = PermissionChecker.MissingFeatureAccess(
                    specialAccesses = missing.specialAccesses
                )
                showPermissionDialog = true
            } else {
                showRestartDialog = true
            }
        }
    }

    val specialAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showRestartDialog = true
    }

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings(settings.copy(webDavConfig = newConfig))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(vertical = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroup(title = "Connection") {
            SettingGroupInputItem(
                title = stringResource(R.string.backup_page_webdav_server_address),
            ) {
                AppOutlinedField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.url,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                    singleLine = true,
                )
            }
            SettingGroupInputItem(
                title = stringResource(R.string.backup_page_username),
            ) {
                AppOutlinedField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.username,
                    onValueChange = {
                        updateWebDavConfig(
                            webDavConfig.copy(
                                username = it.trim()
                            )
                        )
                    },
                    singleLine = true,
                )
            }
            SettingGroupInputItem(
                title = stringResource(R.string.backup_page_password),
            ) {
                var passwordVisible by remember { mutableStateOf(false) }
                AppOutlinedField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.password,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Rounded.VisibilityOff
                        else
                            Icons.Rounded.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, null)
                        }
                    },
                    singleLine = true,
                )
            }
            SettingGroupInputItem(
                title = stringResource(R.string.backup_page_path),
            ) {
                AppOutlinedField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.path,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                    singleLine = true,
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.backup_page_backup_items)) {
            SettingsGroupCustomItem { position ->
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupedItemShape(position),
                    color = placedSurfaceColor(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_page_backup_items),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MultiChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WebDavConfig.BackupItem.entries.forEachIndexed { index, item ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = WebDavConfig.BackupItem.entries.size
                                    ),
                                    onCheckedChange = {
                                        val newItems = if (it) {
                                            webDavConfig.items + item
                                        } else {
                                            webDavConfig.items - item
                                        }
                                        updateWebDavConfig(webDavConfig.copy(items = newItems))
                                    },
                                    checked = item in webDavConfig.items
                                ) {
                                    Text(
                                        when (item) {
                                            WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                                            WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            vm.testWebDav()
                            toaster.show(
                                context.getString(R.string.backup_page_connection_success),
                                type = ToastType.Success
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toaster.show(
                                context.getString(
                                    R.string.backup_page_connection_failed,
                                    e.message ?: ""
                                ), type = ToastType.Error
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.backup_page_test_connection))
            }
            OutlinedButton(
                onClick = {
                    showBackupFiles = true
                }
            ) {
                Text(stringResource(R.string.backup_page_restore))
            }
        }
    }

    if (showBackupFiles) {
        AppModalSheet(
            onDismissRequest = {
                showBackupFiles = false
            },
            title = stringResource(R.string.backup_page_webdav_backup_files),
            showCloseButton = true,
            maxHeightFraction = 0.8f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val backupItems by vm.webDavBackupItems.collectAsStateWithLifecycle()
                backupItems.onSuccess {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(it) { item ->
                            BackupItemCard(
                                item = item,
                                isRestoring = restoringItemId == item.displayName,
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            vm.deleteWebDavBackupFile(item)
                                            toaster.show(
                                                context.getString(R.string.backup_page_delete_success),
                                                type = ToastType.Success
                                            )
                                            vm.loadBackupFileItems()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                context.getString(
                                                    R.string.backup_page_delete_failed,
                                                    err.message ?: ""
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                },
                                onRestore = { item ->
                                    scope.launch {
                                        restoringItemId = item.displayName
                                        runCatching {
                                            val result = vm.restore(item = item)
                                            restoreResult = result
                                            toaster.show(
                                                context.getString(R.string.backup_page_restore_success),
                                                type = ToastType.Success
                                            )
                                            showBackupFiles = false
                                            
                                            val missing = PermissionChecker.getMissingFeatureAccess(
                                                context,
                                                vm.getAssistantsSnapshot()
                                            )
                                            if (!missing.isEmpty) {
                                                pendingFeatureAccess = missing
                                                showPermissionDialog = true
                                            } else {
                                                showRestartDialog = true
                                            }
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                context.getString(
                                                    R.string.backup_page_restore_failed,
                                                    err.message ?: ""
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                        restoringItemId = null
                                    }
                                },
                            )
                        }
                    }
                }.onError {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.backup_page_loading_failed, it.message ?: ""),
                            color = Color.Red
                        )
                    }
                }.onLoading {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }
        }
    }

    // Permission explanation dialog
    if (showPermissionDialog) {
        AppAlertDialog(
            onDismissRequest = {
                // User dismissed - proceed without permissions
                showPermissionDialog = false
                showRestartDialog = true
            },
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your restored backup includes notification features that need additional access:")
                    PermissionChecker.getFeatureAccessDescriptions(pendingFeatureAccess).forEach { description ->
                        val desc = description
                        Text("- $desc", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Grant or enable them so notifications and scheduled follow-ups work properly.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        when {
                            pendingFeatureAccess.runtimePermissions.isNotEmpty() -> {
                                permissionLauncher.launch(pendingFeatureAccess.runtimePermissions.toTypedArray())
                            }
                            pendingFeatureAccess.specialAccesses.isNotEmpty() -> {
                                val nextAccess = pendingFeatureAccess.specialAccesses.firstOrNull()
                                    ?: return@Button
                                specialAccessLauncher.launch(
                                    PermissionChecker.createSpecialAccessIntent(nextAccess)
                                )
                            }
                            else -> {
                                showRestartDialog = true
                            }
                        }
                    }
                ) {
                    Text(
                        if (pendingFeatureAccess.runtimePermissions.isNotEmpty()) {
                            "Grant Permissions"
                        } else {
                            "Open Settings"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        showRestartDialog = true
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }
    
    if (showRestartDialog) {
        val result = restoreResult // Capture immutable for checking
        BackupDialog(
             result = result,
             onConfirm = {
                 vm.restartApp(context)
             }
        )
    }
}

@Composable
private fun BackupItemCard(
    item: WebDavBackupItem,
    isRestoring: Boolean = false,
    onDelete: (WebDavBackupItem) -> Unit = {},
    onRestore: (WebDavBackupItem) -> Unit = {},
) {
    Card(
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = me.rerere.rikkahub.ui.theme.placedSurfaceColor()
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.lastModified.toLocalDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = item.size.fileSizeToString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    onDelete(item)
                },
                enabled = !isRestoring
            ) {
                Text(stringResource(R.string.backup_page_delete))
            }
            Button(
                onClick = {
                    onRestore(item)
                },
                enabled = !isRestoring
            ) {
                if (isRestoring) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRestoring) stringResource(R.string.backup_page_restoring) else stringResource(R.string.backup_page_restore_now))
            }
        }
    }
}

@Composable
private fun ImportExportPage(
    vm: BackupVM,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    var restoreResult by remember { mutableStateOf<me.rerere.rikkahub.data.sync.WebdavSync.RestoreResult?>(null) }
    
    // Permission handling after restore
    var pendingFeatureAccess by remember {
        mutableStateOf(PermissionChecker.MissingFeatureAccess())
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        scope.launch {
            val missing = PermissionChecker.getMissingFeatureAccess(context, vm.getAssistantsSnapshot())
            if (missing.specialAccesses.isNotEmpty()) {
                pendingFeatureAccess = PermissionChecker.MissingFeatureAccess(
                    specialAccesses = missing.specialAccesses
                )
                showPermissionDialog = true
            } else {
                showRestartDialog = true
            }
        }
    }

    val specialAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showRestartDialog = true
    }

    // å¯¼å…¥ç±»åž‹ï¼šlocal ä¸ºæœ¬åœ°å¤‡ä»½ï¼Œchatbox ä¸º Chatbox å¯¼å…¥
    var importType by remember { mutableStateOf("local") }

    // åˆ›å»ºæ–‡ä»¶ä¿å­˜çš„launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            scope.launch {
                isExporting = true
                runCatching {
                    // å¯¼å‡ºæ–‡ä»¶
                    val exportFile = vm.exportToFile()

                    // å¤åˆ¶åˆ°ç”¨æˆ·é€‰æ‹©çš„ä½ç½®
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                            FileInputStream(exportFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }

                    // æ¸…ç†ä¸´æ—¶æ–‡ä»¶
                    withContext(Dispatchers.IO) {
                        exportFile.delete()
                    }

                    toaster.show(
                        context.getString(R.string.backup_page_backup_success),
                        type = ToastType.Success
                    )
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                }
                isExporting = false
            }
        }
    }

    // åˆ›å»ºæ–‡ä»¶é€‰æ‹©çš„launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            scope.launch {
                isRestoring = true
                runCatching {
                    when (importType) {
                        "local" -> {
                            // æœ¬åœ°å¤‡ä»½å¯¼å…¥ï¼šå¤„ç†zipæ–‡ä»¶
                            val tempFile =
                                File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")

                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                    FileOutputStream(tempFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }

                            // ä»Žä¸´æ—¶æ–‡ä»¶æ¢å¤
                            val result = vm.restoreFromLocalFile(tempFile)
                            restoreResult = result

                            // æ¸…ç†ä¸´æ—¶æ–‡ä»¶
                            withContext(Dispatchers.IO) {
                                tempFile.delete()
                            }
                        }
                        "chatbox" -> {
                            val tempFile =
                                File(context.cacheDir, "temp_chatbox_${System.currentTimeMillis()}.json")

                            try {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                        FileOutputStream(tempFile).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                                vm.restoreFromChatBox(tempFile)
                            } finally {
                                withContext(Dispatchers.IO) {
                                    tempFile.delete()
                                }
                            }
                        }
                        "cherry" -> {
                            val tempFile =
                                File(context.cacheDir, "temp_cherry_${System.currentTimeMillis()}.zip")

                            try {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                        FileOutputStream(tempFile).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                                vm.restoreFromCherryStudio(tempFile)
                            } finally {
                                withContext(Dispatchers.IO) {
                                    tempFile.delete()
                                }
                            }
                        }
                    }

                    toaster.show(
                        context.getString(R.string.backup_page_restore_success),
                        type = ToastType.Success
                    )

                    if (importType == "local") {
                        val missing = PermissionChecker.getMissingFeatureAccess(
                            context,
                            vm.getAssistantsSnapshot()
                        )
                        if (!missing.isEmpty) {
                            pendingFeatureAccess = missing
                            showPermissionDialog = true
                        } else {
                            showRestartDialog = true
                        }
                    }
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                }
                isRestoring = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = contentPadding + PaddingValues(16.dp)
    ) {
        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_local_backup_export))
            }
        }

        item {
            BackupActionItem(
                position = ItemPosition.FIRST,
                title = stringResource(R.string.backup_page_local_backup_export),
                subtitle = if (isExporting) {
                    stringResource(R.string.backup_page_exporting)
                } else {
                    stringResource(R.string.backup_page_export_desc)
                },
                leading = {
                    if (isExporting) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.FileUpload, null)
                    }
                },
                onClick = {
                    if (!isExporting) {
                        val timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        createDocumentLauncher.launch("LastChat_backup_$timestamp.zip")
                    }
                }
            )
        }

        item {
            BackupActionItem(
                position = ItemPosition.LAST,
                title = stringResource(R.string.backup_page_local_backup_import),
                subtitle = if (isRestoring && importType == "local") {
                    stringResource(R.string.backup_page_importing)
                } else {
                    stringResource(R.string.backup_page_import_desc)
                },
                leading = {
                    if (isRestoring && importType == "local") {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.SystemUpdateAlt, null)
                    }
                },
                onClick = {
                    if (!isRestoring) {
                        importType = "local"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                }
            )
        }

        stickyHeader {
            StickyHeader {
                Text(stringResource(R.string.backup_page_import_from_other_app))
            }
        }

        item {
            BackupActionItem(
                position = ItemPosition.FIRST,
                title = stringResource(R.string.backup_page_import_from_chatbox),
                subtitle = stringResource(R.string.backup_page_import_chatbox_desc),
                leading = {
                    if (isRestoring && importType == "chatbox") {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.SystemUpdateAlt, null)
                    }
                },
                onClick = {
                    if (!isRestoring) {
                        importType = "chatbox"
                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                }
            )
        }

        item {
            BackupActionItem(
                position = ItemPosition.LAST,
                title = stringResource(R.string.backup_page_import_from_cherry_studio),
                subtitle = stringResource(R.string.backup_page_import_cherry_studio_desc),
                leading = {
                    if (isRestoring && importType == "cherry") {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.Folder, null)
                    }
                },
                onClick = {
                    if (!isRestoring) {
                        importType = "cherry"
                        openDocumentLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                        )
                    }
                }
            )
        }
    }

    // Permission explanation dialog
    if (showPermissionDialog) {
        AppAlertDialog(
            onDismissRequest = {
                // User dismissed - proceed without permissions
                showPermissionDialog = false
                showRestartDialog = true
            },
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your restored backup includes notification features that need additional access:")
                    PermissionChecker.getFeatureAccessDescriptions(pendingFeatureAccess).forEach { description ->
                        val desc = description
                        Text("- $desc", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Grant or enable them so notifications and scheduled follow-ups work properly.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        when {
                            pendingFeatureAccess.runtimePermissions.isNotEmpty() -> {
                                permissionLauncher.launch(pendingFeatureAccess.runtimePermissions.toTypedArray())
                            }
                            pendingFeatureAccess.specialAccesses.isNotEmpty() -> {
                                val nextAccess = pendingFeatureAccess.specialAccesses.firstOrNull()
                                    ?: return@Button
                                specialAccessLauncher.launch(
                                    PermissionChecker.createSpecialAccessIntent(nextAccess)
                                )
                            }
                            else -> {
                                showRestartDialog = true
                            }
                        }
                    }
                ) {
                    Text(
                        if (pendingFeatureAccess.runtimePermissions.isNotEmpty()) {
                            "Grant Permissions"
                        } else {
                            "Open Settings"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        showRestartDialog = true
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }
    
    // é‡å¯å¯¹è¯æ¡†
    if (showRestartDialog) {
        val result = restoreResult // Capture immutable for checking
        BackupDialog(
             result = result,
             onConfirm = {
                 vm.restartApp(context)
             }
        )
    }
}

@Composable
private fun BackupActionItem(
    position: ItemPosition,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    AppPickerRow(
        icon = leading,
        title = title,
        subtitle = subtitle,
        position = position,
        style = AppPickerRowStyle.PlacedSurface,
        onClick = onClick,
    )
}

@Composable
private fun BackupDialog(
    result: me.rerere.rikkahub.data.sync.WebdavSync.RestoreResult?,
    onConfirm: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = {}, // Disallow dismissing by clicking outside
        title = { Text(stringResource(R.string.backup_page_restart_app)) },
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_page_restart_desc))
                
                result?.let {
                    if (it.sanitization.skippedRows > 0 || it.settingsCleanup.totalIssuesFixed > 0 || it.settingsCleanup.unsupportedZipEntriesBytes > 0) {
                        Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Restore Report:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (it.sanitization.skippedRows > 0) {
                                    Text("- Removed ${it.sanitization.skippedRows} corrupt/invalid items")
                                }
                                if (it.settingsCleanup.totalIssuesFixed > 0) {
                                    Text("- Fixed ${it.settingsCleanup.totalIssuesFixed} setting issues")
                                }
                                if (it.settingsCleanup.unsupportedZipEntriesBytes > 0) {
                                    Text("- Cleaned ${it.settingsCleanup.unsupportedZipEntriesBytes.fileSizeToString()} of junk data")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.backup_page_restart_app))
            }
        },
    )
}


