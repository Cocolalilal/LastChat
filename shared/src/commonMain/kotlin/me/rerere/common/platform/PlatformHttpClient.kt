package me.rerere.common.platform

import kotlinx.coroutines.flow.Flow

data class PlatformHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val mediaType: String? = null,
    val proxy: PlatformHttpProxy? = null
)

data class PlatformHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0)
)

data class PlatformHttpProxy(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
)

sealed class PlatformServerEvent {
    data class Open(
        val statusCode: Int? = null,
        val headers: Map<String, List<String>> = emptyMap()
    ) : PlatformServerEvent()

    data class Event(
        val id: String? = null,
        val event: String? = null,
        val data: String
    ) : PlatformServerEvent()

    data object Closed : PlatformServerEvent()

    data class Failure(
        val message: String? = null,
        val statusCode: Int? = null,
        val body: String? = null
    ) : PlatformServerEvent()
}

interface PlatformHttpClient {
    suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse

    fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent>

    /**
     * Streams the response body in chunks. Used for catalog JSON and on-device model
     * files so Android OkHttp and iOS URLSession share one download loop.
     */
    suspend fun downloadTo(
        request: PlatformHttpRequest,
        onChunk: suspend (ByteArray) -> Unit,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): PlatformHttpResponse {
        val response = execute(request)
        if (response.statusCode in 200..299 && response.body.isNotEmpty()) {
            onChunk(response.body)
            onProgress(response.body.size.toLong(), response.body.size.toLong())
        }
        return response.copy(body = ByteArray(0))
    }
}
