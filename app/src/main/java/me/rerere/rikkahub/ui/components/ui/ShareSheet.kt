package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.JsonInstant
import java.util.UUID
import kotlin.io.encoding.Base64

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()
    if (state.isShow) {
        val shareValue = state.currentProvider?.encodeForShare() ?: ""
        val qrValues = remember(shareValue) {
            splitProviderShareIntoQrPayloads(shareValue)
        }
        var currentQrIndex by remember(qrValues) { mutableStateOf(0) }

        ModalBottomSheet(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.provider_share_title), style = MaterialTheme.typography.titleLarge)

                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                shareValue
                            )
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Share, null)
                    }
                }

                QRCode(
                    value = qrValues.getOrElse(currentQrIndex) { shareValue },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                if (qrValues.size > 1) {
                    Text(
                        text = stringResource(
                            R.string.provider_share_qr_progress,
                            currentQrIndex + 1,
                            qrValues.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                currentQrIndex = (currentQrIndex - 1).coerceAtLeast(0)
                            },
                            enabled = currentQrIndex > 0,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                            Text(stringResource(R.string.back))
                        }
                        Button(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                currentQrIndex = (currentQrIndex + 1).coerceAtMost(qrValues.lastIndex)
                            },
                            enabled = currentQrIndex < qrValues.lastIndex,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(stringResource(R.string.provider_share_qr_next))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }
}

fun ProviderSetting.encodeForShare(): String {
    return buildString {
        append(PROVIDER_SHARE_PREFIX)

        val value = JsonInstant.encodeToString(this@encodeForShare)
        append(Base64.encode(value.encodeToByteArray()))
    }
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith(PROVIDER_SHARE_PREFIX)) { "Invalid provider setting string" }

    // 去掉前缀
    val base64Str = value.removePrefix(PROVIDER_SHARE_PREFIX)

    // Base64解码
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()

    return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
}

sealed class ProviderShareQrContent {
    data class Single(val provider: ProviderSetting) : ProviderShareQrContent()
    data class Part(val part: ProviderShareQrPart) : ProviderShareQrContent()
}

data class ProviderShareQrPart(
    val transferId: String,
    val index: Int,
    val total: Int,
    val data: String
)

fun parseProviderShareQrContent(value: String): ProviderShareQrContent {
    if (value.startsWith(PROVIDER_SHARE_PREFIX)) {
        return ProviderShareQrContent.Single(decodeProviderSetting(value))
    }

    require(value.startsWith(PROVIDER_SHARE_PART_PREFIX)) { "Invalid provider setting string" }
    val parts = value.removePrefix(PROVIDER_SHARE_PART_PREFIX).split(":", limit = 4)
    require(parts.size == 4) { "Invalid provider QR part" }

    val index = parts[1].toIntOrNull() ?: error("Invalid provider QR part index")
    val total = parts[2].toIntOrNull() ?: error("Invalid provider QR part count")
    require(total > 1) { "Invalid provider QR part count" }
    require(index in 1..total) { "Invalid provider QR part index" }
    require(parts[3].isNotEmpty()) { "Invalid provider QR part data" }

    return ProviderShareQrContent.Part(
        ProviderShareQrPart(
            transferId = parts[0],
            index = index,
            total = total,
            data = parts[3]
        )
    )
}

fun decodeProviderSettingParts(parts: Collection<ProviderShareQrPart>): ProviderSetting {
    require(parts.isNotEmpty()) { "No provider QR parts scanned" }
    val transferId = parts.first().transferId
    val total = parts.first().total
    require(parts.all { it.transferId == transferId && it.total == total }) {
        "Provider QR parts belong to different transfers"
    }
    require(parts.map { it.index }.toSet().size == total) {
        "Provider QR transfer is incomplete"
    }

    val base64Value = parts.sortedBy { it.index }.joinToString(separator = "") { it.data }
    return decodeProviderSetting(PROVIDER_SHARE_PREFIX + base64Value)
}

private fun splitProviderShareIntoQrPayloads(value: String): List<String> {
    if (value.isBlank() || canEncodeQr(value)) {
        return listOf(value)
    }

    val base64Value = value.removePrefix(PROVIDER_SHARE_PREFIX)
    val transferId = UUID.randomUUID().toString()
    var chunkSize = PROVIDER_SHARE_QR_CHUNK_SIZE

    while (chunkSize >= PROVIDER_SHARE_MIN_QR_CHUNK_SIZE) {
        val chunks = base64Value.chunked(chunkSize)
        val payloads = chunks.mapIndexed { index, chunk ->
            buildProviderSharePartPayload(
                transferId = transferId,
                index = index + 1,
                total = chunks.size,
                data = chunk
            )
        }
        if (payloads.all(::canEncodeQr)) {
            return payloads
        }
        chunkSize -= PROVIDER_SHARE_QR_CHUNK_STEP
    }

    val chunks = base64Value.chunked(PROVIDER_SHARE_MIN_QR_CHUNK_SIZE)
    return chunks.mapIndexed { index, chunk ->
        buildProviderSharePartPayload(
            transferId = transferId,
            index = index + 1,
            total = chunks.size,
            data = chunk
        )
    }
}

private fun buildProviderSharePartPayload(
    transferId: String,
    index: Int,
    total: Int,
    data: String
): String {
    return "$PROVIDER_SHARE_PART_PREFIX$transferId:$index:$total:$data"
}

private fun canEncodeQr(value: String): Boolean {
    return runCatching {
        QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(EncodeHintType.MARGIN to 0)
        )
    }.isSuccess
}

private const val PROVIDER_SHARE_PREFIX = "ai-provider:v1:"
private const val PROVIDER_SHARE_PART_PREFIX = "ai-provider:v1-part:"
private const val PROVIDER_SHARE_QR_CHUNK_SIZE = 900
private const val PROVIDER_SHARE_MIN_QR_CHUNK_SIZE = 120
private const val PROVIDER_SHARE_QR_CHUNK_STEP = 120

class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState {
    return remember { ShareSheetState() }
}
