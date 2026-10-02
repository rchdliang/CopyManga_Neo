package com.rchdliang.copymanga_neo.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReaderImageAction { SAVE, SAVE_AS, SHARE }
data class PreparedReaderImage(val image: ReaderExportImage, val action: ReaderImageAction)
data class ReaderImageActionsUiState(
    val busy: Boolean = false,
    val pending: PreparedReaderImage? = null,
    val message: String? = null,
    val closeAfterMessage: Boolean = false
)

/** 操作固定到长按时选中的原图；旋转屏幕后系统选择器结果仍可继续处理。 */
@HiltViewModel
class ReaderImageActionsViewModel @Inject constructor(
    private val repository: ReaderImageRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val state = MutableStateFlow(ReaderImageActionsUiState())
    val uiState = state.asStateFlow()
    private var work: Job? = null
    private var documentImage: ReaderExportImage? =
        savedState.get<String>("document_image_path")?.let { path ->
            val mime = savedState.get<String>("document_image_mime")
            val name = savedState.get<String>("document_image_name")
            if (mime != null && name != null) ReaderExportImage(path, mime, name) else null
        }

    init {
        if (documentImage != null) state.value = ReaderImageActionsUiState(busy = true)
    }

    private val handler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("ReaderImageExport", "图片导出失败", error)
        fail("图片导出失败，请重试")
    }

    fun perform(action: ReaderImageAction, source: String?, title: String) {
        if (state.value.busy) return
        state.value = ReaderImageActionsUiState(busy = true)
        work = viewModelScope.launch(handler) {
            repository.prepare(source.orEmpty(), title).fold(
                onSuccess = { image ->
                    if (action == ReaderImageAction.SAVE) {
                        repository.saveToGallery(image).fold(
                            onSuccess = { finish("已保存到相册 · Pictures/CopyManga Neo") },
                            onFailure = { fail(it.message ?: "保存失败，请重试") }
                        )
                    } else {
                        state.value = ReaderImageActionsUiState(
                            busy = true,
                            pending = PreparedReaderImage(image, action)
                        )
                    }
                },
                onFailure = { fail(it.message ?: "图片准备失败，请重试") }
            )
        }
    }

    /** 先消费待启动操作，避免重组或旋转时重复打开系统界面。 */
    fun claimPending(): PreparedReaderImage? {
        val pending = state.value.pending ?: return null
        documentImage = pending.image.takeIf { pending.action == ReaderImageAction.SAVE_AS }
        documentImage?.let {
            savedState["document_image_path"] = it.filePath
            savedState["document_image_mime"] = it.mimeType
            savedState["document_image_name"] = it.displayName
        }
        state.value = state.value.copy(pending = null)
        return pending
    }

    fun saveDocument(uri: Uri?) {
        val image = documentImage
        clearDocument()
        if (uri == null) {
            state.value = ReaderImageActionsUiState()
            return
        }
        if (image == null) {
            fail("图片操作已中断，请重新选择保存位置")
            return
        }
        work = viewModelScope.launch(handler) {
            repository.saveToDocument(image, uri.toString()).fold(
                onSuccess = { finish("图片已保存到所选位置") },
                onFailure = { fail(it.message ?: "保存失败，请重试") }
            )
        }
    }

    fun shareLaunched() {
        state.value = ReaderImageActionsUiState()
    }

    fun launchFailed(message: String) {
        clearDocument(); fail(message)
    }

    fun clearMessage() {
        state.value = state.value.copy(message = null, closeAfterMessage = false)
    }

    fun cancel() {
        work?.cancel(CancellationException("用户取消图片导出"))
        clearDocument()
        state.value = ReaderImageActionsUiState()
    }

    private fun fail(message: String) {
        state.value = ReaderImageActionsUiState(message = message)
    }

    private fun finish(message: String) {
        state.value = ReaderImageActionsUiState(message = message, closeAfterMessage = true)
    }

    private fun clearDocument() {
        documentImage = null
        savedState.remove<String>("document_image_path")
        savedState.remove<String>("document_image_mime")
        savedState.remove<String>("document_image_name")
    }
}
