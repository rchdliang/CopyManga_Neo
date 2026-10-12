package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.ReadingProgress
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** 加载当前账号的云端同步状态；失败结束等待并保留原始数据。 */
internal fun <T> readingProgressLoadFlow(
    owner: String?, version: Long, source: Flow<T>,
    decode: (T, String) -> Map<String, ReadingProgress>
): Flow<ReadingProgressSnapshot> = flow {
    emit(ReadingProgressSnapshot(owner, loadVersion = version))
    if (owner == null) return@flow
    source.collect { prefs ->
        val loaded = try {
            Result.success(decode(prefs, owner))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        emit(
            ReadingProgressSnapshot(
                owner, loaded.getOrDefault(emptyMap()), isReady = true,
                error = if (loaded.isFailure) "阅读进度读取失败，原数据已保留，请重试" else null,
                loadVersion = version
            )
        )
    }
}.catch { error ->
    if (error is CancellationException) throw error
    emit(
        ReadingProgressSnapshot(
            owner,
            isReady = true,
            error = "阅读进度读取失败，原文件已保留，请重试",
            loadVersion = version
        )
    )
}

internal suspend fun awaitReadingProgress(
    state: StateFlow<ReadingProgressSnapshot>, owner: String,
    minimumVersion: Long = 0L, timeoutMillis: Long = 15_000
): ReadingProgressSnapshot =
    withTimeoutOrNull(timeoutMillis) { state.first { it.owner == owner && it.isReady && it.loadVersion >= minimumVersion } }
        ?: ReadingProgressSnapshot(
            owner,
            isReady = true,
            error = "阅读进度读取超时，请重试",
            loadVersion = minimumVersion
        )
