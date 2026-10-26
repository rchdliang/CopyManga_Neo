package com.rchdliang.copymanga_neo.desktop

import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.multiplatform.platform.*
import dev.nucleusframework.composenativetray.tray.impl.WindowsTrayInitializer
import dev.nucleusframework.composenativetray.utils.TrayClickTracker
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.NotificationResult
import dev.nucleusframework.notification.common.notification
import dev.nucleusframework.notification.windows.WindowsNotificationCenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** 原生托盘与通知；回调统一回到 Tao UI 线程，托盘失效时恢复可见窗口。 */
internal class NucleusDesktopTransferNotifications(
    applicationScope: CoroutineScope,
    private val downloads: DownloadRepository,
    private val exports: ComicExportRepository,
    private val onOpenDownloads: () -> Unit,
    private val onWarning: (String) -> Unit,
    private val onOpenApplication: () -> Unit,
    private val onQuit: () -> Unit,
) : AutoCloseable {
    private val lifetime = SupervisorJob(applicationScope.coroutineContext[Job])
    private val scope = CoroutineScope(applicationScope.coroutineContext + lifetime)
    private val mutableStatus = MutableStateFlow<DesktopNotificationStatus>(DesktopNotificationStatus.Starting)
    val status = mutableStatus.asStateFlow()
    private val id = "copymanga-" + UUID.randomUUID()
    private val tracker = DesktopTransferNotificationTracker()

    init {
        scope.launch {
            var icon: Path? = null
            try {
                withContext(Dispatchers.IO) {
                    icon = Files.createTempFile("copymanga-tray-", ".ico")
                    checkNotNull(javaClass.getResourceAsStream("/copymanga-neo.ico")).use { source ->
                        Files.newOutputStream(requireNotNull(icon)).use(source::copyTo)
                    }
                }
                coroutineScope {
                    launch {
                        var missed = 0
                        while (isActive) {
                            val before = TrayClickTracker.lastClick(id)
                            withContext(Dispatchers.IO) { WindowsTrayInitializer.refreshPosition(id) }
                            val after = TrayClickTracker.lastClick(id)
                            if (after != null && after !== before) {
                                missed = 0
                                mutableStatus.value = DesktopNotificationStatus.Available
                            } else if (++missed >= 3) {
                                unavailable("系统托盘暂不可用，窗口将保持可见")
                            }
                            delay(1000)
                        }
                    }
                    combine(downloads.getDownloadProgress(), downloads.getTasks(), exports.state) { progress, tasks, export ->
                        Triple(progress.values.toList(), tasks.isNotEmpty(), export)
                    }.collect { (progress, hasTasks, export) ->
                        val presentation = desktopTransferPresentation(progress, export)
                        withContext(Dispatchers.IO) {
                            WindowsTrayInitializer.update(id, icon.toString(), presentation.take(120),
                                onLeftClick = { action { onOpenDownloads() } }, menuContent = {
                                    Item("打开应用") { action { onOpenApplication() } }
                                    Item("打开下载") { action { onOpenDownloads() } }
                                    if (presentation != "CopyManga Neo") Item(presentation.take(100), isEnabled = false)
                                    Divider()
                                    Item("暂停全部下载", isEnabled = hasTasks && progress.any { it.status in active }) {
                                        action {
                                            downloads.awaitReady().getOrThrow()
                                            downloads.getTasks().value.forEach { downloads.pauseDownload(it.taskKey) }
                                        }
                                    }
                                    Item("继续已暂停或失败的下载", isEnabled = progress.any { it.status in resumable }) {
                                        action {
                                            downloads.awaitReady().getOrThrow()
                                            downloads.getDownloadProgress().value.values.filter { it.status in resumable }
                                                .forEach { downloads.resumeDownload(it.taskKey) }
                                        }
                                    }
                                    Item("取消全部下载", isEnabled = hasTasks) { action {
                                        downloads.awaitReady().getOrThrow(); downloads.cancelAll()
                                    } }
                                    Item("取消导出", isEnabled = export.running) { action { exports.cancel() } }
                                    Divider()
                                    Item("退出应用…") { action { onQuit() } }
                                })
                        }
                        tracker.events(progress, export).forEach { notice ->
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    NotificationManager.initialize()
                                    notification(title = notice.title, message = notice.message,
                                        onActivated = { action { onOpenDownloads() } }).send()
                                }
                                if (result is NotificationResult.Failure) onWarning("系统通知不可用：${result.reason}")
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                onWarning("系统通知不可用：${failure.message.orEmpty()}")
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { unavailable("系统托盘不可用：${failure.message.orEmpty()}") }
            catch (failure: LinkageError) { unavailable("系统托盘组件不可用：${failure.message.orEmpty()}") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try { WindowsTrayInitializer.dispose(id) }
                    finally {
                        WindowsNotificationCenter.uninitialize()
                        icon?.let { Files.deleteIfExists(it) }
                    }
                }
                mutableStatus.value = DesktopNotificationStatus.Closed
            }
        }
    }

    private fun unavailable(message: String) {
        if (mutableStatus.value !is DesktopNotificationStatus.Unavailable) onWarning(message)
        mutableStatus.value = DesktopNotificationStatus.Unavailable(message)
    }

    private fun action(block: suspend () -> Unit) {
        if (!lifetime.isActive) return
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onWarning("下载操作失败：${failure.message.orEmpty()}") }
        }
    }

    override fun close() { lifetime.cancel() }
}

private val active = setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.WAITING_FOR_WIFI,
    DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING)
private val resumable = setOf(DownloadTaskStatus.PAUSED, DownloadTaskStatus.PAUSED_BY_RISK, DownloadTaskStatus.ERROR)
