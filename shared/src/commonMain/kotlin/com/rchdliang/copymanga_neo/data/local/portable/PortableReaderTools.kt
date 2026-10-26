package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Publish settings only after the atomic write succeeds, as in the Android store. */
class PortableReaderTools(storage: DocumentStorage) : ReaderToolsRepository {
    private val document = DocumentStore(
        storage, "reader-tools.json", ReaderToolsSettings.serializer(), ReaderToolsSettings()
    )
    override val settings = document.state
    val error = document.error
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.Default) {
        document.ensureLoaded().exceptionOrNull()?.let { return@withContext Result.failure(it) }
        if (settings.value == settings.value.normalized()) Result.success(Unit)
        else document.update { it.normalized() }
    }
    override suspend fun awaitReady() = initialize()
    override suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings) =
        document.update { transform(it).normalized() }
}
