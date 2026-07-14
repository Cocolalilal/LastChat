package me.rerere.search

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val searchIoDispatcher: CoroutineDispatcher = Dispatchers.Default
