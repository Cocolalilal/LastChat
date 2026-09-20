package me.rerere.lastchat.ios

import me.rerere.rikkahub.data.web.PortableWebApiResponse

internal expect class IosLocalWebServer() {
    fun start(
        port: Int,
        handler: (
            method: String,
            path: String,
            authorization: String?,
            query: Map<String, String>,
            body: String?,
        ) -> PortableWebApiResponse,
    ): Boolean

    fun stop()
}
