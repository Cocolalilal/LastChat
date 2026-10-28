package me.rerere.rikkahub.data.memory

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.rerere.locallm.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class MemoryModelInstallTest {
    @Test fun retiringOldEmbeddingDeletesOnlyUnsharedManagedFiles()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val store=LocalModelStore(context)
        val owned=File(context.filesDir,"local_models/retired-test.tflite").apply{parentFile.mkdirs();writeText("old")}
        val external=File.createTempFile("external-model-", ".tflite")
        fun model(id:String,file:File)=InstalledLocalModel(id,id,LocalModelKind.EMBEDDING,file.absolutePath,commitHash="test",sizeInBytes=file.length())
        try {
            store.upsert(model(RETIRED_EMBEDDING_MODEL_ID,owned))
            store.retireLegacyEmbedding()
            assertFalse(owned.exists())
            assertNull(store.get(RETIRED_EMBEDDING_MODEL_ID))
            store.upsert(model(RETIRED_EMBEDDING_MODEL_ID,external))
            store.retireLegacyEmbedding()
            assertTrue(external.exists())
            owned.writeText("shared")
            store.upsert(model("another-model",owned))
            store.upsert(model(RETIRED_EMBEDDING_MODEL_ID,owned))
            store.retireLegacyEmbedding()
            assertTrue(owned.exists())
        } finally { store.remove(RETIRED_EMBEDDING_MODEL_ID);store.remove("another-model");owned.delete();external.delete() }
    }
    @Test fun missingTruncatedAndRetiredBundlesAreNotReady() {
        val file=File.createTempFile("embedding-install-", ".litertlm")
        try {
            file.writeText("bundle")
            val model=InstalledLocalModel(TEXT_EMBEDDING_MODEL_ID,"Text",LocalModelKind.EMBEDDING,file.absolutePath,commitHash="test",sizeInBytes=file.length())
            assertTrue(model.hasUsableTextEmbeddingFile())
            assertFalse(model.copy(sizeInBytes=file.length()+1).hasUsableTextEmbeddingFile())
            assertFalse(model.copy(id=RETIRED_EMBEDDING_MODEL_ID).hasUsableTextEmbeddingFile())
            file.delete()
            assertFalse(model.hasUsableTextEmbeddingFile())
        } finally { file.delete() }
    }
}
