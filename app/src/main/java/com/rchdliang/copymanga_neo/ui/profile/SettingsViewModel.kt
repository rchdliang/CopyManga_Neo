package com.rchdliang.copymanga_neo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.StorageUsage
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import com.rchdliang.copymanga_neo.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val readerDefaults: ReaderPreferences = ReaderPreferences(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDictionaryDownloading: Boolean = false,
    val dictionaryReady: Boolean = false,
    val dictionaryVersion: String? = null,
    val dictionaryWorking: Boolean = false,
    val error: String? = null,
    val storageUsage: StorageUsage? = null,
    val storageLoading: Boolean = true,
    val clearingCache: Boolean = false,
    val clearingNovelCache: Boolean = false,
    val storageError: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val readerPreferences: ReaderPreferencesRepository,
    private val storage: StorageRepository,
    private val chineseDictionary: com.rchdliang.copymanga_neo.domain.repository.ChineseDictionaryRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("SettingsViewModel", "设置读取失败", error)
        mutableState.update { it.copy(isSaving = false, error = "设置读取失败，请重新打开应用") }
    }

    init {
        viewModelScope.launch(errors) {
            combine(
                chineseDictionary.version,
                chineseDictionary.isWorking
            ) { version, working -> version to working }
                .collect { (version, working) ->
                    mutableState.update {
                        it.copy(
                            dictionaryVersion = version,
                            dictionaryWorking = working
                        )
                    }
                }
        }
        viewModelScope.launch(errors) {
            chineseDictionary.converter.collect { converter ->
                mutableState.update { it.copy(dictionaryReady = converter != null) }
            }
        }
        viewModelScope.launch(errors) {
            combine(
                repository.settings,
                readerPreferences.globalPreferences
            ) { settings, reader -> settings to reader }
                .collect { (settings, reader) ->
                    mutableState.update {
                        it.copy(
                            settings = settings,
                            readerDefaults = reader,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun setThemeMode(mode: AppThemeMode) = save { repository.setThemeMode(mode) }
    fun setSimplifyChinese(enabled: Boolean) = save {
        if (enabled) {
            mutableState.update { it.copy(isDictionaryDownloading = true) }
            try {
                chineseDictionary.install().fold(
                    onSuccess = { repository.setSimplifyChinese(true) },
                    onFailure = {
                        Result.failure(
                            IllegalStateException(
                                "词典下载失败，请检查网络后重试",
                                it
                            )
                        )
                    }
                )
            } finally {
                mutableState.update { it.copy(isDictionaryDownloading = false, isSaving = false) }
            }
        } else repository.setSimplifyChinese(false)
    }

    private var dictionaryUpdateJob: Job? = null
    fun updateChineseDictionary() {
        if (dictionaryUpdateJob?.isActive == true || uiState.value.dictionaryWorking || uiState.value.isDictionaryDownloading) return
        dictionaryUpdateJob = viewModelScope.launch(errors) {
            chineseDictionary.update().fold(
                onSuccess = { result ->
                    mutableState.update {
                        it.copy(message = if (result.updated) "已更新至 ${result.version}" else "已是最新 ${result.version}")
                    }
                },
                onFailure = { mutableState.update { state -> state.copy(message = "词典更新失败，请检查网络后重试") } }
            )
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) = save { repository.setWifiOnlyDownloads(enabled) }
    fun setBlurNovelIllustrationPreviews(enabled: Boolean) =
        save { repository.setBlurNovelIllustrationPreviews(enabled) }

    fun setComicImageConcurrency(count: Int) = save { repository.setComicImageConcurrency(count) }
    fun setComicDownloadLimitKiB(limit: Int) = save { repository.setComicDownloadLimitKiB(limit) }
    fun setCredentialExportPassword(enabled: Boolean) =
        save { repository.setCredentialExportPassword(enabled) }

    fun setReadingDirection(value: String) =
        save { readerPreferences.updateGlobal { it.copy(readingDirection = value) } }

    fun setPageScale(value: String) =
        save { readerPreferences.updateGlobal { it.copy(pageScale = value) } }

    fun setScreenOrientation(value: String) =
        save { readerPreferences.updateGlobal { it.copy(screenOrientation = value) } }

    fun resetReaderDefaults() = save { readerPreferences.resetGlobal() }
    fun resetGlobalDefaults() = save {
        repository.resetDefaults().fold(
            onSuccess = { readerPreferences.resetGlobal() },
            onFailure = { Result.failure(it) }
        )
    }

    private var storageJob: Job? = null
    fun refreshStorage() {
        storageJob?.cancel()
        mutableState.update { it.copy(storageLoading = true, storageError = null) }
        storageJob = viewModelScope.launch(errors) {
            storage.getUsage().fold(
                onSuccess = { usage ->
                    mutableState.update {
                        it.copy(
                            storageUsage = usage,
                            storageLoading = false
                        )
                    }
                },
                onFailure = {
                    mutableState.update {
                        it.copy(
                            storageLoading = false,
                            storageError = "存储统计失败，请重试"
                        )
                    }
                }
            )
        }
    }

    fun clearImageCache() = clearCache(false, "图片缓存已清理") { storage.clearImageCache() }

    fun clearNovelCache() =
        clearCache(true, "小说正文、插图和接口缓存已清理") { storage.clearNovelCache() }

    private fun clearCache(novel: Boolean, success: String, action: suspend () -> Result<Unit>) {
        if (uiState.value.clearingCache) return
        mutableState.update { it.copy(clearingCache = true, clearingNovelCache = novel) }
        viewModelScope.launch(errors) {
            action().fold(
                onSuccess = {
                    mutableState.update {
                        it.copy(
                            clearingCache = false,
                            clearingNovelCache = false,
                            message = success
                        )
                    }
                },
                onFailure = {
                    mutableState.update {
                        it.copy(
                            clearingCache = false,
                            clearingNovelCache = false,
                            message = it.message ?: "清理缓存失败，请重试"
                        )
                    }
                }
            )
            refreshStorage()
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun save(action: suspend () -> Result<Unit>) {
        if (uiState.value.isLoading || uiState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true) }
        viewModelScope.launch(errors) {
            action().fold(
                onSuccess = { mutableState.update { it.copy(isSaving = false, error = null) } },
                onFailure = { failure ->
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            error = failure.message ?: "保存失败，请重试"
                        )
                    }
                }
            )
        }
    }
}
