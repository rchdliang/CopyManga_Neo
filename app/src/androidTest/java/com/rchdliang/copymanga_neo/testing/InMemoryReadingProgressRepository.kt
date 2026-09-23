package com.rchdliang.copymanga_neo.testing

import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ReadingProgress
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** 现有 ViewModel 测试的内存依赖，不访问真实账号或网络。 */
class InMemoryReadingProgressRepository(private val remote: MangaRepository? = null) :
    ReadingProgressRepository {
    override val currentOwner = "guest"
    override val progress = MutableStateFlow(ReadingProgressSnapshot(currentOwner, isReady = true))
    private var version = 0L
    override suspend fun awaitOwner() = currentOwner
    override fun requestVersion() = ++version
    override fun recordRead(
        owner: String,
        comicPathWord: String,
        chapterUuid: String,
        chapterName: String,
        group: String
    ) {
        progress.value = progress.value.copy(
            records = progress.value.records +
                    (comicPathWord to ReadingProgress(
                        chapterUuid,
                        chapterName,
                        group,
                        requestVersion(),
                        false
                    ))
        )
    }

    override suspend fun acceptRemote(
        owner: String,
        records: Map<String, BrowseInfo?>,
        requestVersion: Long
    ) = Result.success(Unit)

    override fun retryPending() = Unit
    override suspend fun clearHistory(): Result<Unit> =
        (remote?.clearBrowseHistory() ?: Result.success(Unit)).onSuccess {
            progress.value = ReadingProgressSnapshot(currentOwner, isReady = true)
        }
}
