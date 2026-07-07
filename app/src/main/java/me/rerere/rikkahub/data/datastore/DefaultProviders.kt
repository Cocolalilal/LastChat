package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_PROVIDERS = listOf(
    // Pinned on-device provider — always present, always first (see normalizeLocalProvider).
    ProviderSetting.LiteRtLocal(),
)
