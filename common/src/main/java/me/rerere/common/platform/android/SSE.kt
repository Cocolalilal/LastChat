package me.rerere.common.platform.android

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

sealed class SseEvent {
    data class Open(
        val statusCode: Int,
        val headers: Map<String, List<String>>
    ) : SseEvent()

    data class Event(
        val id: String?,
        val type: String?,
        val data: String
    ) : SseEvent()

    data object Closed : SseEvent()

    data class Failure(
        val throwable: Throwable?,
        val response: Response?
    ) : SseEvent()
}

fun OkHttpClient.sseFlow(request: Request): Flow<SseEvent> {
    return callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(SseEvent.Open(response.code, response.headers.toMultimap()))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(SseEvent.Event(id, type, data))
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(SseEvent.Closed)
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(SseEvent.Failure(t, response))
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(this@sseFlow)
            .newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }
}
