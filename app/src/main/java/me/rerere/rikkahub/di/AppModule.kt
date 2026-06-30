package me.rerere.rikkahub.di

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.highlight.android.AndroidHighlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.android.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single<Highlighter> {
        AndroidHighlighter(get())
    }

    single {
        LocalTools(get(), get(), get(), get(), get())
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        Firebase.crashlytics
    }

    single {
        Firebase.remoteConfig
    }

    single {
        AILoggingManager()
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatAttachmentRepository = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            workspaceRepository = get(),
            mcpManager = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
        )
    }
}
