package me.rerere.rikkahub.ui.components.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.common.android.appTempFolder
import okio.buffer
import okio.sink
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A full-screen dialog for cropping an image to a square aspect ratio.
 * The crop area is always square, based on the shorter side of the image.
 * 
 * @param sourceUri The URI of the image to crop
 * @param onCropComplete Called with the URI of the cropped image when complete
 * @param onCancel Called when the user cancels the crop operation
 */
@Composable
fun SquareCropImageScreen(
    sourceUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Original bitmap
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    
    // Load the bitmap with safe downsampling
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val loadedBitmap = me.rerere.rikkahub.utils.ImageUtils.loadOptimizedBitmap(
                    context, sourceUri, maxSize = 4096
                )
                if (loadedBitmap != null) {
                    bitmap = loadedBitmap
                    aspectRatio = loadedBitmap.width.toFloat() / loadedBitmap.height
                } else {
                    onCancel()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onCancel()
            }
        }
    }
    
    // Container dimensions
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    
    // Crop area
    var cropArea by remember { mutableStateOf(Rect.Zero) }
    var originalSize by remember { mutableStateOf(Size.Zero) }
    
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Image preview with square crop overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onSizeChanged { size ->
                            containerWidth = size.width.toFloat()
                            containerHeight = size.height.toFloat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    bitmap?.let { bmp ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Image
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Square crop overlay
                            if (containerWidth > 0 && containerHeight > 0) {
                                SquareCropBox(
                                    containerWidth = containerWidth,
                                    containerHeight = containerHeight,
                                    mediaAspectRatio = aspectRatio,
                                    enabled = true,
                                    onAreaChanged = { area, original ->
                                        cropArea = area
                                        originalSize = original
                                    },
                                    onCropDone = { /* Optional haptic feedback */ }
                                )
                            }
                        }
                    } ?: Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            color = Color.White
                        )
                    }
                }
                
                // Bottom bar with buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button with physics animation
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    val cancelPressed by cancelInteractionSource.collectIsPressedAsState()
                    val cancelScale by animateFloatAsState(
                        targetValue = if (cancelPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                        label = "cancel_scale"
                    )
                    val cancelAlpha by animateFloatAsState(
                        targetValue = if (cancelPressed) 0.7f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "cancel_alpha"
                    )
                    
                    FilledTonalButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = cancelScale
                                scaleY = cancelScale
                                alpha = cancelAlpha
                            },
                        shape = RoundedCornerShape(16.dp),
                        interactionSource = cancelInteractionSource
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cancel))
                    }
                    
                    // Done button with physics animation
                    val doneInteractionSource = remember { MutableInteractionSource() }
                    val donePressed by doneInteractionSource.collectIsPressedAsState()
                    val doneScale by animateFloatAsState(
                        targetValue = if (donePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                        label = "done_scale"
                    )
                    val doneAlpha by animateFloatAsState(
                        targetValue = if (donePressed) 0.7f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "done_alpha"
                    )
                    
                    Button(
                        onClick = {
                            val bmp = bitmap ?: return@Button
                            scope.launch {
                                val croppedUri = squareCropAndSaveImage(
                                    context = context,
                                    sourceBitmap = bmp,
                                    cropArea = cropArea,
                                    originalSize = originalSize
                                )
                                if (croppedUri != null) {
                                    onCropComplete(croppedUri)
                                } else {
                                    onCancel()
                                }
                            }
                        },
                        enabled = bitmap != null,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = doneScale
                                scaleY = doneScale
                                alpha = doneAlpha
                            },
                        shape = RoundedCornerShape(16.dp),
                        interactionSource = doneInteractionSource
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

/**
 * Crops the source bitmap according to the crop area and saves it to a temp file.
 * Returns the URI of the saved file, or null on failure.
 */
private suspend fun squareCropAndSaveImage(
    context: android.content.Context,
    sourceBitmap: Bitmap,
    cropArea: Rect,
    originalSize: Size
): Uri? = withContext(Dispatchers.IO) {
    try {
        // Calculate the scale factor between displayed size and actual bitmap size
        val scaleX = sourceBitmap.width / originalSize.width
        val scaleY = sourceBitmap.height / originalSize.height
        
        // Calculate crop coordinates in actual bitmap coordinates
        val cropLeft = (cropArea.left * scaleX).roundToInt().coerceIn(0, sourceBitmap.width)
        val cropTop = (cropArea.top * scaleY).roundToInt().coerceIn(0, sourceBitmap.height)
        val cropWidth = (cropArea.width * scaleX).roundToInt().coerceIn(1, sourceBitmap.width - cropLeft)
        val cropHeight = (cropArea.height * scaleY).roundToInt().coerceIn(1, sourceBitmap.height - cropTop)
        
        // Ensure square - use the smaller dimension
        val squareSize = min(cropWidth, cropHeight)
        
        // Create cropped bitmap
        val croppedBitmap = Bitmap.createBitmap(
            sourceBitmap,
            cropLeft,
            cropTop,
            squareSize,
            squareSize
        )
        
        // Save to persistent file
        val avatarsDir = File(context.filesDir, "avatars")
        if (!avatarsDir.exists()) {
            avatarsDir.mkdirs()
        }
        val outputFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.png")
        outputFile.sink().buffer().outputStream().use { stream ->
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        
        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
