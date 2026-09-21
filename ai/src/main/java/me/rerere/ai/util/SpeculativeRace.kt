package me.rerere.ai.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.atomic.AtomicBoolean

private sealed class RaceEvent<T> {
    data class Value<T>(val value: T, val isFirst: Boolean, val fromSpeculative: Boolean) : RaceEvent<T>()
    data class Error<T>(val error: Throwable, val fromSpeculative: Boolean) : RaceEvent<T>()
    data class Complete<T>(val fromSpeculative: Boolean) : RaceEvent<T>()
}

/**
 * Executes a primary flow and, if no item is emitted within [timeoutMs], launches a speculative backup flow.
 * Whichever flow emits the first item wins; the other flow's job is cancelled.
 *
 * If the primary flow throws an error before emitting any item, the speculative flow is launched immediately
 * without waiting for the remaining delay.
 */
fun <T> raceSpeculativeFlow(
    timeoutMs: Long,
    primaryFlow: Flow<T>,
    speculativeFlow: Flow<T>,
): Flow<T> = flow {
    supervisorScope {
        val channel = Channel<RaceEvent<T>>(Channel.BUFFERED)
        val hasEmittedFirst = AtomicBoolean(false)
        var primaryJob: Job? = null
        var speculativeJob: Job? = null
        var timerJob: Job? = null

        fun selectWinner(winnerIsSpeculative: Boolean) {
            timerJob?.cancel()
            if (winnerIsSpeculative) {
                primaryJob?.cancel()
            } else {
                speculativeJob?.cancel()
            }
        }

        primaryJob = launch {
            try {
                var isFirst = true
                primaryFlow.collect { item ->
                    if (isFirst) {
                        isFirst = false
                        channel.send(RaceEvent.Value(item, isFirst = true, fromSpeculative = false))
                    } else {
                        channel.send(RaceEvent.Value(item, isFirst = false, fromSpeculative = false))
                    }
                }
                channel.send(RaceEvent.Complete(fromSpeculative = false))
            } catch (e: CancellationException) {
                // Cancelled when competitor won
            } catch (e: Throwable) {
                channel.send(RaceEvent.Error(e, fromSpeculative = false))
            }
        }

        timerJob = launch {
            delay(timeoutMs)
            if (!hasEmittedFirst.get()) {
                speculativeJob = launch {
                    try {
                        var isFirst = true
                        speculativeFlow.collect { item ->
                            if (isFirst) {
                                isFirst = false
                                channel.send(RaceEvent.Value(item, isFirst = true, fromSpeculative = true))
                            } else {
                                channel.send(RaceEvent.Value(item, isFirst = false, fromSpeculative = true))
                            }
                        }
                        channel.send(RaceEvent.Complete(fromSpeculative = true))
                    } catch (e: CancellationException) {
                        // Cancelled when competitor won
                    } catch (e: Throwable) {
                        channel.send(RaceEvent.Error(e, fromSpeculative = true))
                    }
                }
            }
        }

        var winningStreamIsSpeculative: Boolean? = null

        try {
            for (event in channel) {
                when (event) {
                    is RaceEvent.Value -> {
                        if (event.isFirst) {
                            if (hasEmittedFirst.compareAndSet(false, true)) {
                                winningStreamIsSpeculative = event.fromSpeculative
                                selectWinner(event.fromSpeculative)
                                emit(event.value)
                            }
                        } else {
                            if (winningStreamIsSpeculative == event.fromSpeculative) {
                                emit(event.value)
                            }
                        }
                    }

                    is RaceEvent.Complete -> {
                        if (winningStreamIsSpeculative == event.fromSpeculative) {
                            break
                        }
                    }

                    is RaceEvent.Error -> {
                        if (!hasEmittedFirst.get()) {
                            if (!event.fromSpeculative) {
                                timerJob?.cancel()
                                if (speculativeJob == null) {
                                    speculativeJob = launch {
                                        try {
                                            var isFirst = true
                                            speculativeFlow.collect { item ->
                                                if (isFirst) {
                                                    isFirst = false
                                                    channel.send(RaceEvent.Value(item, isFirst = true, fromSpeculative = true))
                                                } else {
                                                    channel.send(RaceEvent.Value(item, isFirst = false, fromSpeculative = true))
                                                }
                                            }
                                            channel.send(RaceEvent.Complete(fromSpeculative = true))
                                        } catch (e: CancellationException) {
                                            // Cancelled
                                        } catch (e: Throwable) {
                                            channel.send(RaceEvent.Error(e, fromSpeculative = true))
                                        }
                                    }
                                }
                            } else {
                                if (primaryJob.isCompleted) {
                                    throw event.error
                                }
                            }
                        } else if (winningStreamIsSpeculative == event.fromSpeculative) {
                            throw event.error
                        }
                    }
                }
            }
        } finally {
            timerJob?.cancel()
            primaryJob.cancel()
            speculativeJob?.cancel()
            channel.close()
        }
    }
}
