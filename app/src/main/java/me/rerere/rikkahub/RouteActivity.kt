package me.rerere.rikkahub

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.developer.DeveloperPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
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
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.service.EXTRA_IS_SPONTANEOUS_NOTIFICATION
import me.rerere.rikkahub.service.EXTRA_SPONTANEOUS_EVENT_ID
import me.rerere.rikkahub.service.EXTRA_SPONTANEOUS_MESSAGE
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.createChatTextFile
import me.rerere.search.SearchService
import org.jsoup.Jsoup
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"
private const val MAX_SHARED_WEBPAGE_CHARS = 16_000
private val SHARED_URL_REGEX = Regex("""(?i)\b((?:https?://|www\.)[^\s<>()]+)""")

/**
 * Data class to hold text selection intent data for navigation
 */
data class TextSelectionData(
    val navigateTo: String?,
    val selectedText: String?,
    val aiResponse: String?,
    val userPrompt: String?,
    val selectionAssistantId: String?
)

private data class ShareIntentData(
    val text: String,
    val subject: String?,
    val mimeType: String?,
    val streamUris: List<String>,
)

private data class SharedUrlMatch(
    val raw: String,
    val normalized: String,
)

private data class ScrapedWebsiteContent(
    val url: String,
    val title: String?,
    val description: String?,
    val content: String,
)

private data class SpontaneousNotificationData(
    val assistantId: String,
    val conversationId: String?,
    val eventId: String,
    val message: String,
)

class RouteActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private val spontaneousMessagingStateStore by inject<SpontaneousMessagingStateStore>()
    private val chatService by inject<me.rerere.rikkahub.service.ChatService>()
    private val conversationRepo by inject<me.rerere.rikkahub.data.repository.ConversationRepository>()
    private var navStack by mutableStateOf<NavHostController?>(null)
    private var pendingAssistantId by mutableStateOf<String?>(null)
    private var pendingTextSelection by mutableStateOf<TextSelectionData?>(null)
    private var pendingConversationId by mutableStateOf<String?>(null)
    private var pendingSpontaneousNotification by mutableStateOf<SpontaneousNotificationData?>(null)
    private var pendingShareIntent by mutableStateOf<ShareIntentData?>(null)

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
        
        // Store intent data - will be processed AFTER composition is ready
        val spontaneousNotification = intent.toSpontaneousNotificationData()
        val intentAssistantId = if (spontaneousNotification == null) intent?.getStringExtra("assistantId") else null
        val intentConversationId = if (spontaneousNotification == null) intent?.getStringExtra("conversationId") else null
        if (spontaneousNotification != null) {
            pendingSpontaneousNotification = spontaneousNotification
        }
        
        // Check for text selection intent
        val navigateTo = intent?.getStringExtra("navigate_to")
        val continueConversation = intent?.getBooleanExtra("continue_conversation", false) ?: false
        if (continueConversation) {
            pendingTextSelection = TextSelectionData(
                navigateTo = navigateTo,
                selectedText = intent?.getStringExtra("selected_text"),
                aiResponse = intent?.getStringExtra("ai_response"),
                userPrompt = intent?.getStringExtra("user_prompt"),
                selectionAssistantId = intent?.getStringExtra("selection_assistant_id")
            )
        }
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            pendingShareIntent = intent.toShareIntentData()
        }
        
        setContent {
            val navStack = rememberNavController()
            this.navStack = navStack
            ShareHandler(navStack)
            TextSelectionHandler(navStack)
            NotificationHandler(navStack)
            RikkahubTheme {
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
                AppRoutes(navStack)
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
        if (intentConversationId != null) {
            pendingConversationId = intentConversationId
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    
    // AssistantShortcutHandler removed - shortcuts now handled directly in onCreate/onNewIntent

    private fun Intent?.toShareIntentData(): ShareIntentData {
        if (this == null) {
            return ShareIntentData(
                text = "",
                subject = null,
                mimeType = null,
                streamUris = emptyList()
            )
        }

        val sharedText = runCatching {
            getStringExtra(Intent.EXTRA_TEXT)
                ?: getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: getStringExtra(Intent.EXTRA_HTML_TEXT)
                ?: ""
        }.getOrElse {
            android.util.Log.w(TAG, "Failed to parse shared text extra", it)
            ""
        }
        val sharedSubject = runCatching {
            getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
        }.getOrElse {
            android.util.Log.w(TAG, "Failed to parse shared subject extra", it)
            null
        }
        val sharedStreamUris = runCatching {
            buildList {
                @Suppress("DEPRECATION")
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()?.let(::add)
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.map(Uri::toString)
                    ?.let(::addAll)
                getStringExtra(Intent.EXTRA_STREAM)?.takeIf { it.isNotBlank() }?.let(::add)
                clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index).uri?.toString()?.let(::add)
                    }
                }
            }.distinct()
        }.getOrElse {
            android.util.Log.w(TAG, "Failed to parse shared stream extras", it)
            emptyList()
        }

        return ShareIntentData(
            text = sharedText,
            subject = sharedSubject,
            mimeType = type,
            streamUris = sharedStreamUris
        )
    }

    private suspend fun resolveShareIntentData(shareData: ShareIntentData): ShareIntentData {
        return withContext(Dispatchers.IO) {
            val copiedStreamUris = if (shareData.streamUris.isEmpty()) {
                emptyList()
            } else {
                createChatFilesByContents(shareData.streamUris.map { it.toUri() }).map(Uri::toString)
            }
            val scrapedWebsite = if (copiedStreamUris.isEmpty()) {
                scrapeWebsiteShare(shareData)
            } else {
                null
            }

            val baseText = shareData.text.ifBlank { shareData.subject.orEmpty() }
            if (scrapedWebsite == null) {
                shareData.copy(
                    text = baseText,
                    streamUris = copiedStreamUris
                )
            } else {
                shareData.copy(
                    text = scrapedWebsite.first,
                    streamUris = copiedStreamUris + scrapedWebsite.second
                )
            }
        }
    }

    private suspend fun scrapeWebsiteShare(shareData: ShareIntentData): Pair<String, String>? {
        val sharedUrl = findSharedUrlMatch(shareData.text) ?: return null
        val scrapedPage = scrapeWebsiteContent(sharedUrl.normalized) ?: return null
        val cleanedText = shareData.text
            .replace(sharedUrl.raw, "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n")
        val fileName = buildSharedWebpageFileName(
            title = scrapedPage.title,
            url = scrapedPage.url
        )
        val localFile = createChatTextFile(
            fileName = fileName,
            content = buildSharedWebpageDocument(scrapedPage)
        )
        return cleanedText to localFile.toString()
    }

    private suspend fun scrapeWebsiteContent(url: String): ScrapedWebsiteContent? {
        val settings = settingsStore.settingsFlow.value
        val selectedSearchOptions = settings.searchServices.getOrElse(
            index = settings.searchServiceSelected,
            defaultValue = { me.rerere.search.SearchServiceOptions.DEFAULT }
        )
        val searchService = SearchService.getService(selectedSearchOptions)

        if (searchService.scrapingParameters != null) {
            runCatching {
                val scrapedResult = searchService.scrape(
                    params = buildJsonObject {
                        put("url", url)
                    },
                    commonOptions = settings.searchCommonOptions,
                    serviceOptions = selectedSearchOptions,
                ).getOrThrow()
                scrapedResult.urls.firstOrNull { it.content.isNotBlank() }?.let { page ->
                    return ScrapedWebsiteContent(
                        url = page.url,
                        title = page.metadata?.title,
                        description = page.metadata?.description,
                        content = limitSharedWebpageContent(page.content)
                    )
                }
            }.onFailure {
                android.util.Log.w(TAG, "Configured scraper failed for shared URL: $url", it)
            }
        }

        return runCatching {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .timeout(15_000)
                .get()
            val description = document.selectFirst(
                "meta[name=description], meta[property=og:description]"
            )?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            val mainContent = document.selectFirst("main, article, [role=main]") ?: document.body()
            val blocks = mainContent
                .select("h1, h2, h3, h4, h5, h6, p, li, pre, blockquote")
                .eachText()
                .map(String::trim)
                .filter(String::isNotBlank)
            val textContent = if (blocks.isNotEmpty()) {
                blocks.joinToString("\n\n")
            } else {
                mainContent.text()
            }.trim()

            if (textContent.isBlank()) {
                null
            } else {
                ScrapedWebsiteContent(
                    url = url,
                    title = document.title().takeIf { it.isNotBlank() },
                    description = description,
                    content = limitSharedWebpageContent(textContent)
                )
            }
        }.getOrElse {
            android.util.Log.w(TAG, "Fallback scrape failed for shared URL: $url", it)
            null
        }
    }

    private fun findSharedUrlMatch(text: String): SharedUrlMatch? {
        val rawMatch = SHARED_URL_REGEX.find(text)?.groupValues?.getOrNull(1)
            ?.trimEnd('.', ',', ';', ':', ')', ']', '>', '"', '\'')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val normalized = if (rawMatch.startsWith("http://", ignoreCase = true) ||
            rawMatch.startsWith("https://", ignoreCase = true)
        ) {
            rawMatch
        } else {
            "https://$rawMatch"
        }
        return SharedUrlMatch(raw = rawMatch, normalized = normalized)
    }

    private fun buildSharedWebpageDocument(page: ScrapedWebsiteContent): String {
        return buildString {
            page.title?.let {
                append("# ")
                append(it)
                append("\n\n")
            }
            append("Source: ")
            append(page.url)
            append("\n\n")
            page.description?.let {
                append(it)
                append("\n\n")
            }
            append(page.content)
        }.trim()
    }

    private fun buildSharedWebpageFileName(title: String?, url: String): String {
        val host = Uri.parse(url).host
            ?.replace(Regex("[^A-Za-z0-9]+"), "-")
            ?.trim('-')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: "webpage"
        val titlePart = title
            ?.replace(Regex("[^A-Za-z0-9]+"), "-")
            ?.trim('-')
            ?.lowercase()
            ?.take(24)
            ?.takeIf { it.isNotBlank() }
        val baseName = listOfNotNull("shared-page", host, titlePart).joinToString("-")
        return "$baseName.md"
    }

    private fun limitSharedWebpageContent(content: String): String {
        if (content.length <= MAX_SHARED_WEBPAGE_CHARS) {
            return content
        }
        return content.take(MAX_SHARED_WEBPAGE_CHARS) +
            "\n\n[Shared webpage content truncated by LastChat.]"
    }

    private fun Intent?.toSpontaneousNotificationData(): SpontaneousNotificationData? {
        if (this == null || !getBooleanExtra(EXTRA_IS_SPONTANEOUS_NOTIFICATION, false)) {
            return null
        }

        val assistantId = getStringExtra("assistantId") ?: return null
        val eventId = getStringExtra(EXTRA_SPONTANEOUS_EVENT_ID) ?: return null
        val message = getStringExtra(EXTRA_SPONTANEOUS_MESSAGE) ?: return null

        return SpontaneousNotificationData(
            assistantId = assistantId,
            conversationId = getStringExtra("conversationId"),
            eventId = eventId,
            message = message,
        )
    }

    @Composable
    private fun ShareHandler(navBackStack: NavHostController) {
        val shareData = pendingShareIntent
        LaunchedEffect(navBackStack, shareData) {
            val currentShareData = shareData ?: return@LaunchedEffect
            pendingShareIntent = null
            val resolvedShareData = runCatching {
                resolveShareIntentData(currentShareData)
            }.getOrElse { throwable ->
                android.util.Log.e(TAG, "Share preprocessing failed", throwable)
                currentShareData
            }
            runCatching {
                navBackStack.navigate(
                    Screen.ShareHandler(
                        text = resolvedShareData.text,
                        files = resolvedShareData.streamUris
                    )
                )
            }.onFailure { throwable ->
                android.util.Log.e(TAG, "Share navigation failed", throwable)
                navBackStack.navigate(
                    Screen.ShareHandler(
                        text = resolvedShareData.text,
                        files = resolvedShareData.streamUris
                    )
                )
            }
        }
    }

    @Composable
    private fun NotificationHandler(navBackStack: NavHostController) {
        val spontaneousData = pendingSpontaneousNotification
        val conversationIdStr = pendingConversationId
        LaunchedEffect(spontaneousData, conversationIdStr) {
            if (spontaneousData != null) {
                pendingSpontaneousNotification = null
                navigateToSpontaneousNotification(navBackStack, spontaneousData)
            } else if (conversationIdStr != null) {
                pendingConversationId = null
                navBackStack.navigate(Screen.Chat(conversationIdStr))
            }
        }
    }

    private suspend fun navigateToSpontaneousNotification(
        navBackStack: NavHostController,
        data: SpontaneousNotificationData,
    ) {
        val assistantId = runCatching { Uuid.parse(data.assistantId) }.getOrNull() ?: return
        if (data.message.isBlank()) return
        val originalConversationId = data.conversationId?.let { raw ->
            runCatching { Uuid.parse(raw) }.getOrNull()
        }

        settingsStore.updateAssistant(assistantId)
        settingsStore.markAssistantUsed(assistantId)

        val targetConversationId = when {
            originalConversationId != null && conversationRepo.getConversationById(originalConversationId) != null -> {
                originalConversationId
            }

            else -> {
                val fallbackConversationId = spontaneousMessagingStateStore.getFallbackConversation(data.eventId)
                if (fallbackConversationId != null && conversationRepo.getConversationById(fallbackConversationId) != null) {
                    fallbackConversationId
                } else {
                    val fallbackConversation = chatService.persistSpontaneousAssistantMessage(
                        assistantId = assistantId,
                        content = data.message,
                    )
                    spontaneousMessagingStateStore.rememberFallbackConversation(data.eventId, fallbackConversation.id)
                    fallbackConversation.id
                }
            }
        }

        navBackStack.navigate(Screen.Chat(targetConversationId.toString()))
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
                    
                    // Create user message with selected text
                    val userContent = buildString {
                        if (!data.selectedText.isNullOrBlank()) {
                            append(data.selectedText)
                        }
                        if (!data.userPrompt.isNullOrBlank()) {
                            append("\n\n")
                            append(data.userPrompt)
                        }
                    }
                    
                    val messages = mutableListOf<me.rerere.rikkahub.data.model.MessageNode>()
                    
                    // Add user message if there's content
                    if (userContent.isNotBlank()) {
                        val userMessage = me.rerere.ai.ui.UIMessage.user(userContent.trim())
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
                        val assistantId = data.selectionAssistantId?.takeIf { it.isNotBlank() }?.let { 
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
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            pendingShareIntent = intent.toShareIntentData()
        }

        intent.toSpontaneousNotificationData()?.let { notification ->
            pendingSpontaneousNotification = notification
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
    fun AppRoutes(navBackStack: NavHostController) {
        val toastState = rememberAppToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navBackStack,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
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
                    startDestination = Screen.Chat(
                        id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random().toString()
                        } else {
                            readStringPreference(
                                "lastConversationId",
                                Uuid.random().toString()
                            ) ?: Uuid.random().toString()
                        }
                    ),
                    navController = navBackStack,
                    enterTransition = { 
                        slideInHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { it / 2 } + fadeIn(animationSpec = tween(150))
                    },
                    exitTransition = { 
                        slideOutHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { -it / 4 } + fadeOut(animationSpec = tween(100))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { -it / 4 } + fadeIn(animationSpec = tween(150))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { it / 2 } + fadeOut(animationSpec = tween(100))
                    }
                ) {
                    composable<Screen.Chat>(
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.Chat>()
                        ChatPage(
                            id = Uuid.parse(route.id),
                            text = route.text,
                            files = route.files.map { it.toUri() },
                            searchQuery = route.searchQuery
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
                                slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { it } + fadeIn(animationSpec = tween(180))
                            } else {
                                null
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route?.contains("SettingSkills") == true) {
                                slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { it } + fadeOut(animationSpec = tween(150))
                            } else {
                                null
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.contains("SettingSkills") == true) {
                                slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { it } + fadeIn(animationSpec = tween(180))
                            } else {
                                null
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route?.contains("SettingSkills") == true) {
                                slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { it } + fadeOut(animationSpec = tween(150))
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
                                slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { -it } + fadeIn(animationSpec = tween(180))
                            } else {
                                null
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route?.contains("SettingLorebooks") == true) {
                                slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { -it } + fadeOut(animationSpec = tween(150))
                            } else {
                                null
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.contains("SettingLorebooks") == true) {
                                slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { -it } + fadeIn(animationSpec = tween(180))
                            } else {
                                null
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route?.contains("SettingLorebooks") == true) {
                                slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { -it } + fadeOut(animationSpec = tween(150))
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
    data class Chat(val id: String, val text: String? = null, val files: List<String> = emptyList(), val searchQuery: String? = null) : Screen

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
