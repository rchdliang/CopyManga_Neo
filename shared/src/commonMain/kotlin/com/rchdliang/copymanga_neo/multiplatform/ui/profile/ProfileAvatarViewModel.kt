package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileAvatarState(val bytes: ByteArray? = null, val busy: Boolean = false, val error: String? = null)

/** Keep chooser/decoded preview through configuration changes; never persist avatar bytes or URIs. */
class ProfileAvatarViewModel(private val platform: ProfilePlatform) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileAvatarState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var generation = 0

    fun choose() {
        if (mutableState.value.busy) return
        val expected = ++generation
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                platform.pickAvatarJpeg().fold(
                    onSuccess = { bytes -> if (expected == generation) mutableState.value = mutableState.value.copy(bytes = bytes ?: mutableState.value.bytes) },
                    onFailure = { if (expected == generation) mutableState.value = mutableState.value.copy(error = it.message ?: "无法读取头像图片") }
                )
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (expected == generation) mutableState.value = mutableState.value.copy(error = error.message ?: "无法读取头像图片") }
            finally { if (expected == generation) mutableState.value = mutableState.value.copy(busy = false) }
        }
    }

    fun clear() {
        generation++
        work?.cancel()
        work = null
        mutableState.value = ProfileAvatarState()
    }
}
