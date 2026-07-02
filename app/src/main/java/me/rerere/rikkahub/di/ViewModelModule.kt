package me.rerere.rikkahub.di

import me.rerere.rikkahub.ui.activity.TextSelectionVM
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.developer.DeveloperVM
import me.rerere.rikkahub.ui.pages.imggen.ImgGenVM
import me.rerere.rikkahub.ui.pages.setting.SettingVM

import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerVM
import me.rerere.rikkahub.ui.pages.menu.MenuVM
import me.rerere.rikkahub.ui.pages.onboarding.OnboardingVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params[0],
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatAttachmentRepository = get(),
            chatService = get(),
            updateChecker = get(),
            appScope = get(),
            appStorageRepository = get(),
        )
    }
    viewModel<SettingVM> {
        SettingVM(
            settingsStore = get(),
            mcpManager = get(),
            context = get(),
            platformHttpClient = get(),
            appStorageRepository = get(),
            modelCatalogService = get(),
            modelMetadataResolver = get(),
            memoryRepository = get(),
        )
    }

    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it[0],
            settingsStore = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            context = get(),
            chatEpisodeDAO = get(),
            providerManager = get(),
            appStorageRepository = get(),
        )
    }
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it[0],
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::DeveloperVM)
    viewModelOf(::MenuVM)
    viewModel<OnboardingVM> {
        OnboardingVM(
            settingsStore = get(),
            providerManager = get(),
            modelCatalogService = get(),
            modelMetadataResolver = get(),
        )
    }
    viewModel<TextSelectionVM> {
        TextSelectionVM(
            settingsStore = get(),
            generationHandler = get(),
            memoryRepository = get(),
            templateTransformer = get(),
        )
    }
    viewModel { me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceVM(get()) }
    viewModel<me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailVM> { params ->
        me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailVM(
            id = params[0],
            repository = get(),
        )
    }
}

