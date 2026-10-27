package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.model.AppVersion
import com.rchdliang.copymanga_neo.domain.repository.AppInfoRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val checking: Boolean = false,
    val update: AppRelease? = null,
    val message: String? = null
)

class AboutViewModel(private val repository: AppInfoRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AboutUiState())
    val uiState = mutableState.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, _ ->
        mutableState.update { it.copy(checking = false, message = "检查更新失败，请稍后重试") }
    }

    private var autoCheckStarted = false

    fun checkAutomatically(currentVersion: String, enabled: Boolean) {
        if (autoCheckStarted) return
        autoCheckStarted = true
        if (enabled) checkForUpdate(currentVersion, automatic = true)
    }

    fun checkForUpdate(currentVersion: String, automatic: Boolean = false) {
        if (uiState.value.checking) return
        mutableState.update { it.copy(checking = true, update = null, message = null) }
        viewModelScope.launch(errors) {
            try {
                repository.latestRelease().fold(
                    onSuccess = { release ->
                        val newer = AppVersion.isNewer(release.version, currentVersion)
                        mutableState.update {
                            it.copy(
                                checking = false, update = release.takeIf { newer },
                                message = if (newer || automatic) null else "已是最新版本"
                            )
                        }
                    },
                    onFailure = { failure ->
                        mutableState.update {
                            it.copy(
                                checking = false,
                                message = if (automatic) null else if (failure is com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException) failure.message else "检查更新失败，请稍后重试"
                            )
                        }
                    }
                )
            } finally {
                mutableState.update { it.copy(checking = false) }
            }
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun dismissUpdate() {
        mutableState.update { it.copy(update = null) }
    }
}
