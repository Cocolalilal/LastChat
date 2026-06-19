package me.rerere.rikkahub.ui.components.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

/**
 * A full-screen dialog for cropping an image.
 * 
 * @param sourceUri The URI of the image to crop
 * @param onCropComplete Called with the URI of the cropped image when complete
 * @param onCancel Called when the user cancels the crop operation
 */
@Composable
fun CropImageScreen(
    sourceUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    
    // Original bitmap (never rotated)
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Display bitmap (rotated version for display)
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    
    // Rotation state (counts number of 90° rotations)
    var rotationCount by remember { mutableIntStateOf(0) }
    
    // Load the original bitmap with safe downsampling and EXIF orientation correction
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val loadedBitmap = me.rerere.rikkahub.utils.ImageUtils.loadOptimizedBitmap(
                    context, sourceUri, maxSize = 4096
                )
                if (loadedBitmap != null) {
                    originalBitmap = loadedBitmap
                    displayBitmap = loadedBitmap
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
    
    // Rotate the display bitmap when rotationCount changes
    LaunchedEffect(rotationCount, originalBitmap) {
        val original = originalBitmap ?: return@LaunchedEffect
        if (rotationCount == 0) {
            displayBitmap = original
            aspectRatio = original.width.toFloat() / original.height
        } else {
            withContext(Dispatchers.Default) {
                val degrees = (rotationCount * 90) % 360
                val matrix = Matrix().apply {
                    postRotate(degrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                displayBitmap = rotated
                aspectRatio = rotated.width.toFloat() / rotated.height
            }
        }
    }
    
    // Container dimensions
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    
    // Crop area
    var cropArea by remember { mutableStateOf(Rect.Zero) }
    var originalSize by remember { mutableStateOf(Size.Zero) }
    var isCropping by remember { mutableStateOf(false) }
    val hasValidCropGeometry = cropArea.width > 0f &&
        cropArea.height > 0f &&
        originalSize.width > 0f &&
        originalSize.height > 0f
    
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
                // Image preview with crop overlay
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
                    displayBitmap?.let { bitmap ->
                        // Calculate image display size to fit container
                        val imageAspect = bitmap.width.toFloat() / bitmap.height
                        val containerAspect = if (containerHeight > 0) containerWidth / containerHeight else 1f
                        
                        val (imageWidth, imageHeight) = if (imageAspect > containerAspect) {
                            containerWidth to containerWidth / imageAspect
                        } else {
                            containerHeight * imageAspect to containerHeight
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Image (already rotated via bitmap)
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Crop overlay
                            if (containerWidth > 0 && containerHeight > 0) {
                                CropBox(
                                    containerWidth = containerWidth,
                                    containerHeight = containerHeight,
                                    mediaAspectRatio = aspectRatio,
                                    enabled = true,
                                    onAreaChanged = { area, original ->
                                        cropArea = area
                                        originalSize = original
                                    },
                                    onCropDone = { /* Optional: could add haptic feedback here */ }
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
                    
                    // Rotate button with spring spin animation
                    val rotateInteractionSource = remember { MutableInteractionSource() }
                    val rotatePressed by rotateInteractionSource.collectIsPressedAsState()
                    
                    // Spring rotation animation:
                    // - When pressed: slight counter-clockwise wind-up (-15°)
                    // - When released: spring to current rotation (0° relative, which with increment becomes full spin)
                    val rotateIconRotation by animateFloatAsState(
                        targetValue = if (rotatePressed) -15f else (rotationCount * 90f),
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 400f
                        ),
                        label = "rotate_icon_spin"
                    )
                    val rotateScale by animateFloatAsState(
                        targetValue = if (rotatePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                        label = "rotate_scale"
                    )
                    val rotateAlpha by animateFloatAsState(
                        targetValue = if (rotatePressed) 0.7f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "rotate_alpha"
                    )
                    
                    FilledTonalIconButton(
                        onClick = {
                            rotationCount++
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = rotateScale
                                scaleY = rotateScale
                                alpha = rotateAlpha
                            },
                        shape = CircleShape,
                        interactionSource = rotateInteractionSource
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.RotateRight,
                            contentDescription = stringResource(R.string.a11y_rotate),
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotateIconRotation
                            }
                        )
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
                            val bitmap = displayBitmap ?: return@Button
                            if (!hasValidCropGeometry || isCropping) return@Button
                            isCropping = true
                            scope.launch {
                                val croppedUri = cropAndSaveImage(
                                    context = context,
                                    sourceBitmap = bitmap,
                                    cropArea = cropArea,
                                    originalSize = originalSize
                                )
                                if (croppedUri != null) {
                                    onCropComplete(croppedUri)
                                } else {
                                    isCropping = false
                                    onCancel()
                                }
                            }
                        },
                        enabled = displayBitmap != null && hasValidCropGeometry && !isCropping,
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
private suspend fun cropAndSaveImage(
    context: android.content.Context,
    sourceBitmap: Bitmap,
    cropArea: Rect,
    originalSize: Size
): Uri? = withContext(Dispatchers.IO) {
    try {
        if (
            originalSize.width <= 0f ||
            originalSize.height <= 0f ||
            cropArea.width <= 0f ||
            cropArea.height <= 0f
        ) {
            return@withContext null
        }

        // Calculate the scale factor between displayed size and actual bitmap size
        val scaleX = sourceBitmap.width / originalSize.width
        val scaleY = sourceBitmap.height / originalSize.height
        
        // Calculate crop coordinates in actual bitmap coordinates
        val cropLeft = (cropArea.left * scaleX).roundToInt().coerceIn(0, sourceBitmap.width)
        val cropTop = (cropArea.top * scaleY).roundToInt().coerceIn(0, sourceBitmap.height)
        val cropWidth = (cropArea.width * scaleX).roundToInt().coerceIn(1, sourceBitmap.width - cropLeft)
        val cropHeight = (cropArea.height * scaleY).roundToInt().coerceIn(1, sourceBitmap.height - cropTop)
        
        // Create cropped bitmap
        val croppedBitmap = Bitmap.createBitmap(
            sourceBitmap,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight
        )
        
        val preserveAlpha = croppedBitmap.hasAlpha()
        val outputFile = File(
            context.appTempFolder,
            "cropped_${System.currentTimeMillis()}.${if (preserveAlpha) "png" else "jpg"}"
        )
        outputFile.sink().buffer().outputStream().use { stream ->
            croppedBitmap.compress(
                if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                if (preserveAlpha) 100 else 92,
                stream
            )
        }
        croppedBitmap.recycle()
        
        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

