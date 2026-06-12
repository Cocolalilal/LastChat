package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

@Composable
fun QRCode(
    value: String,
    modifier: Modifier = Modifier,
    size: Int = 512,
    color: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified
) {
    val actualColor = color.takeOrElse { Color.Black }
    val actualBackgroundColor = backgroundColor.takeOrElse { Color.White }

    val qrCodeWriter = remember { QRCodeWriter() }
    val bitMatrixResult = remember(value) {
        runCatching {
            qrCodeWriter.encode(
                value,
                BarcodeFormat.QR_CODE,
                1,
                1,
                mapOf(EncodeHintType.MARGIN to 0)
            )
        }
    }
    val bitMatrix = bitMatrixResult.getOrNull()
    if (bitMatrix == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "This configuration is too large to show as a QR code. Use the share button instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }
    val bitmap = remember(bitMatrix, size, actualColor, actualBackgroundColor) {
        val quietZonePx = (size * QR_CODE_QUIET_ZONE_RATIO).roundToInt()
        val qrContentSize = size - quietZonePx * 2

        createBitmap(size, size).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val qrX = x - quietZonePx
                    val qrY = y - quietZonePx
                    val isInQrContent = qrX in 0 until qrContentSize && qrY in 0 until qrContentSize
                    val isQrPixel = if (isInQrContent) {
                        val matrixX = qrX * bitMatrix.width / qrContentSize
                        val matrixY = qrY * bitMatrix.height / qrContentSize
                        bitMatrix[matrixX, matrixY]
                    } else {
                        false
                    }
                    this[x, y] = if (isQrPixel) actualColor.toArgb() else actualBackgroundColor.toArgb()
                }
            }
        }
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "qrcode",
        modifier = modifier
    )
}

private const val QR_CODE_QUIET_ZONE_RATIO = 0.1f
