package me.rerere.rikkahub

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.SpontaneousMessagingStateStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalAnimatedVisibilityScope
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
import me.rerere.rikkahub.ui.motion.rootEnterTransition
import me.rerere.rikkahub.ui.motion.rootExitTransition
import me.rerere.rikkahub.ui.motion.rootPopEnterTransition
import me.rerere.rikkahub.ui.motion.rootPopExitTransition
import me.rerere.rikkahub.ui.motion.lateralEnterTransition
import me.rerere.rikkahub.ui.motion.lateralExitTransition
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.developer.DeveloperPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingChatStoragePage
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage

import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingTTSPage
import me.rerere.rikkahub.ui.pages.setting.SettingWebPage
import me.rerere.rikkahub.ui.pages.setting.SettingRpOptimizationsPage
import me.rerere.rikkahub.ui.pages.setting.SettingPromptInjectionsPage
import me.rerere.rikkahub.ui.pages.setting.SettingLorebooksPage
import me.rerere.rikkahub.ui.pages.setting.SettingLorebookDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingSkillsPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.pages.setting.SettingAndroidIntegrationPage
import me.rerere.rikkahub.ui.pages.setting.SettingUICustomizationPage
import me.rerere.rikkahub.ui.pages.setting.SettingFontsPage
import me.rerere.rikkahub.share.ResolvedSharePayload
import me.rerere.rikkahub.share.readResolvedSharePayload
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.service.EXTRA_IS_SPONTANEOUS_NOTIFICATION
import me.rerere.rikkahub.service.EXTRA_SPONTANEOUS_EVENT_ID
import me.rerere.rikkahub.service.EXTRA_SPONTANEOUS_MESSAGE
import me.rerere.rikkahub.service.EXTRA_SPONTANEOUS_RELATION
import me.rerere.rikkahub.service.ChatPersistenceMode
import me.rerere.rikkahub.ui.activity.QuickAskContinuationData
import me.rerere.rikkahub.ui.activity.buildQuickAskMessageParts
import me.rerere.rikkahub.ui.activity.readQuickAskContinuationData
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import me.rerere.rikkahub.utils.fileSizeToString
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

internal data class SpontaneousNotificationData(
    val assistantId: String,
    val conversationId: String?,
    val eventId: String,
    val message: String,
    val relation: me.rerere.rikkahub.service.SpontaneousMessageRelation,
)

internal data class ResolvedSpontaneousChatTarget(
    val conversationId: Uuid,
    val persistenceMode: ChatPersistenceMode,
    val assistantId: Uuid,
)

internal fun resolveSpontaneousNotificationRelation(
    relationExtra: String?,
    conversationId: String?,
): me.rerere.rikkahub.service.SpontaneousMessageRelation {
    return me.rerere.rikkahub.service.SpontaneousMessageRelation.fromWireValue(relationExtra)
        ?: if (conversationId.isNullOrBlank()) {
            me.rerere.rikkahub.service.SpontaneousMessageRelation.UNRELATED
        } else {
            me.rerere.rikkahub.service.SpontaneousMessageRelation.RECENT_CHAT
        }
}

internal suspend fun resolveSpontaneousNotificationTarget(
    data: SpontaneousNotificationData,
    isEventConsumed: (String) -> Boolean,
    updateAssistantSelection: suspend (Uuid) -> Unit,
    hasConversation: suspend (Uuid) -> Boolean,
    appendToConversation: suspend (Uuid, String, Uuid) -> Uuid?,
    seedDraftConversation: suspend (Uuid, String) -> Uuid?,
    markEventConsumed: (String) -> Unit,
): ResolvedSpontaneousChatTarget? {
    val assistantId = runCatching { Uuid.parse(data.assistantId) }.getOrNull() ?: return null
    val message = data.message.trim()
    if (message.isBlank() || isEventConsumed(data.eventId)) return null

    updateAssistantSelection(assistantId)

    val originalConversationId = data.conversationId?.let { raw ->
        runCatching { Uuid.parse(raw) }.getOrNull()
    }

    val target = when (data.relation) {
        me.rerere.rikkahub.service.SpontaneousMessageRelation.RECENT_CHAT -> {
            val existingConversationId = if (
                originalConversationId != null &&
                hasConversation(originalConversationId)
            ) {
                appendToConversation(assistantId, message, originalConversationId)
            } else {
                null
            }
            val conversationId = existingConversationId
                ?: seedDraftConversation(assistantId, message)
                ?: return null
            ResolvedSpontaneousChatTarget(
                conversationId = conversationId,
                persistenceMode = if (existingConversationId != null) {
                    ChatPersistenceMode.NORMAL
                } else {
                    ChatPersistenceMode.PERSIST_ON_REPLY
                },
                assistantId = assistantId,
            )
        }

        me.rerere.rikkahub.service.SpontaneousMessageRelation.UNRELATED -> {
            val conversationId = seedDraftConversation(assistantId, message) ?: return null
            ResolvedSpontaneousChatTarget(
                conversationId = conversationId,
                persistenceMode = ChatPersistenceMode.PERSIST_ON_REPLY,
                assistantId = assistantId,
            )
        }
    }

    markEventConsumed(data.eventId)
    return target
}

internal fun determineInitialChatScreen(
    defaultScreen: Screen.Chat,
    deepLinkedConversationId: String?,
    spontaneousTarget: ResolvedSpontaneousChatTarget?,
): Screen.Chat {
    return when {
        spontaneousTarget != null -> spontaneousTarget.toScreen()
        !deepLinkedConversationId.isNullOrBlank() -> Screen.Chat(id = deepLinkedConversationId)
        else -> defaultScreen
    }
}

private fun ResolvedSpontaneousChatTarget.toScreen(): Screen.Chat {
    return Screen.Chat(
        id = conversationId.toString(),
        persistenceMode = persistenceMode.routeValue.takeIf { persistenceMode != ChatPersistenceMode.NORMAL },
    )
}

class RouteActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private val spontaneousMessagingStateStore by inject<SpontaneousMessagingStateStore>()
    private val chatService by inject<me.rerere.rikkahub.service.ChatService>()
    private val conversationRepo by inject<me.rerere.rikkahub.data.repository.ConversationRepository>()
    private var navStack by mutableStateOf<NavHostController?>(null)
    private var pendingTextSelection by mutableStateOf<QuickAskContinuationData?>(null)
    private var pendingConversationId by mutableStateOf<String?>(null)
    private var pendingResolvedSpontaneousTarget by mutableStateOf<ResolvedSpontaneousChatTarget?>(null)
    private var pendingShareIntent by mutableStateOf<ResolvedSharePayload?>(null)
    private var initialChatScreen by mutableStateOf<Screen.Chat?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        
        // Track app launch and initialize usage stats
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { conversationRepo.initUsageStats() }
                .onFailure { android.util.Log.e(TAG, "initUsageStats failed", it) }
            runCatching { conversationRepo.backfillDailyActivityFromConversationHistoryIfNeeded() }
                .onFailure { android.util.Log.e(TAG, "daily activity backfill failed", it) }
            runCatching { conversationRepo.backfillUsageStatsFromHistoryIfNeeded() }
                .onFailure { android.util.Log.e(TAG, "usage stats backfill failed", it) }
            runCatching { conversationRepo.incrementAppLaunches() }
                .onFailure { android.util.Log.e(TAG, "increment app launches failed", it) }
        }
        
        val spontaneousNotification = intent.toSpontaneousNotificationData()
        val intentAssistantId = if (spontaneousNotification == null) intent?.getStringExtra("assistantId") else null
        val intentConversationId = if (spontaneousNotification == null) intent?.getStringExtra("conversationId") else null
        val intentWebServerSettings = intent?.getBooleanExtra("webServerSettings", false) == true
        pendingTextSelection = intent?.readQuickAskContinuationData()
        pendingShareIntent = intent?.readResolvedSharePayload()
        lifecycleScope.launch {
            initialChatScreen = determineInitialChatScreen(
                defaultScreen = defaultStartScreen(),
                deepLinkedConversationId = intentConversationId,
                spontaneousTarget = spontaneousNotification?.let { resolveSpontaneousChatTarget(it) },
            )
        }

        setContent {
            val navStack = rememberNavController()
            this.navStack = navStack
            RikkahubTheme {
                val startScreen = initialChatScreen
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .memoryCache {
                            MemoryCache.Builder()
                                .maxSizePercent(context, 0.25) // Use 25% of app's memory for image cache
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(context.filesDir.resolve("icon_cache").toOkioPath())
                                .maxSizeBytes(50 * 1024 * 1024) // 50 MB persistent disk cache for icons
                                .build()
                        }
                        .components {
                            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                if (startScreen == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    ShareHandler(navStack)
                    TextSelectionHandler(navStack)
                    NotificationHandler(navStack)
                    AppRoutes(navStack, startScreen)
                    
                    LaunchedEffect(intentWebServerSettings) {
                        if (intentWebServerSettings) {
                            navStack.navigate(Screen.SettingWeb)
                        }
                    }
                }
            }
        }
        
        // Handle assistant shortcut - navigate directly by waiting for navStack to be ready
        if (intentAssistantId != null) {
            lifecycleScope.launch {
                // Wait for navStack to be ready (set in composition)
                while (navStack == null) {
                    kotlinx.coroutines.delay(50)
                }
                try {
                    val assistantId = Uuid.parse(intentAssistantId)
                    // Update the selected assistant
                    settingsStore.updateAssistant(assistantId)
                    // Mark as recently used
                    settingsStore.markAssistantUsed(assistantId)
                    // Navigate to a new chat
                    navStack?.navigate(Screen.Chat(Uuid.random().toString())) {
                        popUpTo(0) { inclusive = true }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun defaultStartScreen(): Screen.Chat {
        return Screen.Chat(
            id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                readStringPreference(
                    "lastConversationId",
                    Uuid.random().toString()
                ) ?: Uuid.random().toString()
            }
        )
    }

    private fun Intent?.toSpontaneousNotificationData(): SpontaneousNotificationData? {
        if (this == null || !getBooleanExtra(EXTRA_IS_SPONTANEOUS_NOTIFICATION, false)) {
            return null
        }

        val assistantId = getStringExtra("assistantId") ?: return null
        val eventId = getStringExtra(EXTRA_SPONTANEOUS_EVENT_ID) ?: return null
        val message = getStringExtra(EXTRA_SPONTANEOUS_MESSAGE) ?: return null
        val conversationId = getStringExtra("conversationId")
        val relation = resolveSpontaneousNotificationRelation(
            relationExtra = getStringExtra(EXTRA_SPONTANEOUS_RELATION),
            conversationId = conversationId,
        )

        return SpontaneousNotificationData(
            assistantId = assistantId,
            conversationId = conversationId,
            eventId = eventId,
            message = message,
            relation = relation,
        )
    }

    @Composable
    private fun ShareHandler(navBackStack: NavHostController) {
        val shareData = pendingShareIntent
        LaunchedEffect(navBackStack, shareData) {
            val currentShareData = shareData ?: return@LaunchedEffect
            pendingShareIntent = null
            runCatching {
                navBackStack.navigate(
                    Screen.ShareHandler(
                        text = currentShareData.text,
                        files = currentShareData.attachmentUris()
                    )
                )
            }.onFailure { throwable ->
                android.util.Log.e(TAG, "Share navigation failed", throwable)
                navBackStack.navigate(
                    Screen.ShareHandler(
                        text = currentShareData.text,
                        files = currentShareData.attachmentUris()
                    )
                )
            }
        }
    }

    @Composable
    private fun NotificationHandler(navBackStack: NavHostController) {
        val spontaneousTarget = pendingResolvedSpontaneousTarget
        val conversationIdStr = pendingConversationId
        LaunchedEffect(spontaneousTarget, conversationIdStr) {
            if (spontaneousTarget != null) {
                pendingResolvedSpontaneousTarget = null
                navBackStack.navigate(spontaneousTarget.toScreen())
            } else if (conversationIdStr != null) {
                pendingConversationId = null
                navBackStack.navigate(Screen.Chat(conversationIdStr))
            }
        }
    }

    private suspend fun resolveSpontaneousChatTarget(
        data: SpontaneousNotificationData,
    ): ResolvedSpontaneousChatTarget? {
        return resolveSpontaneousNotificationTarget(
            data = data,
            isEventConsumed = spontaneousMessagingStateStore::isEventConsumed,
            updateAssistantSelection = { assistantId ->
                settingsStore.updateAssistant(assistantId)
                settingsStore.markAssistantUsed(assistantId)
            },
            hasConversation = { conversationId ->
                conversationRepo.getConversationById(conversationId) != null
            },
            appendToConversation = { assistantId, message, conversationId ->
                chatService.persistSpontaneousAssistantMessage(
                    assistantId = assistantId,
                    content = message,
                    conversationId = conversationId,
                ).id
            },
            seedDraftConversation = { assistantId, message ->
                chatService.seedSpontaneousDraftConversation(
                    assistantId = assistantId,
                    content = message,
                ).id
            },
            markEventConsumed = spontaneousMessagingStateStore::markEventConsumed,
        )
    }

    @Composable
    private fun TextSelectionHandler(navBackStack: NavHostController) {
        val data = pendingTextSelection
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        
        
        LaunchedEffect(data) {
            if (data != null) {
                pendingTextSelection = null
                try {
                    // Create a new conversation with pre-existing messages
                    val conversationId = Uuid.random()
                    
                    val messages = mutableListOf<me.rerere.rikkahub.data.model.MessageNode>()

                    val userParts = buildQuickAskMessageParts(
                        text = data.text,
                        attachments = data.attachments,
                        customPrompt = data.userPrompt
                    )

                    if (userParts.isNotEmpty()) {
                        val userMessage = me.rerere.ai.ui.UIMessage(
                            role = me.rerere.ai.core.MessageRole.USER,
                            parts = userParts
                        )
                        messages.add(me.rerere.rikkahub.data.model.MessageNode.of(userMessage))
                    }
                    
                    // Add AI response message if available
                    val aiResponse = data.aiResponse
                    if (!aiResponse.isNullOrBlank()) {
                        val assistantMessage = me.rerere.ai.ui.UIMessage.assistant(aiResponse)
                        messages.add(me.rerere.rikkahub.data.model.MessageNode.of(assistantMessage))
                    }
                    
                    if (messages.isNotEmpty()) {
                        // Use the assistant from text selection config if available
                        val assistantId = data.assistantId?.takeIf { it.isNotBlank() }?.let {
                            try { Uuid.parse(it) } catch (e: Exception) { null }
                        } ?: settings.assistantId
                        
                        // Create the conversation with messages
                        val conversation = me.rerere.rikkahub.data.model.Conversation.ofId(
                            id = conversationId,
                            assistantId = assistantId,
                            messages = messages
                        )
                        
                        // Save to database
                        chatService.saveConversation(conversationId, conversation)
                        
                        // Navigate to the conversation
                        navBackStack.navigate(Screen.Chat(id = conversationId.toString()))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d(TAG, "onNewIntent called")
        android.util.Log.d(TAG, "Intent extras: conversationId=${intent.getStringExtra("conversationId")}, assistantId=${intent.getStringExtra("assistantId")}")
        pendingShareIntent = intent.readResolvedSharePayload()
        pendingTextSelection = intent.readQuickAskContinuationData() ?: pendingTextSelection

        if (intent.getBooleanExtra("webServerSettings", false)) {
            navStack?.navigate(Screen.SettingWeb)
            return
        }

        intent.toSpontaneousNotificationData()?.let { notification ->
            lifecycleScope.launch {
                resolveSpontaneousChatTarget(notification)?.let { target ->
                    if (navStack == null) {
                        initialChatScreen = target.toScreen()
                    } else {
                        pendingResolvedSpontaneousTarget = target
                    }
                }
            }
            return
        }
        
        // Navigate to the chat screen if a conversation ID is provided
        intent.getStringExtra("conversationId")?.let { text ->
            android.util.Log.d(TAG, "Navigating to conversation: $text")
            navStack?.navigate(Screen.Chat(text))
        }
        
        // Handle assistant shortcut - navigate directly instead of using state
        intent.getStringExtra("assistantId")?.let { assistantIdStr ->
            android.util.Log.d(TAG, "Handling assistant shortcut directly: $assistantIdStr")
            lifecycleScope.launch {
                try {
                    val assistantId = Uuid.parse(assistantIdStr)
                    android.util.Log.d(TAG, "Updating to assistant: $assistantId")
                    // Update the selected assistant
                    settingsStore.updateAssistant(assistantId)
                    // Mark as recently used
                    settingsStore.markAssistantUsed(assistantId)
                    // Navigate to a new chat
                    val newChatId = Uuid.random().toString()
                    android.util.Log.d(TAG, "Navigating to new chat: $newChatId")
                    navStack?.navigate(Screen.Chat(newChatId)) {
                        popUpTo(0) { inclusive = true }
                    }
                    android.util.Log.d(TAG, "Navigation complete")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error handling assistant shortcut", e)
                    e.printStackTrace()
                }
            }
        }
    }

    @Composable
    fun AppRoutes(navBackStack: NavHostController, startDestination: Screen.Chat) {
        val toastState = rememberAppToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val motionPolicy = rememberSystemMotionPolicy()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navBackStack,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalMotionPolicy provides motionPolicy,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
            ) {
                // Check for backup cleanup results and show toast
                LaunchedEffect(Unit) {
                    val prefs = this@RouteActivity.getSharedPreferences("backup_cleanup", MODE_PRIVATE)
                    val unsupportedBytes = prefs.getLong("unsupported_bytes", 0)
                    val issuesFixed = prefs.getInt("issues_fixed", 0)
                    val skippedRows = prefs.getInt("db_skipped_rows", 0)
                    
                    if (unsupportedBytes > 0 || issuesFixed > 0 || skippedRows > 0) {
                        // Clear the stored values
                        prefs.edit().clear().apply()
                        
                        // Build cleanup message
                        val parts = mutableListOf<String>()
                        if (unsupportedBytes > 0) {
                            parts.add("${unsupportedBytes.fileSizeToString()} of unsupported data")
                        }
                        if (issuesFixed > 0) {
                            parts.add("$issuesFixed invalid references")
                        }
                        if (skippedRows > 0) {
                            parts.add("$skippedRows corrupt items removed")
                        }
                        
                        val message = "Import completed: ${parts.joinToString(", ")}"
                        toastState.show(message, type = me.rerere.rikkahub.ui.components.ui.ToastType.Info)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                TTSController()
                NavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    startDestination = startDestination,
                    navController = navBackStack,
                    enterTransition = { rootEnterTransition(motionPolicy) },
                    exitTransition = { rootExitTransition(motionPolicy) },
                    popEnterTransition = { rootPopEnterTransition(motionPolicy) },
                    popExitTransition = { rootPopExitTransition(motionPolicy) }
                ) {
                    composable<Screen.Chat> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.Chat>()
                        ChatPage(
                            id = Uuid.parse(route.id),
                            text = route.text,
                            files = route.files.map { it.toUri() },
                            searchQuery = route.searchQuery,
                            persistenceMode = route.persistenceMode,
                        )
                    }

                    composable<Screen.ShareHandler> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.ShareHandler>()
                        ShareHandlerPage(
                            text = route.text,
                            files = route.files
                        )
                    }



                    // All assistant-related routes share the same AnimatedVisibilityScope
                    // for seamless hero animations across all screens
                    composable<Screen.Assistant> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                            AssistantPage()
                        }
                    }

                    composable<Screen.AssistantDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantDetail>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                            AssistantDetailPage(
                                id = route.id,
                                startRoute = route.startRoute,
                                initialMemoryTab = route.initialMemoryTab,
                                scrollToMemoryId = route.scrollToMemoryId
                            )
                        }
                    }

                    composable<Screen.Menu> {
                        MenuPage()
                    }

                    composable<Screen.Setting> {
                        SettingPage()
                    }

                    composable<Screen.Backup> {
                        BackupPage()
                    }

                    composable<Screen.ImageGen> {
                        ImageGenPage()
                    }

                    composable<Screen.WebView> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.WebView>()
                        WebViewPage(route.url, route.content)
                    }

                    composable<Screen.SettingDisplay> {
                        SettingDisplayPage()
                    }

                    composable<Screen.SettingProvider> {
                        SettingProviderPage()
                    }

                    composable<Screen.SettingProviderDetail> {
                        val route = it.toRoute<Screen.SettingProviderDetail>()
                        val id = Uuid.parse(route.providerId)
                        SettingProviderDetailPage(id = id)
                    }

                    composable<Screen.SettingModels> {
                        SettingModelPage()
                    }

                    composable<Screen.SettingAbout> {
                        SettingAboutPage()
                    }

                    composable<Screen.SettingChatStorage> {
                        SettingChatStoragePage()
                    }

                    composable<Screen.SettingSearch> {
                        SettingSearchPage()
                    }

                    composable<Screen.SettingTTS> {
                        SettingTTSPage()
                    }

                    composable<Screen.SettingWeb> {
                        SettingWebPage()
                    }

                    composable<Screen.SettingMcp> {
                        SettingMcpPage()
                    }

                    composable<Screen.SettingRpOptimizations> {
                        SettingRpOptimizationsPage()
                    }

                    composable<Screen.SettingPromptInjections> {
                        SettingPromptInjectionsPage()
                    }

                    composable<Screen.SettingLorebooks>(
                        enterTransition = {
                            if (initialState.destination.route?.contains("SettingSkills") == true) {
                                lateralEnterTransition(
                                    offset = { it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route?.contains("SettingSkills") == true) {
                                lateralExitTransition(
                                    offset = { it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.contains("SettingSkills") == true) {
                                lateralEnterTransition(
                                    offset = { it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route?.contains("SettingSkills") == true) {
                                lateralExitTransition(
                                    offset = { it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        }
                    ) {
                        SettingLorebooksPage()
                    }

                    composable<Screen.SettingLorebookDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.SettingLorebookDetail>()
                        SettingLorebookDetailPage(id = route.id, scrollToEntryId = route.scrollToEntryId)
                    }

                    composable<Screen.SettingSkills>(
                        enterTransition = {
                            if (initialState.destination.route?.contains("SettingLorebooks") == true) {
                                lateralEnterTransition(
                                    offset = { -it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route?.contains("SettingLorebooks") == true) {
                                lateralExitTransition(
                                    offset = { -it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.contains("SettingLorebooks") == true) {
                                lateralEnterTransition(
                                    offset = { -it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route?.contains("SettingLorebooks") == true) {
                                lateralExitTransition(
                                    offset = { -it },
                                    motionPolicy = motionPolicy
                                )
                            } else {
                                null
                            }
                        }
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.SettingSkills>()
                        SettingSkillsPage(scrollToSkillId = route.scrollToSkillId)
                    }

                    composable<Screen.Developer> {
                        DeveloperPage()
                    }

                    composable<Screen.SettingAndroidIntegration> {
                        SettingAndroidIntegrationPage()
                    }

                    composable<Screen.SettingUICustomization> {
                        SettingUICustomizationPage()
                    }

                    composable<Screen.SettingFonts> {
                        SettingFontsPage()
                    }

                }
                // Toast host must be last so it renders on top of all content
                AppToasterHost(state = toastState)
                }
            }
        }
    }
}

sealed interface Screen {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val searchQuery: String? = null,
        val persistenceMode: String? = null,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val files: List<String> = emptyList()) : Screen


    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(
        val id: String,
        val startRoute: String? = null,  // Navigate directly to a sub-route (e.g., "memory")
        val initialMemoryTab: Int? = null,  // 0 = Core, 1 = Episodic
        val scrollToMemoryId: Int? = null  // Memory ID to scroll to
    ) : Screen

    @Serializable
    data object Menu : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val content: String = "") : Screen

    @Serializable
    data object SettingDisplay : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingChatStorage : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data object SettingTTS : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingRpOptimizations : Screen

    @Serializable
    data object SettingPromptInjections : Screen

    @Serializable
    data object SettingLorebooks : Screen

    @Serializable
    data class SettingLorebookDetail(val id: String, val scrollToEntryId: String? = null) : Screen

    @Serializable
    data object Developer : Screen

    @Serializable
    data class SettingSkills(val scrollToSkillId: String? = null) : Screen

    @Serializable
    data object SettingAndroidIntegration : Screen

    @Serializable
    data object SettingUICustomization : Screen

    @Serializable
    data object SettingFonts : Screen

}
