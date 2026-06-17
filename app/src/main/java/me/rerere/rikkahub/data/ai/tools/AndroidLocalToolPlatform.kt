package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.utils.createImageFileFromBase64
import me.rerere.rikkahub.utils.getImagesDir
import java.io.File
import java.security.MessageDigest

data class SavedGeneratedToolImage(
    val uri: String,
    val path: String,
    val markdownImage: String,
)

interface GeneratedToolImageSaver {
    suspend fun save(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
    ): SavedGeneratedToolImage
}

internal object AndroidLocalToolPlatform {
    fun pathExists(path: String): Boolean {
        return File(path).exists()
    }

    fun shortStableHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(8)
    }
}

class AndroidGeneratedToolImageSaver(
    private val context: Context,
    private val genMediaRepository: GenMediaRepository,
) : GeneratedToolImageSaver {
    override suspend fun save(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
    ): SavedGeneratedToolImage = withContext(Dispatchers.IO) {
        val imagesDir = context.getImagesDir()
        val timestamp = System.currentTimeMillis()
        val safeModelName = modelName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48).ifBlank { "image" }
        val imageFile = File(imagesDir, "${timestamp}_${safeModelName}_tool_$index.png")
        context.createImageFileFromBase64(item.data, imageFile.absolutePath)
        genMediaRepository.insertMedia(
            GenMediaEntity(
                path = "images/${imageFile.name}",
                modelId = modelName,
                prompt = prompt,
                createAt = timestamp,
            )
        )
        val uri = "file://${imageFile.absolutePath}"
        SavedGeneratedToolImage(
            uri = uri,
            path = imageFile.absolutePath,
            markdownImage = "![Generated image]($uri)",
        )
    }
}
