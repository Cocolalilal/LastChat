package me.rerere.rikkahub.di

import android.app.Application
import com.google.firebase.FirebaseApp
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.rikkahub.data.config.AppRemoteConfig
import me.rerere.rikkahub.data.ai.AIRequestInterceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.core.qualifier.named
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StartupDependenciesTest {
    @Before fun noFirebaseApp() {
        FirebaseApp.getApps(RuntimeEnvironment.getApplication()).forEach { it.delete() }
    }

    @Test fun startupSearchClientResolvesWithoutFirebaseConfiguration() {
        val app = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule, dataSourceModule)
        }
        try {
            // LastChatApp resolves this client before configuring Remote Config.
            assertNotNull(app.koin.get<PlatformHttpClient>(named(SEARCH_PLATFORM_HTTP_CLIENT)))
        } finally {
            app.close()
        }
    }

    @Test fun initializationAndBundledValuesWorkWithoutFirebase() {
        val context = RuntimeEnvironment.getApplication()
        val config = AppRemoteConfig(context)
        config.initialize()
        assertTrue(config.getString("silicon_cloud_free_models").split(",").contains("Qwen/Qwen3-8B"))
        assertTrue(config.getString("silicon_cloud_api_key").isNotBlank())
        assertEquals("", config.getString("unknown_configuration_key"))
        assertTrue(FirebaseApp.getApps(context).isEmpty())
    }

    @Test fun bundledFreeModelStillWorksAndUserAuthorizationIsPreserved() {
        val config = AppRemoteConfig(RuntimeEnvironment.getApplication())
        val received = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(AIRequestInterceptor(config))
            .addInterceptor { chain ->
                received += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{}".toResponseBody()).build()
            }
            .build()
        val request = Request.Builder().url("https://api.siliconflow.cn/v1/chat/completions")
            .header("Authorization", "Bearer sk-")
            .post("""{"model":"Qwen/Qwen3-8B","messages":[]}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()
        client.newCall(request.newBuilder().header("Authorization", "Bearer user-owned-key").build()).execute().close()
        assertTrue(received[0].header("Authorization").orEmpty().startsWith("Bearer "))
        assertTrue(received[0].header("Authorization") != "Bearer sk-")
        assertEquals("Bearer user-owned-key", received[1].header("Authorization"))
    }

}
