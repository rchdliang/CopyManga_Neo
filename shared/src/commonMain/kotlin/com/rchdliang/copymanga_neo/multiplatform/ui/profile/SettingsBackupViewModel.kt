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
    val pendingImport: String? = null,
    val pendingImportUserData: Boolean = false,
    val restorePending: Boolean = false
)

class SettingsBackupViewModel constructor(
    private val repository: SettingsBackupRepository,
    private val platform: ProfilePlatform,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsBackupUiState(
        pendingImport = savedState["pending_import"],
        pendingImportUserData = savedState["pending_import_userdata"] ?: false))
    val uiState = mutableState.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, error ->
        UiLog.e("SettingsBackup", "设置备份操作失败", error)
        mutableState.update { it.copy(busy = false, message = "设置备份操作失败，请重试") }
    }

    init {
        savedState.get<Boolean>("pending_picker_export")?.let {
            chooseDocument(exporting = it, userData = savedState["pending_picker_userdata"] ?: false)
        }
        viewModelScope.launch(errors) {
            val pending = withContext(Dispatchers.Default) { repository.hasPendingUserDataRestore() }
            mutableState.update { it.copy(restorePending = pending) }
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    /** Retained model owns file pickers so recomposition or route recreation cannot cancel an accepted selection. */
    fun chooseExport() = chooseDocument(exporting = true)
    fun chooseImport() = chooseDocument(exporting = false)
    fun chooseExportUserData() = chooseDocument(exporting = true, userData = true)
    fun chooseImportUserData() = chooseDocument(exporting = false, userData = true)
    fun cancelRestore() = operate {
        repository.cancelUserDataRestore().fold(
            { mutableState.update { it.copy(restorePending = false) }; "已取消用户数据恢复" },
            { "取消失败，请重试" })
    }

    private fun chooseDocument(exporting: Boolean, userData: Boolean = false) {
        if (uiState.value.busy || uiState.value.picking) return
        mutableState.update { it.copy(picking = true) }
        savedState["pending_picker_export"] = exporting
        savedState["pending_picker_userdata"] = userData
        viewModelScope.launch(errors) {
            try {
                val selected = if (exporting) {
                    platform.createDocument(if (userData) "CopyManga-Neo-user-data.json" else "CopyManga-Neo-settings.json", "application/json")
                } else {
                    platform.openDocument(listOf("application/json", "text/plain", "application/octet-stream"))
                }
                selected.fold(
                    onSuccess = { destination ->
                        if (destination != null) {
                            if (exporting) exportBackup(destination, userData)
                            else {
                                savedState["pending_import"] = destination
                                savedState["pending_import_userdata"] = userData
                                mutableState.update { it.copy(pendingImport = destination, pendingImportUserData = userData) }
                            }
                        }
                    },
                    onFailure = { error -> mutableState.update { it.copy(message = error.message ?: "无法打开文件选择器") } }
                )
            } finally {
                savedState.remove<Boolean>("pending_picker_export")
                savedState.remove<Boolean>("pending_picker_userdata")
                mutableState.update { it.copy(picking = false) }
            }
        }
    }

    fun dismissImport() {
        savedState.remove<String>("pending_import")
        savedState.remove<Boolean>("pending_import_userdata")
        mutableState.update { it.copy(pendingImport = null) }
    }

    fun confirmImport() {
        val selected = uiState.value.pendingImport ?: return
        val userData = uiState.value.pendingImportUserData
        dismissImport()
        importBackup(selected, userData)
    }

    fun export(uri: String) = exportBackup(uri, false)
    private fun exportBackup(uri: String, userData: Boolean) = operate {
        (if (userData) repository.exportUserData() else repository.exportSettings()).fold(
            onSuccess = { serialized ->
                platform.writeDocument(uri, serialized.encodeToByteArray()).fold({}, { throw it })
                if (userData) "用户数据已导出；不包含下载文件和登录凭据" else "软件设置已导出"
            },
            onFailure = { "导出失败，请重试" }
        )
    }

    fun import(uri: String) = importBackup(uri, false)
    private fun importBackup(uri: String, userData: Boolean) = operate {
        val limit = if (userData) SettingsBackupRepository.USER_DATA_MAX_BYTES else SettingsBackupRepository.MAX_BYTES
        // The platform applies the same bounded read while streaming, before allocating untrusted data.
        val bytes = platform.readDocument(uri, limit + 1).fold({ it }, { throw it })
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
