package me.rerere.common.platform

interface PlatformMediaEncoder {
    fun encodeImage(url: String, withPrefix: Boolean = true): Result<String>

    fun encodeVideo(url: String, withPrefix: Boolean = true): Result<String>

    fun encodeAudio(url: String, withPrefix: Boolean = true): Result<String>
}
