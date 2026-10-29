package me.rerere.rikkahub.ui.components.richtext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.ArtifactStorageManager
import me.rerere.rikkahub.ui.components.chat.LocalMessageBubbleShape
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.LocalOpticalFrame
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.writeClipboardText

private val artifactHeightCache = LruCache<String, Int>(150)
private val artifactBitmapCache = LruCache<String, ImageBitmap>(80)

private class ArtifactInterface(
    private val onHeightChanged: (Int) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHeightChanged(height: Int) {
        handler.post {
            onHeightChanged(height)
        }
    }
}

/**
 * Animated dotted 3D thinking orb rendered while an in-world visual artifact is generating.
 * Directly based on Jakub Antalik's thinking-orbs (tilted orbital particle physics).
 */
@Composable
fun ArtifactOrbGenerating(
    modifier: Modifier = Modifier,
    statusText: String = "Forging in-world visual..."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Jakub Antalik Dotted Orbital Thought Orb
            ThinkingOrb(
                size = 64.dp,
                mode = ThinkingOrbMode.ORBITS,
                speed = 1.0f
            )

            // Status Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Render an in-world visual artifact (notes, letters, phone screens, receipts)
 * inside chat flow with self-contained sizing, zero outer halo, and recycling resilience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InWorldArtifact(
    code: String,
    modifier: Modifier = Modifier,
) {
    val darkMode = LocalDarkMode.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val toaster = LocalToaster.current
    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val chatAnimationsEnabled = LocalChatAnimationsEnabled.current
    val scope = rememberCoroutineScope()

    var showSource by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sanitizeResult: ArtifactSanitizeResult = remember(code, darkMode) {
        ArtifactSanitizer.sanitize(code, darkMode)
    }

    when (sanitizeResult) {
        is ArtifactSanitizeResult.Rejected -> {
            HighlightCodeBlock(
                code = code,
                language = "artifact",
                modifier = modifier,
                completeCodeBlock = true
            )
        }
        is ArtifactSanitizeResult.Success -> {
            val estimatedInitial = remember(code) {
                artifactHeightCache.get(code) ?: maxOf(360, (code.length * 0.85f).toInt().coerceAtMost(1200))
            }
            var contentHeightDp by remember(code) {
                mutableIntStateOf(estimatedInitial)
            }
            var cachedBitmap by remember(code) {
                mutableStateOf(artifactBitmapCache.get(code))
            }

            val jsInterface = remember(code) {
                ArtifactInterface { measuredHeight ->
                    if (measuredHeight > 30) {
                        val finalH = measuredHeight + 14
                        contentHeightDp = finalH
                        artifactHeightCache.put(code, finalH)
                    }
                }
            }

            var webViewInstance by remember { mutableStateOf<WebView?>(null) }

            AnimatedContent(
                targetState = showSource,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing))
                },
                label = "ArtifactSourceTransition"
            ) { isSourceMode ->
                if (isSourceMode) {
                    HighlightCodeBlock(
                        code = code,
                        language = "artifact",
                        modifier = modifier,
                        completeCodeBlock = true
                    )
                } else {
                    val frame = LocalOpticalFrame.current
                    val bubbleShape = LocalMessageBubbleShape.current
                    val blockShape = bubbleShape ?: if (frame.nested) frame.innerShape else AppShapes.InputField

                    Surface(
                        modifier = modifier
                            .combinedClickable(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    showActionSheet = true
                                },
                                onLongClick = {
                                    haptics.perform(HapticPattern.Thud)
                                    showActionSheet = true
                                }
                            ),
                        shape = blockShape,
                        color = Color.Transparent,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .then(
                                    if (chatAnimationsEnabled) {
                                        Modifier.animateContentSize(
                                            animationSpec = tween(
                                                durationMillis = 350,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cachedBitmap != null) {
                                Image(
                                    bitmap = cachedBitmap!!,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(contentHeightDp.dp)
                                )
                            } else {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            with(this.settings) {
                                                javaScriptEnabled = true
                                                domStorageEnabled = false
                                                allowFileAccess = false
                                                allowContentAccess = false
                                                setSupportZoom(true)
                                                builtInZoomControls = true
                                                displayZoomControls = false
                                            }
                                            isVerticalScrollBarEnabled = false
                                            isHorizontalScrollBarEnabled = false
                                            overScrollMode = View.OVER_SCROLL_NEVER
                                            tag = code
                                            addJavascriptInterface(jsInterface, "AndroidInterface")
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    view?.evaluateJavascript(
                                                        "(function() { var r = document.getElementById('artifact-root'); return r ? Math.ceil(r.getBoundingClientRect().height) : (document.body.scrollHeight || 0); })();"
                                                    ) { result ->
                                                        val measured = result?.replace("\"", "")?.toIntOrNull() ?: 0
                                                        if (measured > 30) {
                                                            val finalH = measured + 14
                                                            contentHeightDp = finalH
                                                            artifactHeightCache.put(code, finalH)
                                                        }
                                                    }
                                                    view?.postDelayed({
                                                        runCatching {
                                                            val w = view.width
                                                            val h = view.height
                                                            if (w > 0 && h > 0) {
                                                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                                val c = Canvas(b)
                                                                view.draw(c)
                                                                val imgBmp = b.asImageBitmap()
                                                                artifactBitmapCache.put(code, imgBmp)
                                                                cachedBitmap = imgBmp
                                                                if (settings.displaySetting.autoSaveArtifactsToStorage) {
                                                                    scope.launch(Dispatchers.IO) {
                                                                        ArtifactStorageManager.saveArtifact(context, code, b)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }, 300)
                                                }
                                            }
                                            loadDataWithBaseURL(
                                                "http://localhost/",
                                                sanitizeResult.documentHtml,
                                                "text/html",
                                                "utf-8",
                                                null
                                            )
                                            webViewInstance = this
                                        }
                                    },
                                    update = { webView ->
                                        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        webViewInstance = webView
                                        if (webView.tag != code) {
                                            webView.tag = code
                                            webView.loadDataWithBaseURL(
                                                "http://localhost/",
                                                sanitizeResult.documentHtml,
                                                "text/html",
                                                "utf-8",
                                                null
                                            )
                                        } else {
                                            webView.invalidate()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(contentHeightDp.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom action sheet on click/long-press
            if (showActionSheet) {
                ModalBottomSheet(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onDismissRequest = { showActionSheet = false },
                    sheetState = sheetState,
                    shape = AppShapes.BottomSheet
                ) {
                    val groupContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArtifactActionGroupItem(
                                label = stringResource(R.string.artifact_zoom_inspect),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                },
                                position = ArtifactActionItemPosition.FIRST,
                                containerColor = groupContainerColor,
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    scope.launch {
                                        sheetState.hide()
                                        showActionSheet = false
                                        showZoomDialog = true
                                    }
                                }
                            )

                            ArtifactActionGroupItem(
                                label = stringResource(R.string.artifact_copy_html),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                },
                                position = ArtifactActionItemPosition.MIDDLE,
                                containerColor = groupContainerColor,
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    context.writeClipboardText(code)
                                    toaster.show(
                                        context.getString(R.string.artifact_copy_success),
                                        type = ToastType.Success
                                    )
                                    scope.launch {
                                        sheetState.hide()
                                        showActionSheet = false
                                    }
                                }
                            )

                            ArtifactActionGroupItem(
                                label = stringResource(
                                    if (showSource) R.string.artifact_view_rendered
                                    else R.string.artifact_view_source
                                ),
                                icon = {
                                    Icon(
                                        imageVector = if (showSource) Icons.Rounded.Visibility else Icons.Rounded.Code,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                },
                                position = ArtifactActionItemPosition.MIDDLE,
                                containerColor = groupContainerColor,
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    showSource = !showSource
                                    scope.launch {
                                        sheetState.hide()
                                        showActionSheet = false
                                    }
                                }
                            )

                            ArtifactActionGroupItem(
                                label = stringResource(R.string.artifact_save_image),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                },
                                position = ArtifactActionItemPosition.LAST,
                                containerColor = groupContainerColor,
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    val currentActivity = activity
                                    if (currentActivity != null) {
                                        runCatching {
                                            val exportBmp: Bitmap? = if (cachedBitmap != null) {
                                                cachedBitmap!!.asAndroidBitmap()
                                            } else if (webViewInstance != null && webViewInstance!!.width > 0 && webViewInstance!!.height > 0) {
                                                val b = Bitmap.createBitmap(webViewInstance!!.width, webViewInstance!!.height, Bitmap.Config.ARGB_8888)
                                                val c = Canvas(b)
                                                webViewInstance!!.draw(c)
                                                b
                                            } else null

                                            checkNotNull(exportBmp) { "Could not capture full artifact bitmap" }
                                            context.exportImage(
                                                currentActivity,
                                                exportBmp,
                                                "artifact_${System.currentTimeMillis()}.png"
                                            )
                                            toaster.show(
                                                context.getString(R.string.artifact_save_success),
                                                type = ToastType.Success
                                            )
                                        }.onFailure {
                                            toaster.show(
                                                context.getString(R.string.artifact_save_failed),
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                    scope.launch {
                                        sheetState.hide()
                                        showActionSheet = false
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.size(4.dp))

                        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                            Text(stringResource(R.string.artifact_in_world_visual))
                        }
                    }
                }
            }

            if (showZoomDialog) {
                ArtifactZoomDialog(
                    code = sanitizeResult.documentHtml,
                    cachedBitmap = cachedBitmap,
                    onDismissRequest = { showZoomDialog = false }
                )
            }
        }
    }
}

private enum class ArtifactActionItemPosition {
    FIRST,
    MIDDLE,
    LAST,
    SINGLE
}

private fun groupedArtifactActionShape(position: ArtifactActionItemPosition): RoundedCornerShape {
    return when (position) {
        ArtifactActionItemPosition.FIRST -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 10.dp,
            bottomEnd = 10.dp
        )
        ArtifactActionItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
        ArtifactActionItemPosition.LAST -> RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        ArtifactActionItemPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
}

@Composable
private fun ArtifactActionGroupItem(
    label: String,
    icon: @Composable () -> Unit,
    position: ArtifactActionItemPosition,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(groupedArtifactActionShape(position))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun ArtifactZoomDialog(
    code: String,
    cachedBitmap: ImageBitmap?,
    onDismissRequest: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 6.0f)
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cachedBitmap != null) {
                    Image(
                        bitmap = cachedBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                with(this.settings) {
                                    javaScriptEnabled = true
                                    domStorageEnabled = false
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                loadDataWithBaseURL("http://localhost/", code, "text/html", "utf-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(500.dp)
                    )
                }
            }

            // Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.artifact_zoom_inspect),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset Zoom",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom Floating Zoom Controls Pill
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { scale = (scale - 0.25f).coerceAtLeast(0.75f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Remove,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                IconButton(
                    onClick = { scale = (scale + 0.25f).coerceAtMost(6.0f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
