package com.rchdliang.copymanga_neo.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsBackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val restorePending: Boolean = false
)

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    private val repository: SettingsBackupRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsBackupUiState())
    val uiState = mutableState.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("SettingsBackup", "设置备份操作失败", error)
        mutableState.update { it.copy(busy = false, message = "设置备份操作失败，请重试") }
    }

    init {
        viewModelScope.launch(errors) {
            val pending = withContext(Dispatchers.IO) { repository.hasPendingUserDataRestore() }
            mutableState.update { it.copy(restorePending = pending) }
        }
    }

    fun cancelRestore() = operate {
        repository.cancelUserDataRestore().fold(
            { mutableState.update { it.copy(restorePending = false) }; "已取消用户数据恢复" },
            { "取消失败，请重试" }
        )
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun export(uri: Uri) = exportBackup(uri, false)
    fun exportUserData(uri: Uri) = exportBackup(uri, true)
    private fun exportBackup(uri: Uri, userData: Boolean) = operate {
        (if (userData) repository.exportUserData() else repository.exportSettings()).fold(
            onSuccess = { serialized ->
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("无法打开文件")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(serialized) }
                if (userData) "用户数据已导出；不包含下载文件和登录凭据" else "软件设置已导出"
            },
            onFailure = { "导出失败，请重试" }
        )
    }

    fun import(uri: Uri) = importBackup(uri, false)
    fun importUserData(uri: Uri) = importBackup(uri, true)
    private fun importBackup(uri: Uri, userData: Boolean) = operate {
        val limit = if (userData) SettingsBackupRepository.USER_DATA_MAX_BYTES else SettingsBackupRepository.MAX_BYTES
        val input = context.contentResolver.openInputStream(uri) ?: error("无法打开文件")
        // 仅读取有限长度，避免意外选择图片或大型文件时耗尽内存。
        val bytes = input.use { stream ->
            val buffer = ByteArray(limit + 1)
            var count = 0
            while (count < buffer.size) {
                val read = stream.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
        if (bytes.size > limit) return@operate "备份文件过大，请选择有效的备份"
        val raw = bytes.decodeToString(throwOnInvalidSequence = true)
        (if (userData) repository.importUserData(raw) else repository.importSettings(raw)).fold(
            onSuccess = {
                if (userData) {
                    mutableState.update { it.copy(restorePending = true) }
                    "备份已校验，请完全退出并重新打开应用以恢复"
                } else "设置及本地阅读数据已导入"
            },
            onFailure = {
                when (it.message) {
                    "导入失败，已恢复原设置", "导入未完成，请检查设置后重试" -> it.message!!
                    else -> if (userData) "用户备份无效、版本不兼容或无法暂存，原数据未更改" else "备份无效或无法读取，设置未更改"
                }
            }
        )
    }

    private fun operate(block: suspend () -> String) {
        if (uiState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch(errors) {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                mutableState.update { it.copy(message = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("SettingsBackup", "设置文件读写失败", error)
                mutableState.update { it.copy(message = "无法读写文件，请重新选择") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
