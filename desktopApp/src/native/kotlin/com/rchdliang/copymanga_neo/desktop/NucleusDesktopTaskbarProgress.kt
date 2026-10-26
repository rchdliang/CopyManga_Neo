package com.rchdliang.copymanga_neo.desktop

import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.multiplatform.platform.desktopTaskbarPresentation
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.taskbarprogress.tao.NucleusTaskbarProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.Taskbar

/** 复用传输进度规则，通过 Tao 窗口更新 Windows 任务栏。 */
internal class NucleusDesktopTaskbarProgress(
    applicationScope: CoroutineScope,
    window: NucleusWindow,
    downloads: DownloadRepository,
    exports: ComicExportRepository,
    visibility: Flow<Boolean>,
    onWarning: (String) -> Unit,
) : AutoCloseable {
    private val job = applicationScope.launch {
        try {
            check(NucleusTaskbarProgress.isAvailable()) { "系统未提供任务栏进度接口" }
            combine(downloads.getDownloadProgress(), exports.state, window.minimizedFlow, visibility) { progress, export, _, _ ->
                desktopTaskbarPresentation(progress.values.toList(), export)
            }.collect { presentation ->
                val value = presentation.percent / 100.0
                val queued = when (presentation.state) {
                    Taskbar.State.OFF -> NucleusTaskbarProgress.hideProgress(window)
                    Taskbar.State.INDETERMINATE -> NucleusTaskbarProgress.showIndeterminate(window)
                    Taskbar.State.ERROR -> NucleusTaskbarProgress.showError(window, value)
                    Taskbar.State.PAUSED -> NucleusTaskbarProgress.showPaused(window, value)
                    else -> NucleusTaskbarProgress.showProgress(window, value)
                }
                check(queued) { "窗口尚未就绪" }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { onWarning("任务栏进度不可用：${failure.message.orEmpty()}") }
        finally { NucleusTaskbarProgress.hideProgress(window) }
    }
    override fun close() { job.cancel() }
}
