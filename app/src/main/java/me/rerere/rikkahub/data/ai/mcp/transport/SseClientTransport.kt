package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.mcp.McpJson
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "SseClientTransport"

@OptIn(ExperimentalAtomicApi::class)
internal class SseClientTransport(
    private val client: PlatformHttpClient,
    private val urlString: String,
    private val headers: List<Pair<String, String>>,
) : AbstractTransport() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private val endpoint = CompletableDeferred<String>()

    private var job: Job? = null

    private val baseUrl by lazy {
        URLBuilder()
            .takeFrom(urlString)
            .apply {
                pathSegments = emptyList()
                parameters.clear()
            }
            .build()
            .toString()
            .trimEnd('/')
    }

    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            error(
                "SSEClientTransport already started! " +
                    "If using Client class, note that connect() calls start() automatically.",
            )
        }

        job = scope.launch {
            val request = PlatformHttpRequest(
                method = "GET",
                url = urlString,
                headers = headers.toMap() + mapOf(
                    "Accept" to "text/event-stream",
                    "User-Agent" to "LastChat/${BuildConfig.VERSION_NAME}",
                ),
            )
            client.streamEvents(request).collect(::handleServerEvent)
        }

        withTimeout(30000) {
            endpoint.await()
            Log.i(TAG, "start: Connected to endpoint ${endpoint.getCompleted()}")
        }
    }

    private fun handleServerEvent(event: PlatformServerEvent) {
        when (event) {
            is PlatformServerEvent.Open -> {
                Log.i(TAG, "onOpen: $urlString")
            }

            PlatformServerEvent.Closed -> {
                Log.i(TAG, "onClosed: $urlString")
            }

            is PlatformServerEvent.Failure -> {
                val failure = Exception(event.message ?: "SSE Failure")
                Log.i(TAG, "onFailure: $urlString / ${event.message} / $baseUrl")
                endpoint.completeExceptionally(failure)
                _onError(failure)
                _onClose()
            }

            is PlatformServerEvent.Event -> {
                handleEventMessage(event)
            }
        }
    }

    private fun handleEventMessage(event: PlatformServerEvent.Event) {
        val type = event.event
        val data = event.data
        Log.i(TAG, "onEvent($baseUrl): id=${event.id} type=$type payloadSize=${data.length}")
        when (type) {
            "error" -> {
                val e = IllegalStateException("SSE error: $data")
                _onError(e)
            }

            "open" -> {
                // The connection is open, but we need to wait for the endpoint to be received.
            }

            "endpoint" -> {
                val endpointData = if (data.startsWith("http://") || data.startsWith("https://")) {
                    data
                } else {
                    baseUrl + if (data.startsWith("/")) data else "/$data"
                }
                Log.i(TAG, "onEvent: endpoint: $endpointData")
                endpoint.complete(endpointData)
            }

            else -> {
                scope.launch {
                    try {
                        val message = McpJson.decodeFromString<JSONRPCMessage>(data)
                        _onMessage(message)
                    } catch (e: Exception) {
                        _onError(e)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun send(message: JSONRPCMessage) {
        if (!endpoint.isCompleted) {
            error("Not connected")
        }

        Log.i(
            TAG,
            "send: POSTing to endpoint ${endpoint.getCompleted()} messageType=${message::class.simpleName}"
        )

        try {
            val response = client.execute(
                PlatformHttpRequest(
                    method = "POST",
                    url = endpoint.getCompleted(),
                    headers = headers.toMap(),
                    body = McpJson.encodeToString(message).encodeToByteArray(),
                    mediaType = "application/json",
                )
            )
            if (response.statusCode !in 200..299) {
                val text = response.body.decodeToString()
                error(
                    "Error POSTing to endpoint ${endpoint.getCompleted()} " +
                        "(HTTP ${response.statusCode}, bodySize=${text.length})"
                )
            } else {
                Log.i(TAG, "send: POST to endpoint ${endpoint.getCompleted()} successful")
            }
        } catch (e: Exception) {
            _onError(e)
            throw e
        }
    }

    override suspend fun close() {
        if (!initialized.load()) {
            error("SSEClientTransport is not initialized!")
        }

        _onClose()
        job?.cancel()
    }
}
