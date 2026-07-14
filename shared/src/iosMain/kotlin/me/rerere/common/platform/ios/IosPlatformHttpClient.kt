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
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent

/** Darwin/NSURLSession transport used by shared providers on iOS. */
class IosPlatformHttpClient(
    private val client: HttpClient = defaultClient(),
) : PlatformHttpClient {
    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        require(request.proxy == null) { "Per-provider proxies are not yet supported on iOS" }
        val response = client.request(request.url) { apply(request) }
        return PlatformHttpResponse(
            statusCode = response.status.value,
            headers = response.headers.names().associateWith { response.headers.getAll(it).orEmpty() },
            body = response.body(),
        )
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = flow {
        if (request.proxy != null) {
            emit(PlatformServerEvent.Failure(message = "Per-provider proxies are not yet supported on iOS"))
            return@flow
        }
        try {
            client.prepareRequest(request.url) { apply(request) }.execute { response ->
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

    private fun io.ktor.client.request.HttpRequestBuilder.apply(request: PlatformHttpRequest) {
        method = HttpMethod.parse(request.method)
        request.headers.forEach { (name, value) -> header(name, value) }
        request.mediaType?.let { contentType(ContentType.parse(it)) }
        request.body?.let(::setBody)
    }

    companion object {
        private fun defaultClient(): HttpClient = HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 60_000
                requestTimeoutMillis = null
                socketTimeoutMillis = null
            }
        }
    }
}
