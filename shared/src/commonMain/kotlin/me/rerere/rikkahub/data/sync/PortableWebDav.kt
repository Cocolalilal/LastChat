package me.rerere.rikkahub.data.sync

import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformLog
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class PortableWebDavConfig(
    val url: String,
    val username: String = "",
    val password: String = "",
    val path: String = "lastchat_backups",
)

data class PortableWebDavItem(
    val href: String,
    val displayName: String,
    val contentLength: Long? = null,
)

class PortableWebDavClient(
    private val httpClient: PlatformHttpClient,
) {
    suspend fun test(config: PortableWebDavConfig) {
        ensureCollection(config)
    }

    suspend fun list(config: PortableWebDavConfig): List<PortableWebDavItem> {
        ensureCollection(config)
        val collection = collectionUrl(config)
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent().encodeToByteArray()
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "PROPFIND",
                url = collection,
                headers = authHeaders(config) + mapOf("Depth" to "1"),
                body = body,
                mediaType = "application/xml",
            ),
        )
        require(response.statusCode in 200..299) {
            "WebDAV list failed: HTTP ${response.statusCode} ${response.body.decodeToString().take(300)}"
        }
        return parsePropfind(response.body.decodeToString(), collection)
    }

    suspend fun upload(config: PortableWebDavConfig, fileName: String, bytes: ByteArray) {
        ensureCollection(config)
        val target = joinUrl(collectionUrl(config), fileName)
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "PUT",
                url = target,
                headers = authHeaders(config),
                body = bytes,
                mediaType = "application/zip",
            ),
        )
        require(response.statusCode in 200..299) {
            "WebDAV upload failed: HTTP ${response.statusCode}"
        }
    }

    suspend fun download(config: PortableWebDavConfig, href: String): ByteArray {
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = absoluteHref(config, href),
                headers = authHeaders(config),
            ),
        )
        require(response.statusCode in 200..299) {
            "WebDAV download failed: HTTP ${response.statusCode}"
        }
        return response.body
    }

    suspend fun delete(config: PortableWebDavConfig, href: String) {
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "DELETE",
                url = absoluteHref(config, href),
                headers = authHeaders(config),
            ),
        )
        require(response.statusCode in 200..299) {
            "WebDAV delete failed: HTTP ${response.statusCode}"
        }
    }

    suspend fun ensureCollection(config: PortableWebDavConfig) {
        val collection = collectionUrl(config)
        val probe = httpClient.execute(
            PlatformHttpRequest(
                method = "PROPFIND",
                url = collection,
                headers = authHeaders(config) + mapOf("Depth" to "0"),
                body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>".encodeToByteArray(),
                mediaType = "application/xml",
            ),
        )
        if (probe.statusCode in 200..299) return
        if (probe.statusCode != 404) {
            PlatformLog.w(TAG, "WebDAV collection probe HTTP ${probe.statusCode}")
        }
        val created = httpClient.execute(
            PlatformHttpRequest(
                method = "MKCOL",
                url = collection,
                headers = authHeaders(config),
            ),
        )
        require(created.statusCode in 200..299 || created.statusCode == 405) {
            "Unable to create WebDAV collection: HTTP ${created.statusCode}"
        }
    }

    companion object {
        private const val TAG = "PortableWebDav"

        fun collectionUrl(config: PortableWebDavConfig): String {
            val base = config.url.trim().trimEnd('/')
            require(base.isNotBlank()) { "WebDAV URL is required" }
            val path = config.path.trim('/').takeIf { it.isNotBlank() } ?: return "$base/"
            return "$base/$path/"
        }

        fun parsePropfind(xml: String, collectionUrl: String): List<PortableWebDavItem> {
            val responses = Regex(
                "<(?:D:|d:)?response\\b[\\s\\S]*?</(?:D:|d:)?response>",
                RegexOption.IGNORE_CASE,
            ).findAll(xml)
            val collectionNormalized = collectionUrl.trimEnd('/')
            return responses.mapNotNull { match ->
                val block = match.value
                val href = xmlTag(block, "href")?.replace("&amp;", "&") ?: return@mapNotNull null
                val absolute = if (href.startsWith("http://") || href.startsWith("https://")) {
                    href
                } else {
                    val origin = collectionUrl.substringBefore("://").let { scheme ->
                        val rest = collectionUrl.substringAfter("://")
                        val host = rest.substringBefore('/')
                        "$scheme://$host"
                    }
                    origin + if (href.startsWith('/')) href else "/$href"
                }
                if (absolute.trimEnd('/') == collectionNormalized) return@mapNotNull null
                if (block.contains("<d:collection", ignoreCase = true) ||
                    block.contains("<D:collection", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                val name = xmlTag(block, "displayname")
                    ?.ifBlank { null }
                    ?: absolute.substringAfterLast('/').ifBlank { absolute }
                val length = xmlTag(block, "getcontentlength")?.toLongOrNull()
                PortableWebDavItem(href = absolute, displayName = name, contentLength = length)
            }.toList()
        }

        internal fun joinUrl(base: String, name: String): String {
            val trimmed = base.trimEnd('/')
            return "$trimmed/${encodePathSegment(name)}"
        }

        private fun xmlTag(block: String, localName: String): String? {
            val regex = Regex(
                "<(?:D:|d:)?$localName\\b[^>]*>([\\s\\S]*?)</(?:D:|d:)?$localName>",
                RegexOption.IGNORE_CASE,
            )
            return regex.find(block)?.groupValues?.getOrNull(1)?.trim()
        }

        private fun absoluteHref(config: PortableWebDavConfig, href: String): String {
            if (href.startsWith("http://") || href.startsWith("https://")) return href
            return joinUrl(collectionUrl(config), href.trimStart('/'))
        }

        private fun encodePathSegment(name: String): String {
            val hex = "0123456789ABCDEF"
            return buildString {
                for (byte in name.encodeToByteArray()) {
                    val value = byte.toInt() and 0xFF
                    val unreserved = value in 'A'.code..'Z'.code ||
                        value in 'a'.code..'z'.code ||
                        value in '0'.code..'9'.code ||
                        value == '-'.code ||
                        value == '.'.code ||
                        value == '_'.code ||
                        value == '~'.code
                    if (unreserved) append(value.toChar())
                    else {
                        append('%')
                        append(hex[value shr 4])
                        append(hex[value and 0x0F])
                    }
                }
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun authHeaders(config: PortableWebDavConfig): Map<String, String> {
            val username = config.username
            if (username.isBlank() && config.password.isBlank()) return emptyMap()
            val token = Base64.Default.encode("${config.username}:${config.password}".encodeToByteArray())
            return mapOf("Authorization" to "Basic $token")
        }
    }
}
