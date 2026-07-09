package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting

// A fresh install starts with an empty provider list. No providers are seeded — the user adds
// them (including the on-device "Local" provider) via the provider presets / first-time setup.
// See ProviderPresets.kt (SPECIAL_PROVIDER_PRESETS / LOCAL_PROVIDER_PRESET).
val DEFAULT_PROVIDERS = emptyList<ProviderSetting>()
