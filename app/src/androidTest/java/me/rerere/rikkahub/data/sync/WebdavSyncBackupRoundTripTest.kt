package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.FontConfig
import me.rerere.rikkahub.data.datastore.FontSettings
import me.rerere.rikkahub.data.datastore.FontSource
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.SpontaneousMessagingStateStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.Migration_6_7
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.model.TextSelectionConfig
import me.rerere.rikkahub.data.ai.tools.PythonSandbox
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class WebdavSyncBackupRoundTripTest {
    private val environments = mutableListOf<TestEnvironment>()

    @After
    fun cleanup() {
        environments.asReversed().forEach { it.close() }
        environments.clear()
    }

    @Test
    fun backupRoundTripRestoresPortableData() = runBlocking<Unit> {
        val env = createEnvironment("roundtrip")
        val fixture = seedRoundTripFixture(env)
        val backupFile = env.webdavSync.prepareBackupFile(
            WebDavConfig(
                items = listOf(
                    WebDavConfig.BackupItem.DATABASE,
                    WebDavConfig.BackupItem.FILES,
                )
            )
        )

        mutateAfterBackup(env)

        val result = env.webdavSync.restoreFromLocalFile(
            backupFile,
            WebDavConfig(items = emptyList()),
        )

        assertEquals(0L, result.settingsCleanup.unsupportedZipEntriesBytes)
        assertTrue(File(env.context.filesDir, "avatars/stale-avatar.png").notExists())
        assertTrue(File(env.context.filesDir, "upload/stale-upload.txt").notExists())

        val restoredSettings = env.settingsStore.settingsFlow.value
        val restoredAssistant = restoredSettings.assistants.single()
        val restoredLorebook = restoredSettings.lorebooks.single()
        val restoredProvider = restoredSettings.providers.single() as ProviderSetting.OpenAI
        val restoredModel = restoredProvider.models.single()

        assertFileUriExists((restoredAssistant.avatar as Avatar.Image).url)
        assertFileUriExists((restoredSettings.displaySetting.userAvatar as Avatar.Image).url)
        assertFileUriExists(restoredProvider.customIconUri)
        assertFileUriExists(restoredModel.customIconUri)
        assertFileUriExists((restoredLorebook.cover as Avatar.Image).url)
        restoredLorebook.entries.single().attachments.forEach { attachment ->
            assertFileUriExists(attachment.url)
        }

        val restoredFontPath = restoredSettings.displaySetting.fontSettings.headerFont.customFontPath
        assertNotNull(restoredFontPath)
        assertTrue(File(restoredFontPath ?: "").exists())
        assertTrue(fixture.pythonOutputFile(env).exists())

        val restoredPrefs = env.context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
        assertTrue(restoredPrefs.getBoolean("create_new_conversation_on_start", false))
        assertEquals(fixture.conversationId.toString(), restoredPrefs.getString("lastConversationId", null))

        val spontaneousStore = SpontaneousMessagingStateStore(env.context)
        assertEquals(123_456L, spontaneousStore.globalQuietUntil)
        assertEquals(fixture.assistant.id, spontaneousStore.lastSenderAssistantId)
        assertTrue(spontaneousStore.isEventConsumed("evt-portable"))

        val restoredDb = env.reopenDatabase()
        try {
            val restoredConversation =
                restoredDb.conversationDao().getConversationById(fixture.conversationId.toString())
            assertNotNull(restoredConversation)
            assertTrue(restoredConversation?.nodes?.contains(fixture.pythonOutputUri.toString()) == true)

            val restoredMedia = restoredDb.genMediaDao().getAllMedia()
            assertEquals(1, restoredMedia.size)
            assertEquals("images/generated-image.png", restoredMedia.single().path)
        } finally {
            restoredDb.close()
        }
    }

    @Test
    fun restoreReadsLegacyWalEntries() = runBlocking<Unit> {
        val env = createEnvironment("legacy-wal")
        val conversationId = Uuid.random()
        val assistantId = Uuid.random()
        val nodesJson = JsonInstant.encodeToString(
            listOf(
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("legacy wal row")),
                    )
                )
            )
        )
        env.appDatabase.conversationDao().insert(
            ConversationEntity(
                id = conversationId.toString(),
                assistantId = assistantId.toString(),
                title = "Legacy",
                nodes = nodesJson,
                createAt = 1L,
                updateAt = 2L,
                truncateIndex = -1,
                chatSuggestions = "[]",
                isPinned = false,
            )
        )

        val liveDbFile = env.context.getDatabasePath(BackupArchiveFormat.DB_ENTRY)
        val walFile = File(liveDbFile.parentFile, "${BackupArchiveFormat.DB_ENTRY}-wal")
        val shmFile = File(liveDbFile.parentFile, "${BackupArchiveFormat.DB_ENTRY}-shm")
        assertTrue(walFile.exists())

        val mainOnlyCopy = File(env.context.cacheDir, "main-only.db")
        liveDbFile.copyTo(mainOnlyCopy, overwrite = true)
        val mainOnlyRowCount = SQLiteDatabase.openDatabase(
            mainOnlyCopy.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM ConversationEntity WHERE id=?",
                arrayOf(conversationId.toString()),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
        assertEquals(0, mainOnlyRowCount)

        val backupFile = File(env.context.cacheDir, "legacy_backup.zip")
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            writeVirtualEntry(
                zipOut,
                BackupArchiveFormat.SETTINGS_ENTRY,
                JsonInstant.encodeToString(Settings()),
            )
            writeFileEntry(zipOut, liveDbFile, BackupArchiveFormat.LEGACY_DB_ENTRY)
            writeFileEntry(zipOut, walFile, BackupArchiveFormat.WAL_ENTRY)
            if (shmFile.exists()) {
                writeFileEntry(zipOut, shmFile, BackupArchiveFormat.SHM_ENTRY)
            }
        }

        env.appDatabase.close()
        env.context.deleteDatabase(BackupArchiveFormat.DB_ENTRY)

        env.webdavSync.restoreFromLocalFile(
            backupFile,
            WebDavConfig(items = emptyList()),
        )

        val restoredDb = env.reopenDatabase()
        try {
            val restored = restoredDb.conversationDao().getConversationById(conversationId.toString())
            assertNotNull(restored)
            assertEquals("Legacy", restored?.title)
        } finally {
            restoredDb.close()
        }
    }

    @Test
    fun restoreUsesArchiveContentsEvenWhenCurrentBackupSelectionIsEmpty() = runBlocking<Unit> {
        val env = createEnvironment("archive-driven")
        val fixture = seedRoundTripFixture(env)
        val backupFile = env.webdavSync.prepareBackupFile(
            WebDavConfig(
                items = listOf(
                    WebDavConfig.BackupItem.DATABASE,
                    WebDavConfig.BackupItem.FILES,
                )
            )
        )

        env.appDatabase.close()
        env.context.deleteDatabase(BackupArchiveFormat.DB_ENTRY)
        BackupArchiveFormat.MANAGED_FILE_DIRS.forEach { dirName ->
            File(env.context.filesDir, dirName).deleteRecursively()
        }
        env.settingsStore.update(Settings(textSelectionConfig = TextSelectionConfig()))

        env.webdavSync.restoreFromLocalFile(
            backupFile,
            WebDavConfig(items = emptyList()),
        )

        val restoredDb = env.reopenDatabase()
        try {
            val restored = restoredDb.conversationDao().getConversationById(fixture.conversationId.toString())
            assertNotNull(restored)
        } finally {
            restoredDb.close()
        }
        assertTrue(fixture.avatarFile(env).exists())
    }

    private fun createEnvironment(label: String): TestEnvironment {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val isolatedContext = BackupTestContext(baseContext, label)
        val appScope = AppScope()
        val quickCache = QuickSettingsCache(isolatedContext)
        val secureStore = SecureStore(isolatedContext)
        secureStore.clearAll()
        val secretKeyManager = SecretKeyManager(secureStore)
        val settingsStore = SettingsStore(isolatedContext, appScope, quickCache, secretKeyManager)
        val appDatabase = createDatabase(isolatedContext)
        val webdavSync = WebdavSync(
            settingsStore = settingsStore,
            json = JsonInstant,
            context = isolatedContext,
            secretKeyManager = secretKeyManager,
            appDatabase = appDatabase,
            webDavClientFactory = unsupportedWebDavClientFactory,
        )
        return TestEnvironment(
            context = isolatedContext,
            appScope = appScope,
            settingsStore = settingsStore,
            secureStore = secureStore,
            appDatabase = appDatabase,
            webdavSync = webdavSync,
        ).also(environments::add)
    }

    private fun seedRoundTripFixture(env: TestEnvironment): RoundTripFixture {
        val assistantId = Uuid.random()
        val conversationId = Uuid.random()
        val avatarFile = writeFile(env.context.filesDir, "avatars/assistant-avatar.png", "assistant-avatar")
        val userAvatarFile = writeFile(env.context.filesDir, "avatars/user-avatar.png", "user-avatar")
        val providerIconFile = writeFile(env.context.filesDir, "custom_icons/provider-icon.png", "provider-icon")
        val modelIconFile = writeFile(env.context.filesDir, "custom_icons/model-icon.png", "model-icon")
        val fontFile = writeFile(env.context.filesDir, "custom_fonts/expressive.ttf", "font")
        val generatedImage = writeFile(env.context.filesDir, "images/generated-image.png", "image")
        val lorebookCover = writeFile(env.context.filesDir, "upload/lorebook-cover.png", "cover")
        val backgroundImage = writeFile(env.context.filesDir, "upload/background.png", "background")
        val chatAttachment = writeFile(env.context.filesDir, "chat_files/attachment.txt", "chat-file")
        val importedAttachment = writeFile(env.context.filesDir, "lorebook_attachments/imported.txt", "imported-file")

        val pythonConversationId = conversationId
        val pythonSandbox = PythonSandbox(env.context)
        val pythonOutputUri = pythonSandbox.saveOutputFile(
            pythonConversationId,
            "report.txt",
            "python-output".toByteArray(),
        )

        val providerModel = Model(
            modelId = "portable-model",
            displayName = "Portable Model",
            id = Uuid.random(),
            customIconUri = Uri.fromFile(modelIconFile).toString(),
        )
        val provider = ProviderSetting.OpenAI(
            id = Uuid.random(),
            name = "Portable Provider",
            customIconUri = Uri.fromFile(providerIconFile).toString(),
            models = listOf(providerModel),
        )
        val assistant = Assistant(
            id = assistantId,
            name = "Portable Assistant",
            avatar = Avatar.Image(Uri.fromFile(avatarFile).toString()),
            background = Uri.fromFile(backgroundImage).toString(),
        )
        val lorebook = Lorebook(
            id = Uuid.random(),
            name = "Portable Lorebook",
            cover = Avatar.Image(Uri.fromFile(lorebookCover).toString()),
            entries = listOf(
                LorebookEntry(
                    id = Uuid.random(),
                    name = "Portable Entry",
                    prompt = "Portable Prompt",
                    activationType = LorebookActivationType.ALWAYS,
                    injectionPosition = InjectionPosition.AFTER_SYSTEM,
                    attachments = listOf(
                        ModeAttachment(
                            url = Uri.fromFile(chatAttachment).toString(),
                            type = ModeAttachmentType.DOCUMENT,
                            fileName = "attachment.txt",
                            mime = "text/plain",
                        ),
                        ModeAttachment(
                            url = Uri.fromFile(importedAttachment).toString(),
                            type = ModeAttachmentType.DOCUMENT,
                            fileName = "imported.txt",
                            mime = "text/plain",
                        ),
                    ),
                )
            ),
        )
        val settings = Settings(
            chatModelId = providerModel.id,
            titleModelId = providerModel.id,
            imageGenerationModelId = providerModel.id,
            translateModeId = providerModel.id,
            suggestionModelId = providerModel.id,
            ocrModelId = providerModel.id,
            embeddingModelId = providerModel.id,
            assistantId = assistant.id,
            providers = listOf(provider),
            assistants = listOf(assistant),
            lorebooks = listOf(lorebook),
            displaySetting = DisplaySetting(
                userAvatar = Avatar.Image(Uri.fromFile(userAvatarFile).toString()),
                fontSettings = FontSettings(
                    headerFont = FontConfig(
                        fontSource = FontSource.Custom,
                        customFontPath = fontFile.absolutePath,
                        customFontName = "Expressive",
                    ),
                    contentFont = FontConfig.DEFAULT_EXPRESSIVE,
                    codeFont = FontConfig.DEFAULT_CODE,
                ),
            ),
        )

        runBlocking {
            env.settingsStore.update(settings)
            env.appDatabase.conversationDao().insert(
                ConversationEntity(
                    id = conversationId.toString(),
                    assistantId = assistant.id.toString(),
                    title = "Portable Conversation",
                    nodes = JsonInstant.encodeToString(
                        listOf(
                            MessageNode.of(
                                UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(
                                        UIMessagePart.Text("Download: ${pythonOutputUri}")
                                    ),
                                )
                            )
                        )
                    ),
                    createAt = 10L,
                    updateAt = 20L,
                    truncateIndex = -1,
                    chatSuggestions = "[]",
                    isPinned = false,
                )
            )
            env.appDatabase.genMediaDao().insert(
                GenMediaEntity(
                    path = "images/${generatedImage.name}",
                    modelId = providerModel.id.toString(),
                    prompt = "portable image",
                    createAt = 30L,
                )
            )
        }

        env.context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("create_new_conversation_on_start", true)
            .putString("lastConversationId", conversationId.toString())
            .commit()

        val spontaneousStateStore = SpontaneousMessagingStateStore(env.context)
        spontaneousStateStore.updateDeliveryState(123_456L, assistant.id)
        spontaneousStateStore.markEventConsumed("evt-portable")

        return RoundTripFixture(
            assistant = assistant,
            conversationId = conversationId,
            pythonConversationId = pythonConversationId,
            pythonOutputUri = pythonOutputUri,
        )
    }

    private fun mutateAfterBackup(env: TestEnvironment) = runBlocking {
        writeFile(env.context.filesDir, "avatars/stale-avatar.png", "stale-avatar")
        writeFile(env.context.filesDir, "upload/stale-upload.txt", "stale-upload")
        BackupArchiveFormat.MANAGED_FILE_DIRS.forEach { dirName ->
            File(env.context.filesDir, dirName).deleteRecursively()
        }
        env.context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("create_new_conversation_on_start", false)
            .commit()
        env.context.getSharedPreferences("spontaneous_messaging_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        env.settingsStore.update(Settings())
        env.appDatabase.close()
        env.context.deleteDatabase(BackupArchiveFormat.DB_ENTRY)
    }

    private fun createDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, BackupArchiveFormat.DB_ENTRY)
            .addMigrations(
                Migration_6_7,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_14_16,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
            )
            .build()
    }

    private fun writeFile(root: File, relativePath: String, content: String): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun assertFileUriExists(uri: String?) {
        assertNotNull(uri)
        val path = Uri.parse(uri).path
        assertNotNull(path)
        assertTrue(File(path ?: "").exists())
    }

    private fun writeFileEntry(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { input ->
            zipOut.putNextEntry(ZipEntry(entryName))
            input.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    private fun writeVirtualEntry(zipOut: ZipOutputStream, entryName: String, content: String) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }

    private fun File.notExists(): Boolean = !exists()

    private data class RoundTripFixture(
        val assistant: Assistant,
        val conversationId: Uuid,
        val pythonConversationId: Uuid,
        val pythonOutputUri: Uri,
    ) {
        fun avatarFile(env: TestEnvironment): File {
            return File(env.context.filesDir, "avatars/assistant-avatar.png")
        }

        fun pythonOutputFile(env: TestEnvironment): File {
            return File(
                env.context.filesDir,
                "workspaces/${pythonConversationId}/report.txt",
            )
        }
    }

    private data class TestEnvironment(
        val context: BackupTestContext,
        val appScope: AppScope,
        val settingsStore: SettingsStore,
        val secureStore: SecureStore,
        var appDatabase: AppDatabase,
        val webdavSync: WebdavSync,
    ) {
        fun reopenDatabase(): AppDatabase {
            appDatabase = Room.databaseBuilder(context, AppDatabase::class.java, BackupArchiveFormat.DB_ENTRY)
                .addMigrations(
                    Migration_6_7,
                    AppDatabase.MIGRATION_11_12,
                    AppDatabase.MIGRATION_12_13,
                    AppDatabase.MIGRATION_14_16,
                    AppDatabase.MIGRATION_22_23,
                    AppDatabase.MIGRATION_23_24,
                    AppDatabase.MIGRATION_24_25,
                    AppDatabase.MIGRATION_25_26,
                    AppDatabase.MIGRATION_26_27,
                    AppDatabase.MIGRATION_27_28,
                    AppDatabase.MIGRATION_28_29,
                    AppDatabase.MIGRATION_29_30,
                )
                .build()
            return appDatabase
        }

        fun close() {
            runCatching { appDatabase.close() }
            runCatching { secureStore.clearAll() }
            appScope.cancel()
            context.cleanup()
        }
    }

    private val unsupportedWebDavClientFactory = object : WebDavClientFactory {
        override fun collection(
            config: WebDavConfig,
            path: String?,
        ): at.bitfire.dav4jvm.okhttp.DavCollection {
            error("WebDAV network operations are not used by local backup round-trip tests")
        }

        override fun hrefCollection(
            config: WebDavConfig,
            href: String,
        ): at.bitfire.dav4jvm.okhttp.DavCollection {
            error("WebDAV network operations are not used by local backup round-trip tests")
        }

        override fun putFile(
            collection: at.bitfire.dav4jvm.okhttp.DavCollection,
            file: File,
            onResponse: (String) -> Unit,
        ) {
            error("WebDAV network operations are not used by local backup round-trip tests")
        }
    }

    private class BackupTestContext(
        base: Context,
        label: String,
    ) : ContextWrapper(base) {
        private val scopeId = "${label}_${UUID.randomUUID()}"
        private val rootDir = File(base.cacheDir, "backup_tests/$scopeId").apply { mkdirs() }
        private val filesRoot = File(rootDir, "files").apply { mkdirs() }
        private val cacheRoot = File(rootDir, "cache").apply { mkdirs() }
        private val databaseRoot = File(rootDir, "databases").apply { mkdirs() }
        private val prefNames = linkedSetOf<String>()

        override fun getApplicationContext(): Context {
            return this
        }

        override fun getFilesDir(): File {
            return filesRoot
        }

        override fun getCacheDir(): File {
            return cacheRoot
        }

        override fun getDatabasePath(name: String): File {
            return File(databaseRoot, name).also { file ->
                file.parentFile?.mkdirs()
            }
        }

        override fun deleteDatabase(name: String): Boolean {
            val baseFile = getDatabasePath(name)
            val deleted = listOf(
                baseFile,
                File(baseFile.path + "-wal"),
                File(baseFile.path + "-shm"),
                File(baseFile.path + "-journal"),
            ).map { file ->
                !file.exists() || file.delete()
            }
            return deleted.all { it }
        }

        override fun getSharedPreferences(name: String, mode: Int) =
            baseContext.getSharedPreferences(prefixedName(name).also(prefNames::add), mode)

        fun cleanup() {
            prefNames.forEach { prefName ->
                baseContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
            rootDir.deleteRecursively()
        }

        private fun prefixedName(name: String): String {
            return "${scopeId}_$name"
        }
    }
}
