package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent + in-memory cache for provider balance values so the UI displays
 * the last known credit amount immediately without flickering to "~" while the
 * background network refresh runs.
 */
object ProviderBalanceCache {
    private val memoryCache = ConcurrentHashMap<String, String>()
    private const val PREFS_NAME = "provider_balance_cache"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun get(context: Context, key: String): String? {
        memoryCache[key]?.let { return it }
        val saved = getPrefs(context).getString(key, null)
        if (saved != null) {
            memoryCache[key] = saved
        }
        return saved
    }

    fun put(context: Context, key: String, value: String) {
        memoryCache[key] = value
        getPrefs(context).edit().putString(key, value).apply()
    }
}

@Composable
fun ProviderBalanceText(
    providerSetting: ProviderSetting,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    if (!providerSetting.balanceOption.enabled || providerSetting !is ProviderSetting.OpenAI) {
        // Balance option is disabled or provider is not OpenAI type
        return
    }

    val context = LocalContext.current
    val providerManager = koinInject<ProviderManager>()
    val secretKeyManager = koinInject<SecretKeyManager>()

    val cacheKey = "balance_${providerSetting.id}_${providerSetting.balanceOption.apiPath}_${providerSetting.balanceOption.resultPath}"
    val cachedInitial = remember(cacheKey) {
        ProviderBalanceCache.get(context, cacheKey) ?: "~"
    }

    val poolHash = remember(providerSetting.apiKeyPool, providerSetting.apiKey) {
        (providerSetting.apiKeyPool.map { "${it.id}:${it.enabled}:${it.key}" } + providerSetting.apiKey).hashCode()
    }

    val value = produceState(
        initialValue = cachedInitial,
        key1 = providerSetting.id,
        key2 = providerSetting.balanceOption,
        key3 = poolHash,
    ) {
        // Show cached balance immediately if available
        val cached = ProviderBalanceCache.get(context, cacheKey)
        if (cached != null) {
            value = cached
        }

        // Fetch fresh balance in background
        runCatching {
            val resolvedProvider = secretKeyManager.populateProviderSecrets(providerSetting) as? ProviderSetting.OpenAI ?: providerSetting
            val balance = providerManager.getProviderByType(resolvedProvider).getBalance(resolvedProvider)
            ProviderBalanceCache.put(context, cacheKey, balance)
            value = balance
        }.onFailure {
            // Only show error message if there was no prior cached value
            if (ProviderBalanceCache.get(context, cacheKey) == null) {
                value = "Error: ${it.message ?: "Failed"}"
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MonetizationOn,
            contentDescription = null,
            modifier = Modifier.size(style.fontSize.toDp()),
            tint = color.takeOrElse { LocalContentColor.current }
        )
        Text(
            text = value.value,
            style = style,
            maxLines = 1,
            color = color
        )
    }
}
