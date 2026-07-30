package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.MemorySearchService
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

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
        MemorySearchService(get(), get(), get(), get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
                extraBindMounts = listOf(
                    WorkspaceBindMount(
                        source = File(context.filesDir, "skills").apply { mkdirs() },
                        target = "/skills",
                    ),
                    WorkspaceBindMount(
                        source = File(context.filesDir, "tool_outputs").apply { mkdirs() },
                        target = "/tool_outputs",
                    ),
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        me.rerere.rikkahub.data.repository.WorkspaceRepository(
            dao = get(),
            settingsStore = get(),
            manager = get(),
            rootfsInstaller = get(),
        )
    }
}
