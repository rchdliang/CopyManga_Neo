package com.rchdliang.copymanga_neo.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicMetadataRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ComicPresentationUiState(
    val metadata: Map<String, ComicCardMetadata> = emptyMap(),
    val blocked: List<Theme> = emptyList(),
    val ready: Boolean = false,
    val failures: Map<String, String> = emptyMap(),
    val cacheScope: String = ""
) {
    fun visibility(path: String?): ComicCoverVisibility =
        if (path.isNullOrBlank() || path.startsWith("novel:")) ComicCoverVisibility.VISIBLE
        else comicCoverVisibility(ready, blocked, metadata[path])
}

@HiltViewModel
class ComicPresentationViewModel @Inject constructor(
    private val repository: ComicMetadataRepository,
    blacklist: ThemeBlacklistRepository
) : ViewModel() {
    val uiState = combine(
        repository.metadata, repository.failures, repository.cacheScope,
        combine(blacklist.blockedThemes, blacklist.isEnabled) { themes, enabled ->
            if (enabled) themes else emptyList()
        }
    ) { metadata, failures, cacheScope, blocked ->
        ComicPresentationUiState(metadata, blocked, true, failures, cacheScope)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ComicPresentationUiState())

    fun preload(paths: List<String>, priority: Boolean = false) =
        repository.preload(paths, priority)

    fun retry(path: String) = repository.retry(path)
}
