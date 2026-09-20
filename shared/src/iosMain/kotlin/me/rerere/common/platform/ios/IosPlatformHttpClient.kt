package me.rerere.common.platform.ios

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Darwin/NSURLSession transport used by shared providers on iOS. */
@OptIn(ExperimentalAtomicApi::class)
class IosPlatformHttpClient(
    private val client: HttpClient = defaultClient(),
) : PlatformHttpClient {
    private val proxiedClients = AtomicReference<Map<String, HttpClient>>(emptyMap())

    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        val response = clientFor(request.proxy).request(request.url) { apply(request) }
        return PlatformHttpResponse(
            statusCode = response.status.value,
            headers = response.headers.names().associateWith { response.headers.getAll(it).orEmpty() },
            body = response.body(),
        )
    }

    override suspend fun downloadTo(
        request: PlatformHttpRequest,
        onChunk: suspend (ByteArray) -> Unit,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): PlatformHttpResponse {
        return clientFor(request.proxy).prepareRequest(request.url) { apply(request) }.execute { response ->
            val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val channel = response.body<io.ktor.utils.io.ByteReadChannel>()
            var downloaded = 0L
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) continue
                onChunk(buffer.copyOf(read))
                downloaded += read
                onProgress(downloaded, total)
            }
            PlatformHttpResponse(
                statusCode = response.status.value,
                headers = response.headers.names().associateWith { response.headers.getAll(it).orEmpty() },
                body = ByteArray(0),
            )
        }
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = flow {
        try {
            clientFor(request.proxy).prepareRequest(request.url) { apply(request) }.execute { response ->
                if (response.status.value !in 200..299) {
                    val body = response.body<ByteArray>().decodeToString()
                    emit(
                        PlatformServerEvent.Failure(
                            message = "HTTP ${response.status.value}",
                            statusCode = response.status.value,
                            body = body,
                        ),
                    )
                    return@execute
                }
                emit(
                    PlatformServerEvent.Open(
                        statusCode = response.status.value,
                        headers = response.headers.names().associateWith { response.headers.getAll(it).orEmpty() },
                    ),
                )
                val channel = response.body<io.ktor.utils.io.ByteReadChannel>()
                var id: String? = null
                var event: String? = null
                val data = mutableListOf<String>()

                suspend fun flushEvent() {
                    if (data.isNotEmpty()) {
                        emit(PlatformServerEvent.Event(id = id, event = event, data = data.joinToString("\n")))
                    }
                    id = null
                    event = null
                    data.clear()
                }

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    when {
                        line.isEmpty() -> flushEvent()
                        line.startsWith(":") -> Unit
                        line.startsWith("id:") -> id = line.substringAfter(':').trimStart()
                        line.startsWith("event:") -> event = line.substringAfter(':').trimStart()
                        line.startsWith("data:") -> data += line.substringAfter(':').trimStart()
                    }
                }
                flushEvent()
                emit(PlatformServerEvent.Closed)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(PlatformServerEvent.Failure(message = throwable.message))
        }
    }

    private fun clientFor(proxy: PlatformHttpProxy?): HttpClient {
        if (proxy == null) return client
        val key = "${proxy.host}:${proxy.port}:${proxy.username.orEmpty()}"
        proxiedClients.load()[key]?.let { return it }
        val created = defaultClient(proxy)
        while (true) {
            val current = proxiedClients.load()
            current[key]?.let { existing ->
                created.close()
                return existing
            }
            if (proxiedClients.compareAndSet(current, current + (key to created))) {
                return created
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.apply(request: PlatformHttpRequest) {
        method = HttpMethod.parse(request.method)
        request.headers.forEach { (name, value) -> header(name, value) }
        proxyAuthorization(request.proxy)?.let { header("Proxy-Authorization", it) }
        request.mediaType?.let { contentType(ContentType.parse(it)) }
        request.body?.let(::setBody)
    }

    companion object {
        private fun defaultClient(proxy: PlatformHttpProxy? = null): HttpClient = HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 60_000
                requestTimeoutMillis = null
                socketTimeoutMillis = null
            }
            if (proxy != null) {
                engine {
                    configureSession {
                        connectionProxyDictionary = proxyDictionary(proxy)
                    }
                }
            }
        }

        private fun proxyDictionary(proxy: PlatformHttpProxy): Map<Any?, *> = buildMap {
            put("HTTPEnable", 1)
            put("HTTPProxy", proxy.host)
            put("HTTPPort", proxy.port)
            put("HTTPSEnable", 1)
            put("HTTPSProxy", proxy.host)
            put("HTTPSPort", proxy.port)
            proxy.username?.takeIf { it.isNotBlank() }?.let { put("kCFProxyUsernameKey", it) }
            proxy.password?.takeIf { it.isNotBlank() }?.let { put("kCFProxyPasswordKey", it) }
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun proxyAuthorization(proxy: PlatformHttpProxy?): String? {
            val username = proxy?.username?.takeIf { it.isNotBlank() } ?: return null
            val password = proxy.password.orEmpty()
            val token = Base64.Default.encode("$username:$password".encodeToByteArray())
            return "Basic $token"
        }
    }
}
