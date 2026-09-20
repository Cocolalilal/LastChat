package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.platform.PlatformImageOcr
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import kotlin.coroutines.resume

/**
 * On-device OCR via Vision `VNRecognizeTextRequest`. Wired into
 * `PortableOcrTransformer` on iOS so non-multimodal models still receive
 * image text the same way Android's attachment OCR runtime does.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPlatformImageOcr : PlatformImageOcr {
    override suspend fun recognizeText(imageBytes: ByteArray): String? {
        if (imageBytes.isEmpty()) return null
        val data = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        return suspendCancellableCoroutine { continuation ->
            var completed = false
            fun complete(value: String?) {
                if (completed) return
                completed = true
                if (continuation.isActive) continuation.resume(value)
            }
            val request = VNRecognizeTextRequest { request, error ->
                if (error != null || request == null) {
                    complete(null)
                    return@VNRecognizeTextRequest
                }
                val text = request.results.orEmpty().mapNotNull { observation ->
                    val recognized = observation as? VNRecognizedTextObservation ?: return@mapNotNull null
                    val candidate = recognized.topCandidates(1u).firstOrNull() as? VNRecognizedText
                    candidate?.string?.trim()?.takeIf { it.isNotEmpty() }
                }.joinToString("\n")
                complete(text.takeIf { it.isNotBlank() })
            }
            request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
            request.usesLanguageCorrection = true
            val handler = VNImageRequestHandler(data = data, options = emptyMap<Any?, Any>())
            val ok = runCatching {
                handler.performRequests(listOf(request), error = null)
            }.getOrDefault(false)
            if (!ok) complete(null)
        }
    }
}
