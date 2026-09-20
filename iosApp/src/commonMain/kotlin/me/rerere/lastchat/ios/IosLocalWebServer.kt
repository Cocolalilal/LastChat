package me.rerere.lastchat.ios

import me.rerere.rikkahub.data.web.PortableWebApiResponse
import me.rerere.rikkahub.data.web.PortableWebRequest

internal expect class IosLocalWebServer() {
    fun start(
        port: Int,
        handler: (PortableWebRequest) -> PortableWebApiResponse,
    ): Boolean

    fun stop()
}
