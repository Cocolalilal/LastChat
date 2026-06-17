package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "StreamableHttpClientTra"

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
private const val MCP_RESUMPTION_TOKEN_HEADER = "Last-Event-ID"

class StreamableHttpError(
    val code: Int? = null,
    message: String? = null
) : Exception("Streamable HTTP error: $message")

@OptIn(ExperimentalAtomicApi::class)
class StreamableHttpClientTransport(
    private val client: PlatformHttpClient,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
) : AbstractTransport() {
    var sessionId: String? = null
        private set
    var protocolVersion: String? = null

    private val initialized: AtomicBoolean = AtomicBoolean(false)

    private var sseJob: Job? = null

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    private var lastEventId: String? = null

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StreamableHttpClientTransport already started!")
        }
        Log.d(TAG, "start: Client transport starting...")
    }

    /**
     * Sends a single message with optional resumption support
     */
    override suspend fun send(message: JSONRPCMessage) {
        send(message, null)
    }

    /**
     * Sends one or more messages with optional resumption support.
     * This is the main send method that matches the TypeScript implementation.
     */
    suspend fun send(
        message: JSONRPCMessage,
        resumptionToken: String?,
        onResumptionToken: ((String) -> Unit)? = null
    ) {
        Log.d(
            TAG,
            "send: Client sending message via POST to $url type=${message::class.simpleName}"
        )

        // If we have a resumption token, reconnect the SSE stream with it
        resumptionToken?.let { token ->
            startSseSession(
                resumptionToken = token,
                onResumptionToken = onResumptionToken,
                replayMessageId = if (message is JSONRPCRequest) message.id else null
            )
            return
        }

        val response = client.execute(
            PlatformHttpRequest(
                method = "POST",
                url = url,
                headers = commonHeaders() + mapOf("Accept" to "application/json, text/event-stream"),
                body = McpJson.encodeToString(message).encodeToByteArray(),
                mediaType = "application/json",
            )
        )

        response.header(MCP_SESSION_ID_HEADER)?.let {
            sessionId = it
        }

        if (response.statusCode == 202) { // HTTP_ACCEPTED
            if (message is JSONRPCNotification && message.method == "notifications/initialized") {
                startSseSession(onResumptionToken = onResumptionToken)
            }
            return
        }

        if (!response.isSuccessful) {
            val error = StreamableHttpError(response.statusCode, response.body.decodeToString())
            _onError(error)
            throw error
        }

        val contentType = response.header("Content-Type")
        when {
            contentType?.startsWith("application/json") == true -> {
                val body = response.body.decodeToString()
                if (body.isNotEmpty()) {
                    runCatching { McpJson.decodeFromString<JSONRPCMessage>(body) }
                        .onSuccess { _onMessage(it) }
                        .onFailure(_onError)
                }
            }

            contentType?.startsWith("text/event-stream") == true -> {
                handleInlineSse(
                    data = response.body.decodeToString(),
                    replayMessageId = if (message is JSONRPCRequest) message.id else null,
                    onResumptionToken = onResumptionToken
                )
            }

            else -> {
                val body = response.body.decodeToString()
                if (contentType == null && body.isBlank()) return

                val ct = contentType ?: "<none>"
                val error = StreamableHttpError(-1, "Unexpected content type: $ct")
                _onError(error)
                throw error
            }
        }
    }

    override suspend fun close() {
        if (!initialized.load()) return // Already closed or never started
        Log.d(TAG, "close: Client transport closing.")

        try {
            // Try to terminate session if we have one
            terminateSession()

            sseJob?.cancelAndJoin()
            scope.cancel()
        } catch (_: Exception) {
            // Ignore errors during cleanup
        } finally {
            initialized.store(false)
            _onClose()
        }
    }

    /**
     * Terminates the current session by sending a DELETE request to the server.
     */
    suspend fun terminateSession() {
        if (sessionId == null) return
        Log.d(TAG, "terminateSession: Terminating session: $sessionId")

        val response = client.execute(
            PlatformHttpRequest(
                method = "DELETE",
                url = url,
                headers = commonHeaders(),
            )
        )

        // 405 means server doesn't support explicit session termination
        if (!response.isSuccessful && response.statusCode != 405) {
            val error = StreamableHttpError(
                response.statusCode,
                "Failed to terminate session: ${response.body.decodeToString()}"
            )
            Log.e(TAG, "Failed to terminate session", error)
            _onError(error)
            throw error
        }

        sessionId = null
        lastEventId = null
        Log.d(TAG, "Session terminated successfully")
    }

    private suspend fun startSseSession(
        resumptionToken: String? = null,
        replayMessageId: RequestId? = null,
        onResumptionToken: ((String) -> Unit)? = null
    ) {
        sseJob?.cancelAndJoin()

        Log.d(TAG, "startSseSession: Client attempting to start SSE session at url: $url")

        val requestHeaders = commonHeaders() + buildMap {
            put("Accept", "text/event-stream")
            (resumptionToken ?: lastEventId)?.let {
                put(MCP_RESUMPTION_TOKEN_HEADER, it)
            }
        }

        sseJob = client.streamEvents(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = requestHeaders,
            )
        )
            .onEach { event ->
                when (event) {
                    is PlatformServerEvent.Open -> {
                        Log.d(TAG, "startSseSession: Client SSE session started successfully.")
                    }

                    is PlatformServerEvent.Event -> {
                        event.id?.let {
                            lastEventId = it
                            onResumptionToken?.invoke(it)
                        }
                        Log.d(
                            TAG,
                            "collectSse: Client received SSE event: event=${event.event}, id=${event.id}, payloadSize=${event.data.length}"
                        )

                        when (event.event) {
                            null, "message" -> {
                                if (event.data.isNotEmpty()) {
                                    runCatching { McpJson.decodeFromString<JSONRPCMessage>(event.data) }
                                        .onSuccess { msg ->
                                            scope.launch {
                                                if (replayMessageId != null && msg is JSONRPCResponse) {
                                                    _onMessage(msg.copy(id = replayMessageId))
                                                } else {
                                                    _onMessage(msg)
                                                }
                                            }
                                        }
                                        .onFailure(_onError)
                                }
                            }

                            "error" -> _onError(StreamableHttpError(null, event.data))
                        }
                    }

                    PlatformServerEvent.Closed -> {
                        Log.d(TAG, "startSseSession: SSE connection closed")
                    }

                    is PlatformServerEvent.Failure -> {
                        if (event.statusCode == 405) {
                            Log.i(TAG, "startSseSession: Server returned 405 for GET/SSE, stream disabled.")
                            return@onEach
                        }
                        _onError(StreamableHttpError(event.statusCode, event.body ?: event.message))
                    }
                }
            }
            .catch { throwable ->
                Log.e(TAG, "SSE flow error", throwable)
                _onError(throwable)
            }
            .launchIn(scope)
    }

    private fun commonHeaders() = buildMap {
        sessionId?.let { put(MCP_SESSION_ID_HEADER, it) }
        protocolVersion?.let { put(MCP_PROTOCOL_VERSION_HEADER, it) }
        putAll(headers)
    }

    private val PlatformHttpResponse.isSuccessful: Boolean
        get() = statusCode in 200..299

    private fun PlatformHttpResponse.header(name: String): String? {
        headers.forEach { (headerName, values) ->
            if (headerName.equals(name, ignoreCase = true)) {
                return values.firstOrNull()
            }
        }
        return null
    }

    private suspend fun handleInlineSse(
        data: String,
        replayMessageId: RequestId?,
        onResumptionToken: ((String) -> Unit)?
    ) {
        Log.d(TAG, "handleInlineSse: Handling inline SSE from POST response")

        val sb = StringBuilder()
        var id: String? = null
        var eventName: String? = null

        fun dispatch(data: String) {
            id?.let {
                lastEventId = it
                onResumptionToken?.invoke(it)
            }
            if (eventName == null || eventName == "message") {
                runCatching { McpJson.decodeFromString<JSONRPCMessage>(data) }
                    .onSuccess { msg ->
                        scope.launch {
                            if (replayMessageId != null && msg is JSONRPCResponse) {
                                _onMessage(msg.copy(id = replayMessageId))
                            } else {
                                _onMessage(msg)
                            }
                        }
                    }
                    .onFailure(_onError)
            }
            // reset
            id = null
            eventName = null
            sb.clear()
        }

        data.lineSequence().forEach { line ->
            if (line.isEmpty()) {
                dispatch(sb.toString())
                return@forEach
            }
            when {
                line.startsWith("id:") -> id = line.substringAfter("id:").trim()
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                line.startsWith("data:") -> sb.append(line.substringAfter("data:").trim())
            }
        }
    }
}
