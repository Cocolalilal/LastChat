package me.rerere.rikkahub.localstt

import android.app.Application
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import okio.Buffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.rerere.asr.local.*
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalSttInstallTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var install: SherpaModelInstall
    private val bodies = linkedMapOf("/encoder" to "encoder data".toByteArray(),
        "/decoder" to "decoder data".toByteArray(), "/tokens" to "token data".toByteArray())
    private val requests = mutableListOf<Pair<String, String?>>()
    private var handler: ((RecordedRequest) -> MockResponse)? = null
    private val gate = CompletableDeferred<Unit>()

    @Before fun setup() {
        File(context.filesDir, "local_stt").deleteRecursively()
        install = SherpaModelInstall(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request.path.orEmpty() to request.getHeader("Range") }
                return handler?.invoke(request) ?: reply(request)
            }
        }
        server.start()
    }

    @After fun teardown() { gate.complete(Unit); server.shutdown() }
    private fun url(path: String) = server.url(path).toString()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun metadata() = SherpaModelMetadata(id = "test-model", name = "Test speech", family = SherpaModelFamily.WHISPER,
        archiveUrl = url("/archive"), archiveSizeBytes = 1, revision = "pinned",
        files = linkedMapOf("encoder" to "encoder.onnx", "decoder" to "decoder.onnx", "tokens" to "tokens.txt"),
        downloads = bodies.mapKeys { it.key.removePrefix("/") }.mapValues { (role, bytes) ->
            SherpaModelFileDownload(url("/$role"), bytes.size.toLong(), hash(bytes))
        })

    private fun partial(source: SherpaModelFileDownload) = File(context.filesDir,
        "local_stt/downloads/test-model/${source.sha256}.part")
    private fun reply(request: RecordedRequest, ignoreRange: Boolean = false): MockResponse {
        val data = bodies.getValue(request.path.orEmpty())
        val offset = if (ignoreRange) 0 else request.getHeader("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
        return MockResponse().setResponseCode(if (offset > 0) 206 else 200)
            .apply { if (offset > 0) setHeader("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}") }
            .setBody(Buffer().write(data, offset, data.size - offset))
    }

    @Test fun verifiedFilesInstallWithoutAnArchiveAndRecoverAfterRegistryLoss() = runBlocking {
        val meta = metadata()
        val progress = mutableListOf<SherpaDownloadProgress>()
        val model = install.download(meta, progress::add)
        assertEquals(listOf("/encoder", "/decoder", "/tokens"), requests.map { it.first })
        meta.files.forEach { (role, _) -> assertArrayEquals(bodies.getValue("/$role"), File(model.files.getValue(role)).readBytes()) }
        assertTrue(File(model.directoryPath, SherpaModelInstall.INSTALL_MANIFEST).isFile)
        assertFalse(File(model.directoryPath + ".installing").exists())
        assertEquals(meta.downloadSizeBytes, progress.last().totalBytes)
        assertEquals(100, progress.last().percent)
        val store = SherpaModelStore(context)
        store.clear()
        store.reconcileWithCatalog(SherpaModelCatalog(models = listOf(meta)))
        assertEquals(model, store.current().single())
        store.rename(model.id, "My model")
        store.reconcileWithCatalog(SherpaModelCatalog(models = listOf(meta)))
        assertEquals("My model", store.current().single().displayName)
        val before = requests.size
        assertEquals(model, install.download(meta) {})
        assertEquals("A completed installation must not be downloaded again to retry registry publication", before, requests.size)
    }

    @Test fun resumesAnInterruptedFileAndRestartsWhenTheServerIgnoresRange() = runBlocking {
        val meta = metadata()
        val encoder = meta.downloads.getValue("encoder")
        partial(encoder).apply { parentFile.mkdirs(); writeBytes(bodies.getValue("/encoder").copyOf(4)) }
        val model = install.download(meta) {}
        assertEquals("bytes=4-", requests.first().second)
        assertArrayEquals(bodies.getValue("/encoder"), File(model.files.getValue("encoder")).readBytes())
        install.delete(model)
        synchronized(requests) { requests.clear() }
        partial(encoder).apply { parentFile.mkdirs(); writeBytes(bodies.getValue("/encoder").copyOf(4)) }
        handler = { reply(it, ignoreRange = true) }
        val restarted = install.download(meta) {}
        assertArrayEquals(bodies.getValue("/encoder"), File(restarted.files.getValue("encoder")).readBytes())
    }

    @Test fun rangePastEndRestartsOnceAndDoesNotAppendAnErrorBody() = runBlocking {
        val meta = metadata()
        partial(meta.downloads.getValue("encoder")).apply { parentFile.mkdirs(); writeBytes("old".toByteArray()) }
        handler = { exchange ->
            if (exchange.getHeader("Range") != null) MockResponse().setResponseCode(416)
            else reply(exchange)
        }
        val installed = install.download(meta) {}
        assertEquals(listOf("bytes=3-", null), requests.filter { it.first == "/encoder" }.map { it.second })
        assertArrayEquals(bodies.getValue("/encoder"), File(installed.files.getValue("encoder")).readBytes())
    }

    @Test fun checksumFailurePreservesTheExistingModelAndAllowsACleanRetry() = runBlocking {
        val original = install.download(metadata()) {}
        val originalBytes = File(original.files.getValue("encoder")).readBytes()
        val changed = metadata().copy(revision = "next", downloads = metadata().downloads.mapValues { (role, source) ->
            if (role == "encoder") source.copy(sha256 = hash("expected new".toByteArray())) else source
        })
        val error = runCatching { install.download(changed) {} }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("checksum"))
        assertArrayEquals(originalBytes, File(original.files.getValue("encoder")).readBytes())
        assertFalse(File(original.directoryPath + ".installing").exists())
        assertFalse(partial(changed.downloads.getValue("encoder")).exists())
        assertEquals(original, Json.decodeFromString<InstalledSherpaModel>(File(original.directoryPath, "installed.json").readText()))
    }

    @Test fun laterFileFailureKeepsVerifiedDownloadsForRetry() = runBlocking {
        val meta = metadata()
        handler = { exchange -> if (exchange.path == "/decoder") MockResponse().setResponseCode(503) else reply(exchange) }
        assertTrue(runCatching { install.download(meta) {} }.isFailure)
        assertTrue(partial(meta.downloads.getValue("encoder")).isFile)
        assertFalse(install.modelDir(meta.id).exists())
        handler = null
        install.download(meta) {}
        assertEquals(1, requests.count { it.first == "/encoder" })
    }

    @Test fun cancellingABlockedSocketReturnsPromptlyAndDoesNotInstall(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        handler = { exchange ->
            started.complete(Unit)
            MockResponse().setBody("e").setHeader("Content-Length", bodies.getValue("/encoder").size)
        }
        val job = async(Dispatchers.IO) { install.download(metadata()) {} }
        withTimeout(5_000) { started.await() }
        withTimeout(5_000) { job.cancelAndJoin() }
        assertFalse(install.modelDir("test-model").exists())
        assertFalse(File(install.modelDir("test-model").path + ".installing").exists())
        gate.complete(Unit)
    }

    @Test fun archiveExtractionIsCancellableAndACompletedArchiveIsReused() = runBlocking {
        val output = ByteArrayOutputStream()
        BZip2CompressorOutputStream(output).use { bzip -> TarArchiveOutputStream(bzip).use { tar ->
            listOf("tokens.txt", "encoder.onnx", "decoder.onnx").forEach { path ->
                val data = "binary for $path".toByteArray()
                tar.putArchiveEntry(TarArchiveEntry("model/$path").apply { size = data.size.toLong() })
                tar.write(data); tar.closeArchiveEntry()
            }
        } }
        bodies["/archive"] = output.toByteArray()
        val meta = metadata().copy(downloads = emptyMap(), archiveSizeBytes = output.size().toLong())
        val error = runCatching { install.download(meta) { if (it.phase == SherpaDownloadPhase.INSTALLING && it.bytesDownloaded > 0) throw CancellationException("cancel") } }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertFalse(install.modelDir(meta.id).exists())
        val before = requests.count { it.first == "/archive" }
        val installed = install.download(meta) {}
        assertEquals(before, requests.count { it.first == "/archive" })
        assertTrue(File(installed.files.getValue("encoder")).isFile)
    }
    @Test fun cancelAndImmediateRetryCannotLoseTheNewDownloadOrRecreateAnOldError() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var first = true
        handler = { exchange ->
            if (first) {
                first = false
                started.complete(Unit)
                runBlocking { gate.await() }
                reply(exchange)
            } else reply(exchange)
        }
        val store = SherpaModelStore(context)
        store.clear()
        val manager = SherpaDownloadManager(context, install, store)
        manager.download(metadata())
        withTimeout(5_000) { started.await() }
        manager.cancel("test-model")
        manager.download(metadata())
        gate.complete(Unit)
        withTimeout(10_000) {
            while (manager.downloads.value.isNotEmpty()) {
                assertFalse(manager.downloads.value.toString(), manager.downloads.value.values.any { it is SherpaDownload.Failed })
                kotlinx.coroutines.delay(25)
            }
        }
        assertEquals("test-model", store.current().single().id)
        assertTrue(File(store.current().single().files.getValue("decoder")).isFile)
    }

    @Test fun completedLegacyFilesAreRecoveredWithoutAManifestButPartialFilesStayDownloadable() = runBlocking {
        val meta = metadata()
        val installed = install.download(meta) {}
        File(installed.directoryPath, SherpaModelInstall.INSTALL_MANIFEST).delete()
        val partialMeta = meta.copy(id = "partial-model")
        val directory = install.modelDir(partialMeta.id).apply { mkdirs() }
        val partialModel = installed.copy(id = partialMeta.id, directoryPath = directory.absolutePath,
            files = partialMeta.files.mapValues { (_, relative) -> File(directory, relative).apply { writeText("x") }.absolutePath })
        val store = SherpaModelStore(context)
        store.clear()
        store.upsert(partialModel)
        store.reconcileWithCatalog(SherpaModelCatalog(models = listOf(meta, partialMeta)))
        assertEquals(listOf(meta.id), store.current().map { it.id })
        assertTrue(File(installed.files.getValue("encoder")).isFile)
    }

    @Test fun interruptedPromotionRestoresThePreviousCompleteInstallAndItsSettings() = runBlocking {
        val meta = metadata()
        val installed = install.download(meta) {}
        val store = SherpaModelStore(context)
        store.clear()
        store.upsert(installed.copy(displayName = "Custom name", config = installed.config.copy(numThreads = 4)))
        val target = File(installed.directoryPath)
        assertTrue(target.renameTo(File(target.parentFile, target.name + ".previous")))
        store.reconcileWithCatalog(SherpaModelCatalog(models = listOf(meta)))
        assertTrue(File(installed.files.getValue("encoder")).isFile)
        assertEquals("Custom name", store.current().single().displayName)
        assertEquals(4, store.current().single().config.numThreads)
    }

}
