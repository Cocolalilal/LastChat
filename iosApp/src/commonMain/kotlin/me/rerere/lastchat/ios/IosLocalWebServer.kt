package me.rerere.lastchat.ios

internal expect class IosLocalWebServer() {
    fun start(
        port: Int,
        handler: (
            method: String,
            path: String,
            authorization: String?,
            queryToken: String?,
        ) -> Pair<Int, String>,
    ): Boolean

    fun stop()
}
