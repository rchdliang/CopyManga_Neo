package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/** Taskbar progress belongs to the main window and must work even if the tray is unavailable. */
class DesktopTaskbarProgress(
    applicationScope: CoroutineScope,
    window: Window,
    downloads: DownloadRepository,
    exports: ComicExportRepository,
    onWarning: (String) -> Unit
) : AutoCloseable {
    private val lifetime = SupervisorJob(applicationScope.coroutineContext[Job])

    init {
        CoroutineScope(applicationScope.coroutineContext + lifetime).launch(Dispatchers.Swing) {
            var taskbar: Taskbar? = null
            var presentation = DesktopTaskbarPresentation(Taskbar.State.OFF)
            fun applyProgress() {
                if (!window.isDisplayable) return
                taskbar?.let {
                    // Set state first: leaving indeterminate mode can reset the native value.
                    it.setWindowProgressState(window, presentation.state)
                    if (presentation.state != Taskbar.State.OFF && presentation.state != Taskbar.State.INDETERMINATE) {
                        it.setWindowProgressValue(window, presentation.percent)
                    }
                }
            }
            val visibility = object : ComponentAdapter() {
                override fun componentShown(event: ComponentEvent) {
                    try { applyProgress() }
                    catch (failure: Exception) {
                        // A shell failure while restoring the window must not escape onto the EDT.
                        taskbar = null
                        onWarning("任务栏进度不可用：${failure.message.orEmpty()}")
                    }
                }
            }
            try {
                if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) return@launch
                val supported = Taskbar.getTaskbar()
                if (!supported.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW) ||
                    !supported.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) return@launch
                taskbar = supported
                window.addComponentListener(visibility)
                combine(downloads.getDownloadProgress(), exports.state) { progress, export ->
                    desktopTaskbarPresentation(progress.values.toList(), export)
                }.distinctUntilChanged().collect {
                    presentation = it
                    applyProgress()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { onWarning("任务栏进度不可用：${failure.message.orEmpty()}") }
            finally {
                withContext(NonCancellable + Dispatchers.Swing) {
                    window.removeComponentListener(visibility)
                    if (window.isDisplayable) runCatching { taskbar?.setWindowProgressState(window, Taskbar.State.OFF) }
                }
            }
        }
    }

    override fun close() { lifetime.cancel() }
}

internal data class DesktopTaskbarPresentation(val state: Taskbar.State, val percent: Int = 0)

internal fun desktopTaskbarPresentation(downloads: List<DownloadProgressInfo>, export: ComicExportState): DesktopTaskbarPresentation {
    val pending = downloads.filter { it.status != DownloadTaskStatus.IDLE && it.status != DownloadTaskStatus.COMPLETED }
    val active = pending.filter { it.status in taskbarActiveStatuses }
    if (pending.isEmpty() && !export.running) return DesktopTaskbarPresentation(Taskbar.State.OFF)
    val running = active.isNotEmpty() || export.running
    if (running && (active.any { it.totalChapters <= 0 } || (export.running && export.totalBytes <= 0))) {
        return DesktopTaskbarPresentation(Taskbar.State.INDETERMINATE)
    }
    // Give each pending transfer an equal share; chapter counts and export bytes are different units.
    val fractions = pending.map { it.overallProgress } + if (export.running) listOf(export.progress) else emptyList()
    val percent = (fractions.map { if (it.isFinite()) it.coerceIn(0f, 1f).toDouble() else 0.0 }.average() * 100).toInt()
    val state = when {
        running -> Taskbar.State.NORMAL
        pending.any { it.status == DownloadTaskStatus.ERROR || it.status == DownloadTaskStatus.PAUSED_BY_RISK } -> Taskbar.State.ERROR
        else -> Taskbar.State.PAUSED
    }
    return DesktopTaskbarPresentation(state, percent)
}

private val taskbarActiveStatuses = setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING)
