package me.rerere.common.platform.android

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy

class OkHttpPlatformHttpClient(
    private val client: OkHttpClient
) : PlatformHttpClient {
    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        request.client().newCall(request.toOkHttpRequest()).await().use { response ->
            return PlatformHttpResponse(
                statusCode = response.code,
                headers = response.headers.toMultimap(),
                body = response.body.bytes()
            )
        }
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> {
        return request.client().sseFlow(request.toOkHttpRequest()).map { event ->
            when (event) {
                is SseEvent.Event -> PlatformServerEvent.Event(
                    id = event.id,
                    event = event.type,
                    data = event.data
                )
                is SseEvent.Open -> PlatformServerEvent.Open(
                    statusCode = event.statusCode,
                    headers = event.headers
                )
                SseEvent.Closed -> PlatformServerEvent.Closed
                is SseEvent.Failure -> PlatformServerEvent.Failure(
                    message = event.throwable?.message,
                    statusCode = event.response?.code,
                    body = runCatching { event.response?.peekBody(1_000_000L)?.string() }.getOrNull()
                )
            }
        }
    }

    private fun PlatformHttpRequest.toOkHttpRequest(): Request {
        val requestBody = body?.toRequestBody(mediaType?.toMediaTypeOrNull())
        return Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .method(method, requestBody)
            .build()
    }

    private fun PlatformHttpRequest.client(): OkHttpClient {
        val proxyConfig = proxy ?: return client
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port))
        val username = proxyConfig.username
        val password = proxyConfig.password
        return client.newBuilder()
            .proxy(proxy)
            .apply {
                if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    proxyAuthenticator { _: Route?, response: Response ->
                        response.request.newBuilder()
                            .header(
                                "Proxy-Authorization",
                                Credentials.basic(username, password)
                            )
                            .build()
                    }
                }
            }
            .build()
    }
}
