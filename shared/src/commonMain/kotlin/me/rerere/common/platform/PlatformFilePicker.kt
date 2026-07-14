package me.rerere.common.platform

import kotlinx.serialization.Serializable

@Serializable
enum class PlatformPickedFileKind {
    Image,
    Video,
    Audio,
    Document,
}

data class PlatformPickedFile(
    val storagePath: String,
    val localUrl: String,
    val displayName: String,
    val mimeType: String,
    val kind: PlatformPickedFileKind,
)

fun interface PlatformFilePicker {
    /** A successful null result means that the user cancelled the picker. */
    fun pickFile(onResult: (Result<PlatformPickedFile?>) -> Unit)
}
