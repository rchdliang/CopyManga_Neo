package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

sealed interface DesktopNotificationStatus {
    data object Starting : DesktopNotificationStatus
    data object Available : DesktopNotificationStatus
    data class Unavailable(val reason: String) : DesktopNotificationStatus
    data object Closed : DesktopNotificationStatus
}

/** System-tray controls; the window host owns background lifetime and explicit quit policy. */
class DesktopTransferNotifications(
    applicationScope: CoroutineScope,
    private val downloads: DownloadRepository,
    private val exports: ComicExportRepository,
    private val applicationIcon: Image,
    private val onOpenDownloads: () -> Unit,
    private val onWarning: (String) -> Unit,
    private val onOpenApplication: () -> Unit = onOpenDownloads,
    private val onQuit: () -> Unit = {}
) : AutoCloseable {
    private val lifetime = SupervisorJob(applicationScope.coroutineContext[Job])
    private val scope = CoroutineScope(applicationScope.coroutineContext + lifetime)
    private val mutableStatus = MutableStateFlow<DesktopNotificationStatus>(DesktopNotificationStatus.Starting)
    val status = mutableStatus.asStateFlow()
    private val tracker = DesktopTransferNotificationTracker()

    init {
        scope.launch(Dispatchers.Swing) {
            var tray: SystemTray? = null
            var icon: TrayIcon? = null
            try {
                if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                    unavailable("当前桌面不支持系统托盘通知；下载与导出进度仍可在应用内查看")
                    return@launch
                }
                tray = SystemTray.getSystemTray()
                val progressItem = MenuItem().apply { isEnabled = false }
                val pause = MenuItem("暂停全部下载").apply { addActionListener { action {
                    downloads.awaitReady().getOrThrow()
                    downloads.getTasks().value.forEach { downloads.pauseDownload(it.taskKey) }
                } } }
                val resume = MenuItem("继续已暂停或失败的下载").apply { addActionListener { action {
                    downloads.awaitReady().getOrThrow()
                    downloads.getDownloadProgress().value.values.filter { it.status in resumableStatuses }
                        .forEach { downloads.resumeDownload(it.taskKey) }
                } } }
                val cancelDownloads = MenuItem("取消全部下载").apply { addActionListener { action { downloads.awaitReady().getOrThrow(); downloads.cancelAll() } } }
                val cancelExport = MenuItem("取消导出").apply { addActionListener { action { exports.cancel() } } }
                val menu = PopupMenu().apply {
                    add(MenuItem("打开应用").apply { addActionListener { onOpenApplication() } })
                    add(MenuItem("打开下载").apply { addActionListener { openDownloads() } })
                    addSeparator()
                    add(pause); add(resume); add(cancelDownloads); add(cancelExport)
                    addSeparator()
                    add(MenuItem("退出应用…").apply { addActionListener { onQuit() } })
                }
                // Preserve the multi-resolution variants: shrinking to 16px here loses HiDPI detail.
                val created = TrayIcon(applicationIcon, "CopyManga Neo", menu).apply {
                    isImageAutoSize = true
                    // Tray action events include a clicked native notification balloon.
                    addActionListener { openDownloads() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(event: MouseEvent) {
                            if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) openDownloads()
                        }
                    })
                }
                tray.add(created)
                icon = created
                mutableStatus.value = DesktopNotificationStatus.Available
                combine(downloads.getDownloadProgress(), downloads.getTasks(), exports.state) { progress, tasks, export ->
                    Triple(progress.values.toList(), tasks.isNotEmpty(), export)
                }.collect { (progress, hasTasks, export) ->
                    val presentation = desktopTransferPresentation(progress, export)
                    created.toolTip = presentation.take(120)
                    if (presentation == "CopyManga Neo") {
                        if (progressItem.parent != null) menu.remove(progressItem)
                    } else {
                        progressItem.label = presentation.take(100)
                        if (progressItem.parent == null) menu.insert(progressItem, 2)
                    }
                    pause.isEnabled = hasTasks && progress.any { it.status in activeStatuses }
                    resume.isEnabled = progress.any { it.status in resumableStatuses }
                    cancelDownloads.isEnabled = hasTasks
                    cancelExport.isEnabled = export.running
                    tracker.events(progress, export).forEach { event ->
                        created.displayMessage(event.title, event.message,
                            if (event.error) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { unavailable("系统托盘通知不可用：${error.message.orEmpty()}") }
            finally {
                withContext(NonCancellable + Dispatchers.Swing) {
                    icon?.let { tray?.remove(it) }
                    if (mutableStatus.value !is DesktopNotificationStatus.Unavailable) mutableStatus.value = DesktopNotificationStatus.Closed
                }
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (!lifetime.isActive) return
        scope.launch(Dispatchers.Swing) {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { onWarning("下载操作失败：${error.message.orEmpty()}") }
        }
    }
    private fun openDownloads() {
        if (lifetime.isActive) runCatching(onOpenDownloads).onFailure { onWarning("无法打开下载页面：${it.message.orEmpty()}") }
    }
    private fun unavailable(reason: String) {
        mutableStatus.value = DesktopNotificationStatus.Unavailable(reason)
        onWarning(reason)
    }
    override fun close() { lifetime.cancel() }
}

private val activeStatuses = setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING)
private val resumableStatuses = setOf(DownloadTaskStatus.PAUSED, DownloadTaskStatus.PAUSED_BY_RISK, DownloadTaskStatus.ERROR)

data class DesktopTransferNotice(val title: String, val message: String, val error: Boolean = false)

/** Pure event projection keeps progress updates from repeating completion/error balloons. */
class DesktopTransferNotificationTracker {
    private val announced = linkedSetOf<String>()
    fun events(downloads: List<DownloadProgressInfo>, export: ComicExportState): List<DesktopTransferNotice> = buildList {
        fun once(key: String, notice: DesktopTransferNotice) {
            if (announced.add(key)) add(notice)
            while (announced.size > 256) announced.remove(announced.first())
        }
        downloads.forEach { item ->
            val identity = "download:${item.taskKey}:${item.taskId}"
            when (item.status) {
                DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING -> once("$identity:started",
                    DesktopTransferNotice("正在下载 · ${item.name}", "${item.statusText}，点击查看进度"))
                DownloadTaskStatus.COMPLETED -> if (item.totalChapters > 0) once("$identity:done:${item.totalChapters}",
                    DesktopTransferNotice(item.name, "${item.statusText} · ${item.completedChapters}/${item.totalChapters}"))
                DownloadTaskStatus.ERROR, DownloadTaskStatus.PAUSED_BY_RISK -> once("$identity:${item.status}:${item.errorMessage}",
                    DesktopTransferNotice(item.name, item.errorMessage ?: item.statusText, error = true))
                else -> Unit
            }
        }
        val id = export.taskId
        if (id != null) when (export.status) {
            ComicExportStatus.RUNNING -> once("export:$id:started", DesktopTransferNotice("正在导出", "${export.current.ifBlank { "正在准备输出" }}，点击查看进度"))
            ComicExportStatus.COMPLETED -> once("export:$id:done:${export.outputs.size}:${export.failures.size}",
                DesktopTransferNotice(if (export.failures.isEmpty()) "导出完成" else "导出部分完成",
                    "已保存 ${export.outputs.size} 项" + if (export.failures.isEmpty()) "" else "，失败 ${export.failures.size} 项，请在下载页面查看",
                    error = export.failures.isNotEmpty()))
            ComicExportStatus.INTERRUPTED -> once("export:$id:interrupted", DesktopTransferNotice("导出已中断", export.current.ifBlank { "请在下载页面查看并重试" }, true))
            ComicExportStatus.CANCELLED -> once("export:$id:cancelled", DesktopTransferNotice("导出已取消", "${export.outputs.size} 项已完成的输出已保留"))
            else -> Unit
        }
    }
}

fun desktopTransferPresentation(downloads: List<DownloadProgressInfo>, export: ComicExportState): String {
    val active = downloads.firstOrNull { it.status in activeStatuses }
    val downloadText = active?.let { "${it.name} ${(it.overallProgress.coerceIn(0f, 1f) * 100).toInt()}% · ${it.statusText}" }
        ?: downloads.firstOrNull { it.status !in setOf(DownloadTaskStatus.IDLE, DownloadTaskStatus.COMPLETED) }?.let { "${it.name} · ${it.statusText}" }
    val exportText = if (export.running) "导出 ${(export.progress * 100).toInt()}% · ${export.current}" else null
    return listOfNotNull(downloadText, exportText).joinToString(" | ").ifBlank { "CopyManga Neo" }
}
