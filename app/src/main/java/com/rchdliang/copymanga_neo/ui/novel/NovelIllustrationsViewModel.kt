package com.rchdliang.copymanga_neo.ui.novel

import com.rchdliang.copymanga_neo.ui.components.userFacingError
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelIllustrationExportRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NovelIllustrationExportState(
    val busy: Boolean = false,
    val pending: ReaderExportImage? = null,
    val message: String? = null
)

/** 作品偏好独立于分卷；保存操作固定到点击时的原图，系统选择器返回后仍可继续。 */
@HiltViewModel
class NovelIllustrationsViewModel @Inject constructor(
    private val preferences: NovelReaderPreferencesRepository,
    private val appSettings: AppSettingsRepository,
    private val exports: NovelIllustrationExportRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private fun restoredDocument(): ReaderExportImage? {
        val path = savedState.get<String>("novel_export_path") ?: return null
        val mime = savedState.get<String>("novel_export_mime") ?: return null
        val name = savedState.get<String>("novel_export_name") ?: return null
        return ReaderExportImage(path, mime, name)
    }

    private val mutable =
        MutableStateFlow(NovelIllustrationExportState(busy = restoredDocument() != null))
    val state = mutable.asStateFlow()
    private val handler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("NovelIllustrations", "插图操作失败", error)
        mutable.value = NovelIllustrationExportState(message = "插图操作失败，请重试")
    }

    fun blurForBook(path: String) = combine(
        preferences.illustrationBlur(path), appSettings.settings
    ) { override, settings -> override ?: settings.blurNovelIllustrationPreviews }
        .distinctUntilChanged()
        .catch { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("NovelIllustrations", "模糊设置读取失败", error)
            mutable.value = mutable.value.copy(message = "模糊设置读取失败，暂时隐藏预览细节")
            emit(true)
        }

    fun setBlur(path: String, enabled: Boolean) {
        viewModelScope.launch(handler) {
            preferences.setIllustrationBlur(path, enabled).onFailure {
                mutable.value = mutable.value.copy(message = "模糊设置保存失败，请重试")
            }
        }
    }

    fun export(volume: NovelReadingVolume, image: NovelSection? = null) {
        if (mutable.value.busy) return
        mutable.value = NovelIllustrationExportState(busy = true)
        viewModelScope.launch(handler) {
            val result = if (image == null) exports.prepareVolume(volume) else exports.prepareImage(
                volume,
                image
            )
            result.fold(
                onSuccess = {
                    mutable.value = NovelIllustrationExportState(busy = true, pending = it)
                },
                onFailure = {
                    mutable.value =
                        NovelIllustrationExportState(message = userFacingError(it, "插图准备失败，请重试"))
                }
            )
        }
    }

    fun claimPending(): ReaderExportImage? {
        val file = mutable.value.pending ?: return null
        savedState["novel_export_path"] = file.filePath
        savedState["novel_export_mime"] = file.mimeType
        savedState["novel_export_name"] = file.displayName
        mutable.value = mutable.value.copy(pending = null)
        return file
    }

    fun saveDocument(destination: String?) {
        val file = restoredDocument()
        clearDocument()
        if (destination == null) {
            mutable.value = NovelIllustrationExportState()
            return
        }
        if (file == null) {
            mutable.value = NovelIllustrationExportState(message = "保存操作已中断，请重试")
            return
        }
        viewModelScope.launch(handler) {
            exports.saveToDocument(file, destination).fold(
                onSuccess = {
                    mutable.value = NovelIllustrationExportState(message = "已保存到所选位置")
                },
                onFailure = {
                    mutable.value =
                        NovelIllustrationExportState(message = userFacingError(it, "保存失败，请重试"))
                }
            )
        }
    }

    fun launchFailed() {
        clearDocument()
        mutable.value = NovelIllustrationExportState(message = "无法打开保存位置，请重试")
    }

    fun clearMessage() {
        mutable.value = mutable.value.copy(message = null)
    }

    private fun clearDocument() {
        savedState.remove<String>("novel_export_path")
        savedState.remove<String>("novel_export_mime")
        savedState.remove<String>("novel_export_name")
    }
}
