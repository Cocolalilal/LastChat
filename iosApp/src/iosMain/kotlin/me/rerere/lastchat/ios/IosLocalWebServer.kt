package me.rerere.lastchat.ios

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.web.PortableWebApiResponse
import me.rerere.rikkahub.data.web.PortableWebApiRouter
import me.rerere.rikkahub.data.web.PortableWebRequest

internal actual class IosLocalWebServer {
    private var server: EmbeddedServer<*, *>? = null

    actual fun start(
        port: Int,
        handler: (PortableWebRequest) -> PortableWebApiResponse,
    ): Boolean {
        stop()
        return runCatching {
            val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                intercept(ApplicationCallPipeline.Call) {
                    val uri = call.request.uri
                    val path = uri.substringBefore('?')
                    val query = PortableWebApiRouter.parseQuery(uri.substringAfter('?', missingDelimiterValue = ""))
                    val method = call.request.httpMethod.value
                    val authorization = call.request.header("Authorization")
                    val contentType = call.request.header("Content-Type")
                    val isGet = method.equals("GET", ignoreCase = true)
                    val isMultipart = contentType?.contains("multipart/", ignoreCase = true) == true
                    val bodyBytes = if (!isGet && isMultipart) {
                        runCatching { call.receive<ByteArray>() }.getOrNull()
                    } else {
                        null
                    }
                    val body = if (isGet || isMultipart) {
                        null
                    } else {
                        runCatching { call.receiveText() }.getOrNull()
                    }
                    val request = PortableWebRequest(
                        method = method,
                        path = path,
                        authorization = authorization,
                        query = query,
                        body = body,
                        bodyBytes = bodyBytes,
                        contentType = contentType,
                    )
                    val response = handler(request)
                    val keepAliveMs = response.sseKeepAliveMs
                    when {
                        response.sse && keepAliveMs != null -> {
                            call.response.headers.append("Cache-Control", "no-cache")
                            call.response.headers.append("Connection", "keep-alive")
                            call.response.headers.append("X-Accel-Buffering", "no")
                            call.respond(
                                SseWriteContent(
                                    statusCode = response.statusCode,
                                    mediaType = response.contentType,
                                    initial = response,
                                    keepAliveMs = keepAliveMs,
                                    next = { handler(request) },
                                ),
                            )
                        }
                        response.bytes != null -> {
                            call.respondBytes(
                                bytes = response.bytes ?: ByteArray(0),
                                contentType = ContentType.parse(response.contentType),
                                status = HttpStatusCode.fromValue(response.statusCode),
                            )
                        }
                        else -> {
                            call.respondText(
                                text = response.body,
                                contentType = ContentType.parse(response.contentType),
                                status = HttpStatusCode.fromValue(response.statusCode),
                            )
                        }
                    }
                    finish()
                }
            }
            engine.start(wait = false)
            server = engine
            true
        }.getOrDefault(false)
    }

    actual fun stop() {
        runCatching { runBlocking { server?.stop() } }
        server = null
    }
}

private class SseWriteContent(
    statusCode: Int,
    mediaType: String,
    private val initial: PortableWebApiResponse,
    private val keepAliveMs: Long,
    private val next: () -> PortableWebApiResponse,
) : OutgoingContent.WriteChannelContent() {
    override val contentType: ContentType = ContentType.parse(mediaType)
    override val status: HttpStatusCode = HttpStatusCode.fromValue(statusCode)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        coroutineScope {
            var lastBody = initial.body
            channel.writeFully(lastBody.encodeToByteArray())
            channel.flush()
            while (isActive) {
                delay(keepAliveMs)
                val latest = runCatching { next() }.getOrNull() ?: break
                val payload = if (latest.body != lastBody) {
                    lastBody = latest.body
                    latest.body
                } else {
                    PortableWebApiResponse.SSE_HEARTBEAT
                }
                channel.writeFully(payload.encodeToByteArray())
                channel.flush()
            }
        }
    }
}
