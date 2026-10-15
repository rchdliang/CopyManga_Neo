package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsBackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val picking: Boolean = false,
    val pendingImport: String? = null
)

class SettingsBackupViewModel constructor(
    private val repository: SettingsBackupRepository,
    private val platform: ProfilePlatform,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsBackupUiState(pendingImport = savedState["pending_import"]))
    val uiState = mutableState.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, error ->
        UiLog.e("SettingsBackup", "设置备份操作失败", error)
        mutableState.update { it.copy(busy = false, message = "设置备份操作失败，请重试") }
    }

    init {
        savedState.get<Boolean>("pending_picker_export")?.let { chooseDocument(exporting = it) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    /** Retained model owns file pickers so recomposition or route recreation cannot cancel an accepted selection. */
    fun chooseExport() = chooseDocument(exporting = true)
    fun chooseImport() = chooseDocument(exporting = false)

    private fun chooseDocument(exporting: Boolean) {
        if (uiState.value.busy || uiState.value.picking) return
        mutableState.update { it.copy(picking = true) }
        savedState["pending_picker_export"] = exporting
        viewModelScope.launch(errors) {
            try {
                val selected = if (exporting) {
                    platform.createDocument("CopyManga-Neo-settings.json", "application/json")
                } else {
                    platform.openDocument(listOf("application/json", "text/plain", "application/octet-stream"))
                }
                selected.fold(
                    onSuccess = { destination ->
                        if (destination != null) {
                            if (exporting) export(destination)
                            else {
                                savedState["pending_import"] = destination
                                mutableState.update { it.copy(pendingImport = destination) }
                            }
                        }
                    },
                    onFailure = { error -> mutableState.update { it.copy(message = error.message ?: "无法打开文件选择器") } }
                )
            } finally {
                savedState.remove<Boolean>("pending_picker_export")
                mutableState.update { it.copy(picking = false) }
            }
        }
    }

    fun dismissImport() {
        savedState.remove<String>("pending_import")
        mutableState.update { it.copy(pendingImport = null) }
    }

    fun confirmImport() {
        val selected = uiState.value.pendingImport ?: return
        dismissImport()
        import(selected)
    }

    fun export(uri: String) = operate {
        repository.exportSettings().fold(
            onSuccess = { serialized ->
                platform.writeDocument(uri, serialized.encodeToByteArray()).getOrThrow()
                "软件设置已导出"
            },
            onFailure = { "导出失败，请重试" }
        )
    }

    fun import(uri: String) = operate {
        // The platform applies the same bounded read while streaming, before allocating untrusted data.
        val bytes = platform.readDocument(uri, SettingsBackupRepository.MAX_BYTES + 1).getOrThrow()
        if (bytes.size > SettingsBackupRepository.MAX_BYTES) return@operate "设置文件过大，请选择有效的备份"
        repository.importSettings(bytes.decodeToString(throwOnInvalidSequence = true)).fold(
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
                val message = withContext(Dispatchers.Default) { block() }
                mutableState.update { it.copy(message = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UiLog.w("SettingsBackup", "设置文件读写失败", error)
                mutableState.update { it.copy(message = "无法读写文件，请重新选择") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
