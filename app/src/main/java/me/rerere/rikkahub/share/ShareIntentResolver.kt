package me.rerere.rikkahub.share

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.text.unescapeHtml
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ChatAttachmentManager
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.createChatTextFile
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.search.SearchService

private const val TAG = "ShareIntentResolver"
private const val MAX_SHARED_WEBPAGE_CHARS = 16_000
private const val SHARE_PERMISSION_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
private val SHARED_URL_REGEX = Regex("""(?i)\b((?:https?://|www\.)[^\s<>()]+)""")

internal const val EXTRA_RESOLVED_SHARE_PAYLOAD = "me.rerere.rikkahub.extra.RESOLVED_SHARE_PAYLOAD"

@Serializable
internal data class ShareAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String? = null,
)

@Serializable
internal data class ResolvedSharePayload(
    val text: String = "",
    val mimeType: String? = null,
    val attachments: List<ShareAttachment> = emptyList(),
) {
    fun hasContent(): Boolean {
        return text.isNotBlank() || attachments.isNotEmpty()
    }

    fun attachmentUris(): List<String> {
        return attachments.map { it.uri }
    }
}

internal data class RawSharePayload(
    val text: String = "",
    val subject: String? = null,
    val mimeType: String? = null,
    val streamUris: List<String> = emptyList(),
) {
    fun hasContent(): Boolean {
        return text.isNotBlank() || !subject.isNullOrBlank() || streamUris.isNotEmpty()
    }
}

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

internal fun buildRawSharePayload(
    text: String?,
    subject: String?,
    mimeType: String?,
    streamUris: List<String>,
): RawSharePayload {
    return RawSharePayload(
        text = text.orEmpty(),
        subject = subject?.takeIf { it.isNotBlank() },
        mimeType = mimeType?.takeIf { it.isNotBlank() },
        streamUris = streamUris
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    )
}

internal fun buildResolvedShareText(text: String, subject: String?): String {
    return text.ifBlank { subject.orEmpty() }
}

internal fun combineSharedStreamUris(
    singleStreamUri: String?,
    multipleStreamUris: List<String>,
    clipDataUris: List<String>,
): List<String> {
    return buildList {
        singleStreamUri?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(multipleStreamUris.filter { it.isNotBlank() })
        addAll(clipDataUris.filter { it.isNotBlank() })
    }.distinct()
}

internal fun Intent?.toRawSharePayload(): RawSharePayload {
    if (this == null) {
        return RawSharePayload()
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
    val streamUris = runCatching {
        val clipDataUris = buildList {
            clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.toString()?.let(::add)
                }
            }
        }
        combineSharedStreamUris(
            singleStreamUri = runCatching {
                @Suppress("DEPRECATION")
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
                    ?: getStringExtra(Intent.EXTRA_STREAM)
            }.getOrNull(),
            multipleStreamUris = runCatching {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.map(Uri::toString)
                    .orEmpty()
            }.getOrDefault(emptyList()),
            clipDataUris = clipDataUris
        )
    }.getOrElse {
        android.util.Log.w(TAG, "Failed to parse shared stream extras", it)
        emptyList()
    }

    return buildRawSharePayload(
        text = sharedText,
        subject = sharedSubject,
        mimeType = type,
        streamUris = streamUris
    )
}

internal suspend fun resolveSharePayload(
    context: android.content.Context,
    settingsStore: SettingsStore,
    httpClient: PlatformHttpClient,
    rawSharePayload: RawSharePayload,
): ResolvedSharePayload = withContext(Dispatchers.IO) {
    val copiedAttachments = rawSharePayload.streamUris.mapNotNull { rawUri ->
        context.copyShareAttachment(rawUri, rawSharePayload.mimeType)
    }
    val scrapedWebsiteAttachment = if (copiedAttachments.isEmpty()) {
        scrapeWebsiteShare(context, settingsStore, httpClient, rawSharePayload)
    } else {
        null
    }
    val baseText = buildResolvedShareText(rawSharePayload.text, rawSharePayload.subject)

    if (scrapedWebsiteAttachment == null) {
        ResolvedSharePayload(
            text = baseText,
            mimeType = rawSharePayload.mimeType,
            attachments = copiedAttachments
        )
    } else {
        ResolvedSharePayload(
            text = scrapedWebsiteAttachment.first,
            mimeType = rawSharePayload.mimeType,
            attachments = copiedAttachments + scrapedWebsiteAttachment.second
        )
    }
}

internal fun Intent.putResolvedSharePayload(payload: ResolvedSharePayload) {
    putExtra(EXTRA_RESOLVED_SHARE_PAYLOAD, JsonInstant.encodeToString(payload))
}

internal fun Intent.readResolvedSharePayload(): ResolvedSharePayload? {
    val rawPayload = getStringExtra(EXTRA_RESOLVED_SHARE_PAYLOAD) ?: return null
    return runCatching {
        JsonInstant.decodeFromString<ResolvedSharePayload>(rawPayload)
    }.getOrElse {
        android.util.Log.w(TAG, "Failed to decode resolved share payload", it)
        null
    }
}

internal fun Intent.copyShareGrantFlagsFrom(source: Intent) {
    addFlags(source.flags and SHARE_PERMISSION_FLAGS)
    clipData = source.clipData
}

private suspend fun android.content.Context.copyShareAttachment(
    rawUri: String,
    fallbackMimeType: String?,
): ShareAttachment? {
    val sourceUri = rawUri.toUri()
    val copiedUri = ChatAttachmentManager.importChatFile(
        uri = sourceUri,
        fileNameHint = getFileNameFromUri(sourceUri),
        mimeHint = getFileMimeType(sourceUri) ?: fallbackMimeType,
    )?.uri ?: return null
    return ShareAttachment(
        uri = copiedUri.toString(),
        fileName = getFileNameFromUri(sourceUri) ?: "file",
        mimeType = getFileMimeType(sourceUri) ?: fallbackMimeType
    )
}

private suspend fun scrapeWebsiteShare(
    context: android.content.Context,
    settingsStore: SettingsStore,
    httpClient: PlatformHttpClient,
    rawSharePayload: RawSharePayload,
): Pair<String, ShareAttachment>? {
    val sourceText = buildResolvedShareText(rawSharePayload.text, rawSharePayload.subject)
    val sharedUrl = findSharedUrlMatch(sourceText) ?: return null
    val scrapedPage = scrapeWebsiteContent(settingsStore, httpClient, sharedUrl.normalized) ?: return null
    val cleanedText = sourceText
        .replace(sharedUrl.raw, "")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")
    val fileName = buildSharedWebpageFileName(
        title = scrapedPage.title,
        url = scrapedPage.url
    )
    val localFile = context.createChatTextFile(
        fileName = fileName,
        content = buildSharedWebpageDocument(scrapedPage)
    )
    return cleanedText to ShareAttachment(
        uri = localFile.toString(),
        fileName = fileName,
        mimeType = "text/markdown"
    )
}

private suspend fun scrapeWebsiteContent(
    settingsStore: SettingsStore,
    httpClient: PlatformHttpClient,
    url: String,
): ScrapedWebsiteContent? {
    val settings = settingsStore.settingsFlow.value
    val selectedSearchOptions = settings.searchServices.getOrElse(
        index = settings.searchServiceSelected,
        defaultValue = { me.rerere.search.SearchServiceOptions.DEFAULT }
    )
    val searchService = SearchService.getService(selectedSearchOptions)

    if (searchService.scrapingParameters != null) {
        runCatching {
            val scrapedResult = searchService.scrape(
                params = kotlinx.serialization.json.buildJsonObject {
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
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                )
            )
        )
        if (response.statusCode !in 200..299) return@runCatching null
        val html = response.body.decodeToString()
        val description = extractHtmlMetaContent(
            html = html,
            keys = setOf("description", "og:description"),
        )
        val textContent = extractReadableHtmlText(html)

        if (textContent.isBlank()) {
            null
        } else {
            ScrapedWebsiteContent(
                url = url,
                title = extractHtmlTitle(html),
                description = description,
                content = limitSharedWebpageContent(textContent)
            )
        }
    }.getOrElse {
        android.util.Log.w(TAG, "Fallback scrape failed for shared URL: $url", it)
        null
    }
}

internal fun extractHtmlTitle(html: String): String? {
    return Regex("""(?is)<title[^>]*>(.*?)</title>""")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanHtmlText()
        ?.takeIf { it.isNotBlank() }
}

internal fun extractHtmlMetaContent(html: String, keys: Set<String>): String? {
    val metaTagRegex = Regex("""(?is)<meta\b[^>]*>""")
    return metaTagRegex.findAll(html)
        .map { it.value }
        .firstNotNullOfOrNull { tag ->
            val name = tag.htmlAttribute("name")
                ?: tag.htmlAttribute("property")
                ?: return@firstNotNullOfOrNull null
            if (name.lowercase() !in keys) return@firstNotNullOfOrNull null
            tag.htmlAttribute("content")
                ?.cleanHtmlText()
                ?.takeIf { it.isNotBlank() }
        }
}

internal fun extractReadableHtmlText(html: String): String {
    val body = Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?: html
    return body
        .replace(Regex("""(?is)<(script|style|noscript|svg)\b.*?</\1>"""), " ")
        .replace(Regex("""(?is)</?(h[1-6]|p|li|pre|blockquote|br|div|section|article|main)\b[^>]*>"""), "\n")
        .replace(Regex("""(?is)<[^>]+>"""), " ")
        .cleanHtmlText()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n\n")
}

private fun String.htmlAttribute(name: String): String? {
    val quoted = Regex("""(?is)\b${Regex.escape(name)}\s*=\s*(['"])(.*?)\1""")
        .find(this)
        ?.groupValues
        ?.getOrNull(2)
    if (quoted != null) return quoted

    return Regex("""(?is)\b${Regex.escape(name)}\s*=\s*([^\s>]+)""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
}

private fun String.cleanHtmlText(): String {
    return unescapeHtml()
        .replace('\u00A0', ' ')
        .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
        .trim()
}

private fun findSharedUrlMatch(text: String): SharedUrlMatch? {
    val rawMatch = SHARED_URL_REGEX.find(text)?.groupValues?.getOrNull(1)
        ?.trimEnd('.', ',', ';', ':', ')', ']', '>', '"', '\'')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val normalized = if (
        rawMatch.startsWith("http://", ignoreCase = true) ||
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
