package me.rerere.rikkahub.utils

import android.content.Context
import me.rerere.locallm.LiteRtRuntime
import me.rerere.rikkahub.R

fun Throwable.toLocalInferenceUserMessage(context: Context): String? {
    val memoryError = generateSequence(this) { current ->
        current.cause?.takeUnless { it === current }
    }.filterIsInstance<LiteRtRuntime.InsufficientMemoryException>().firstOrNull()

    return memoryError?.info?.let { info ->
        context.getString(
            R.string.local_llm_insufficient_memory_format,
            info.requiredMb,
            info.modelMb,
            info.availableMb,
        )
    } ?: message
}
