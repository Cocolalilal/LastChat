package me.rerere.rikkahub.ui.components.richtext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import androidx.activity.compose.LocalActivity
import me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.ui.ToastType
import android.util.LruCache
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.exportImage
import androidx.compose.ui.text.style.TextOverflow
import me.rerere.rikkahub.utils.toCssHex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val EXPORT_WATERMARK = "LastChat"

// Simple LRU cache for mermaid heights (max 100 entries)
private val mermaidHeightCache = LruCache<String, Int>(100)

/**
 * Normalize mermaid code by replacing problematic Unicode characters
 * that AI models sometimes generate (fancy quotes, non-breaking hyphens, etc.)
 */
private fun String.normalizeMermaidCode(): String {
    return this
        // Replace non-breaking hyphens with regular hyphens
        .replace('\u2011', '-')  // Non-breaking hyphen
        .replace('\u2010', '-')  // Hyphen
        .replace('\u2012', '-')  // Figure dash
        .replace('\u2013', '-')  // En dash
        .replace('\u2014', '-')  // Em dash
        // Replace fancy quotes with regular quotes
        .replace('\u201C', '"')  // Left double quote
        .replace('\u201D', '"')  // Right double quote
        .replace('\u2018', '\'') // Left single quote
        .replace('\u2019', '\'') // Right single quote
        // Escape < and > for HTML safety (but not &, which may be used for entities)
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

/**
 * A component that renders Mermaid diagrams.
 *
 * @param code The Mermaid diagram code
 * @param modifier The modifier to be applied to the component
 */
@Composable
fun Mermaid(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkMode = LocalDarkMode.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val toaster = LocalToaster.current

    var contentHeight by remember { mutableIntStateOf(mermaidHeightCache.get(code) ?: 150) }
    val height = with(density) {
        contentHeight.toDp()
    }
    val jsInterface = remember {
        MermaidInterface(
            onHeightChanged = { height ->
                // 需要乘以density
                // https://stackoverflow.com/questions/43394498/how-to-get-the-full-height-of-in-android-webview
                contentHeight = (height * density.density).toInt()
                mermaidHeightCache.put(code, contentHeight)
            },
                    onExportImage = { base64Image ->
                runCatching {
                    check(base64Image.isNotBlank()) { "Exported image was empty" }
                    activity?.let {
                        // 解码Base64图像并保存
                        val imageBytes = base64Decode(base64Image)
                        val bitmap: Bitmap? = decodeBitmapWithBounds(imageBytes, 2048, 2048)
                        checkNotNull(bitmap) { "Could not decode exported image" }
                        context.exportImage(
                            it,
                            bitmap,
                            "mermaid_${System.currentTimeMillis()}.png"
                        )
                    }
                    toaster.show(
                        context.getString(R.string.mermaid_export_success),
                        type = ToastType.Success
                    )
                }.onFailure {
                    it.printStackTrace()
                    toaster.show(
                        context.getString(R.string.mermaid_export_failed),
                        type = ToastType.Error
                    )
                }
            }
        )
    }

    val html = remember(code, colorScheme) {
        buildMermaidHtml(
            code = code,
            theme = if (darkMode) MermaidTheme.DARK else MermaidTheme.DEFAULT,
            colorScheme = colorScheme,
        )
    }

    val webViewState = rememberWebViewState(
        data = html,
        mimeType = "text/html",
        encoding = "UTF-8",
        interfaces = mapOf(
            "AndroidInterface" to jsInterface
        ),
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
        }
    )

    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    var preview by remember { mutableStateOf(false) }

    var expandState by remember(effectiveDisplay.codeBlockAutoCollapse) {
        mutableStateOf(
            if (effectiveDisplay.codeBlockAutoCollapse) CodeBlockState.Collapsed
            else CodeBlockState.Expanded
        )
    }

    val shellColor = colorScheme.surfaceContainerHigh
    val headerColor = colorScheme.surfaceContainerHighest
    val bodyColor = colorScheme.surfaceContainerLow
    val footerColor = bodyColor
    val outlineColor = colorScheme.outline.copy(alpha = 0.18f)
    val actionTextColor = colorScheme.onSurfaceVariant

    val footerText = if (expandState == CodeBlockState.Collapsed) {
        stringResource(R.string.activity_timeline_expand)
    } else {
        stringResource(R.string.activity_timeline_collapse)
    }


    Surface(
        modifier = modifier,
        shape = AppShapes.InputField,
        color = shellColor,
        contentColor = colorScheme.onSurface,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        val chatAnimationsEnabled = LocalChatAnimationsEnabled.current
        Column(
            modifier = Modifier
                .clipToBounds()
                .then(
                    if (chatAnimationsEnabled) {
                        Modifier.animateContentSize(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "mermaid",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = actionTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.height(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CodeBlockActionButton(
                            icon = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.mermaid_export),
                            tint = actionTextColor,
                            onClick = {
                                webViewState.webView?.evaluateJavascript(
                                    "exportSvgToPng();",
                                    null
                                )
                            },
                            hapticsEnabled = settings.displaySetting.enableUIHaptics
                        )
                        CodeBlockActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.mermaid_preview),
                            tint = actionTextColor,
                            onClick = { preview = true },
                            hapticsEnabled = settings.displaySetting.enableUIHaptics
                        )
                    }
                }
            }

            if (expandState == CodeBlockState.Expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bodyColor)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    WebView(
                        state = webViewState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height),
                        onUpdated = {
                            it.evaluateJavascript("calculateAndSendHeight();", null)
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerColor)
            ) {
                CodeBlockFooter(
                    expanded = expandState == CodeBlockState.Expanded,
                    footerText = footerText,
                    footerAction = codeBlockFooterAction(expandState),
                    onToggle = {
                        haptics.perform(HapticPattern.Pop)
                        expandState = if (expandState == CodeBlockState.Collapsed) CodeBlockState.Expanded else CodeBlockState.Collapsed
                    }
                )
            }
        }
    }

    if (preview) {
        ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                preview = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = false,
            dragHandle = {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            preview = false
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.a11y_close)
                        )
                    }
                }
                WebView(
                    state = rememberWebViewState(
                        data = html,
                        mimeType = "text/html",
                        encoding = "UTF-8",
                        interfaces = mapOf(
                            "AndroidInterface" to jsInterface
                        ),
                        settings = {
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        }
    }
}

/**
 * JavaScript interface to receive height updates and handle image export from the WebView
 */
private class MermaidInterface(
    private val onHeightChanged: (Int) -> Unit,
    private val onExportImage: (String) -> Unit
) {
    @JavascriptInterface
    fun updateHeight(height: Int) {
        onHeightChanged(height)
    }

    @JavascriptInterface
    fun exportImage(base64Image: String) {
        onExportImage(base64Image)
    }
}

/**
 * Builds HTML with Mermaid JS to render the diagram
 */
private fun buildMermaidHtml(
    code: String,
    theme: MermaidTheme,
    colorScheme: ColorScheme,
): String {
    // 将 ColorScheme 颜色转为 HEX 字符串
    val primaryColor = colorScheme.primaryContainer.toCssHex()
    val secondaryColor = colorScheme.secondaryContainer.toCssHex()
    val tertiaryColor = colorScheme.tertiaryContainer.toCssHex()
    val background = colorScheme.background.toCssHex()
    val surface = colorScheme.surface.toCssHex()
    val onSurfaceVariant = colorScheme.onSurfaceVariant.toCssHex()
    val onPrimary = colorScheme.onPrimaryContainer.toCssHex()
    val onSecondary = colorScheme.onSecondaryContainer.toCssHex()
    val onTertiary = colorScheme.onTertiaryContainer.toCssHex()
    val onBackground = colorScheme.onBackground.toCssHex()
    val errorColor = colorScheme.error.toCssHex()
    val onErrorColor = colorScheme.onError.toCssHex()

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes, maximum-scale=5.0">
            <title>Mermaid Diagram</title>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: transparent;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: auto;
                    background-color: ${background};
                }
                .mermaid {
                    width: 100%;
                    padding: 8px;
                }
            </style>
        </head>
        <body>
            <pre class="mermaid">
                ${code.normalizeMermaidCode()}
            </pre>
            <script>
              mermaid.initialize({
                    startOnLoad: false,
                    theme: '${theme.value}',
                    themeVariables: {
                        primaryColor: '${primaryColor}',
                        primaryTextColor: '${onPrimary}',
                        primaryBorderColor: '${primaryColor}',

                        secondaryColor: '${secondaryColor}',
                        secondaryTextColor: '${onSecondary}',
                        secondaryBorderColor: '${secondaryColor}',

                        tertiaryColor: '${tertiaryColor}',
                        tertiaryTextColor: '${onTertiary}',
                        tertiaryBorderColor: '${tertiaryColor}',

                        background: '${background}',
                        mainBkg: '${primaryColor}',
                        secondBkg: '${secondaryColor}',

                        lineColor: '${onBackground}',
                        textColor: '${onBackground}',

                        nodeBkg: '${surface}',
                        nodeBorder: '${primaryColor}',
                        clusterBkg: '${surface}',
                        clusterBorder: '${primaryColor}',

                        // 序列图变量
                        actorBorder: '${primaryColor}',
                        actorBkg: '${surface}',
                        actorTextColor: '${onBackground}',
                        actorLineColor: '${primaryColor}',

                        // 甘特图变量
                        taskBorderColor: '${primaryColor}',
                        taskBkgColor: '${primaryColor}',
                        taskTextLightColor: '${onPrimary}',
                        taskTextDarkColor: '${onBackground}',

                        // 状态图变量
                        labelColor: '${onBackground}',
                        errorBkgColor: '${errorColor}',
                        errorTextColor: '${onErrorColor}'
                    }
              });

              function calculateAndSendHeight() {
                    // 获取实际内容高度，考虑缩放因素
                    const contentElement = document.querySelector('.mermaid');
                    const contentBox = contentElement.getBoundingClientRect();
                    // 添加内边距和一点额外空间以确保完整显示
                    const height = Math.ceil(contentBox.height) + 20;

                    // 处理移动设备的初始缩放
                    const visualViewportScale = window.visualViewport ? window.visualViewport.scale : 1;
                    console.warn('visualViewportScale', visualViewportScale)
                    const adjustedHeight = Math.ceil(height * visualViewportScale);

                    AndroidInterface.updateHeight(adjustedHeight);
              }

              mermaid.run({
                    querySelector: '.mermaid'
              }).catch((err) => {
                 console.error(err);
              }).then(() => {
                calculateAndSendHeight();
              });

              // 监听窗口大小变化以重新计算高度
              window.addEventListener('resize', calculateAndSendHeight);

              // 导出SVG为PNG图像
              window.exportSvgToPng = function() {
                try {
                    const svgElement = document.querySelector('.mermaid svg');
                    if (!svgElement) {
                        console.error('No SVG element found');
                        AndroidInterface.exportImage(''); // Notify error or send empty
                        return;
                    }

                    // Get SVG's dimensions
                    const svgRect = svgElement.getBoundingClientRect();
                    const width = svgRect.width;
                    const height = svgRect.height;

                    // Set canvas dimensions with scaling for better resolution
                    const scaleFactor = window.devicePixelRatio * 2; // Increase resolution
                    const padding = 24 * scaleFactor;
                    const footerHeight = 34 * scaleFactor;
                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');
                    canvas.width = Math.ceil(width * scaleFactor + padding * 2);
                    canvas.height = Math.ceil(height * scaleFactor + padding * 2 + footerHeight);

                    // Serialize SVG to XML
                    const svgXml = new XMLSerializer().serializeToString(svgElement);
                    const svgBase64 = btoa(unescape(encodeURIComponent(svgXml))); // Properly encode to base64

                    const img = new Image();
                    img.onload = function() {
                        // Match LastChat's Material surface and leave room for branding.
                        ctx.fillStyle = '${surface}';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);

                        // Draw the SVG image onto the canvas
                        ctx.drawImage(
                            img,
                            padding,
                            padding,
                            width * scaleFactor,
                            height * scaleFactor
                        );

                        // Draw subtle LastChat branding.
                        ctx.font = Math.round(12 * scaleFactor) + 'px sans-serif';
                        ctx.fillStyle = '${onSurfaceVariant}';
                        ctx.fillText('${EXPORT_WATERMARK}', padding, canvas.height - padding);

                        // Get PNG image as base64
                        const pngBase64 = canvas.toDataURL('image/png').split(',')[1];
                        AndroidInterface.exportImage(pngBase64);
                    };
                    img.onerror = function(e) {
                        console.error('Error loading SVG image:', e);
                        AndroidInterface.exportImage(''); // Notify error or send empty
                    }
                    img.src = 'data:image/svg+xml;base64,' + svgBase64;
                } catch (e) {
                    console.error('Error exporting SVG:', e);
                    AndroidInterface.exportImage(''); // Notify error or send empty
                }
              };
            </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Enum class for Mermaid diagram themes
 */
enum class MermaidTheme(val value: String) {
    DEFAULT("default"),
    DARK("dark"),
}

@OptIn(ExperimentalEncodingApi::class)
private fun base64Decode(value: String): ByteArray = Base64.decode(value)

private fun decodeBitmapWithBounds(data: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    options.inJustDecodeBounds = false
    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight)
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
