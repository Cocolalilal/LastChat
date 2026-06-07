package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TTSVoice
import me.rerere.tts.provider.findTtsVoice
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Composable
fun VoiceSelector(
    voiceId: Uuid?,
    providers: List<TTSProviderSetting>,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onClear: (() -> Unit)? = null,
    onSelect: (TTSVoice) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val selected = providers.findTtsVoice(voiceId)

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showSheet = true }, modifier = modifier) {
            Text(
                text = selected?.second?.name?.ifBlank { selected.second.providerVoiceId }
                    ?: "Select voice",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (allowClear && selected != null) {
            IconButton(onClick = { onClear?.invoke() }) {
                Icon(Icons.Rounded.Close, null)
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            showSheet = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
            }
        ) {
            VoiceList(
                currentVoice = voiceId,
                providers = providers,
                onSelect = {
                    onSelect(it)
                    scope.launch {
                        sheetState.hide()
                        showSheet = false
                    }
                }
            )
        }
    }
}

@Composable
fun VoiceList(
    currentVoice: Uuid?,
    providers: List<TTSProviderSetting>,
    onSelect: (TTSVoice) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search voices...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = AppShapes.SearchField,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            providers.forEach { provider ->
                val voices = provider.voices.filter { voice ->
                    searchQuery.isBlank() ||
                        voice.name.contains(searchQuery, ignoreCase = true) ||
                        voice.providerVoiceId.contains(searchQuery, ignoreCase = true) ||
                        voice.locale?.contains(searchQuery, ignoreCase = true) == true ||
                        provider.name.contains(searchQuery, ignoreCase = true)
                }
                if (voices.isNotEmpty()) {
                    item(key = "header:${provider.id}") {
                        Text(
                            text = provider.name.ifBlank { "TTS Provider" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(voices, key = { it.id }) { voice ->
                        VoicePickerRow(
                            voice = voice,
                            selected = voice.id == currentVoice,
                            onClick = { onSelect(voice) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePickerRow(
    voice: TTSVoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (LocalDarkMode.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = voice.name.ifBlank { voice.providerVoiceId.ifBlank { "Voice" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val locale = voice.locale
                    if (!locale.isNullOrBlank()) Tag { Text(locale) }
                    Tag { Text("x${"%.2f".format(voice.speed)}") }
                    Tag { Text("p${"%.2f".format(voice.pitch)}") }
                }
            }
        }
    }
}
