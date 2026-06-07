package com.rchdliang.copymanga_neo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(userRepository.isLoggedInSync())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        android.util.Log.d(
            "MainViewModel",
            "init: isLoggedInSync=${userRepository.isLoggedInSync()}"
        )
        viewModelScope.launch {
            userRepository.isLoggedIn().collect { value ->
                android.util.Log.d("MainViewModel", "isLoggedIn Flow emitted: $value")
                _isLoggedIn.value = value
            }
        }
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            if (userRepository.isLoggedIn().first()) {
                userRepository.getMemberInfo().fold(
                    onSuccess = { /* 静默刷新用户信息 */ },
                    onFailure = { error ->
                        android.util.Log.w(
                            "MainViewModel",
                            "refresh member info failed: ${error.message}"
                        )
                    }
                )
            }
        }
    }
}
