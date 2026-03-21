package me.rerere.rikkahub.di

import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ChatAttachmentRepository(
            context = get(),
            chatAttachmentDao = get(),
            conversationAttachmentRefDao = get(),
            conversationDao = get(),
            settingsStore = get(),
            appScope = get(),
        )
    }

    single {
        AppStorageRepository(
            context = get(),
            settingsStore = get(),
            chatAttachmentRepository = get(),
            appScope = get(),
        )
    }

    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        EmbeddingService(get(), get())
    }

    single {
        MemoryRepository(get(), get(), get(), get())
    }

    single {
        GenMediaRepository(get())
    }
}
