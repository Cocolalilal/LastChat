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
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlinx.coroutines.runBlocking

internal actual class IosLocalWebServer {
    private var server: EmbeddedServer<*, *>? = null

    actual fun start(
        port: Int,
        handler: (
            method: String,
            path: String,
            authorization: String?,
            queryToken: String?,
        ) -> Pair<Int, String>,
    ): Boolean {
        stop()
        return runCatching {
            val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                intercept(ApplicationCallPipeline.Call) {
                    val uri = call.request.uri
                    val queryToken = uri.substringAfter("access_token=", "")
                        .substringBefore('&')
                        .takeIf { "access_token=" in uri }
                    val (status, body) = handler(
                        call.request.httpMethod.value,
                        uri.substringBefore('?'),
                        call.request.header("Authorization"),
                        queryToken,
                    )
                    call.respondText(
                        text = body,
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.fromValue(status),
                    )
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
