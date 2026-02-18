package me.rerere.rikkahub.di

import me.rerere.rikkahub.data.ai.memory.DecayEngine
import me.rerere.rikkahub.data.ai.memory.MemoryAgent
import me.rerere.rikkahub.data.ai.memory.RelationExtractor
import me.rerere.rikkahub.data.ai.memory.TimelineManager
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.PersonProfileRepository
import me.rerere.rikkahub.service.PersonProfileService
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get())
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

    single {
        GraphMemoryRepository(get(), get(), get(), get(), get())
    }

    single {
        RelationExtractor(providerManager = get(), settingsStore = get(), graphRepo = get())
    }

    single {
        DecayEngine(nodeDAO = get(), edgeDAO = get())
    }

    single {
        TimelineManager(timelineEventDAO = get(), nodeDAO = get())
    }

    single {
        MemoryAgent(
            graphRepo = get(),
            extractor = get(),
            decayEngine = get(),
            timelineManager = get(),
            embeddingService = get(),
        )
    }
}
