package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportRequest
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class ComicExportSelectionState(
    val loading: Boolean = true,
    val plan: ComicExportPlan? = null,
    val groups: List<ExportChapterGroup> = emptyList(),
    val selected: Set<ExportChapterKey> = emptySet(),
    val error: String? = null
) {
    val allKeys: Set<ExportChapterKey> = groups.flatMap { it.keys }.toSet()
}

internal class ComicExportSelectionViewModel(
    private val repository: ComicExportRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutable = MutableStateFlow(ComicExportSelectionState())
    val state = mutable.asStateFlow()
    private var loading: Job? = null

    init { load() }

    fun load() {
        loading?.cancel()
        mutable.value = ComicExportSelectionState()
        loading = viewModelScope.launch {
            try {
                val comics = Json.decodeFromString<List<String>>(checkNotNull(savedState["comics"]))
                val plan = repository.prepare(ComicExportRequest(comics)).getOrThrow()
                val groups = plan.selectionGroups()
                val available = groups.flatMap { it.keys }.toSet()
                val restored = savedState.get<String>("selected_chapters")?.let {
                    Json.decodeFromString<Set<ExportChapterKey>>(it)
                }
                val selected = restored?.intersect(available) ?: available
                savedState["selected_chapters"] = Json.encodeToString(selected)
                mutable.value = ComicExportSelectionState(false, plan, groups, selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.value = ComicExportSelectionState(loading = false, error = error.message ?: "无法读取本地章节")
            }
        }
    }

    private fun select(keys: Set<ExportChapterKey>) {
        val selected = keys intersect mutable.value.allKeys
        savedState["selected_chapters"] = Json.encodeToString(selected)
        mutable.value = mutable.value.copy(selected = selected)
    }

    fun toggle(key: ExportChapterKey) = select(
        if (key in mutable.value.selected) mutable.value.selected - key else mutable.value.selected + key
    )
    fun selectAll() = select(mutable.value.allKeys)
    fun invert() = select(mutable.value.allKeys - mutable.value.selected)
    fun clear() = select(emptySet())
    fun toggleGroup(group: ExportChapterGroup) = select(
        if (mutable.value.selected.containsAll(group.keys)) mutable.value.selected - group.keys
        else mutable.value.selected + group.keys
    )
    fun selectRange(group: ExportChapterGroup, first: Int, last: Int) =
        select(selectExportRange(mutable.value.selected, group, first, last))

    fun selectedPlan(): ComicExportPlan? = mutable.value.plan?.onlySelected(mutable.value.selected)
        ?.takeIf { it.chapterCount > 0 }
}
