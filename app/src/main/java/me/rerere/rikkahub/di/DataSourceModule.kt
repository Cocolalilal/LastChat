package me.rerere.rikkahub.di

import androidx.room.Room
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.http.AcceptLanguageBuilder
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.local.LiteRtRuntimeEngine
import me.rerere.rikkahub.data.ai.local.LocalCompatibilityEstimator
import me.rerere.rikkahub.data.ai.local.LocalModelInstallCoordinator
import me.rerere.rikkahub.data.ai.local.LocalModelRepository
import me.rerere.rikkahub.data.ai.local.LocalProvider
import me.rerere.rikkahub.data.ai.local.LocalRuntimeEngine
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.LastChatAPI
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SpontaneousMessagingStateStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.utils.appLocale
import androidx.work.WorkManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val dataSourceModule = module {
    single {
        QuickSettingsCache(context = get())
    }

    single {
        SecureStore(context = get())
    }

    single {
        SecretKeyManager(secureStore = get())
    }

    single {
        SettingsStore(context = get(), scope = get(), quickCache = get(), secretKeyManager = get())
    }

    single {
        SpontaneousMessagingStateStore(context = get())
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "rikka_hub")
            .addMigrations(Migration_6_7, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_14_16, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28)
            .build()
    }

    single {
        WorkManager.getInstance(get())
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(get<android.content.Context>().appLocale())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().chatAttachmentDao()
    }

    single {
        get<AppDatabase>().conversationAttachmentRefDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().chatEpisodeDao()
    }

    single {
        get<AppDatabase>().embeddingCacheDao()
    }

    single {
        get<AppDatabase>().dailyActivityDao()
    }

    single {
        get<AppDatabase>().usageStatsDao()
    }

    single {
        get<AppDatabase>().localModelInstallDao()
    }

    single { McpManager(settingsStore = get(), appScope = get()) }

    single {
        GenerationHandler(
            context = get(),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
            chatAttachmentRepository = get(),
            conversationRepo = get(),
            aiLoggingManager = get(),
            embeddingService = get()
        )
    }

    single<OkHttpClient> {
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)
                    .addHeader(HttpHeaders.UserAgent, "LastChat-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(AIRequestInterceptor(remoteConfig = get()))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
    }

    single {
        SponsorAPI.create(get())
    }

    single {
        ProviderManager(client = get()).apply {
            registerProvider(
                "local",
                LocalProvider(
                    repository = get(),
                    runtimeEngine = get(),
                    compatibilityEstimator = get(),
                )
            )
        }
    }

    single {
        LocalCompatibilityEstimator(
            context = get(),
        )
    }

    single<LocalRuntimeEngine> { LiteRtRuntimeEngine(context = get(), compatibilityEstimator = get()) }

    single {
        LocalModelRepository(
            context = get(),
            appScope = get(),
            installDao = get(),
            settingsStore = get(),
            secureStore = get(),
            client = get(),
            compatibilityEstimator = get(),
        )
    }

    single {
        LocalModelInstallCoordinator(
            appScope = get(),
            workManager = get(),
            installDao = get(),
            repository = get(),
        )
    }

    single {
        ModelCatalogService(
            context = get(),
            client = get(),
        )
    }

    single {
        ModelMetadataResolver(snapshotProvider = { get<ModelCatalogService>().snapshotOrNull() })
    }

    single {
        WebdavSync(
            settingsStore = get(),
            json = get(),
            context = get(),
            secretKeyManager = get(),
            appDatabase = get(),
        )
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<LastChatAPI> {
        get<Retrofit>().create(LastChatAPI::class.java)
    }
}
