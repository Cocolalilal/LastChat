package me.rerere.lastchat.ios

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.web.PortableWebApiResponse
import me.rerere.rikkahub.data.web.PortableWebApiRouter

internal actual class IosLocalWebServer {
    private var server: EmbeddedServer<*, *>? = null

    actual fun start(
        port: Int,
        handler: (
            method: String,
            path: String,
            authorization: String?,
            query: Map<String, String>,
            body: String?,
        ) -> PortableWebApiResponse,
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
                    val body = if (method.equals("GET", ignoreCase = true)) {
                        null
                    } else {
                        runCatching { call.receiveText() }.getOrNull()
                    }
                    val response = handler(method, path, authorization, query, body)
                    when {
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
