package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "",
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "data.total_credits - data.total_usage",
        )
    ),
)
