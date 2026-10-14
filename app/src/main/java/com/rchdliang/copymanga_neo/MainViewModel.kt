package com.rchdliang.copymanga_neo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    downloadRepository: DownloadRepository,
    appSettingsRepository: AppSettingsRepository,
    private val novelImagePositionDebug: NovelImagePositionDebugStore
) : ViewModel() {
    val downloadProgress = downloadRepository.getDownloadProgress()
    val novelImagePositionDebugSamples = novelImagePositionDebug.samples

    fun dismissNovelImagePositionDebugSample(id: String) {
        novelImagePositionDebug.dismiss(id)
    }

    val appSettings = appSettingsRepository.settings.catch {
        android.util.Log.e("MainViewModel", "应用设置读取失败", it)
        emit(AppSettings())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isLoggedIn = MutableStateFlow(userRepository.isLoggedInSync())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _avatar = MutableStateFlow<String?>(null)
    val avatar: StateFlow<String?> = _avatar.asStateFlow()

    /** 防止过期后重复触发自动登录 */
    private var hasTriedAutoLoginAfterExpiry = false

    init {
        android.util.Log.d(
            "MainViewModel",
            "init: isLoggedInSync=${userRepository.isLoggedInSync()}, autoLogin=${userRepository.getAutoLoginSync()}"
        )
        viewModelScope.launch {
            userRepository.isAuthCacheReady().first { it }
            userRepository.sessionChanges().collectLatest {
                val value = userRepository.isLoggedInSync()
                android.util.Log.d("MainViewModel", "isLoggedIn Flow emitted: $value")
                val wasValue = _isLoggedIn.value
                _isLoggedIn.value = value
                if (!value && wasValue) {
                    android.util.Log.d("MainViewModel", "检测到登出，尝试自动登录")
                    tryAutoLoginAfterExpiry()
                } else if (value) {
                    android.util.Log.d("MainViewModel", "检测到登录，刷新用户信息")
                    refreshMemberInfo()
                    hasTriedAutoLoginAfterExpiry = false
                }
            }
        }
        viewModelScope.launch {
            userRepository.getAvatar().collect { value ->
                _avatar.value = value
            }
        }
        viewModelScope.launch {
            userRepository.isAuthCacheReady().first { it }
            tryAutoLogin()
        }
    }

    private suspend fun refreshMemberInfo() {
        userRepository.getMemberInfo().fold(
            onSuccess = { android.util.Log.d("MainViewModel", "Member info refreshed") },
            onFailure = { error ->
                android.util.Log.w("MainViewModel", "refresh member info failed: ${error.message}")
            }
        )
    }

    /** 启动时尝试自动登录 */
    private fun tryAutoLogin() {
        android.util.Log.d(
            "MainViewModel",
            "tryAutoLogin: isLoggedIn=${userRepository.isLoggedInSync()}, autoLogin=${userRepository.getAutoLoginSync()}"
        )
        if (!userRepository.isLoggedInSync() && userRepository.getAutoLoginSync()) {
            viewModelScope.launch { performAutoLogin() }
        }
    }

    /** 会话过期后尝试自动登录 */
    private fun tryAutoLoginAfterExpiry() {
        if (!userRepository.getAutoLoginSync()) return
        if (hasTriedAutoLoginAfterExpiry) return
        hasTriedAutoLoginAfterExpiry = true
        viewModelScope.launch { performAutoLogin() }
    }

    private suspend fun performAutoLogin() {
        val generation = userRepository.sessionVersionSync()
        val username = userRepository.getSavedUsernameSync()
        val password = userRepository.getSavedPasswordSync()
        if (username == null || password == null) {
            android.util.Log.w("MainViewModel", "自动登录跳过：无保存的凭据")
            return
        }
        android.util.Log.d("MainViewModel", "尝试自动登录: $username")
        if (!userRepository.getAutoLoginSync() || userRepository.sessionVersionSync() != generation) return
        userRepository.login(username, password, expectedGeneration = generation).fold(
            onSuccess = {
                android.util.Log.d("MainViewModel", "自动登录成功，刷新用户信息")
                refreshMemberInfo()
            },
            onFailure = { error ->
                android.util.Log.e("MainViewModel", "自动登录失败: ${error.message}")
            }
        )
    }
}
