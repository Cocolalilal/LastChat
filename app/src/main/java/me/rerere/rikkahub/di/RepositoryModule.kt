package me.rerere.rikkahub.di

import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ConversationRepository(
            context = get(),
            conversationDAO = get(),
            chatEpisodeDAO = get(),
            embeddingCacheDAO = get(),
            dailyActivityDAO = get(),
            usageStatsDAO = get(),
        )
    }

    single {
        EmbeddingService(get(), get())
    }

    single {
        MemoryRepository(
            memoryDAO = get(),
            chatEpisodeDAO = get(),
            embeddingService = get(),
            embeddingCacheDAO = get(),
        )
    }

    single {
        GenMediaRepository(get())
    }
}
