package me.rerere.rikkahub.data.ai.local

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.dao.LocalModelInstallDao
import me.rerere.rikkahub.service.LocalModelInstallWorker

class LocalModelInstallCoordinator(
    private val appScope: AppScope,
    private val workManager: WorkManager,
    private val installDao: LocalModelInstallDao,
    private val repository: LocalModelRepository,
) {
    fun downloadModel(catalogId: String) {
        appScope.launch(Dispatchers.IO) {
            repository.markQueued(catalogId)
            enqueueWork(
                catalogId = catalogId,
                action = LocalModelInstallWorker.ACTION_DOWNLOAD,
            )
        }
    }

    fun importModel(uri: Uri) {
        appScope.launch(Dispatchers.IO) {
            val catalogId = repository.prepareImport(uri)
            enqueueWork(
                catalogId = catalogId,
                action = LocalModelInstallWorker.ACTION_IMPORT,
                sourceUri = uri.toString(),
            )
        }
    }

    fun retryModel(catalogId: String) {
        appScope.launch(Dispatchers.IO) {
            val install = installDao.getByCatalogId(catalogId) ?: return@launch
            if (install.provenance == LocalModelProvenance.IMPORTED.name && install.sourceUri.isNotBlank()) {
                repository.markQueued(catalogId)
                enqueueWork(
                    catalogId = catalogId,
                    action = LocalModelInstallWorker.ACTION_IMPORT,
                    sourceUri = install.sourceUri,
                )
            } else {
                downloadModel(catalogId)
            }
        }
    }

    fun cancelModel(catalogId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(catalogId))
        appScope.launch(Dispatchers.IO) {
            repository.markCanceled(catalogId)
        }
    }

    fun removeModel(catalogId: String) {
        cancelModel(catalogId)
        appScope.launch(Dispatchers.IO) {
            repository.removeModel(catalogId)
        }
    }

    private fun enqueueWork(
        catalogId: String,
        action: String,
        sourceUri: String = "",
    ) {
        val request = OneTimeWorkRequestBuilder<LocalModelInstallWorker>()
            .addTag(LOCAL_MODEL_INSTALL_WORK_TAG)
            .addTag(catalogId)
            .setInputData(
                workDataOf(
                    LocalModelInstallWorker.KEY_ACTION to action,
                    LocalModelInstallWorker.KEY_CATALOG_ID to catalogId,
                    LocalModelInstallWorker.KEY_SOURCE_URI to sourceUri,
                )
            )
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(catalogId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(catalogId: String): String = "$LOCAL_MODEL_INSTALL_WORK_TAG:$catalogId"
}

const val LOCAL_MODEL_INSTALL_WORK_TAG = "local_model_install"
