package me.rerere.rikkahub.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.data.datastore.BrowserSettings
import me.rerere.rikkahub.data.datastore.SetOfMarkMode
import me.rerere.rikkahub.ui.browser.BrowserExternalSchemeHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

/**
 * Manages a single Android WebView for browser automation and live interactive browsing.
 */
class BrowserUseManager(
    val webView: WebView,
    profile: UserAgentProfile = UserAgentProfile.DESKTOP_CHROME,
    private val conversationIdProvider: () -> String? = { null },
    private val settingsProvider: () -> BrowserSettings = { BrowserSettings() },
    private val secretsStore: BrowserSecretsStore? = null,
    private val modelIdProvider: () -> String? = { null },
    private val screenshotsDirProvider: () -> File = {
        File(webView.context.filesDir, "browser_sessions/default").apply { mkdirs() }
    },
) {
    companion object {
        private const val TAG = "BrowserUseManager"
        private const val NAVIGATION_TIMEOUT_MS = 30_000L
        private const val DEFAULT_DOM_STABLE_TIMEOUT_MS = 10_000
        private const val MIN_DOM_STABLE_TIMEOUT_MS = 1_000
        private const val MAX_DOM_STABLE_TIMEOUT_MS = 60_000
        private const val MAX_FULL_PAGE_HEIGHT_PX = 32768
        private const val BLANK_PAGE_HTML =
            "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width\"></head><body></body></html>"

        @SuppressLint("SetJavaScriptEnabled")
        fun configureWebView(webView: WebView, profile: UserAgentProfile, customUA: String? = null) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportMultipleWindows(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                val ua = customUA?.takeIf { it.isNotBlank() } ?: profile.userAgentString
                if (ua != null) userAgentString = ua
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }

    private val _currentURL = MutableStateFlow("")
    val currentURL: StateFlow<String> = _currentURL.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val dialogQueue = InterceptedDialogQueue()

    var currentProfile: UserAgentProfile = profile
        private set

    var onNewWindow: ((Message) -> Unit)? = null
    var onCloseWindow: (() -> Unit)? = null
    var onDownloadStart: ((url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long) -> Unit)? = null
    var onBlobDownloadData: ((data: ByteArray, filename: String, mimeType: String?) -> Unit)? = null
    var onHandoffRequested: ((url: String) -> Unit)? = null

    private var navigationDeferred: CompletableDeferred<Unit>? = null
    private var asyncJsDeferred: CompletableDeferred<String>? = null
    private var lastKnownContainerWidthPx: Int = 0
    private var lastAppliedViewport: Pair<Int, Int>? = null
    @Volatile private var agentActionActive: Boolean = false

    private val jsBridge = object {
        @JavascriptInterface
        fun resolve(result: String) {
            asyncJsDeferred?.complete(result)
        }

        @JavascriptInterface
        fun reject(error: String) {
            asyncJsDeferred?.complete("{\"error\":${JSONObject.quote(error)}}")
        }

        @JavascriptInterface
        fun saveBlobDownload(dataUrl: String, filename: String) {
            val comma = dataUrl.indexOf(',')
            if (comma < 0 || !dataUrl.startsWith("data:")) return
            val header = dataUrl.substring(5, comma)
            val mime = header.substringBefore(';').ifEmpty { null }
            val bytes = try {
                if (header.endsWith(";base64")) {
                    Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                } else {
                    java.net.URLDecoder.decode(dataUrl.substring(comma + 1), "UTF-8").toByteArray(Charsets.UTF_8)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "blob decode failed: ${t.message}")
                return
            }
            onBlobDownloadData?.invoke(bytes, filename, mime)
        }

        @JavascriptInterface
        fun blobDownloadError(error: String) {
            Log.w(TAG, "blob download failed: $error")
        }
    }

    init {
        configureWebView(webView, profile, settingsProvider().customUserAgent)
        webView.addJavascriptInterface(jsBridge, "__lastchat__")
        setupWebViewClient()
        setupWebChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            when {
                url.startsWith("blob:") -> fetchBlobDownload(url, contentDisposition, mimetype)
                url.startsWith("data:") -> {
                    val name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    jsBridge.saveBlobDownload(url, name)
                }
                else -> onDownloadStart?.invoke(url, userAgent, contentDisposition, mimetype, contentLength)
            }
        }
        webView.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = r - l
            if (w > 0 && w != lastKnownContainerWidthPx) {
                lastKnownContainerWidthPx = w
                lastAppliedViewport?.let { (vw, _) -> applyShrinkToFit(vw) }
            }
        }
    }

    private fun fetchBlobDownload(blobUrl: String, contentDisposition: String?, mimeType: String?) {
        val guessedName = android.webkit.URLUtil.guessFileName(blobUrl, contentDisposition, mimeType)
        val js = """
            (function() {
                fetch(${JSONObject.quote(blobUrl)})
                    .then(function(r) { return r.blob(); })
                    .then(function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            __lastchat__.saveBlobDownload(reader.result, ${JSONObject.quote(guessedName)});
                        };
                        reader.onerror = function() { __lastchat__.blobDownloadError('FileReader error'); };
                        reader.readAsDataURL(blob);
                    })
                    .catch(function(e) { __lastchat__.blobDownloadError(String(e)); });
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun applyShrinkToFit(cssWidth: Int) {
        val containerPx = lastKnownContainerWidthPx
        if (containerPx <= 0 || cssWidth <= 0) return
        val density = webView.resources.displayMetrics.density
        val cssWidthPx = (cssWidth * density).toInt()
        var scalePct = if (cssWidthPx > containerPx) {
            ((containerPx.toLong() * 100) / cssWidthPx).toInt().coerceAtLeast(1)
        } else {
            0
        }
        if (scalePct in 1..9) scalePct = 0
        webView.setInitialScale(scalePct)
    }

    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val urlStr = request.url?.toString()
                if (BrowserGuard.isDomainBlocked(urlStr, settingsProvider().blockedDomains)) {
                    Log.i(TAG, "Blocked navigation to $urlStr by domain blocklist")
                    return true
                }
                if (!settingsProvider().keepAllLoginsInApp && GoogleAuthRouter.shouldRouteExternally(urlStr)) {
                    if (urlStr != null) {
                        GoogleAuthRouter.openInCustomTab(view.context, urlStr)
                    }
                    return true
                }
                return BrowserExternalSchemeHandler.handle(
                    view.context,
                    request.url,
                    BrowserExternalSchemeHandler.Origin.AGENT_BACKGROUND,
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                _isLoading.value = true
                if (!url.isNullOrBlank()) _currentURL.value = url
            }

            override fun onPageFinished(view: WebView, url: String?) {
                _isLoading.value = false
                _progress.value = 100
                _currentURL.value = url ?: ""
                _pageTitle.value = view.title ?: ""
                _canGoBack.value = view.canGoBack()
                _canGoForward.value = view.canGoForward()
                navigationDeferred?.complete(Unit)
                navigationDeferred = null

                val histUrl = url ?: ""
                val histTitle = view.title ?: ""
                if (histUrl.isNotEmpty() && histUrl != "about:blank" && !settingsProvider().incognitoMode) {
                    BrowserHistoryStore.getInstance(view.context).record(histUrl, histTitle, byAgent = agentActionActive)
                }
                view.postDelayed({
                    view.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                }, 80)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    _isLoading.value = false
                    navigationDeferred?.complete(Unit)
                    navigationDeferred = null
                }
            }
        }
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _progress.value = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                _pageTitle.value = title ?: ""
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                onNewWindow?.invoke(resultMsg)
                return onNewWindow != null
            }

            override fun onCloseWindow(window: WebView) {
                onCloseWindow?.invoke()
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                recordInterceptedDialog("alert", message.orEmpty(), null, "(dismissed)")
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                recordInterceptedDialog("confirm", message.orEmpty(), null, "false")
                result?.cancel()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: android.webkit.JsPromptResult?,
            ): Boolean {
                recordInterceptedDialog("prompt", message.orEmpty(), defaultValue, "null")
                result?.cancel()
                return true
            }
        }
    }

    private fun recordInterceptedDialog(kind: String, message: String, defaultText: String?, defaultResponse: String) {
        val url = _currentURL.value
        dialogQueue.record(kind, message, defaultText, url.ifEmpty { null }, defaultResponse)
    }

    fun drainInterceptedDialogReport(): String? = dialogQueue.drainReport()

    // -- Execute Action --

    suspend fun execute(input: BrowserActionInput): BrowserActionResult {
        val settings = settingsProvider()
        val prevUrl = withContext(Dispatchers.Main) { webView.url }

        // Guard checks for click & type
        val gateError = checkGuard(input, settings)
        if (gateError != null) return redactIfNeeded(gateError, settings)

        var result: BrowserActionResult = when (input.action) {
            BrowserAction.NAVIGATE -> navigate(input.url, settings)
            BrowserAction.SCREENSHOT -> screenshot(fullPage = input.fullPage, settings = settings)
            BrowserAction.CLICK -> click(input.selector, input.coordinateX, input.coordinateY, settings)
            BrowserAction.TYPE -> type(input.selector, input.text)
            BrowserAction.GET_TEXT -> getText(input.selector)
            BrowserAction.SCROLL -> scroll(input.selector, input.direction, input.amount)
            BrowserAction.GET_PAGE_INFO -> getPageInfo()
            BrowserAction.EXECUTE_JS -> executeJS(input.script)
            BrowserAction.FIND_ELEMENTS -> findElements(input.selector)
            BrowserAction.HOVER -> hover(input.selector)
            BrowserAction.GET_READABLE -> getReadable()
            BrowserAction.SET_USER_AGENT -> setUserAgent(input.userAgent, input.customUserAgent)
            BrowserAction.GET_BACKBONE -> getBackbone(input.maxDepth)
            BrowserAction.FETCH -> fetch(input.url, settings)
            BrowserAction.GET_COOKIES -> getCookies(input.keywords, input.fuzzy)
            BrowserAction.SET_COOKIES -> setCookies(input.cookies)
            BrowserAction.SCROLL_AND_COLLECT -> scrollAndCollect(input.scrollCount, input.itemSelector, input.keywords)
            BrowserAction.WAIT_FOR_DOM_STABLE -> waitForDomStable(input.timeoutMs)
            BrowserAction.SET_VIEWPORT,
            BrowserAction.NEW_TAB,
            BrowserAction.CLOSE_TAB,
            BrowserAction.LIST_TABS,
            BrowserAction.HAND_OVER,
            BrowserAction.SAVE_SECRET,
            BrowserAction.SAVE_COOKIES_AS_SECRET,
            BrowserAction.RESTORE_COOKIES_FROM_SECRET,
            BrowserAction.CLOSE_BROWSER ->
                BrowserActionResult.error("${input.action.value} must be routed through BrowserTabPool")
        }

        if (result.success && BrowserAction.visualChangeActions.contains(input.action)) {
            result = attachSnapshot(result, settings)
            val newUrl = withContext(Dispatchers.Main) { webView.url }
            if (prevUrl != null && newUrl != null) {
                val prevNoHash = prevUrl.substringBefore("#")
                val curNoHash = newUrl.substringBefore("#")
                if (prevNoHash != curNoHash) {
                    result = result.copy(
                        text = result.text + "\n[URL Changed] Page navigated: $prevUrl -> $newUrl. Take a screenshot to see the current state before continuing."
                    )
                }
            }
        }

        return redactIfNeeded(result, settings)
    }

    private fun redactIfNeeded(result: BrowserActionResult, settings: BrowserSettings): BrowserActionResult {
        if (!settings.redactSecretsInToolOutput || secretsStore == null) return result
        val (masked, _) = BrowserSecretRedactor.redactIfEnabled(result.text, true, secretsStore)
        return if (masked === result.text) result else result.copy(text = masked)
    }

    private suspend fun checkGuard(input: BrowserActionInput, settings: BrowserSettings): BrowserActionResult? {
        if (input.action != BrowserAction.CLICK && input.action != BrowserAction.TYPE) return null
        val described = runCatching {
            evaluateJavascript(BrowserGuard.describeJs(input.selector, input.coordinateX, input.coordinateY))
        }.getOrNull() ?: return null
        val target = BrowserGuard.Target.parse(described) ?: return null
        val verdict = if (input.action == BrowserAction.TYPE) {
            BrowserGuard.judgeType(target, settings)
        } else {
            BrowserGuard.judgeClick(target, settings)
        }
        return when (verdict) {
            is BrowserGuard.Verdict.Proceed -> null
            is BrowserGuard.Verdict.Refuse -> {
                val url = target.url.ifBlank { withContext(Dispatchers.Main) { webView.url }.orEmpty() }
                onHandoffRequested?.invoke(url)
                BrowserActionResult.error(verdict.text).copy(
                    pageURL = url,
                    handoffRequested = true,
                )
            }
            is BrowserGuard.Verdict.Ask -> {
                val convId = conversationIdProvider().orEmpty()
                val approved = BrowserRiskGate.requestApproval(
                    conversationId = convId,
                    kind = BrowserRiskGate.Kind.RISKY_CLICK,
                    title = "Confirm browser tap",
                    summary = verdict.reason,
                    detail = target.url.ifBlank { null },
                )
                if (approved) null
                else BrowserActionResult.error(
                    "The user denied this tap (${verdict.reason}). Do not retry or work around it; ask how they want to proceed."
                )
            }
        }
    }

    private suspend fun attachSnapshot(result: BrowserActionResult, settings: BrowserSettings): BrowserActionResult {
        return try {
            awaitVisualPaint(timeoutMs = 500L)
            delay(300)
            var bitmap = captureWebViewBitmap() ?: return result
            if (isMostlyUniformColor(bitmap)) {
                bitmap.recycle()
                delay(650)
                awaitVisualPaint(timeoutMs = 400L)
                bitmap = captureWebViewBitmap() ?: return result
            }
            val scaled = scaleToMaxLongEdge(bitmap, settings.screenshotMaxLongEdgePx)
            val file = saveBitmapToFile(scaled, "snapshot", settings.screenshotJpegQuality)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            result.copy(imageFilePath = file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Auto-snapshot failed: ${e.message}")
            result
        }
    }

    private suspend fun awaitVisualPaint(timeoutMs: Long) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        val deferred = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            runCatching {
                webView.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        deferred.complete(Unit)
                    }
                })
            }.onFailure { deferred.complete(Unit) }
        }
        withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    private fun isMostlyUniformColor(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return false
        val first = bitmap.getPixel(w / 7, h / 7)
        var same = 0
        var total = 0
        for (gx in 1..6) {
            val px = (w * gx) / 7
            for (gy in 1..6) {
                val py = (h * gy) / 7
                total++
                if (bitmap.getPixel(px, py) == first) same++
            }
        }
        return total > 0 && same >= (total * 95) / 100
    }

    // -- Navigate --

    private suspend fun navigate(urlString: String?, settings: BrowserSettings): BrowserActionResult {
        if (urlString.isNullOrBlank()) return BrowserActionResult.error("Missing 'url' parameter")

        var normalized = urlString.trim()
        if (!normalized.contains("://")) normalized = "https://$normalized"

        if (BrowserGuard.isDomainBlocked(normalized, settings.blockedDomains)) {
            return BrowserActionResult.error("Blocked domain: '$normalized' matches browserSettings.blockedDomains.")
        }

        val deferred = CompletableDeferred<Unit>()
        navigationDeferred = deferred
        _isLoading.value = true

        withContext(Dispatchers.Main) {
            lastAppliedViewport?.let { (w, h) -> applyViewport(w, h) }
            webView.loadUrl(normalized)
        }

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (navigationDeferred === deferred) {
                Log.w(TAG, "Navigation timed out for $normalized")
                _isLoading.value = false
                deferred.complete(Unit)
                navigationDeferred = null
            }
        }
        handler.postDelayed(timeoutRunnable, NAVIGATION_TIMEOUT_MS)

        try {
            deferred.await()
        } finally {
            handler.removeCallbacks(timeoutRunnable)
        }

        _currentURL.value = _currentURL.value.ifEmpty { normalized }
        _isLoading.value = false
        return BrowserActionResult(text = navigationMetadata())
    }

    private suspend fun navigationMetadata(): String {
        val url = _currentURL.value
        val title = _pageTitle.value
        val scrollInfo = evaluateJavascript(
            "JSON.stringify({sx:window.scrollX||0,sy:window.scrollY||0,pw:document.documentElement.scrollWidth||0,ph:document.documentElement.scrollHeight||0,vw:window.innerWidth||0,vh:window.innerHeight||0})"
        )
        var scrollX = 0; var scrollY = 0; var pageW = 0; var pageH = 0
        var vpW = 0; var vpH = 0
        runCatching {
            val info = JSONObject(scrollInfo)
            scrollX = info.optInt("sx"); scrollY = info.optInt("sy")
            pageW = info.optInt("pw"); pageH = info.optInt("ph")
            vpW = info.optInt("vw"); vpH = info.optInt("vh")
        }
        val fallback = currentProfile.viewportSize
        val effectiveVpW = if (vpW > 0) vpW else fallback.first
        val effectiveVpH = if (vpH > 0) vpH else fallback.second

        return buildString {
            appendLine("Navigated to $url")
            if (title.isNotEmpty()) appendLine("  Title: $title")
            appendLine("  Viewport: ${effectiveVpW}x$effectiveVpH")
            if (pageW > 0 || pageH > 0) appendLine("  Page size: ${pageW}x$pageH")
            append("  Scroll position: ($scrollX, $scrollY)")
        }
    }

    // -- Screenshot --

    private fun shouldInjectSetOfMark(settings: BrowserSettings): Boolean = when (settings.setOfMarkMode) {
        SetOfMarkMode.ALWAYS -> true
        SetOfMarkMode.OFF -> false
        SetOfMarkMode.AUTO_SMALL_MODELS -> {
            val id = modelIdProvider()?.lowercase().orEmpty()
            id.contains("mini") || id.contains("nano") || id.contains("flash") ||
                id.contains("haiku") || id.contains("small") || id.contains("3b") ||
                id.contains("4b") || id.contains("7b") || id.contains("8b") || id.contains("local")
        }
    }

    private suspend fun screenshot(fullPage: Boolean, settings: BrowserSettings): BrowserActionResult {
        var truncated = false
        var originalHeightPx = 0
        var didStretch = false
        var savedW = 0
        var savedH = 0
        val useSom = shouldInjectSetOfMark(settings)

        if (useSom) {
            withContext(Dispatchers.Main) { webView.evaluateJavascript(BrowserUseJS.setOfMarkInjectJs(), null) }
            delay(40)
        }

        try {
            if (fullPage) {
                val cssScrollHeight = evaluateJavascript("document.documentElement.scrollHeight").trim().toIntOrNull() ?: 0
                val density = webView.resources.displayMetrics.density
                val scrollHeightPx = if (cssScrollHeight > 0) (cssScrollHeight * density).toInt() else withContext(Dispatchers.Main) { webView.height }
                originalHeightPx = scrollHeightPx
                val cappedPx = scrollHeightPx.coerceAtMost(MAX_FULL_PAGE_HEIGHT_PX)
                truncated = scrollHeightPx > MAX_FULL_PAGE_HEIGHT_PX

                runCatching {
                    evaluateJavascript(
                        "(async()=>{document.querySelectorAll('img[loading=\"lazy\"]').forEach(i=>i.loading='eager');await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return 'ok';})()"
                    )
                }
                delay(50)

                val applied = lastAppliedViewport ?: currentProfile.viewportSize
                savedW = applied.first
                savedH = applied.second
                val cssCappedHeight = (cappedPx / density).toInt().coerceAtLeast(savedH)
                withContext(Dispatchers.Main) { applyViewport(savedW, cssCappedHeight) }
                didStretch = true
            }

            val rawBitmap = try {
                captureWebViewBitmap()
            } finally {
                if (didStretch) {
                    withContext(Dispatchers.Main) { applyViewport(savedW, savedH) }
                }
            } ?: return BrowserActionResult.error("Failed to capture screenshot")

            val scaledBitmap = scaleToMaxLongEdge(rawBitmap, settings.screenshotMaxLongEdgePx)
            val quality = settings.screenshotJpegQuality.coerceIn(60, 90)
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val jpegBytes = out.toByteArray()
            val file = saveBitmapToFile(scaledBitmap, "screenshot", quality)
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val w = scaledBitmap.width
            val h = scaledBitmap.height
            if (scaledBitmap !== rawBitmap) scaledBitmap.recycle()
            rawBitmap.recycle()

            val meta = viewportMetadata(
                imageW = w,
                imageH = h,
                fileSize = jpegBytes.size,
                fullPage = fullPage,
                truncated = truncated,
                originalHeightPx = originalHeightPx,
            )
            return BrowserActionResult(
                text = meta,
                base64Image = base64,
                imageFilePath = file.absolutePath,
            )
        } finally {
            if (useSom) {
                withContext(Dispatchers.Main) { webView.evaluateJavascript(BrowserUseJS.setOfMarkClearJs(), null) }
            }
        }
    }

    private fun scaleToMaxLongEdge(source: Bitmap, maxLongEdgePx: Int): Bitmap {
        val cap = maxLongEdgePx.coerceIn(720, 2048)
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= cap || longEdge <= 0) return source
        val scale = cap.toFloat() / longEdge.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(source, w, h, true) }.getOrDefault(source)
    }

    private suspend fun viewportMetadata(
        imageW: Int,
        imageH: Int,
        fileSize: Int,
        fullPage: Boolean = false,
        truncated: Boolean = false,
        originalHeightPx: Int = 0,
    ): String {
        val url = _currentURL.value
        val title = _pageTitle.value
        val scrollInfo = evaluateJavascript(
            "JSON.stringify({sx:window.scrollX||0,sy:window.scrollY||0,pw:document.documentElement.scrollWidth||0,ph:document.documentElement.scrollHeight||0,vw:window.innerWidth||0,vh:window.innerHeight||0})"
        )
        var scrollX = 0; var scrollY = 0; var pageW = 0; var pageH = 0
        var vpW = 0; var vpH = 0
        runCatching {
            val info = JSONObject(scrollInfo)
            scrollX = info.optInt("sx"); scrollY = info.optInt("sy")
            pageW = info.optInt("pw"); pageH = info.optInt("ph")
            vpW = info.optInt("vw"); vpH = info.optInt("vh")
        }
        val fallback = currentProfile.viewportSize
        val effectiveVpW = if (vpW > 0) vpW else fallback.first
        val effectiveVpH = if (vpH > 0) vpH else fallback.second

        return buildString {
            appendLine("Screenshot captured")
            appendLine("  URL: $url")
            if (title.isNotEmpty()) appendLine("  Title: $title")
            appendLine("  Image: ${imageW}x$imageH (${fileSize / 1024}KB)")
            appendLine("  Viewport: ${effectiveVpW}x$effectiveVpH")
            if (pageW > 0 || pageH > 0) appendLine("  Page size: ${pageW}x$pageH")
            if (fullPage) {
                appendLine("  Full page: true")
                if (originalHeightPx > 0) appendLine("  Original height: ${originalHeightPx}px")
                if (truncated) appendLine("  Truncated: true (capped at ${MAX_FULL_PAGE_HEIGHT_PX}px)")
            }
            append("  Scroll position: ($scrollX, $scrollY)")
        }
    }

    suspend fun captureLiveSnapshot(maxWidthPx: Int = 480): Bitmap? = withContext(Dispatchers.Main) {
        try {
            val vp = lastAppliedViewport ?: currentProfile.viewportSize
            val density = webView.resources.displayMetrics.density
            var w = webView.width
            var h = webView.height
            if (w <= 0 || h <= 0) {
                val targetW = (vp.first.coerceAtLeast(412) * density).toInt().coerceAtLeast(1)
                val targetH = (vp.second.coerceAtLeast(800) * density).toInt().coerceAtLeast(1)
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY),
                )
                webView.layout(0, 0, targetW, targetH)
                w = targetW; h = targetH
            }
            val scale = if (w > maxWidthPx) maxWidthPx.toFloat() / w.toFloat() else 1f
            val outW = (w * scale).toInt().coerceAtLeast(1)
            val outH = (h * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "captureLiveSnapshot failed: ${e.message}")
            null
        }
    }

    private suspend fun captureWebViewBitmap(): Bitmap? = withContext(Dispatchers.Main) {
        try {
            val vp = lastAppliedViewport ?: currentProfile.viewportSize
            val density = webView.resources.displayMetrics.density
            var w = webView.width
            var h = webView.height
            if (w <= 0 || h <= 0) {
                val targetW = (vp.first.coerceAtLeast(412) * density).toInt().coerceAtLeast(1)
                val targetH = (vp.second.coerceAtLeast(800) * density).toInt().coerceAtLeast(1)
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY),
                )
                webView.layout(0, 0, targetW, targetH)
                w = targetW; h = targetH
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "captureWebViewBitmap failed: ${e.message}")
            null
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, prefix: String, quality: Int): File {
        val dir = screenshotsDirProvider().apply { mkdirs() }
        val filename = "${prefix}_${System.currentTimeMillis()}.jpg"
        val file = File(dir, filename)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 90), out)
        }
        return file
    }

    // -- Interaction --

    private suspend fun click(selector: String?, x: Int?, y: Int?, settings: BrowserSettings): BrowserActionResult {
        if (settings.showAgentTapRipple) {
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(BrowserUseJS.showTapRippleJs(selector, x, y), null)
            }
        }
        val js = when {
            selector != null -> BrowserUseJS.click(selector)
            x != null && y != null -> BrowserUseJS.clickCoordinate(x, y)
            else -> return BrowserActionResult.error("click requires 'selector' or 'coordinate_x'/'coordinate_y'")
        }
        return evaluateAndReturn(js)
    }

    private suspend fun type(selector: String?, rawText: String?): BrowserActionResult {
        if (selector == null) return BrowserActionResult.error("type requires 'selector'")
        if (rawText == null) return BrowserActionResult.error("type requires 'text'")
        val resolvedText = try {
            secretsStore?.resolvePlaceholders(rawText) ?: rawText
        } catch (e: IllegalArgumentException) {
            return BrowserActionResult.error(e.message ?: "Unknown secret placeholder")
        }
        return evaluateAndReturn(BrowserUseJS.type(selector, resolvedText))
    }

    private suspend fun getText(selector: String?): BrowserActionResult =
        evaluateJSAndParse(BrowserUseJS.getText(selector))

    private suspend fun getReadable(): BrowserActionResult =
        evaluateJSAndParse(BrowserUseJS.getReadable())

    private suspend fun scroll(selector: String?, direction: ScrollDirection?, amount: Int?): BrowserActionResult {
        val dir = direction ?: ScrollDirection.DOWN
        val px = amount ?: 500
        return evaluateJSAndParse(BrowserUseJS.scroll(dir, px, selector))
    }

    private suspend fun getPageInfo(): BrowserActionResult =
        evaluateAndReturn(BrowserUseJS.getPageInfo())

    private suspend fun hover(selector: String?): BrowserActionResult {
        if (selector == null) return BrowserActionResult.error("hover requires 'selector'")
        return evaluateAndReturn(BrowserUseJS.hover(selector))
    }

    private suspend fun findElements(selector: String?): BrowserActionResult {
        if (selector == null) return BrowserActionResult.error("find_elements requires 'selector'")
        return evaluateAndReturn(BrowserUseJS.findElements(selector))
    }

    private suspend fun getBackbone(maxDepth: Int?): BrowserActionResult {
        val depth = (maxDepth ?: 5).coerceIn(1, 10)
        val raw = evaluateJavascript(BrowserUseJS.getBackbone(depth))
        return try {
            val json = JSONObject(raw)
            if (json.has("error")) BrowserActionResult.error(json.getString("error"))
            else BrowserActionResult(text = formatBackboneResult(json))
        } catch (e: Exception) {
            BrowserActionResult.error("JavaScript error: ${e.message}")
        }
    }

    private suspend fun executeJS(script: String?): BrowserActionResult {
        if (script.isNullOrEmpty()) return BrowserActionResult.error("execute_js requires 'script'")
        return try {
            val deferred = CompletableDeferred<String>()
            asyncJsDeferred = deferred
            val wrapped = """
                (async function(){
                    try {
                        var __r__ = (async function(){ $script })();
                        var __v__ = await __r__;
                        if (__v__ === undefined || __v__ === null) {
                            __lastchat__.resolve(String(__v__));
                        } else if (typeof __v__ === 'object') {
                            __lastchat__.resolve(JSON.stringify(__v__));
                        } else {
                            __lastchat__.resolve(String(__v__));
                        }
                    } catch(e) {
                        __lastchat__.reject(e.message || String(e));
                    }
                })();
            """.trimIndent()
            withContext(Dispatchers.Main) { webView.evaluateJavascript(wrapped, null) }
            val raw = withTimeoutOrNull(30_000L) { deferred.await() }
                ?: run {
                    asyncJsDeferred = null
                    return BrowserActionResult.error("JavaScript execution timed out (30s)")
                }
            asyncJsDeferred = null
            val json = try { JSONObject(raw) } catch (_: Exception) { null }
            if (json != null) {
                if (json.has("error")) BrowserActionResult.error(json.getString("error"))
                else BrowserActionResult(text = formatJSONResult(json))
            } else {
                BrowserActionResult(text = raw)
            }
        } catch (e: Exception) {
            asyncJsDeferred = null
            BrowserActionResult.error("JavaScript error: ${e.message}")
        }
    }

    private suspend fun fetch(urlString: String?, settings: BrowserSettings): BrowserActionResult {
        if (urlString.isNullOrEmpty()) return BrowserActionResult.error("fetch requires 'url' parameter")
        if (BrowserGuard.isDomainBlocked(urlString, settings.blockedDomains)) {
            return BrowserActionResult.error("Blocked domain: '$urlString' matches browserSettings.blockedDomains.")
        }
        val raw = awaitPromiseJs(BrowserUseJS.fetch(urlString))
            ?: return BrowserActionResult.error("fetch timed out")
        return try {
            val json = JSONObject(raw)
            if (json.has("error")) return BrowserActionResult.error("Fetch failed: ${json.getString("error")}")
            val contentType = json.optString("contentType", "")
            val status = json.optInt("status", 0)
            val finalURL = json.optString("url", urlString)
            val data: ByteArray? = json.optString("base64").takeIf { it.isNotEmpty() }?.let {
                runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
            } ?: json.optString("text").takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            if (data == null) return BrowserActionResult.error("Fetch returned no body (status=$status)")
            val size = json.optInt("size", data.size)
            val filename = "fetch_${System.currentTimeMillis()}.${extensionForMimeType(contentType)}"
            val text = buildString {
                appendLine("Fetched $finalURL")
                appendLine("  Status: $status")
                appendLine("  Content-Type: $contentType")
                appendLine("  Size: ${formatBytes(size)}")
                append("  Filename: $filename")
            }
            BrowserActionResult(text = text, fetchedFileData = data, fetchedFileName = filename)
        } catch (e: Exception) {
            BrowserActionResult.error("Fetch parse error: ${e.message}")
        }
    }

    private suspend fun awaitPromiseJs(js: String): String? {
        val deferred = CompletableDeferred<String>()
        asyncJsDeferred = deferred
        val wrapped = """
            (async function(){
                try {
                    var __v__ = await ($js);
                    if (__v__ === undefined || __v__ === null) {
                        __lastchat__.resolve('null');
                    } else if (typeof __v__ === 'object') {
                        __lastchat__.resolve(JSON.stringify(__v__));
                    } else {
                        __lastchat__.resolve(String(__v__));
                    }
                } catch(e) {
                    __lastchat__.reject(e && e.message ? e.message : String(e));
                }
            })();
        """.trimIndent()
        withContext(Dispatchers.Main) { webView.evaluateJavascript(wrapped, null) }
        val raw = withTimeoutOrNull(60_000L) { deferred.await() }
        asyncJsDeferred = null
        return raw
    }

    // -- User Agent & Viewport --

    fun setUserAgent(profile: UserAgentProfile, customUA: String? = null) {
        currentProfile = profile
        val ua = if (profile == UserAgentProfile.CUSTOM && !customUA.isNullOrBlank()) customUA
        else profile.userAgentString ?: customUA?.takeIf { it.isNotBlank() }
        if (ua != null) webView.settings.userAgentString = ua
        applyViewport()
        if (_currentURL.value.isNotEmpty()) webView.reload()
    }

    private suspend fun setUserAgent(profile: UserAgentProfile?, customUA: String?): BrowserActionResult {
        val newProfile = profile ?: UserAgentProfile.DESKTOP_CHROME
        currentProfile = newProfile
        val ua = if (newProfile == UserAgentProfile.CUSTOM) customUA else newProfile.userAgentString
        withContext(Dispatchers.Main) {
            if (!ua.isNullOrBlank()) webView.settings.userAgentString = ua
            applyViewport()
            if (_currentURL.value.isNotEmpty()) webView.reload()
        }
        val vp = newProfile.viewportSize
        return BrowserActionResult(text = "Switched to ${newProfile.value} (${vp.first}x${vp.second})")
    }

    fun applyViewport() {
        val vp = currentProfile.viewportSize
        applyViewport(vp.first, vp.second)
    }

    fun applyViewport(cssWidth: Int, cssHeight: Int) {
        if (cssWidth <= 0 || cssHeight <= 0) {
            lastAppliedViewport = null
            webView.setInitialScale(0)
            return
        }
        val density = webView.resources.displayMetrics.density
        val w = (cssWidth * density).toInt().coerceAtLeast(1)
        val h = (cssHeight * density).toInt().coerceAtLeast(1)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, w, h)
        lastAppliedViewport = cssWidth to cssHeight
        applyShrinkToFit(cssWidth)
    }

    fun goBack() { if (webView.canGoBack()) webView.goBack() }
    fun goForward() { if (webView.canGoForward()) webView.goForward() }
    fun reload() { webView.reload() }
    fun stopLoading() { webView.stopLoading(); _isLoading.value = false }

    suspend fun reloadAndWait() {
        val deferred = CompletableDeferred<Unit>()
        navigationDeferred = deferred
        _isLoading.value = true
        val url = _currentURL.value
        if (url.isEmpty() || url == "about:blank") {
            webView.loadDataWithBaseURL(null, BLANK_PAGE_HTML, "text/html", "utf-8", null)
        } else {
            webView.reload()
        }
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (navigationDeferred === deferred) {
                _isLoading.value = false
                deferred.complete(Unit)
                navigationDeferred = null
            }
        }
        handler.postDelayed(timeoutRunnable, NAVIGATION_TIMEOUT_MS)
        try { deferred.await() } finally { handler.removeCallbacks(timeoutRunnable) }
        _isLoading.value = false
    }

    suspend fun loadBlankPage() {
        val deferred = CompletableDeferred<Unit>()
        navigationDeferred = deferred
        _isLoading.value = true
        webView.loadDataWithBaseURL(null, BLANK_PAGE_HTML, "text/html", "utf-8", null)
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (navigationDeferred === deferred) {
                _isLoading.value = false
                deferred.complete(Unit)
                navigationDeferred = null
            }
        }
        handler.postDelayed(timeoutRunnable, NAVIGATION_TIMEOUT_MS)
        try { deferred.await() } finally { handler.removeCallbacks(timeoutRunnable) }
        _isLoading.value = false
    }

    fun loadURL(urlString: String) {
        var normalized = urlString.trim()
        if (!normalized.contains("://")) normalized = "https://$normalized"
        if (BrowserGuard.isDomainBlocked(normalized, settingsProvider().blockedDomains)) return
        _isLoading.value = true
        webView.loadUrl(normalized)
    }

    // -- Cookies --

    fun extractCookiesForCurrentPage(keywords: List<String>?, fuzzy: Boolean): Result<Pair<String, List<Pair<String, String>>>> {
        val url = _currentURL.value.takeIf { it.isNotEmpty() }
            ?: return Result.failure(IllegalStateException("No page is loaded (navigate first)"))
        val cookieMgr = runCatching { CookieManager.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("CookieManager unavailable"))
        val raw = cookieMgr.getCookie(url).orEmpty()
        if (raw.isEmpty()) return Result.success(url to emptyList())
        val pairs = raw.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull {
                val eq = it.indexOf('=')
                if (eq <= 0) null else it.substring(0, eq).trim() to it.substring(eq + 1).trim()
            }
        val filtered = if (keywords.isNullOrEmpty()) pairs else pairs.filter { (name, _) ->
            keywords.any { kw ->
                if (fuzzy) name.contains(kw, ignoreCase = true)
                else name.equals(kw, ignoreCase = true)
            }
        }
        return Result.success(url to filtered)
    }

    private fun getCookies(keywords: List<String>?, fuzzy: Boolean): BrowserActionResult {
        val res = extractCookiesForCurrentPage(keywords, fuzzy)
        val (url, filtered) = res.getOrElse { return BrowserActionResult.error("get_cookies: ${it.message}") }
        if (filtered.isEmpty()) return BrowserActionResult(text = "No matching cookies set for $url")
        val text = buildString {
            appendLine("Cookies for $url (${filtered.size} matched):")
            for ((name, value) in filtered) {
                val preview = if (value.length > 80) value.take(77) + "…" else value
                appendLine("  $name = $preview")
            }
        }.trimEnd()
        return BrowserActionResult(text = text)
    }

    fun applyCookies(cookies: List<Map<String, Any?>>?, targetUrl: String? = null): BrowserActionResult {
        val url = targetUrl?.takeIf { it.isNotBlank() } ?: _currentURL.value.takeIf { it.isNotEmpty() }
            ?: return BrowserActionResult.error("set_cookies: no page is loaded (navigate first)")
        if (cookies == null) {
            return BrowserActionResult.error(
                "set_cookies: 'cookies' must be a JSON array of {name, value} objects."
            )
        }
        if (cookies.isEmpty()) {
            return BrowserActionResult.error("set_cookies: 'cookies' array is empty.")
        }
        val cookieMgr = runCatching { CookieManager.getInstance() }.getOrNull()
            ?: return BrowserActionResult.error("set_cookies: CookieManager unavailable")
        val defaultDomain = runCatching { URI(url).host }.getOrNull().orEmpty()
        val httpDateFmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }

        val setNames = mutableListOf<String>()
        val domainsTouched = linkedSetOf<String>()
        val failures = mutableListOf<String>()

        for (raw in cookies) {
            val name = cookieString(raw, "name")?.takeIf { it.isNotEmpty() }
            val value = cookieString(raw, "value")
            if (name == null || value == null) {
                failures.add("(missing name/value)")
                continue
            }
            val domain = cookieString(raw, "domain")?.takeIf { it.isNotEmpty() } ?: defaultDomain
            val path = cookieString(raw, "path")?.takeIf { it.isNotEmpty() } ?: "/"

            val sb = StringBuilder()
            sb.append(name).append('=').append(value)
            if (domain.isNotEmpty()) sb.append("; Domain=").append(domain)
            sb.append("; Path=").append(path)
            if (cookieBool(raw, "secure") == true) sb.append("; Secure")
            if (cookieBool(raw, "http_only", "httpOnly") == true) sb.append("; HttpOnly")
            cookieNumber(raw, "expires", "expirationDate")?.takeIf { it > 0 }?.let { expires ->
                val date = java.util.Date(expires.toLong() * 1000L)
                sb.append("; Expires=").append(httpDateFmt.format(date))
            }
            val cookieUrl = if (domain.isNotEmpty()) "https://${domain.trimStart('.')}$path" else url
            cookieMgr.setCookie(cookieUrl, sb.toString())
            setNames.add(name)
            domainsTouched.add(domain)
        }
        cookieMgr.flush()

        if (setNames.isEmpty()) {
            return BrowserActionResult.error("set_cookies: no valid cookies (${failures.joinToString(", ")})")
        }
        val domainLabel = if (domainsTouched.size == 1) domainsTouched.first() else domainsTouched.joinToString(", ")
        var text = "Set ${setNames.size} cookie(s) for $domainLabel: ${setNames.joinToString(", ")}"
        if (failures.isNotEmpty()) text += "\nSkipped ${failures.size} invalid entry(ies)"
        return BrowserActionResult(text = text)
    }

    private fun setCookies(cookies: List<Map<String, Any?>>?): BrowserActionResult = applyCookies(cookies)

    private fun cookieValue(raw: Map<String, Any?>, vararg aliases: String): Any? {
        for (key in aliases) raw[key]?.let { return it }
        val lowered = aliases.map { it.lowercase() }.toSet()
        for ((k, v) in raw) if (k.lowercase() in lowered && v != null) return v
        return null
    }

    private fun cookieString(raw: Map<String, Any?>, vararg aliases: String): String? =
        when (val v = cookieValue(raw, *aliases)) {
            is String -> v
            is Number -> v.toString()
            else -> null
        }

    private fun cookieBool(raw: Map<String, Any?>, vararg aliases: String): Boolean? =
        when (val v = cookieValue(raw, *aliases)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.lowercase() in setOf("true", "1", "yes")
            else -> null
        }

    private fun cookieNumber(raw: Map<String, Any?>, vararg aliases: String): Double? =
        when (val v = cookieValue(raw, *aliases)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

    // -- Wait for DOM stable & Scroll and Collect --

    private suspend fun waitForDomStable(timeoutMs: Int?): BrowserActionResult {
        val budget = (timeoutMs ?: DEFAULT_DOM_STABLE_TIMEOUT_MS).coerceIn(
            MIN_DOM_STABLE_TIMEOUT_MS, MAX_DOM_STABLE_TIMEOUT_MS,
        )
        val readyState = evaluateJavascript("(function(){try{return document.readyState;}catch(e){return '';}})()").trim('"')
        if (readyState == "complete") {
            val first = evaluateJavascript("(function(){try{return (document.body&&document.body.innerHTML.length)||0;}catch(e){return -1;}})()").toLongOrNull() ?: -1L
            delay(50)
            val second = evaluateJavascript("(function(){try{return (document.body&&document.body.innerHTML.length)||0;}catch(e){return -1;}})()").toLongOrNull() ?: -1L
            if (first == second && first > 0) {
                return BrowserActionResult(text = "DOM stable immediately (readyState=complete, body length=$first)")
            }
        }
        var lastSize = -1L
        var stable = false
        val deadline = System.currentTimeMillis() + budget
        while (System.currentTimeMillis() < deadline) {
            val size = evaluateJavascript("(function(){try{return (document.body&&document.body.innerHTML.length)||0;}catch(e){return -1;}})()").toLongOrNull() ?: -1L
            if (size == lastSize && size >= 0) { stable = true; break }
            lastSize = size
            delay(200L)
        }
        val elapsed = budget - (deadline - System.currentTimeMillis())
        return if (stable) {
            BrowserActionResult(text = "DOM stable after ${elapsed}ms (body length=$lastSize)")
        } else {
            BrowserActionResult(text = "DOM did not stabilize within ${budget}ms (last body length=$lastSize)", success = false)
        }
    }

    private suspend fun scrollAndCollect(scrollCount: Int?, itemSelector: String?, keywords: List<String>?): BrowserActionResult {
        val iterations = (scrollCount ?: 10).coerceIn(1, 20)
        val selector = itemSelector?.takeIf { it.isNotBlank() }
        val collected = LinkedHashSet<String>()

        for (i in 0 until iterations) {
            val js = if (selector != null) {
                val escaped = BrowserUseJS.jsQuote(selector)
                """(function(){try{var nodes=document.querySelectorAll('$escaped');var out=[];for(var i=0;i<nodes.length;i++){var t=(nodes[i].innerText||nodes[i].textContent||'').trim();if(t)out.push(t);}return JSON.stringify(out);}catch(e){return '[]';}})()"""
            } else {
                """(function(){try{var nodes=document.querySelectorAll('article,li,[role=listitem],[role=article],.item,.card,.post');var out=[];for(var i=0;i<nodes.length;i++){var t=(nodes[i].innerText||nodes[i].textContent||'').trim();if(t&&t.length>20)out.push(t);}return JSON.stringify(out);}catch(e){return '[]';}})()"""
            }
            runCatching {
                val arr = JSONArray(evaluateJavascript(js))
                for (j in 0 until arr.length()) {
                    val s = arr.optString(j)
                    if (s.isNotBlank()) collected.add(s)
                }
            }
            evaluateJavascript("window.scrollBy(0, window.innerHeight);")
            delay(400)
        }

        val filtered = if (keywords.isNullOrEmpty()) collected.toList()
        else collected.filter { text -> keywords.any { k -> text.contains(k, ignoreCase = true) } }

        val text = buildString {
            appendLine("scroll_and_collect: $iterations scrolls, selector='${selector ?: "auto"}'")
            appendLine("  matched: ${filtered.size} / ${collected.size} total")
            for ((i, item) in filtered.withIndex()) {
                val preview = if (item.length > 200) item.take(197) + "…" else item
                appendLine("  [${i + 1}] $preview")
            }
        }.trimEnd()
        return BrowserActionResult(text = text)
    }

    // -- JS helpers --

    private suspend fun evaluateJavascript(js: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        webView.evaluateJavascript(js) { result ->
            val unquoted = if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                try { JSONObject("{\"v\":$result}").getString("v") } catch (_: Exception) { result }
            } else {
                result ?: "null"
            }
            deferred.complete(unquoted)
        }
        deferred.await()
    }

    private suspend fun evaluateAndReturn(js: String): BrowserActionResult =
        evaluateJSAndParse(js)

    private suspend fun evaluateJSAndParse(js: String): BrowserActionResult {
        return try {
            val raw = evaluateJavascript(js)
            val json = try { JSONObject(raw) } catch (_: Exception) { null }
            if (json != null) {
                if (json.has("error")) BrowserActionResult.error(json.getString("error"))
                else BrowserActionResult(text = formatJSONResult(json))
            } else {
                BrowserActionResult(text = raw)
            }
        } catch (e: Exception) {
            BrowserActionResult.error("JavaScript error: ${e.message}")
        }
    }

    private fun formatJSONResult(json: JSONObject): String = buildString {
        when {
            json.optBoolean("clicked") -> {
                val tag = json.optString("tag", "?")
                appendLine("Clicked <$tag>")
                if (json.has("x") && json.has("y")) appendLine("  Position: (${json.optInt("x")}, ${json.optInt("y")})")
                val text = json.optString("text", "")
                if (text.isNotEmpty()) append("  Text: ${text.take(200)}")
            }
            json.optBoolean("typed") -> {
                append("Typed ${json.optInt("length", 0)} chars into ${json.optString("selector", "?")}")
            }
            json.optBoolean("scrolled") -> {
                appendLine("Scrolled ${json.optString("direction", "?")} ${json.optInt("amount", 0)}px")
                if (json.has("scrollY")) appendLine("  Scroll Y: ${json.optInt("scrollY")}")
                if (json.has("scrollHeight")) appendLine("  Page height: ${json.optInt("scrollHeight")}")
                if (json.has("viewportHeight")) append("  Viewport height: ${json.optInt("viewportHeight")}")
            }
            json.optBoolean("hovered") -> {
                appendLine("Hovered <${json.optString("tag", "?")}>")
                val text = json.optString("text", "")
                if (text.isNotEmpty()) append("  Text: ${text.take(200)}")
            }
            json.has("text") && json.has("length") -> {
                val title = json.optString("title", "")
                if (title.isNotEmpty()) appendLine("Title: $title")
                val text = json.optString("text", "")
                appendLine("Text (${json.optInt("length", text.length)} chars):")
                append(text.take(10000))
            }
            json.has("count") && json.has("elements") -> {
                val count = json.optInt("count")
                val elements = json.optJSONArray("elements")
                val shown = json.optInt("shown", elements?.length() ?: 0)
                appendLine("Found $count element(s) (showing $shown):")
                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val el = elements.getJSONObject(i)
                        append("  [${el.optInt("index")}] <${el.optString("tag", "?")}>")
                        val id = el.optString("id", "")
                        if (id.isNotEmpty()) append(" #$id")
                        val text = el.optString("text", "").take(80)
                        if (text.isNotEmpty()) append(" \"$text\"")
                        val href = el.optString("href", "")
                        if (href.isNotEmpty()) append(" -> $href")
                        appendLine()
                    }
                }
            }
            else -> {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    appendLine("  $key: ${json.opt(key)}")
                }
            }
        }
    }.trimEnd()

    private fun formatBackboneResult(json: JSONObject): String = buildString {
        appendLine("Page backbone: ${json.optInt("nodeCount")} nodes, depth ${json.optInt("depth")}, ${json.optInt("merged")} merged")
        val backbone = json.optJSONArray("backbone")
        if (backbone != null) {
            for (i in 0 until backbone.length()) {
                formatBackboneNode(backbone.getJSONObject(i), 0, this)
            }
        }
    }.trimEnd()

    private fun formatBackboneNode(node: JSONObject, indent: Int, sb: StringBuilder) {
        val pad = "  ".repeat(indent)
        sb.append("$pad<${node.optString("tag", "?")}>")
        val id = node.optString("id", "")
        if (id.isNotEmpty()) sb.append(" #$id")
        val cls = node.optString("cls", "")
        if (cls.isNotEmpty()) sb.append(" .${cls.replace(" ", ".")}")
        val role = node.optString("role", "")
        if (role.isNotEmpty()) sb.append(" [$role]")
        sb.appendLine(" | ${node.optString("sel", "")} | ${node.optString("rect", "")}")
        node.optString("text", "").takeIf { it.isNotEmpty() }?.let { sb.appendLine("$pad  \"$it\"") }
        node.optString("href", "").takeIf { it.isNotEmpty() }?.let { sb.appendLine("$pad  -> $it") }
        node.optString("img", "").takeIf { it.isNotEmpty() }?.let { sb.appendLine("$pad  img: $it") }
        node.optString("input", "").takeIf { it.isNotEmpty() }?.let { sb.appendLine("$pad  input: $it") }
        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                formatBackboneNode(children.getJSONObject(i), indent + 1, sb)
            }
        }
    }

    private fun extensionForMimeType(mime: String): String {
        val lower = mime.lowercase().split(";").firstOrNull()?.trim() ?: ""
        return when (lower) {
            "text/html" -> "html"; "text/plain" -> "txt"; "text/css" -> "css"; "text/csv" -> "csv"
            "application/json" -> "json"; "application/xml", "text/xml" -> "xml"
            "application/pdf" -> "pdf"; "image/png" -> "png"; "image/jpeg" -> "jpg"
            "image/gif" -> "gif"; "image/webp" -> "webp"; "image/svg+xml" -> "svg"
            "application/zip" -> "zip"; "application/gzip" -> "gz"
            else -> "bin"
        }
    }

    private fun formatBytes(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
