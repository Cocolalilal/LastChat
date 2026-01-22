package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import me.rerere.rikkahub.data.model.Tag as DataTag
import me.rerere.rikkahub.ui.pages.chat.ExportOptionsDialog
import me.rerere.rikkahub.utils.AssistantExportImport
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.ToastAction


// Sub-routes within assistant detail
private object AssistantDetailRoutes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val MODEL = "model"
    const val PROMPTS = "prompts"
    const val CONTEXT_MANAGEMENT = "context_management"
    const val LOREBOOKS = "lorebooks"
    const val TOOLS = "tools"
    const val MEMORY = "memory"
    const val UI = "ui"
    const val ADVANCED = "advanced"
}

@Composable
fun AssistantDetailPage(
    id: String,
    startRoute: String? = null,
    initialMemoryTab: Int? = null,
    scrollToMemoryId: Int? = null
) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )

    val navController = rememberNavController()
    val toaster = LocalToaster.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    val hasMemories by vm.hasMemories.collectAsStateWithLifecycle()
    val hasLorebooks by vm.hasLorebooks.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    // Export state
    var showExportMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showExportOptionsDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var pendingExportContent by remember { androidx.compose.runtime.mutableStateOf("") }
    var pendingExportBytes by remember { androidx.compose.runtime.mutableStateOf<ByteArray?>(null) }
    
    // Export file launcher (JSON)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportContent.isNotEmpty()) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { 
                        it.write(pendingExportContent.toByteArray())
                    }
                    toaster.show(context.getString(R.string.export_success))
                } catch (e: Exception) {
                    e.printStackTrace()
                    toaster.show("Export failed: ${e.message}")
                }
                pendingExportContent = ""
            }
        }
    }
    
    // Export file launcher (PNG)
    val pngExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null && pendingExportBytes != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { 
                        it.write(pendingExportBytes!!)
                    }
                    toaster.show(context.getString(R.string.export_success))
                } catch (e: Exception) {
                    e.printStackTrace()
                    toaster.show("Export failed: ${e.message}")
                }
                pendingExportBytes = null
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbarMessage()
        }
    }
    
    // Auto-navigate to start route if specified (e.g., for deep linking to memory)
    LaunchedEffect(startRoute) {
        if (startRoute == AssistantDetailRoutes.MEMORY) {
            navController.navigate(AssistantDetailRoutes.MEMORY) {
                popUpTo(AssistantDetailRoutes.HOME) { inclusive = false }
            }
        }
    }

    fun onUpdate(assistant: Assistant) {
        vm.update(assistant)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Show title only on sub-pages, not on home - fade in only, instant exit
                    AnimatedVisibility(
                        visible = currentRoute != null && currentRoute != AssistantDetailRoutes.HOME,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(0)) // Instant exit to avoid fade artifact
                    ) {
                        Text(
                            text = assistant.name.ifBlank {
                                stringResource(R.string.assistant_page_default_assistant)
                            },
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    AnimatedVisibility(
                        visible = currentRoute == AssistantDetailRoutes.HOME,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Upload,
                                    contentDescription = "Export"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("LastChat Bundle (.json)") },
                                    onClick = {
                                        showExportMenu = false
                                        if (hasMemories || hasLorebooks) {
                                            showExportOptionsDialog = true
                                        } else {
                                            scope.launch {
                                                try {
                                                    val content = AssistantExportImport.exportToLastChatBundle(
                                                        assistant = assistant,
                                                        context = context,
                                                        includeMemories = false,
                                                        includeLorebooks = false
                                                    )
                                                    pendingExportContent = content
                                                    val fileName = AssistantExportImport.getSuggestedFileName(assistant, "lastchat")
                                                    exportLauncher.launch(fileName)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    toaster.show("Export failed: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Character Card V2 (.json)") },
                                    onClick = {
                                        showExportMenu = false
                                        scope.launch {
                                            try {
                                                val content = AssistantExportImport.exportToCharacterCardV2(assistant, context)
                                                pendingExportContent = content
                                                val fileName = AssistantExportImport.getSuggestedFileName(assistant, "card_v2")
                                                exportLauncher.launch(fileName)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                toaster.show("Export failed: ${e.message}")
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Character Card V2 (.png)") },
                                    onClick = {
                                        showExportMenu = false
                                        scope.launch {
                                            try {
                                                val bytes = AssistantExportImport.exportToCharacterCardPng(assistant, context)
                                                if (bytes != null) {
                                                    pendingExportBytes = bytes
                                                    val fileName = AssistantExportImport.getSuggestedFileName(assistant, "card_v2_png")
                                                    pngExportLauncher.launch(fileName)
                                                } else {
                                                    toaster.show("Export failed: Could not create PNG")
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                toaster.show("Export failed: ${e.message}")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AssistantDetailRoutes.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            composable(AssistantDetailRoutes.HOME) {
                AssistantDetailHome(
                    assistant = assistant,
                    onNavigateToProfile = { navController.navigate(AssistantDetailRoutes.PROFILE) },
                    onNavigateToModel = { navController.navigate(AssistantDetailRoutes.MODEL) },
                    onNavigateToPrompts = { navController.navigate(AssistantDetailRoutes.PROMPTS) },
                    onNavigateToContextManagement = { navController.navigate(AssistantDetailRoutes.CONTEXT_MANAGEMENT) },
                    onNavigateToTools = { navController.navigate(AssistantDetailRoutes.TOOLS) },
                    onNavigateToMemory = { navController.navigate(AssistantDetailRoutes.MEMORY) },
                    onNavigateToUI = { navController.navigate(AssistantDetailRoutes.UI) },
                    onNavigateToAdvanced = { navController.navigate(AssistantDetailRoutes.ADVANCED) }
                )
            }

            // Profile (Identity, Tags, Appearance)
            composable(AssistantDetailRoutes.PROFILE) {
                AssistantProfileSubPage(
                    assistant = assistant,
                    tags = tags,
                    onUpdate = { onUpdate(it) },
                    vm = vm
                )
            }

            // Model (Chat model, parameters, reasoning)
            composable(AssistantDetailRoutes.MODEL) {
                AssistantModelSubPage(
                    assistant = assistant,
                    providers = providers,
                    onUpdate = { onUpdate(it) }
                )
            }

            // Prompts
            composable(AssistantDetailRoutes.PROMPTS) {
                AssistantPromptSubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) },
                    vm = vm
                )
            }

            // Context Management
            composable(AssistantDetailRoutes.CONTEXT_MANAGEMENT) {
                AssistantContextManagementSubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) },
                    onNavigateToLorebooks = { navController.navigate(AssistantDetailRoutes.LOREBOOKS) },
                    onNavigateToModels = { navController.navigate(AssistantDetailRoutes.MODEL) }
                )
            }

            // Lorebooks (nested under Context Management)
            composable(AssistantDetailRoutes.LOREBOOKS) {
                AssistantLorebooksSubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) },
                    vm = vm
                )
            }

            // Tools
            composable(AssistantDetailRoutes.TOOLS) {
                AssistantToolsSubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) },
                    vm = vm,
                    mcpServerConfigs = mcpServerConfigs
                )
            }

            // Memory
            composable(AssistantDetailRoutes.MEMORY) {
                val embeddingProgress by vm.embeddingProgress.collectAsStateWithLifecycle()
                val estimatedMemoryCapacity by vm.estimatedMemoryCapacity.collectAsStateWithLifecycle()
                val needsEmbeddingRegeneration by vm.needsEmbeddingRegeneration.collectAsStateWithLifecycle()
                val retrievalResults by vm.retrievalResults.collectAsStateWithLifecycle()
                AssistantMemorySettings(
                    assistant = assistant,
                    memories = memories,
                    onUpdateAssistant = { onUpdate(it) },
                    onDeleteMemory = { vm.deleteMemory(it) },
                    onAddMemory = { vm.addMemory(it) },
                    onUpdateMemory = { vm.updateMemory(it) },
                    onRegenerateEmbeddings = { vm.regenerateEmbeddings() },
                    embeddingProgress = embeddingProgress,
                    onTestRetrieval = { vm.testRetrieval(it) },
                    retrievalResults = retrievalResults,
                    assistantDetailVM = vm,
                    estimatedMemoryCapacity = estimatedMemoryCapacity,
                    needsEmbeddingRegeneration = needsEmbeddingRegeneration,
                    initialMemoryTab = initialMemoryTab,
                    scrollToMemoryId = scrollToMemoryId,
                    onNavigateToModels = { navController.navigate(AssistantDetailRoutes.MODEL) }
                )
            }

            // UI Customization
            composable(AssistantDetailRoutes.UI) {
                AssistantUISubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) }
                )
            }

            // Advanced
            composable(AssistantDetailRoutes.ADVANCED) {
                AssistantAdvancedSubPage(
                    assistant = assistant,
                    onUpdate = { onUpdate(it) }
                )
            }
        }
    }


    
    if (showExportOptionsDialog) {
        me.rerere.rikkahub.ui.pages.chat.ExportOptionsDialog(
            title = "Export Options",
            onDismissRequest = { showExportOptionsDialog = false },
            showMemoriesOption = hasMemories,
            showLorebooksOption = hasLorebooks,
            onConfirm = { includeMemories, includeLorebooks ->
                showExportOptionsDialog = false
                scope.launch {
                    try {
                        val content = AssistantExportImport.exportToLastChatBundle(
                            assistant = assistant, 
                            context = context, 
                            includeMemories = includeMemories, 
                            includeLorebooks = includeLorebooks
                        )
                        pendingExportContent = content
                        val fileName = AssistantExportImport.getSuggestedFileName(assistant, "lastchat")
                        exportLauncher.launch(fileName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                            toaster.show(message = "Export failed: ${e.message}")
                    }
                }
            }
        )
    }
}

/**
 * Home screen with header and navigation cards
 */
@Composable
private fun AssistantDetailHome(
    assistant: Assistant,
    onNavigateToProfile: () -> Unit,
    onNavigateToModel: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    onNavigateToContextManagement: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToUI: () -> Unit,
    onNavigateToAdvanced: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // HEADER SECTION - Avatar, Name, System Prompt Preview (2 lines, centered)
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hero animation for smooth List ↔ Home transition
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = null, // Read-only in home view
                modifier = Modifier
                    .size(96.dp)
                    .heroAnimation(key = "assistant_avatar_${assistant.id}")
            )
            
            Text(
                text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            if (assistant.systemPrompt.isNotBlank()) {
                Text(
                    text = assistant.systemPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ═══════════════════════════════════════════════════════════════════
        // NAVIGATION CARDS - Grouped properly
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Configuration") {
            NavigationCard(
                icon = Icons.Rounded.Person,
                title = "Profile",
                description = "Name, avatar, tags, appearance",
                onClick = onNavigateToProfile
            )

            NavigationCard(
                icon = Icons.AutoMirrored.Rounded.Chat,
                title = stringResource(R.string.assistant_page_tab_prompt),
                description = "System prompts, templates, regex",
                onClick = onNavigateToPrompts
            )

            NavigationCard(
                icon = Icons.Rounded.DataObject,
                title = stringResource(R.string.context_management_title),
                description = stringResource(R.string.context_management_desc),
                onClick = onNavigateToContextManagement
            )

            NavigationCard(
                icon = Icons.Rounded.Psychology,
                title = "Models",
                description = "Chat model, summarizer, generation settings",
                onClick = onNavigateToModel
            )
        }

        SettingsGroup(title = "Capabilities") {
            NavigationCard(
                icon = Icons.Rounded.Memory,
                title = stringResource(R.string.assistant_page_tab_memory),
                description = "Memory settings, recent chats, RAG",
                onClick = onNavigateToMemory
            )

            NavigationCard(
                icon = Icons.Rounded.Build,
                title = "Tools & Search",
                description = "Search providers, local tools, MCP",
                onClick = onNavigateToTools
            )
        }

        SettingsGroup(title = "Other") {
            NavigationCard(
                icon = Icons.Rounded.Palette,
                title = "UI Customization",
                description = "Per-character display settings",
                onClick = onNavigateToUI
            )

            NavigationCard(
                icon = Icons.Rounded.Tune,
                title = "Advanced",
                description = "Notifications, custom headers",
                onClick = onNavigateToAdvanced
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Navigation card component with proper styling
 */
@Composable
private fun NavigationCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    SettingGroupItem(
        title = title,
        subtitle = description,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        onClick = onClick
    )
}
