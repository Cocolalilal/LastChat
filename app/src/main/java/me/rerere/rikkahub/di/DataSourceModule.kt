package me.rerere.rikkahub.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.dav4jvm.okhttp.BasicDigestAuthHandler
import at.bitfire.dav4jvm.okhttp.DavCollection
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.android.AndroidFileStore
import me.rerere.common.platform.android.AndroidPlatformJwtSigner
import me.rerere.common.platform.android.AndroidPlatformMediaEncoder
import me.rerere.common.platform.android.OkHttpPlatformHttpClient
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.transformers.AndroidMessageTemplateContextFactory
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateCache
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateContextFactory
import me.rerere.rikkahub.data.ai.transformers.MessageTemplateRenderer
import me.rerere.rikkahub.data.ai.transformers.PebbleMessageTemplateRenderer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SpontaneousMessagingStateStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpTransportFactory
import me.rerere.rikkahub.data.ai.mcp.transport.SseClientTransport
import me.rerere.rikkahub.data.ai.mcp.transport.StreamableHttpClientTransport
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.WebDavClientFactory
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.ui.image.AppImageLoaderFactory
import me.rerere.rikkahub.utils.acceptLanguageHeader
import me.rerere.rikkahub.utils.appLocale
import androidx.work.WorkManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Path.Companion.toOkioPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val SEARCH_PLATFORM_HTTP_CLIENT = "searchPlatformHttpClient"
private const val MCP_OKHTTP_CLIENT = "mcpOkHttpClient"
private const val MCP_PLATFORM_HTTP_CLIENT = "mcpPlatformHttpClient"

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

    single<PlatformFileStore> {
        AndroidFileStore(rootDir = get<android.content.Context>().filesDir)
    }

    single {
        SpontaneousMessagingStateStore(context = get())
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "rikka_hub")
            .addMigrations(Migration_6_7, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_14_16, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28, AppDatabase.MIGRATION_28_29, AppDatabase.MIGRATION_29_30, AppDatabase.MIGRATION_31_32, AppDatabase.MIGRATION_32_33)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.query("PRAGMA busy_timeout = 5000").close()
                }
            })
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

    single {
        PebbleMessageTemplateRenderer(engine = get())
    }

    single<MessageTemplateRenderer> { get<PebbleMessageTemplateRenderer>() }

    single<MessageTemplateCache> { get<PebbleMessageTemplateRenderer>() }

    single<MessageTemplateContextFactory> { AndroidMessageTemplateContextFactory() }

    single { TemplateTransformer(renderer = get(), contextFactory = get()) }

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
        get<AppDatabase>().workspaceDao()
    }

    single {
        McpManager(
            settingsStore = get(),
            appScope = get(),
            transportFactory = get(),
        )
    }

    single<McpTransportFactory> {
        val platformHttpClient = get<PlatformHttpClient>(named(MCP_PLATFORM_HTTP_CLIENT))
        McpTransportFactory { config ->
            when (config) {
                is McpServerConfig.SseTransportServer -> SseClientTransport(
                    urlString = config.url,
                    client = platformHttpClient,
                    headers = config.commonOptions.headers,
                )

                is McpServerConfig.StreamableHTTPServer -> StreamableHttpClientTransport(
                    url = config.url,
                    client = platformHttpClient,
                    headers = config.commonOptions.headers.toMap(),
                )
            }
        }
    }

    single {
        GenerationHandler(
            context = get(),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
            chatAttachmentRepository = get(),
            conversationRepo = get(),
            aiLoggingManager = get(),
            embeddingService = get(),
            memorySearchService = get()
        )
    }

    single<OkHttpClient> {
        val acceptLang = get<android.content.Context>().acceptLanguageHeader()
        OkHttpClient.Builder()
            .connectTimeout(20.seconds)
            .readTimeout(10.minutes)
            .writeTimeout(120.seconds)
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

    single<OkHttpClient>(named(MCP_OKHTTP_CLIENT)) {
        OkHttpClient.Builder()
            .connectTimeout(20.seconds)
            .readTimeout(10.minutes)
            .writeTimeout(120.seconds)
            .followSslRedirects(true)
            .followRedirects(true)
            .build()
    }

    single<PlatformHttpClient>(named(MCP_PLATFORM_HTTP_CLIENT)) {
        OkHttpPlatformHttpClient(get<OkHttpClient>(named(MCP_OKHTTP_CLIENT)))
    }

    single<WebDavClientFactory> {
        object : WebDavClientFactory {
            override fun collection(config: WebDavConfig, path: String?): DavCollection {
                val location = buildString {
                    append(config.url.trimEnd('/'))
                    append("/")
                    if (config.path.isNotBlank()) {
                        append(config.path.trim('/'))
                        append("/")
                    }
                    if (path != null) {
                        append(path.trim('/'))
                    }
                }.toHttpUrl()
                return DavCollection(
                    httpClient = config.createWebDavClient(),
                    location = location,
                )
            }

            override fun hrefCollection(config: WebDavConfig, href: String): DavCollection {
                return DavCollection(
                    httpClient = config.createWebDavClient(),
                    location = href.toHttpUrl(),
                )
            }

            override fun putFile(
                collection: DavCollection,
                file: java.io.File,
                onResponse: (String) -> Unit,
            ) {
                collection.put(body = file.asRequestBody()) { response ->
                    onResponse(response.toString())
                }
            }

            private fun WebDavConfig.createWebDavClient(): OkHttpClient {
                val authHandler = BasicDigestAuthHandler(
                    domain = null,
                    username = username,
                    password = password.toCharArray(),
                )
                return OkHttpClient.Builder()
                    .followRedirects(false)
                    .authenticator(authHandler)
                    .addNetworkInterceptor(authHandler)
                    .writeTimeout(5.minutes)
                    .build()
            }
        }
    }

    single {
        SponsorAPI.create(get())
    }

    single<PlatformHttpClient> {
        OkHttpPlatformHttpClient(get())
    }

    single<PlatformHttpClient>(named(SEARCH_PLATFORM_HTTP_CLIENT)) {
        OkHttpPlatformHttpClient(
            get<OkHttpClient>().newBuilder()
                .readTimeout(30.seconds)
                .build()
        )
    }

    single<AppImageLoaderFactory> {
        val okHttpClient = get<OkHttpClient>()
        AppImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.filesDir.resolve("icon_cache").toOkioPath())
                        .maxSizeBytes(50 * 1024 * 1024)
                        .build()
                }
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                    add(SvgDecoder.Factory(scaleToDensity = true))
                }
                .build()
        }
    }

    single {
        ProviderManager(
            platformHttpClient = get(),
            platformMediaEncoder = AndroidPlatformMediaEncoder(),
            platformJwtSigner = AndroidPlatformJwtSigner(),
        )
    }

    single {
        ModelCatalogService(
            context = get(),
            httpClient = get(),
            fileStore = get(),
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
            webDavClientFactory = get(),
        )
    }
}
