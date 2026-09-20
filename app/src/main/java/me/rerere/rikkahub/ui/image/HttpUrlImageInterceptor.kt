package me.rerere.rikkahub.ui.image

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import me.rerere.common.http.normalizeHttpUrl

class HttpUrlImageInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String) {
            return chain.proceed()
        }

        val trimmed = data.trim()
        if (!needsHttpUrlValidation(trimmed)) {
            return chain.proceed()
        }

        val normalized = trimmed.normalizeHttpUrl()
            ?: return ErrorResult(
                image = null,
                request = chain.request,
                throwable = IllegalArgumentException("Invalid image URL: '$trimmed'"),
            )

        return if (normalized == data) {
            chain.proceed()
        } else {
            chain.withRequest(chain.request.newBuilder().data(normalized).build()).proceed()
        }
    }
}

internal fun needsHttpUrlValidation(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return true
    }
    if (trimmed.contains("://") || trimmed.startsWith("/") || trimmed.startsWith(".")) {
        return false
    }
    val firstSegment = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
    return firstSegment.contains('.') ||
        firstSegment.startsWith("localhost", ignoreCase = true) ||
        firstSegment.startsWith("127.0.0.1")
}
