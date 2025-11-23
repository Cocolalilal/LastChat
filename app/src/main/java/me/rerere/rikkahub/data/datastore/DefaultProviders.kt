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
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

val GEMINI_2_5_FLASH_ID = Uuid.parse("5f4d3c2b-1a0e-9d8c-7b6a-543210fedcba")

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        builtIn = true,
        models = listOf(
            Model(
                id = Uuid.parse("ea7b9574-e590-42ac-a9ac-01e3aa213f4f"),
                modelId = "gpt-4o",
                displayName = "GPT-4o",
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            ),
            Model(
                id = Uuid.parse("5c33502d-2307-40bd-83fc-133f504bb0c9"),
                modelId = "gpt-4o-mini",
                displayName = "GPT-4o Mini",
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            ),
        )
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Google",
        apiKey = "",
        enabled = true,
        builtIn = true,
        models = listOf(
            Model(
                id = Uuid.parse("5f4d3c2b-1a0e-9d8c-7b6a-543210fedcba"),
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            ),
            Model(
                id = Uuid.parse("a1b2c3d4-e5f6-7890-1234-567890abcdef"),
                modelId = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            ),
            Model(
                id = Uuid.parse("f1e2d3c4-b5a6-9780-1234-098765fedcba"),
                modelId = "gemini-1.5-flash",
                displayName = "Gemini 1.5 Flash",
                inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
                outputModalities = listOf(Modality.TEXT),
                abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
            )
        )
    ),
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
