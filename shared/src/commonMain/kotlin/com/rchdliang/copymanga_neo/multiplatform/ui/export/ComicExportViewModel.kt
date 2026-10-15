package com.rchdliang.copymanga_neo.multiplatform.ui.export

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportRequest
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComicExportUiState(
    val visible: Boolean = false, val preparing: Boolean = false,
    val plan: ComicExportPlan? = null, val format: ComicExportFormat = ComicExportFormat.ZIP,
    val error: String? = null, val pickLocation: Boolean = false, val retry: Boolean = false,
    val previousOutputs: List<ComicExportOutput> = emptyList()
)

class ComicExportViewModel constructor(
    private val repository: ComicExportRepository,
    private val downloads: DownloadRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutableUi = MutableStateFlow(ComicExportUiState())
    val uiState = mutableUi.asStateFlow()
    val progress = repository.state
    private val mutableAvailable = MutableStateFlow<Set<String>>(emptySet())
    val availableChapters = mutableAvailable.asStateFlow()
    private var watching: Job? = null
    private var preparation: Job? = null
    private var destinationPicker: Job? = null
    private val handler = CoroutineExceptionHandler { _, error ->
        UiLog.e("ComicExport", "导出界面操作失败", error)
        mutableUi.update {
            it.copy(
                visible = true,
                preparing = false,
                error = error.message ?: "导出失败，请重试"
            )
        }
    }
    private val restoration = viewModelScope.launch(handler) {
        savedState.get<String>("export_draft")?.let { id ->
            repository.readDraft(id).fold(
                onSuccess = { draft ->
                    val format =
                        ComicExportFormat.entries.firstOrNull { it.name == savedState.get<String>("export_format") }
                            ?: ComicExportFormat.ZIP
                    if (draft != null) mutableUi.value = ComicExportUiState(
                        visible = true,
                        plan = draft.plan,
                        format = format,
                        previousOutputs = draft.outputs,
                        retry = savedState["export_retry"] ?: false,
                        pickLocation = savedState["export_picker_pending"] ?: false
                    )
                },
                onFailure = { showFailure(it, "导出草稿读取失败") }
            )
        }
    }

    fun prepare(request: ComicExportRequest) {
        if (repository.state.value.running) {
            mutableUi.value = ComicExportUiState(
                visible = true,
                error = "已有导出任务正在执行，请在系统通知中查看进度"
            )
            return
        }
        preparation?.cancel()
        mutableUi.value = ComicExportUiState(visible = true, preparing = true)
        preparation = viewModelScope.launch(handler) {
            restoration.join()
            repository.prepare(request).fold(onSuccess = { plan ->
                repository.saveDraft(plan).fold(
                    onSuccess = { draftId ->
                        savedState["export_draft"] = draftId
                        savedState["export_retry"] = false
                        savedState["export_format"] = ComicExportFormat.ZIP.name
                        mutableUi.value = ComicExportUiState(visible = true, plan = plan)
                    },
                    onFailure = { showFailure(it, "导出草稿保存失败") }
                )
            }, onFailure = { error ->
                mutableUi.value =
                    ComicExportUiState(visible = true, error = error.message ?: "无法准备导出")
            })
        }
    }

    fun setFormat(format: ComicExportFormat) {
        if (mutableUi.value.retry) return
        savedState["export_format"] = format.name
        mutableUi.update { it.copy(format = format) }
    }

    fun pickLocation() {
        if (destinationPicker?.isActive == true) return
        savedState["export_picker_pending"] = true
        mutableUi.update { it.copy(pickLocation = true, error = null) }
    }

    /** Native picker continuations belong to the retained ViewModel, not the current composition. */
    fun launchDestinationPicker(platform: ComicExportPlatform) {
        if (destinationPicker?.isActive == true) return
        destinationPicker = viewModelScope.launch(handler) {
            restoration.join()
            val location = claimLocation() ?: return@launch
            try {
                platform.pickDestination(location).fold(
                    onSuccess = { destination -> saveDestination(destination) },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        saveDestination(null, "无法打开系统文件选择器：${error.message}")
                    }
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                saveDestination(null, "无法打开系统文件选择器：${error.message}")
            } finally {
                savedState["export_picker_pending"] = false
            }
        }
    }

    fun claimLocation(): ComicExportLocation? {
        val state = mutableUi.value
        val plan = state.plan ?: return null
        if (!state.pickLocation) return null
        mutableUi.update { it.copy(pickLocation = false) }
        val location =
            repository.describeLocation(plan, state.format, state.previousOutputs) ?: return null
        savedState["export_directory"] = location.directory
        return location
    }

    fun saveDestination(uri: String?, error: String? = null) {
        if (uri == null) {
            mutableUi.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch(handler) {
            restoration.join()
            val state = mutableUi.value
            val plan = state.plan
            if (plan == null) {
                mutableUi.update {
                    it.copy(
                        visible = true,
                        error = "导出准备已失效，请重新选择章节"
                    )
                }
                return@launch
            }
            repository.start(
                plan, state.format, uri, savedState["export_directory"] ?: true,
                state.previousOutputs
            ).fold(
                onSuccess = { dismiss() },
                onFailure = { failure ->
                    mutableUi.update {
                        it.copy(
                            error = failure.message ?: "导出启动失败"
                        )
                    }
                }
            )
        }
    }

    fun retry(taskId: String? = null) {
        preparation = viewModelScope.launch(handler) {
            restoration.join()
            repository.awaitRestoration().fold(
                onSuccess = {},
                onFailure = { showFailure(it, "导出任务恢复失败"); return@launch }
            )
            val state = repository.state.value
            if (state.running || (taskId != null && taskId != state.taskId)) {
                mutableUi.value = ComicExportUiState(
                    visible = true,
                    error = "导出任务已更新，请重新选择要导出的漫画"
                )
                return@launch
            }
            val plan = state.plan ?: return@launch
            repository.saveDraft(plan, state.outputs).fold(
                onSuccess = { draftId ->
                    savedState["export_draft"] = draftId
                    savedState["export_format"] = state.format.name
                    savedState["export_retry"] = true
                    mutableUi.value = ComicExportUiState(
                        visible = true,
                        plan = plan,
                        format = state.format,
                        retry = true,
                        previousOutputs = state.outputs
                    )
                },
                onFailure = { showFailure(it, "导出草稿保存失败") }
            )
        }
    }

    fun cancel() = repository.cancel()
    fun watchLocalChapters(comic: String) {
        watching?.cancel()
        watching = viewModelScope.launch(handler) {
            downloads.getAllDownloadedComics().collect {
                mutableAvailable.value = downloads.getDownloadedChapterUuids(comic)
            }
        }
    }

    fun dismissProgress() {
        viewModelScope.launch(handler) {
            repository.dismiss()
                .fold(onSuccess = {}, onFailure = { showFailure(it, "导出记录关闭失败") })
        }
    }

    fun dismiss() {
        destinationPicker?.cancel()
        savedState["export_picker_pending"] = false
        preparation?.cancel()
        savedState.remove<String>("export_draft")
        mutableUi.value = ComicExportUiState()
    }

    private fun showFailure(error: Throwable, fallback: String) {
        if (error is CancellationException) throw error
        mutableUi.update {
            it.copy(
                visible = true,
                preparing = false,
                error = error.message ?: fallback
            )
        }
    }
}
