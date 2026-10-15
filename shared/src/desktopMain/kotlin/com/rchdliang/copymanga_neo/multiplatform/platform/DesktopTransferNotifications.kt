package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

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
                val progressItem = MenuItem("没有正在进行的下载或导出").apply { isEnabled = false }
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
                    add(progressItem); addSeparator()
                    add(pause); add(resume); add(cancelDownloads); add(cancelExport)
                    addSeparator()
                    add(MenuItem("退出应用…").apply { addActionListener { onQuit() } })
                }
                val created = TrayIcon(trayImage(null), "CopyManga Neo", menu).apply {
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
                    progressItem.label = presentation.take(100)
                    pause.isEnabled = hasTasks && progress.any { it.status in activeStatuses }
                    resume.isEnabled = progress.any { it.status in resumableStatuses }
                    cancelDownloads.isEnabled = hasTasks
                    cancelExport.isEnabled = export.running
                    val active = progress.firstOrNull { it.status in activeStatuses }
                    val fraction = active?.overallProgress ?: export.progress.takeIf { export.running }
                    created.image = trayImage(fraction)
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

internal data class DesktopTransferNotice(val title: String, val message: String, val error: Boolean = false)

/** Pure event projection keeps progress updates from repeating completion/error balloons. */
internal class DesktopTransferNotificationTracker {
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

internal fun desktopTransferPresentation(downloads: List<DownloadProgressInfo>, export: ComicExportState): String {
    val active = downloads.firstOrNull { it.status in activeStatuses }
    val downloadText = active?.let { "${it.name} ${(it.overallProgress.coerceIn(0f, 1f) * 100).toInt()}% · ${it.statusText}" }
        ?: downloads.firstOrNull { it.status !in setOf(DownloadTaskStatus.IDLE, DownloadTaskStatus.COMPLETED) }?.let { "${it.name} · ${it.statusText}" }
    val exportText = if (export.running) "导出 ${(export.progress * 100).toInt()}% · ${export.current}" else null
    return listOfNotNull(downloadText, exportText).joinToString(" | ").ifBlank { "CopyManga Neo · 没有正在进行的下载或导出" }
}

private fun trayImage(progress: Float?): BufferedImage = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
    val graphics = createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color(0x46, 0x66, 0x36); graphics.fillRoundRect(1, 1, 30, 30, 9, 9)
        graphics.color = Color.WHITE; graphics.fillRect(14, 7, 4, 12)
        graphics.fillPolygon(intArrayOf(9, 23, 16), intArrayOf(16, 16, 23), 3)
        graphics.fillRect(8, 25, 16, 2)
        progress?.let {
            graphics.color = Color(0xe3, 0xf6, 0xb5)
            graphics.drawArc(2, 2, 27, 27, 90, -(it.coerceIn(0f, 1f) * 360).toInt())
        }
    } finally { graphics.dispose() }
}
