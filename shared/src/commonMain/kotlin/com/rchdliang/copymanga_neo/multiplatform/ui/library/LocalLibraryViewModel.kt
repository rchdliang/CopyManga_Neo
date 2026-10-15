package com.rchdliang.copymanga_neo.multiplatform.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalLibraryState(
    val owner: String? = null, val history: List<LocalReadingRecord> = emptyList(),
    val bookmarks: List<LocalReadingRecord> = emptyList(), val error: String? = null
)

class LocalLibraryViewModel constructor(
    private val store: LocalReadingRepository,
    private val progress: ReadingProgressRepository
) : ViewModel() {
    private val operationError = MutableStateFlow<String?>(null)
    val state = combine(
        progress.progress,
        store.snapshot,
        store.error,
        operationError
    ) { p, s, error, operation ->
        LocalLibraryState(
            p.owner,
            s.records.filter { it.owner == p.owner }.groupBy { it.bookKey }.values
                .map { it.maxBy { r -> r.updatedAt } }.sortedByDescending { it.updatedAt },
            s.bookmarks.filter { it.owner == p.owner }.sortedByDescending { it.updatedAt },
            operation ?: p.error ?: error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalLibraryState())

    fun retry() {
        operationError.value = null
        val version = progress.retryLoading()
        viewModelScope.launch {
            try {
                store.retryRead().fold(
                    onSuccess = {},
                    onFailure = { operationError.value = it.message ?: "本地书库读取失败" }
                )
                val owner = progress.awaitOwner()
                val loaded = progress.awaitReady(owner, version)
                if (owner == progress.currentOwner && loaded.error != null) operationError.value =
                    loaded.error
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                operationError.value = e.message ?: "读取失败，请重试"
            }
        }
    }

    fun remove(r: LocalReadingRecord, bookmark: Boolean) {
        viewModelScope.launch {
            val result = if (bookmark) store.removeBookmark(r) else store.removeHistory(r)
            operationError.value = result.exceptionOrNull()?.message
        }
    }

    fun clear() {
        val owner = state.value.owner ?: return; viewModelScope.launch {
            operationError.value = store.clearHistory(owner).exceptionOrNull()?.message
        }
    }
}
