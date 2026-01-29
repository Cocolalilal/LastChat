package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A chip that displays a document/file attachment with an appropriate icon.
 * Used in chat input to show attached files.
 */
@Composable
fun DocumentChip(
    fileName: String,
    mimeType: String?,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    val icon = getFileIcon(fileName, mimeType)
    
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 60.dp, max = 160.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = truncateFileName(fileName),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(18.dp)
                    .clickable { onRemove() }
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.secondary),
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}

/**
 * Truncates filename to show start and extension if too long.
 * E.g., "very_long_filename.pdf" -> "very_lon….pdf"
 */
private fun truncateFileName(fileName: String, maxLength: Int = 16): String {
    if (fileName.length <= maxLength) return fileName
    
    val lastDot = fileName.lastIndexOf('.')
    return if (lastDot > 0 && lastDot < fileName.length - 1) {
        val extension = fileName.substring(lastDot)
        val nameWithoutExt = fileName.substring(0, lastDot)
        val availableChars = maxLength - extension.length - 1 // -1 for ellipsis
        if (availableChars > 3) {
            nameWithoutExt.take(availableChars) + "…" + extension
        } else {
            fileName.take(maxLength - 1) + "…"
        }
    } else {
        fileName.take(maxLength - 1) + "…"
    }
}

/**
 * Returns appropriate icon based on file extension and MIME type.
 */
private fun getFileIcon(fileName: String, mimeType: String?): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    // Check by extension first (more reliable)
    return when (extension) {
        // Documents
        "pdf" -> Icons.Rounded.PictureAsPdf
        "doc", "docx", "odt", "rtf" -> Icons.Rounded.Description
        "txt", "md", "markdown" -> Icons.AutoMirrored.Rounded.TextSnippet
        
        // Spreadsheets
        "xls", "xlsx", "csv", "ods" -> Icons.Rounded.TableChart
        
        // Code files
        "py", "js", "ts", "tsx", "jsx", "kt", "java", "c", "cpp", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "dart", "lua", "r", "scala",
        "html", "css", "scss", "sass", "less", "xml", "yaml", "yml", "toml" -> Icons.Rounded.Code
        
        // Data files
        "json" -> Icons.Rounded.DataObject
        
        // Images
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico" -> Icons.Rounded.Image
        
        // Videos
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> Icons.Rounded.VideoFile
        
        // Audio
        "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma" -> Icons.Rounded.AudioFile
        
        // Archives
        "zip", "rar", "7z", "tar", "gz", "bz2" -> Icons.Rounded.FolderZip
        
        else -> {
            // Fall back to MIME type
            when {
                mimeType?.startsWith("text/") == true -> Icons.AutoMirrored.Rounded.TextSnippet
                mimeType?.startsWith("image/") == true -> Icons.Rounded.Image
                mimeType?.startsWith("video/") == true -> Icons.Rounded.VideoFile
                mimeType?.startsWith("audio/") == true -> Icons.Rounded.AudioFile
                mimeType == "application/pdf" -> Icons.Rounded.PictureAsPdf
                mimeType?.contains("zip") == true || mimeType?.contains("compressed") == true -> Icons.Rounded.FolderZip
                else -> Icons.AutoMirrored.Rounded.InsertDriveFile
            }
        }
    }
}
