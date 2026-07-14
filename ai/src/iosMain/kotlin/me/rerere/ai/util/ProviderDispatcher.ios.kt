package me.rerere.ai.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val providerIoDispatcher: CoroutineDispatcher = Dispatchers.Default
