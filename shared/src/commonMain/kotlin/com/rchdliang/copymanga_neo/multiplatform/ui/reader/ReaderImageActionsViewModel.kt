package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.multiplatform.ui.components.shareFailureMessage
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderImageAction { SAVE, SAVE_AS, SHARE }
data class PreparedReaderImage(val image: ReaderExportImage, val action: ReaderImageAction)
data class ReaderImageActionsUiState(
    val busy: Boolean = false,
    val pending: PreparedReaderImage? = null,
    val message: String? = null,
    val closeAfterMessage: Boolean = false,
    val dismissRequested: Boolean = false
)

/** 操作固定到长按时选中的原图；旋转屏幕后系统选择器结果仍可继续处理。 */
class ReaderImageActionsViewModel constructor(
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
        documentImage?.let { image ->
            // Rejoin the host's restored native result using the same image metadata. Android's
            // retained/saved picker registry avoids opening a second chooser after process death.
            state.value = ReaderImageActionsUiState(busy = true, pending = PreparedReaderImage(image, ReaderImageAction.SAVE_AS))
        }
    }

    private val handler = CoroutineExceptionHandler { _, error ->
        UiLog.e("ReaderImageExport", "图片导出失败", error)
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
                            onSuccess = { finish("图片已保存") },
                            onFailure = { if (it is UserActionCancelled) state.value = ReaderImageActionsUiState()
                                else fail(it.message ?: "保存失败，请重试") }
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

    /** Claim synchronously; the retained model owns a suspended picker/share operation across recomposition. */
    fun launchPending(platform: ReaderPlatform) {
        val pending = claimPending() ?: return
        work = viewModelScope.launch(handler) {
            when (pending.action) {
                ReaderImageAction.SAVE_AS -> platform.chooseImageDestination(pending.image).fold(
                    onSuccess = { destination -> saveChosenDocument(destination) },
                    onFailure = { launchFailed(it.message ?: "无法打开保存位置选择器") }
                )
                ReaderImageAction.SHARE -> platform.shareImage(pending.image).fold(
                    onSuccess = { state.value = ReaderImageActionsUiState(dismissRequested = true) },
                    onFailure = {
                        state.value = ReaderImageActionsUiState()
                        shareFailureMessage(it, "无法打开分享界面")?.let(::launchFailed)
                    }
                )
                ReaderImageAction.SAVE -> Unit
            }
        }
    }

    fun consumeDismissRequest() {
        state.value = state.value.copy(dismissRequested = false)
    }

    fun saveDocument(uri: String?) {
        work = viewModelScope.launch(handler) { saveChosenDocument(uri) }
    }

    private suspend fun saveChosenDocument(uri: String?) {
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
        repository.saveToDocument(image, uri).fold(
            onSuccess = { finish("图片已保存到所选位置") },
            onFailure = { fail(it.message ?: "保存失败，请重试") }
        )
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
