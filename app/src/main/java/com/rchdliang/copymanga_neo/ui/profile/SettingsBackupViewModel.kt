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
    val message: String? = null
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

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun export(uri: Uri) = operate {
        repository.exportSettings().fold(
            onSuccess = { serialized ->
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("无法打开文件")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(serialized) }
                "软件设置已导出"
            },
            onFailure = { "导出失败，请重试" }
        )
    }

    fun import(uri: Uri) = operate {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法打开文件")
        // 仅读取有限长度，避免意外选择图片或大型文件时耗尽内存。
        val bytes = input.use { stream ->
            val buffer = ByteArray(SettingsBackupRepository.MAX_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = stream.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
        if (bytes.size > SettingsBackupRepository.MAX_BYTES) return@operate "设置文件过大，请选择有效的备份"
        repository.importSettings(bytes.toString(Charsets.UTF_8)).fold(
            onSuccess = { "设置及本地阅读数据已导入" },
            onFailure = {
                when (it.message) {
                    "导入失败，已恢复原设置", "导入未完成，请检查设置后重试" -> it.message!!
                    else -> "备份无效或无法读取，设置未更改"
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
