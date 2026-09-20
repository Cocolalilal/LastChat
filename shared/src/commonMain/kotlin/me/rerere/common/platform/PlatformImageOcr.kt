package me.rerere.common.platform

/**
 * On-device image OCR used by [me.rerere.ai.generation.PortableOcrTransformer].
 * Android keeps ChatAttachmentRepository / model OCR; iOS uses Vision.
 */
fun interface PlatformImageOcr {
    suspend fun recognizeText(imageBytes: ByteArray): String?
}

object UnavailablePlatformImageOcr : PlatformImageOcr {
    override suspend fun recognizeText(imageBytes: ByteArray): String? = null
}
