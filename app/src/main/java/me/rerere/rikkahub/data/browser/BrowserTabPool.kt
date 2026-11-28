package me.rerere.rikkahub.data.browser

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.data.datastore.AgentSecretWritePolicy
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.BrowserViewMode
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-tab native WebView pool for LastChat, optimized for 4GB-RAM tablets.
 */
class BrowserTabPool(
    private val context: Context,
    private val settingsStore: SettingsStore,
    val secretsStore: BrowserSecretsStore,
) {
    companion object {
        private const val TAG = "BrowserTabPool"
        private const val IDLE_CHECK_INTERVAL_MS = 60_000L
        private const val TAB_SERIAL_WAIT_TIMEOUT_MS = 60_000L
        private const val IMPLICIT_TAB_GRACE_MS = 15_000L
        private const val IMPLICIT_TAB_WAIT_MS = 20_000L
        private const val IMPLICIT_TAB_WAIT_POLL_MS = 250L

        fun middleTruncated(name: String, head: Int = 22, tail: Int = 10): String {
            if (name.length <= head + tail + 1) return name
            return name.take(head) + "…" + name.takeLast(tail)
        }
    }

    data class Tab(
        val id: Int,
        val manager: BrowserUseManager,
        var inUse: Boolean = false,
        var lastActivityDate: Date = Date(),
        var needsInitialBlankPage: Boolean = false,
        var inUseGraceJob: Job? = null,
    )

    enum class DownloadState { DOWNLOADING, COMPLETED, FAILED }

    data class DownloadUiState(val filename: String, val progress: Float)

    data class DownloadEntry(
        val id: Long,
        val filename: String,
        val destination: File?,
        val bytesDone: Long,
        val totalBytes: Long,
        val state: DownloadState,
        val failureReason: String? = null,
        val startedAt: Long,
        val seen: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tabLocks = ConcurrentHashMap<Int, Mutex>()

    private fun lockForTab(id: Int): Mutex = tabLocks.getOrPut(id) { Mutex() }

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _selectedTabId = MutableStateFlow(0)

    private val _barDismissedByAgentVersion = MutableStateFlow(0)
    val barDismissedByAgentVersion: StateFlow<Int> = _barDismissedByAgentVersion.asStateFlow()
    val selectedTabId: StateFlow<Int> = _selectedTabId.asStateFlow()

    private val _isAgentBusyFlow = MutableStateFlow(false)
    val isAgentBusyFlow: StateFlow<Boolean> = _isAgentBusyFlow.asStateFlow()

    val isAgentBusy: Boolean get() = _tabs.value.any { it.inUse }

    private val _browserSheetOpen = MutableStateFlow(false)
    val browserSheetOpen: StateFlow<Boolean> = _browserSheetOpen.asStateFlow()

    private val _userAgentProfile = MutableStateFlow(UserAgentProfile.DESKTOP_CHROME)
    val currentUserAgentProfile: StateFlow<UserAgentProfile> = _userAgentProfile.asStateFlow()

    private val _customViewportWidth = MutableStateFlow(0)
    val customViewportWidth: StateFlow<Int> = _customViewportWidth.asStateFlow()
    private val _customViewportHeight = MutableStateFlow(0)
    val customViewportHeight: StateFlow<Int> = _customViewportHeight.asStateFlow()

    private val _sessionViewportWidth = MutableStateFlow(0)
    val sessionViewportWidth: StateFlow<Int> = _sessionViewportWidth.asStateFlow()
    private val _sessionViewportHeight = MutableStateFlow(0)
    val sessionViewportHeight: StateFlow<Int> = _sessionViewportHeight.asStateFlow()

    private val _activeDownload = MutableStateFlow<DownloadUiState?>(null)
    val activeDownload: StateFlow<DownloadUiState?> = _activeDownload.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val nextDownloadId = AtomicLong(1)

    @Volatile
    private var activeSessionId: String? = null
    @Volatile
    private var activeModelId: String? = null
    @Volatile
    private var customUserAgentString: String? = null

    private val savedURLs = mutableMapOf<Int, String>()
    private var nextTabId = 0

    fun managerForTab(tabId: Int?): BrowserUseManager? {
        val current = _tabs.value
        if (current.isEmpty()) return null
        if (tabId != null) {
            current.firstOrNull { it.id == tabId }?.manager?.let { return it }
        }
        return activeManager
    }

    val activeManager: BrowserUseManager?
        get() {
            val current = _tabs.value
            if (current.isEmpty()) return null
            return current.firstOrNull { it.id == _selectedTabId.value }?.manager ?: current.first().manager
        }

    private val currentBrowserSettings: BrowserSettings
        get() = settingsStore.settingsFlow.value.browserSettings

    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (!currentBrowserSettings.evictWebViewsOnLowMemory) return
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            ) {
                scope.launch(Dispatchers.Main) { evictBackgroundTabsOnMemoryPressure() }
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
            if (currentBrowserSettings.evictWebViewsOnLowMemory) {
                scope.launch(Dispatchers.Main) { evictBackgroundTabsOnMemoryPressure() }
            }
        }
    }

    init {
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        // Observe BrowserSettings changes reactively
        scope.launch {
            settingsStore.settingsFlow.collect { settings ->
                val bs = settings.browserSettings
                BrowserFrames.updateCapacityMb(bs.frameCacheCapMb)
                secretsStore.setSyncToWorkspaceEnabled(bs.syncSecretsToWorkspace)
                val (profile, vp) = bs.resolveDefaultProfileAndViewport()
                _userAgentProfile.value = profile
                customUserAgentString = bs.customUserAgent.takeIf { it.isNotBlank() }
                if (bs.defaultViewMode == BrowserViewMode.CUSTOM) {
                    _customViewportWidth.value = vp.first
                    _customViewportHeight.value = vp.second
                } else if (bs.defaultViewMode == BrowserViewMode.FIT_SCREEN) {
                    _customViewportWidth.value = 0
                    _customViewportHeight.value = 0
                } else {
                    _customViewportWidth.value = 0
                    _customViewportHeight.value = 0
                }
            }
        }

        // Idle tab eviction loop
        scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                withContext(Dispatchers.Main) { evictIdleTabs() }
            }
        }
    }

    fun setActiveModelId(modelId: String?) {
        activeModelId = modelId
    }

    fun openBrowserSheet(url: String? = null) {
        scope.launch(Dispatchers.Main) {
            if (!url.isNullOrBlank()) {
                selectOrCreateTabForURL(url)
            } else {
                ensureTabForUI()
            }
            _browserSheetOpen.value = true
        }
    }

    fun closeBrowserSheet() {
        _browserSheetOpen.value = false
    }

    suspend fun setSession(sessionId: String) {
        if (activeSessionId == sessionId) return
        withContext(Dispatchers.Main) {
            saveState()
            // Destroy live WebViews from previous session to preserve 4GB RAM
            destroyAllTabsInMemoryLocked()
            savedURLs.clear()
            _sessionViewportWidth.value = 0
            _sessionViewportHeight.value = 0
            activeSessionId = sessionId
            loadSavedState()
        }
    }

    fun deleteSessionData(conversationId: String) {
        scope.launch(Dispatchers.Main) {
            if (activeSessionId == conversationId) {
                destroyAllTabsInMemoryLocked()
                savedURLs.clear()
                activeSessionId = null
            }
            withContext(Dispatchers.IO) {
                runCatching { File(context.filesDir, "browser_sessions/$conversationId").deleteRecursively() }
                runCatching { File(context.filesDir, "browser_tabs/$conversationId.json").delete() }
            }
        }
    }

    suspend fun clearAllBrowserData() {
        withContext(Dispatchers.Main) {
            destroyAllTabsInMemoryLocked()
            savedURLs.clear()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            BrowserHistoryStore.getInstance(context).clear()
            BrowserFrames.clear()
            withContext(Dispatchers.IO) {
                runCatching { File(context.filesDir, "browser_sessions").deleteRecursively() }
                runCatching { File(context.filesDir, "browser_tabs").deleteRecursively() }
            }
        }
    }

    private fun destroyAllTabsInMemoryLocked() {
        for (tab in _tabs.value) {
            tab.inUseGraceJob?.cancel()
            runCatching {
                tab.manager.webView.stopLoading()
                tab.manager.webView.destroy()
            }
        }
        _tabs.value = emptyList()
        _isAgentBusyFlow.value = false
    }

    // -- Downloads --

    private fun sessionDownloadsDir(): File {
        val sid = activeSessionId ?: "default"
        return File(context.filesDir, "browser_sessions/$sid/downloads").apply { mkdirs() }
    }

    private fun sessionScreenshotsDir(): File {
        val sid = activeSessionId ?: "default"
        return File(context.filesDir, "browser_sessions/$sid").apply { mkdirs() }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext")
            i++
        }
        return f
    }

    private fun registerDownload(dest: File, totalBytes: Long): Long {
        val id = nextDownloadId.getAndIncrement()
        val entry = DownloadEntry(
            id = id,
            filename = dest.name,
            destination = dest,
            bytesDone = 0L,
            totalBytes = totalBytes,
            state = DownloadState.DOWNLOADING,
            startedAt = System.currentTimeMillis(),
        )
        _downloads.value = listOf(entry) + _downloads.value
        _activeDownload.value = DownloadUiState(dest.name, if (totalBytes > 0) 0f else -1f)
        return id
    }

    private fun updateDownloadProgress(id: Long, name: String, copied: Long, total: Long) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(bytesDone = copied, totalBytes = total) else it
        }
        _activeDownload.value = DownloadUiState(
            name,
            if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else -1f,
        )
    }

    private fun settleDownload(id: Long, state: DownloadState, reason: String? = null, bytes: Long? = null) {
        downloadJobs.remove(id)
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(state = state, failureReason = reason, bytesDone = bytes ?: it.bytesDone) else it
        }
        _activeDownload.value = null
    }

    fun cancelDownload(id: Long) {
        downloadJobs[id]?.cancel()
    }

    fun downloadBadgeCount(entries: List<DownloadEntry>): Int =
        entries.count { it.state == DownloadState.DOWNLOADING || !it.seen }

    fun markDownloadsSeen() {
        _downloads.value = _downloads.value.map {
            if (it.state != DownloadState.DOWNLOADING) it.copy(seen = true) else it
        }
    }

    fun clearFinishedDownloads() {
        _downloads.value = _downloads.value.filter { it.state == DownloadState.DOWNLOADING }
    }

    internal fun startUrlDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) {
        val dir = sessionDownloadsDir()
        val name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        val dest = uniqueFile(dir, name)
        val id = registerDownload(dest, contentLength)
        val job = downloadScope.launch {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                userAgent?.takeIf { it.isNotEmpty() }?.let { conn.setRequestProperty("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                val total = if (contentLength > 0) contentLength else conn.contentLengthLong
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            updateDownloadProgress(id, dest.name, copied, total)
                        }
                    }
                }
                conn.disconnect()
                settleDownload(id, DownloadState.COMPLETED, bytes = dest.length())
            } catch (e: kotlinx.coroutines.CancellationException) {
                runCatching { dest.delete() }
                settleDownload(id, DownloadState.FAILED, reason = "Cancelled")
            } catch (t: Throwable) {
                runCatching { dest.delete() }
                settleDownload(id, DownloadState.FAILED, reason = t.message ?: "error")
            }
        }
        downloadJobs[id] = job
    }

    internal fun saveBlobDownload(data: ByteArray, filename: String, mimeType: String?) {
        val dir = sessionDownloadsDir()
        var name = filename
        if ((name.endsWith(".bin") || !name.contains('.')) && mimeType != null) {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ext ->
                name = name.substringBeforeLast('.') + "." + ext
            }
        }
        val dest = uniqueFile(dir, name)
        val id = registerDownload(dest, data.size.toLong())
        val job = downloadScope.launch {
            try {
                dest.writeBytes(data)
                settleDownload(id, DownloadState.COMPLETED, bytes = dest.length())
            } catch (t: Throwable) {
                runCatching { dest.delete() }
                settleDownload(id, DownloadState.FAILED, reason = t.message ?: "error")
            }
        }
        downloadJobs[id] = job
    }

    private fun wireHandlers(manager: BrowserUseManager) {
        manager.onDownloadStart = { url, ua, cd, mime, len -> startUrlDownload(url, ua, cd, mime, len) }
        manager.onBlobDownloadData = { data, name, mime -> saveBlobDownload(data, name, mime) }
        manager.onHandoffRequested = { url -> openBrowserSheet(url) }
    }

    // -- Agent Execution --

    suspend fun execute(input: BrowserActionInput, singleTab: Boolean = false): BrowserActionResult {
        return when (input.action) {
            BrowserAction.NEW_TAB -> newTab(input.url)
            BrowserAction.CLOSE_TAB -> closeTab(input.tabId)
            BrowserAction.LIST_TABS -> listTabs()
            BrowserAction.SET_VIEWPORT -> handleSetViewport(input)
            BrowserAction.HAND_OVER -> handleHandOver(input)
            BrowserAction.SAVE_SECRET -> handleSaveSecret(input)
            BrowserAction.SAVE_COOKIES_AS_SECRET -> handleSaveCookiesAsSecret(input)
            BrowserAction.RESTORE_COOKIES_FROM_SECRET -> handleRestoreCookiesFromSecret(input)
            BrowserAction.CLOSE_BROWSER -> {
                closeBrowserSheet()
                releaseAllTabs()
                _barDismissedByAgentVersion.value += 1
                BrowserActionResult(text = "Closed browser preview bar.", success = true)
            }
            else -> {
                val serialTabId = input.tabId?.takeIf { reqId -> _tabs.value.any { it.id == reqId } }
                if (serialTabId != null) {
                    executeSerialized(serialTabId, input)
                } else {
                    val mustFollowSelected = singleTab || !input.action.opensNewPage
                    if (mustFollowSelected && _tabs.value.isNotEmpty()) {
                        val sel = _selectedTabId.value
                        val targetId = if (_tabs.value.any { it.id == sel }) sel
                        else _tabs.value.maxByOrNull { it.lastActivityDate }!!.id
                        executeSerialized(targetId, input)
                    } else {
                        runAcquiredAction(input, implicitTab = true)
                    }
                }
            }
        }
    }

    private suspend fun handleHandOver(input: BrowserActionInput): BrowserActionResult {
        val bs = currentBrowserSettings
        if (!bs.allowAgentHandOverAction) {
            return BrowserActionResult(
                text = "Hand-over action is disabled in Browser Settings. Proceed directly or report the blocker to the user.",
                success = false,
            )
        }
        val reason = input.reason?.ifBlank { null } ?: input.text?.ifBlank { null } ?: ""
        val selected = _selectedTabId.value
        val url = input.url?.takeIf { it.isNotBlank() }
            ?: _tabs.value.firstOrNull { it.id == selected }?.manager?.currentURL?.value.orEmpty()
        withContext(Dispatchers.Main) {
            releaseAllTabs()
            if (url.isNotBlank()) selectOrCreateTabForURL(url) else ensureTabForUI()
            _browserSheetOpen.value = true
        }
        val done = BrowserHandOver.wait(
            conversationId = activeSessionId.orEmpty(),
            reason = reason,
            url = url,
            timeoutMinutes = bs.handOverTimeoutMinutes,
        )
        val what = reason.ifBlank { "what the page asked of them" }
        return if (done) {
            BrowserActionResult(
                text = "The user finished the hand-over ($what) and returned control. Continue from the page as it is now — take a screenshot or read it first.",
                pageURL = activeManager?.currentURL?.value ?: url,
                tabId = _selectedTabId.value,
            )
        } else {
            BrowserActionResult(
                text = "The user has not finished the hand-over ($what) yet. Stop and tell them you will continue once they are done.",
                pageURL = url,
                tabId = _selectedTabId.value,
            )
        }
    }

    private suspend fun handleSaveSecret(input: BrowserActionInput): BrowserActionResult {
        val bs = currentBrowserSettings
        if (bs.agentSecretWritePolicy == AgentSecretWritePolicy.READ_ONLY) {
            return BrowserActionResult.error("Secret saving is disabled (Agent Secret Write Policy is Read Only in Browser Settings).")
        }
        val key = input.key?.trim()?.takeIf { it.isNotEmpty() }
            ?: return BrowserActionResult.error("save_secret requires 'key'")
        val value = input.value?.takeIf { it.isNotEmpty() }
            ?: return BrowserActionResult.error("save_secret requires 'value'")
        val domain = input.domain ?: runCatching {
            activeManager?.currentURL?.value?.let { URI(it).host }
        }.getOrNull().orEmpty()

        val exists = secretsStore.hasKey(key)
        if (exists && !input.overwrite) {
            return BrowserActionResult.error("Secret '$key' already exists. Pass overwrite=true if you intend to replace it.")
        }

        if (bs.agentSecretWritePolicy == AgentSecretWritePolicy.ASK_BEFORE_SAVING) {
            val approved = BrowserRiskGate.requestApproval(
                conversationId = activeSessionId.orEmpty(),
                kind = BrowserRiskGate.Kind.SAVE_SECRET,
                title = if (exists) "Overwrite saved secret?" else "Save browser secret?",
                summary = buildString {
                    append(if (exists) "Overwrite" else "Save")
                    append(" key '$key'")
                    if (domain.isNotBlank()) append(" for $domain")
                },
                detail = input.note,
            )
            if (!approved) {
                return BrowserActionResult.error("User declined saving secret '$key'.")
            }
        }

        val saved = secretsStore.saveSecret(
            key = key,
            value = value,
            domain = domain,
            kind = BrowserSecretKind.SITE_LOGIN,
            note = input.note.orEmpty(),
            savedByAgent = true,
            overwrite = input.overwrite,
        )
        return saved.fold(
            onSuccess = { meta ->
                BrowserActionResult(
                    text = "Saved secret '${meta.key}'${if (meta.domain.isNotBlank()) " for ${meta.domain}" else ""}. Reference it as {{SECRET:${meta.key}}}.",
                )
            },
            onFailure = { err -> BrowserActionResult.error(err.message ?: "Failed to save secret") },
        )
    }

    private suspend fun handleSaveCookiesAsSecret(input: BrowserActionInput): BrowserActionResult {
        val bs = currentBrowserSettings
        if (bs.agentSecretWritePolicy == AgentSecretWritePolicy.READ_ONLY) {
            return BrowserActionResult.error("Secret saving is disabled (Agent Secret Write Policy is Read Only in Browser Settings).")
        }
        val key = input.key?.trim()?.takeIf { it.isNotEmpty() }
            ?: return BrowserActionResult.error("save_cookies_as_secret requires 'key'")
        val manager = activeManager
            ?: return BrowserActionResult.error("save_cookies_as_secret: no active browser tab")
        val extracted = manager.extractCookiesForCurrentPage(input.keywords, input.fuzzy)
        val (url, pairs) = extracted.getOrElse {
            return BrowserActionResult.error("save_cookies_as_secret: ${it.message}")
        }
        if (pairs.isEmpty()) {
            return BrowserActionResult.error("save_cookies_as_secret: no matching cookies found on $url")
        }
        val exists = secretsStore.hasKey(key)
        if (exists && !input.overwrite) {
            return BrowserActionResult.error("Secret '$key' already exists. Pass overwrite=true to replace it.")
        }
        val domain = input.domain ?: runCatching { URI(url).host }.getOrNull().orEmpty()
        if (bs.agentSecretWritePolicy == AgentSecretWritePolicy.ASK_BEFORE_SAVING) {
            val approved = BrowserRiskGate.requestApproval(
                conversationId = activeSessionId.orEmpty(),
                kind = BrowserRiskGate.Kind.SAVE_SECRET,
                title = if (exists) "Overwrite cookie bundle?" else "Save session cookies as secret?",
                summary = "${if (exists) "Overwrite" else "Save"} ${pairs.size} cookie(s) from ${domain.ifBlank { url }} as '$key'",
                detail = "Cookie names: ${pairs.joinToString(", ") { it.first }}",
            )
            if (!approved) {
                return BrowserActionResult.error("User declined saving cookie bundle '$key'.")
            }
        }

        val bundleJson = JSONArray().apply {
            for ((name, value) in pairs) {
                put(JSONObject().apply {
                    put("name", name)
                    put("value", value)
                    if (domain.isNotBlank()) put("domain", domain)
                    put("path", "/")
                })
            }
        }.toString()

        val saved = secretsStore.saveSecret(
            key = key,
            value = bundleJson,
            domain = domain,
            kind = BrowserSecretKind.COOKIE_BUNDLE,
            note = input.note ?: "Cookies (${pairs.size}) from $domain",
            savedByAgent = true,
            overwrite = input.overwrite,
        )
        return saved.fold(
            onSuccess = { meta ->
                BrowserActionResult(
                    text = "Saved ${pairs.size} cookie(s) as secret '${meta.key}' for ${meta.domain}. Restore later with restore_cookies_from_secret(key='${meta.key}').",
                    pageURL = url,
                    tabId = _selectedTabId.value,
                )
            },
            onFailure = { err -> BrowserActionResult.error(err.message ?: "Failed to save cookie bundle") },
        )
    }

    private suspend fun handleRestoreCookiesFromSecret(input: BrowserActionInput): BrowserActionResult {
        val key = input.key?.trim()?.takeIf { it.isNotEmpty() }
            ?: return BrowserActionResult.error("restore_cookies_from_secret requires 'key'")
        val meta = secretsStore.getMetadata(key)
            ?: return BrowserActionResult.error("Secret '$key' not found")
        val raw = secretsStore.getValue(key)
            ?: return BrowserActionResult.error("Secret '$key' has no stored payload")
        val arr = runCatching { JSONArray(raw) }.getOrNull()
            ?: return BrowserActionResult.error("Secret '$key' is not a valid cookie bundle")

        val cookies = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { co ->
                co.keys().asSequence().associateWith { k -> if (co.isNull(k)) null else co.get(k) }
            }
        }
        val manager = withContext(Dispatchers.Main) {
            activeManager ?: ensureTabForUI().manager
        }
        val targetUrl = input.url ?: meta.domain.takeIf { it.isNotBlank() }?.let { "https://${it.trimStart('.')}/" }
        val result = manager.applyCookies(cookies, targetUrl = targetUrl)
        return result.copy(
            pageURL = manager.currentURL.value.ifBlank { targetUrl },
            tabId = _selectedTabId.value,
        )
    }

    private suspend fun executeSerialized(tabId: Int, input: BrowserActionInput): BrowserActionResult {
        val mutex = lockForTab(tabId)
        val result = withTimeoutOrNull(TAB_SERIAL_WAIT_TIMEOUT_MS) {
            mutex.withLock {
                runAcquiredAction(input, implicitTab = false, acquireTabId = tabId)
            }
        }
        return result ?: BrowserActionResult.error(
            "Tab $tabId is busy (${TAB_SERIAL_WAIT_TIMEOUT_MS / 1000L}s timeout). Open a new tab or retry."
        )
    }

    private suspend fun runAcquiredAction(
        input: BrowserActionInput,
        implicitTab: Boolean,
        acquireTabId: Int? = null,
    ): BrowserActionResult {
        val tab = acquireTab(acquireTabId ?: input.tabId)
            ?: return BrowserActionResult.error("Failed to acquire browser tab")
        return try {
            val result = tab.manager.execute(input)
            val withDialogs = tab.manager.drainInterceptedDialogReport()
                ?.let { result.copy(text = it + result.text) }
                ?: result
            stampTabId(withDialogs.copy(pageURL = tab.manager.currentURL.value), tab.id)
        } finally {
            tab.lastActivityDate = Date()
            if (implicitTab) {
                armImplicitGraceRelease(tab)
            } else {
                tab.inUseGraceJob?.cancel()
                tab.inUseGraceJob = null
                tab.inUse = false
            }
            updateTabs()
            saveState()
        }
    }

    private fun stampTabId(result: BrowserActionResult, targetId: Int): BrowserActionResult {
        val needsLine = !result.text.contains("tab_id:")
        val text = if (needsLine) {
            val needsNewline = result.text.isNotEmpty() && !result.text.endsWith("\n")
            result.text + (if (needsNewline) "\n" else "") + "  tab_id: $targetId"
        } else {
            result.text
        }
        return result.copy(text = text, tabId = targetId)
    }

    private fun armImplicitGraceRelease(tab: Tab) {
        tab.inUse = true
        _isAgentBusyFlow.value = isAgentBusy
        tab.inUseGraceJob?.cancel()
        tab.inUseGraceJob = scope.launch {
            delay(IMPLICIT_TAB_GRACE_MS)
            withContext(Dispatchers.Main) {
                tab.inUse = false
                tab.inUseGraceJob = null
                tab.lastActivityDate = Date()
                updateTabs()
            }
        }
    }

    private suspend fun acquireTab(requestedTabId: Int? = null): Tab? = withContext(Dispatchers.Main) {
        var currentTabs = _tabs.value.toMutableList()
        val tab = if (requestedTabId != null && currentTabs.any { it.id == requestedTabId }) {
            currentTabs.first { it.id == requestedTabId }
        } else {
            var picked = currentTabs.firstOrNull { !it.inUse } ?: createTab(currentTabs)
            if (picked == null) {
                var waited = 0L
                while (waited < IMPLICIT_TAB_WAIT_MS) {
                    delay(IMPLICIT_TAB_WAIT_POLL_MS)
                    waited += IMPLICIT_TAB_WAIT_POLL_MS
                    currentTabs = _tabs.value.toMutableList()
                    picked = currentTabs.firstOrNull { !it.inUse } ?: createTab(currentTabs)
                    if (picked != null) break
                }
            }
            picked ?: currentTabs.firstOrNull()
        }

        if (tab != null) {
            tab.inUseGraceJob?.cancel()
            tab.inUseGraceJob = null
            tab.inUse = true
            tab.lastActivityDate = Date()
            _selectedTabId.value = tab.id
            _tabs.value = currentTabs
            _isAgentBusyFlow.value = isAgentBusy
        }

        if (tab != null && tab.needsInitialBlankPage) {
            tab.needsInitialBlankPage = false
            tab.manager.loadBlankPage()
        }
        tab
    }

    private fun createTab(tabs: MutableList<Tab>, url: String? = null): Tab? {
        val maxTabs = currentBrowserSettings.maxTabs.coerceIn(1, 3)
        if (tabs.size >= maxTabs) return null

        val id = nextTabId++
        val webView = WebView(context)
        val manager = BrowserUseManager(
            webView = webView,
            profile = _userAgentProfile.value,
            conversationIdProvider = { activeSessionId },
            settingsProvider = { currentBrowserSettings },
            secretsStore = secretsStore,
            modelIdProvider = { activeModelId },
            screenshotsDirProvider = { sessionScreenshotsDir() },
        )
        if (_userAgentProfile.value == UserAgentProfile.CUSTOM && !customUserAgentString.isNullOrEmpty()) {
            manager.setUserAgent(_userAgentProfile.value, customUserAgentString)
        }
        val (vpW, vpH) = resolvedViewportSize()
        manager.applyViewport(vpW, vpH)
        manager.onNewWindow = { msg -> handleNewWindow(msg) }
        manager.onCloseWindow = { handleCloseWindow(manager) }
        wireHandlers(manager)

        val tab = Tab(id = id, manager = manager)
        tabs.add(tab)
        _tabs.value = tabs.toList()

        val loadUrl = url ?: savedURLs.remove(id)
        if (loadUrl != null) {
            manager.loadURL(loadUrl)
        } else {
            tab.needsInitialBlankPage = true
        }
        return tab
    }

    // -- Tab management --

    private suspend fun newTab(url: String?): BrowserActionResult = withContext(Dispatchers.Main) {
        val currentTabs = _tabs.value.toMutableList()
        val maxTabs = currentBrowserSettings.maxTabs.coerceIn(1, 3)
        if (currentTabs.size >= maxTabs) {
            return@withContext BrowserActionResult.error("Maximum $maxTabs tabs reached")
        }
        val tab = createTab(currentTabs, url)
            ?: return@withContext BrowserActionResult.error("Failed to create new tab")
        _selectedTabId.value = tab.id
        if (tab.needsInitialBlankPage) {
            tab.needsInitialBlankPage = false
            tab.manager.loadBlankPage()
        }
        saveState()
        BrowserActionResult(
            text = "Opened new tab ${tab.id}" + (if (url != null) " at $url" else "") + ". Use tab_id: ${tab.id} to target this tab.",
            tabId = tab.id,
        )
    }

    private suspend fun closeTab(tabId: Int?): BrowserActionResult = withContext(Dispatchers.Main) {
        val id = tabId ?: _selectedTabId.value
        val currentTabs = _tabs.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == id }
        if (idx < 0) return@withContext BrowserActionResult.error("Tab $id not found")
        val removed = currentTabs.removeAt(idx)
        removed.inUseGraceJob?.cancel()
        runCatching { removed.manager.webView.destroy() }
        _tabs.value = currentTabs
        if (currentTabs.isNotEmpty() && _selectedTabId.value == id) {
            _selectedTabId.value = currentTabs.first().id
        }
        saveState()
        BrowserActionResult(text = "Closed tab $id")
    }

    private fun listTabs(): BrowserActionResult {
        val lines = _tabs.value.map { tab ->
            val marker = if (tab.id == _selectedTabId.value) "*" else " "
            val title = tab.manager.pageTitle.value.ifEmpty { "(blank)" }
            val url = tab.manager.currentURL.value.ifEmpty { "about:blank" }
            "$marker Tab ${tab.id}: $title — $url"
        }
        return if (lines.isEmpty()) BrowserActionResult(text = "No open tabs")
        else BrowserActionResult(text = lines.joinToString("\n"))
    }

    private fun handleNewWindow(resultMsg: Message) {
        val currentTabs = _tabs.value.toMutableList()
        val maxTabs = currentBrowserSettings.maxTabs.coerceIn(1, 3)
        if (currentTabs.size >= maxTabs) return
        val id = nextTabId++
        val newWebView = WebView(context)
        val manager = BrowserUseManager(
            webView = newWebView,
            profile = _userAgentProfile.value,
            conversationIdProvider = { activeSessionId },
            settingsProvider = { currentBrowserSettings },
            secretsStore = secretsStore,
            modelIdProvider = { activeModelId },
            screenshotsDirProvider = { sessionScreenshotsDir() },
        )
        val (vpW, vpH) = resolvedViewportSize()
        manager.applyViewport(vpW, vpH)
        manager.onNewWindow = { msg -> handleNewWindow(msg) }
        manager.onCloseWindow = { handleCloseWindow(manager) }
        wireHandlers(manager)

        val tab = Tab(id = id, manager = manager)
        currentTabs.add(tab)
        _tabs.value = currentTabs
        _selectedTabId.value = id

        val transport = resultMsg.obj as? WebView.WebViewTransport
        transport?.webView = newWebView
        resultMsg.sendToTarget()
    }

    private fun handleCloseWindow(manager: BrowserUseManager) {
        val currentTabs = _tabs.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.manager === manager }
        if (idx >= 0) {
            val closed = currentTabs.removeAt(idx)
            closed.inUseGraceJob?.cancel()
            runCatching { closed.manager.webView.destroy() }
            _tabs.value = currentTabs
            if (_selectedTabId.value == closed.id && currentTabs.isNotEmpty()) {
                _selectedTabId.value = currentTabs.first().id
            }
        }
    }

    // -- UI Tab Actions --

    fun selectTab(id: Int) {
        if (_tabs.value.any { it.id == id }) {
            _selectedTabId.value = id
        }
    }

    suspend fun newTabFromUI(): Tab? = withContext(Dispatchers.Main) {
        val currentTabs = _tabs.value.toMutableList()
        val tab = createTab(currentTabs) ?: return@withContext null
        _selectedTabId.value = tab.id
        if (tab.needsInitialBlankPage) {
            tab.needsInitialBlankPage = false
            tab.manager.loadBlankPage()
        }
        saveState()
        tab
    }

    suspend fun closeTabFromUI(tabId: Int) = withContext(Dispatchers.Main) {
        closeTab(tabId)
    }

    fun selectOrCreateTabForURL(url: String): Tab? {
        if (url.isBlank()) return _tabs.value.firstOrNull()
        val target = normalizeUrlForMatch(url)
        val currentTabs = _tabs.value.toMutableList()
        val match = currentTabs.firstOrNull { normalizeUrlForMatch(it.manager.currentURL.value) == target }
        if (match != null) {
            _selectedTabId.value = match.id
            match.lastActivityDate = Date()
            updateTabs()
            return match
        }
        val tab = createTab(currentTabs, url) ?: return null
        _selectedTabId.value = tab.id
        saveState()
        return tab
    }

    private fun normalizeUrlForMatch(raw: String): String {
        if (raw.isEmpty()) return ""
        return try {
            val uri = android.net.Uri.parse(raw)
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""
            val path = (uri.path ?: "").trimEnd('/')
            "$scheme://$host$path"
        } catch (_: Exception) {
            raw.trimEnd('/')
        }
    }

    fun ensureTabForUI(): Tab {
        val currentTabs = _tabs.value.toMutableList()
        val existing = currentTabs.firstOrNull()
        if (existing != null) {
            if (_selectedTabId.value != existing.id && currentTabs.none { it.id == _selectedTabId.value }) {
                _selectedTabId.value = existing.id
            }
            return existing
        }
        val tab = createTab(currentTabs)!!
        _selectedTabId.value = tab.id
        saveState()
        return tab
    }

    fun setUserAgentFromUI(profile: UserAgentProfile, customUA: String? = null) {
        _userAgentProfile.value = profile
        customUserAgentString = customUA
        for (tab in _tabs.value) {
            tab.manager.setUserAgent(profile, customUA)
        }
        scope.launch { applyViewportToAllTabs() }
    }

    fun releaseAllTabs() {
        _tabs.value = _tabs.value.map {
            it.inUseGraceJob?.cancel()
            it.copy(inUse = false, inUseGraceJob = null)
        }
        _isAgentBusyFlow.value = false
        saveState()
    }

    // -- Eviction (Idle & Low-Memory 4GB-RAM Protection) --

    fun evictIdleTabs() {
        val now = System.currentTimeMillis()
        val currentTabs = _tabs.value.toMutableList()
        val timeoutMs = currentBrowserSettings.idleTabTimeoutMinutes.coerceIn(1, 240) * 60_000L
        val toRemove = currentTabs.filter { !it.inUse && (now - it.lastActivityDate.time) >= timeoutMs }
        for (tab in toRemove) {
            val url = tab.manager.currentURL.value
            if (url.isNotEmpty()) savedURLs[tab.id] = url
            tab.inUseGraceJob?.cancel()
            runCatching { tab.manager.webView.destroy() }
            currentTabs.remove(tab)
            Log.i(TAG, "Evicted idle tab ${tab.id}")
        }
        if (toRemove.isNotEmpty()) {
            _tabs.value = currentTabs
            _isAgentBusyFlow.value = isAgentBusy
            if (currentTabs.isNotEmpty() && currentTabs.none { it.id == _selectedTabId.value }) {
                _selectedTabId.value = currentTabs.first().id
            }
            saveState()
        }
    }

    fun evictBackgroundTabsOnMemoryPressure() {
        val currentTabs = _tabs.value.toMutableList()
        val selected = _selectedTabId.value
        val toEvict = currentTabs.filter { !it.inUse && it.id != selected }
        if (toEvict.isEmpty()) return
        for (tab in toEvict) {
            val url = tab.manager.currentURL.value
            if (url.isNotEmpty()) savedURLs[tab.id] = url
            tab.inUseGraceJob?.cancel()
            runCatching { tab.manager.webView.destroy() }
            currentTabs.remove(tab)
            Log.i(TAG, "Evicted non-selected tab ${tab.id} under memory pressure")
        }
        _tabs.value = currentTabs
        _isAgentBusyFlow.value = isAgentBusy
        saveState()
    }

    // -- Viewport API --

    fun resolvedViewportSize(): Pair<Int, Int> {
        if (_sessionViewportWidth.value > 0 && _sessionViewportHeight.value > 0) {
            return _sessionViewportWidth.value to _sessionViewportHeight.value
        }
        if (_customViewportWidth.value > 0 && _customViewportHeight.value > 0) {
            return _customViewportWidth.value to _customViewportHeight.value
        }
        if (currentBrowserSettings.defaultViewMode == BrowserViewMode.FIT_SCREEN) {
            return 0 to 0
        }
        return _userAgentProfile.value.viewportSize
    }

    suspend fun setSessionViewport(width: Int, height: Int) {
        _sessionViewportWidth.value = width.coerceAtLeast(0)
        _sessionViewportHeight.value = height.coerceAtLeast(0)
        applyViewportToAllTabs()
        saveState()
    }

    suspend fun resetSessionViewport() {
        _sessionViewportWidth.value = 0
        _sessionViewportHeight.value = 0
        applyViewportToAllTabs()
        saveState()
    }

    suspend fun applyQuickViewMode(mode: BrowserViewMode) {
        settingsStore.update { current ->
            current.copy(browserSettings = current.browserSettings.copy(defaultViewMode = mode).normalized())
        }
        _sessionViewportWidth.value = 0
        _sessionViewportHeight.value = 0
        val (profile, vp) = currentBrowserSettings.copy(defaultViewMode = mode).resolveDefaultProfileAndViewport()
        _userAgentProfile.value = profile
        if (mode == BrowserViewMode.CUSTOM) {
            _customViewportWidth.value = vp.first
            _customViewportHeight.value = vp.second
        } else {
            _customViewportWidth.value = 0
            _customViewportHeight.value = 0
        }
        withContext(Dispatchers.Main) {
            for (tab in _tabs.value) {
                tab.manager.setUserAgent(profile, customUserAgentString)
            }
        }
        applyViewportToAllTabs()
    }

    private suspend fun applyViewportToAllTabs() {
        val (w, h) = resolvedViewportSize()
        withContext(Dispatchers.Main) {
            for (tab in _tabs.value) {
                tab.manager.applyViewport(w, h)
                tab.manager.reloadAndWait()
            }
        }
    }

    private suspend fun handleSetViewport(input: BrowserActionInput): BrowserActionResult = withContext(Dispatchers.Main) {
        if (input.reset) {
            resetSessionViewport()
            val (w, h) = resolvedViewportSize()
            return@withContext BrowserActionResult(text = "Viewport reset to default (${w}x$h)")
        }
        val w = input.viewportWidth
        val h = input.viewportHeight
        if (w == null || h == null || w <= 0 || h <= 0) {
            return@withContext BrowserActionResult.error("set_viewport requires positive viewport_width and viewport_height, or reset=true")
        }
        setSessionViewport(w, h)
        BrowserActionResult(text = "Viewport set to ${w}x$h (session override)")
    }

    // -- Disk Persistence --

    private fun saveState() {
        val sid = activeSessionId ?: return
        if (currentBrowserSettings.incognitoMode) return
        try {
            val dir = File(context.filesDir, "browser_tabs").apply { mkdirs() }
            val file = File(dir, "$sid.json")
            val json = JSONObject()
            val urlsJson = JSONObject()
            for (tab in _tabs.value) {
                val url = tab.manager.currentURL.value
                if (url.isNotEmpty()) urlsJson.put(tab.id.toString(), url)
            }
            for ((id, url) in savedURLs) {
                urlsJson.put(id.toString(), url)
            }
            json.put("tabURLs", urlsJson)
            json.put("selectedTabId", _selectedTabId.value)
            if (_sessionViewportWidth.value > 0 && _sessionViewportHeight.value > 0) {
                json.put("sessionViewportWidth", _sessionViewportWidth.value)
                json.put("sessionViewportHeight", _sessionViewportHeight.value)
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save tab state: ${e.message}")
        }
    }

    private fun loadSavedState() {
        val sid = activeSessionId ?: return
        try {
            val file = File(context.filesDir, "browser_tabs/$sid.json")
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val urlsJson = json.optJSONObject("tabURLs")
            if (urlsJson != null) {
                val keys = urlsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    savedURLs[key.toInt()] = urlsJson.getString(key)
                }
                _selectedTabId.value = json.optInt("selectedTabId", 0)
            }
            val w = json.optInt("sessionViewportWidth", 0)
            val h = json.optInt("sessionViewportHeight", 0)
            if (w > 0 && h > 0) {
                _sessionViewportWidth.value = w
                _sessionViewportHeight.value = h
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load tab state: ${e.message}")
        }
    }

    private fun updateTabs() {
        _tabs.value = _tabs.value.toList()
        _isAgentBusyFlow.value = isAgentBusy
    }
}
