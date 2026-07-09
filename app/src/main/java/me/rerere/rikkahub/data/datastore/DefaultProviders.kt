package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_CODEX_PROVIDER_ID = Uuid.parse("7ce7e322-b995-4b0c-9d48-42e08dcfcdda")

val DEFAULT_PROVIDERS = listOf(
    // Pinned on-device provider — always present, always first (see normalizeLocalProvider).
    ProviderSetting.LiteRtLocal(),
    ProviderSetting.Codex(
        id = DEFAULT_CODEX_PROVIDER_ID,
        name = "Codex",
        enabled = false,
        builtIn = true,
    ),
)
